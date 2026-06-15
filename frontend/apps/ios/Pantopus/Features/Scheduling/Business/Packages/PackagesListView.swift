//
//  PackagesListView.swift
//  Pantopus
//
//  G8 Packages List (owner) — Stream I15. Active / Archived segmented list of
//  the owner's session packages, with the empty + payouts-gate states. Matches
//  `packageslist-frames.jsx`. Tokens only.
//

import SwiftUI

struct PackagesListView: View {
    @State private var model: PackagesListViewModel
    @Environment(\.dismiss) private var dismiss

    init(
        owner: SchedulingOwner,
        push: @escaping @MainActor (SchedulingRoute) -> Void,
        client: SchedulingClient = .shared
    ) {
        _model = State(wrappedValue: PackagesListViewModel(owner: owner, push: push, client: client))
    }

    var body: some View {
        VStack(spacing: Spacing.s0) {
            PkgTopBar(title: "Packages", onBack: { dismiss() }) {
                if model.phase != .comingSoon {
                    PkgTopBarIconButton(icon: .plus, accessibilityLabel: "Create a package") { model.createPackage() }
                }
            }
            content
        }
        .background(Theme.Color.appBg)
        .navigationBarBackButtonHidden(true)
        .task { await model.load() }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .accessibilityIdentifier("scheduling.packages.list")
    }

    @ViewBuilder
    private var content: some View {
        switch model.phase {
        case .loading:
            loadingBody
        case .comingSoon:
            PkgComingSoon(title: "Packages")
        case let .error(message):
            PkgErrorState(message: message) { Task { await model.load() } }
        case .loaded:
            loadedBody
        }
    }

    // MARK: Loaded

    private var loadedBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s3) {
                filterSegment
                if model.filter == .active { intro }
                stateForFilter
                Color.clear.frame(height: Spacing.s8)
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s3)
        }
        .refreshable { await model.refresh() }
    }

    @ViewBuilder
    private var stateForFilter: some View {
        if model.visiblePackages.isEmpty {
            if model.filter == .active {
                if model.showsPayoutsGate {
                    payoutsGate
                } else {
                    emptyActive
                }
            } else {
                emptyArchived
            }
        } else {
            PkgRowCard {
                ForEach(Array(model.visiblePackages.enumerated()), id: \.element.id) { index, package in
                    PackagePill(
                        package: package,
                        subtitle: model.subtitle(for: package),
                        accent: model.accent,
                        accentBg: model.theme.accentBg,
                        onTap: { model.openPackage(package) },
                        onArchive: { Task { await model.archive(package) } },
                        onRestore: { Task { await model.restore(package) } }
                    )
                    if index < model.visiblePackages.count - 1 {
                        Divider().background(Theme.Color.appBorder)
                    }
                }
            }
        }
    }

    private var filterSegment: some View {
        PkgSegmented(
            options: ["Active", "Archived"],
            selectedIndex: model.filter.rawValue,
            accent: model.accent,
            onSelect: { idx in model.filter = PackagesListViewModel.Filter(rawValue: idx) ?? .active }
        )
    }

    private var intro: some View {
        Text("Sell a bundle of sessions at a better rate. Buyers keep their price if you change it later.")
            .font(.system(size: 11.5))
            .foregroundStyle(Theme.Color.appTextSecondary)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, Spacing.s1)
    }

    // MARK: Empty / gate

    private var emptyActive: some View {
        EmptyState(
            icon: .layers,
            headline: "Sell a package of sessions",
            subcopy: "Bundle sessions so regulars can prepay and rebook fast.",
            cta: EmptyState.CTA(title: "Create a package") { await MainActor.run { model.createPackage() } },
            tint: model.theme.accentBg,
            accent: model.accent
        )
        .padding(.top, Spacing.s10)
    }

    private var emptyArchived: some View {
        EmptyState(
            icon: .archive,
            headline: "No archived packages",
            subcopy: "Packages you archive will appear here.",
            tint: Theme.Color.appSurfaceSunken,
            accent: Theme.Color.appTextSecondary
        )
        .padding(.top, Spacing.s10)
    }

    private var payoutsGate: some View {
        VStack(spacing: Spacing.s4) {
            ZStack {
                Circle().fill(model.theme.accentBg).frame(width: 72, height: 72)
                Icon(.layers, size: 32, color: model.accent)
            }
            Text("Sell a package of sessions")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .multilineTextAlignment(.center)
            Text("Bundle sessions so regulars can prepay and rebook fast.")
                .font(.system(size: 14))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            VStack(spacing: 10) {
                HStack(alignment: .top, spacing: 9) {
                    Icon(.lock, size: 16, color: Theme.Color.warning).padding(.top, 1)
                    Text("Set up payouts to sell packages.")
                        .font(.system(size: 11.5, weight: .semibold))
                        .foregroundStyle(Theme.Color.warning)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                Button(action: { model.connectPayments() }) {
                    HStack(spacing: 6) {
                        Icon(.externalLink, size: 14, color: Theme.Color.appTextInverse)
                        Text("Connect payments").font(.system(size: 12.5, weight: .bold)).foregroundStyle(Theme.Color.appTextInverse)
                    }
                    .frame(maxWidth: .infinity).frame(height: 38)
                    .background(Theme.Color.primary600)
                    .clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 14).padding(.vertical, 13)
            .frame(maxWidth: 260)
            .background(Theme.Color.warningBg)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                    .stroke(Theme.Color.warningLight, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        }
        .frame(maxWidth: .infinity)
        .padding(.top, Spacing.s8)
        .padding(.horizontal, Spacing.s4)
    }

    // MARK: Loading

    private var loadingBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s3) {
                Shimmer(height: 36, cornerRadius: 9)
                PkgRowCard {
                    ForEach(0..<3, id: \.self) { i in
                        HStack(spacing: 11) {
                            Shimmer(width: 38, height: 38, cornerRadius: 11)
                            VStack(alignment: .leading, spacing: 6) {
                                Shimmer(width: 150, height: 12)
                                Shimmer(width: 190, height: 9)
                                Shimmer(width: 64, height: 15, cornerRadius: Radii.pill)
                            }
                            Spacer()
                        }
                        .padding(.vertical, 13)
                        if i < 2 { Divider().background(Theme.Color.appBorder) }
                    }
                }
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s3)
        }
    }
}

