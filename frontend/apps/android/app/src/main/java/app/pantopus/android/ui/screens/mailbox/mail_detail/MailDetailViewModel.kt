@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "TooManyFunctions", "LongParameterList")

package app.pantopus.android.ui.screens.mailbox.mail_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.mailbox.MailDetail
import app.pantopus.android.data.api.models.mailbox.v2.BookletDetailDto
import app.pantopus.android.data.api.models.mailbox.v2.CertifiedDetailDto
import app.pantopus.android.data.api.models.mailbox.v2.CommunityDetailDto
import app.pantopus.android.data.api.models.mailbox.v2.CommunityRsvpStatus
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import app.pantopus.android.ui.screens.mailbox.item_detail.MailItemCategory
import app.pantopus.android.ui.screens.mailbox.item_detail.MailTrust
import app.pantopus.android.ui.screens.shared.mail_item_detail.MailDetailTrust
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Nav arg key for the new A17.1 generic detail route. */
const val MAIL_DETAIL_MAIL_ID_KEY = "mailId"

/** Lifecycle state for the generic A17.1 detail screen. */
sealed interface MailDetailUiState {
    data object Loading : MailDetailUiState

    data class Loaded(val content: MailDetailContent) : MailDetailUiState

    data class Error(val message: String) : MailDetailUiState
}

/**
 * Pure projection of the backend mail item into the A17 shell slots.
 * Mirrors iOS `MailDetailContent`.
 *
 * T6.5c — adds optional `bookletDetail` / `certifiedDetail` fields so
 * the variant layouts can render their slot-specific designs without a
 * second fetch.
 */
data class MailDetailContent(
    val mailId: String,
    val category: MailItemCategory,
    val trust: MailTrust,
    val detailTrust: MailDetailTrust,
    val senderDisplayName: String,
    val senderMeta: String?,
    val senderInitials: String,
    val senderUserId: String?,
    val title: String,
    val excerpt: String?,
    val createdAtLabel: String?,
    val expiresAtLabel: String?,
    val bodyParagraphs: List<String>,
    val attachments: List<String>,
    val aiSummary: String?,
    val ackRequired: Boolean,
    val isAcknowledged: Boolean,
    val bookletDetail: BookletDetailDto? = null,
    val certifiedDetail: CertifiedDetailDto? = null,
    val communityDetail: CommunityDetailDto? = null,
) {
    /** Build a typed key-facts row list for the shell's KeyFacts slot. */
    fun keyFacts(): List<MailDetailKeyFact> =
        buildList {
            createdAtLabel?.let {
                add(MailDetailKeyFact(icon = PantopusIcon.Calendar, label = "Received", value = it))
            }
            expiresAtLabel?.let {
                add(MailDetailKeyFact(icon = PantopusIcon.Clock, label = "Expires", value = it))
            }
            senderMeta?.let {
                add(MailDetailKeyFact(icon = PantopusIcon.Briefcase, label = "From", value = it))
            }
            add(
                MailDetailKeyFact(
                    icon = category.icon,
                    label = "Category",
                    value = category.label,
                ),
            )
        }
}

/** Lightweight key/value/icon triple for the generic detail's key facts panel. */
data class MailDetailKeyFact(
    val id: String = java.util.UUID.randomUUID().toString(),
    val icon: PantopusIcon,
    val label: String,
    val value: String,
)

/**
 * T6.5b (P20) — Drives the generic A17.1 mail item detail screen on
 * Android. Mirrors iOS `MailDetailViewModel`.
 */
