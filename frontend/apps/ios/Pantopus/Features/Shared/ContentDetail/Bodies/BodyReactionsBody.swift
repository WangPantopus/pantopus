//
//  BodyReactionsBody.swift
//  Pantopus
//
//  `body_reactions` slot for the Pulse post detail. Body copy → media
//  grid → reactions bar → inline composer → flattened comment thread.
//
// swiftlint:disable multiple_closures_with_trailing_closure

import SwiftUI

/// View-model surface for a single comment row.
public struct PostCommentRow: Sendable, Identifiable, Hashable {
    public let id: String
    public let authorName: String
    public let authorAvatarURL: URL?
    public let authorIdentity: IdentityPillar
    public let body: String
    public let timestamp: String
    public let reactionCount: Int
    public let userReacted: Bool
    /// 0 for top-level, 1 for nested. We collapse deeper threads to 1.
    public let indentLevel: Int
    public let authorUserId: String?

    public init(
        id: String,
        authorName: String,
        authorAvatarURL: URL?,
        authorIdentity: IdentityPillar,
        body: String,
        timestamp: String,
        reactionCount: Int = 0,
        userReacted: Bool = false,
        indentLevel: Int,
        authorUserId: String?
    ) {
        self.id = id
        self.authorName = authorName
        self.authorAvatarURL = authorAvatarURL
        self.authorIdentity = authorIdentity
        self.body = body
        self.timestamp = timestamp
        self.reactionCount = reactionCount
        self.userReacted = userReacted
        self.indentLevel = indentLevel
        self.authorUserId = authorUserId
    }
}

/// Quick-reply prompt rendered in the empty thread state.
public struct PostQuickReplyPrompt: Sendable, Identifiable, Hashable {
    public let id: String
    public let label: String
    public let icon: PantopusIcon

    public init(label: String, icon: PantopusIcon) {
        id = label
        self.label = label
        self.icon = icon
    }
}

/// Counts displayed under each reaction pill, plus which (if any) the
/// signed-in user has currently selected.
public struct PostReactionCounts: Sendable, Hashable {
    public var helpful: Int
    public var heart: Int
    public var going: Int
    public var userReaction: PostReactionKind?

    public init(helpful: Int = 0, heart: Int = 0, going: Int = 0, userReaction: PostReactionKind? = nil) {
        self.helpful = helpful
        self.heart = heart
        self.going = going
        self.userReaction = userReaction
    }
}

/// Pulse post body — text + media + reactions + comments. Pure render
/// surface; all state lives in the host view-model.
@MainActor
public struct BodyReactionsBody: View {
    private let bodyText: String
    private let mediaURLs: [URL]
    private let intent: PostIntent
    private let reactions: PostReactionCounts
    private let onReactionTap: @MainActor (PostReactionKind) -> Void
    private let composerAvatarURL: URL?
    private let composerAvatarName: String
    @Binding private var composerText: String
    private let isSending: Bool
    private let onSendTap: @MainActor () -> Void
    private let comments: [PostCommentRow]
    private let hiddenReplyCount: Int
    private let onShowMoreReplies: (@MainActor () -> Void)?
    private let onCommentAvatarTap: @MainActor (String) -> Void

