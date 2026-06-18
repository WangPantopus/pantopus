@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.scheduling.invitee.edge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.scheduling.ManageBookingResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.scheduling.SchedulingFeatureFlags
import app.pantopus.android.data.scheduling.SchedulingRepository
import app.pantopus.android.ui.screens.scheduling._shared.MoneyAndFlag
import app.pantopus.android.ui.screens.settings.payments.CheckoutOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * D6 — Payment failed / retry. Drives the retry sheet from the booking's manage
 * `payment` block: a declined payment holds the slot while the invitee retries
 * the Stripe `clientSecret` (test mode, behind the paid flag), and a dropped
 * connection re-checks idempotently — we never charge twice. Settlement is
 * deferred server-side, so a succeeded retry surfaces processing/pending until
 * the webhook reconciles.
 */
@HiltViewModel
class PaymentRetryViewModel
    @Inject
    constructor(
        private val repo: SchedulingRepository,
        val flags: SchedulingFeatureFlags,
    ) : ViewModel() {
        sealed interface PaymentRetryUiState {
            data object Loading : PaymentRetryUiState

            /** Card declined / requires a new payment method — the slot is still held. */
            data class Declined(val amountLabel: String, val message: String) : PaymentRetryUiState

            /** Outcome unknown after a dropped connection — re-check, idempotent. */
            data class Timeout(val amountLabel: String) : PaymentRetryUiState

            /** The held slot was released while waiting — pick again. */
            data object HoldExpired : PaymentRetryUiState

            /** Payment captured (or processing — settlement deferred). */
            data class Succeeded(val amountLabel: String, val processing: Boolean) : PaymentRetryUiState

            data class Error(val message: String) : PaymentRetryUiState
        }

        private val _state = MutableStateFlow<PaymentRetryUiState>(PaymentRetryUiState.Loading)
        val state: StateFlow<PaymentRetryUiState> = _state.asStateFlow()

        private var loadedToken: String? = null

        fun start(manageToken: String) {
            if (loadedToken == manageToken && _state.value !is PaymentRetryUiState.Loading) return
            loadedToken = manageToken
            load(manageToken)
        }

        fun load(manageToken: String) {
            viewModelScope.launch {
                _state.value = PaymentRetryUiState.Loading
                _state.value = mapState(repo.publicGetManageBooking(manageToken))
            }
        }

        /** "Check again" after a timeout — idempotent re-read of the payment state. */
        fun recheck(manageToken: String) = load(manageToken)

        fun onCheckoutOutcome(
            outcome: CheckoutOutcome,
            manageToken: String,
        ) {
            when (outcome) {
                is CheckoutOutcome.Paid -> load(manageToken) // re-read; webhook reconciles
                is CheckoutOutcome.Canceled -> Unit // stay on the retry sheet
                is CheckoutOutcome.Declined ->
                    _state.value =
                        PaymentRetryUiState.Declined(
                            amountLabel = (_state.value as? PaymentRetryUiState.Declined)?.amountLabel.orEmpty(),
                            message = outcome.message ?: "Your card was declined. Nothing was charged.",
                        )
            }
        }

        private fun mapState(result: NetworkResult<ManageBookingResponse>): PaymentRetryUiState =
            when (result) {
                is NetworkResult.Success -> mapBooking(result.data)
                is NetworkResult.Failure -> PaymentRetryUiState.Error("Couldn't check your payment. Try again.")
            }

        private fun mapBooking(data: ManageBookingResponse): PaymentRetryUiState {
            val status = data.booking.status?.lowercase()
            if (status in TERMINAL_STATUSES) return PaymentRetryUiState.HoldExpired
            val payment = data.payment
            val amountLabel = MoneyAndFlag.formatPrice(payment?.amountTotal, payment?.currency)
            return when (payment?.paymentStatus?.lowercase()) {
                "succeeded", "paid" -> PaymentRetryUiState.Succeeded(amountLabel, processing = false)
                "processing" -> PaymentRetryUiState.Succeeded(amountLabel, processing = true)
                "requires_action", null -> PaymentRetryUiState.Timeout(amountLabel)
                else ->
                    PaymentRetryUiState.Declined(
                        amountLabel = amountLabel,
                        message = "Your card was declined. Nothing was charged.",
                    )
            }
        }

        private companion object {
            val TERMINAL_STATUSES = setOf("cancelled", "canceled", "declined", "expired", "no_show")
        }
    }
