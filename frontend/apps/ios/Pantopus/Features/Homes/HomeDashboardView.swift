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
    @State private var showsInviteOwner = false
    private let homeId: String
    private let onBack: (() -> Void)?
    private let onClaimOwnership: (() -> Void)?
    private let onOpenClaimsList: (() -> Void)?
    /// Route to the Bills list for this home (T5.2.2).
    private let onOpenBills: (() -> Void)?
    /// Route to the Polls list for this home (T6.3e).
    private let onOpenPolls: (() -> Void)?
    /// Host-supplied navigation for actions whose dedicated screen
    /// isn't built yet (Log package, Add mail, etc). Receives the
    /// human-readable action label.
    private let onOpenPlaceholder: ((String) -> Void)?
    /// Push onto the host stack when the user taps the Pets quick-action
    /// tile. Receives this home's id so the destination can pre-fetch.
    private let onOpenPets: ((String) -> Void)?
    /// Push onto the host stack when the user taps the Calendar
    /// quick-action tile (T6.4c / P18).
    private let onOpenCalendar: ((String) -> Void)?
    /// Push onto the host stack when the user taps the Packages
    /// quick-action tile. Receives this home's id (T6.3d / P14).
    private let onOpenPackages: ((String) -> Void)?
    /// Push onto the host stack when the user taps the Tasks (T6.3c)
    /// quick-action tile. Receives this home's id so the destination
    /// can pre-fetch.
    private let onOpenTasks: ((String) -> Void)?
    /// T6.3b / P10 — Push onto the host stack when the user taps the
    /// Maintenance quick-action tile. Receives this home's id.
    private let onOpenMaintenance: ((String) -> Void)?
    /// Push onto the host stack when the user taps the Members
    /// quick-action tile or "Add member" CTA (T6.3a / P9). Receives
    /// this home's id so the destination can pre-fetch the roster.
    private let onOpenMembers: ((String) -> Void)?

    init(
        homeId: String,
        onBack: (() -> Void)? = nil,
        onClaimOwnership: (() -> Void)? = nil,
        onOpenClaimsList: (() -> Void)? = nil,
        onOpenBills: (() -> Void)? = nil,
        onOpenPolls: (() -> Void)? = nil,
        onOpenPlaceholder: ((String) -> Void)? = nil,
        onOpenPets: ((String) -> Void)? = nil,
        onOpenCalendar: ((String) -> Void)? = nil,
        onOpenPackages: ((String) -> Void)? = nil,
        onOpenTasks: ((String) -> Void)? = nil,
        onOpenMaintenance: ((String) -> Void)? = nil,
        onOpenMembers: ((String) -> Void)? = nil
    ) {
        _viewModel = State(initialValue: HomeDashboardViewModel(homeId: homeId))
        self.homeId = homeId
        self.onBack = onBack
        self.onClaimOwnership = onClaimOwnership
        self.onOpenClaimsList = onOpenClaimsList
        self.onOpenBills = onOpenBills
        self.onOpenPolls = onOpenPolls
        self.onOpenPlaceholder = onOpenPlaceholder
        self.onOpenPets = onOpenPets
        self.onOpenCalendar = onOpenCalendar
        self.onOpenPackages = onOpenPackages
        self.onOpenTasks = onOpenTasks
        self.onOpenMaintenance = onOpenMaintenance
        self.onOpenMembers = onOpenMembers
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
        .accessibilityIdentifier("homeDashboard")
        .onAppear { Analytics.track(.screenHomeDashboardViewed) }
        .task { await viewModel.load() }
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
                VStack(spacing: Spacing.s4) {
                    if !content.isVerifiedOwner {
                        ClaimOwnershipBanner(
                            onClaim: { onClaimOwnership?() },
                            onViewClaims: { onOpenClaimsList?() }
                        )
                        .padding(.horizontal, Spacing.s4)
                    }
                    GridTabsBody(
                        quickActions: content.quickActions,
                        tabs: content.tabs,
                        selectedTab: Binding(
                            get: { viewModel.selectedTab },
                            set: { viewModel.selectedTab = $0 }
                        ),
                        onQuickAction: { handleQuickAction($0) },
                        overview: {
                            HomeOverviewSection(content: content)
                        }
                    )
                }
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
                currentUserEmail: currentUserEmail
            ) { showsInviteOwner = false }
        }
    }

    private func handleFabAction(_ action: String) {
        switch action {
        case "add_member":
            // Prefer the dedicated Members screen when its host wired
            // the callback (T6.3a). Falls back to the legacy
            // InviteOwnerForm sheet for older hosts.
            if let onOpenMembers {
                onOpenMembers(homeId)
            } else {
                showsInviteOwner = true
            }
        default:
            onOpenPlaceholder?(actionLabel(action))
        }
    }

    private func handleQuickAction(_ action: String) {
        switch action {
        case "verify":
            onClaimOwnership?()
        case "add_member":
            if let onOpenMembers {
                onOpenMembers(homeId)
            } else {
                showsInviteOwner = true
            }
        case "view_bills":
            onOpenBills?()
        case "view_polls":
            onOpenPolls?()
        case "view_maintenance":
            onOpenMaintenance?(homeId)
        case "pets":
            onOpenPets?(homeId)
        case "calendar":
            onOpenCalendar?(homeId)
        case "view_packages":
            onOpenPackages?(homeId)
        case "view_tasks":
            onOpenTasks?(homeId)
        default:
            onOpenPlaceholder?(actionLabel(action))
        }
    }

    private func actionLabel(_ id: String) -> String {
        switch id {
        case "log_package": "Log a package"
        case "view_packages": "Packages"
        case "add_member": "Add member"
        case "add_mail": "Add mail"
        case "verify": "Verify home"
        case "view_bills": "Bills"
        case "view_polls": "Polls"
        case "view_maintenance": "Maintenance"
        case "pets": "Pets"
        case "calendar": "Calendar"
        case "view_tasks": "Tasks"
        default: id.replacingOccurrences(of: "_", with: " ").capitalized
        }
    }
}

// MARK: - Subviews

/// Inline banner shown above the grid-tabs body when the signed-in user
/// is not yet a verified owner of this home. Two CTAs: "Claim" (opens
/// the wizard) and "View status" (opens MyClaimsList).
private struct ClaimOwnershipBanner: View {
    let onClaim: () -> Void
    let onViewClaims: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s2) {
                Icon(.shieldCheck, size: 20, color: Theme.Color.primary600)
                Text("Are you the owner?")
                    .pantopusTextStyle(.body)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.appText)
            }
            Text("Claim this home to unlock private features for owners.")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
            HStack(spacing: Spacing.s3) {
                Button(action: onClaim) {
                    Text("Claim ownership")
                        .pantopusTextStyle(.small)
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.Color.appTextInverse)
                        .padding(.horizontal, Spacing.s4)
                        .padding(.vertical, Spacing.s2)
                        .background(Theme.Color.primary600)
                        .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
                }
                .buttonStyle(.plain)
                .frame(minHeight: 44)
                .accessibilityIdentifier("homeDashboard_claimCTA")

                Button(action: onViewClaims) {
                    Text("View claims")
                        .pantopusTextStyle(.small)
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.Color.primary600)
                        .padding(.horizontal, Spacing.s4)
                        .padding(.vertical, Spacing.s2)
                }
                .buttonStyle(.plain)
                .frame(minHeight: 44)
                .accessibilityIdentifier("homeDashboard_viewClaimsCTA")
            }
        }
        .padding(Spacing.s4)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.primary600.opacity(0.4), lineWidth: 1)
        )
    }
}

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
