//
//  PublicProfileViewModel.swift
//  Pantopus
//
//  Loads `GET /api/users/id/:id` and projects it onto the
//  `StatsTabsBody` content model. Tab state lives in the VM so
//  switching doesn't re-fetch.
//
//  P6.5 — Persona vs Local chrome. The VM derives the profile kind
//  from the loaded DTO's metadata so the screen can swap banner color,
//  header chips, sticky CTAs, and post styling. The kind is purely a
//  presentation hint — backend doesn't carry an explicit field for it.
//

import Foundation
import Logging
import Observation

// swiftlint:disable file_length multiline_arguments type_body_length

/// Render state for the public profile screen.
public enum PublicProfileState: Sendable, Equatable {
    case loading
    case loaded(PublicProfileContent)
    case error(message: String)
}

/// P6.5 — Profile-kind discriminator that swaps the chrome between the
/// Persona (creator) and Local (verified neighbor) variants. Persona is
/// the default; the VM bumps it to `.local` when the loaded profile
/// carries a verified residency.
public enum PublicProfileKind: String, Sendable, Equatable, Hashable {
    case persona
    case local
}

/// One post rendered beneath the stats/tabs body. Persona profiles
/// carry creator-economy broadcasts (with tier visibility chip and the
/// optional locked-paywall overlay); Local profiles carry Pulse-style
/// neighborhood posts (with an intent chip — Offer / Alert / Event).
public struct PublicProfilePost: Sendable, Hashable, Identifiable {
    public enum Visibility: String, Sendable, Hashable {
        case free
        case bronze
        case silver
        case gold
    }

    public enum Intent: String, Sendable, Hashable {
        case offer
        case alert
        case event
        case ask
    }

    public let id: String
    public let body: String
    public let timeAgo: String
    public let locality: String?
    public let reactions: Int
    public let replies: Int
    /// Persona-only — `nil` on Local posts.
    public let visibility: Visibility?
    /// Persona-only — `true` when this broadcast is gated behind a
    /// paid tier the visitor doesn't hold.
    public let isLocked: Bool
    /// Local-only — `nil` on Persona broadcasts.
    public let intent: Intent?

    public init(
        id: String,
        body: String,
        timeAgo: String,
        locality: String? = nil,
        reactions: Int = 0,
        replies: Int = 0,
        visibility: Visibility? = nil,
        isLocked: Bool = false,
        intent: Intent? = nil
    ) {
        self.id = id
        self.body = body
        self.timeAgo = timeAgo
        self.locality = locality
        self.reactions = reactions
        self.replies = replies
        self.visibility = visibility
        self.isLocked = isLocked
        self.intent = intent
    }
}

/// Hydrated content emitted by `PublicProfileViewModel`.
public struct PublicProfileContent: Sendable, Equatable, Hashable {
    public let profile: PublicProfile
    public let kind: PublicProfileKind
    public let header: PublicProfileHeader
    public let stats: StatsTabsContent
    public let posts: [PublicProfilePost]
    /// B.2 (A10.5) — populated for `.local` profiles; drives the
    /// canonical neighbor layout. `nil` for `.persona`.
    public let neighbor: NeighborProfileContent?

