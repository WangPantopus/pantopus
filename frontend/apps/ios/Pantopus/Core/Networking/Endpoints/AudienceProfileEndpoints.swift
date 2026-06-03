//
//  AudienceProfileEndpoints.swift
//  Pantopus
//
//  T3.3 Public Profile management (creator-facing audience dashboard).
//  Backend keeps the legacy persona / broadcast / audience names on
//  the wire; UI labels follow
//  docs/identity-firewall-ui-ux-redesign-2026-05-06.md
//  ("Public Profile" / "Updates" / "Followers" / "Post update").
//

import Foundation

public enum AudienceProfileEndpoints {
    /// `GET /api/personas/me` — owner-side persona + primary broadcast
    /// channel. Route `backend/routes/personas.js:367`.
    public static let me = Endpoint(method: .get, path: "/api/personas/me")

    /// `GET /api/personas/me/audience` — fan list + counts by tier.
    /// Route `backend/routes/personas.js:649`.
    public static func audience(
        sort: String? = nil,
        status: String? = nil,
        tierRank: Int? = nil
    ) -> Endpoint {
        var query: [String: String] = [:]
        if let sort { query["sort"] = sort }
        if let status { query["status"] = status }
        if let tierRank { query["tier_rank"] = String(tierRank) }
        return Endpoint(method: .get, path: "/api/personas/me/audience", query: query)
    }

    /// `PATCH /api/personas/me/audience/:membershipId` — owner-side action
    /// on a single audience member. `action` ∈
    /// `approve / decline / remove / mute / unmute`; the backend maps each
    /// to a status transition and writes an audit-log entry. Route
    /// `backend/routes/personas.js:753`.
    public static func memberAction(membershipId: String, action: String) -> Endpoint {
        Endpoint(
            method: .patch,
            path: "/api/personas/me/audience/\(membershipId)",
            body: AudienceMemberActionBody(action: action)
        )
    }

    /// `GET /api/personas/:handle/posts` — recent Update posts.
    /// Route `backend/routes/personas.js:1046`.
    public static func posts(handle: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/personas/\(handle)/posts")
    }

    /// `GET /api/personas/:handle/tiers` — tier ladder (chips).
    /// Route `backend/routes/personas.js:1111`.
    public static func tiers(handle: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/personas/\(handle)/tiers")
    }

    /// `GET /api/personas/:id/membership-stats` — owner-only counts
    /// by tier for analytics cells. Route
    /// `backend/routes/personas.js:1256`.
    public static func membershipStats(personaId: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/personas/\(personaId)/membership-stats")
    }

    /// `GET /api/personas/:id/dms/threads` — owner inbox of fan
    /// threads. Route `backend/routes/personaDms.js:185`.
    public static func threads(personaId: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/personas/\(personaId)/dms/threads")
    }

    /// `POST /api/broadcast/channels/:channelId/messages` — publish a
    /// new Update. Route `backend/routes/broadcastChannels.js:450`.
    public static func publishUpdate(
        channelId: String,
        body: PublishUpdateBody
    ) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/broadcast/channels/\(channelId)/messages",
            body: body
        )
    }

    /// `GET /api/broadcast/channels/:channelId/messages` — broadcast
    /// history, most-recent first. `limit`-only (no offset/cursor). Route
    /// `backend/routes/broadcastChannels.js:315`.
    public static func broadcastHistory(channelId: String, limit: Int = 50) -> Endpoint {
        Endpoint(
            method: .get,
            path: "/api/broadcast/channels/\(channelId)/messages",
            query: ["limit": String(limit)]
        )
    }
}

/// Body for the broadcast-publish route. `visibility` valid values:
/// `public / followers / tier_or_above / subscribers`. When `tier_or_above`
/// is selected, `target_tier_rank` (1-4) is required.
public struct PublishUpdateBody: Encodable, Sendable {
    public var body: String
    public var visibility: String
    public var targetTierRank: Int?

    public init(body: String, visibility: String, targetTierRank: Int? = nil) {
        self.body = body
        self.visibility = visibility
        self.targetTierRank = targetTierRank
    }

    enum CodingKeys: String, CodingKey {
        case body, visibility
        case targetTierRank = "target_tier_rank"
    }
}

/// Body for the owner-side audience-member action route (A22.2 "Your
/// audience"). `action` valid values: `approve / decline / remove / mute /
/// unmute`.
public struct AudienceMemberActionBody: Encodable, Sendable {
    public let action: String

    public init(action: String) {
        self.action = action
    }
}
