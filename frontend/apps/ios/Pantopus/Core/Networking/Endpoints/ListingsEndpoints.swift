//
//  ListingsEndpoints.swift
//  Pantopus
//
//  Endpoint builders for `backend/routes/listings.js`. T2.4 only wires
//  the in-bounds map mode — full browse / detail land in T2.5.
//

import Foundation

/// Endpoints under `/api/listings/*`.
public enum ListingsEndpoints {
    /// `GET /api/listings/in-bounds` — map-viewport pins. Note the
    /// backend uses `south/west/north/east` (not `min_lat/min_lon/…`)
    /// — see `backend/routes/listings.js:858`.
    public static func inBounds(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        category: String? = nil,
        layer: String? = nil,
        limit: Int = 100
    ) -> Endpoint {
        var query: [String: String] = [
            "south": String(south),
            "west": String(west),
            "north": String(north),
            "east": String(east),
            "limit": String(limit)
        ]
        if let category, category != "all" { query["category"] = category }
        if let layer { query["layer"] = layer }
        return Endpoint(method: .get, path: "/api/listings/in-bounds", query: query)
    }
}
