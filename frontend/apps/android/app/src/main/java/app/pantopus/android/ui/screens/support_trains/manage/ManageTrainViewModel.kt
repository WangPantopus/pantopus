@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.support_trains.manage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.support_trains.SupportTrainUpdateBody
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.support_trains.SupportTrainsRepository
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A13.13 — Manage train. Aggregate UI state for the organizer-side
 * surface. Mirrors the iOS `ManageTrainState` shape so the two
 * platforms project identical content.
 */
sealed interface ManageTrainState {
    /** Initial fetch in flight (shimmer skeleton). */
    object Loading : ManageTrainState

    /** Train loaded successfully. */
    data class Loaded(val content: ManageTrainContent) : ManageTrainState

    /** Fetch failed; the screen surfaces the message with a `Try again` CTA. */
    data class Error(val message: String) : ManageTrainState
}

/** One audience chip in the Send-an-update form. */
data class AudienceChipContent(
    val id: String,
    val label: String,
    val count: String,
)

/** Visual tone for an Organize-section row's leading icon tile. */
enum class OrganizeRowTone { AMBER, SKY, GREEN, RED }

/** One row in the Organize section card (or the Close-train destructive row). */
data class OrganizeRowContent(
    val id: String,
    val icon: PantopusIcon,
    val tone: OrganizeRowTone,
    val label: String,
    val meta: String?,
    val sub: String?,
    val isDestructive: Boolean,
)

/** The CloseTrainSheet's static copy. The editable thank-you note lives on the VM. */
data class CloseTrainSheetContent(
    val daysEarlyLabel: String,
    val mealsDelivered: String,
    val neighborsHelped: String,
    val coverageDays: String,
    val recipientQuote: String,
)

/** Static, design-driven content for one Manage Train screen instance. */
data class ManageTrainContent(
    val trainId: String,
    val title: String,
    val dateRangeLabel: String,
    val isActive: Boolean,
    val slotFillValue: String,
    val helpersValue: String,
    val daysLeftValue: String,
    val dropoutValue: String,
    val slotsFilled: Int,
    val slotsOpen: Int,
    val slotsDropout: Int,
    val slotsTotal: Int,
    val slotFillCaption: String,
    val draftMessage: String,
    val audienceChips: List<AudienceChipContent>,
    val selectedAudienceId: String,
    val pushToPhones: Boolean,
    val organizeRows: List<OrganizeRowContent>,
    val closeRow: OrganizeRowContent,
    val close: CloseTrainSheetContent,
)

/** Drives the Close-train confirmation sheet presentation. */
enum class ManageTrainSheetMode { HIDDEN, CLOSING, CLOSED }

/**
 * Wire-format UI state: the content frame + the editable draft + the
 * sheet + the transient toast. Mirrors the iOS `ManageTrainViewModel`
 * surface so parity tests can compare projections directly.
 */
data class ManageTrainUiState(
    val state: ManageTrainState = ManageTrainState.Loading,
    val draftMessage: String = "",
    val selectedAudienceId: String = "",
    val pushToPhones: Boolean = true,
    val thankYouNote: String = "",
    val sheetMode: ManageTrainSheetMode = ManageTrainSheetMode.HIDDEN,
    val toast: String? = null,
) {
    val characterCount: Int get() = draftMessage.length
    val characterCounterLabel: String get() = "$characterCount / $MAX_MESSAGE_CHARS"

    /**
     * True when the draft message has at least one non-whitespace
     * character and is under the cap. Mirrors the design's
     * `Send update` enable rule.
     */
    val canSendUpdate: Boolean
        get() =
            draftMessage.trim().isNotEmpty() &&
                draftMessage.length <= MAX_MESSAGE_CHARS

    companion object {
        const val MAX_MESSAGE_CHARS: Int = 500
    }
}

/**
 * P4.3 — A13.13 — Manage train ViewModel.
 *
 * `load()` fetches `GET /api/support-trains/:id` and derives the organizer
 * dashboard ([ManageTrainProjection]); pass a `seed` to render offline
 * (previews / tests). `sendUpdate` posts to `POST /:id/updates`;
 * `confirmClose` marks the train completed via `POST /:id/complete`
 * (sending the optional thank-you note as a final broadcast first). Both
 * mutate local state optimistically so the toast / chip flip stay instant.
 *
 * PROJECTION GAPS (no backend field): audience segmentation + push-to-phones
 * stay client-only (the endpoint broadcasts to everyone); dropout shows 0;
 * there is no single "close with thanks" route.
 */
