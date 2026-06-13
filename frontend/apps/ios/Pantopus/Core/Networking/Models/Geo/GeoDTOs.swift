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

/// One address-typeahead suggestion from `GET /api/geo/autocomplete`.
///
/// NOTE the wire shape (verified against the live backend): `center`
/// is a GeoJSON-style `[lng, lat]` ARRAY, not a `{lat, lng}` object
/// (the web `GeoSuggestion` TS type claims an object — the wire wins).
public struct GeoSuggestion: Decodable, Sendable, Hashable, Identifiable {
    public let suggestionId: String
    public let placeId: String?
    public let primaryText: String
    public let secondaryText: String?
    public let label: String
    public let text: String?
    /// GeoJSON order: `[longitude, latitude]`.
    public let center: [Double]
    public let kind: String

    public var id: String { suggestionId }

    public var longitude: Double? { center.count >= 2 ? center[0] : nil }
    public var latitude: Double? { center.count >= 2 ? center[1] : nil }

    private enum CodingKeys: String, CodingKey {
        case label, center, kind, text
        case suggestionId = "suggestion_id"
        case placeId = "place_id"
        case primaryText = "primary_text"
        case secondaryText = "secondary_text"
    }
}

/// `GET /api/geo/autocomplete` envelope.
public struct GeoAutocompleteResponse: Decodable, Sendable, Hashable {
    public let suggestions: [GeoSuggestion]
}

/// `POST /api/geo/resolve` body.
public struct GeoResolveRequest: Encodable, Sendable, Hashable {
    public let suggestionId: String

    public init(suggestionId: String) {
        self.suggestionId = suggestionId
    }

    private enum CodingKeys: String, CodingKey {
        case suggestionId = "suggestion_id"
    }
}

/// `POST /api/geo/resolve` envelope.
public struct GeoResolveResponse: Decodable, Sendable, Hashable {
    public let normalized: NormalizedAddress
}