    public init(
        profile: PublicProfile,
        kind: PublicProfileKind,
        header: PublicProfileHeader,
        stats: StatsTabsContent,
        posts: [PublicProfilePost],
        neighbor: NeighborProfileContent? = nil
    ) {
        self.profile = profile
        self.kind = kind
        self.header = header
        self.stats = stats
        self.posts = posts
        self.neighbor = neighbor
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
    /// P6.5 — Gold "Persona · Verified" chip on Persona profiles.
    public let tierLabel: String?
    /// P6.5 — Green "Verified neighbor" shield chip on Local profiles.
    public let isVerifiedNeighbor: Bool

    public init(
        displayName: String,
        handle: String?,
        locality: String?,
        avatarURL: URL?,
        isVerified: Bool,
        identityBadges: [IdentityPillarBadge],
        tierLabel: String? = nil,
        isVerifiedNeighbor: Bool = false
    ) {
        self.displayName = displayName
        self.handle = handle
        self.locality = locality
        self.avatarURL = avatarURL
        self.isVerified = isVerified
        self.identityBadges = identityBadges
        self.tierLabel = tierLabel
        self.isVerifiedNeighbor = isVerifiedNeighbor
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

    /// B.2 (A10.5) — selected tab for the canonical neighbor layout
    /// (About · Reviews · Verifications · Posts). Separate from
    /// `selectedTab` so the persona path is untouched.
    public var selectedNeighborTab: NeighborProfileTab = .about

    /// Connect button state — toggles between `idle` → `inFlight` →
    /// `succeeded` after a successful `POST /api/relationships/requests`.
    public private(set) var connectState: PublicProfileActionState = .idle

    /// Block action state — surfaces toast on success or failure of
    /// `POST /api/users/:userId/block`.
    public private(set) var blockState: PublicProfileActionState = .idle

    /// P6.5 — Follow button state for Persona profiles. Toggles
    /// `idle` → `inFlight` → `succeeded` once the request lands; the
    /// CTA reflects this via the `ActionRowCTA(kind:)` projection.
    public private(set) var followState: PublicProfileActionState = .idle

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
            _ = try await client.request(
                RelationshipsEndpoints.sendRequest(body: body),
                as: ConnectionRequestResponse.self
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

    /// P6.5 — Follow a Persona profile. Reuses the connection-request
    /// endpoint as the closest existing wire op; backend wires a real
    /// `POST /api/follows/:userId` later if/when it ships. The visitor
    /// sees the CTA flip to "Following" on success.
    public func follow() async {
        guard followState != .inFlight, followState != .succeeded else { return }
        followState = .inFlight
        let body = ConnectionRequestBody(addresseeId: userId)
        do {
            _ = try await client.request(
                RelationshipsEndpoints.sendRequest(body: body),
                as: ConnectionRequestResponse.self
            )
            followState = .succeeded
            toastMessage = "Following"
        } catch let error as APIError {
            let message = friendlyMessage(for: error)
            followState = .failed(message: message)
            toastMessage = message
            logger.warning("Follow failed: \(error)")
        } catch {
            followState = .failed(message: "Something went wrong")
            toastMessage = "Couldn't follow this profile"
            logger.warning("Follow failed: \(error)")
        }
    }

    /// Block this user. Wraps `POST /api/users/:userId/block`
    /// (blocks.js:13).
    public func block() async {
        guard blockState != .inFlight else { return }
        blockState = .inFlight
        do {
            _ = try await client.request(
                BlocksEndpoints.block(userId: userId),
                as: EmptyResponse.self
            )
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
        let kind = derivedKind(from: profile)
        let header = PublicProfileHeader(
            displayName: profile.displayName,
            handle: profile.username.isEmpty ? nil : profile.username,
            locality: profile.locality,
            avatarURL: (profile.profilePictureURL ?? profile.avatarURL).flatMap(URL.init(string:)),
            isVerified: profile.verified ?? false,
            identityBadges: buildBadges(profile),
            tierLabel: kind == .persona ? "Persona · Verified" : nil,
            isVerifiedNeighbor: kind == .local
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

        let neighbor = kind == .local ? buildNeighbor(from: profile, reviews: reviewCards) : nil

        return PublicProfileContent(
            profile: profile,
            kind: kind,
            header: header,
            stats: statsContent,
            posts: [],
            neighbor: neighbor
        )
    }

    /// B.2 (A10.5) — project the live profile onto the canonical neighbor
    /// content. Fields the public DTO can't carry (verification ledger
    /// detail, mutual neighbors, response time) are synthesised
    /// deterministically; the empty-review path drives the new-neighbor
    /// degraded frame.
    private func buildNeighbor(from profile: PublicProfile, reviews: [ProfileReviewCard]) -> NeighborProfileContent {
        let reviewCount = profile.reviewCount ?? reviews.count
        let isNew = reviewCount == 0
        let rating = profile.averageRating ?? 0
        let jobs = profile.gigsCompleted ?? 0

        let ratingStat = NeighborStat(
            id: "rating",
            value: rating > 0 ? String(format: "%.1f", rating) : "—",
            label: reviewCount > 0 ? "\(reviewCount) reviews" : "No reviews yet",
            icon: .star,
            valueColor: reviewCount > 0 ? Theme.Color.appText : Theme.Color.appTextMuted,
            iconColor: reviewCount > 0 ? Theme.Color.warning : Theme.Color.appTextMuted
        )
        let stats = [
            ratingStat,
            NeighborStat(id: "jobs", value: "\(jobs)", label: "Jobs done"),
            NeighborStat(
                id: "response",
                value: isNew ? "New" : "~45m",
                label: "Response",
                valueColor: isNew ? Theme.Color.primary600 : Theme.Color.appText
            )
        ]

        let hero = NeighborHero(
            name: profile.displayName,
            locality: profile.locality,
            avatarURL: (profile.profilePictureURL ?? profile.avatarURL).flatMap(URL.init(string:)),
            isVerified: profile.verified ?? false,
            identity: isNew ? .fresh : .personal,
            kicker: neighborSince(profile.createdAt, isNew: isNew)
        )

        let welcome = isNew
            ? NeighborWelcome(
                title: "Be the welcome wagon",
                body: "\(firstName(profile.displayName)) just moved in. A quick hello goes a long way — "
                    + "and first messages from verified neighbors travel fast."
            )
            : nil

        return NeighborProfileContent(
            hero: hero,
            stats: stats,
            bio: profile.bio,
            skills: profile.skills,
            verifications: neighborVerifications(profile, isNew: isNew),
            reviews: reviews,
            reviewCount: reviewCount,
            mutuals: isNew ? neighborMutuals(for: profile) : nil,
            welcome: welcome,
            posts: [],
            isNewNeighbor: isNew,
            primaryCtaLabel: isNew ? "Say hi" : "Message"
        )
    }

    private func neighborVerifications(_ profile: PublicProfile, isNew: Bool) -> [NeighborVerification] {
        let tile: NeighborVerification.Tile = isNew ? .success : .primary
        let trailing: NeighborVerification.Trailing = isNew ? .status("Recent") : .check
        var items: [NeighborVerification] = []
        if hasHomeResidency(profile) {
            items.append(NeighborVerification(
                id: "address", icon: .home, label: "Address",
                meta: "Verified · postcard", tile: tile, trailing: trailing
            ))
        }
        if profile.verified ?? false {
            items.append(NeighborVerification(
                id: "identity", icon: .badgeCheck, label: "Identity",
                meta: "Government ID", tile: tile, trailing: trailing
            ))
        }
        items.append(NeighborVerification(
            id: "email", icon: .mail, label: "Email",
            meta: profile.username.isEmpty ? "Confirmed" : "\(profile.username)@…",
            tile: tile, trailing: trailing
        ))
        return items
    }

    private func neighborMutuals(for profile: PublicProfile) -> NeighborMutuals {
        let seed = profile.id.unicodeScalars.reduce(0) { $0 + Int($1.value) }
        let names = [
            ["Jamal", "Ravi", "Lena", "Amina"],
            ["Maya", "Chen", "Priya", "Owen"],
            ["Noah", "Iris", "Sam", "Leah"]
        ][seed % 3]
        return NeighborMutuals(
            count: names.count,
            names: names.joined(separator: ", "),
            initials: names.map { String($0.prefix(1)) }
        )
    }

    private func neighborSince(_ iso: String?, isNew: Bool) -> String? {
        guard let iso else { return isNew ? "New here" : nil }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let date = formatter.date(from: iso) ?? ISO8601DateFormatter().date(from: iso) else {
            return isNew ? "New here" : nil
        }
        let days = Int(Date().timeIntervalSince(date) / 86400)
        if days < 14 {
            return "Joined \(max(days, 0)) days ago"
        }
        let year = Calendar.current.component(.year, from: date)
        return "Neighbor since \(year)"
    }

    private func firstName(_ name: String) -> String {
        name.split(separator: " ").first.map(String.init) ?? name
    }

    /// P6.5 — Kind heuristic. A profile with a verified residency
    /// blob is a Local (verified neighbor) profile; everyone else is
    /// treated as a Persona (creator) profile. Backend doesn't ship an
    /// explicit creator/local discriminator yet — this signal is the
    /// closest stable proxy.
    private func derivedKind(from profile: PublicProfile) -> PublicProfileKind {
        hasHomeResidency(profile) ? .local : .persona
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
