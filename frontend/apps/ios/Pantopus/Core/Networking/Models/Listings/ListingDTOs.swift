//
//  ListingDTOs.swift
//  Pantopus
//
//  Minimal decoder shapes for `/api/listings/in-bounds`. T2.4 needs
//  only category + coordinates + title to drop a pin. Full listing
//  shape lands in T2.5.
//

import Foundation

/// One row from `GET /api/listings/in-bounds`.
public struct ListingDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let title: String?
    public let category: String?
    public let layer: String?
    public let price: Double?
    public let createdAt: String?
    public let latitude: Double?
    public let longitude: Double?
    public let approxLocation: ListingApproxLocation?

    enum CodingKeys: String, CodingKey {
        case id, title, category, layer, price
        case createdAt = "created_at"
        case latitude
        case longitude
        case approxLocation = "approx_location"
    }
}

/// Privacy-safe coarse location surfaced on map / in-bounds responses.
public struct ListingApproxLocation: Decodable, Sendable, Hashable {
    public let latitude: Double?
    public let longitude: Double?
    public let label: String?
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
/// bbox is empty.
public struct NearestActivityCenter: Decodable, Sendable, Hashable {
    public let latitude: Double?
    public let longitude: Double?
}
