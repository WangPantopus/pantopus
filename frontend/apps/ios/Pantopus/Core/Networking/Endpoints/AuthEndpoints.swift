//
//  AuthEndpoints.swift
//  Pantopus
//

import Foundation

/// Endpoint builders for `backend/routes/users.js` auth routes.
public enum AuthEndpoints {
    /// `POST /api/users/login` — route `backend/routes/users.js:1492`.
    public static func login(email: String, password: String) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/users/login",
            body: LoginRequest(email: email, password: password),
            authenticated: false
        )
    }

    /// `POST /api/users/register` — route `backend/routes/users.js:1177`.
    public static func register(_ body: RegisterRequest) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/users/register",
            body: body,
            authenticated: false
        )
    }

    /// `POST /api/users/refresh` — route `backend/routes/users.js:1910`.
    public static func refresh(refreshToken: String?) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/users/refresh",
            body: RefreshRequest(refreshToken: refreshToken),
            authenticated: false
        )
    }

    /// `POST /api/users/forgot-password` — route `backend/routes/users.js:3197`.
    public static func forgotPassword(email: String) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/users/forgot-password",
            body: ForgotPasswordRequest(email: email),
            authenticated: false
        )
    }

    /// `POST /api/users/reset-password` — route `backend/routes/users.js:3247`.
    public static func resetPassword(token: String, newPassword: String) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/users/reset-password",
            body: ResetPasswordRequest(token: token, newPassword: newPassword),
            authenticated: false
        )
    }

    /// `POST /api/users/verify-email` — route `backend/routes/users.js:3115`.
    public static func verifyEmail(tokenHash: String, type: String = "signup") -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/users/verify-email",
            body: VerifyEmailRequest(tokenHash: tokenHash, type: type),
            authenticated: false
        )
    }

    /// `POST /api/users/resend-verification` — route `backend/routes/users.js:3049`.
    public static func resendVerification(email: String) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/users/resend-verification",
            body: ResendVerificationRequest(email: email),
            authenticated: false
        )
    }
}
