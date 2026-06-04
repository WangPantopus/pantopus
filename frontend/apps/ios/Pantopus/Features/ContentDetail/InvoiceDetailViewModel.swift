//
//  InvoiceDetailViewModel.swift
//  Pantopus
//
//  T2.6 ships the invoice frame with hardcoded fixture data; Block 3B wires
//  the "Pay" CTA to the real Stripe PaymentSheet via the shared
//  `CheckoutCoordinator`. The invoice itself still projects from a fixture
//  (the invoice/order backend lands separately), but the pay step is real:
//  it creates a PaymentIntent (`POST /api/payments/intent`), presents
//  PaymentSheet, and on success re-reads server state — we never mark the
//  invoice paid client-side (webhooks reconcile the `Payment`). The shell
//  already supports the variable jsonb_modules[] surface so plumbing real
//  invoice modules through later changes the VM only. Both designed A09.4
//  states (due + paid) are projected here.
//

import Foundation
import Observation

/// Where the "Pay" CTA currently sits, so the view can surface the right
/// result toast (success / declined / canceled) after PaymentSheet returns.
public enum InvoicePaymentStatus: Sendable, Equatable {
    case idle
    case paying
    case paid
    case canceled
    case declined(message: String)
}

@Observable
@MainActor
public final class InvoiceDetailViewModel {
    public private(set) var state: ContentDetailState = .loading
    /// Drives the post-checkout toast in the view (`checkout.*` surfaces).
    public private(set) var paymentStatus: InvoicePaymentStatus = .idle

    private let invoiceId: String
    private let paid: Bool
    private let checkout: CheckoutCoordinator
    /// The order this invoice bills for. Real invoices carry the payee +
    /// amount; until the invoice backend lands we derive a request from the
    /// fixture so the pay step is exercised end-to-end. `nil` disables pay.
    private let checkoutRequest: CheckoutRequest?

    public init(
        invoiceId: String,
        paid: Bool = false,
        checkout: CheckoutCoordinator = CheckoutCoordinator(),
        checkoutRequest: CheckoutRequest? = nil
    ) {
        self.invoiceId = invoiceId
        self.paid = paid
        self.checkout = checkout
        self.checkoutRequest = checkoutRequest ?? Self.fixtureCheckoutRequest
    }

    public func load() async {
        state = .loaded(paid ? Self.paidFixture(invoiceId: invoiceId) : Self.fixture(invoiceId: invoiceId))
    }

    /// Run the PaymentSheet checkout for this invoice. On success we re-read
    /// from the backend (the source of truth) rather than flipping the state
    /// locally — for the fixture that re-projects the same frame; once a real
    /// invoice backend lands, `load()` reflects the paid state.
    public func payNow() async {
        guard !paid else { return }
        guard let request = checkoutRequest else {
            paymentStatus = .declined(message: "This invoice can't be paid yet.")
            return
        }
        paymentStatus = .paying
        let outcome = await checkout.pay(request)
        switch outcome {
        case .paid:
            paymentStatus = .paid
            await load()
        case .canceled:
            paymentStatus = .canceled
        case let .declined(message), let .failed(message):
            paymentStatus = .declined(message: message)
        }
    }

    /// Clear a result toast once the view has shown it.
    public func clearPaymentStatus() {
        paymentStatus = .idle
    }

    /// Fixture order reference. The fixture total is $642.85 → 64 285 cents,
    /// paid to the issuing business ("Brightside Outdoor"). The payee id is a
    /// stand-in until the invoice backend supplies the real one.
    private static let fixtureCheckoutRequest = CheckoutRequest(
        payeeId: "00000000-0000-4000-8000-000000000b51",
        amountCents: 64285,
        description: "Holiday lighting · install + takedown"
    )

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
