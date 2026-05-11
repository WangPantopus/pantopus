//
//  PublicProfileView.swift
//  Pantopus
//
//  Public profile screen wired through `ContentDetailShell` with
//  `ProfileHeader` + `StatsTabsBody` + `ActionRowCTA`.
//

import SwiftUI

/// Public profile entry point.
@MainActor
public struct PublicProfileView: View {
    @State private var viewModel: PublicProfileViewModel
    private let onBack: @MainActor () -> Void

    public init(userId: String, onBack: @escaping @MainActor () -> Void) {
        _viewModel = State(initialValue: PublicProfileViewModel(userId: userId))
        self.onBack = onBack
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
                ProfileHeader(
                    displayName: payload.header.displayName,
                    handle: payload.header.handle,
                    locality: payload.header.locality,
                    avatarURL: payload.header.avatarURL,
                    isVerified: payload.header.isVerified,
                    identityBadges: payload.header.identityBadges
                )
            },
            body: {
                StatsTabsBody(
                    content: payload.stats,
                    selectedTab: Binding(
                        get: { viewModel.selectedTab },
                        set: { viewModel.selectedTab = $0 }
                    ),
                    onMessage: { viewModel.tapMessage() },
                    onConnect: { viewModel.tapConnect() },
                    onOverflow: { viewModel.tapOverflow() }
                )
            },
            cta: { ActionRowCTA() }
        )
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
