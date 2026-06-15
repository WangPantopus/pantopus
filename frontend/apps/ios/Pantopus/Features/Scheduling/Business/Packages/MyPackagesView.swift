//
//  MyPackagesView.swift
//  Pantopus
//
//  G11 My Packages / Credits (customer) — Stream I15. Credit cards with a
//  remaining-sessions meter; "book with a credit" opens the apply-credit sheet,
//  "buy again" routes to checkout. Buyer chrome is personal sky; each card's
//  owner badge carries the owner pillar accent. Matches `mypackages-frames.jsx`.
//

import SwiftUI

struct MyPackagesView: View {
    @State private var model: MyPackagesViewModel
    @Environment(\.dismiss) private var dismiss

    init(push: @escaping @MainActor (SchedulingRoute) -> Void, client: SchedulingClient = .shared) {
        _model = State(wrappedValue: MyPackagesViewModel(push: push, client: client))
    }

    var body: some View {
        VStack(spacing: Spacing.s0) {
            PkgTopBar(title: "My packages", onBack: { dismiss() })
            content
        }
        .background(Theme.Color.appBg)
        .navigationBarBackButtonHidden(true)
        .task { await model.load() }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .sheet(item: $model.creditForUse) { credit in
            UseCreditSheet(credit: credit) { await model.creditApplied() }
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .accessibilityIdentifier("scheduling.myPackages")
    }

    @ViewBuilder
    private var content: some View {
        switch model.phase {
        case .loading:
            loadingBody
        case .comingSoon:
            PkgComingSoon(title: "My packages")
        case let .error(message):
            PkgErrorState(message: message) { Task { await model.load() } }
        case .empty:
            EmptyState(
                icon: .tag,
                headline: "No packages yet",
                subcopy: "When you buy a package, your credits show up here.",
                cta: EmptyState.CTA(title: "Browse services") { await MainActor.run { model.browseServices() } },
                tint: Theme.Color.personalBg,
                accent: Theme.Color.personal
            )
            .padding(.top, Spacing.s10)
        case .loaded:
            loadedBody
        }
    }

    private var loadedBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s3) {
                Text("Tap a credit to book your next session.")
                    .font(.system(size: 11.5)).foregroundStyle(Theme.Color.appTextSecondary)
                    .padding(.horizontal, Spacing.s1)
                ForEach(model.credits) { credit in
                    CreditCard(
                        credit: credit,
                        ownerTheme: model.theme(for: credit),
                        remaining: model.remaining(credit),
                        total: model.total(credit),
                        progress: model.progress(credit),
                        spent: model.isSpent(credit),
                        purchased: PackagesFormat.dayString(credit.purchasedAt),
                        onUse: { model.useCredit(credit) },
                        onBuyAgain: { model.buyAgain(credit) }
                    )
                }
                Color.clear.frame(height: Spacing.s8)
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s3)
        }
        .refreshable { await model.refresh() }
    }

    private var loadingBody: some View {
        ScrollView {
            VStack(spacing: Spacing.s3) {
                ForEach(0..<2, id: \.self) { _ in Shimmer(height: 180, cornerRadius: Radii.xl) }
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s3)
        }
    }
}

// MARK: - Credit card

private struct CreditCard: View {
    let credit: PackageCreditDTO
    let ownerTheme: SchedulingIdentityTheme
    let remaining: Int
    let total: Int
    let progress: Double
    let spent: Bool
    let purchased: String?
    let onUse: () -> Void
    let onBuyAgain: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s0) {
            ownerRow
            Text(credit.bookingPackage?.name ?? "Package")
                .font(.system(size: 14, weight: .bold)).foregroundStyle(Theme.Color.appText)
                .padding(.top, 10)
            HStack(alignment: .firstTextBaseline) {
                Text(spent ? "0 of \(total) left" : "\(remaining) of \(total) left")
                    .font(.system(size: 18, weight: .heavy)).tracking(-0.4)
                    .foregroundStyle(spent ? Theme.Color.appTextSecondary : Theme.Color.appText)
                Spacer()
                if spent { PkgChip(text: "All used", tone: .neutral, uppercased: true) }
            }
            .padding(.top, 8).padding(.bottom, 6)
            meter
            if let purchased {
                Text("Purchased \(purchased)").font(.system(size: 10.5)).foregroundStyle(Theme.Color.appTextSecondary)
                    .padding(.top, 7)
            }
            cta.padding(.top, 11)
        }
        .padding(.horizontal, 14).padding(.vertical, 13)
        .background(Theme.Color.appSurface)
        .overlay(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous).stroke(Theme.Color.appBorder, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .pantopusShadow(.sm)
        .opacity(spent ? 0.7 : 1)
    }

    private var ownerRow: some View {
        HStack(spacing: 9) {
            ZStack {
                Circle().fill(SchedulingGradient.linear(for: ownerTheme.title.lowercased()))
                Icon(ownerTheme.icon, size: 13, color: Theme.Color.appTextInverse)
            }
            .frame(width: 28, height: 28)
            Text("\(ownerTheme.title) provider").font(.system(size: 12, weight: .bold)).foregroundStyle(Theme.Color.appText)
            Icon(.badgeCheck, size: 13, color: ownerTheme.accent)
        }
    }

    private var meter: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Theme.Color.appSurfaceSunken)
                Capsule().fill(spent ? Theme.Color.appBorderStrong : Theme.Color.personal)
                    .frame(width: max(0, min(1, spent ? 1 : progress)) * geo.size.width)
            }
        }
        .frame(height: 6)
    }

    @ViewBuilder
    private var cta: some View {
        if spent {
            Button(action: onBuyAgain) {
                Text("Buy again")
                    .font(.system(size: 13, weight: .bold)).foregroundStyle(Theme.Color.primary600)
                    .frame(maxWidth: .infinity).frame(height: 40)
                    .overlay(RoundedRectangle(cornerRadius: 11, style: .continuous).stroke(Theme.Color.appBorder, lineWidth: 1))
            }
            .buttonStyle(.plain)
        } else {
            Button(action: onUse) {
                HStack(spacing: 7) {
                    Icon(.calendarPlus, size: 15, color: Theme.Color.appTextInverse)
                    Text("Book with a credit").font(.system(size: 13.5, weight: .bold)).foregroundStyle(Theme.Color.appTextInverse)
                }
                .frame(maxWidth: .infinity).frame(height: 42)
                .background(Theme.Color.personal)
                .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
            }
            .buttonStyle(.plain)
        }
    }
}
