@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.wallet.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A10.10 / P1-F — backs the Wallet screen.
 *
 * The production path hydrates the balance hero + Recent-activity feed from
 * the READ endpoints (`GET /api/wallet`, `/transactions`, `/pending-release`)
 * via [WalletRepository]. The withdraw/payout surface (payout method, tax
 * docs, Withdraw CTA) stays on its P3.2 placeholder — it's wired in Phase 3
 * with Stripe. Previews / snapshots / tests seed deterministic
 * [WalletSampleData] through [setFixture], which bypasses the network.
 */
@HiltViewModel
class WalletViewModel
    @Inject
    constructor(
        private val repository: WalletRepository,
    ) : ViewModel() {
        private var fixture: WalletContent? = null

        private val _state = MutableStateFlow<WalletUiState>(WalletUiState.Loading)
        val state: StateFlow<WalletUiState> = _state.asStateFlow()

        fun load() {
            val seed = fixture
            if (seed != null) {
                _state.value = if (seed.isOnHold) WalletUiState.Hold(seed) else WalletUiState.Populated(seed)
                return
            }
            _state.value = WalletUiState.Loading
            viewModelScope.launch {
                when (val balance = repository.balance()) {
                    is NetworkResult.Success -> {
                        // Pending-release is supplementary — its failure shouldn't
                        // sink the screen, so it degrades to nil (zero pending).
                        val transactionsResult = repository.transactions()
                        val transactions =
                            if (transactionsResult is NetworkResult.Success) {
                                transactionsResult.data.transactions
                            } else {
                                emptyList()
                            }
                        val pendingResult = repository.pendingRelease()
                        val pending =
                            if (pendingResult is NetworkResult.Success) pendingResult.data else null
                        _state.value =
                            WalletUiState.Populated(WalletMapper.build(balance.data, transactions, pending))
                    }
                    is NetworkResult.Failure -> {
                        _state.value = WalletUiState.Error(balance.error.message)
                    }
                }
            }
        }

        fun refresh() = load()

        /** Test/preview seam — swap the stub fixture before calling [load]. */
        fun setFixture(content: WalletContent) {
            this.fixture = content
        }
    }
