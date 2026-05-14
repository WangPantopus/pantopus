//
//  GigsEndpoints.swift
//  Pantopus
//
//  Endpoint builders for `backend/routes/gigs.js` — list (with category +
//  sort + radius), nearby, in-bounds map mode, save/unsave, detail.
//

import Foundation

/// Endpoints under `/api/gigs/*`.
public enum GigsEndpoints {
    /// `GET /api/gigs` — paginated list with filter + sort. The backend
    /// excludes the caller's own gigs by default. Route
    /// `backend/routes/gigs.js`.
    public static func list(
        category: String? = nil,
        sort: String? = nil,
        latitude: Double? = nil,
        longitude: Double? = nil,
        radiusMiles: Double? = nil,
        includeRemote: Bool? = nil,
        minPrice: Double? = nil,
        maxPrice: Double? = nil,
        search: String? = nil,
        limit: Int = 20,
        offset: Int = 0
    ) -> Endpoint {
        var query: [String: String] = [
            "limit": String(limit),
            "offset": String(offset)
        ]
        if let category, category != "all" { query["category"] = category }
        if let sort { query["sort"] = sort }
        if let latitude { query["latitude"] = String(latitude) }
        if let longitude { query["longitude"] = String(longitude) }
        if let radiusMiles { query["radiusMiles"] = String(radiusMiles) }
        if let includeRemote { query["includeRemote"] = includeRemote ? "true" : "false" }
        if let minPrice { query["minPrice"] = String(minPrice) }
        if let maxPrice { query["maxPrice"] = String(maxPrice) }
        if let search, !search.isEmpty { query["search"] = search }
        return Endpoint(method: .get, path: "/api/gigs", query: query)
    }

    /// `GET /api/gigs/nearby` — radius search around `latitude/longitude`.
    /// `radius` is **meters** to match the backend signature.
    public static func nearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 5_000,
        status: String = "open",
        limit: Int = 20
    ) -> Endpoint {
        Endpoint(
            method: .get,
            path: "/api/gigs/nearby",
            query: [
                "latitude": String(latitude),
                "longitude": String(longitude),
                "radius": String(radiusMeters),
                "status": status,
                "limit": String(limit)
            ]
        )
    }

    /// `GET /api/gigs/in-bounds` — map-viewport pins for T2.4 Map+List.
    public static func inBounds(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
        category: String? = nil,
        includeRemote: Bool? = nil,
        status: String = "open"
    ) -> Endpoint {
        var query: [String: String] = [
            "min_lat": String(minLat),
            "min_lon": String(minLon),
            "max_lat": String(maxLat),
            "max_lon": String(maxLon),
            "status": status
        ]
        if let category, category != "all" { query["category"] = category }
        if let includeRemote { query["includeRemote"] = includeRemote ? "true" : "false" }
        return Endpoint(method: .get, path: "/api/gigs/in-bounds", query: query)
    }

    /// `GET /api/gigs/:id` — single-gig detail wrapper.
    public static func detail(id: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/gigs/\(id)")
    }

    /// `POST /api/gigs/:id/save` — bookmark.
    public static func save(id: String) -> Endpoint {
        Endpoint(method: .post, path: "/api/gigs/\(id)/save")
    }

    /// `DELETE /api/gigs/:id/save` — un-bookmark.
    public static func unsave(id: String) -> Endpoint {
        Endpoint(method: .delete, path: "/api/gigs/\(id)/save")
    }
}
