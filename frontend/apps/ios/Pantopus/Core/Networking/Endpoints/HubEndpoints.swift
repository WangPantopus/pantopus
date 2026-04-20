//
//  HubEndpoints.swift
//  Pantopus
//

import Foundation

/// Endpoint builders for `backend/routes/hub.js`.
public enum HubEndpoints {
    /// `GET /api/hub` — route `backend/routes/hub.js:24`. Server sends
    /// `Cache-Control: no-store`, so we defensively disable client cache too.
    public static func overview() -> Endpoint {
        Endpoint(
            method: .get,
            path: "/api/hub",
            cachePolicy: .reloadIgnoringLocalCacheData
        )
    }

    /// `GET /api/hub/today` — route `backend/routes/hub.js:596`.
    public static func today() -> Endpoint {
        Endpoint(method: .get, path: "/api/hub/today")
    }

    /// `GET /api/hub/discovery` — route `backend/routes/hub.js:720`.
    public static func discovery(
        filter: String,
        lat: Double? = nil,
        lng: Double? = nil,
        limit: Int = 10
    ) -> Endpoint {
        var query: [String: String] = ["filter": filter, "limit": String(limit)]
        if let lat { query["lat"] = String(lat) }
        if let lng { query["lng"] = String(lng) }
        return Endpoint(method: .get, path: "/api/hub/discovery", query: query)
    }
}
