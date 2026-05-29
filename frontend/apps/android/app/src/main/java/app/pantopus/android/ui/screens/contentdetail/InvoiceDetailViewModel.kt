@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.contentdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * T2.6 ships the invoice frame with hardcoded fixture data — the
 * backend wiring (paymentOps.js + wallet.js + Stripe PaymentIntent)
 * lands alongside the Stripe payment-sheet integration in a follow-up.
 */
@HiltViewModel
class InvoiceDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        companion object {
            const val INVOICE_ID_KEY = "invoiceId"
        }

        private val invoiceId: String = savedStateHandle.get<String>(INVOICE_ID_KEY) ?: "INV-00247"

        private val _state = MutableStateFlow<ContentDetailUiState>(ContentDetailUiState.Loading)
        val state: StateFlow<ContentDetailUiState> = _state.asStateFlow()

        fun load() {
            _state.value = ContentDetailUiState.Loaded(Projection.fixture(invoiceId))
        }

        object Projection {
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
                            priceLine = "$642.85",
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
                            primary = ContentDetailDockButton(label = "Pay $642.85", icon = PantopusIcon.CreditCard),
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
                            priceLine = "$642.85",
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
                    totalValue = "$642.85",
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
