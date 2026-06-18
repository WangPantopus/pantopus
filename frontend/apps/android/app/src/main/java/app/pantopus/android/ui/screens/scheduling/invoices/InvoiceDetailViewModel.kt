@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.scheduling.invoices

import androidx.lifecycle.SavedStateHandle
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
import app.pantopus.android.ui.screens.scheduling.packages.PackagesFormat
import app.pantopus.android.ui.screens.scheduling.packages.PackagesMoney
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** G13 Invoice Detail UI state. */
sealed interface InvoiceDetailUiState {
    data object Loading : InvoiceDetailUiState

    data object ComingSoon : InvoiceDetailUiState

    data class Error(val message: String) : InvoiceDetailUiState

    data class Loaded(
        val reference: String,
        val issuedLabel: String,
        val totalLabel: String,
        val currencyCode: String,
        val recipientLabel: String,
        val shareText: String,
        val lineItems: List<InvoiceLineItem>,
        val unitLabels: Map<Int, String>,
        val lineTotalLabels: Map<Int, String>,
        val pillar: SchedulingPillar,
    ) : InvoiceDetailUiState
}

/**
 * G13 Invoice Detail (owner) — Stream A15. Renders `GET /invoices/:id` and the
 * owner "send" action (`POST /invoices/:id/send`). Behind [SchedulingFeatureFlags].
 * Mirrors iOS `InvoiceDetailViewModel` / `invoicedetail-frames.jsx` within the
 * InvoiceDto's fields (no status / timeline / due date / memo / payer name →
 * those design sections are omitted).
 */
@HiltViewModel
class InvoiceDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repo: SchedulingRepository,
        private val auth: AuthRepository,
        private val errors: SchedulingErrorDecoder,
        private val flags: SchedulingFeatureFlags,
    ) : ViewModel() {
        private val invoiceId: String =
            savedStateHandle.get<String>(
                SchedulingRoutes.ARG_INVOICE_ID,
            ).orEmpty()

        private val _state = MutableStateFlow<InvoiceDetailUiState>(InvoiceDetailUiState.Loading)
        val state: StateFlow<InvoiceDetailUiState> = _state.asStateFlow()

        private val _sending = MutableStateFlow(false)
        val sending: StateFlow<Boolean> = _sending.asStateFlow()

        private val _sentToast = MutableStateFlow(false)
        val sentToast: StateFlow<Boolean> = _sentToast.asStateFlow()

        private var owner: SchedulingOwner = SchedulingOwner.Personal
        private var shareText: String = ""
        private var started = false

        fun start() {
            if (started) return
            started = true
            owner = resolveOwner()
            load()
        }

        fun load() {
            viewModelScope.launch {
                if (!flags.paidSchedulingEnabled) {
                    _state.value = InvoiceDetailUiState.ComingSoon
                    return@launch
                }
                _state.value = InvoiceDetailUiState.Loading
                when (val result = repo.getInvoice(owner, invoiceId)) {
                    is NetworkResult.Success -> _state.value = build(result.data.invoice)
                    is NetworkResult.Failure ->
                        _state.value =
                            InvoiceDetailUiState.Error(
                                errors.decode(result.error).message(),
                            )
                }
            }
        }

        /** Send the invoice to its recipient (in-app notification; no state mutation). */
        fun send() {
            if (_sending.value) return
            _sending.value = true
            viewModelScope.launch {
                when (repo.sendInvoice(owner, invoiceId)) {
                    is NetworkResult.Success -> {
                        _sending.value = false
                        _sentToast.value = true
                        delay(SENT_TOAST_MS)
                        _sentToast.value = false
                    }
                    is NetworkResult.Failure -> {
                        _sending.value = false
                        _state.value = InvoiceDetailUiState.Error("Couldn't send the invoice.")
                    }
                }
            }
        }

        fun shareText(): String = shareText

        private fun build(invoice: InvoiceDto): InvoiceDetailUiState.Loaded {
            val currency = (invoice.currency ?: "USD").uppercase()
            val total = PackagesMoney.format(invoice.totalCents, invoice.currency)
            val reference = "INV-" + invoiceId.take(REF_PREFIX_LEN).uppercase()
            val lineItems = InvoiceParsing.lineItems(invoice.lineItems)
            shareText = "$reference · $total"
            return InvoiceDetailUiState.Loaded(
                reference = reference,
                issuedLabel = PackagesFormat.dayString(invoice.createdAt) ?: "—",
                totalLabel = total,
                currencyCode = currency,
                recipientLabel =
                    invoice.recipientUserId?.let {
                        "Customer · " + it.take(REF_PREFIX_LEN).uppercase()
                    } ?: "Customer",
                shareText = shareText,
                lineItems = lineItems,
                unitLabels =
                    lineItems.indices.associateWith { i ->
                        unitLabel(
                            lineItems[i],
                            invoice.currency,
                        )
                    },
                lineTotalLabels =
                    lineItems.indices.associateWith { i ->
                        lineTotalLabel(
                            lineItems[i],
                            invoice.currency,
                        )
                    },
                pillar = owner.pillar(),
            )
        }

        private fun unitLabel(
            item: InvoiceLineItem,
            currency: String?,
        ): String = item.unitCents?.let { PackagesMoney.format(it, currency) } ?: "—"

        private fun lineTotalLabel(
            item: InvoiceLineItem,
            currency: String?,
        ): String = item.totalCents?.let { PackagesMoney.format(it, currency) } ?: "—"

        private fun resolveOwner(): SchedulingOwner =
            (auth.state.value as? AuthRepository.State.SignedIn)?.user?.id
                ?.let { SchedulingOwner.Business(it) }
                ?: SchedulingOwner.Personal

        private fun SchedulingError.message(): String =
            when (this) {
                is SchedulingError.Secret -> "Only the business owner can view this invoice."
                is SchedulingError.Generic -> message
                else -> "Couldn't load that invoice."
            }

        private companion object {
            const val SENT_TOAST_MS = 1800L
            const val REF_PREFIX_LEN = 6
        }
    }
