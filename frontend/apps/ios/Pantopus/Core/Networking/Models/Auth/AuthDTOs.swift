//
//  AuthDTOs.swift
//  Pantopus
//
//  DTOs for the auth endpoints on `backend/routes/users.js`. See inline
//  route citations for response-shape provenance.
//

import Foundation

/// `POST /api/users/login` — see `backend/routes/users.js:955`.
public struct LoginRequest: Encodable, Sendable {
    /// Account email.
    public let email: String
    /// Plain-text password; transported over TLS only.
    public let password: String

    public init(email: String, password: String) {
        self.email = email
        self.password = password
    }
}

/// Login response body. Tokens are omitted when the server is in
/// cookie-transport mode (header: `x-token-transport: cookie`).
///
/// Route: `backend/routes/users.js:955`.
public struct LoginResponse: Decodable, Sendable, Hashable {
    public let message: String?
    public let accessToken: String?
    public let refreshToken: String?
    /// Seconds until the access token expires.
    public let expiresIn: Int?
    /// Absolute expiry as a Unix epoch in seconds.
    public let expiresAt: Int?
    public let user: AuthenticatedUser

    private enum CodingKeys: String, CodingKey {
        case message
        case accessToken = "accessToken"
        case refreshToken = "refreshToken"
        case expiresIn = "expiresIn"
        case expiresAt = "expiresAt"
        case user
    }
}

/// `POST /api/users/refresh` — see `backend/routes/users.js:1370`.
public struct RefreshRequest: Encodable, Sendable {
    /// Optional: the server can also read the refresh token from the
    /// `pantopus_refresh` cookie.
    public let refreshToken: String?

    public init(refreshToken: String?) {
        self.refreshToken = refreshToken
    }
}

/// Refresh response body. Token fields are omitted in cookie-transport mode.
///
/// Route: `backend/routes/users.js:1370`.
public struct RefreshResponse: Decodable, Sendable, Hashable {
    public let ok: Bool
    public let accessToken: String?
    public let refreshToken: String?
    public let expiresIn: Int?
    public let expiresAt: Int?
}

/// User payload embedded in `LoginResponse`. Mirrors the shape emitted
/// by `sanitizeUserForAuthResponse` in `backend/routes/users.js:955`.
public struct AuthenticatedUser: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let email: String
    public let username: String
    public let name: String
    public let firstName: String
    public let middleName: String?
    public let lastName: String
    public let phoneNumber: String?
    public let address: String?
    public let city: String?
    public let state: String?
    public let zipcode: String?
    public let accountType: String
    public let role: String
    public let verified: Bool
    public let createdAt: String

    private enum CodingKeys: String, CodingKey {
        case id, email, username, name
        case firstName = "firstName"
        case middleName = "middleName"
        case lastName = "lastName"
        case phoneNumber = "phoneNumber"
        case address, city, state, zipcode
        case accountType = "accountType"
        case role, verified
        case createdAt = "createdAt"
    }
}
