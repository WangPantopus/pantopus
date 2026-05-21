@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.audience_profile.compose_broadcast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Nav-arg key for the persona id read off the back-stack handle. */
const val COMPOSE_BROADCAST_PERSONA_ID_KEY = "personaId"

/**
 * A.7 (A22.2) — Backs the full-screen Compose Broadcast surface. The
 * editor fields (body / audience / media / schedule) plus the send
 * [ComposePhase] live in a single [ComposeBroadcastUiState]; the prompt's
 * five-case contract is derived via [ComposeBroadcastUiState.composeState].
 *
 * No backend: [performSend] defaults to a no-op success and can be swapped
 * by tests (latency / failure). Persona + reach + recent broadcasts are
 * seeded from [ComposeBroadcastSampleData].
 */
@HiltViewModel
class ComposeBroadcastViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val personaId: String =
            savedStateHandle.get<String>(COMPOSE_BROADCAST_PERSONA_ID_KEY)
                ?.takeIf { it.isNotBlank() }
                ?: ComposeBroadcastSampleData.persona.id

        private var lastSavedDraft = ComposeBroadcastDraft()

        private val _state =
            MutableStateFlow(
                ComposeBroadcastUiState(
                    persona = ComposeBroadcastSampleData.persona.copy(id = personaId),
                    recentBroadcasts = ComposeBroadcastSampleData.recentBroadcasts,
                    draft = ComposeBroadcastDraft(),
                    scheduledAtMillis = null,
                    scheduledLabel = null,
                    phase = ComposePhase.Idle,
                    isDirty = false,
                    audienceReach = ComposeBroadcastSampleData.audienceReach,
                ),
            )
        val state: StateFlow<ComposeBroadcastUiState> = _state.asStateFlow()

        /** Stubbed network call. Default immediate success; swapped by tests. */
        var performSend: suspend (ComposeBroadcastDraft, Long?) -> Unit = { _, _ -> }

        fun updateBody(text: String) = mutateDraft { it.copy(body = text) }

        fun setAudience(audience: BroadcastAudience) = mutateDraft { it.copy(audience = audience) }

        fun attachMedia(media: ComposeMediaPreview) = mutateDraft { it.copy(media = media) }

        fun removeMedia() = mutateDraft { it.copy(media = null) }

        fun schedule(atMillis: Long) {
            update {
                it.copy(scheduledAtMillis = atMillis, scheduledLabel = formatSchedule(atMillis))
            }
        }

        fun sendNow() {
            update { it.copy(scheduledAtMillis = null, scheduledLabel = null) }
        }

        fun saveDraft() {
            lastSavedDraft = _state.value.draft
            _state.update { it.copy(isDirty = false) }
        }

        fun retry() {
            if (_state.value.phase is ComposePhase.Error) {
                _state.update { it.copy(phase = ComposePhase.Idle) }
            }
        }

        fun send(onSent: () -> Unit = {}) {
            val current = _state.value
            if (!current.canSend) return
            val snapshot = current.draft
            val at = current.scheduledAtMillis
            _state.update { it.copy(phase = ComposePhase.Sending) }
            viewModelScope.launch {
                try {
                    performSend(snapshot, at)
                    lastSavedDraft = ComposeBroadcastDraft(audience = snapshot.audience)
                    _state.update {
                        it.copy(
                            draft = lastSavedDraft,
                            scheduledAtMillis = null,
                            scheduledLabel = null,
                            phase = ComposePhase.Idle,
                            isDirty = false,
                        )
                    }
                    onSent()
                } catch (error: Throwable) {
                    _state.update {
                        it.copy(phase = ComposePhase.Error(error.message ?: "Couldn't send broadcast. Try again."))
                    }
                }
            }
        }

        private fun mutateDraft(transform: (ComposeBroadcastDraft) -> ComposeBroadcastDraft) {
            update { it.copy(draft = transform(it.draft)) }
        }

        /** Apply a state change, clearing any prior send error and recomputing dirty. */
        private fun update(transform: (ComposeBroadcastUiState) -> ComposeBroadcastUiState) {
            _state.update { current ->
                val next = transform(current)
                val phase = if (next.phase is ComposePhase.Error) ComposePhase.Idle else next.phase
                next.copy(
                    phase = phase,
                    isDirty = !next.draft.isEmpty && next.draft != lastSavedDraft,
                )
            }
        }

        private fun formatSchedule(millis: Long): String =
            SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(millis))
    }
