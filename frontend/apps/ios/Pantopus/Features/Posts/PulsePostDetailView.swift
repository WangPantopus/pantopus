//
//  PulsePostDetailView.swift
//  Pantopus
//
//  Pulse post detail screen wired through `ContentDetailShell` with
//  `PostAuthorHeader` + `BodyReactionsBody` + `InlineReplyCTA`.
//

import SwiftUI

/// Pulse post detail entry point.
@MainActor
public struct PulsePostDetailView: View {
    @State private var viewModel: PulsePostDetailViewModel
    private let onBack: @MainActor () -> Void
    private let onOpenProfile: @MainActor (String) -> Void

    public init(
        postId: String,
        onBack: @escaping @MainActor () -> Void,
        onOpenProfile: @escaping @MainActor (String) -> Void = { _ in }
    ) {
        _viewModel = State(initialValue: PulsePostDetailViewModel(postId: postId))
        self.onBack = onBack
        self.onOpenProfile = onOpenProfile
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            content
            if let toast = viewModel.toastMessage {
                ToastView(message: ToastMessage(text: toast, kind: .error))
                    .padding(.bottom, Spacing.s10)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .task(id: toast) {
                        try? await Task.sleep(nanoseconds: 2_500_000_000)
                        viewModel.toastMessage = nil
                    }
            }
        }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .accessibilityIdentifier("pulsePostDetail")
        .task { await viewModel.load() }
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            LoadingLayout(onBack: onBack)
        case let .loaded(detail):
            loadedLayout(detail)
        case let .error(message):
            ErrorLayout(message: message, onBack: onBack) {
                Task { await viewModel.refresh() }
            }
        }
    }

    private func loadedLayout(_ detail: PulsePostDetailContent) -> some View {
        ContentDetailShell(
            title: nil,
            onBack: onBack,
            header: {
                PostAuthorHeader(
                    displayName: detail.authorDisplayName,
                    avatarURL: detail.authorAvatarURL,
                    isVerified: detail.authorVerified,
                    identity: detail.authorIdentity,
                    timeAndLocality: detail.timeAndLocality,
                    intent: detail.intent
                ) { onOpenProfile(detail.post.userId) }
            },
            body: {
                BodyReactionsBody(
                    body: detail.post.content,
                    mediaURLs: detail.mediaURLs,
                    reactions: detail.reactions,
                    onReactionTap: { kind in Task { await viewModel.tapReaction(kind) } },
                    composerAvatarURL: nil,
                    composerAvatarName: "You",
                    composerText: Binding(
                        get: { viewModel.composerText },
                        set: { viewModel.composerText = $0 }
                    ),
                    isSending: viewModel.isSendingComment,
                    onSendTap: { Task { await viewModel.sendComment() } },
                    comments: detail.comments,
                    hiddenReplyCount: detail.hiddenReplyCount,
                    onShowMoreReplies: { viewModel.showMoreReplies() },
                    onCommentAvatarTap: { userId in onOpenProfile(userId) }
                )
            },
            cta: { InlineReplyCTA() }
        )
    }
}

private struct LoadingLayout: View {
    let onBack: @MainActor () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ContentDetailTopBar(title: nil, onBack: onBack, action: nil)
            VStack(alignment: .leading, spacing: Spacing.s3) {
                Shimmer(width: 220, height: 22, cornerRadius: Radii.sm)
                Shimmer(height: 16, cornerRadius: Radii.sm)
                Shimmer(height: 16, cornerRadius: Radii.sm)
                Shimmer(height: 160, cornerRadius: Radii.lg)
                HStack(spacing: Spacing.s2) {
                    Shimmer(width: 80, height: 32, cornerRadius: Radii.pill)
                    Shimmer(width: 80, height: 32, cornerRadius: Radii.pill)
                    Shimmer(width: 80, height: 32, cornerRadius: Radii.pill)
                }
            }
            .padding(Spacing.s4)
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
                headline: "Couldn't load this post",
                subcopy: message,
                cta: EmptyState.CTA(title: "Try again") { await MainActor.run { onRetry() } }
            )
            .frame(maxHeight: .infinity)
        }
        .background(Theme.Color.appBg)
    }
}

#Preview {
    PulsePostDetailView(postId: "preview") {}
}