    public init(
        body: String,
        mediaURLs: [URL],
        intent: PostIntent = .share,
        reactions: PostReactionCounts,
        onReactionTap: @escaping @MainActor (PostReactionKind) -> Void,
        composerAvatarURL: URL?,
        composerAvatarName: String,
        composerText: Binding<String>,
        isSending: Bool,
        onSendTap: @escaping @MainActor () -> Void,
        comments: [PostCommentRow],
        hiddenReplyCount: Int = 0,
        onShowMoreReplies: (@MainActor () -> Void)? = nil,
        onCommentAvatarTap: @escaping @MainActor (String) -> Void = { _ in }
    ) {
        bodyText = body
        self.mediaURLs = mediaURLs
        self.intent = intent
        self.reactions = reactions
        self.onReactionTap = onReactionTap
        self.composerAvatarURL = composerAvatarURL
        self.composerAvatarName = composerAvatarName
        _composerText = composerText
        self.isSending = isSending
        self.onSendTap = onSendTap
        self.comments = comments
        self.hiddenReplyCount = hiddenReplyCount
        self.onShowMoreReplies = onShowMoreReplies
        self.onCommentAvatarTap = onCommentAvatarTap
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            if !bodyText.isEmpty {
                Text(bodyText)
                    .font(.system(size: 15, weight: .regular))
                    .foregroundStyle(Theme.Color.appText)
                    .lineSpacing(7) // 15 + 7 ≈ 22pt line height
                    .padding(.horizontal, Spacing.s4)
                    .accessibilityLabel(bodyText)
            }

            if !mediaURLs.isEmpty {
                PostMediaGrid(urls: mediaURLs)
                    .padding(.horizontal, Spacing.s4)
            }

            ReactionsBar(
                counts: reactions,
                commentCount: visibleCommentCount,
                commentsAreFresh: comments.isEmpty,
                onTap: onReactionTap
            )
            .padding(.horizontal, Spacing.s4)

            Rectangle()
                .fill(Theme.Color.appBorder)
                .frame(height: 1)
                .padding(.horizontal, Spacing.s4)

            CommentComposer(
                avatarName: composerAvatarName,
                avatarURL: composerAvatarURL,
                text: $composerText,
                placeholder: comments.isEmpty ? "Be the first to reply..." : "Add a comment",
                isFocusedPresentation: comments.isEmpty,
                isSending: isSending,
                onSend: onSendTap
            )
            .padding(.horizontal, Spacing.s4)

            if !comments.isEmpty {
                VStack(alignment: .leading, spacing: Spacing.s3) {
                    ForEach(comments) { comment in
                        CommentRow(comment: comment) {
                            if let uid = comment.authorUserId { onCommentAvatarTap(uid) }
                        }
                    }
                    if hiddenReplyCount > 0, let onShowMoreReplies {
                        Button {
                            onShowMoreReplies()
                        } label: {
                            Text("View \(hiddenReplyCount) more \(hiddenReplyCount == 1 ? "reply" : "replies")")
                                .font(.system(size: PantopusTextStyle.small.size, weight: .semibold))
                                .foregroundStyle(Theme.Color.primary600)
                                .frame(minHeight: 44)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("View \(hiddenReplyCount) more replies")
                        .padding(.leading, 28 + Spacing.s3) // align with comment body
                    }
                }
                .padding(.horizontal, Spacing.s4)
            } else {
                EmptyThreadState(
                    intent: intent,
                    prompts: intent.quickReplyPrompts
                ) { prompt in
                    composerText = prompt.label
                }
                .padding(.horizontal, Spacing.s4)
            }
        }
    }

    private var visibleCommentCount: Int {
        comments.count + hiddenReplyCount
    }
}

// MARK: - Sub-views

private struct PostMediaGrid: View {
    let urls: [URL]

