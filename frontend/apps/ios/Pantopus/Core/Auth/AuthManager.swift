//
//  AuthManager.swift
//  Pantopus
//
//  Holds auth state, persists tokens to the Keychain, and coordinates
//  login / logout / session restore.
//

import Foundation
import Logging

/// Account type chosen at registration. Maps to the backend's
/// `account_type` column. Persisted as `"individual"` (personal) or
/// `"business"` per `registerSchema` at `backend/routes/users.js:723`.
public enum AccountType: String, Sendable, Hashable, CaseIterable {
    case personal
    case business

    /// Value sent to the backend. Personal maps to `"individual"` because
    /// the DB column predates the design's "personal" wording.
    public var backendValue: String {
        switch self {
        case .personal: "individual"
        case .business: "business"
        }
    }
}

/// Typed error surface for the auth flows. Mapping rules:
///
/// - `invalidCredentials` — login 401 (wrong email/password).
/// - `emailAlreadyExists` — register 400 whose error message references
///   "Email already registered" or "already registered".
/// - `weakPassword` — register 400 whose error message references the
///   password length policy (`PASSWORD_MIN_LENGTH`, default 8).
/// - `networkError` — transport-level failure: offline, timeout, DNS.
/// - `rateLimited` — 429 from any auth endpoint (loginLimiter,
///   registerLimiter, forgotPasswordLimiter, etc).
/// - `serverError(String)` — 5xx or otherwise unrecoverable server reply;
///   carries the backend's `error` field for diagnostics.
/// - `unknown` — any other failure (decoding, invalid response, 4xx that
///   doesn't fit the above).
public enum AuthError: Error, LocalizedError, Hashable, Sendable {
    case invalidCredentials
    case emailAlreadyExists
    case weakPassword
    case networkError
    case rateLimited
    case serverError(String)
    case unknown

    public var errorDescription: String? {
        switch self {
        case .invalidCredentials: "Invalid email or password."
        case .emailAlreadyExists: "An account with this email already exists."
        case .weakPassword: "Choose a stronger password."
        case .networkError: "Can't reach Pantopus. Check your connection."
        case .rateLimited: "Too many attempts. Try again in a moment."
        case let .serverError(message): message
        case .unknown: "Something went wrong. Please try again."
        }
    }
}

/// Result of a successful `signUp` call. Carries the created user plus a
/// flag the UI uses to decide whether to show the verify-email banner
/// (per the Q4 soft-gate decision).
public struct SignUpResult: Sendable, Hashable {
    public let user: AuthenticatedUser
    public let requiresEmailVerification: Bool
}

@Observable
@MainActor
final class AuthManager {
    enum State: Equatable {
        case unknown
        case signedOut
        case signedIn(UserDTO)
    }

    static let shared = AuthManager()

    /// Convenience for SwiftUI previews.
    static let previewSignedIn: AuthManager = {
        let manager = AuthManager(store: InMemoryStore())
        manager.state = .signedIn(
            UserDTO(id: "preview", email: "preview@pantopus.app", displayName: "Preview", avatarURL: nil)
        )
        manager.accessToken = "preview-token"
        return manager
    }()

    static let previewSignedOut: AuthManager = {
        let manager = AuthManager(store: InMemoryStore())
        manager.state = .signedOut
        return manager
    }()

    private(set) var state: State = .unknown
    private(set) var accessToken: String?

    private let store: any SecureStore
    private let apiClient: APIClient
    private let logger = Logger(label: "app.pantopus.ios.AuthManager")

    init(
        store: any SecureStore = KeychainStore(),
        apiClient: APIClient = .shared
    ) {
        self.store = store
        self.apiClient = apiClient
    }

    // MARK: - Session restore

    func restoreSession() async {
        if let token = store.get(SecureStoreKey.accessToken) {
            accessToken = token
            // Best-effort hydration of the current user. If this fails with
            // 401, handleUnauthorized will flip us to signedOut.
            do {
                let response: ProfileResponse = try await apiClient.request(UsersEndpoints.profile())
                let user = UserDTO(from: response.user)
                state = .signedIn(user)
                Observability.shared.identify(userId: user.id, email: user.email)
                logger.info("Session restored", metadata: ["userId": .string(user.id)])
            } catch {
                logger.info("Session restore failed, signing out", metadata: ["error": .string("\(error)")])
                await signOut()
            }
        } else {
            state = .signedOut
        }
    }

    // MARK: - Sign in

    func signIn(email: String, password: String) async throws {
        do {
            let response: LoginResponse = try await apiClient.request(
                AuthEndpoints.login(email: email, password: password)
            )
            if let access = response.accessToken {
                try store.set(access, for: SecureStoreKey.accessToken)
                accessToken = access
            }
            if let refresh = response.refreshToken {
                try store.set(refresh, for: SecureStoreKey.refreshToken)
            }
            try store.set(response.user.id, for: SecureStoreKey.userId)

            let user = UserDTO(from: response.user)
            state = .signedIn(user)
            Observability.shared.identify(userId: response.user.id, email: response.user.email)
            Observability.shared.track("auth.signed_in")
            logger.info("Signed in", metadata: ["userId": .string(response.user.id)])
        } catch let apiError as APIError {
            throw Self.mapSignInError(apiError)
        }
    }

