//
//  AuthEndpoints.swift
//  Pantopus
//

import Foundation

/// Endpoint builders for `backend/routes/users.js` auth routes.
public enum AuthEndpoints {
    /// `POST /api/users/login` — route `backend/routes/users.js:955`.
    public static func login(email: String, password: String) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/users/login",
            body: LoginRequest(email: email, password: password),
            authenticated: false
        )
    }

    /// `POST /api/users/refresh` — route `backend/routes/users.js:1370`.
    public static func refresh(refreshToken: String?) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/users/refresh",
            body: RefreshRequest(refreshToken: refreshToken),
            authenticated: false
        )
    }
}
