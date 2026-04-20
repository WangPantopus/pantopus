//
//  AuthManagerTests.swift
//  PantopusTests
//
//  Example test using the in-memory SecureStore to avoid touching the real
//  Keychain during tests.
//

import XCTest
@testable import Pantopus

final class InMemorySecureStore: SecureStore {
    private var storage: [String: String] = [:]
    func set(_ value: String, for key: String) throws { storage[key] = value }
    func get(_ key: String) -> String? { storage[key] }
    func delete(_ key: String) throws { storage.removeValue(forKey: key) }
}

@MainActor
final class AuthManagerTests: XCTestCase {
    func testSignOutClearsState() async throws {
        let manager = AuthManager(store: InMemorySecureStore())
        await manager.signOut()
        if case .signedOut = manager.state {
            // pass
        } else {
            XCTFail("Expected .signedOut after signOut()")
        }
        XCTAssertNil(manager.accessToken)
    }
}
