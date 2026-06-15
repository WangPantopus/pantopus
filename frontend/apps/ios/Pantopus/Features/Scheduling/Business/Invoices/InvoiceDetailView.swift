//
//  InvoiceDetailView.swift
//  Pantopus
//
//  G13 Invoice Detail (owner) — Stream I15. Mono reference header, total hero,
//  payer→payee identity cards, parsed line-items table, and a Send / Share dock.
//  Matches `invoicedetail-frames.jsx` within the InvoiceDTO's available fields.
//  Tokens only.
//

import SwiftUI

struct InvoiceDetailView: View {
    @State private var model: InvoiceDetailViewModel
    @Environment(\.dismiss) private var dismiss

    init(
        owner: SchedulingOwner,
        invoiceId: String,
        push: @escaping @MainActor (SchedulingRoute) -> Void,
        client: SchedulingClient = .shared
    ) {
        _model = State(wrappedValue: InvoiceDetailViewModel(owner: owner, invoiceId: invoiceId, push: push, client: client))
    }

    var body: some View {
        VStack(spacing: Spacing.s0) {
            PkgTopBar(title: "Invoice", onBack: { dismiss() })
            content
        }
        .background(Theme.Color.appBg)
        .navigationBarBackButtonHidden(true)
        .overlay(alignment: .top) { sentToast }
        .task { await model.load() }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .accessibilityIdentifier("scheduling.invoiceDetail")
    }

    @ViewBuilder
    private var content: some View {
        switch model.phase {
        case .loading:
            loadingBody
        case .comingSoon:
            PkgComingSoon(title: "Invoice")
        case let .error(message):
            PkgErrorState(message: message) { Task { await model.load() } }
        case .loaded:
            loaded
        }
    }

