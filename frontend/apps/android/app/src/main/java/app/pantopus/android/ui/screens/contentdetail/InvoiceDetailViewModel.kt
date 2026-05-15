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
            fun fixture(invoiceId: String): ContentDetailContent {
                val lineItems =
                    listOf(
                        ContentDetailLineItem("l1", "Demo + haul", "1", "$60", "$60"),
                        ContentDetailLineItem("l2", "12\" porcelain tile", "48", "$2.10", "$100.80"),
                        ContentDetailLineItem("l3", "Thinset + grout", "1", "$32", "$32"),
                        ContentDetailLineItem("l4", "Labor · 4h", "4", "$45", "$180"),
                    )
                val modules =
                    listOf<ContentDetailModule>(
                        ContentDetailModule.FromTo(
                            id = "fromto",
                            from =
                                ContentDetailParty(
                                    label = "From",
                                    name = "Lopez Tile Co.",
                                    sub = "Business · Verified",
                                    accent = ContentDetailParty.Accent.Business,
                                ),
                            to =
                                ContentDetailParty(
                                    label = "To",
                                    name = "Maria Kowalski",
                                    sub = "Personal",
                                    accent = ContentDetailParty.Accent.Personal,
                                ),
                        ),
                        ContentDetailModule.LineItems(
                            id = "items",
                            title = "Line items",
                            icon = PantopusIcon.File,
                            rows = lineItems,
                        ),
                        ContentDetailModule.Summary(
                            id = "summary",
                            rows =
                                listOf(
                                    ContentDetailSummaryRow("sub", "Subtotal", "$372.80"),
                                    ContentDetailSummaryRow("tax", "Tax (8.6%)", "$32.06"),
                                ),
                            totalLabel = "Total",
                            totalValue = "$404.86",
                        ),
                    )
                return ContentDetailContent(
                    kind = ContentDetailKind.Invoice,
                    statusPill =
                        ContentDetailPill(
                            id = "status",
                            label = "Due in 3 days",
                            icon = PantopusIcon.Calendar,
                            tone = ContentDetailPill.Tone.Warning,
                        ),
                    hero =
                        ContentDetailHero(
                            title = "Bathroom retile",
                            monoId = "${invoiceId.uppercase()} · Nov 6, 2025",
                        ),
                    modules = modules,
                    dock =
                        ContentDetailDock(
                            secondary = null,
                            primary = ContentDetailDockButton(label = "Pay $404.86", icon = PantopusIcon.Check),
                        ),
                )
            }
        }
    }
