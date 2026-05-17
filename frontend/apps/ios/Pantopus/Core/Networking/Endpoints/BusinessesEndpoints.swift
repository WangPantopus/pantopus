//
//  BusinessesEndpoints.swift
//  Pantopus
//
//  Endpoint builders for `backend/routes/businesses.js`. T6.3f added
//  the `myBusinesses()` helper that backs the My businesses screen.
//  Business discovery (search / nearby) lives in its own namespace
//  under `BusinessDiscoveryEndpoints`.
//

import Foundation

/// Endpoints under `/api/businesses/*`.
public enum BusinessesEndpoints {
    /// `GET /api/businesses/my-businesses` — every business the current
    /// user owns or staffs (via BusinessSeat or legacy BusinessTeam).
    /// Route `backend/routes/businesses.js:682`.
    public static func myBusinesses() -> Endpoint {
        Endpoint(method: .get, path: "/api/businesses/my-businesses")
    }
}
