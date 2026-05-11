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
    @Environment(AuthManager.self) private var auth
    @State private var viewModel: HomeDashboardViewModel
    @State private var toast: String?
    @State private var showsInviteOwner = false
    private let homeId: String
    private let onBack: (() -> Void)?

    init(homeId: String, onBack: (() -> Void)? = nil) {
        _viewModel = State(initialValue: HomeDashboardViewModel(homeId: homeId))
        self.homeId = homeId
        self.onBack = onBack
    }

    /// Current signed-in user's email — used by the Invite Owner form
    /// to reject self-invites. Returns empty when in preview mode.
    private var currentUserEmail: String {
        if case let .signedIn(user) = auth.state { return user.email }
        return ""
    }

    var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                LoadingView(onBack: onBack)
            case let .loaded(content):
                loadedBody(for: content)
            case let .error(message):
                ErrorView(message: message, onBack: onBack) { Task { await viewModel.refresh() } }
            }
        }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .onAppear { Analytics.track(.screenHomeDashboardViewed) }
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
                    onQuickAction: { showPlaceholderToast(for: $0) },
                    overview: {
                        HomeOverviewSection(content: content)
                    }
                )
            },
            cta: {
                FABCreateCTA(
                    actions: [
                        FABSheetAction(id: "log_package", title: "Log a package", icon: .shoppingBag),
                        FABSheetAction(id: "add_member", title: "Invite owner", icon: .userPlus),
                        FABSheetAction(id: "add_mail", title: "Add mail", icon: .mailbox)
                    ]
                ) { handleFabAction($0) }
            }
        )
        .sheet(isPresented: $showsInviteOwner) {
            InviteOwnerFormView(
                homeId: homeId,
                currentUserEmail: currentUserEmail,
                onClose: { showsInviteOwner = false }
            )
        }
    }

    private func handleFabAction(_ action: String) {
        switch action {
        case "add_member":
            showsInviteOwner = true
        default:
            showPlaceholderToast(for: action)
        }
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
                )
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
        .environment(AuthManager.previewSignedIn)
}
