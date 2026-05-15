//
//  PulseFeedViewModel.swift
//  Pantopus
//
//  Backs the Pulse feed (Hub → Pulse pillar). Fetches
//  `GET /api/posts/feed?surface=place` and refetches when the chip-row
//  filter changes. Reactions optimistically toggle the local count and
//  hit `POST /api/posts/:id/like`.
//

import Foundation
import Observation

/// Render state for the Pulse feed screen.
public enum PulseFeedState: Sendable {
    case loading
    case empty(PulseFeedEmpty)
    case loaded([PulsePostCardContent])
    case error(message: String)
}

/// Empty-state content. `scopeLabel` is the locality the user is
/// browsing within (e.g. "Elm Park") so the empty message can hint at
/// the filter scope.
public struct PulseFeedEmpty: Sendable, Hashable {
    public let scopeLabel: String?

    public init(scopeLabel: String? = nil) {
        self.scopeLabel = scopeLabel
    }
}

/// Pulse feed view-model.
@Observable
@MainActor
public final class PulseFeedViewModel {
    /// Current render state.
    public private(set) var state: PulseFeedState = .loading

    /// Active chip-row filter. Drives the list query and the compose
    /// FAB's pre-fill.
    public private(set) var activeIntent: PulseIntent = .all

    /// Locality name surfaced on the empty state. Set from the loaded
    /// first post or a backend hint.
    public private(set) var scopeLabel: String?

    private let api: APIClient
    /// Coordinates from the active location source. `nil` skips
    /// `latitude`/`longitude` params — the backend will then default to
    /// the user's last known location or return an empty list.
    private let latitude: Double?
    private let longitude: Double?
    private var loadedItems: [FeedPostDTO] = []
    private var isLoading = false

    init(
        api: APIClient = .shared,
        latitude: Double? = nil,
        longitude: Double? = nil
    ) {
        self.api = api
        self.latitude = latitude
        self.longitude = longitude
    }

    /// First-time load. No-op if we already have content.
    public func load() async {
        if case .loaded = state { return }
        await fetch()
    }

    /// Pull-to-refresh / retry.
    public func refresh() async {
        await fetch()
    }

    /// Chip-row tap. `all` clears the filter and the FAB pre-fill.
    public func selectIntent(_ intent: PulseIntent) async {
        guard intent != activeIntent else { return }
        activeIntent = intent
        await fetch()
    }

    /// Tap on a post's primary reaction. Optimistically toggles the
    /// per-post `userHasReacted` flag + helpful count, then hits
    /// `POST /api/posts/:id/like`. Rolls back on failure.
    public func tapReaction(postId: String) async {
        guard case let .loaded(rows) = state else { return }
        guard let index = rows.firstIndex(where: { $0.id == postId }) else { return }
        let current = rows[index]
        let toggled = !current.userHasReacted
        let primary = current.reactions.first
        let optimisticCount = (primary?.count ?? 0) + (toggled ? 1 : -1)
        applyReactionState(at: index, hasReacted: toggled, primaryCount: max(0, optimisticCount))

        do {
            let response = try await api.request(
                PostsEndpoints.toggleLike(id: postId),
                as: PostLikeResponse.self
            )
            applyReactionState(at: index, hasReacted: response.liked, primaryCount: response.likeCount)
        } catch {
            // Roll back.
            applyReactionState(
                at: index,
                hasReacted: current.userHasReacted,
                primaryCount: primary?.count ?? 0
            )
        }
    }

    // MARK: - Fetch

    private func fetch() async {
        if isLoading { return }
        isLoading = true
        defer { isLoading = false }
        if case .loaded = state {} else { state = .loading }
        do {
            let response: FeedResponse = try await api.request(
                PostsEndpoints.feed(
                    surface: "place",
                    latitude: latitude,
                    longitude: longitude,
                    postType: activeIntent.postType,
                    limit: 20
                )
            )
            loadedItems = response.posts
            scopeLabel = response.posts.first?.locationName ?? scopeLabel
            if response.posts.isEmpty {
                state = .empty(PulseFeedEmpty(scopeLabel: scopeLabel))
            } else {
                state = .loaded(response.posts.map(Self.project))
            }
        } catch {
            let message = (error as? APIError)?.errorDescription ?? "Couldn't load posts."
            state = .error(message: message)
        }
    }

    // MARK: - Projection

    private static func project(_ post: FeedPostDTO) -> PulsePostCardContent {
        let intent = PulseIntent.from(postType: post.postType)
        let initials = Self.initials(from: post.creator?.displayName ?? "?")
        let attendees: PulseAttendeeStrip? = intent == .event
            ? PulseAttendeeStrip(
                avatars: [],
                goingCount: post.likeCount, // backend doesn't surface attendees yet
                userIsGoing: post.userHasLiked
            )
            : nil
        return PulsePostCardContent(
            id: post.id,
            authorName: post.creator?.displayName ?? "Pantopus user",
            authorInitials: initials,
            authorVerified: post.creator?.accountType == "business" || post.userHasLiked,
            // placeholder until backend surfaces creator.verified
            meta: Self.metaString(post: post, intent: intent),
            intent: intent,
            title: intent == .event ? post.title : nil,
            body: post.content,
            reactions: intent.reactionTemplate(
                helpfulCount: post.likeCount,
                secondaryCount: post.commentCount
            ),
            attendees: attendees,
            userHasReacted: post.userHasLiked
        )
    }

    private static func metaString(post: FeedPostDTO, intent _: PulseIntent) -> String {
        let relative = relative(timestamp: post.createdAt)
        if let locality = post.locationName, !locality.isEmpty {
            return "\(relative) · \(locality)"
        }
        return relative
    }

    private static func relative(timestamp: String) -> String {
        let parser = ISO8601DateFormatter()
        parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = parser.date(from: timestamp) ?? ISO8601DateFormatter().date(from: timestamp) ?? Date()
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: Date())
    }

    private static func initials(from name: String) -> String {
        let parts = name.split(separator: " ").prefix(2)
        return parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
    }

    private func applyReactionState(at index: Int, hasReacted: Bool, primaryCount: Int) {
        guard case var .loaded(rows) = state, rows.indices.contains(index) else { return }
        let original = rows[index]
        guard let primary = original.reactions.first else { return }
        var updatedReactions = original.reactions
        updatedReactions[0] = PulseReaction(
            kind: primary.kind,
            icon: primary.icon,
            label: primary.label,
            count: primaryCount,
            isInteractive: primary.isInteractive
        )
        rows[index] = PulsePostCardContent(
            id: original.id,
            authorName: original.authorName,
            authorInitials: original.authorInitials,
            authorVerified: original.authorVerified,
            meta: original.meta,
            intent: original.intent,
            title: original.title,
            body: original.body,
            reactions: updatedReactions,
            attendees: original.attendees,
            userHasReacted: hasReacted
        )
        state = .loaded(rows)
    }
}
