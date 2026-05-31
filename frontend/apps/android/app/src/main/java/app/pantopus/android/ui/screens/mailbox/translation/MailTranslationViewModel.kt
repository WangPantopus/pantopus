@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.mailbox.translation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A17.13 — Translation view-model. Mirror of iOS `MailTranslationViewModel`.
 * Drives the four DoD states off the sample letter, owns the machine →
 * confirmed transition (optimistic, rolls back on failure), the
 * [TranslationViewToggle] selection, and the stubbed "Listen" affordance.
 *
 * Translation / TTS are sample-driven (B2.3 out-of-scope) — the confirm
 * call hits the real translate endpoint so the wiring exists, but a failure
 * simply rolls the optimistic flip back.
 */
@HiltViewModel
class MailTranslationViewModel
    @Inject
    constructor(
        private val repo: MailboxRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val mailId: String = savedStateHandle.get<String>(TRANSLATION_MAIL_ID_KEY).orEmpty()

        /**
         * Test/preview seam — seed the confirmed frame. Mirrors iOS's
         * `seedConfirmed`. Not read from nav today; the screen always lands
         * in the machine state and transitions on confirm.
         */
        private var seedConfirmed: Boolean = false

        private val _state = MutableStateFlow<MailTranslationUiState>(MailTranslationUiState.Loading)
        val state: StateFlow<MailTranslationUiState> = _state.asStateFlow()

        private val _toast = MutableStateFlow<String?>(null)
        val toast: StateFlow<String?> = _toast.asStateFlow()

        private val _confirmInFlight = MutableStateFlow(false)
        val confirmInFlight: StateFlow<Boolean> = _confirmInFlight.asStateFlow()

        /**
         * Load the (sample) translation. Real MT lands behind this seam
         * later; today the projection is deterministic so previews +
         * snapshots are stable.
         */
        fun load() {
            _state.value = MailTranslationUiState.Loading
            val content =
                if (seedConfirmed) {
                    MailTranslationSampleData.confirmedLetter(mailId.ifEmpty { "mail-translation-sample" })
                } else {
                    MailTranslationSampleData.letter(mailId.ifEmpty { "mail-translation-sample" })
                }
            _state.value = MailTranslationUiState.Loaded(content)
        }

        fun refresh() = load()

        fun consumeToast() {
            _toast.value = null
        }

        /** Test/preview seam — set the confirmed seed before [load]. */
        fun setSeedConfirmed(value: Boolean) {
            seedConfirmed = value
        }

        /** Switch the body the toggle renders. */
        fun selectViewMode(mode: TranslationViewMode) {
            val current = _state.value
            if (current is MailTranslationUiState.Loaded && current.content.viewMode != mode) {
                _state.value = MailTranslationUiState.Loaded(current.content.copy(viewMode = mode))
            }
        }

        /**
         * Confirm the machine translation. Optimistically flips to the
         * confirmed state (banner + reading view + reply CTA); rolls back
         * and toasts on failure.
         */
        fun confirmTranslation() {
            val current = _state.value
            if (current !is MailTranslationUiState.Loaded) return
            if (current.content.confirmed || _confirmInFlight.value) return
            val previous = current.content
            _confirmInFlight.value = true
            _state.value =
                MailTranslationUiState.Loaded(
                    previous.copy(confirmed = true, viewMode = TranslationViewMode.Translated),
                )
            viewModelScope.launch {
                // The translate endpoint doubles as the "confirm/trust" write
                // until a dedicated confirm route ships. The optimistic
                // projection is the source of truth for the UI.
                when (repo.translate(mailId)) {
                    is NetworkResult.Success -> _toast.value = "Translation confirmed"
                    is NetworkResult.Failure -> {
                        _state.value = MailTranslationUiState.Loaded(previous)
                        _toast.value = "Couldn't confirm — try again"
                    }
                }
                _confirmInFlight.value = false
            }
        }

        /**
         * Stubbed text-to-speech affordance. Real audio is out of scope
         * (B2.3); this surfaces a toast so the control is never a dead tap.
         */
        fun listen(which: TranslationListenColumn) {
            _toast.value =
                when (which) {
                    TranslationListenColumn.Original -> "Playing the original aloud…"
                    TranslationListenColumn.Translated -> "Playing the translation aloud…"
                }
        }

        /** One-off toast for the stubbed overflow / chip affordances. */
        fun showToast(message: String) {
            _toast.value = message
        }
    }
