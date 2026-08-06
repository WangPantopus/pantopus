//
//  PublicProfileView.swift
//  Pantopus
//
//  Public profile screen wired through `ContentDetailShell` with
//  `BeaconBanner` + `BeaconIdentityBlock` + `StatsTabsBody`.
//
//  P6.5 — Differentiates between Persona (creator) and Local (verified
//  neighbor) profiles. The view-model picks the kind from the loaded
//  profile's metadata, then this view swaps:
//
//    - `BeaconBanner` identity tint (sky `.personal` vs green `.home`),
//    - in-header chips ("Persona · Verified" gold tier vs "Verified
//      neighbor" green shield),
//    - `BeaconIdentityBlock` action area (share + Follow vs Connect +
//      Message — P8.6 moved these in-header from the old sticky footer),
//    - post styling beneath the identity block (broadcasts with a tier
//      visibility chip + locked-paywall overlay vs Pulse-style posts
//      with an intent chip), incl. the full empty-state card.
//

import SwiftUI

/// Public profile entry point.
@MainActor
public struct PublicProfileView: View {
    @State private var viewModel: PublicProfileViewModel
    @State private var showReportSheet = false
    private let onBack: @MainActor () -> Void
    private let onOpenMessages: @MainActor (PublicProfile) -> Void
    private let onEditPersona: @MainActor () -> Void
    private let onOpenInsights: @MainActor () -> Void
    private let onComposeBroadcast: @MainActor () -> Void

    public init(
        userId: String,
        currentUserId: String? = nil,
        onBack: @escaping @MainActor () -> Void,
        onOpenMessages: @escaping @MainActor (PublicProfile) -> Void = { _ in },
        onEditPersona: @escaping @MainActor () -> Void = {},
        onOpenInsights: @escaping @MainActor () -> Void = {},
        onComposeBroadcast: @escaping @MainActor () -> Void = {}
    ) {
        // No call site threads the signed-in id through, so fall back to the
        // session when the caller doesn't supply one — otherwise `isOwner`
        // (and the whole persona-owner chrome) is permanently false.
        _viewModel = State(
            initialValue: PublicProfileViewModel(
                userId: userId,
                currentUserId: currentUserId ?? PublicProfileViewModel.signedInUserId()
            )
        )
        self.onBack = onBack
        self.onOpenMessages = onOpenMessages
        self.onEditPersona = onEditPersona
        self.onOpenInsights = onOpenInsights
        self.onComposeBroadcast = onComposeBroadcast
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            content
            if let toast = viewModel.toastMessage {
                ToastView(message: ToastMessage(text: toast, kind: .neutral))
                    .padding(.bottom, Spacing.s10)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .task(id: toast) {
                        try? await Task.sleep(nanoseconds: 2_000_000_000)
                        viewModel.toastMessage = nil
                    }
            }
        }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .confirmationDialog(
            "More",
            isPresented: Binding(
                get: { viewModel.showOverflow },
                set: { viewModel.showOverflow = $0 }
            ),
            titleVisibility: .hidden
        ) {
            Button("Block this user", role: .destructive) {
                Task { await viewModel.block() }
            }
            Button("Report") { showReportSheet = true }
            Button("Cancel", role: .cancel) {}
        }
        .sheet(isPresented: $showReportSheet) {
            reportSheet
        }
        .sheet(
            isPresented: Binding(
                get: { viewModel.showFollowHandshake },
                set: { viewModel.showFollowHandshake = $0 }
            ),
            onDismiss: {
                viewModel.clearHandshakeTier()
                Task { await viewModel.refresh() }
            },
            content: {
                PrivacyHandshakeWizardView(
                    viewModel: PrivacyHandshakeViewModel(
                        personaHandle: viewModel.loadedPersonaHandle,
                        preselectedTierRank: viewModel.handshakePreselectedTierRank
                    ) {
                        Task { @MainActor in
                            viewModel.showFollowHandshake = false
                            viewModel.clearHandshakeTier()
                        }
                    }
                )
            }
        )
        .accessibilityIdentifier("publicProfile")
        .task { await viewModel.load() }
    }

    @ViewBuilder private var reportSheet: some View {
        if case let .loaded(payload) = viewModel.state {
            ReportUserSheet(
                userId: payload.profile.id,
                handle: payload.header.handle,
                displayName: payload.header.displayName,
                onClose: { showReportSheet = false },
                onSubmitted: {
                    showReportSheet = false
                    viewModel.toastMessage = "Report received"
                }
            )
        }
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            LoadingLayout(onBack: onBack)
        case let .loaded(payload):
            loadedLayout(payload)
        case let .error(message):
            ErrorLayout(message: message, onBack: onBack) {
                Task { await viewModel.refresh() }
            }
        }
    }

    @ViewBuilder
    private func loadedLayout(_ payload: PublicProfileContent) -> some View {
        if payload.kind == .local, let neighbor = payload.neighbor {
            NeighborProfileLayout(
                content: neighbor,
                selectedTab: Binding(
                    get: { viewModel.selectedNeighborTab },
                    set: { viewModel.selectedNeighborTab = $0 }
                ),
                connectState: viewModel.connectState,
                onBack: onBack,
                onMessage: { onOpenMessages(payload.profile) },
                onConnect: { Task { await viewModel.connect() } },
                onReport: { showReportSheet = true },
                onBlock: { Task { await viewModel.block() } },
                onOverflow: { viewModel.showOverflow = true }
            )
        } else {
            personaLayout(payload)
        }
    }

    private func personaLayout(_ payload: PublicProfileContent) -> some View {
        ContentDetailShell(
            title: nil,
            onBack: onBack,
            topBarAction: ContentDetailTopBarAction(
                icon: .moreHorizontal,
                accessibilityLabel: "More"
            ) {
                Task { @MainActor in viewModel.showOverflow = true }
            },
            header: {
                VStack(spacing: Spacing.s0) {
                    PublicProfileBanner(kind: payload.kind)
                    BeaconIdentityBlock(
                        identity: payload.kind == .persona ? .personal : .home,
                        name: payload.header.displayName,
                        handle: payload.header.handle,
                        tierLabel: payload.header.tierLabel,
                        isVerifiedNeighbor: payload.header.isVerifiedNeighbor,
                        locality: payload.header.locality,
                        bio: payload.stats.bio,
                        isVerified: payload.header.isVerified,
                        avatarURL: payload.header.avatarURL,
                        stats: payload.stats.stats
                    ) {
                        identityActions(for: payload)
                    }
                }
                .accessibilityIdentifier(
                    payload.kind == .persona
                        ? "publicProfilePersonaHeader"
                        : "publicProfileLocalHeader"
                )
            },
            body: {
                VStack(alignment: .leading, spacing: Spacing.s4) {
                    if payload.isOwner {
                        BeaconOwnerAnalyticsStrip(followerStat: payload.stats.stats.first?.value ?? "—") {
                            onOpenInsights()
                        }
                    }
                    StatsTabsBody(
                        content: payload.stats,
                        selectedTab: Binding(
                            get: { viewModel.selectedTab },
                            set: { viewModel.selectedTab = $0 }
                        ),
                        showStats: false,
                        showActionRow: false,
                        onMessage: { onOpenMessages(payload.profile) },
                        onConnect: { Task { await viewModel.connect() } },
                        onOverflow: { viewModel.showOverflow = true }
                    )
                    ReceivedReviewsSection(userId: payload.profile.id)
                    PublicProfilePostsFeed(
                        kind: payload.kind,
                        posts: payload.posts,
                        onUnlock: { post in viewModel.unlockBroadcast(tierRank: post.targetTierRank) },
                        onEmptyCTA: { emptyCTAAction(for: payload) }
                    )
                }
            }
        )
    }

    /// Kind-aware action buttons rendered top-right inside the
    /// `BeaconIdentityBlock` (replacing the former sticky footer).
    @ViewBuilder
    private func identityActions(for payload: PublicProfileContent) -> some View {
        switch payload.kind {
        case .persona:
            if payload.isOwner {
                BeaconHeaderGhostButton(icon: .barChart3, accessibilityLabel: "Insights") {
                    onOpenInsights()
                }
                BeaconHeaderGhostButton(title: "Edit", icon: .pencil, accessibilityLabel: "Edit Persona") {
                    onEditPersona()
                }
            } else {
                BeaconHeaderGhostButton(icon: .share, accessibilityLabel: "Share profile") {
                    viewModel.showOverflow = true
                }
                BeaconHeaderPrimaryButton(title: "Follow", icon: .plus) {
                    viewModel.follow()
                }
            }
        case .local:
            BeaconHeaderGhostButton(
                title: viewModel.connectState == .succeeded ? "Requested" : "Connect",
                icon: .userPlus,
                accessibilityLabel: viewModel.connectState == .succeeded ? "Requested" : "Connect"
            ) {
                Task { await viewModel.connect() }
            }
            BeaconHeaderPrimaryButton(title: "Message", icon: .messageSquare) {
                onOpenMessages(payload.profile)
            }
        }
    }

    /// First-touch action behind the posts-feed empty-state CTA.
    private func emptyCTAAction(for payload: PublicProfileContent) {
        switch payload.kind {
        case .persona:
            if payload.isOwner {
                onComposeBroadcast()
            } else {
                viewModel.follow()
            }
        case .local:
            onOpenMessages(payload.profile)
        }
    }
}

