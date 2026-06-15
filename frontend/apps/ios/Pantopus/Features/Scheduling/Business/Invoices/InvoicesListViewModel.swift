//
//  InvoicesListViewModel.swift
//  Pantopus
//
//  G12 Invoices List (owner, business-only) — Stream I15. Lists `GET /invoices`
//  grouped by created day, with the Stripe-not-connected gate from
//  `GET /payments/status`. Behind `SchedulingFeatureFlags.paidEnabled`. Matches
//  `invoiceslist-frames.jsx` (within the limits of the minimal InvoiceDTO — see
//  InvoicesKit note: no status pills / status filters / status summary).
//

import Foundation
import Observation
import SwiftUI

@Observable
@MainActor
final class InvoicesListViewModel {
    enum Phase: Equatable { case loading, loaded, empty, gate, error(String), comingSoon }

    // MARK: Inputs

    let owner: SchedulingOwner
    let push: @MainActor (SchedulingRoute) -> Void
    private let client: SchedulingClient

    // MARK: State

    private(set) var phase: Phase = .loading
    private(set) var invoices: [InvoiceDTO] = []
    private(set) var sections: [InvoiceDaySection] = []
    private(set) var paymentsConnected = false
    private(set) var paymentsApplicable = true

    var theme: SchedulingIdentityTheme { owner.theme }
    var accent: Color { theme.accent }

    /// Sum of all invoice totals — the one summary stat the DTO supports.
    var totalLabel: String {
        SchedulingMoney.format(cents: invoices.reduce(0) { $0 + ($1.totalCents ?? 0) }, currency: invoices.first?.currency)
    }

    var countLabel: String { "\(invoices.count)" }

    init(
        owner: SchedulingOwner,
        push: @escaping @MainActor (SchedulingRoute) -> Void,
        client: SchedulingClient
    ) {
        self.owner = owner
        self.push = push
        self.client = client
    }

    // MARK: Lifecycle

    func load() async {
        guard SchedulingFeatureFlags.paidEnabled else { phase = .comingSoon; return }
        phase = .loading
        do {
            if let status: PaymentsStatusDTO = try? await client.request(SchedulingEndpoints.paymentsStatus(owner: owner)) {
                paymentsConnected = status.connected
                paymentsApplicable = status.applicable
            }
            let result: InvoicesResponse = try await client.request(SchedulingEndpoints.getInvoices(owner: owner))
            invoices = result.invoices
            sections = InvoiceGrouping.byDay(invoices)
            if !invoices.isEmpty {
                phase = .loaded
            } else if paymentsApplicable, !paymentsConnected {
                phase = .gate
            } else {
                phase = .empty
            }
        } catch let error as SchedulingError {
            phase = .error(error.userMessage ?? "Couldn't load invoices.")
        } catch {
            phase = .error("Couldn't load invoices.")
        }
    }

    func refresh() async { await load() }

    // MARK: Actions

    func openInvoice(_ invoice: InvoiceDTO) { push(.invoiceDetail(owner: owner, invoiceId: invoice.id)) }
    func connectPayments() { push(.paymentsSetup(owner: owner)) }

    // MARK: Row formatting

    func amount(_ invoice: InvoiceDTO) -> String {
        SchedulingMoney.format(cents: invoice.totalCents, currency: invoice.currency)
    }

    /// Short monospace reference from the invoice id (no invoice_number in DTO).
    func reference(_ invoice: InvoiceDTO) -> String {
        "INV-" + invoice.id.prefix(6).uppercased()
    }
}
