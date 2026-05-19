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

    /// `GET /api/businesses/:businessId` — the authenticated detail
    /// fetch. Returns `business + profile + locations + access`. The
    /// `access` block tells the caller whether the viewer owns / staffs
    /// the business; for a non-owning viewer the rest of the response
    /// still renders (this is the public-style read).
    /// Route `backend/routes/businesses.js:912`.
    public static func business(businessId: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/businesses/\(businessId)")
    }

    /// `GET /api/businesses/public/:username` — unauthenticated public
    /// view used to surface hours + catalog + founding badge for a
    /// published business. The viewer only knows the business UUID at
    /// tap time, so the Business Profile screen calls
    /// [business(businessId:)] first to resolve `username`, then folds
    /// this response in for the Overview hours and Services tabs. The
    /// endpoint 404s for unpublished businesses; the VM absorbs that
    /// silently and renders the empty state for the affected tabs.
    /// Route `backend/routes/businesses.js:3277`.
    public static func publicBusiness(username: String) -> Endpoint {
        Endpoint(
            method: .get,
            path: "/api/businesses/public/\(username)",
            authenticated: false
        )
    }
}
