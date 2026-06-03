//
//  ProfessionalDTOs.swift
//  Pantopus
//
//  DTOs for `backend/routes/professional.js`. Field names mirror the
//  `UserProfessionalProfile` row (snake_case) via explicit `CodingKeys`.
//

import Foundation

// MARK: - GET /api/professional/profile/me

/// `{ profile: … | null }`. `profile` is null when the user has not enabled
/// professional mode.
public struct ProfessionalProfileResponse: Decodable, Sendable, Hashable {
    public let profile: ProfessionalProfileDTO?
}

/// A `UserProfessionalProfile` row.
public struct ProfessionalProfileDTO: Decodable, Sendable, Hashable {
    public let headline: String?
    public let bio: String?
    public let categories: [String]?
    public let serviceArea: ServiceArea?
    public let pricingMeta: PricingMeta?
    public let isPublic: Bool?
    public let isActive: Bool?
    public let verificationTier: Int?
    public let verificationStatus: String?

    private enum CodingKeys: String, CodingKey {
        case headline, bio, categories
        case serviceArea = "service_area"
        case pricingMeta = "pricing_meta"
        case isPublic = "is_public"
        case isActive = "is_active"
        case verificationTier = "verification_tier"
        case verificationStatus = "verification_status"
    }

    public struct ServiceArea: Decodable, Sendable, Hashable {
        public let city: String?
        public let state: String?
    }

    public struct PricingMeta: Decodable, Sendable, Hashable {
        public let hourlyRate: Double?
        public let currency: String?

        private enum CodingKeys: String, CodingKey {
            case hourlyRate = "hourly_rate"
            case currency
        }
    }
}

// MARK: - GET /api/professional/verification/status

public struct ProfessionalVerificationStatusResponse: Decodable, Sendable, Hashable {
    public let tier: Int?
    public let status: String?
    public let submittedAt: String?
    public let completedAt: String?

    private enum CodingKeys: String, CodingKey {
        case tier, status
        case submittedAt = "submitted_at"
        case completedAt = "completed_at"
    }
}

// MARK: - PATCH /api/professional/profile/me (request)

/// Partial update body. All fields optional; nil keys are omitted by the
/// encoder so only edited fields are sent.
public struct ProfessionalProfileUpdateRequest: Encodable, Sendable, Hashable {
    public let headline: String?
    public let bio: String?
    public let isPublic: Bool?
    public let isActive: Bool?

    public init(headline: String? = nil, bio: String? = nil, isPublic: Bool? = nil, isActive: Bool? = nil) {
        self.headline = headline
        self.bio = bio
        self.isPublic = isPublic
        self.isActive = isActive
    }

    private enum CodingKeys: String, CodingKey {
        case headline, bio
        case isPublic = "is_public"
        case isActive = "is_active"
    }
}
