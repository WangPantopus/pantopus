//
//  PulsePostDetailViewModel.swift
//  Pantopus
//
//  Loads a single Pulse post, lets the user toggle reactions optimistic-
//  style, and posts new comments inline. Tap-targets for the multi-kind
//  reaction bar (Helpful/Heart/Going) all map to the single backend
//  `POST /:id/like` toggle today — see PR description for the discrepancy.
//

import Foundation
import Logging
import Observation

/// View-model state for the Pulse post detail screen.
public enum PulsePostDetailState: Sendable, Equatable {
    case loading
    case loaded(PulsePostDetailContent)
    case error(message: String)
}

/// Hydrated content for the Pulse post detail. Built in the VM from the
/// route handler's `PostDetailDTO` so the view sees only render-ready
/// values (formatted timestamps, mapped identity pillars, etc.).
public struct PulsePostDetailContent: Sendable, Equatable, Hashable {
    public let post: PostDetailDTO
    public let authorDisplayName: String
    public let authorAvatarURL: URL?
    public let authorIdentity: IdentityPillar
    public let authorVerified: Bool
    public let timeAndLocality: String
    public let intent: PostIntent
    public let mediaURLs: [URL]
    public let reactions: PostReactionCounts
    public let comments: [PostCommentRow]
    public let hiddenReplyCount: Int

    public init(
        post: PostDetailDTO,
        authorDisplayName: String,
        authorAvatarURL: URL?,
        authorIdentity: IdentityPillar,
        authorVerified: Bool,
        timeAndLocality: String,
        intent: PostIntent,
        mediaURLs: [URL],
        reactions: PostReactionCounts,
        comments: [PostCommentRow],
        hiddenReplyCount: Int
    ) {
        self.post = post
        self.authorDisplayName = authorDisplayName
        self.authorAvatarURL = authorAvatarURL
        self.authorIdentity = authorIdentity
        self.authorVerified = authorVerified
        self.timeAndLocality = timeAndLocality
        self.intent = intent
        self.mediaURLs = mediaURLs
        self.reactions = reactions
        self.comments = comments
        self.hiddenReplyCount = hiddenReplyCount
    }
}

/// Loads + mutates a single Pulse post.
@MainActor
@Observable
public final class PulsePostDetailViewModel {
    /// Render-state.
    public private(set) var state: PulsePostDetailState = .loading

    /// Inline composer's draft text. Bound from the view.
    public var composerText: String = ""

    /// True while a comment is in flight.
    public private(set) var isSendingComment: Bool = false

    /// Transient error string surfaced in the toast overlay.
    public var toastMessage: String?

    /// True when the entire reply thread is expanded.
    public private(set) var showingAllReplies: Bool = false

    private let postId: String
    private let client: APIClient
    private let logger = Logger(label: "app.pantopus.ios.PulsePostDetail")
    private let maxInitialReplies = 3

    init(postId: String, client: APIClient = .shared) {
        self.postId = postId
        self.client = client
    }

    /// First-load entry. Re-run via `refresh()` after a pull-to-refresh.
    public func load() async {
        state = .loading
        await fetch()
    }

    /// Pull-to-refresh.
    public func refresh() async {
        await fetch()
    }

    /// Expand the truncated reply list.
    public func showMoreReplies() {
        guard case let .loaded(content) = state else { return }
        showingAllReplies = true
        state = .loaded(rebuildContent(from: content.post))
    }

