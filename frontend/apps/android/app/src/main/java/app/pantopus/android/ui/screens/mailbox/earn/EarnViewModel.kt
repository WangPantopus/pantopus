@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.mailbox.earn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.mailbox.v2.EarnBalanceDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * A10.11 — backs the Earn dashboard.
 *
 * `load()` fetches the live cleared / pending payout sums from
 * `GET /api/mailbox/v2/earn/balance` and renders them in the balance hero.
 * A non-zero balance selects the active-earner (populated) frame; an
 * all-zero balance selects the empty new-earner frame.
 *
 * Only the balance is wired today. The rest of the populated frame — weekly
 * goal, the earnings ledger, payout method, auto-cash-out, tax docs — has no
 * backend source yet (it lands with the Phase-3 Stripe Connect integration),
 * so those slots keep the designed [EarnSampleData] placeholder. `waysToEarn`
 * stays a static launcher: the `/earn/offers` feed is an advertiser-coupon
 * model, not these three rows, so it is intentionally not mapped here.
 */
@HiltViewModel
class EarnViewModel
    @Inject
    constructor(
        private val repository: MailboxRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<EarnUiState>(EarnUiState.Loading)
        val state: StateFlow<EarnUiState> = _state.asStateFlow()

        fun load() {
            if (_state.value is EarnUiState.Populated || _state.value is EarnUiState.Empty) return
            fetch()
        }

        fun refresh() = fetch()

        private fun fetch() {
            _state.value = EarnUiState.Loading
            viewModelScope.launch {
                _state.value =
                    when (val result = repository.earnBalance()) {
                        is NetworkResult.Success -> project(result.data.balance)
                        is NetworkResult.Failure -> EarnUiState.Error(result.error.message)
                    }
            }
        }

        /**
         * Map the live balance onto a render frame. All-zero balance → the
         * new-earner empty frame; otherwise the populated frame with the live
         * `available` / `pending` sums spliced over the placeholder.
         */
        private fun project(balance: EarnBalanceDto): EarnUiState {
            val isNewEarner =
                balance.total <= 0.0 && balance.available <= 0.0 && balance.pending <= 0.0
            if (isNewEarner) return EarnUiState.Empty(EarnSampleData.waysToEarn)
            return EarnUiState.Populated(
                EarnSampleData.populated.copy(
                    available = formatAmount(balance.available),
                    pending = formatDollars(balance.pending),
                ),
            )
        }

        /** Hero balance — bare two-decimal string (the `$` is drawn separately). */
        private fun formatAmount(value: Double): String = String.format(Locale.US, "%.2f", value)

        /** Stat strings carry their own `$` prefix (e.g. `"$60.00"`). */
        private fun formatDollars(value: Double): String = String.format(Locale.US, "\$%.2f", value)
    }
