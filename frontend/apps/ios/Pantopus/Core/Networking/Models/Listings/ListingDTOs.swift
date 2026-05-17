//
//  ListingDTOs.swift
//  Pantopus
//
//  Decoder shapes for `/api/listings/*`. Mirrors the
//  `normalizeListingRow` projection in
//  `backend/services/marketplace/marketplaceService.js:82`.
//

import Foundation

/// One row from the marketplace endpoints.
public struct ListingDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let userId: String?
    public let title: String?
    public let description: String?
    public let price: Double?
    public let isFree: Bool?
    public let category: String?
    public let condition: String?
    public let status: String?
    public let mediaUrls: [String]?
    public let firstImage: String?
    public let layer: String?
    public let listingType: String?
    public let latitude: Double?
    public let longitude: Double?
    public let locationName: String?
    public let distanceMeters: Double?
    public let createdAt: String?
    public let userHasSaved: Bool?
    public let approxLocation: ListingApproxLocation?
    /// Lifetime impressions (from `Listing.view_count`).
    public let viewCount: Int?
    /// Live offer/message count from the buyer side (from
    /// `Listing.active_offer_count`). Drives the chip-meta line on My
    /// listings rows.
    public let activeOfferCount: Int?
    /// When the seller marked the listing sold.
    public let soldAt: String?
    /// When the seller archived the listing.
    public let archivedAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case title, description, price
        case isFree = "is_free"
        case category, condition, status
        case mediaUrls = "media_urls"
        case firstImage = "first_image"
        case layer
        case listingType = "listing_type"
        case latitude
        case longitude
        case locationName = "location_name"
        case distanceMeters = "distance_meters"
        case createdAt = "created_at"
        case userHasSaved
        case approxLocation = "approx_location"
        case viewCount = "view_count"
        case activeOfferCount = "active_offer_count"
        case soldAt = "sold_at"
        case archivedAt = "archived_at"
    }
}

/// Envelope from `GET /api/listings/me` — the current user's own
/// listings, optionally filtered by `status`. Route:
/// `backend/routes/listings.js:1058`.
public struct MyListingsResponse: Decodable, Sendable {
    public let listings: [ListingDTO]
    public let pagination: ListingsPagination?
}

/// Privacy-safe coarse location surfaced on map / in-bounds responses.
public struct ListingApproxLocation: Decodable, Sendable, Hashable {
    public let latitude: Double?
    public let longitude: Double?
    public let label: String?
}

/// Pagination cursor returned by `/browse` and `/nearby`.
public struct ListingsPagination: Decodable, Sendable, Hashable {
    public let limit: Int?
    public let offset: Int?
    public let hasMore: Bool?
    public let nextCursor: String?

    enum CodingKeys: String, CodingKey {
        case limit, offset
        case hasMore
        case nextCursor = "next_cursor"
    }
}

/// Envelope from `/api/listings/browse`.
public struct ListingsBrowseResponse: Decodable, Sendable {
    public let listings: [ListingDTO]
    public let pagination: ListingsPagination?
    public let nearestActivityCenter: NearestActivityCenter?

    enum CodingKeys: String, CodingKey {
        case listings, pagination
        case nearestActivityCenter = "nearest_activity_center"
    }
}

/// Envelope from `/api/listings/nearby`.
public struct ListingsNearbyResponse: Decodable, Sendable {
    public let listings: [ListingDTO]
    public let pagination: ListingsPagination?
}

/// Envelope from `/api/listings/in-bounds`.
public struct ListingsInBoundsResponse: Decodable, Sendable {
    public let listings: [ListingDTO]
    public let nearestActivityCenter: NearestActivityCenter?

    enum CodingKeys: String, CodingKey {
        case listings
        case nearestActivityCenter = "nearest_activity_center"
    }
}

/// Backend hint for where to recenter the viewport when the current
/// bbox / radius is empty.
public struct NearestActivityCenter: Decodable, Sendable, Hashable {
    public let latitude: Double?
    public let longitude: Double?
}

/// Envelope from `/api/listings/categories`.
public struct ListingsCategoriesResponse: Decodable, Sendable {
    public let categories: [String]
    public let conditions: [String]
}

/// Save / unsave envelope from `POST /api/listings/:id/save`.
public struct ListingSaveResponse: Decodable, Sendable {
    public let message: String?
    public let saved: Bool?
}

/// Envelope from `GET /api/listings/:id`.
public struct ListingDetailResponse: Decodable, Sendable {
    public let listing: ListingDTO
}

/// Envelope from `POST /api/listings/:id/message`.
public struct MessageListingResponse: Decodable, Sendable {
    public let message: String?
    public let listingMessage: ListingMessageDTO?

    enum CodingKeys: String, CodingKey {
        case message
        case listingMessage = "listing_message"
    }
}

/// One listing-message row.
public struct ListingMessageDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let listingId: String?
    public let buyerId: String?
    public let sellerId: String?
    public let offerAmount: Double?
    public let messageText: String?
    public let status: String?
    public let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case listingId = "listing_id"
        case buyerId = "buyer_id"
        case sellerId = "seller_id"
        case offerAmount = "offer_amount"
        case messageText = "message"
        case status
        case createdAt = "created_at"
    }
}