    // MARK: - Sign up

    // swiftlint:disable function_parameter_count
    /// `POST /api/users/register` (route `backend/routes/users.js:1177`).
    ///
    /// On 201, returns a `SignUpResult` with the freshly-created user. The
    /// backend always sets `requiresEmailVerification: true` for new
    /// accounts; the verify-email soft-gate banner is driven from that
    /// field per the Q4 decision. Does **not** sign the user in — the
    /// caller pushes to either the verify-email screen or hands off to
    /// `signIn` once the user verifies.
    func signUp(
        email: String,
        password: String,
        phoneNumber: String?,
        username: String,
        firstName: String,
        middleName: String?,
        lastName: String,
        dateOfBirth: Date?,
        address: String?,
        city: String?,
        state: String?,
        zipcode: String?,
        accountType: AccountType,
        inviteCode: String?
    ) async throws -> SignUpResult {
        let body = RegisterRequest(
            email: email,
            password: password,
            phoneNumber: phoneNumber,
            username: username,
            firstName: firstName,
            middleName: middleName,
            lastName: lastName,
            dateOfBirth: dateOfBirth.map(Self.iso8601Date),
            address: address,
            city: city,
            state: state,
            zipcode: zipcode,
            accountType: accountType.backendValue,
            inviteCode: inviteCode
        )

        do {
            let response: RegisterResponse = try await apiClient.request(
                AuthEndpoints.register(body)
            )
            Observability.shared.track("auth.signed_up")
            logger.info("Registered", metadata: ["userId": .string(response.user.id)])
            return SignUpResult(
                user: response.user,
                requiresEmailVerification: response.requiresEmailVerification ?? true
            )
        } catch let apiError as APIError {
            throw Self.mapRegisterError(apiError)
        }
    }

    // swiftlint:enable function_parameter_count

    // MARK: - Forgot / reset password

    /// `POST /api/users/forgot-password` (route `backend/routes/users.js:3197`).
    /// Backend always replies 200 with a generic message to prevent email
    /// enumeration — there's no distinction between "sent" and "no such
    /// account" at this layer.
    func forgotPassword(email: String) async throws {
        do {
            _ = try await apiClient.request(
                AuthEndpoints.forgotPassword(email: email),
                as: AuthMessageResponse.self
            )
            Observability.shared.track("auth.forgot_password_requested")
        } catch let apiError as APIError {
            throw Self.mapGenericAuthError(apiError)
        }
    }

    /// `POST /api/users/reset-password` (route `backend/routes/users.js:3247`).
    /// `token` is the hashed recovery token carried by the email link.
    func resetPassword(token: String, newPassword: String) async throws {
        do {
            _ = try await apiClient.request(
                AuthEndpoints.resetPassword(token: token, newPassword: newPassword),
                as: AuthMessageResponse.self
            )
            Observability.shared.track("auth.password_reset")
        } catch let apiError as APIError {
            throw Self.mapResetPasswordError(apiError)
        }
    }

    // MARK: - Verify email

    /// `POST /api/users/verify-email` (route `backend/routes/users.js:3115`).
    /// Sends the hashed Supabase OTP carried by the verification link.
    /// Backend revokes the just-issued session, so verifying does NOT
    /// sign the user in; the caller routes to login after success.
    func verifyEmail(token: String) async throws {
        do {
            _ = try await apiClient.request(
                AuthEndpoints.verifyEmail(tokenHash: token),
                as: VerifyEmailResponse.self
            )
            Observability.shared.track("auth.email_verified")
        } catch let apiError as APIError {
            throw Self.mapVerifyEmailError(apiError)
        }
    }

    /// `POST /api/users/resend-verification` (route `backend/routes/users.js:3049`).
    /// Like forgot-password, always returns 200 with a generic message.
    func resendVerification(email: String) async throws {
        do {
            _ = try await apiClient.request(
                AuthEndpoints.resendVerification(email: email),
                as: AuthMessageResponse.self
            )
            Observability.shared.track("auth.verification_resent")
        } catch let apiError as APIError {
            throw Self.mapGenericAuthError(apiError)
        }
    }

    // MARK: - Refresh session

    /// `POST /api/users/refresh` (route `backend/routes/users.js:1910`). On
    /// success, persists the new access token and updates `accessToken`.
    /// On failure (incl. TOKEN_REUSE), signs the user out.
    func refreshSession() async throws {
        let stored = store.get(SecureStoreKey.refreshToken)
        do {
            let response: RefreshResponse = try await apiClient.request(
                AuthEndpoints.refresh(refreshToken: stored)
            )
            if let access = response.accessToken {
                try store.set(access, for: SecureStoreKey.accessToken)
                accessToken = access
            }
            if let refresh = response.refreshToken {
                try store.set(refresh, for: SecureStoreKey.refreshToken)
            }
            logger.info("Session refreshed")
        } catch let apiError as APIError {
            logger.warning("Refresh failed, signing out", metadata: ["error": .string("\(apiError)")])
            await signOut()
            throw Self.mapGenericAuthError(apiError)
        }
    }

