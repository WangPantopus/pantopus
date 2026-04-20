@file:Suppress("MagicNumber", "LongMethod", "PackageNaming")

package app.pantopus.android.ui.screens.mailbox.item_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.mailbox.v2.MailboxV2Item
import app.pantopus.android.data.api.models.mailbox.v2.PackageDetailResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import app.pantopus.android.ui.components.KeyFactRow
import app.pantopus.android.ui.components.TimelineStep
import app.pantopus.android.ui.components.TimelineStepState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Key used to pass the mail id through the nav backstack. */
const val MAILBOX_ITEM_DETAIL_MAIL_ID_KEY = "mailId"

/** Data projected onto the Package body slot. */
data class PackageBodyContent(
    val carrier: String,
    val etaLine: String?,
)

/** Full projection of a single mailbox item for the detail screen. */
data class MailboxItemDetailContent(
    val category: MailItemCategory,
    val trust: MailTrust,
    val sender: SenderBlockContent,
    val aiElf: AIElfContent?,
    val keyFacts: List<KeyFactRow>,
    val timeline: List<TimelineStep>,
    val packageInfo: PackageBodyContent?,
    val ctaEnabled: Boolean,
)

/** Observed state for the Mailbox Item Detail screen. */
sealed interface MailboxItemDetailUiState {
    data object Loading : MailboxItemDetailUiState

    data class Loaded(val content: MailboxItemDetailContent) : MailboxItemDetailUiState

    data class Error(val message: String) : MailboxItemDetailUiState
}

/** Per-CTA busy / error flags surfaced to the view for optimistic UI. */
data class MailboxCTAFlags(
    val primaryLoading: Boolean = false,
    val ghostLoading: Boolean = false,
    val errorToast: String? = null,
    val primaryCompleted: Boolean = false,
)

