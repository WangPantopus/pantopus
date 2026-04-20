//
//  Fixtures.swift
//  PantopusTests
//
//  Canonical JSON payloads + DTO builders for reuse across tests.
//

import Foundation
@testable import Pantopus

enum Fixtures {
    static let userJSON = """
    {
      "id": "u_123",
      "email": "alice@example.com",
      "display_name": "Alice",
      "avatar_url": null
    }
    """

    static func authJSON(accessToken: String = "at_test", refreshToken: String? = "rt_test") -> String {
        let refresh = refreshToken.map { "\"\($0)\"" } ?? "null"
        return """
        {
          "access_token": "\(accessToken)",
          "refresh_token": \(refresh),
          "user": \(userJSON)
        }
        """
    }

    static let feedJSON = """
    {
      "posts": [
        {
          "id": "p_1",
          "author_id": "u_123",
          "author_name": "Alice",
          "content": "Hello, neighborhood!",
          "created_at": "2026-04-20T10:00:00Z",
          "like_count": 3
        }
      ],
      "next_cursor": null
    }
    """

    static var sampleUser: UserDTO {
        UserDTO(id: "u_123", email: "alice@example.com", displayName: "Alice", avatarURL: nil)
    }
}

final class InMemorySecureStore: SecureStore {
    private var storage: [String: String] = [:]
    func set(_ value: String, for key: String) throws { storage[key] = value }
    func get(_ key: String) -> String? { storage[key] }
    func delete(_ key: String) throws { storage.removeValue(forKey: key) }
}
