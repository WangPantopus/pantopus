//
//  HomeDashboardView.swift
//  Pantopus
//
//  Concrete content-detail screen for a Home. Hero header + grid-tabs
//  body + FAB CTA.
//

import SwiftUI

/// Home Dashboard screen wired to `GET /api/homes/:id` (with public-profile fallback).
struct HomeDashboardView: View {
    @State private var viewModel: HomeDashboardViewModel
    @State private var toast: String?
    private let onBack: (() -> Void)?

    init(homeId: String, onBack: (() -> Void)? = nil) {
        _viewModel = State(initialValue: HomeDashboardViewModel(homeId: homeId))
        self.onBack = onBack
    }

    var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                LoadingView(onBack: onBack)
            case .loaded(let content):
                loadedBody(for: content)
            case .error(let message):
                ErrorView(message: message, onBack: onBack) { Task { await viewModel.refresh() } }
            }
        }
        .task { await viewModel.load() }
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast)
                    .pantopusTextStyle(.small)
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, Spacing.s4)
                    .padding(.vertical, Spacing.s2)
                    .background(Theme.Color.appText.opacity(0.9))
                    .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
                    .padding(.bottom, Spacing.s10)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: toast)
    }

    @ViewBuilder
    private func loadedBody(for content: HomeDashboardContent) -> some View {
        ContentDetailShell(
            title: "Home",
            onBack: onBack,
            header: {
                HomeHeroHeader(
                    address: content.address,
                    verified: content.verified,
                    stats: content.stats
                )
            },
            body: {
                GridTabsBody(
                    quickActions: content.quickActions,
                    tabs: content.tabs,
                    selectedTab: Binding(
                        get: { viewModel.selectedTab },
                        set: { viewModel.selectedTab = $0 }
                    ),
                    onQuickAction: { showPlaceholderToast(for: $0) }
                ) {
                    HomeOverviewSection(content: content)
                }
            },
            cta: {
                FABCreateCTA(
                    actions: [
                        FABSheetAction(id: "log_package", title: "Log a package", icon: .shoppingBag),
                        FABSheetAction(id: "add_member", title: "Add member", icon: .userPlus),
                        FABSheetAction(id: "add_mail", title: "Add mail", icon: .mailbox),
                    ]
                ) { showPlaceholderToast(for: $0) }
            }
        )
    }

    private func showPlaceholderToast(for action: String) {
        toast = "\(actionLabel(action)) isn't available yet"
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            toast = nil
        }
    }

    private func actionLabel(_ id: String) -> String {
        switch id {
        case "log_package": "Log a package"
        case "add_member": "Add member"
        case "add_mail": "Add mail"
        case "verify": "Verify home"
        default: "That"
        }
    }
}

// MARK: - Subviews

private struct HomeOverviewSection: View {
    let content: HomeDashboardContent

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            SectionHeader("Summary")
            KeyFactsPanel(rows: [
                KeyFactRow(id: "address", label: "Address", value: content.address),
                KeyFactRow(id: "status", label: "Status", value: content.verified ? "Verified" : "Unverified"),
                KeyFactRow(
                    id: "members",
                    label: "Members",
                    value: content.stats.first { $0.id == "members" }?.value ?? "—"
                ),
            ])
        }
    }
}

private struct LoadingView: View {
    let onBack: (() -> Void)?

    var body: some View {
        ContentDetailShell(
            title: "Home",
            onBack: onBack,
            header: {
                Shimmer(height: 180, cornerRadius: Radii.xl2)
                    .padding(.horizontal, Spacing.s4)
            },
            body: {
                VStack(spacing: Spacing.s3) {
                    Shimmer(height: 80, cornerRadius: Radii.md)
                    Shimmer(height: 40, cornerRadius: Radii.sm)
                    Shimmer(height: 120, cornerRadius: Radii.lg)
                }
                .padding(.horizontal, Spacing.s4)
            },
            cta: { NoCTA() }
        )
    }
}

private struct ErrorView: View {
    let message: String
    let onBack: (() -> Void)?
    let onRetry: () -> Void

    var body: some View {
        ContentDetailShell(
            title: "Home",
            onBack: onBack,
            header: { EmptyView() },
            body: {
                EmptyState(
                    icon: .alertCircle,
                    headline: "Couldn't load this home",
                    subcopy: message,
                    cta: EmptyState.CTA(title: "Try again") { await MainActor.run { onRetry() } }
                )
                .frame(height: 400)
            },
            cta: { NoCTA() }
        )
    }
}

#Preview {
    HomeDashboardView(homeId: "preview")
}
