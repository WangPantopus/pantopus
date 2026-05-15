//
//  SettingsEndpoints.swift
//  Pantopus
//
//  Endpoint builders for the T3.1 Settings surfaces — privacy
//  settings, notification push tokens, auth methods, password,
//  account deletion. Each helper carries a doc-comment with the
//  backend route file + line.
//

import Foundation

/// Endpoints under `/api/privacy/*`.
public enum PrivacyEndpoints {
    /// `GET /api/privacy/settings` — route
    /// `backend/routes/privacy.js:50`.
    public static let settings = Endpoint(method: .get, path: "/api/privacy/settings")

    /// `PATCH /api/privacy/settings` — partial update. Route
    /// `backend/routes/privacy.js:95`.
    public static func updateSettings(_ body: PrivacySettingsUpdate) -> Endpoint {
        Endpoint(method: .patch, path: "/api/privacy/settings", body: body)
    }

    /// `GET /api/privacy/blocks` — list of blocked users for the
    /// "Blocked users" row. Route `backend/routes/privacy.js:154`.
    public static let blocks = Endpoint(method: .get, path: "/api/privacy/blocks")
}

/// Endpoints under `/api/notifications/*`.
public enum NotificationEndpoints {
    /// `POST /api/notifications/push-token` — register a device.
    /// Route `backend/routes/notifications.js:269`.
    public static func registerPushToken(_ body: PushTokenBody) -> Endpoint {
        Endpoint(method: .post, path: "/api/notifications/push-token", body: body)
    }

    /// `DELETE /api/notifications/push-token` — clear the current
    /// device's token. Route `backend/routes/notifications.js:309`.
    public static let deletePushToken = Endpoint(method: .delete, path: "/api/notifications/push-token")
}

/// Endpoints under `/api/users/*` relevant to Settings.
public enum AuthMethodsEndpoints {
    /// `GET /api/users/auth-methods` — what sign-in methods are
    /// connected. Route `backend/routes/users.js:1739`.
    public static let methods = Endpoint(method: .get, path: "/api/users/auth-methods")

    /// `POST /api/users/password` — change the password (rate-limited
    /// by `reauthLimiter`). Route `backend/routes/users.js:1771`.
    public static func updatePassword(_ body: PasswordUpdateBody) -> Endpoint {
        Endpoint(method: .post, path: "/api/users/password", body: body)
    }

    /// `DELETE /api/users/account` — schedule account deletion. Route
    /// `backend/routes/users.js:3945`.
    public static let deleteAccount = Endpoint(method: .delete, path: "/api/users/account")
}

// MARK: - Bodies

/// Partial-update body for `PATCH /api/privacy/settings`. Only set the
/// keys you intend to change; the backend ignores nil keys at the
/// JSON level (each Optional encodes nothing when nil).
public struct PrivacySettingsUpdate: Encodable, Sendable {
    public var searchVisibility: String?
    public var addressPrecision: String?
    public var hideFromSearch: Bool?
    public var showOnlineStatus: Bool?
    public var showLastActive: Bool?
    public var showReadReceipts: Bool?
    public var shareHomeCheckIns: Bool?
    public var pushPreferences: [String: Bool]?
    public var emailPreferences: [String: Bool]?
    public var smsPreferences: [String: Bool]?

    public init(
        searchVisibility: String? = nil,
        addressPrecision: String? = nil,
        hideFromSearch: Bool? = nil,
        showOnlineStatus: Bool? = nil,
        showLastActive: Bool? = nil,
        showReadReceipts: Bool? = nil,
        shareHomeCheckIns: Bool? = nil,
        pushPreferences: [String: Bool]? = nil,
        emailPreferences: [String: Bool]? = nil,
        smsPreferences: [String: Bool]? = nil
    ) {
        self.searchVisibility = searchVisibility
        self.addressPrecision = addressPrecision
        self.hideFromSearch = hideFromSearch
        self.showOnlineStatus = showOnlineStatus
        self.showLastActive = showLastActive
        self.showReadReceipts = showReadReceipts
        self.shareHomeCheckIns = shareHomeCheckIns
        self.pushPreferences = pushPreferences
        self.emailPreferences = emailPreferences
        self.smsPreferences = smsPreferences
    }

    enum CodingKeys: String, CodingKey {
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
    }
}

/// Body for `POST /api/notifications/push-token`.
public struct PushTokenBody: Encodable, Sendable {
    public let token: String
    public let platform: String

    public init(token: String, platform: String = "ios") {
        self.token = token
        self.platform = platform
    }
}

/// Body for `POST /api/users/password` — the backend's
/// `updatePasswordSchema` accepts current + new password.
public struct PasswordUpdateBody: Encodable, Sendable {
    public let currentPassword: String
    public let newPassword: String

    public init(currentPassword: String, newPassword: String) {
        self.currentPassword = currentPassword
        self.newPassword = newPassword
    }

    enum CodingKeys: String, CodingKey {
        case currentPassword = "current_password"
        case newPassword = "new_password"
    }
}
