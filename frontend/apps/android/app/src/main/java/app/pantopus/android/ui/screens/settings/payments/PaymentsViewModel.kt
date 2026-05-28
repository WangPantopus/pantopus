@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.settings.payments

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Which sample frame to load. The screen is fixture-driven for P5.2
 * (live Stripe Connect isn't wired yet); flipping the seed swaps in
 * the empty-account fixture so the disabled-state chrome stays
 * exercised at preview/snapshot time.
 */
enum class PaymentsSeed {
    Populated,
    Empty,
}

/**
 * Projects the A14.6 Payments screen into render state. Stripe
 * Connect onboarding and the card-add bottom sheet are out of scope
 * for P5.2 — the row taps are no-ops today, the view stays typed for
 * the follow-up wiring.
 */
@HiltViewModel
class PaymentsViewModel
    @Inject
    constructor() : ViewModel() {
        val title: String = "Payments"

        private val _state = MutableStateFlow<PaymentsUiState>(PaymentsUiState.Loading)
        val state: StateFlow<PaymentsUiState> = _state.asStateFlow()

        private var seed: PaymentsSeed = PaymentsSeed.Populated

        /** Override the active seed before [load] runs. */
        fun seed(seed: PaymentsSeed) {
            this.seed = seed
        }

        fun load() {
            _state.value = PaymentsUiState.Loading
            val loaded =
                when (seed) {
                    PaymentsSeed.Populated -> PaymentsSampleData.populated
                    PaymentsSeed.Empty -> PaymentsSampleData.empty
                }
            _state.value = PaymentsUiState.Loaded(loaded)
        }

        fun refresh() {
            load()
        }

        /**
         * Row taps are no-ops today — Stripe Connect deep-link and the
         * card-add bottom sheet are flagged out of scope (P5.2 OUT OF
         * SCOPE). The hook stays so the screen can stay typed.
         */
        fun tapRow(
            @Suppress("UNUSED_PARAMETER") id: String,
        ) {}

        fun tapAddMethod() {}

        fun tapCloseAccount() {}
    }
