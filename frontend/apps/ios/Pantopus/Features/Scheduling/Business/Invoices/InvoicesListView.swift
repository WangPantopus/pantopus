//
//  InvoicesListView.swift
//  Pantopus
//
//  G12 Invoices List (owner) — Stream I15. Day-grouped invoice rows + a totals
//  summary, with the Stripe-not-connected gate. Matches `invoiceslist-frames.jsx`
//  within the InvoiceDTO's available fields. Tokens only.
//

import SwiftUI

struct InvoicesListView: View {
    @State private var model: InvoicesListViewModel
    @Environment(\.dismiss) private var dismiss

    init(
        owner: SchedulingOwner,
        push: @escaping @MainActor (SchedulingRoute) -> Void,
        client: SchedulingClient = .shared
    ) {
        _model = State(wrappedValue: InvoicesListViewModel(owner: owner, push: push, client: client))
    }

    var body: some View {
        VStack(spacing: Spacing.s0) {
            PkgTopBar(title: "Invoices", onBack: { dismiss() })
            content
        }
        .background(Theme.Color.appBg)
        .navigationBarBackButtonHidden(true)
        .task { await model.load() }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .accessibilityIdentifier("scheduling.invoices.list")
    }

    @ViewBuilder
    private var content: some View {
        switch model.phase {
        case .loading:
            loadingBody
        case .comingSoon:
            PkgComingSoon(title: "Invoices")
        case .gate:
            PkgStripeGate(
                icon: .receipt,
                title: "Connect payments to invoice for services",
                message: "Pantopus uses Stripe to send and collect invoices."
            ) { model.connectPayments() }
        case let .error(message):
            PkgErrorState(message: message) { Task { await model.load() } }
        case .empty:
            EmptyState(
                icon: .receipt,
                headline: "No invoices yet",
                subcopy: "Invoices appear here once you take a booking or sell a package.",
                tint: Theme.Color.businessBg,
                accent: Theme.Color.business
            )
            .padding(.top, Spacing.s10)
        case .loaded:
            loadedBody
        }
    }

    private var loadedBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s3) {
                summary
                ForEach(model.sections) { section in
                    Text(section.day.uppercased())
                        .font(.system(size: 9, weight: .bold)).tracking(0.8)
                        .foregroundStyle(Theme.Color.appTextMuted)
                        .padding(.horizontal, Spacing.s1).padding(.top, Spacing.s1)
                    PkgRowCard {
                        ForEach(Array(section.invoices.enumerated()), id: \.element.id) { index, invoice in
                            InvoiceRow(
                                reference: model.reference(invoice),
                                amount: model.amount(invoice),
                                accent: model.accent,
                                accentBg: model.theme.accentBg,
                                onTap: { model.openInvoice(invoice) }
                            )
                            if index < section.invoices.count - 1 { Divider().background(Theme.Color.appBorder) }
                        }
                    }
                }
                Color.clear.frame(height: Spacing.s8)
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s3)
        }
        .refreshable { await model.refresh() }
    }

    private var summary: some View {
        PkgCard {
            HStack(spacing: Spacing.s0) {
                stat(label: "Invoices", value: model.countLabel)
                Rectangle().fill(Theme.Color.appBorder).frame(width: 1, height: 40)
                stat(label: "Total invoiced", value: model.totalLabel)
            }
        }
    }

    private func stat(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label.uppercased())
                .font(.system(size: 10, weight: .bold)).tracking(0.6)
                .foregroundStyle(Theme.Color.appTextSecondary)
            Text(value)
                .font(.system(size: 21, weight: .heavy)).tracking(-0.5).monospacedDigit()
                .foregroundStyle(Theme.Color.appText)
                .lineLimit(1).minimumScaleFactor(0.6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Spacing.s3)
    }

    private var loadingBody: some View {
        ScrollView {
            VStack(spacing: Spacing.s3) {
                Shimmer(height: 76, cornerRadius: Radii.xl)
                PkgRowCard {
                    ForEach(0..<4, id: \.self) { i in
                        HStack(spacing: 11) {
                            Shimmer(width: 34, height: 34, cornerRadius: Radii.pill)
                            VStack(alignment: .leading, spacing: 6) { Shimmer(width: 120, height: 11); Shimmer(width: 80, height: 8) }
                            Spacer()
                            Shimmer(width: 56, height: 14)
                        }
                        .padding(.vertical, 13)
                        if i < 3 { Divider().background(Theme.Color.appBorder) }
                    }
                }
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s3)
        }
    }
}

// MARK: - Row

private struct InvoiceRow: View {
    let reference: String
    let amount: String
    let accent: Color
    let accentBg: Color
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 11) {
                Icon(.receipt, size: 16, color: accent)
                    .frame(width: 34, height: 34)
                    .background(accentBg)
                    .clipShape(Circle())
                VStack(alignment: .leading, spacing: 1) {
                    Text("Invoice").font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.Color.appText)
                    Text(reference)
                        .font(.system(size: 10.5, design: .monospaced)).foregroundStyle(Theme.Color.appTextSecondary)
                }
                Spacer()
                Text(amount)
                    .font(.system(size: 13.5, weight: .bold)).monospacedDigit()
                    .foregroundStyle(Theme.Color.appText)
            }
            .padding(.vertical, 11)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Invoice \(reference), \(amount)")
    }
}