/** ViewModel for the Mailbox Item Detail screen. */
@HiltViewModel
class MailboxItemDetailViewModel
    @Inject
    constructor(
        private val repo: MailboxRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val mailId: String =
            requireNotNull(savedStateHandle[MAILBOX_ITEM_DETAIL_MAIL_ID_KEY]) {
                "MailboxItemDetailViewModel requires a '$MAILBOX_ITEM_DETAIL_MAIL_ID_KEY' nav arg."
            }

        private val _state = MutableStateFlow<MailboxItemDetailUiState>(MailboxItemDetailUiState.Loading)

        /** Observed UI state. */
        val state: StateFlow<MailboxItemDetailUiState> = _state.asStateFlow()

        private val _ctaFlags = MutableStateFlow(MailboxCTAFlags())

        /** Ephemeral CTA busy + toast flags. */
        val ctaFlags: StateFlow<MailboxCTAFlags> = _ctaFlags.asStateFlow()

        /** Initial load; no-op when already loaded. */
        fun load() {
            if (_state.value is MailboxItemDetailUiState.Loaded) return
            refresh()
        }

        /** Pull-to-refresh / retry. */
        fun refresh() {
            _state.value = MailboxItemDetailUiState.Loading
            viewModelScope.launch { fetch() }
        }

        /** Clear the CTA error toast. */
        fun dismissToast() {
            _ctaFlags.update { it.copy(errorToast = null) }
        }

        /**
         * Primary CTA for Package: `PATCH .../status { status: "delivered" }`.
         * Applies an optimistic timeline flip, rolls back on failure.
         */
        fun logAsReceived() {
            val current = _state.value as? MailboxItemDetailUiState.Loaded ?: return
            if (_ctaFlags.value.primaryLoading) return
            val originalTimeline = current.content.timeline
            val originalCtaEnabled = current.content.ctaEnabled
            _state.value =
                MailboxItemDetailUiState.Loaded(
                    current.content.copy(
                        timeline = flipCurrentToDone(originalTimeline),
                        ctaEnabled = false,
                    ),
                )
            _ctaFlags.update { it.copy(primaryLoading = true) }
            viewModelScope.launch {
                when (val result = repo.packageStatusUpdate(mailId, status = "delivered")) {
                    is NetworkResult.Success ->
                        _ctaFlags.update { it.copy(primaryLoading = false, primaryCompleted = true) }
                    is NetworkResult.Failure -> {
                        (_state.value as? MailboxItemDetailUiState.Loaded)?.let { rolled ->
                            _state.value =
                                MailboxItemDetailUiState.Loaded(
                                    rolled.content.copy(
                                        timeline = originalTimeline,
                                        ctaEnabled = originalCtaEnabled,
                                    ),
                                )
                        }
                        _ctaFlags.update {
                            it.copy(
                                primaryLoading = false,
                                errorToast = result.error.message,
                            )
                        }
                    }
                }
            }
        }

        /** Ghost CTA: `POST .../action { action: "not_mine" }`. */
        fun markNotMine() {
            val current = _state.value as? MailboxItemDetailUiState.Loaded ?: return
            if (_ctaFlags.value.ghostLoading) return
            _ctaFlags.update { it.copy(ghostLoading = true) }
            viewModelScope.launch {
                when (val result = repo.itemAction(mailId, "not_mine")) {
                    is NetworkResult.Success -> {
                        _state.value =
                            MailboxItemDetailUiState.Loaded(current.content.copy(ctaEnabled = false))
                        _ctaFlags.update { it.copy(ghostLoading = false) }
                    }
                    is NetworkResult.Failure ->
                        _ctaFlags.update {
                            it.copy(
                                ghostLoading = false,
                                errorToast = result.error.message,
                            )
                        }
                }
            }
        }

        private suspend fun fetch() {
            when (val result = repo.item(mailId)) {
                is NetworkResult.Failure ->
                    _state.value = MailboxItemDetailUiState.Error(result.error.message)
                is NetworkResult.Success -> {
                    val item = result.data.mail
                    val category = MailItemCategory.fromRaw(item.type)
                    if (category == MailItemCategory.Package) {
                        fetchPackage(item, category)
                    } else {
                        _state.value = MailboxItemDetailUiState.Loaded(projectBase(item, category))
                    }
                }
            }
        }

        private suspend fun fetchPackage(
            item: MailboxV2Item,
            category: MailItemCategory,
        ) {
            when (val result = repo.packageDetail(mailId)) {
                is NetworkResult.Failure ->
                    _state.value = MailboxItemDetailUiState.Loaded(projectBase(item, category))
                is NetworkResult.Success ->
                    _state.value = MailboxItemDetailUiState.Loaded(projectPackage(item, result.data))
            }
        }

        private fun projectBase(
            item: MailboxV2Item,
            category: MailItemCategory,
        ): MailboxItemDetailContent =
            MailboxItemDetailContent(
                category = category,
                trust = MailTrust.fromRaw(item.senderTrust),
                sender =
                    SenderBlockContent(
                        displayName = item.senderDisplay,
                        meta = item.createdAt,
                        initials = initials(item.senderDisplay),
                    ),
                aiElf = null,
                keyFacts =
                    listOf(
                        KeyFactRow(label = "Subject", value = item.displayTitle ?: "—"),
                        KeyFactRow(label = "Received", value = item.createdAt),
                    ),
                timeline = emptyList(),
                packageInfo = null,
                ctaEnabled = true,
            )

        private fun projectPackage(
            item: MailboxV2Item,
            pkg: PackageDetailResponse,
        ): MailboxItemDetailContent {
            val map = pkg.`package`
            val tracking = map["tracking_number"] as? String
            val carrier = (map["carrier"] as? String) ?: "Carrier"
            val currentStatus = (map["status"] as? String) ?: "in_transit"
            val suggested = map["suggested_order_match"] as? String
            val facts =
                buildList {
                    if (tracking != null) {
                        add(KeyFactRow(label = "Tracking #", value = tracking, isCode = true))
                    }
                    add(KeyFactRow(label = "Sender", value = item.senderDisplay))
                    add(KeyFactRow(label = "Carrier", value = carrier))
                    add(KeyFactRow(label = "Received at", value = item.createdAt))
                }
            val elf =
                suggested?.let {
                    AIElfContent(
                        suggestion = "Looks like your $it order",
                        primaryChip = "Link",
                        secondaryChip = "Not mine",
                    )
                }
            return MailboxItemDetailContent(
                category = MailItemCategory.Package,
                trust = MailTrust.fromRaw(item.senderTrust),
                sender =
                    SenderBlockContent(
                        displayName = item.senderDisplay,
                        meta = pkg.sender?.display ?: carrier,
                        initials = initials(item.senderDisplay),
                    ),
                aiElf = elf,
                keyFacts = facts,
                timeline = timeline(currentStatus),
                packageInfo = PackageBodyContent(carrier = carrier, etaLine = null),
                ctaEnabled = currentStatus != "delivered",
            )
        }

        private fun initials(name: String): String =
            name.trim()
                .split(" ")
                .take(2)
                .mapNotNull { it.firstOrNull()?.toString() }
                .joinToString("")
                .uppercase()

        private fun timeline(status: String): List<TimelineStep> {
            val order = listOf("pre_receipt", "in_transit", "out_for_delivery", "delivered")
            val labels = listOf("Shipped", "In transit", "Out for delivery", "Delivered")
            val currentIndex = order.indexOf(status).let { if (it < 0) 1 else it }
            return order.mapIndexed { index, id ->
                val state =
                    when {
                        index < currentIndex -> TimelineStepState.Done
                        index == currentIndex -> TimelineStepState.Current
                        else -> TimelineStepState.Upcoming
                    }
                TimelineStep(title = labels[index], state = state)
            }
        }

        private fun flipCurrentToDone(steps: List<TimelineStep>): List<TimelineStep> {
            val index = steps.indexOfFirst { it.state == TimelineStepState.Current }
            if (index < 0) return steps
            return steps.mapIndexed { i, step ->
                when (i) {
                    index -> step.copy(state = TimelineStepState.Done)
                    index + 1 -> step.copy(state = TimelineStepState.Current)
                    else -> step
                }
            }
        }
    }
