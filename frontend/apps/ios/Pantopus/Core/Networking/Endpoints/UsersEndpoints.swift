//
//  UsersEndpoints.swift
//  Pantopus
//

import Foundation

/// Endpoint builders for `backend/routes/users.js` profile routes.
public enum UsersEndpoints {
    /// `GET /api/users/profile` — route `backend/routes/users.js:1427`.
    public static func profile() -> Endpoint {
        Endpoint(method: .get, path: "/api/users/profile")
    }

    /// `PATCH /api/users/profile` — route `backend/routes/users.js:1503`.
    public static func updateProfile(_ update: ProfileUpdateRequest) -> Endpoint {
        Endpoint(method: .patch, path: "/api/users/profile", body: update)
    }
}
