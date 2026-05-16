//
//  AuthRouter.swift
//  Pantopus
//
//  Typed destinations for the signed-out experience. The login surface owns
//  a `NavigationStack(path:)` and dispatches on these cases. P4 and P5 fill
//  in each destination's body.
//

import Foundation

/// Routes within the signed-out auth flow. `Hashable` so the cases can
/// drive a SwiftUI `NavigationPath`.
public enum AuthRoute: Hashable, Sendable {
    /// Default — handled in-place by `LoginView`'s body, not via destination.
    case login
    /// Create account (P4).
    case signUp
    /// Forgot password — request a reset email (P4).
    case forgotPassword
    /// Reset password — landed on via the email deep link with the hashed
    /// recovery `token` (P4).
    case resetPassword(token: String)
    /// Check-your-email confirmation (P5).
    case verifyEmail
    /// Generic auth error / banner detail screen (P4).
    case error(AuthError)
}