    var body: some View {
        switch urls.count {
        case 0: EmptyView()
        case 1:
            mediaTile(urls[0])
                .aspectRatio(16.0 / 9.0, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        case 2:
            HStack(spacing: Spacing.s2) {
                mediaTile(urls[0]).aspectRatio(1, contentMode: .fill)
                mediaTile(urls[1]).aspectRatio(1, contentMode: .fill)
            }
            .frame(height: 160)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        case 3:
            HStack(spacing: Spacing.s2) {
                mediaTile(urls[0]).frame(maxWidth: .infinity)
                VStack(spacing: Spacing.s2) {
                    mediaTile(urls[1]).frame(maxWidth: .infinity)
                    mediaTile(urls[2]).frame(maxWidth: .infinity)
                }
            }
            .frame(height: 200)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        default:
            LazyVGrid(
                columns: [GridItem(.flexible(), spacing: Spacing.s2), GridItem(.flexible(), spacing: Spacing.s2)],
                spacing: Spacing.s2
            ) {
                ForEach(Array(urls.prefix(4).enumerated()), id: \.offset) { _, url in
                    mediaTile(url).aspectRatio(1, contentMode: .fill)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        }
    }

    private func mediaTile(_ url: URL) -> some View {
        AsyncImage(url: url) { phase in
            switch phase {
            case let .success(image):
                image.resizable().scaledToFill()
            case .failure:
                Color.gray.opacity(0.1)
                    .overlay(Icon(.alertCircle, size: 22, color: Theme.Color.appTextMuted))
            case .empty:
                Color.gray.opacity(0.08).overlay(ProgressView())
            @unknown default:
                Color.gray.opacity(0.08)
            }
        }
        .frame(maxWidth: .infinity)
        .clipped()
    }
}

private struct ReactionsBar: View {
    let counts: PostReactionCounts
    let commentCount: Int
    let commentsAreFresh: Bool
    let onTap: @MainActor (PostReactionKind) -> Void

    var body: some View {
        HStack(spacing: Spacing.s2) {
            ReactionPill(
                id: "heart",
                icon: .heart,
                count: counts.helpful,
                accessibilityLabel: "Heart reaction, \(counts.helpful)",
                isSelected: counts.userReaction == .helpful,
                selectedForeground: Theme.Color.error,
                selectedBackground: Theme.Color.errorBg
            ) { onTap(.helpful) }
            ReactionPill(
                id: "hand",
                icon: .hand,
                count: counts.heart,
                accessibilityLabel: "Raised hand reaction, \(counts.heart)",
                isSelected: counts.userReaction == .heart,
                onTap: nil
            )
            ReactionPill(
                id: "eye",
                icon: .eye,
                count: counts.going,
                accessibilityLabel: "Watching reaction, \(counts.going)",
                isSelected: counts.userReaction == .going,
                onTap: nil
            )
            Spacer()
            HStack(spacing: Spacing.s1) {
                Icon(.messageCircle, size: 13, color: commentsAreFresh ? Theme.Color.appTextMuted : Theme.Color.appTextSecondary)
                Text(commentSummary)
                    .font(.system(size: 11, weight: .regular))
                    .foregroundStyle(commentsAreFresh ? Theme.Color.appTextMuted : Theme.Color.appTextSecondary)
            }
            .accessibilityLabel(commentSummary)
        }
    }

    private var commentSummary: String {
        if commentsAreFresh {
            return "0 comments · just posted"
        }
        return "\(commentCount) \(commentCount == 1 ? "comment" : "comments")"
    }
}

private struct ReactionPill: View {
    let id: String
    let icon: PantopusIcon
    let count: Int
    let accessibilityLabel: String
    let isSelected: Bool
    var selectedForeground: Color = Theme.Color.primary700
    var selectedBackground: Color = Theme.Color.primary50
    /// `nil` renders the pill as display-only (no Button wrapper).
    let onTap: (@MainActor () -> Void)?

    var body: some View {
        if let onTap {
            Button(action: { onTap() }) { pillBody }
                .buttonStyle(.plain)
                .frame(minHeight: 44, alignment: .center)
                .accessibilityIdentifier("pulsePostDetail-reaction-\(id)")
                .accessibilityLabel(accessibilityLabel)
                .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
        } else {
            pillBody
                .accessibilityElement()
                .accessibilityIdentifier("pulsePostDetail-reaction-\(id)")
                .accessibilityLabel(accessibilityLabel)
        }
    }

    private var pillBody: some View {
        HStack(spacing: Spacing.s1) {
            Icon(icon, size: 14, color: foreground)
            Text("\(count)")
                .font(.system(size: PantopusTextStyle.caption.size, weight: .regular))
                .foregroundStyle(foreground)
        }
        .padding(.horizontal, Spacing.s3)
        .frame(height: 32)
        .background(background)
        .overlay {
            RoundedRectangle(cornerRadius: Radii.pill, style: .continuous)
                .stroke(border, lineWidth: 1)
        }
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
    }

    private var foreground: Color {
        isSelected ? selectedForeground : Theme.Color.appTextSecondary
    }

    private var background: Color {
        isSelected ? selectedBackground : Theme.Color.appSurface
    }

    private var border: Color {
        isSelected ? selectedForeground.opacity(0.25) : Theme.Color.appBorder
    }
}

// MARK: - Preview

#Preview("Populated") {
    @Previewable @State var text = ""
    BodyReactionsBody(
        body: "Anyone know a reliable handyman for fixing a leaky faucet in Cambridge? Tried two on Angi already.",
        mediaURLs: [],
        reactions: PostReactionCounts(helpful: 7, heart: 2, going: 0, userReaction: .helpful),
        onReactionTap: { _ in },
        composerAvatarURL: nil,
        composerAvatarName: "You",
        composerText: $text,
        isSending: false,
        onSendTap: {},
        comments: [
            PostCommentRow(
                id: "1",
                authorName: "Maria Chen",
                authorAvatarURL: nil,
                authorIdentity: .personal,
                body: "I used Mike at Westside Plumbing last month. Solid.",
                timestamp: "3m ago",
                indentLevel: 0,
                authorUserId: "u1"
            ),
            PostCommentRow(
                id: "2",
                authorName: "Sam Lee",
                authorAvatarURL: nil,
                authorIdentity: .personal,
                body: "+1 to Mike. Fair pricing too.",
                timestamp: "2m ago",
                indentLevel: 1,
                authorUserId: "u2"
            )
        ],
        hiddenReplyCount: 4
    ) {}
        .background(Theme.Color.appBg)
}
