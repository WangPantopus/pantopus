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

    private static let actionLabels: [String: String] = [
        "log_package": "Log a package",
        "view_packages": "Packages",
        "add_member": "Add member",
        "add_mail": "Add mail",
        "verify": "Verify home",
        "view_bills": "Bills",
        "view_polls": "Polls",
        "view_maintenance": "Maintenance",
        "pets": "Pets",
        "calendar": "Calendar",
        "view_docs": "Documents",
        "view_emergency": "Emergency info",
        "view_tasks": "Tasks",
        "access_codes": "Access codes",
        "view_claims": "Claims"
    ]
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
    /// Push onto the host stack when the user taps the Documents
    /// quick-action tile (T6.4b / P17).
    private let onOpenDocs: ((String) -> Void)?
    /// Push onto the host stack when the user taps the Emergency info
    /// quick-action tile (T6.4b / P17).
    private let onOpenEmergency: ((String) -> Void)?
    /// Push onto the host stack when the user taps the Packages
    /// quick-action tile. Receives this home's id (T6.3d / P14).
    private let onOpenPackages: ((String) -> Void)?
    /// Push onto the host stack when the user taps the Access codes
    /// onboarding step. Receives this home's id and optional display name.
    private let onOpenAccessCodes: ((String, String?) -> Void)?
    /// Push onto the host stack when the user taps the Tasks (T6.3c)
    /// quick-action tile. Receives this home's id so the destination
    /// can pre-fetch.
    private let onOpenTasks: ((String) -> Void)?
    /// T6.3b / P10 - Push onto the host stack when the user taps the
    /// Maintenance quick-action tile. Receives this home's id.
    private let onOpenMaintenance: ((String) -> Void)?
    /// Push onto the host stack when the user taps the Members
    /// quick-action tile or "Add member" CTA (T6.3a / P9). Receives
    /// this home's id so the destination can pre-fetch the roster.
    private let onOpenMembers: ((String) -> Void)?
    /// A.4 / A13.5 - Push onto the host stack when the user taps the
    /// "Property details" affordance in the Overview section. Receives
    /// this home's id so the destination can resolve the property.
    private let onOpenPropertyDetails: ((String) -> Void)?
    /// A14.1 (P5.1) — Push onto the host stack when the user taps the
    /// top-bar settings affordance. Routes to the per-home Settings
    /// index. Typed `@MainActor @Sendable` because the closure is
    /// captured inside the `ContentDetailTopBarAction`'s Sendable
    /// handler.
    private let onOpenSettings: (@MainActor @Sendable (String) -> Void)?

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
        onOpenDocs: ((String) -> Void)? = nil,
        onOpenEmergency: ((String) -> Void)? = nil,
        onOpenPackages: ((String) -> Void)? = nil,
        onOpenAccessCodes: ((String, String?) -> Void)? = nil,
        onOpenTasks: ((String) -> Void)? = nil,
        onOpenMaintenance: ((String) -> Void)? = nil,
        onOpenMembers: ((String) -> Void)? = nil,
        onOpenPropertyDetails: ((String) -> Void)? = nil,
        onOpenSettings: (@MainActor @Sendable (String) -> Void)? = nil
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
        self.onOpenDocs = onOpenDocs
        self.onOpenEmergency = onOpenEmergency
        self.onOpenPackages = onOpenPackages
        self.onOpenAccessCodes = onOpenAccessCodes
        self.onOpenTasks = onOpenTasks
        self.onOpenMaintenance = onOpenMaintenance
        self.onOpenMembers = onOpenMembers
        self.onOpenPropertyDetails = onOpenPropertyDetails
        self.onOpenSettings = onOpenSettings
    }

    /// Current signed-in user's email; used by the Invite Owner form
    /// to reject self-invites. Returns empty when in preview mode.
    private var currentUserEmail: String {
        if case let .signedIn(user) = auth.state { return user.email }
        return ""
    }

    var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                HomeDashboardLoadingView(onBack: onBack)
            case let .loaded(content):
                dashboardBody(for: content, brandNew: nil)
            case let .empty(brandNew):
                dashboardBody(for: brandNew.content, brandNew: brandNew)
            case let .needsAttention(content):
                dashboardBody(for: content, brandNew: nil)
            case let .error(message):
                HomeDashboardErrorView(message: message, onBack: onBack) { Task { await viewModel.refresh() } }
            }
        }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .accessibilityIdentifier("homeDashboard")
        .onAppear { Analytics.track(.screenHomeDashboardViewed) }
        .task { await viewModel.load() }
    }

    private func dashboardBody(
        for content: HomeDashboardContent,
        brandNew: HomeDashboardBrandNewContent?
    ) -> some View {
        ContentDetailShell(
            title: "Home",
            onBack: onBack,
            topBarAction: onOpenSettings.map { handler in
                let id = homeId
                return ContentDetailTopBarAction(
                    icon: .slidersHorizontal,
                    accessibilityLabel: "Home settings"
                ) {
                    Task { @MainActor in handler(id) }
                }
            },
            header: {
                HomeHeroHeader(
                    address: content.address,
                    verified: content.verified,
                    stats: content.stats
                )
            },
            body: {
                VStack(spacing: Spacing.s4) {
                    if let attention = content.attentionSummary {
                        NeedsAttentionBanner(summary: attention) { handleQuickAction($0) }
                            .padding(.horizontal, Spacing.s4)
                    }
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
                            if let brandNew {
                                BrandNewHomeSection(brandNew: brandNew) { handleQuickAction($0) }
                            } else {
                                HomeOverviewSection(
                                    content: content,
                                    onOpenEmergency: { onOpenEmergency?(homeId) },
                                    onOpenPropertyDetails: { onOpenPropertyDetails?(homeId) }
                                )
                            }
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
            openMembersOrInvite()
        default:
            onOpenPlaceholder?(actionLabel(action))
        }
    }

    private func handleQuickAction(_ action: String) {
        if let handler = quickActionHandlers[action] {
            handler()
        } else {
            onOpenPlaceholder?(actionLabel(action))
        }
    }

    private var quickActionHandlers: [String: () -> Void] {
        [
            "verify": { onClaimOwnership?() },
            "add_member": { openMembersOrInvite() },
            "view_bills": { onOpenBills?() },
            "view_polls": { onOpenPolls?() },
            "view_maintenance": { onOpenMaintenance?(homeId) },
            "pets": { onOpenPets?(homeId) },
            "calendar": { onOpenCalendar?(homeId) },
            "view_docs": { onOpenDocs?(homeId) },
            "view_emergency": { onOpenEmergency?(homeId) },
            "view_packages": { onOpenPackages?(homeId) },
            "access_codes": { onOpenAccessCodes?(homeId, currentAddress) },
            "view_tasks": { onOpenTasks?(homeId) },
            "view_claims": { onOpenClaimsList?() }
        ]
    }

    private func openMembersOrInvite() {
        // Prefer the dedicated Members screen when its host wired the
        // callback. Falls back to the legacy InviteOwnerForm sheet.
        if let onOpenMembers {
            onOpenMembers(homeId)
        } else {
            showsInviteOwner = true
        }
    }

    private func actionLabel(_ id: String) -> String {
        Self.actionLabels[id] ?? id.replacingOccurrences(of: "_", with: " ").capitalized
    }

    private var currentAddress: String? {
        switch viewModel.state {
        case let .loaded(content), let .needsAttention(content):
            content.address
        case let .empty(brandNew):
            brandNew.content.address
        case .loading, .error:
            nil
        }
    }
}

#Preview {
    HomeDashboardView(homeId: "preview")
        .environment(AuthManager.previewSignedIn)
}
