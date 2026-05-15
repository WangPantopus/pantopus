//
//  NotificationsDTOs.swift
//  Pantopus
//
//  Decoder shapes for `/api/notifications/*`.
//

import Foundation

public struct NotificationsListResponse: Decodable, Sendable {
    public let notifications: [NotificationDTO]
    public let unreadCount: Int?
    public let hasMore: Bool?
}

public struct NotificationDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let userId: String?
    public let type: String?
    public let title: String?
    public let body: String?
    public let icon: String?
    /// Backend-emitted deep link path, e.g. `/post/abc-123`,
    /// `/homes/h_1/dashboard`. DeepLinkRouter parses this.
    public let link: String?
    public let isRead: Bool?
    public let createdAt: String?
    public let context: String?

    enum CodingKeys: String, CodingKey {
        case id, type, title, body, icon, link, context
        case userId = "user_id"
        case isRead = "is_read"
        case createdAt = "created_at"
    }
}

public struct NotificationUnreadCountResponse: Decodable, Sendable {
    public let count: Int
}

public struct NotificationActionEcho: Decodable, Sendable {
    public let ok: Bool?
    public let count: Int?
}
