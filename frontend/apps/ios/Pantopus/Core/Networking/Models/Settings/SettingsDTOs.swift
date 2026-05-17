//
//  SettingsDTOs.swift
//  Pantopus
//
//  Decoder shapes for the privacy + auth-methods Settings endpoints.
//  All fields are Optional so the backend can roll out new keys
//  without breaking older clients.
//

import Foundation

/// Envelope from `GET /api/privacy/settings`.
public struct PrivacySettingsResponse: Decodable, Sendable {
    public let settings: PrivacySettings
}

/// One privacy-settings row.
public struct PrivacySettings: Decodable, Sendable, Hashable {
    public let userId: String?
    public let searchVisibility: String?
    public let addressPrecision: String?
    public let hideFromSearch: Bool?
    public let showOnlineStatus: Bool?
    public let showLastActive: Bool?
    public let showReadReceipts: Bool?
    public let shareHomeCheckIns: Bool?
    public let pushPreferences: [String: Bool]?
    public let emailPreferences: [String: Bool]?
    public let smsPreferences: [String: Bool]?
    public let updatedAt: String?

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case searchVisibility = "search_visibility"
        case addressPrecision = "address_precision"
        case hideFromSearch = "hide_from_search"
        case showOnlineStatus = "show_online_status"
        case showLastActive = "show_last_active"
        case showReadReceipts = "show_read_receipts"
        case shareHomeCheckIns = "share_home_check_ins"
        case pushPreferences = "push_preferences"
        case emailPreferences = "email_preferences"
        case smsPreferences = "sms_preferences"
        case updatedAt = "updated_at"
    }
}

/// Envelope from `GET /api/privacy/blocks`.
public struct PrivacyBlocksResponse: Decodable, Sendable {
    public let blocks: [PrivacyBlock]
}

public struct PrivacyBlock: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let blockedUserId: String?
    public let blockScope: String?
    public let reason: String?
    public let createdAt: String?
    public let blocked: BlockedUserSummary?

    enum CodingKeys: String, CodingKey {
        case id
        case blockedUserId = "blocked_user_id"
        case blockScope = "block_scope"
        case reason
        case createdAt = "created_at"
        case blocked
    }
}

/// Nested user summary returned by `privacy.js:154` join.
public struct BlockedUserSummary: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let username: String?
    public let name: String?
    public let profilePictureUrl: String?

    enum CodingKeys: String, CodingKey {
        case id, username, name
        case profilePictureUrl = "profile_picture_url"
    }
}

/// Envelope from `GET /api/users/auth-methods`.
public struct AuthMethodsResponse: Decodable, Sendable {
    public let methods: [AuthMethod]?
    public let hasPassword: Bool?
    public let providers: [String]?
    public let twoFactorEnabled: Bool?

    enum CodingKeys: String, CodingKey {
        case methods
        case hasPassword = "has_password"
        case providers
        case twoFactorEnabled = "two_factor_enabled"
    }
}

public struct AuthMethod: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let provider: String?
    public let label: String?

    enum CodingKeys: String, CodingKey {
        case id, provider, label
    }
}
