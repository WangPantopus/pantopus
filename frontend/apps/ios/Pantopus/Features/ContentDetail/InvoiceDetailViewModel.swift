//
//  InvoiceDetailViewModel.swift
//  Pantopus
//
//  T2.6 ships the invoice frame with hardcoded fixture data — the
//  backend wiring (backend/routes/paymentOps.js + wallet.js + Stripe
//  PaymentIntent) lands alongside the Stripe payment-sheet integration
//  in a follow-up. The shell already supports the variable
//  jsonb_modules[] surface so plumbing real invoice modules through
//  later changes the VM only. Both designed A09.4 states (due + paid)
//  are projected here.
//

import Foundation
import Observation

@Observable
@MainActor
public final class InvoiceDetailViewModel {
    public private(set) var state: ContentDetailState = .loading

    private let invoiceId: String
    private let paid: Bool

    public init(invoiceId: String, paid: Bool = false) {
        self.invoiceId = invoiceId
        self.paid = paid
    }

    public func load() async {
        state = .loaded(paid ? Self.paidFixture(invoiceId: invoiceId) : Self.fixture(invoiceId: invoiceId))
    }

    public func payNow() async -> Bool {
        // Real implementation hands off to Stripe.confirmPayment(...).
        // Stub returns false so the host can show a "not yet available"
        // sheet until the backend integration lands.
        false
    }

    // MARK: - Fixtures

    /// A09.4 · due state.
    static func fixture(invoiceId: String) -> ContentDetailContent {
        ContentDetailContent(
            kind: .invoice,
            statusPill: ContentDetailPill(label: "Due in 7 days", icon: .clock, tone: .warning),
            hero: ContentDetailHero(
                title: "Holiday lighting · install + takedown",
                monoId: "\(invoiceId.uppercased()) · issued Dec 4 · due Dec 18",
                priceLine: "$642.85",
                priceCaption: "total · USD"
            ),
            modules: [
                payerPayee,
                lineItems(totalLabel: "Total", totalTone: .primary),
                .captionedText(ContentDetailCaptionedText(
                    title: "Payment terms",
                    icon: .file,
                    label: "Net 14 from issue. Pantopus Pay (instant), card, or ACH. Late fee 1.5%/mo applies after due date."
                )),
                noteFromSender
            ],
            dock: ContentDetailDock(
                secondary: nil,
                primary: ContentDetailDockButton(label: "Pay $642.85", icon: .creditCard)
            )
        )
    }

    /// A09.4 · paid state (paid 4 days early via Pantopus Pay).
    static func paidFixture(invoiceId: String) -> ContentDetailContent {
        ContentDetailContent(
            kind: .invoice,
            statusPill: ContentDetailPill(label: "Paid · Dec 14", icon: .checkCircle, tone: .success),
            hero: ContentDetailHero(
                title: "Holiday lighting · install + takedown",
                monoId: "\(invoiceId.uppercased()) · issued Dec 4 · paid Dec 14",
                priceLine: "$642.85",
                priceCaption: nil,
                priceTone: .success,
                priceTrailingLabel: "paid in full",
                priceCheckDisc: true
            ),
            modules: [
                payerPayee,
                .callout(ContentDetailCallout(
                    identifier: "pantopus-pay-receipt",
                    style: .banner,
                    tone: .success,
                    icon: .zap,
                    iconTone: .successOutline,
                    title: "Paid via Pantopus Pay",
                    subtitle: "txn_3p4q9m · Dec 14",
                    subtitleMono: true
                )),
                lineItems(totalLabel: "Paid", totalTone: .success),
                noteFromSender
            ],
            dock: ContentDetailDock(
                secondary: ContentDetailDockButton(label: "Share", icon: .share),
                primary: ContentDetailDockButton(label: "Download receipt", icon: .receipt)
            )
        )
    }

    // MARK: - Shared modules

    private static var payerPayee: ContentDetailModule {
        .fromTo(ContentDetailFromTo(
            from: ContentDetailParty(label: "From", name: "Brightside Outdoor", sub: "Business · Verified", accent: .business),
            to: ContentDetailParty(label: "To", name: "Marcus Chen", sub: "Personal", accent: .personal)
        ))
    }

    private static func lineItems(totalLabel: String, totalTone: ContentDetailLineItems.TotalTone) -> ContentDetailModule {
        .lineItems(ContentDetailLineItems(
            title: "Line items",
            icon: .file,
            rows: [
                ContentDetailLineItem(item: "Install labor · 3.5h", qty: "3.5", unit: "$65", total: "$227.50"),
                ContentDetailLineItem(item: "LED string lights", qty: "8", unit: "$28", total: "$224.00"),
                ContentDetailLineItem(item: "Clips, timer, splitters", qty: "1", unit: "$45", total: "$45.00"),
                ContentDetailLineItem(item: "Takedown · scheduled Jan 6", qty: "1", unit: "$95", total: "$95.00")
            ],
            fees: [
                ContentDetailSummaryRow(label: "Subtotal", value: "$591.50"),
                ContentDetailSummaryRow(label: "Service fee (3%)", value: "$17.75"),
                ContentDetailSummaryRow(label: "Tax (5.7%)", value: "$33.60")
            ],
            totalLabel: totalLabel,
            totalValue: "$642.85",
            totalTone: totalTone
        ))
    }

    private static var noteFromSender: ContentDetailModule {
        .description(ContentDetailDescription(
            title: "Note from sender",
            icon: nil,
            body: "\u{201C}Takedown is on the schedule for the first Tuesday in January — no need "
                + "to be home. Thanks again Marcus, happy holidays.\u{201D}"
        ))
    }
}
