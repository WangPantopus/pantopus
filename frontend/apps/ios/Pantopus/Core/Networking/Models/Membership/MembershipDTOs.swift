//
//  MembershipDTOs.swift
//  Pantopus
//
//  Decoder shapes for `GET /api/personas/:id/membership` — the fan-side
//  view of their own membership (A10.8). Built by `serializeMembershipForFan`
//  (`backend/serializers/identitySerializers.js:352`), wrapped in
//  `stripNullish`, so several keys (e.g. `fanHandle`, period bounds) may be
//  absent — every field is therefore optional. The embedded `persona` is the
//  shared `serializeAudienceProfileForViewer` shape (camelCase keys).
//

import Foundation

// MARK: - GET /api/personas/:id/membership  (+ POST .../membership/cancel)

/// Envelope for the membership read and the cancel mutation (the cancel
/// route echoes the re-fetched membership in the same shape).
public struct PersonaMembershipResponse: Decodable, Sendable {
    public let membership: PersonaMembershipDTO?
}

public struct PersonaMembershipDTO: Decodable, Sendable {
    public let membershipId: String?
    public let persona: MembershipPersonaDTO?
    public let tier: MembershipTierDTO?
    public let status: String?
    public let cancelAtPeriodEnd: Bool?
    public let currentPeriodStart: String?
    public let currentPeriodEnd: String?
    // `scheduledTierChange` + `quotaRemaining` are present on the wire but
    // unused by the fan manage surface — Codable ignores the extra keys.
}

/// The persona the fan supports — `serializeAudienceProfileForViewer`
/// (camelCase). Only the fields the membership card binds to are decoded.
public struct MembershipPersonaDTO: Decodable, Sendable {
    public let id: String?
    public let handle: String?
    public let displayName: String?
    public let avatarUrl: String?
    public let category: String?
    public let audienceLabel: String?
    public let followerCount: Int?
    public let credential: CredentialDTO?

    public struct CredentialDTO: Decodable, Sendable {
        public let status: String?
        public let label: String?
    }
}

/// The fan's tier. Perk fields (`msgThreadsPerPeriod`, `creatorCanInitiateDm`,
/// `replyPolicy`) drive the "What you get" benefit rows. Backend emits
/// camelCase here.
public struct MembershipTierDTO: Decodable, Sendable {
    public let id: String?
    public let rank: Int?
    public let name: String?
    public let priceCents: Int?
    public let currency: String?
    public let billingInterval: String?
    public let msgThreadsPerPeriod: Int?
    public let creatorCanInitiateDm: Bool?
    public let replyPolicy: String?
}