@HiltViewModel
class MailDetailViewModel
    @Inject
    constructor(
        private val repo: MailboxRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val mailId: String =
            checkNotNull(savedStateHandle.get<String>(MAIL_DETAIL_MAIL_ID_KEY)) {
                "MailDetailViewModel requires a $MAIL_DETAIL_MAIL_ID_KEY nav argument"
            }

        private val _state = MutableStateFlow<MailDetailUiState>(MailDetailUiState.Loading)
        val state: StateFlow<MailDetailUiState> = _state.asStateFlow()

        private val _toast = MutableStateFlow<String?>(null)
        val toast: StateFlow<String?> = _toast.asStateFlow()

        private val _ackInFlight = MutableStateFlow(false)
        val ackInFlight: StateFlow<Boolean> = _ackInFlight.asStateFlow()

        private val _rsvpInFlight = MutableStateFlow(false)
        val rsvpInFlight: StateFlow<Boolean> = _rsvpInFlight.asStateFlow()

        fun load() {
            if (_state.value is MailDetailUiState.Loaded) return
            refresh()
        }

        fun refresh() {
            _state.value = MailDetailUiState.Loading
            viewModelScope.launch {
                when (val result = repo.detail(mailId)) {
                    is NetworkResult.Success ->
                        _state.value = MailDetailUiState.Loaded(project(result.data.mail))
                    is NetworkResult.Failure ->
                        _state.value = MailDetailUiState.Error(result.error.message)
                }
            }
        }

        /**
         * Acknowledge the mail item. Optimistic — flips local
         * `isAcknowledged` then rolls back on transport failure.
         */
        fun acknowledge() {
            val current = _state.value as? MailDetailUiState.Loaded ?: return
            if (_ackInFlight.value) return
            _ackInFlight.value = true
            val optimistic = current.content.copy(isAcknowledged = true)
            _state.value = MailDetailUiState.Loaded(optimistic)
            viewModelScope.launch {
                when (val result = repo.acknowledge(mailId)) {
                    is NetworkResult.Success -> {
                        _toast.value = "Acknowledged"
                    }
                    is NetworkResult.Failure -> {
                        _state.value = MailDetailUiState.Loaded(current.content)
                        _toast.value = result.error.message
                    }
                }
                _ackInFlight.value = false
            }
        }

        fun consumeToast() {
            _toast.value = null
        }

        /**
         * Set the user's RSVP status on a Community mail item.
         * Optimistic — flips local state then rolls back on transport
         * failure. "Going" wires to the existing `POST /community/rsvp`
         * route (backend stores it as a `will_attend` reaction); other
         * states are stored locally until the backend exposes a typed
         * per-status route (P22 scope note in the parity audit).
         */
        fun setRsvp(status: CommunityRsvpStatus) {
            val current = _state.value as? MailDetailUiState.Loaded ?: return
            val community = current.content.communityDetail ?: return
            if (_rsvpInFlight.value) return
            _rsvpInFlight.value = true
            val optimistic =
                current.content.copy(
                    communityDetail =
                        community.copy(
                            rsvp = status,
                            attendeeCount =
                                when {
                                    status == CommunityRsvpStatus.Going && community.rsvp != CommunityRsvpStatus.Going ->
                                        community.attendeeCount + 1
                                    status != CommunityRsvpStatus.Going && community.rsvp == CommunityRsvpStatus.Going ->
                                        (community.attendeeCount - 1).coerceAtLeast(0)
                                    else -> community.attendeeCount
                                },
                        ),
                )
            _state.value = MailDetailUiState.Loaded(optimistic)
            if (status != CommunityRsvpStatus.Going) {
                _toast.value = rsvpToast(status)
                _rsvpInFlight.value = false
                return
            }
            viewModelScope.launch {
                when (val result = repo.communityRsvp(community.communityItemId)) {
                    is NetworkResult.Success -> _toast.value = "You're going"
                    is NetworkResult.Failure -> {
                        _state.value = MailDetailUiState.Loaded(current.content)
                        _toast.value = result.error.message
                    }
                }
                _rsvpInFlight.value = false
            }
        }

        private fun rsvpToast(status: CommunityRsvpStatus): String =
            when (status) {
                CommunityRsvpStatus.Going -> "You're going"
                CommunityRsvpStatus.Maybe -> "Saved as maybe"
                CommunityRsvpStatus.NotGoing -> "Marked as can't make it"
                CommunityRsvpStatus.Undecided -> "RSVP cleared"
            }

        companion object {
            /**
             * Pure projection from the backend [MailDetail] envelope to
             * the generic A17.1 content. Static so the test suite can
             * exercise it without standing the VM up.
             */
            @JvmStatic
            fun project(detail: MailDetail): MailDetailContent {
                val category = MailItemCategory.fromRaw(detail.mailType ?: detail.type)
                val trust = MailTrust.fromRaw(null)
                val senderDisplayName =
                    detail.sender?.name
                        ?: detail.senderBusinessName
                        ?: detail.senderAddress
                        ?: "Unknown sender"
                val senderMeta = detail.sender?.username?.let { "@$it" } ?: detail.senderAddress
                val title = detail.displayTitle ?: detail.subject ?: "Mail"
                val excerpt = detail.previewText
                val createdAtLabel = formatLongDate(detail.createdAt)
                val expiresAtLabel = formatLongDate(detail.expiresAt)
                val bodyParagraphs =
                    detail.content
                        ?.takeIf { it.isNotEmpty() }
                        ?.split("\n\n")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?: emptyList()
                val ackRequired = detail.ackRequired == true
                val ackStatus = detail.ackStatus?.lowercase() == "acknowledged"
                // T6.5c–d — decode the per-variant payloads from
                // `mail.object`. Each decoder returns null unless the
                // payload carries its required shape.
                val bookletDetail =
                    if (category == MailItemCategory.Booklet) {
                        BookletDetailDto.decodeFromObjectPayload(detail.`object`)
                    } else {
                        null
                    }
                val certifiedDetail =
                    if (category == MailItemCategory.Certified) {
                        CertifiedDetailDto.decodeFromObjectPayload(detail.`object`)
                    } else {
                        null
                    }
                val communityDetail =
                    if (category == MailItemCategory.Community) {
                        CommunityDetailDto.decodeFromObjectPayload(detail.`object`)
                    } else {
                        null
                    }
                val resolvedAck = ackStatus || (certifiedDetail?.isAcknowledged == true)
                return MailDetailContent(
                    mailId = detail.id,
                    category = category,
                    trust = trust,
                    detailTrust = trust.detailTrust,
                    senderDisplayName = senderDisplayName,
                    senderMeta = senderMeta,
                    senderInitials = makeInitials(senderDisplayName),
                    senderUserId = detail.sender?.id,
                    title = title,
                    excerpt = excerpt,
                    createdAtLabel = createdAtLabel,
                    expiresAtLabel = expiresAtLabel,
                    bodyParagraphs = bodyParagraphs,
                    attachments = detail.attachments ?: emptyList(),
                    aiSummary = null,
                    ackRequired = ackRequired,
                    isAcknowledged = resolvedAck,
                    bookletDetail = bookletDetail,
                    certifiedDetail = certifiedDetail,
                    communityDetail = communityDetail,
                )
            }

            @JvmStatic
            fun makeInitials(name: String): String {
                if (name.isEmpty()) return "M"
                return name
                    .split(" ")
                    .take(2)
                    .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                    .joinToString("")
                    .ifEmpty { "M" }
            }

            @JvmStatic
            fun formatLongDate(iso: String?): String? {
                if (iso.isNullOrBlank()) return null
                val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return null
                val zoned = instant.atZone(ZoneId.systemDefault())
                return DateTimeFormatter.ofPattern("EEE MMM d, yyyy", Locale.US).format(zoned)
            }
        }
    }
