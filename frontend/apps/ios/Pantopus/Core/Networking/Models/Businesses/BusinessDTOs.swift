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

// MARK: - /:businessId detail (P1.6 — Business Profile screen)

/// Full Business "User" row returned by `GET /api/businesses/:businessId`.
/// The backend `select('*')` projects every column; we decode only the
/// fields the Business Profile screen renders.
public struct BusinessUserDetailDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let username: String?
    public let name: String?
    public let email: String?
    public let bio: String?
    public let tagline: String?
    public let profilePictureURL: String?
    public let coverPhotoURL: String?
    public let accountType: String?
    public let city: String?
    public let state: String?
    public let verified: Bool?
    public let averageRating: Double?
    public let reviewCount: Int?
    public let followersCount: Int?
    public let gigsCompleted: Int?
    public let createdAt: String?

    private enum CodingKeys: String, CodingKey {
        case id, username, name, email, bio, tagline
        case profilePictureURL = "profile_picture_url"
        case coverPhotoURL = "cover_photo_url"
        case accountType = "account_type"
        case city, state, verified
        case averageRating = "average_rating"
        case reviewCount = "review_count"
        case followersCount = "followers_count"
        case gigsCompleted = "gigs_completed"
        case createdAt = "created_at"
    }
}

/// Geo point projection — `[longitude, latitude]` or `{lat, lng}` shape
/// (the backend normalises PostGIS into `{lat, lng}` via
/// `parsePostGISPoint`).
public struct BusinessGeoPoint: Decodable, Sendable, Hashable {
    public let lat: Double
    public let lng: Double

    private enum CodingKeys: String, CodingKey {
        case lat, lng
    }
}

/// `BusinessLocation` row.
public struct BusinessLocationDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let label: String?
    public let isPrimary: Bool?
    public let address: String?
    public let address2: String?
    public let city: String?
    public let state: String?
    public let zipcode: String?
    public let country: String?
    public let location: BusinessGeoPoint?
    public let phone: String?
    public let email: String?
    public let timezone: String?

    private enum CodingKeys: String, CodingKey {
        case id, label
        case isPrimary = "is_primary"
        case address, address2, city, state, zipcode, country
        case location, phone, email, timezone
    }
}

/// Full `BusinessProfile` row returned by `GET /api/businesses/:businessId`.
/// `select('*')` on the backend, but the iOS screen only decodes the
/// fields it renders.
public struct BusinessProfileDetailDTO: Decodable, Sendable, Hashable {
    public let businessUserId: String?
    public let businessType: String?
    public let categories: [String]?
    public let description: String?
    public let logoFileId: String?
    public let bannerFileId: String?
    public let publicEmail: String?
    public let publicPhone: String?
    public let website: String?
    public let foundedYear: Int?
    public let employeeCount: String?
    public let serviceArea: String?
    public let foundingBadge: Bool?
    public let isPublished: Bool?
    public let verificationStatus: String?
    public let primaryLocation: BusinessLocationDTO?

    private enum CodingKeys: String, CodingKey {
        case businessUserId = "business_user_id"
        case businessType = "business_type"
        case categories, description
        case logoFileId = "logo_file_id"
        case bannerFileId = "banner_file_id"
        case publicEmail = "public_email"
        case publicPhone = "public_phone"
        case website
        case foundedYear = "founded_year"
        case employeeCount = "employee_count"
        case serviceArea = "service_area"
        case foundingBadge = "founding_badge"
        case isPublished = "is_published"
        case verificationStatus = "verification_status"
        case primaryLocation = "primary_location"
    }
}

/// `access` sub-object on the detail response — only owner/staff see
/// `isOwner: true`. Surfaced through to the VM so the profile screen
/// could later swap the action footer for an "Edit" CTA, but P1.6 only
/// uses it to suppress the "Save" affordance for self-views.
public struct BusinessAccessDTO: Decodable, Sendable, Hashable {
    public let hasAccess: Bool
    public let isOwner: Bool
    public let roleBase: String?

    private enum CodingKeys: String, CodingKey {
        case hasAccess
        case isOwner
        case roleBase = "role_base"
    }
}

/// `GET /api/businesses/:businessId` response envelope — route
/// `backend/routes/businesses.js:912`.
public struct BusinessDetailResponse: Decodable, Sendable, Hashable {
    public let business: BusinessUserDetailDTO
    public let profile: BusinessProfileDetailDTO?
    public let locations: [BusinessLocationDTO]
    public let access: BusinessAccessDTO?

    private enum CodingKeys: String, CodingKey {
        case business, profile, locations, access
    }
}

// MARK: - /public/:username (used for hours + catalog)

/// One `BusinessHours` row returned in the `/public` payload. `day_of_week`
/// is `0..6` (Sunday=0). Closed days have `is_closed = true` and `nil`
/// open/close strings.
public struct BusinessHoursDTO: Decodable, Sendable, Hashable, Identifiable {
    public let rowId: String?
    public let locationId: String?
    public let dayOfWeek: Int
    public let openTime: String?
    public let closeTime: String?
    public let isClosed: Bool?

    /// Identifiable conformance with a stable id even when the backend
    /// row id is missing.
    public var id: String {
        rowId ?? "\(locationId ?? "")-\(dayOfWeek)"
    }

    private enum CodingKeys: String, CodingKey {
        case rowId = "id"
        case locationId = "location_id"
        case dayOfWeek = "day_of_week"
        case openTime = "open_time"
        case closeTime = "close_time"
        case isClosed = "is_closed"
    }
}

/// `BusinessCatalogItem` row. Used by the Services tab. Donation /
/// product items pass through too — the renderer just shows the kind
/// label and the price (or "Variable" when prices aren't set).
public struct BusinessCatalogItemDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let name: String
    public let description: String?
    public let kind: String?
    public let priceCents: Int?
    public let priceMaxCents: Int?
    public let priceUnit: String?
    public let currency: String?
    public let imageURL: String?
    public let isFeatured: Bool?

    private enum CodingKeys: String, CodingKey {
        case id, name, description, kind
        case priceCents = "price_cents"
        case priceMaxCents = "price_max_cents"
        case priceUnit = "price_unit"
        case currency
        case imageURL = "image_url"
        case isFeatured = "is_featured"
    }
}

/// `GET /api/businesses/public/:username` response (subset). Only the
/// fields the Business Profile screen reads are decoded; the response
/// is far larger (pages, blocks, founding slot, …).
/// Route `backend/routes/businesses.js:3277`.
public struct BusinessPublicResponse: Decodable, Sendable, Hashable {
    public let hours: [BusinessHoursDTO]
    public let catalog: [BusinessCatalogItemDTO]

    private enum CodingKeys: String, CodingKey {
        case hours, catalog
    }

    public init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        hours = try c.decodeIfPresent([BusinessHoursDTO].self, forKey: .hours) ?? []
        catalog = try c.decodeIfPresent([BusinessCatalogItemDTO].self, forKey: .catalog) ?? []
    }
}
