//
//  AudienceProfileDTOs.swift
//  Pantopus
//
//  Decoder shapes for /api/personas/* — the owner-facing audience
//  dashboard (T3.3). Backend column names stay (persona / fan /
//  broadcast / tier); the UI renames at the VM boundary.
//

import Foundation

// MARK: - GET /api/personas/me

/// Envelope from `GET /api/personas/me` — the persona summary + its
/// primary broadcast channel ID needed by the composer.
public struct PersonaMeResponse: Decodable, Sendable {
    public let persona: PersonaSummaryDTO?
    public let channel: BroadcastChannelDTO?
}

public struct PersonaSummaryDTO: Decodable, Sendable, Hashable {
    public let id: String
    public let handle: String?
    public let displayName: String?
    public let avatarUrl: String?
    public let bannerUrl: String?
    public let bio: String?
    public let category: String?
    public let audienceLabel: String?
    public let followerCount: Int?
    public let postCount: Int?

    enum CodingKeys: String, CodingKey {
        case id, handle, bio, category
        case displayName, avatarUrl, bannerUrl
        case audienceLabel
        case followerCount, postCount
    }
}

public struct BroadcastChannelDTO: Decodable, Sendable, Hashable {
    public let id: String
    public let title: String?
    public let description: String?
    public let status: String?

    enum CodingKeys: String, CodingKey {
        case id, title, description, status
    }
}

// MARK: - GET /api/personas/me/audience

public struct AudienceListResponse: Decodable, Sendable {
    public let persona: PersonaSummaryDTO?
    public let items: [FanDTO]
    public let counts: AudienceCountsDTO
}

public struct AudienceCountsDTO: Decodable, Sendable, Hashable {
    public let totalActive: Int?
    public let pending: Int?
    public let byTier: [String: Int]?

    enum CodingKeys: String, CodingKey {
        case totalActive, pending, byTier
    }
}

/// One follower row in the creator's audience list — strictly the
/// creator-side serializer (no PII per the privacy gate). Backend
/// returns camelCase keys here (`fanHandle` not `fan_handle`), so no
/// per-field CodingKeys are needed.
public struct FanDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let membershipId: String?
    public let fanHandle: String?
    public let fanDisplayName: String?
    public let fanAvatarUrl: String?
    public let status: String?
    public let tier: FanTierBadgeDTO?
    public let verifiedLocal: Bool?
    public let joinedMonth: String?
    public let tenureMonths: Int?
    public let cancelAtPeriodEnd: Bool?

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        // Server emits `membershipId` (no separate `id`); map either
        // key to our `id` so the serializer evolution stays compatible.
        let mid = try container.decodeIfPresent(String.self, forKey: .membershipId)
        let raw = try container.decodeIfPresent(String.self, forKey: .id)
        id = mid ?? raw ?? ""
        membershipId = mid
        fanHandle = try container.decodeIfPresent(String.self, forKey: .fanHandle)
        fanDisplayName = try container.decodeIfPresent(String.self, forKey: .fanDisplayName)
        fanAvatarUrl = try container.decodeIfPresent(String.self, forKey: .fanAvatarUrl)
        status = try container.decodeIfPresent(String.self, forKey: .status)
        tier = try container.decodeIfPresent(FanTierBadgeDTO.self, forKey: .tier)
        verifiedLocal = try container.decodeIfPresent(Bool.self, forKey: .verifiedLocal)
        joinedMonth = try container.decodeIfPresent(String.self, forKey: .joinedMonth)
        tenureMonths = try container.decodeIfPresent(Int.self, forKey: .tenureMonths)
        cancelAtPeriodEnd = try container.decodeIfPresent(Bool.self, forKey: .cancelAtPeriodEnd)
    }

    enum CodingKeys: String, CodingKey {
        case id, membershipId, fanHandle, fanDisplayName, fanAvatarUrl
        case status, tier, verifiedLocal, joinedMonth, tenureMonths, cancelAtPeriodEnd
    }
}

public struct FanTierBadgeDTO: Decodable, Sendable, Hashable {
    public let rank: Int?
    public let name: String?
}

// MARK: - GET /api/personas/:handle/posts

public struct PersonaPostsResponse: Decodable, Sendable {
    public let posts: [PersonaPostDTO]
}

public struct PersonaPostDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let body: String?
    public let createdAt: String?
    public let visibility: String?
    public let targetTierRank: Int?
    public let deliveredCount: Int?
    public let readCount: Int?
    public let mediaUrls: [String]?

    enum CodingKeys: String, CodingKey {
        case id, body, visibility
        case createdAt = "created_at"
        case targetTierRank = "target_tier_rank"
        case deliveredCount = "delivered_count"
        case readCount = "read_count"
        case mediaUrls = "media_urls"
    }
}

// MARK: - GET /api/personas/:handle/tiers

public struct PersonaTiersResponse: Decodable, Sendable {
    public let tiers: [PersonaTierDTO]
}

public struct PersonaTierDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let rank: Int
    public let name: String
    public let description: String?
    public let priceCents: Int?
    public let currency: String?
}

// MARK: - GET /api/personas/:id/membership-stats

public struct MembershipStatsResponse: Decodable, Sendable {
    public let counts: MembershipStatsCounts
}

public struct MembershipStatsCounts: Decodable, Sendable, Hashable {
    public let followers: Int?
    public let members: Int?
    public let insiders: Int?
    public let direct: Int?
}

// MARK: - GET /api/personas/:id/dms/threads

public struct PersonaThreadsResponse: Decodable, Sendable {
    public let threads: [PersonaThreadDTO]
}

public struct PersonaThreadDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let membershipId: String?
    public let fanHandle: String?
    public let fanDisplayName: String?
    public let fanAvatarUrl: String?
    public let tier: FanTierBadgeDTO?
    public let lastMessagePreview: String?
    public let lastMessageAt: String?
    public let unreadCount: Int?
    public let status: String?
}

// MARK: - POST /api/broadcast/channels/:id/messages

public struct PublishUpdateResponse: Decodable, Sendable {
    public let message: BroadcastMessageDTO?
}

public struct BroadcastMessageDTO: Decodable, Sendable, Hashable {
    public let id: String?
    public let body: String?
    public let visibility: String?
    public let targetTierRank: Int?
    public let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id, body, visibility
        case targetTierRank = "target_tier_rank"
        case createdAt = "created_at"
    }
}
