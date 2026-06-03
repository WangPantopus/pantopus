//
//  MembershipEndpoints.swift
//  Pantopus
//
//  A10.8 Membership (fan-side). The fan's own membership to a persona plus
//  the single-tap cancel. Backend keeps the persona / membership names on
//  the wire; the UI renames at the VM boundary. The router is mounted at
//  `/api/personas/:id/membership` (`backend/app.js:367`) and gates `:id` to
//  a UUID.
//

import Foundation

public enum MembershipEndpoints {
    /// `GET /api/personas/:id/membership` — the calling fan's own
    /// membership for `personaId` (UUID). Route
    /// `backend/routes/personaMembership.js:108`.
    public static func membership(personaId: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/personas/\(personaId)/membership")
    }

    /// `POST /api/personas/:id/membership/cancel` — cancel at period end.
    /// No charge: free memberships cancel immediately, paid memberships
    /// flip `cancel_at_period_end` (a non-charging Stripe flag). Upgrade /
    /// downgrade / refund are paid actions deferred to Phase 3. Route
    /// `backend/routes/personaMembership.js:204`.
    public static func cancelMembership(personaId: String) -> Endpoint {
        Endpoint(method: .post, path: "/api/personas/\(personaId)/membership/cancel")
    }
}
