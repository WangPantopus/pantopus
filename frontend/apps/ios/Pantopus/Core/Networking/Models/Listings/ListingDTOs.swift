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
    }
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
        case hasMore = "hasMore"
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
