//
//  AuthManager.swift
//  Pantopus
//
//  Holds auth state, persists tokens to the Keychain, and coordinates
//  login / logout / session restore.
//

import Foundation
import Logging

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

    private(set) var state: State = .unknown
    private(set) var accessToken: String?

    private let store: SecureStore
    private let logger = Logger(label: "app.pantopus.ios.AuthManager")

    init(store: SecureStore = KeychainStore()) {
        self.store = store
    }

    // MARK: - Session restore

    func restoreSession() async {
        if let token = store.get(SecureStoreKey.accessToken) {
            self.accessToken = token
            // Best-effort hydration of the current user. If this fails with
            // 401, handleUnauthorized will flip us to signedOut.
            do {
                let response: ProfileResponse = try await APIClient.shared.request(UsersEndpoints.profile())
                let user = UserDTO(from: response.user)
                self.state = .signedIn(user)
                Observability.shared.identify(userId: user.id, email: user.email)
                logger.info("Session restored", metadata: ["userId": .string(user.id)])
            } catch {
                logger.info("Session restore failed, signing out", metadata: ["error": .string("\(error)")])
                await signOut()
            }
        } else {
            self.state = .signedOut
        }
    }

    // MARK: - Sign in

    func signIn(email: String, password: String) async throws {
        let response: LoginResponse = try await APIClient.shared.request(
            AuthEndpoints.login(email: email, password: password)
        )
        if let access = response.accessToken {
            try store.set(access, for: SecureStoreKey.accessToken)
            self.accessToken = access
        }
        if let refresh = response.refreshToken {
            try store.set(refresh, for: SecureStoreKey.refreshToken)
        }
        try store.set(response.user.id, for: SecureStoreKey.userId)

        let user = UserDTO(from: response.user)
        self.state = .signedIn(user)
        Observability.shared.identify(userId: response.user.id, email: response.user.email)
        Observability.shared.track("auth.signed_in")
        logger.info("Signed in", metadata: ["userId": .string(response.user.id)])
    }

    // MARK: - Sign out

    func signOut() async {
        try? store.delete(SecureStoreKey.accessToken)
        try? store.delete(SecureStoreKey.refreshToken)
        try? store.delete(SecureStoreKey.userId)
        self.accessToken = nil
        self.state = .signedOut
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
}

// MARK: - Preview helper

private final class InMemoryStore: SecureStore {
    private var storage: [String: String] = [:]
    func set(_ value: String, for key: String) throws { storage[key] = value }
    func get(_ key: String) -> String? { storage[key] }
    func delete(_ key: String) throws { storage.removeValue(forKey: key) }
}
