//
//  PublicProfileViewModel.swift
//  Pantopus
//
//  Loads `GET /api/users/id/:id` and projects it onto the
//  `StatsTabsBody` content model. Tab state lives in the VM so
//  switching doesn't re-fetch.
//

import Foundation
import Logging
import Observation

/// Render state for the public profile screen.
public enum PublicProfileState: Sendable, Equatable {
    case loading
    case loaded(PublicProfileContent)
    case error(message: String)
}

/// Hydrated content emitted by `PublicProfileViewModel`.
public struct PublicProfileContent: Sendable, Equatable, Hashable {
    public let profile: PublicProfile
    public let header: PublicProfileHeader
    public let stats: StatsTabsContent

    public init(profile: PublicProfile, header: PublicProfileHeader, stats: StatsTabsContent) {
        self.profile = profile
        self.header = header
        self.stats = stats
    }
}

/// Header surface for the public profile screen — the VM-prepared
/// arguments passed straight into `ProfileHeader`.
public struct PublicProfileHeader: Sendable, Equatable, Hashable {
    public let displayName: String
    public let handle: String?
    public let locality: String?
    public let avatarURL: URL?
    public let isVerified: Bool
    public let identityBadges: [IdentityPillarBadge]

    public init(
        displayName: String,
        handle: String?,
        locality: String?,
        avatarURL: URL?,
        isVerified: Bool,
        identityBadges: [IdentityPillarBadge]
    ) {
        self.displayName = displayName
        self.handle = handle
        self.locality = locality
        self.avatarURL = avatarURL
        self.isVerified = isVerified
        self.identityBadges = identityBadges
    }
}

/// In-flight state for an action button (Connect, Block).
public enum PublicProfileActionState: Sendable, Equatable {
    case idle
    case inFlight
    case succeeded
    case failed(message: String)
}

/// View-model for the public profile screen.
@MainActor
@Observable
public final class PublicProfileViewModel {
    /// Render state.
    public private(set) var state: PublicProfileState = .loading

    /// Currently visible tab. Switching this is local; no refetch.
    public var selectedTab: ProfileTab = .about

    /// Connect button state — toggles between `idle` → `inFlight` →
    /// `succeeded` after a successful `POST /api/relationships/requests`.
    public private(set) var connectState: PublicProfileActionState = .idle

    /// Block action state — surfaces toast on success or failure of
    /// `POST /api/users/:userId/block`.
    public private(set) var blockState: PublicProfileActionState = .idle

    /// Drives the overflow action sheet presentation.
    public var showOverflow: Bool = false

    /// Transient toast surface used for action feedback.
    public var toastMessage: String?

    private let userId: String
    private let client: APIClient
    private let logger = Logger(label: "app.pantopus.ios.PublicProfile")

    init(userId: String, client: APIClient = .shared) {
        self.userId = userId
        self.client = client
    }

    public func load() async {
        state = .loading
        await fetch()
    }

    public func refresh() async {
        await fetch()
    }

    /// Send a connection request. Wraps
    /// `POST /api/relationships/requests` (relationships.js:67).
    public func connect() async {
        guard connectState != .inFlight, connectState != .succeeded else { return }
        connectState = .inFlight
        let body = ConnectionRequestBody(addresseeId: userId)
        do {
            let _: ConnectionRequestResponse = try await client.request(
                RelationshipsEndpoints.sendRequest(body: body)
            )
            connectState = .succeeded
            toastMessage = "Connection request sent"
        } catch let error as APIError {
            let message = friendlyMessage(for: error)
            connectState = .failed(message: message)
            toastMessage = message
            logger.warning("Connect failed: \(error)")
        } catch {
            connectState = .failed(message: "Something went wrong")
            toastMessage = "Couldn't send the request"
            logger.warning("Connect failed: \(error)")
        }
    }