    /// Tap one of the reaction pills. Only `.helpful` is wired to a
    /// backend route today; the `ReactionsBar` view renders the other
    /// kinds as display-only so this method only ever sees `.helpful`,
    /// but we keep the guard as a safety net.
    public func tapReaction(_ kind: PostReactionKind) async {
        guard case var .loaded(content) = state else { return }
        guard kind.isBackendWired else { return }

        // Optimistic update on the helpful (likes) bucket.
        var optimistic = content.reactions
        let wasOn = optimistic.userReaction == .helpful
        if wasOn {
            optimistic.helpful = max(0, optimistic.helpful - 1)
            optimistic.userReaction = nil
        } else {
            optimistic.helpful += 1
            optimistic.userReaction = .helpful
        }
        content = PulsePostDetailContent(
            post: content.post,
            authorDisplayName: content.authorDisplayName,
            authorAvatarURL: content.authorAvatarURL,
            authorIdentity: content.authorIdentity,
            authorVerified: content.authorVerified,
            timeAndLocality: content.timeAndLocality,
            intent: content.intent,
            mediaURLs: content.mediaURLs,
            reactions: optimistic,
            comments: content.comments,
            hiddenReplyCount: content.hiddenReplyCount
        )
        state = .loaded(content)

        do {
            let response = try await client.request(
                PostsEndpoints.toggleLike(id: postId),
                as: PostLikeResponse.self
            )
            // Reconcile with server truth.
            var reconciled = optimistic
            reconciled.helpful = response.likeCount
            reconciled.userReaction = response.liked ? .helpful : nil
            content = PulsePostDetailContent(
                post: content.post,
                authorDisplayName: content.authorDisplayName,
                authorAvatarURL: content.authorAvatarURL,
                authorIdentity: content.authorIdentity,
                authorVerified: content.authorVerified,
                timeAndLocality: content.timeAndLocality,
                intent: content.intent,
                mediaURLs: content.mediaURLs,
                reactions: reconciled,
                comments: content.comments,
                hiddenReplyCount: content.hiddenReplyCount
            )
            state = .loaded(content)
        } catch {
            logger.warning("Reaction toggle failed: \(error)")
            toastMessage = "Couldn't update your reaction"
            // Rollback.
            var rolled = optimistic
            if wasOn {
                rolled.helpful += 1
                rolled.userReaction = .helpful
            } else {
                rolled.helpful = max(0, rolled.helpful - 1)
                rolled.userReaction = nil
            }
            content = PulsePostDetailContent(
                post: content.post,
                authorDisplayName: content.authorDisplayName,
                authorAvatarURL: content.authorAvatarURL,
                authorIdentity: content.authorIdentity,
                authorVerified: content.authorVerified,
                timeAndLocality: content.timeAndLocality,
                intent: content.intent,
                mediaURLs: content.mediaURLs,
                reactions: rolled,
                comments: content.comments,
                hiddenReplyCount: content.hiddenReplyCount
            )
            state = .loaded(content)
        }
    }

    /// Submit the composer's current text as a new top-level comment.
    /// On success we re-fetch the post so the comment list, count, and
    /// any backend-side mutations (rate-limit windows, derived fields)
    /// stay in sync.
    public func sendComment() async {
        let body = composerText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty, case .loaded = state, !isSendingComment else { return }
        isSendingComment = true
        defer { isSendingComment = false }
        let req = PostCommentRequest(comment: body, parentCommentId: nil)
        do {
            _ = try await client.request(
                PostsEndpoints.createComment(id: postId, body: req),
                as: PostCommentCreateResponse.self
            )
            composerText = ""
            await fetch()
        } catch {
            logger.warning("Comment send failed: \(error)")
            toastMessage = "Couldn't post your comment"
        }
    }

    // MARK: - Internal helpers

    private func fetch() async {
        do {
            let response = try await client.request(
                PostsEndpoints.detail(id: postId),
                as: PostDetailResponse.self
            )
            state = .loaded(rebuildContent(from: response.post))
        } catch let error as APIError {
            logger.warning("Post detail load failed: \(error)")
            state = .error(message: friendlyMessage(for: error))
        } catch {
            logger.warning("Post detail load failed: \(error)")
            state = .error(message: "Something went wrong")
        }
    }

