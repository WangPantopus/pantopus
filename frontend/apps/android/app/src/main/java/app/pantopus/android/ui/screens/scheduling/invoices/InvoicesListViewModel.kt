@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.scheduling.invoices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.scheduling.InvoiceDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.scheduling.SchedulingError
import app.pantopus.android.data.scheduling.SchedulingErrorDecoder
import app.pantopus.android.data.scheduling.SchedulingFeatureFlags
import app.pantopus.android.data.scheduling.SchedulingOwner
import app.pantopus.android.data.scheduling.SchedulingRepository
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingRoutes
import app.pantopus.android.ui.screens.scheduling._shared.pillar
import app.pantopus.android.ui.screens.scheduling.packages.PackagesMoney
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Status filter chips (`invoiceslist-frames.jsx` `FilterChips`). The DTO carries
 * no `status`, so selecting a status chip cannot yet filter the list (deferred);
 * `All` is the only chip with data behind it. The chip row + selection render
 * now so the structure matches the design.
 */
enum class InvoiceFilter(val label: String) {
    All("All"),
    Paid("Paid"),
    Sent("Sent"),
    Overdue("Overdue"),
    Refunded("Refunded"),
}

/** G12 Invoices List UI state. */
sealed interface InvoicesListUiState {
    data object Loading : InvoicesListUiState

    data object ComingSoon : InvoicesListUiState

    /** Stripe not connected → "Connect payments to invoice" gate. */
    data object Gate : InvoicesListUiState

    data object Empty : InvoicesListUiState

    data class Error(val message: String) : InvoicesListUiState

    data class Loaded(
        val sections: List<InvoiceDaySection>,
        val totalLabel: String,
        val countLabel: String,
        val pillar: SchedulingPillar,
    ) : InvoicesListUiState
}

/**
 * G12 Invoices List (owner, business-only) — Stream A15. Lists `GET /invoices`
 * grouped by created day, with the Stripe-not-connected gate from
 * `GET /payments/status`. Behind [SchedulingFeatureFlags]. Mirrors iOS
 * `InvoicesListViewModel` / `invoiceslist-frames.jsx` (within the minimal
 * InvoiceDto — no status pills / status filters with data / overdue summary).
 */
@HiltViewModel
class InvoicesListViewModel
    @Inject
    constructor(
        private val repo: SchedulingRepository,
        private val auth: AuthRepository,
        private val errors: SchedulingErrorDecoder,
        private val flags: SchedulingFeatureFlags,
    ) : ViewModel() {
        private val _state = MutableStateFlow<InvoicesListUiState>(InvoicesListUiState.Loading)
        val state: StateFlow<InvoicesListUiState> = _state.asStateFlow()

        private val _filter = MutableStateFlow(InvoiceFilter.All)
        val filter: StateFlow<InvoiceFilter> = _filter.asStateFlow()

        private var owner: SchedulingOwner = SchedulingOwner.Personal
        private var started = false

        fun start() {
            if (started) {
                refresh()
            } else {
                started = true
                owner = resolveOwner()
                load()
            }
        }

        fun load() {
            viewModelScope.launch {
                if (!flags.paidSchedulingEnabled) {
                    _state.value = InvoicesListUiState.ComingSoon
                    return@launch
                }
                _state.value = InvoicesListUiState.Loading
                val status = (repo.getPaymentsStatus(owner) as? NetworkResult.Success)?.data
                val connected = status?.connected ?: false
                val applicable = status?.applicable ?: true
                when (val result = repo.getInvoices(owner)) {
                    is NetworkResult.Success -> {
                        val invoices = result.data.invoices
                        _state.value =
                            when {
                                invoices.isNotEmpty() ->
                                    InvoicesListUiState.Loaded(
                                        sections = InvoiceGrouping.byDay(invoices),
                                        totalLabel =
                                            PackagesMoney.format(
                                                invoices.sumOf {
                                                    it.totalCents ?: 0
                                                },
                                                invoices.firstOrNull()?.currency,
                                            ),
                                        countLabel = invoices.size.toString(),
                                        pillar = owner.pillar(),
                                    )
                                applicable && !connected -> InvoicesListUiState.Gate
                                else -> InvoicesListUiState.Empty
                            }
                    }
                    is NetworkResult.Failure ->
                        _state.value =
                            InvoicesListUiState.Error(
                                errors.decode(result.error).message(),
                            )
                }
            }
        }

        fun refresh() = load()

        fun selectFilter(target: InvoiceFilter) {
            _filter.value = target
        }

        fun invoiceRoute(invoiceId: String): String = SchedulingRoutes.invoiceDetail(invoiceId)

        fun connectRoute(): String = SchedulingRoutes.PAYMENTS_SETUP

        // ─── Row formatting ─────────────────────────────────────────────────────

        fun amount(invoice: InvoiceDto): String = PackagesMoney.format(invoice.totalCents, invoice.currency)

        /** Short monospace reference from the invoice id (no invoice_number in DTO). */
        fun reference(invoice: InvoiceDto): String = "INV-" + invoice.id.take(REF_PREFIX_LEN).uppercase()

        /** Service sub-label — first parsed line item, else "Service". */
        fun service(invoice: InvoiceDto): String = InvoiceParsing.lineItems(invoice.lineItems).firstOrNull()?.label ?: "Service"

        /** Two-letter payer initials (no payer display name in the DTO). */
        fun payerInitials(invoice: InvoiceDto): String {
            val token = (invoice.recipientUserId ?: invoice.id).filter { it.isLetter() }.take(INITIALS_LEN).uppercase()
            return token.ifEmpty { "IN" }
        }

        private fun resolveOwner(): SchedulingOwner =
            (auth.state.value as? AuthRepository.State.SignedIn)?.user?.id
                ?.let { SchedulingOwner.Business(it) }
                ?: SchedulingOwner.Personal

        private fun SchedulingError.message(): String =
            when (this) {
                is SchedulingError.Secret -> "Only the business owner can view invoices."
                is SchedulingError.Generic -> message
                else -> "Couldn't load invoices."
            }

        private companion object {
            const val REF_PREFIX_LEN = 6
            const val INITIALS_LEN = 2
        }
    }