    // MARK: - Sign out

    func signOut() async {
        try? store.delete(SecureStoreKey.accessToken)
        try? store.delete(SecureStoreKey.refreshToken)
        try? store.delete(SecureStoreKey.userId)
        accessToken = nil
        state = .signedOut
        SocketClient.shared.disconnect()
        Observability.shared.identify(userId: nil)
        Observability.shared.track("auth.signed_out")
    }

    // MARK: - 401 handling

    func handleUnauthorized() async {
        // Extension point: try refresh-token flow here. For now, sign out.
        logger.warning("Handling 401 — signing out")
        await signOut()
    }

    // MARK: - Error mapping

    private static func mapSignInError(_ error: APIError) -> AuthError {
        switch error {
        case .unauthorized: .invalidCredentials
        case let .clientError(status, body): mapByStatus(status: status, body: body)
        case let .server(status, body): .serverError(extractMessage(from: body) ?? "Server error \(status).")
        case .transport: .networkError
        default: .unknown
        }
    }

    private static func mapRegisterError(_ error: APIError) -> AuthError {
        switch error {
        case let .clientError(status, body):
            if status == 429 { return .rateLimited }
            let raw = body ?? ""
            if raw.range(of: "already registered", options: .caseInsensitive) != nil
                || raw.range(of: "Email already", options: .caseInsensitive) != nil {
                return .emailAlreadyExists
            }
            if raw.range(of: "password", options: .caseInsensitive) != nil {
                return .weakPassword
            }
            return .serverError(extractMessage(from: body) ?? raw)
        case let .server(status, body):
            return .serverError(extractMessage(from: body) ?? "Server error \(status).")
        case .transport: return .networkError
        default: return .unknown
        }
    }

    private static func mapResetPasswordError(_ error: APIError) -> AuthError {
        switch error {
        case let .clientError(status, body):
            if status == 429 { return .rateLimited }
            let raw = body ?? ""
            if raw.range(of: "password", options: .caseInsensitive) != nil
                && raw.range(of: "Invalid or expired", options: .caseInsensitive) == nil {
                return .weakPassword
            }
            return .serverError(extractMessage(from: body) ?? raw)
        case let .server(status, body):
            return .serverError(extractMessage(from: body) ?? "Server error \(status).")
        case .transport: return .networkError
        default: return .unknown
        }
    }

    private static func mapVerifyEmailError(_ error: APIError) -> AuthError {
        switch error {
        case let .clientError(status, body):
            if status == 429 { return .rateLimited }
            return .serverError(extractMessage(from: body) ?? body ?? "")
        case let .server(status, body):
            return .serverError(extractMessage(from: body) ?? "Server error \(status).")
        case .transport: return .networkError
        default: return .unknown
        }
    }

    private static func mapGenericAuthError(_ error: APIError) -> AuthError {
        switch error {
        case .unauthorized: .invalidCredentials
        case let .clientError(status, body): mapByStatus(status: status, body: body)
        case let .server(status, body): .serverError(extractMessage(from: body) ?? "Server error \(status).")
        case .transport: .networkError
        default: .unknown
        }
    }

    private static func mapByStatus(status: Int, body: String?) -> AuthError {
        switch status {
        case 429: .rateLimited
        case 401: .invalidCredentials
        default: .serverError(extractMessage(from: body) ?? body ?? "Request failed (\(status)).")
        }
    }

    private static func extractMessage(from body: String?) -> String? {
        guard let body, let data = body.data(using: .utf8) else { return nil }
        let decoded = try? JSONDecoder().decode(AuthErrorBody.self, from: data)
        return decoded?.error
    }

    private static let iso8601DateFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withFullDate]
        return formatter
    }()

    private static func iso8601Date(_ date: Date) -> String {
        iso8601DateFormatter.string(from: date)
    }
}

// MARK: - Preview helper

/// Preview-only in-memory secure store. Marked `@unchecked Sendable` since
/// the underlying dictionary mutation is gated by an `NSLock`.
private final class InMemoryStore: SecureStore, @unchecked Sendable {
    private let lock = NSLock()
    private var storage: [String: String] = [:]
    func set(_ value: String, for key: String) throws {
        lock.lock()
        defer { lock.unlock() }
        storage[key] = value
    }

    func get(_ key: String) -> String? {
        lock.lock()
        defer { lock.unlock() }
        return storage[key]
    }

    func delete(_ key: String) throws {
        lock.lock()
        defer { lock.unlock() }
        storage.removeValue(forKey: key)
    }
}
