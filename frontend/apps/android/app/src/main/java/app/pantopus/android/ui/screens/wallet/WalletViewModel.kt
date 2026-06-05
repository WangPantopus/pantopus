@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.wallet.WalletWithdrawRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.connect.ConnectRepository
import app.pantopus.android.data.wallet.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * A10.10 / P1-F + Block 3C — backs the Wallet screen.
 *
 * P1-F hydrates the balance hero + Recent-activity feed from the READ
 * endpoints (`GET /api/wallet`, `/transactions`, `/pending-release`). Block 3C
 * adds the payout side: it reads Stripe Connect status (`GET /connect/account`)
 * to gate the Withdraw CTA, runs `POST /api/wallet/withdraw`, and drives the
 * Stripe-hosted onboarding / Express dashboard (opened by the screen). We never
 * mark anything paid client-side — the server is the source of truth and we
 * re-read on success. Previews / snapshots / tests seed deterministic
 * [WalletSampleData] through [setFixture], which bypasses the network.
 */
@HiltViewModel
class WalletViewModel
    @Inject
    constructor(
        private val repository: WalletRepository,
        private val connectRepository: ConnectRepository,
    ) : ViewModel() {
        private var fixture: WalletContent? = null

        private val _state = MutableStateFlow<WalletUiState>(WalletUiState.Loading)
        val state: StateFlow<WalletUiState> = _state.asStateFlow()

        private val _action = MutableStateFlow<WalletAction>(WalletAction.Idle)
        val action: StateFlow<WalletAction> = _action.asStateFlow()

        private val _events = MutableSharedFlow<WalletEvent>(extraBufferCapacity = 4)
        val events: SharedFlow<WalletEvent> = _events.asSharedFlow()

        /** Cached from the last live fetch: full available balance (cents) +
         *  whether the connected account can receive payouts. */
        private var availableCents = 0L
        private var payoutsEnabled = false

        fun load() = loadInternal(showLoading = true)

        fun refresh() = load()

        /** Test/preview seam — swap the stub fixture before calling [load]. */
        fun setFixture(content: WalletContent) {
            this.fixture = content
        }

        private fun loadInternal(showLoading: Boolean) {
            val seed = fixture
            if (seed != null) {
                _state.value = if (seed.isOnHold) WalletUiState.Hold(seed) else WalletUiState.Populated(seed)
                return
            }
            if (showLoading) _state.value = WalletUiState.Loading
            viewModelScope.launch {
                when (val balance = repository.balance()) {
                    is NetworkResult.Success -> {
                        // Transactions + pending-release + Connect status are
                        // supplementary — their failure degrades gracefully
                        // rather than sinking the screen.
                        val transactions =
                            (repository.transactions() as? NetworkResult.Success)?.data?.transactions ?: emptyList()
                        val pending = (repository.pendingRelease() as? NetworkResult.Success)?.data
                        val enabled =
                            (connectRepository.accountStatus() as? NetworkResult.Success)?.data?.account?.payoutsEnabled
                                ?: false
                        availableCents = balance.data.wallet.balance
                        payoutsEnabled = enabled
                        _state.value =
                            WalletUiState.Populated(WalletMapper.build(balance.data, transactions, pending, enabled))
                    }
                    is NetworkResult.Failure -> {
                        _state.value = WalletUiState.Error(balance.error.message)
                    }
                }
            }
        }

        // MARK: - Payout actions (Block 3C)

        /** Withdraw the full available balance to the seller's bank. */
        fun withdraw() {
            if (fixture != null) return
            if (!payoutsEnabled || availableCents < 100) {
                _action.value = WalletAction.WithdrawFailed("Set up payouts before withdrawing.")
                return
            }
            _action.value = WalletAction.Withdrawing
            viewModelScope.launch {
                val request = WalletWithdrawRequest(amount = availableCents, idempotencyKey = UUID.randomUUID().toString())
                when (val result = repository.withdraw(request)) {
                    is NetworkResult.Success -> {
                        _action.value = WalletAction.WithdrawSucceeded(result.data.message ?: "Withdrawal initiated.")
                        loadInternal(showLoading = false)
                    }
                    is NetworkResult.Failure -> {
                        _action.value = WalletAction.WithdrawFailed(result.error.message)
                    }
                }
            }
        }

        /** "Set up payouts" / "Re-verify" — ensure a connected account, then ask
         *  the screen to open the Stripe-hosted Account Link. */
        fun setupPayouts() {
            if (fixture != null) return
            _action.value = WalletAction.Connecting
            viewModelScope.launch {
                // Ensure the account exists; a 400 "already exists" is fine.
                connectRepository.createAccount()
                when (val link = connectRepository.onboarding()) {
                    is NetworkResult.Success -> {
                        _action.value = WalletAction.Idle
                        _events.emit(WalletEvent.OpenUrl(link.data.onboardingUrl, refreshOnReturn = true))
                    }
                    is NetworkResult.Failure -> {
                        _action.value = WalletAction.ActionFailed(link.error.message)
                    }
                }
            }
        }

        /** Open the Stripe Express dashboard for an onboarded seller. */
        fun openDashboard() {
            if (fixture != null) return
            viewModelScope.launch {
                when (val link = connectRepository.dashboard()) {
                    is NetworkResult.Success ->
                        _events.emit(WalletEvent.OpenUrl(link.data.dashboardUrl, refreshOnReturn = false))
                    is NetworkResult.Failure ->
                        _action.value = WalletAction.ActionFailed(link.error.message)
                }
            }
        }

        /** Re-read Connect status when the seller returns from hosted onboarding. */
        fun onReturnFromConnect() {
            if (fixture != null) return
            loadInternal(showLoading = false)
        }

        /** Clear the action toast once the screen has shown it. */
        fun clearAction() {
            _action.value = WalletAction.Idle
        }
    }
