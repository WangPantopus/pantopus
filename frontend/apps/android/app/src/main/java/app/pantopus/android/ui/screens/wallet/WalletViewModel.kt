@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.wallet

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * A10.10 — backs the Wallet screen. There's no Stripe Connect backend
 * wired yet (P3.2 ships the visual surface; the live network swap
 * lands with the Connect integration), so the VM is seeded with
 * deterministic [WalletSampleData] and `content.isOnHold` selects
 * populated vs. hold. State machine matches the doc's four-state
 * rule: loading / populated / hold / error.
 */
@HiltViewModel
class WalletViewModel
    @Inject
    constructor() : ViewModel() {
        private var content: WalletContent = WalletSampleData.populated

        private val _state = MutableStateFlow<WalletUiState>(WalletUiState.Loading)
        val state: StateFlow<WalletUiState> = _state.asStateFlow()

        fun load() {
            _state.value =
                if (content.isOnHold) {
                    WalletUiState.Hold(content)
                } else {
                    WalletUiState.Populated(content)
                }
        }

        fun refresh() = load()

        /** Test/preview seam — swap the stub fixture before calling [load]. */
        fun setFixture(content: WalletContent) {
            this.content = content
        }
    }
