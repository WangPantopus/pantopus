//
//  GeoDTOs.swift
//  Pantopus
//
//  DTOs for `/api/geo/*`. Route: `backend/routes/geo.js`.
//

import Foundation

/// Normalized address from reverse geocode / resolve.
public struct NormalizedAddress: Decodable, Sendable, Hashable {
    public let address: String?
    public let city: String?
    public let state: String?
    public let zipcode: String?
    public let latitude: Double?
    public let longitude: Double?
    public let placeId: String?
    public let verified: Bool?
    public let source: String?

    private enum CodingKeys: String, CodingKey {
        case address, city, state, zipcode, latitude, longitude, verified, source
        case placeId = "place_id"
    }

    /// City, state label for compose summaries.
    public var localityLabel: String {
        [city, state].compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: ", ")
    }
}

/// `GET /api/geo/reverse` envelope.
public struct GeoReverseResponse: Decodable, Sendable, Hashable {
    public let normalized: NormalizedAddress
}