// MARK: - Row

private struct PackagePill: View {
    let package: SchedulingPackageDTO
    let subtitle: String
    let accent: Color
    let accentBg: Color
    let onTap: () -> Void
    let onArchive: () -> Void
    let onRestore: () -> Void

    private var isArchived: Bool { package.isActive == false }

    var body: some View {
        HStack(spacing: 11) {
            Icon(.layers, size: 19, color: isArchived ? Theme.Color.appTextSecondary : accent)
                .frame(width: 38, height: 38)
                .background(isArchived ? Theme.Color.appSurfaceSunken : accentBg)
                .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(package.name)
                    .font(.system(size: 13.5, weight: .bold)).tracking(-0.1)
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(1)
                Text(subtitle)
                    .font(.system(size: 11)).foregroundStyle(Theme.Color.appTextSecondary)
                    .lineLimit(1)
                PkgChip(text: isArchived ? "Archived" : "Active", tone: isArchived ? .neutral : .success)
                    .padding(.top, 3)
            }
            Spacer(minLength: Spacing.s2)
            trailing
        }
        .padding(.vertical, 12)
        .opacity(isArchived ? 0.7 : 1)
        .contentShape(Rectangle())
        .onTapGesture { if !isArchived { onTap() } }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(package.name), \(subtitle), \(isArchived ? "archived" : "active")")
    }

    @ViewBuilder
    private var trailing: some View {
        if isArchived {
            Button(action: onRestore) {
                Text("Restore")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextStrong)
                    .padding(.horizontal, 12).frame(height: 28)
                    .overlay(Capsule().stroke(Theme.Color.appBorder, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Restore \(package.name)")
        } else {
            Menu {
                Button { onTap() } label: { Label { Text("Edit") } icon: { Icon(.pencil, size: 16, color: Theme.Color.appText) } }
                Button(role: .destructive, action: onArchive) { Label { Text("Archive") } icon: { Icon(.archive, size: 16, color: Theme.Color.error) } }
            } label: {
                Icon(.moreVertical, size: 18, color: Theme.Color.appTextMuted).frame(width: 32, height: 32)
            }
            .accessibilityLabel("More actions for \(package.name)")
        }
    }
}
