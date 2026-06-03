//
//  ProfessionalEndpoints.swift
//  Pantopus
//
//  Endpoint builders for `backend/routes/professional.js` — the
//  Business-pillar professional profile (headline, categories, pricing,
//  verification).
//

import Foundation

public enum ProfessionalEndpoints {
    /// `GET /api/professional/profile/me` — route `professional.js:164`.
    /// Returns `{ profile: … | null }`; null means professional mode is off.
    public static func profileMe() -> Endpoint {
        Endpoint(method: .get, path: "/api/professional/profile/me")
    }

    /// `PATCH /api/professional/profile/me` — route `professional.js:190`.
    /// Partial update; only the safe, unambiguous fields are sent (headline /
    /// bio / public + active flags). `categories` is enum-constrained on the
    /// server, so free-text skills are not written here.
    public static func updateProfileMe(_ body: ProfessionalProfileUpdateRequest) -> Endpoint {
        Endpoint(method: .patch, path: "/api/professional/profile/me", body: body)
    }

    /// `GET /api/professional/verification/status` — route
    /// `professional.js:372`. Tier + status of the verification flow.
    public static func verificationStatus() -> Endpoint {
        Endpoint(method: .get, path: "/api/professional/verification/status")
    }

    /// `GET /api/professional/:username` — route `professional.js:403`. The
    /// public-facing professional profile (user + portfolio + skills +
    /// review stats). Used by the public view, not the self editor.
    public static func publicProfile(username: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/professional/\(username)")
    }
}
