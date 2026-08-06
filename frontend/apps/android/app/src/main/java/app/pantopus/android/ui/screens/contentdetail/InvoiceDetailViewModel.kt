@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.contentdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.payments.CreatePaymentIntentRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.payments.PaymentsRepository
import app.pantopus.android.ui.screens.settings.payments.CheckoutOutcome
import app.pantopus.android.ui.theme.PantopusIcon
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
 * T2.6 ships the invoice frame from fixture display data. Block 3B wires the
 * "Pay" CTA to Stripe PaymentSheet only when a real backend order reference is
 * provided through navigation args; fixture invoices leave checkout disabled
 * rather than sending placeholder payee/amount data. On a successful pay we
 * re-project into the paid frame (A09.4) — once a real invoice backend lands,
 * [load] reads the paid state from the server instead.
 */
@HiltViewModel
class InvoiceDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val paymentsRepository: PaymentsRepository,
    ) : ViewModel() {
        companion object {
            const val INVOICE_ID_KEY = "invoiceId"
            const val GIG_ID_KEY = "gigId"
            const val LISTING_ID_KEY = "listingId"
            const val OFFER_ID_KEY = "offerId"

            fun checkoutRequestFrom(savedStateHandle: SavedStateHandle): CheckoutRequest? {
                val gigId = savedStateHandle.get<String>(GIG_ID_KEY)?.takeIf { it.isNotBlank() }
                if (gigId != null) {
                    return CheckoutRequest(
                        gigId = gigId,
                        description = "Invoice ${savedStateHandle.get<String>(INVOICE_ID_KEY) ?: ""}".trim(),
                    )
                }
                val listingId = savedStateHandle.get<String>(LISTING_ID_KEY)?.takeIf { it.isNotBlank() }
                val offerId = savedStateHandle.get<String>(OFFER_ID_KEY)?.takeIf { it.isNotBlank() }
                return if (listingId != null && offerId != null) {
                    CheckoutRequest(
                        listingId = listingId,
                        offerId = offerId,
                        description = "Invoice ${savedStateHandle.get<String>(INVOICE_ID_KEY) ?: ""}".trim(),
                    )
                } else {
                    null
                }
            }
        }

        private val invoiceId: String = savedStateHandle.get<String>(INVOICE_ID_KEY) ?: "INV-00247"

        private val _state = MutableStateFlow<ContentDetailUiState>(ContentDetailUiState.Loading)
        val state: StateFlow<ContentDetailUiState> = _state.asStateFlow()

        private val _paymentStatus = MutableStateFlow<InvoicePaymentStatus>(InvoicePaymentStatus.Idle)
        val paymentStatus: StateFlow<InvoicePaymentStatus> = _paymentStatus.asStateFlow()

        private val _events = MutableSharedFlow<InvoiceDetailEvent>(extraBufferCapacity = 4)
        val events: SharedFlow<InvoiceDetailEvent> = _events.asSharedFlow()

        /**
         * The order this invoice bills for. Real invoices must carry a backend
         * order reference; fixture invoices leave this null so pay is disabled.
         */
        private val checkoutRequest: CheckoutRequest? = checkoutRequestFrom(savedStateHandle)

        /**
         * Flips to true after a successful pay so [load] re-projects the
         * paid frame (A09.4).
         */
        private var paid: Boolean = false

        fun load() {
            _state.value =
                ContentDetailUiState.Loaded(
                    if (paid) Projection.paidFixture(invoiceId) else Projection.fixture(invoiceId),
                )
        }

        /**
         * Short summary handed to the paid dock's Share action (system
         * share sheet). Mirrors the iOS string exactly. The amount comes from
         * the same constant the hero / dock / line-item total render, so the
         * shared text can never quote a figure the invoice doesn't show.
         */
        fun shareSummary(): String = "Invoice ${invoiceId.uppercase()} · Paid ${Projection.TOTAL_VALUE} via Pantopus Pay"

        /** Tapped "Pay" — create the PaymentIntent, then ask the screen to present the sheet. */
        fun pay() {
            // The paid dock reuses the primary slot for "Download receipt" —
            // never re-run checkout once this invoice is settled. Mirrors the
            // iOS `guard !paid` in `InvoiceDetailViewModel.payNow()`.
            if (paid) return
            val checkoutRequest = checkoutRequest
            if (checkoutRequest == null) {
                _paymentStatus.value = InvoicePaymentStatus.Declined("This invoice can't be paid yet.")
                return
            }
            _paymentStatus.value = InvoicePaymentStatus.Paying
            viewModelScope.launch {
                val req =
                    CreatePaymentIntentRequest(
                        gigId = checkoutRequest.gigId,
                        listingId = checkoutRequest.listingId,
                        offerId = checkoutRequest.offerId,
                        description = checkoutRequest.description,
                    )
                when (val result = paymentsRepository.createPaymentIntent(req)) {
                    is NetworkResult.Success -> _events.emit(InvoiceDetailEvent.PresentCheckout(result.data))
                    is NetworkResult.Failure ->
                        _paymentStatus.value = InvoicePaymentStatus.Declined(result.error.message)
                }
            }
        }

        /** Result of presenting PaymentSheet, mapped from Stripe in the screen. */
        fun onCheckoutOutcome(outcome: CheckoutOutcome) {
            when (outcome) {
                CheckoutOutcome.Paid -> {
                    _paymentStatus.value = InvoicePaymentStatus.Paid
                    // Re-project into the paid frame (Paid pill, green
                    // total, receipt row, Share + Download dock).
                    paid = true
                    load()
                }
                CheckoutOutcome.Canceled -> _paymentStatus.value = InvoicePaymentStatus.Canceled
                is CheckoutOutcome.Declined ->
                    _paymentStatus.value =
                        InvoicePaymentStatus.Declined(outcome.message ?: "Your card was declined.")
            }
        }

        /** Clear a result toast once the screen has shown it. */
        fun clearPaymentStatus() {
            _paymentStatus.value = InvoicePaymentStatus.Idle
        }

        object Projection {
            /**
             * Single source of truth for this invoice's total. Hero price,
             * dock CTA, line-item total, and `shareSummary` all read it so
             * they cannot drift.
             */
            const val TOTAL_VALUE = "$642.85"

            /** A09.4 · due state. */
            fun fixture(invoiceId: String): ContentDetailContent =
                ContentDetailContent(
                    kind = ContentDetailKind.Invoice,
                    statusPill =
                        ContentDetailPill(
                            id = "status",
                            label = "Due in 7 days",
                            icon = PantopusIcon.Clock,
                            tone = ContentDetailPill.Tone.Warning,
                        ),
                    hero =
                        ContentDetailHero(
                            title = "Holiday lighting · install + takedown",
                            monoId = "${invoiceId.uppercase()} · issued Dec 4 · due Dec 18",
                            priceLine = TOTAL_VALUE,
                            priceCaption = "total · USD",
                        ),
                    modules =
                        listOf(
                            payerPayee,
                            lineItems(totalLabel = "Total", totalTone = ContentDetailModule.LineItems.TotalTone.Primary),
                            ContentDetailModule.CaptionedText(
                                id = "terms",
                                title = "Payment terms",
                                icon = PantopusIcon.File,
                                label =
                                    "Net 14 from issue. Pantopus Pay (instant), card, or ACH. " +
                                        "Late fee 1.5%/mo applies after due date.",
                            ),
                            noteFromSender,
                        ),
                    dock =
                        ContentDetailDock(
                            secondary = null,
                            primary =
                                ContentDetailDockButton(
                                    label = "Pay $TOTAL_VALUE",
                                    icon = PantopusIcon.CreditCard,
                                ),
                        ),
                )

            /** A09.4 · paid state (paid 4 days early via Pantopus Pay). */
            fun paidFixture(invoiceId: String): ContentDetailContent =
                ContentDetailContent(
                    kind = ContentDetailKind.Invoice,
                    statusPill =
                        ContentDetailPill(
                            id = "status",
                            label = "Paid · Dec 14",
                            icon = PantopusIcon.CheckCircle,
                            tone = ContentDetailPill.Tone.Success,
                        ),
                    hero =
                        ContentDetailHero(
                            title = "Holiday lighting · install + takedown",
                            monoId = "${invoiceId.uppercase()} · issued Dec 4 · paid Dec 14",
                            priceLine = TOTAL_VALUE,
                            priceTone = ContentDetailHero.PriceTone.Success,
                            priceTrailingLabel = "paid in full",
                            priceCheckDisc = true,
                        ),
                    modules =
                        listOf(
                            payerPayee,
                            ContentDetailModule.Callout(
                                id = "pantopus-pay-receipt",
                                style = ContentDetailModule.Callout.Style.Banner,
                                tone = ContentDetailModule.Callout.Tone.Success,
                                icon = PantopusIcon.Zap,
                                iconTone = ContentDetailModule.Callout.IconTone.SuccessOutline,
                                title = "Paid via Pantopus Pay",
                                subtitle = "txn_3p4q9m · Dec 14",
                                subtitleMono = true,
                            ),
                            lineItems(totalLabel = "Paid", totalTone = ContentDetailModule.LineItems.TotalTone.Success),
                            noteFromSender,
                        ),
                    dock =
                        ContentDetailDock(
                            secondary = ContentDetailDockButton(label = "Share", icon = PantopusIcon.Share),
                            primary = ContentDetailDockButton(label = "Download receipt", icon = PantopusIcon.Receipt),
                        ),
                )

            private val payerPayee =
                ContentDetailModule.FromTo(
                    id = "fromto",
                    from =
                        ContentDetailParty(
                            label = "From",
                            name = "Brightside Outdoor",
                            sub = "Business · Verified",
                            accent = ContentDetailParty.Accent.Business,
                        ),
                    to =
                        ContentDetailParty(
                            label = "To",
                            name = "Marcus Chen",
                            sub = "Personal",
                            accent = ContentDetailParty.Accent.Personal,
                        ),
                )

            private fun lineItems(
                totalLabel: String,
                totalTone: ContentDetailModule.LineItems.TotalTone,
            ): ContentDetailModule.LineItems =
                ContentDetailModule.LineItems(
                    id = "items",
                    title = "Line items",
                    icon = PantopusIcon.File,
                    rows =
                        listOf(
                            ContentDetailLineItem("l1", "Install labor · 3.5h", "3.5", "$65", "$227.50"),
                            ContentDetailLineItem("l2", "LED string lights", "8", "$28", "$224.00"),
                            ContentDetailLineItem("l3", "Clips, timer, splitters", "1", "$45", "$45.00"),
                            ContentDetailLineItem("l4", "Takedown · scheduled Jan 6", "1", "$95", "$95.00"),
                        ),
                    fees =
                        listOf(
                            ContentDetailSummaryRow("sub", "Subtotal", "$591.50"),
                            ContentDetailSummaryRow("svc", "Service fee (3%)", "$17.75"),
                            ContentDetailSummaryRow("tax", "Tax (5.7%)", "$33.60"),
                        ),
                    totalLabel = totalLabel,
                    totalValue = TOTAL_VALUE,
                    totalTone = totalTone,
                )

            private val noteFromSender =
                ContentDetailModule.Description(
                    id = "note",
                    title = "Note from sender",
                    icon = null,
                    body =
                        "“Takedown is on the schedule for the first Tuesday in January — no need " +
                            "to be home. Thanks again Marcus, happy holidays.”",
                )
        }
    }
