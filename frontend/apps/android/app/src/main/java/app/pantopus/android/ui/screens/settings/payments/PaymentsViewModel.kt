@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.settings.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.payments.PaymentsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Which sample frame to load. Selecting a seed puts the view-model in
 * fixture mode (previews / snapshots) — `load()` projects the static
 * fixture and the repository / PaymentSheet are never touched. Live mode
 * (no seed) fetches from `/api/payments`.
 */
enum class PaymentsSeed {
    Populated,
    Empty,
}

/**
 * Projects the A14.6 Payments screen into render state. Phase 3 (3A) wires
 * the Payment-methods card to the real backend: list saved methods, add a
 * card via Stripe PaymentSheet, set-default and remove (optimistic). The
 * balance hero / Payouts (Stripe Connect) / Activity sections render an
 * honest "not set up yet" scaffold in the live frame — wired in 3C.
 */
@HiltViewModel
class PaymentsViewModel
    @Inject
    constructor(
        private val repository: PaymentsRepository,
    ) : ViewModel() {
        val title: String = "Payments"

        private val _state = MutableStateFlow<PaymentsUiState>(PaymentsUiState.Loading)
        val state: StateFlow<PaymentsUiState> = _state.asStateFlow()

        private val _events = MutableSharedFlow<PaymentsEvent>(extraBufferCapacity = 4)
        val events: SharedFlow<PaymentsEvent> = _events.asSharedFlow()

        /** Non-null → fixture mode (previews / snapshots / projection tests). */
        private var fixtureSeed: PaymentsSeed? = null

        /** Override the active seed before [load] runs (fixture mode). */
        fun seed(seed: PaymentsSeed) {
            fixtureSeed = seed
        }

        fun load() {
            val seed = fixtureSeed
            if (seed != null) {
                _state.value =
                    PaymentsUiState.Loaded(
                        when (seed) {
                            PaymentsSeed.Populated -> PaymentsSampleData.populated
                            PaymentsSeed.Empty -> PaymentsSampleData.empty
                        },
                    )
                return
            }
            _state.value = PaymentsUiState.Loading
            viewModelScope.launch {
                when (val result = repository.paymentMethods()) {
                    is NetworkResult.Success ->
                        _state.value =
                            PaymentsUiState.Loaded(
                                PaymentsMapper.liveFrame(result.data.paymentMethods.map(PaymentsMapper::toUiMethod)),
                            )
                    is NetworkResult.Failure ->
                        _state.value = PaymentsUiState.Error(result.error.message)
                }
            }
        }

        fun refresh() {
            load()
        }

        // MARK: - Add a card (Stripe PaymentSheet, SetupIntent)

        fun tapAddMethod() {
            if (fixtureSeed != null) return
            viewModelScope.launch {
                when (val result = repository.addCardSheetParams()) {
                    is NetworkResult.Success -> _events.emit(PaymentsEvent.PresentAddCardSheet(result.data))
                    is NetworkResult.Failure -> _events.emit(PaymentsEvent.ShowMessage(result.error.message))
                }
            }
        }

        fun onAddCardOutcome(outcome: AddCardOutcome) {
            when (outcome) {
                AddCardOutcome.Completed ->
                    // The attached card is reconciled into the backend by the
                    // `payment_method.attached` webhook; re-read server state.
                    viewModelScope.launch { reloadMethods() }
                AddCardOutcome.Canceled -> Unit
                is AddCardOutcome.Failed ->
                    viewModelScope.launch {
                        _events.emit(PaymentsEvent.ShowMessage(outcome.message ?: "Couldn't add that card."))
                    }
            }
        }

        // MARK: - Set default / remove (optimistic, then reconcile)

        fun setDefault(id: String) {
            val loaded = (_state.value as? PaymentsUiState.Loaded)?.content ?: return
            _state.value = PaymentsUiState.Loaded(loaded.markingDefault(id))
            viewModelScope.launch {
                when (repository.setDefault(id)) {
                    is NetworkResult.Success -> reloadMethods()
                    is NetworkResult.Failure -> {
                        _state.value = PaymentsUiState.Loaded(loaded)
                        _events.emit(PaymentsEvent.ShowMessage("Couldn't update your default payment method."))
                    }
                }
            }
        }

        fun removeMethod(id: String) {
            val loaded = (_state.value as? PaymentsUiState.Loaded)?.content ?: return
            _state.value = PaymentsUiState.Loaded(loaded.removingMethod(id))
            viewModelScope.launch {
                when (repository.removeMethod(id)) {
                    is NetworkResult.Success -> reloadMethods()
                    is NetworkResult.Failure -> {
                        _state.value = PaymentsUiState.Loaded(loaded)
                        _events.emit(PaymentsEvent.ShowMessage("Couldn't remove that payment method."))
                    }
                }
            }
        }

        /** Stripe Connect / payout routing / destructive close land with 3C. */
        fun tapRow(
            @Suppress("UNUSED_PARAMETER") id: String,
        ) = Unit

        fun tapCloseAccount() = Unit

        private suspend fun reloadMethods() {
            when (val result = repository.paymentMethods()) {
                is NetworkResult.Success -> {
                    val methods = result.data.paymentMethods.map(PaymentsMapper::toUiMethod)
                    val current = (_state.value as? PaymentsUiState.Loaded)?.content
                    _state.value =
                        PaymentsUiState.Loaded(current?.copy(methods = methods) ?: PaymentsMapper.liveFrame(methods))
                }
                is NetworkResult.Failure ->
                    _events.emit(PaymentsEvent.ShowMessage(result.error.message))
            }
        }
    }
