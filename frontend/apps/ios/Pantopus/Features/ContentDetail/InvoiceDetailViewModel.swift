//
//  InvoiceDetailViewModel.swift
//  Pantopus
//
//  T2.6 ships the invoice frame with hardcoded fixture data — the
//  backend wiring (backend/routes/paymentOps.js + wallet.js + Stripe
//  PaymentIntent) lands alongside the Stripe payment-sheet integration
//  in a follow-up. The shell already supports the variable
//  jsonb_modules[] surface so plumbing real invoice modules through
//  later changes the VM only.
//

import Foundation
import Observation

@Observable
@MainActor
public final class InvoiceDetailViewModel {
    public private(set) var state: ContentDetailState = .loading

    private let invoiceId: String

    public init(invoiceId: String) {
        self.invoiceId = invoiceId
    }

    public func load() async {
        state = .loaded(Self.fixture(invoiceId: invoiceId))
    }

    public func payNow() async -> Bool {
        // Real implementation hands off to Stripe.confirmPayment(...).
        // Stub returns false so the host can show a "not yet available"
        // sheet until the backend integration lands.
        false
    }

    static func fixture(invoiceId: String) -> ContentDetailContent {
        let lineItems: [ContentDetailLineItem] = [
            ContentDetailLineItem(item: "Demo + haul", qty: "1", unit: "$60", total: "$60"),
            ContentDetailLineItem(item: "12\" porcelain tile", qty: "48", unit: "$2.10", total: "$100.80"),
            ContentDetailLineItem(item: "Thinset + grout", qty: "1", unit: "$32", total: "$32"),
            ContentDetailLineItem(item: "Labor · 4h", qty: "4", unit: "$45", total: "$180")
        ]
        let summary = ContentDetailSummary(
            rows: [
                ContentDetailSummaryRow(label: "Subtotal", value: "$372.80"),
                ContentDetailSummaryRow(label: "Tax (8.6%)", value: "$32.06")
            ],
            totalLabel: "Total",
            totalValue: "$404.86"
        )
        let modules: [ContentDetailModule] = [
            .fromTo(ContentDetailFromTo(
                from: ContentDetailParty(label: "From", name: "Lopez Tile Co.", sub: "Business · Verified", accent: .business),
                to: ContentDetailParty(label: "To", name: "Maria Kowalski", sub: "Personal", accent: .personal)
            )),
            .lineItems(ContentDetailLineItems(title: "Line items", icon: .file, rows: lineItems)),
            .summary(summary)
        ]
        return ContentDetailContent(
            kind: .invoice,
            cover: nil,
            statusPill: ContentDetailPill(label: "Due in 3 days", icon: .calendar, tone: .warning),
            hero: ContentDetailHero(
                title: "Bathroom retile",
                categoryChip: nil,
                meta: nil,
                monoId: "\(invoiceId.uppercased()) · Nov 6, 2025",
                priceLine: nil
            ),
            statStrip: [],
            counterparty: nil,
            modules: modules,
            trustCapsules: [],
            dock: ContentDetailDock(
                secondary: nil,
                primary: ContentDetailDockButton(label: "Pay $404.86", icon: .check)
            )
        )
    }
}
