@file:Suppress("PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.mailbox.mail_day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.mailbox.v2.PendingItemDto
import app.pantopus.android.data.api.models.mailbox.v2.ResolveRoutingRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Nav-arg key for the variant (`populated` or `empty`) — kept for the
 *  deep-link route; the live screen now derives the frame from the data. */
const val MAIL_DAY_VARIANT_KEY = "variant"

private const val UNDO_SECONDS = 5
private const val CONFIDENCE_SCALE = 100

/**
 * A13.16 — Backs the My Mail Day triage editor.
 *
 * `load()` fetches the unresolved routing queue from
 * `GET /api/mailbox/v2/pending` and renders each item as an "needs a call"
 * card. Accepting a suggestion optimistically moves the card into the
 * reviewed list and calls `POST /api/mailbox/v2/resolve`, rolling back on
 * failure. Finishing the day logs `POST /api/mailbox/v2/event` telemetry.
 *
 * Counts (routed / junked / returned / remaining / total) are derived
 * client-side — no summary endpoint returns them. Junk / return-to-sender
 * and per-item undo have no backend route, so those affordances remain
 * out of scope (the reviewed list only grows via accepted suggestions).
 */
@HiltViewModel
class MailDayViewModel
    @Inject
    constructor(
        private val repository: MailboxRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<MailDayUiState>(MailDayUiState.Loading)
        val state: StateFlow<MailDayUiState> = _state.asStateFlow()

        private var onScanRequested: () -> Unit = {}

        fun configure(onScanRequested: () -> Unit) {
            this.onScanRequested = onScanRequested
        }

        fun load() {
            _state.value = MailDayUiState.Loading
            viewModelScope.launch {
                _state.value =
                    when (val result = repository.pending()) {
                        is NetworkResult.Success -> project(result.data.pending)
                        is NetworkResult.Failure -> MailDayUiState.Error(result.error.message)
                    }
            }
        }

        fun refresh() = load()

        /**
         * Build a render frame from the pending queue. The reviewed list
         * starts empty (it grows as the user triages this session); the
         * yesterday-recap + setup-nudges + streak surfaces have no source
         * on `/pending`, so they are left empty and the screen hides them.
         */
        private fun project(pending: List<PendingItemDto>): MailDayUiState {
            val unreviewed = pending.map(::mapItem)
            val content =
                MailDayContent(
                    dateLabel = todayLabel(),
                    streakDays = 0,
                    lastScanLabel = "",
                    unreviewed = unreviewed,
                    reviewed = emptyList(),
                    yesterdayRecap = null,
                    setupNudges = emptyList(),
                )
            return if (unreviewed.isEmpty()) MailDayUiState.Empty(content) else MailDayUiState.Populated(content)
        }

        private fun mapItem(dto: PendingItemDto): UnreviewedMailDayItem {
            val mail = dto.mail
            return UnreviewedMailDayItem(
                id = dto.mailId,
                kind = kindFor(mail?.mailObjectType, mail?.category),
                label = mail?.subject?.takeIf { it.isNotBlank() } ?: "Mail",
                sender = mail?.senderDisplay ?: mail?.senderBusinessName ?: "Unknown sender",
                suggestedName = dto.recipientNameRaw.orEmpty(),
                suggestedAvatar = MailDaySuggestedAvatar.PersonalSky,
                confidencePercent = ((dto.bestMatchConfidence ?: 0.0) * CONFIDENCE_SCALE).toInt().coerceIn(0, CONFIDENCE_SCALE),
                secondaryLabel = "Other",
            )
        }

        private fun kindFor(
            objectType: String?,
            category: String?,
        ): MailDayKind {
            if (category?.contains("bill", ignoreCase = true) == true) return MailDayKind.Bill
            return when (objectType) {
                "package" -> MailDayKind.Package
                "postcard" -> MailDayKind.Postcard
                "booklet" -> MailDayKind.Magazine
                "bundle" -> MailDayKind.Flyer
                else -> MailDayKind.Envelope
            }
        }

        private fun todayLabel(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US))

        fun requestScan() {
            onScanRequested()
        }

        /**
         * Accept the AI suggestion: optimistically move the card to the
         * reviewed list, then resolve the routing on the backend. On failure
         * the previous content is restored.
         */
        fun acceptSuggestion(id: String) {
            val current = _state.value as? MailDayUiState.Populated ?: return
            val target = current.content.unreviewed.firstOrNull { it.id == id } ?: return
            val previous = current.content
            val firstName = target.suggestedName.substringBefore(" ", target.suggestedName)
            val drawer =
                when (target.suggestedAvatar) {
                    MailDaySuggestedAvatar.PersonalSky -> "personal"
                    MailDaySuggestedAvatar.HouseholdGreen -> "home"
                }
            val newReviewed =
                listOf(
                    ReviewedMailDayItem(
                        id = target.id,
                        kind = target.kind,
                        label = target.label,
                        action = ReviewedMailAction.Routed,
                        routedTo = firstName,
                        routedTint =
                            when (target.suggestedAvatar) {
                                MailDaySuggestedAvatar.PersonalSky -> MailDayRoutedTint.PersonPrimary
                                MailDaySuggestedAvatar.HouseholdGreen -> MailDayRoutedTint.HouseholdHome
                            },
                        whenLabel = "just now",
                        undoCountdown = UNDO_SECONDS,
                    ),
                ) + current.content.reviewed.map { it.copy(undoCountdown = null) }
            _state.value =
                MailDayUiState.Populated(
                    current.content.copy(
                        unreviewed = current.content.unreviewed.filterNot { it.id == id },
                        reviewed = newReviewed,
                    ),
                )
            viewModelScope.launch {
                val result = repository.resolve(ResolveRoutingRequest(mailId = id, drawer = drawer))
                if (result is NetworkResult.Failure) {
                    _state.value = MailDayUiState.Populated(previous)
                }
            }
        }

        /** Finish-day commit — no persistence route exists, so log telemetry. */
        fun finishDay() {
            if (!canFinishDay) return
            viewModelScope.launch {
                repository.logEvent(
                    eventType = "mailday_finished",
                    metadata =
                        mapOf(
                            "routed" to routedCount.toString(),
                            "junked" to junkedCount.toString(),
                            "returned" to returnedCount.toString(),
                        ),
                )
            }
        }

        fun tickUndo() {
            val current = _state.value as? MailDayUiState.Populated ?: return
            val reviewed = current.content.reviewed.toMutableList()
            val index = reviewed.indexOfFirst { (it.undoCountdown ?: 0) > 0 }
            if (index == -1) return
            val next = (reviewed[index].undoCountdown ?: 0) - 1
            reviewed[index] = reviewed[index].copy(undoCountdown = if (next > 0) next else null)
            _state.value = MailDayUiState.Populated(current.content.copy(reviewed = reviewed))
        }

        // ---- Derived counters used by the FinishDay bar + DayHeader ----

        val total: Int
            get() {
                val populated = _state.value as? MailDayUiState.Populated ?: return 0
                return populated.content.unreviewed.size + populated.content.reviewed.size
            }

        val done: Int
            get() = (_state.value as? MailDayUiState.Populated)?.content?.reviewed?.size ?: 0

        val remaining: Int
            get() = (_state.value as? MailDayUiState.Populated)?.content?.unreviewed?.size ?: 0

        val routedCount: Int
            get() =
                (_state.value as? MailDayUiState.Populated)
                    ?.content
                    ?.reviewed
                    ?.count { it.action == ReviewedMailAction.Routed } ?: 0

        val junkedCount: Int
            get() =
                (_state.value as? MailDayUiState.Populated)
                    ?.content
                    ?.reviewed
                    ?.count { it.action == ReviewedMailAction.Junked } ?: 0

        val returnedCount: Int
            get() =
                (_state.value as? MailDayUiState.Populated)
                    ?.content
                    ?.reviewed
                    ?.count { it.action == ReviewedMailAction.Returned } ?: 0

        val canFinishDay: Boolean
            get() = _state.value is MailDayUiState.Populated && remaining == 0 && total > 0
    }
