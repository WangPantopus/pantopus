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
    private let onEdit: @MainActor (String) -> Void

    public init(
        postId: String,
        currentUserId: String? = nil,
        onBack: @escaping @MainActor () -> Void,
        onOpenProfile: @escaping @MainActor (String) -> Void = { _ in },
        onEdit: @escaping @MainActor (String) -> Void = { _ in }
    ) {
        _viewModel = State(initialValue: PulsePostDetailViewModel(
            postId: postId,
            currentUserId: currentUserId
        ))
        self.onBack = onBack
        self.onOpenProfile = onOpenProfile
        self.onEdit = onEdit
    }

    public var body: some View {
        @Bindable var bindable = viewModel
        return ZStack(alignment: .bottom) {
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
        .confirmationDialog(
            "Post options",
            isPresented: $bindable.showsOverflowMenu,
            titleVisibility: .hidden
        ) {
            if case let .loaded(detail) = viewModel.state {
                Button("Edit post") { onEdit(detail.post.id) }
                    .accessibilityIdentifier("pulsePostDetail-edit")
            }
            Button("Cancel", role: .cancel) {}
        }
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
        PulsePostDetailLoadedContent(
            detail: detail,
            composerText: Binding(
                get: { viewModel.composerText },
                set: { viewModel.composerText = $0 }
            ),
            isSendingComment: viewModel.isSendingComment,
            topBarAction: viewModel.isOwner ? overflowAction : nil,
            onBack: onBack,
            onOpenProfile: onOpenProfile,
            onReactionTap: { kind in Task { await viewModel.tapReaction(kind) } },
            onSendTap: { Task { await viewModel.sendComment() } },
            onShowMoreReplies: { viewModel.showMoreReplies() }
        )
    }

    private var overflowAction: ContentDetailTopBarAction {
        ContentDetailTopBarAction(
            icon: .moreHorizontal,
            accessibilityLabel: "Post options"
        ) { Task { @MainActor in viewModel.showsOverflowMenu = true } }
    }
}

@MainActor
public struct PulsePostDetailLoadedContent: View {
    private let detail: PulsePostDetailContent
    @Binding private var composerText: String
    private let isSendingComment: Bool
    private let topBarAction: ContentDetailTopBarAction?
    private let onBack: @MainActor () -> Void
    private let onOpenProfile: @MainActor (String) -> Void
    private let onReactionTap: @MainActor (PostReactionKind) -> Void
    private let onSendTap: @MainActor () -> Void
    private let onShowMoreReplies: @MainActor () -> Void

    public init(
        detail: PulsePostDetailContent,
        composerText: Binding<String>,
        isSendingComment: Bool,
        topBarAction: ContentDetailTopBarAction? = nil,
        onBack: @escaping @MainActor () -> Void = {},
        onOpenProfile: @escaping @MainActor (String) -> Void = { _ in },
        onReactionTap: @escaping @MainActor (PostReactionKind) -> Void = { _ in },
        onSendTap: @escaping @MainActor () -> Void = {},
        onShowMoreReplies: @escaping @MainActor () -> Void = {}
    ) {
        self.detail = detail
        _composerText = composerText
        self.isSendingComment = isSendingComment
        self.topBarAction = topBarAction
        self.onBack = onBack
        self.onOpenProfile = onOpenProfile
        self.onReactionTap = onReactionTap
        self.onSendTap = onSendTap
        self.onShowMoreReplies = onShowMoreReplies
    }

    public var body: some View {
        ContentDetailShell(
            title: "Post",
            onBack: onBack,
            topBarAction: topBarAction,
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
                    intent: detail.intent,
                    reactions: detail.reactions,
                    onReactionTap: onReactionTap,
                    composerAvatarURL: nil,
                    composerAvatarName: "You",
                    composerText: $composerText,
                    isSending: isSendingComment,
                    onSendTap: onSendTap,
                    comments: detail.comments,
                    hiddenReplyCount: detail.hiddenReplyCount,
                    onShowMoreReplies: onShowMoreReplies
                ) { userId in
                    onOpenProfile(userId)
                }
            },
            cta: { InlineReplyCTA() }
        )
    }
}

private struct LoadingLayout: View {
    let onBack: @MainActor () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ContentDetailTopBar(title: "Post", onBack: onBack, action: nil)
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
            ContentDetailTopBar(title: "Post", onBack: onBack, action: nil)
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
    @Previewable @State var composer = ""
    PulsePostDetailLoadedContent(
        detail: PulsePostDetailSampleData.populated,
        composerText: $composer,
        isSendingComment: false
    )
}
