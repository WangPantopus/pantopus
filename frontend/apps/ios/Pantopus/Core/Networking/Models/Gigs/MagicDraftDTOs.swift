//
//  MagicDraftDTOs.swift
//  Pantopus
//
//  Request/response shapes for `POST /api/gigs/magic-draft`
//  (`backend/routes/magicTask.js:335`) — the "one sentence to structured
//  task" parser behind the Post-a-Task wizard's Magic describe step.
//  The draft payload is decoded tolerantly: every field is optional so
//  module-suggestion keys we don't model yet are simply ignored.
//

import Foundation

/// Body for `POST /api/gigs/magic-draft`. `text` is the user's
/// plain-English describe input (3–2000 chars, validated server-side);
/// `context` carries optional hints the parser can fold in.
public struct MagicDraftRequestBody: Encodable, Sendable {
    public let text: String
    public let context: MagicDraftContext?
    public let attachmentUrls: [String]?

    public init(
        text: String,
        context: MagicDraftContext? = nil,
        attachmentUrls: [String]? = nil
    ) {
        self.text = text
        self.context = context
        self.attachmentUrls = attachmentUrls
    }
}

/// Optional parse hints — caller location, a budget the user already
/// typed, and the chosen location mode.
public struct MagicDraftContext: Encodable, Sendable {
    public let latitude: Double?
    public let longitude: Double?
    public let budget: Double?
    public let locationMode: String?

    public init(
        latitude: Double? = nil,
        longitude: Double? = nil,
        budget: Double? = nil,
        locationMode: String? = nil
    ) {
        self.latitude = latitude
        self.longitude = longitude
        self.budget = budget
        self.locationMode = locationMode
    }
}

/// Envelope from `POST /api/gigs/magic-draft`. `_fallback` rides along
/// when the backend's AI path failed and it served the deterministic
/// parse instead.
public struct MagicDraftResponse: Decodable, Sendable {
    public let draft: MagicDraftDTO
    public let confidence: Double?
    public let fieldConfidence: [String: Double]?
    public let clarifyingQuestion: String?
    public let source: String?
    public let elapsed: Int?
    public let isFallback: Bool?

    enum CodingKeys: String, CodingKey {
        case draft, confidence, fieldConfidence, clarifyingQuestion, source, elapsed
        case isFallback = "_fallback"
    }
}

/// The structured draft. Field names are snake_case on the wire;
/// `category` is one of the backend's `VALID_CATEGORIES` (may be
/// "Other"). Only the fields the wizard prefills are modeled.
public struct MagicDraftDTO: Decodable, Sendable, Hashable {
    public let title: String?
    public let description: String?
    public let category: String?
    public let taskArchetype: String?
    /// "offers" | "fixed" | "hourly"
    public let payType: String?
    public let budgetFixed: Double?
    public let hourlyRate: Double?
    public let budgetRange: MagicDraftBudgetRange?
    /// "asap" | "today" | "scheduled" | "flexible"
    public let scheduleType: String?
    public let locationMode: String?
    public let privacyLevel: String?
    public let tags: [String]?
    public let isUrgent: Bool?
    public let attachmentsSuggested: Bool?

    enum CodingKeys: String, CodingKey {
        case title, description, category, tags
        case taskArchetype = "task_archetype"
        case payType = "pay_type"
        case budgetFixed = "budget_fixed"
        case hourlyRate = "hourly_rate"
        case budgetRange = "budget_range"
        case scheduleType = "schedule_type"
        case locationMode = "location_mode"
        case privacyLevel = "privacy_level"
        case isUrgent = "is_urgent"
        case attachmentsSuggested = "attachments_suggested"
    }
}

/// Suggested `{ min, max }` budget band for open-to-bids drafts.
public struct MagicDraftBudgetRange: Decodable, Sendable, Hashable {
    public let min: Double
    public let max: Double
}