    private var loaded: some View {
        VStack(spacing: Spacing.s0) {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.s0) {
                    Text("\(model.reference) · issued \(model.issuedLabel)")
                        .font(.system(size: 10.5, design: .monospaced)).foregroundStyle(Theme.Color.appTextSecondary)
                    hero
                    payerPayee.padding(.top, 14)
                    lineItemsSection.padding(.top, 16)
                    Color.clear.frame(height: Spacing.s10)
                }
                .padding(.horizontal, Spacing.s4)
                .padding(.top, Spacing.s2)
            }
            dock
        }
    }

    private var hero: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(model.totalLabel)
                .font(.system(size: 30, weight: .heavy)).tracking(-1.1).monospacedDigit()
                .foregroundStyle(Theme.Color.appText)
            Text("total · \(model.currencyCode)").font(.system(size: 11.5, weight: .medium)).foregroundStyle(Theme.Color.appTextSecondary)
        }
        .padding(.top, 16)
    }

    private var payerPayee: some View {
        HStack(spacing: 8) {
            identityCard(label: "From", name: "\(model.theme.title) provider", sub: model.theme.title, color: model.accent)
            identityCard(label: "To", name: "Customer", sub: model.recipientLabel, color: Theme.Color.personal)
        }
    }

    private func identityCard(label: String, name: String, sub: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label.uppercased()).font(.system(size: 8.5, weight: .bold)).tracking(0.4).foregroundStyle(Theme.Color.appTextMuted)
            Text(name).font(.system(size: 12.5, weight: .bold)).foregroundStyle(Theme.Color.appText).lineLimit(1)
            HStack(spacing: 4) {
                Circle().fill(color).frame(width: 6, height: 6)
                Text(sub).font(.system(size: 9.5, weight: .semibold)).foregroundStyle(color).lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 11).padding(.vertical, 10)
        .background(Theme.Color.appSurface)
        .overlay(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous).stroke(Theme.Color.appBorder, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }

    private var lineItemsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Icon(.list, size: 13, color: Theme.Color.appTextSecondary)
                Text("LINE ITEMS").font(.system(size: 9.5, weight: .bold)).tracking(0.8).foregroundStyle(Theme.Color.appTextSecondary)
            }
            if model.lineItems.isEmpty {
                Text("Itemized details aren't available for this invoice.")
                    .font(.system(size: 11.5)).foregroundStyle(Theme.Color.appTextSecondary)
                    .padding(.horizontal, 12).padding(.vertical, 14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.Color.appSurface)
                    .overlay(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous).stroke(Theme.Color.appBorder, lineWidth: 1))
                    .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            } else {
                table
            }
        }
    }

    private var table: some View {
        VStack(spacing: Spacing.s0) {
            HStack {
                Text("ITEM").frame(maxWidth: .infinity, alignment: .leading)
                Text("QTY").frame(width: 32, alignment: .center)
                Text("TOTAL").frame(width: 72, alignment: .trailing)
            }
            .font(.system(size: 8.5, weight: .bold)).tracking(0.6).foregroundStyle(Theme.Color.appTextMuted)
            .padding(.horizontal, 11).padding(.vertical, 7)
            .background(Theme.Color.appSurfaceRaised)
            ForEach(model.lineItems) { item in
                HStack {
                    Text(item.label).font(.system(size: 11)).foregroundStyle(Theme.Color.appText)
                        .frame(maxWidth: .infinity, alignment: .leading).lineLimit(2)
                    Text(item.quantity.map(String.init) ?? "—")
                        .font(.system(size: 11)).foregroundStyle(Theme.Color.appTextSecondary)
                        .frame(width: 32, alignment: .center)
                    Text(model.lineTotalLabel(item))
                        .font(.system(size: 11, weight: .semibold)).monospacedDigit()
                        .foregroundStyle(Theme.Color.appText)
                        .frame(width: 72, alignment: .trailing)
                }
                .padding(.horizontal, 11).padding(.vertical, 9)
                Divider().background(Theme.Color.appBorderSubtle)
            }
            HStack {
                Text("Total").font(.system(size: 12, weight: .bold)).foregroundStyle(Theme.Color.appText)
                Spacer()
                Text(model.totalLabel).font(.system(size: 15, weight: .heavy)).monospacedDigit().foregroundStyle(Theme.Color.appText)
            }
            .padding(.horizontal, 11).padding(.vertical, 9)
            .background(Theme.Color.appSurfaceRaised)
        }
        .background(Theme.Color.appSurface)
        .overlay(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous).stroke(Theme.Color.appBorder, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }

    private var dock: some View {
        PkgDock {
            ShareLink(item: model.shareText) {
                HStack(spacing: 7) {
                    Icon(.share, size: 15, color: Theme.Color.appText)
                    Text("Share").font(.system(size: 13.5, weight: .bold)).foregroundStyle(Theme.Color.appText)
                }
                .frame(maxWidth: .infinity).frame(height: 46)
                .overlay(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous).stroke(Theme.Color.appBorderStrong, lineWidth: 1))
            }
            .accessibilityLabel("Share invoice")
            PkgPrimaryButton(label: "Send", icon: .send, loading: model.sending) { Task { await model.send() } }
        }
    }

    private var sentToast: some View {
        Group {
            if model.showSentToast {
                HStack(spacing: Spacing.s2) {
                    Icon(.check, size: 15, strokeWidth: 3, color: Theme.Color.success)
                    Text("Invoice sent").font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.Color.appTextInverse)
                }
                .padding(.horizontal, Spacing.s4).padding(.vertical, 10)
                .background(Theme.Color.appText)
                .clipShape(Capsule())
                .pantopusShadow(.lg)
                .padding(.top, Spacing.s3)
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.2), value: model.showSentToast)
    }

    private var loadingBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s3) {
                Shimmer(width: 200, height: 10)
                Shimmer(width: 160, height: 30)
                HStack(spacing: 8) { Shimmer(height: 64, cornerRadius: Radii.lg); Shimmer(height: 64, cornerRadius: Radii.lg) }
                Shimmer(height: 160, cornerRadius: Radii.lg)
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s4)
        }
    }
}
