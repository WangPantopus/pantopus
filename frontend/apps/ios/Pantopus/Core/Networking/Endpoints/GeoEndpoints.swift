//
//  GeoEndpoints.swift
//  Pantopus
//
//  Endpoint builders for `backend/routes/geo.js`.
//

import Foundation

public enum GeoEndpoints {
    /// `GET /api/geo/autocomplete?q=` — route `backend/routes/geo.js:39`.
    /// Address typeahead (Mapbox-backed). Suggestions carry a center
    /// lat/lng; resolve one via `resolve(suggestionId:)`.
    public static func autocomplete(query: String) -> Endpoint {
        Endpoint(
            method: .get,
            path: "/api/geo/autocomplete",
            query: ["q": query]
        )
    }

    /// `POST /api/geo/resolve` — route `backend/routes/geo.js:120`.
    /// Resolve a suggestion to a normalized address.
    public static func resolve(suggestionId: String) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/geo/resolve",
            body: GeoResolveRequest(suggestionId: suggestionId)
        )
    }

    /// `GET /api/geo/reverse?lat=&lon=` — route `backend/routes/geo.js:185`.
    public static func reverse(latitude: Double, longitude: Double) -> Endpoint {
        Endpoint(
            method: .get,
            path: "/api/geo/reverse",
            query: [
                "lat": String(latitude),
                "lon": String(longitude)
            ]
        )
    }
}