@HiltViewModel
class ManageTrainViewModel
    @Inject
    constructor(
        private val repo: SupportTrainsRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val trainId: String =
            savedStateHandle.get<String>(TRAIN_ID_KEY).orEmpty()

        private val _state = MutableStateFlow(ManageTrainUiState())
        val state: StateFlow<ManageTrainUiState> = _state.asStateFlow()

        /**
         * Load the dashboard. With a `seed` (previews / tests) it renders
         * directly; otherwise it fetches `GET /:id` and projects it.
         */
        fun load(seed: ManageTrainContent? = null) {
            if (seed != null) {
                applyContent(seed)
                return
            }
            _state.update { ManageTrainUiState(state = ManageTrainState.Loading) }
            viewModelScope.launch {
                when (val result = repo.detail(trainId)) {
                    is NetworkResult.Success ->
                        applyContent(ManageTrainProjection.project(result.data))
                    is NetworkResult.Failure ->
                        _state.update { it.copy(state = ManageTrainState.Error(result.error.message)) }
                }
            }
        }

        private fun applyContent(content: ManageTrainContent) {
            _state.update {
                ManageTrainUiState(
                    state = ManageTrainState.Loaded(content),
                    draftMessage = content.draftMessage,
                    selectedAudienceId = content.selectedAudienceId,
                    pushToPhones = content.pushToPhones,
                    thankYouNote = "",
                    sheetMode = ManageTrainSheetMode.HIDDEN,
                    toast = null,
                )
            }
        }

        // MARK: - Send-update form

        fun updateDraftMessage(value: String) {
            // Hard-clip to the cap so the counter never displays over-limit.
            val clamped =
                if (value.length > ManageTrainUiState.MAX_MESSAGE_CHARS) {
                    value.substring(0, ManageTrainUiState.MAX_MESSAGE_CHARS)
                } else {
                    value
                }
            _state.update { it.copy(draftMessage = clamped) }
        }

        fun selectAudience(id: String) {
            val current = _state.value
            val content = (current.state as? ManageTrainState.Loaded)?.content ?: return
            if (content.audienceChips.none { it.id == id }) return
            _state.update { it.copy(selectedAudienceId = id) }
        }

        fun togglePush(value: Boolean) {
            _state.update { it.copy(pushToPhones = value) }
        }

        /**
         * Send the typed update via `POST /api/support-trains/:id/updates`.
         * Optimistically clears the draft + flashes the toast; the audience
         * filter + push-to-phones toggle have no backend field (the endpoint
         * broadcasts to everyone) so they stay client-only.
         */
        fun sendUpdate() {
            val current = _state.value
            if (!current.canSendUpdate) return
            val content = (current.state as? ManageTrainState.Loaded)?.content ?: return
            val body = current.draftMessage
            val helperCount =
                content.audienceChips.firstOrNull { it.id == current.selectedAudienceId }?.count
                    ?: content.helpersValue
            _state.update {
                it.copy(
                    draftMessage = "",
                    toast = "Update sent · $helperCount helpers",
                )
            }
            viewModelScope.launch {
                repo.postUpdate(trainId, SupportTrainUpdateBody(body = body))
            }
        }

        fun acknowledgeToast() {
            _state.update { it.copy(toast = null) }
        }

        // MARK: - Close-train sheet

        fun showCloseSheet() {
            _state.update { it.copy(sheetMode = ManageTrainSheetMode.CLOSING) }
        }

        fun hideCloseSheet() {
            _state.update { it.copy(sheetMode = ManageTrainSheetMode.HIDDEN) }
        }

        fun updateThankYouNote(value: String) {
            _state.update { it.copy(thankYouNote = value) }
        }

        /**
         * Close & thank. Optimistically flips the train to `closed`, then
         * sends the thank-you note as a final broadcast (`POST /:id/updates`)
         * when one was typed and marks the train completed
         * (`POST /:id/complete`). The backend has no single "close with
         * thanks" route, so this composes the two calls.
         */
        fun confirmClose() {
            val current = _state.value
            val content = (current.state as? ManageTrainState.Loaded)?.content ?: return
            val note = current.thankYouNote.trim()
            val next = content.copy(isActive = false)
            _state.update {
                it.copy(
                    state = ManageTrainState.Loaded(next),
                    sheetMode = ManageTrainSheetMode.CLOSED,
                    toast = "Train closed · thanks sent to ${content.helpersValue} helpers",
                )
            }
            viewModelScope.launch {
                if (note.isNotEmpty()) {
                    repo.postUpdate(trainId, SupportTrainUpdateBody(body = note))
                }
                repo.complete(trainId)
            }
        }

        companion object {
            /** Nav-arg key for the train id. Keep in sync with [TRAIN_ID_KEY] in `ChildRoutes`. */
            const val TRAIN_ID_KEY: String = "supportTrainId"
        }
    }
