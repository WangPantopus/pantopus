//
//  BusinessDTOs.swift
//  Pantopus
//
//  Decoder shapes for `/api/businesses/my-businesses`. The response is a
//  list of `BusinessMembership` rows — each is a (seat + business user +
//  optional business profile) join. Route:
//  `backend/routes/businesses.js:682`.
//

import Foundation

/// Lightweight business "user" projection emitted alongside every
/// membership row. The backend joins `User` (account_type='business')
/// plus `city, state` for the locality body.
public struct BusinessUserDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let username: String?
    public let name: String?
    public let email: String?
    public let profilePictureURL: String?
    public let accountType: String?
    public let city: String?
    public let state: String?

    private enum CodingKeys: String, CodingKey {
        case id, username, name, email
        case profilePictureURL = "profile_picture_url"
        case accountType = "account_type"
        case city, state
    }
}

/// Optional `BusinessProfile` join — present when the business has
/// onboarded a profile (categories, logo, description). Always nil for
/// freshly-created businesses with no profile yet.
public struct BusinessProfileDTO: Decodable, Sendable, Hashable {
    public let businessUserId: String?
    public let businessType: String?
    public let categories: [String]?
    public let isPublished: Bool?
    public let logoFileId: String?
    public let bannerFileId: String?
    public let description: String?

    private enum CodingKeys: String, CodingKey {
        case businessUserId = "business_user_id"
        case businessType = "business_type"
        case categories
        case isPublished = "is_published"
        case logoFileId = "logo_file_id"
        case bannerFileId = "banner_file_id"
        case description
    }
}

/// One row from `/api/businesses/my-businesses` — the membership +
/// business projection used by My businesses.
public struct BusinessMembership: Decodable, Sendable, Hashable, Identifiable {
    /// Seat id (or BusinessTeam id for legacy rows).
    public let id: String
    /// Role base: `owner / admin / manager / staff / viewer`.
    public let roleBase: String?
    /// Free-form display title (e.g. "Founder", "Manager"). Optional.
    public let title: String?
    /// When the seat was joined (legacy only — seats return `nil`).
    public let joinedAt: String?
    /// User id of the business — same as `business.id`.
    public let businessUserId: String
    /// The business profile shell — name, locality, avatar.
    public let business: BusinessUserDTO
    /// Optional rich profile (categories, description, is_published).
    public let profile: BusinessProfileDTO?

    private enum CodingKeys: String, CodingKey {
        case id
        case roleBase = "role_base"
        case title
        case joinedAt = "joined_at"
        case businessUserId = "business_user_id"
        case business
        case profile
    }
}

/// `GET /api/businesses/my-businesses` envelope — route
/// `backend/routes/businesses.js:682`.
public struct MyBusinessesResponse: Decodable, Sendable {
    public let businesses: [BusinessMembership]
}
