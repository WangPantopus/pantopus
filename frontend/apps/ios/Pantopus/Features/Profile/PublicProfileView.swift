//
//  PublicProfileView.swift
//  Pantopus
//
//  Public profile screen wired through `ContentDetailShell` with
//  `ProfileHeader` + `StatsTabsBody` + `ActionRowCTA`.
//
//  P6.5 — Differentiates between Persona (creator) and Local (verified
//  neighbor) profiles. The view-model picks the kind from the loaded
//  profile's metadata, then this view swaps:
//
//    - banner color (sky vs green) above the header,
//    - in-header chips ("Persona · Verified" gold tier vs "Verified
//      neighbor" green shield),
//    - sticky footer CTAs (single Follow vs Message + Connect),
//    - post styling beneath the stats/tabs body (broadcasts with a
//      tier visibility chip + locked-paywall overlay vs Pulse-style
//      posts with an intent chip).
//

import SwiftUI

/// Public profile entry point.
@MainActor
public struct PublicProfileView: View {
    @State private var viewModel: PublicProfileViewModel
    private let onBack: @MainActor () -> Void
    private let onOpenMessages: @MainActor (PublicProfile) -> Void
    private let onOpenReport: @MainActor () -> Void

    public init(
        userId: String,
        onBack: @escaping @MainActor () -> Void,
        onOpenMessages: @escaping @MainActor (PublicProfile) -> Void = { _ in },
        onOpenReport: @escaping @MainActor () -> Void = {}
    ) {
        _viewModel = State(initialValue: PublicProfileViewModel(userId: userId))
        self.onBack = onBack
        self.onOpenMessages = onOpenMessages
        self.onOpenReport = onOpenReport
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
            Button("Report") { onOpenReport() }
            Button("Cancel", role: .cancel) {}
        }
        .accessibilityIdentifier("publicProfile")
        .task { await viewModel.load() }
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

    private func loadedLayout(_ payload: PublicProfileContent) -> some View {
        ContentDetailShell(
            title: nil,
            onBack: onBack,
            header: {
                VStack(spacing: 0) {
                    PublicProfileBanner(kind: payload.kind)
                    ProfileHeader(
                        displayName: payload.header.displayName,
                        handle: payload.header.handle,
                        locality: payload.header.locality,
                        avatarURL: payload.header.avatarURL,
                        isVerified: payload.header.isVerified,
                        identityBadges: payload.header.identityBadges,
                        tierLabel: payload.header.tierLabel,
                        isVerifiedNeighbor: payload.header.isVerifiedNeighbor
                    )
                }
                .accessibilityIdentifier(
                    payload.kind == .persona
                        ? "publicProfilePersonaHeader"
                        : "publicProfileLocalHeader"
                )
            },
            body: {
                VStack(alignment: .leading, spacing: Spacing.s4) {
                    StatsTabsBody(
                        content: payload.stats,
                        selectedTab: Binding(
                            get: { viewModel.selectedTab },
                            set: { viewModel.selectedTab = $0 }
                        ),
                        showActionRow: false,
                        onMessage: { onOpenMessages(payload.profile) },
                        onConnect: { Task { await viewModel.connect() } },
                        onOverflow: { viewModel.showOverflow = true }
                    )
                    PublicProfilePostsFeed(
                        kind: payload.kind,
                        posts: payload.posts,
                        onUnlock: { _ in viewModel.toastMessage = "Subscribe flow coming soon" }
                    )
                }
            },
            cta: { stickyFooter(for: payload) }
        )
    }

    @ViewBuilder
    private func stickyFooter(for payload: PublicProfileContent) -> some View {
        switch payload.kind {
        case .persona:
            ActionRowCTA(kind: .persona(
                followState: viewModel.followState,
                onFollow: { Task { await viewModel.follow() } }
            ))
        case .local:
            ActionRowCTA(kind: .local(
                messageState: .idle,
                connectState: viewModel.connectState,
                onMessage: { onOpenMessages(payload.profile) },
                onConnect: { Task { await viewModel.connect() } }
            ))
        }
    }
}

private struct LoadingLayout: View {
    let onBack: @MainActor () -> Void

    var body: some View {
        VStack(spacing: 0) {
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
        VStack(spacing: 0) {
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