    private func rebuildContent(from post: PostDetailDTO) -> PulsePostDetailContent {
        let displayName = post.creator?.displayName ?? "Pantopus user"
        let avatarURL = post.creator?.profilePictureURL.flatMap(URL.init(string:))
        let mediaURLs = post.mediaURLs.compactMap(URL.init(string:))
        let identity: IdentityPillar = mapAccountType(post.creator?.accountType)
        let timeAndLocality = formatTimeAndLocality(
            createdAt: post.createdAt,
            locality: post.creator?.locality ?? post.home?.city
        )
        let intent = PostIntent.from(purpose: post.purpose, postType: post.postType)
        let reactions = PostReactionCounts(
            helpful: post.likeCount,
            heart: 0,
            going: 0,
            userReaction: post.userHasLiked ? .helpful : nil
        )
        let (rows, hidden) = buildCommentRows(post.comments)
        return PulsePostDetailContent(
            post: post,
            authorDisplayName: displayName,
            authorAvatarURL: avatarURL,
            authorIdentity: identity,
            // TODO(backend): backend `CREATOR_SELECT` for posts does not
            // include the `verified` column today, so the post header
            // can never show a verified badge. Wire this up once
            // feedService.js#CREATOR_SELECT joins `verified`.
            authorVerified: false,
            timeAndLocality: timeAndLocality,
            intent: intent,
            mediaURLs: mediaURLs,
            reactions: reactions,
            comments: rows,
            hiddenReplyCount: hidden
        )
    }

    private func mapAccountType(_ accountType: String?) -> IdentityPillar {
        switch accountType {
        case "business": .business
        case "home": .home
        default: .personal
        }
    }

    private func buildCommentRows(_ comments: [PostCommentDTO]) -> (rows: [PostCommentRow], hidden: Int) {
        // Group by parent → flatten with indentLevel capped at 1.
        let topLevel = comments.filter { $0.parentCommentId == nil && !$0.isDeleted }
        let repliesByParent = Dictionary(
            grouping: comments.filter { $0.parentCommentId != nil && !$0.isDeleted }
        ) { $0.parentCommentId ?? "" }

        var rows: [PostCommentRow] = []
        var visibleReplyCount = 0
        var totalReplyCount = 0

        for parent in topLevel {
            rows.append(row(from: parent, indent: 0))
            let replies = (repliesByParent[parent.id] ?? [])
                .sorted { ($0.createdAt) < ($1.createdAt) }
            totalReplyCount += replies.count
            let cap = showingAllReplies ? replies.count : min(maxInitialReplies, replies.count)
            for reply in replies.prefix(cap) {
                rows.append(row(from: reply, indent: 1))
                visibleReplyCount += 1
            }
        }

        let hidden = showingAllReplies ? 0 : max(0, totalReplyCount - visibleReplyCount)
        return (rows, hidden)
    }

    private func row(from comment: PostCommentDTO, indent: Int) -> PostCommentRow {
        let name = comment.author?.displayName ?? "Pantopus user"
        return PostCommentRow(
            id: comment.id,
            authorName: name,
            authorAvatarURL: comment.author?.profilePictureURL.flatMap(URL.init(string:)),
            authorIdentity: mapAccountType(comment.author?.accountType),
            body: comment.comment,
            timestamp: relativeTimestamp(comment.createdAt),
            indentLevel: indent,
            authorUserId: comment.author?.id
        )
    }

    private func formatTimeAndLocality(createdAt: String, locality: String?) -> String {
        let ts = relativeTimestamp(createdAt)
        if let locality, !locality.isEmpty {
            return "\(ts) · \(locality)"
        }
        return ts
    }

    private func relativeTimestamp(_ iso: String) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = formatter.date(from: iso) ?? ISO8601DateFormatter().date(from: iso) ?? Date()
        let elapsed = Date().timeIntervalSince(date)
        switch elapsed {
        case ..<60: return "Just now"
        case ..<3600: return "\(Int(elapsed / 60))m ago"
        case ..<86400: return "\(Int(elapsed / 3600))h ago"
        case ..<604_800: return "\(Int(elapsed / 86400))d ago"
        default:
            let display = DateFormatter()
            display.dateStyle = .medium
            display.timeStyle = .none
            return display.string(from: date)
        }
    }

    private func friendlyMessage(for error: APIError) -> String {
        switch error {
        case .notFound: "We couldn't find this post."
        case .forbidden: "You don't have access to this post."
        case .transport: "Check your connection and try again."
        default: "Something went wrong. Try again."
        }
    }
}
