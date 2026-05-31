@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.mailbox.earn

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * A10.11 — backs the Earn dashboard. There's no payout / earnings backend
 * wired yet (this batch ships the visual surface; the live network swap
 * lands with the Stripe Connect integration), so the VM is seeded with
 * deterministic [EarnSampleData]. A non-null `content` selects the
 * active-earner frame; a null `content` selects the empty new-earner
 * frame (no hero, gated rows, add-payout nudge). State machine matches
 * the doc's four-state rule: loading / populated / empty / error.
 */
@HiltViewModel
class EarnViewModel
    @Inject
    constructor() : ViewModel() {
        /** Non-null → active earner (populated); null → new earner (empty). */
        private var content: EarnContent? = EarnSampleData.populated

        private val _state = MutableStateFlow<EarnUiState>(EarnUiState.Loading)
        val state: StateFlow<EarnUiState> = _state.asStateFlow()

        fun load() {
            _state.value =
                content?.let { EarnUiState.Populated(it) }
                    ?: EarnUiState.Empty(EarnSampleData.waysToEarn)
        }

        fun refresh() = load()

        /** Test/preview seam — null selects the empty new-earner frame. */
        fun setFixture(content: EarnContent?) {
            this.content = content
        }
    }
