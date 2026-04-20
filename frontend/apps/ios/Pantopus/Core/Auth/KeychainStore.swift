//
//  KeychainStore.swift
//  Pantopus
//
//  Thin wrapper around KeychainAccess. Used for access + refresh tokens.
//

import Foundation
import KeychainAccess

/// Protocol for testability — swap in an in-memory implementation in tests.
protocol SecureStore: Sendable {
    func set(_ value: String, for key: String) throws
    func get(_ key: String) -> String?
    func delete(_ key: String) throws
}

struct KeychainStore: SecureStore {
    private let keychain: Keychain

    init(service: String = "app.pantopus.ios") {
        self.keychain = Keychain(service: service)
            .accessibility(.afterFirstUnlockThisDeviceOnly)
            .synchronizable(false)
    }

    func set(_ value: String, for key: String) throws {
        try keychain.set(value, key: key)
    }

    func get(_ key: String) -> String? {
        try? keychain.get(key)
    }

    func delete(_ key: String) throws {
        try keychain.remove(key)
    }
}

enum SecureStoreKey {
    static let accessToken = "accessToken"
    static let refreshToken = "refreshToken"
    static let userId = "userId"
}
