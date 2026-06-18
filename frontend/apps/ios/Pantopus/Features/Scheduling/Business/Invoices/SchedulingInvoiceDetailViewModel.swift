//
//  SchedulingInvoiceDetailViewModel.swift
//  Pantopus
//
//  G13 Invoice Detail (owner) — Stream I15. Renders `GET /invoices/:id` and the
//  owner "send" action (`POST /invoices/:id/send`). Behind
//  `SchedulingFeatureFlags.paidEnabled`. Matches `invoicedetail-frames.jsx`
//  within the InvoiceDTO's fields (see InvoicesKit note: no status / timeline /
//  due date / memo / payer name → those design sections are omitted).
//

import Foundation
import Observation
import SwiftUI

@Observable
@MainActor
final class SchedulingInvoiceDetailViewModel {
    enum Phase: Equatable { case loading, loaded, error(String), comingSoon }

    // MARK: Inputs

    let owner: SchedulingOwner
    let invoiceId: String
    let push: @MainActor (SchedulingRoute) -> Void
    private let client: SchedulingClient

    // MARK: State

    private(set) var phase: Phase = .loading
    private(set) var invoice: InvoiceDTO?
    private(set) var lineItems: [InvoiceLineItem] = []
    private(set) var sending = false
    private(set) var showSentToast = false

    var theme: SchedulingIdentityTheme { owner.theme }
    var accent: Color { theme.accent }
    var accentBg: Color { theme.accentBg }

    var totalLabel: String { SchedulingMoney.format(cents: invoice?.totalCents, currency: invoice?.currency) }
    var currencyCode: String { (invoice?.currency ?? "USD").uppercased() }
    var reference: String { "INV-" + invoiceId.prefix(6).uppercased() }
    var issuedLabel: String { PackagesFormat.dayString(invoice?.createdAt) ?? "—" }
    var recipientLabel: String {
        guard let recipient = invoice?.recipientUserId else { return "Customer" }
        return "Customer · " + recipient.prefix(6).uppercased()
    }

    var shareText: String { "\(reference) · \(totalLabel)" }

    init(
        owner: SchedulingOwner,
        invoiceId: String,
        push: @escaping @MainActor (SchedulingRoute) -> Void,
        client: SchedulingClient
    ) {
        self.owner = owner
        self.invoiceId = invoiceId
        self.push = push
        self.client = client
    }

    // MARK: Lifecycle

    func load() async {
        guard SchedulingFeatureFlags.paidEnabled else { phase = .comingSoon; return }
        phase = .loading
        do {
            let result: InvoiceResponse = try await client.request(SchedulingEndpoints.getInvoice(owner: owner, id: invoiceId))
            invoice = result.invoice
            lineItems = InvoiceParsing.lineItems(from: result.invoice.lineItems)
            phase = .loaded
        } catch let error as SchedulingError {
            phase = .error(error.userMessage ?? "Couldn't load that invoice.")
        } catch {
            phase = .error("Couldn't load that invoice.")
        }
    }

    func refresh() async { await load() }

    // MARK: Actions

    /// Send the invoice to its recipient (in-app notification; does not mutate
    /// invoice state server-side).
    func send() async {
        sending = true
        defer { sending = false }
        do {
            try await client.send(SchedulingEndpoints.sendInvoice(owner: owner, id: invoiceId))
            flashSent()
        } catch let error as SchedulingError {
            phase = .error(error.userMessage ?? "Couldn't send the invoice.")
        } catch {
            phase = .error("Couldn't send the invoice.")
        }
    }

    private func flashSent() {
        showSentToast = true
        Task {
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            showSentToast = false
        }
    }

    // MARK: Line-item formatting

    func unitLabel(_ item: InvoiceLineItem) -> String {
        guard let unit = item.unitCents else { return "—" }
        return SchedulingMoney.format(cents: unit, currency: invoice?.currency)
    }

    func lineTotalLabel(_ item: InvoiceLineItem) -> String {
        guard let total = item.totalCents else { return "—" }
        return SchedulingMoney.format(cents: total, currency: invoice?.currency)
    }
}
