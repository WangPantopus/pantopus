@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.mailbox.mail_day

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav-arg key for the variant (`populated` or `empty`). */
const val MAIL_DAY_VARIANT_KEY = "variant"

/**
 * A13.16 — Backs the My Mail Day editor. The backend has been removed
 * from the repo, so `load()` projects a deterministic fixture
 * ([MailDaySampleData]) instead of fetching. The nav arg selects the
 * frame: any value other than "empty" loads the populated mid-afternoon
 * fixture.
 *
 * Action handlers (`acceptSuggestion` / `tickUndo`) flip in-memory state
 * so the screen feels real in previews; nothing writes to the wire.
 */
@HiltViewModel
class MailDayViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val variant: MailDayVariant =
            when (savedStateHandle.get<String>(MAIL_DAY_VARIANT_KEY)) {
                "empty" -> MailDayVariant.Empty
                else -> MailDayVariant.Populated
            }

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
                    when (variant) {
                        MailDayVariant.Populated -> MailDayUiState.Populated(MailDaySampleData.populated)
                        MailDayVariant.Empty -> MailDayUiState.Empty(MailDaySampleData.empty)
                    }
            }
        }

        fun requestScan() {
            onScanRequested()
        }

        fun acceptSuggestion(id: String) {
            val current = _state.value as? MailDayUiState.Populated ?: return
            val target = current.content.unreviewed.firstOrNull { it.id == id } ?: return
            val firstName = target.suggestedName.substringBefore(" ", target.suggestedName)
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
                        undoCountdown = 5,
                    ),
                ) + current.content.reviewed.map { it.copy(undoCountdown = null) }
            _state.value =
                MailDayUiState.Populated(
                    current.content.copy(
                        unreviewed = current.content.unreviewed.filterNot { it.id == id },
                        reviewed = newReviewed,
                    ),
                )
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
                (_state.value as? MailDayUiState.Populated)?.content?.reviewed
                    ?.count { it.action == ReviewedMailAction.Routed } ?: 0

        val junkedCount: Int
            get() =
                (_state.value as? MailDayUiState.Populated)?.content?.reviewed
                    ?.count { it.action == ReviewedMailAction.Junked } ?: 0

        val returnedCount: Int
            get() =
                (_state.value as? MailDayUiState.Populated)?.content?.reviewed
                    ?.count { it.action == ReviewedMailAction.Returned } ?: 0

        val canFinishDay: Boolean
            get() = _state.value is MailDayUiState.Populated && remaining == 0 && total > 0
    }