    /// Block this user. Wraps `POST /api/users/:userId/block`
    /// (blocks.js:13).
    public func block() async {
        guard blockState != .inFlight else { return }
        blockState = .inFlight
        do {
            _ = try await client.request(BlocksEndpoints.block(userId: userId))
            blockState = .succeeded
            toastMessage = "User blocked"
        } catch let error as APIError {
            let message = friendlyMessage(for: error)
            blockState = .failed(message: message)
            toastMessage = message
            logger.warning("Block failed: \(error)")
        } catch {
            blockState = .failed(message: "Something went wrong")
            toastMessage = "Couldn't block this user"
            logger.warning("Block failed: \(error)")
        }
    }

    private func fetch() async {
        do {
            let profile = try await client.request(
                PublicProfileEndpoints.profile(id: userId),
                as: PublicProfile.self
            )
            state = .loaded(build(from: profile))
        } catch let error as APIError {
            logger.warning("Profile load failed: \(error)")
            state = .error(message: friendlyMessage(for: error))
        } catch {
            logger.warning("Profile load failed: \(error)")
            state = .error(message: "Something went wrong")
        }
    }

    private func build(from profile: PublicProfile) -> PublicProfileContent {
        let header = PublicProfileHeader(
            displayName: profile.displayName,
            handle: profile.username.isEmpty ? nil : profile.username,
            locality: profile.locality,
            avatarURL: (profile.profilePictureURL ?? profile.avatarURL).flatMap(URL.init(string:)),
            isVerified: profile.verified ?? false,
            identityBadges: buildBadges(profile)
        )

        var stats: [ProfileStatCell] = []
        if let reviewCount = profile.reviewCount, reviewCount > 0 || !profile.reviews.isEmpty {
            stats.append(
                ProfileStatCell(id: "reviews", value: "\(profile.reviewCount ?? profile.reviews.count)", label: "Reviews")
            )
        }
        if let rating = profile.averageRating, rating > 0 {
            stats.append(
                ProfileStatCell(id: "rating", value: String(format: "%.1f", rating), label: "Rating")
            )
        }
        if let gigsCompleted = profile.gigsCompleted, gigsCompleted > 0 {
            stats.append(
                ProfileStatCell(id: "gigs", value: "\(gigsCompleted)", label: "Gigs")
            )
        } else if let gigsPosted = profile.gigsPosted, gigsPosted > 0 {
            stats.append(
                ProfileStatCell(id: "gigs", value: "\(gigsPosted)", label: "Gigs")
            )
        }
        if stats.isEmpty {
            stats.append(ProfileStatCell(id: "placeholder", value: "—", label: "Activity"))
        }

        let reviewCards = profile.reviews.map { r in
            ProfileReviewCard(
                id: r.id ?? UUID().uuidString,
                reviewerName: r.reviewerName ?? "Anonymous",
                reviewerAvatarURL: r.reviewerAvatar.flatMap(URL.init(string:)),
                rating: r.rating,
                body: r.content ?? "",
                timestamp: relativeTimestamp(r.createdAt)
            )
        }

        let statsContent = StatsTabsContent(
            stats: stats,
            bio: profile.bio,
            skills: profile.skills,
            reviews: reviewCards
        )

        return PublicProfileContent(profile: profile, header: header, stats: statsContent)
    }

    private func buildBadges(_ profile: PublicProfile) -> [IdentityPillarBadge] {
        let verified = profile.verified ?? false
        return [
            IdentityPillarBadge(pillar: .personal, state: verified ? .verified : .unverified),
            IdentityPillarBadge(pillar: .home, state: hasHomeResidency(profile) ? .verified : .unverified),
            IdentityPillarBadge(
                pillar: .business,
                state: profile.accountType == "business" ? .verified : .unverified
            )
        ]
    }

    private func hasHomeResidency(_ profile: PublicProfile) -> Bool {
        guard case let .object(map) = profile.residency ?? .null else { return false }
        if case let .bool(value) = map["verified"] ?? .null { return value }
        return !map.isEmpty
    }

    private func relativeTimestamp(_ iso: String?) -> String {
        guard let iso else { return "" }
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
        case .notFound: "We couldn't find this profile."
        case .forbidden: "This profile is private."
        case .transport: "Check your connection and try again."
        default: "Something went wrong. Try again."
        }
    }
}
