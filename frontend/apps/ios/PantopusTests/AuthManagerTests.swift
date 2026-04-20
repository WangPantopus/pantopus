//
//  AuthManagerTests.swift
//  PantopusTests
//
//  Exercises AuthManager with an in-memory SecureStore so nothing hits the
//  real Keychain. Uses `InMemorySecureStore` from Support/Fixtures.swift.
//

import XCTest
@testable import Pantopus

@MainActor
final class AuthManagerTests: XCTestCase {

    func testInitialStateIsUnknown() {
        let manager = AuthManager(store: InMemorySecureStore())
        if case .unknown = manager.state { /* pass */ } else {
            XCTFail("Expected .unknown on init, got \(manager.state)")
        }
        XCTAssertNil(manager.accessToken)
    }

    func testSignOutClearsState() async throws {
        let store = InMemorySecureStore()
        try store.set("at_test", for: SecureStoreKey.accessToken)
        try store.set("u_123", for: SecureStoreKey.userId)

        let manager = AuthManager(store: store)
        await manager.signOut()

        if case .signedOut = manager.state { /* pass */ } else {
            XCTFail("Expected .signedOut, got \(manager.state)")
        }
        XCTAssertNil(manager.accessToken)
        XCTAssertNil(store.get(SecureStoreKey.accessToken))
        XCTAssertNil(store.get(SecureStoreKey.userId))
    }

    func testHandleUnauthorizedTransitionsToSignedOut() async {
        let manager = AuthManager(store: InMemorySecureStore())
        await manager.handleUnauthorized()
        if case .signedOut = manager.state { /* pass */ } else {
            XCTFail("Expected .signedOut after 401, got \(manager.state)")
        }
    }
}
