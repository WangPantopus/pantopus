//
//  GeoEndpoints.swift
//  Pantopus
//
//  Endpoint builders for `backend/routes/geo.js`.
//

import Foundation

public enum GeoEndpoints {
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