private struct LoadingLayout: View {
    let onBack: @MainActor () -> Void

    var body: some View {
        VStack(spacing: Spacing.s0) {
            ContentDetailTopBar(title: nil, onBack: onBack, action: nil)
            VStack(spacing: Spacing.s4) {
                Shimmer(width: 72, height: 72, cornerRadius: 36)
                    .padding(.top, Spacing.s5)
                Shimmer(width: 160, height: 22, cornerRadius: Radii.sm)
                Shimmer(width: 220, height: 12, cornerRadius: Radii.sm)
                Shimmer(height: 80, cornerRadius: Radii.lg)
                    .padding(.horizontal, Spacing.s4)
                Shimmer(height: 42, cornerRadius: Radii.lg)
                    .padding(.horizontal, Spacing.s4)
            }
            Spacer()
        }
        .background(Theme.Color.appBg)
    }
}

private struct ErrorLayout: View {
    let message: String
    let onBack: @MainActor () -> Void
    let onRetry: @MainActor () -> Void

    var body: some View {
        VStack(spacing: Spacing.s0) {
            ContentDetailTopBar(title: nil, onBack: onBack, action: nil)
            EmptyState(
                icon: .alertCircle,
                headline: "Couldn't load this profile",
                subcopy: message,
                cta: EmptyState.CTA(title: "Try again") { await MainActor.run { onRetry() } }
            )
            .frame(maxHeight: .infinity)
        }
        .background(Theme.Color.appBg)
    }
}

#Preview {
    PublicProfileView(userId: "preview") {}
}
