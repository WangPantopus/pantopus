//
//  NotificationsEndpoints.swift
//  Pantopus
//
//  T4.1 Notifications center endpoints. Backed by
//  `backend/routes/notifications.js`.
//

import Foundation

public enum NotificationsEndpoints {
    /// `GET /api/notifications?limit=&offset=&unread=` — route
    /// `backend/routes/notifications.js:84`.
    public static func list(
        limit: Int = 20,
        offset: Int = 0,
        unreadOnly: Bool = false
    ) -> Endpoint {
        var query: [String: String] = [
            "limit": String(limit),
            "offset": String(offset)
        ]
        if unreadOnly { query["unread"] = "true" }
        return Endpoint(method: .get, path: "/api/notifications", query: query)
    }

    /// `GET /api/notifications/unread-count` — route
    /// `backend/routes/notifications.js:160`. Drives the bell badge.
    public static let unreadCount = Endpoint(method: .get, path: "/api/notifications/unread-count")

    /// `PATCH /api/notifications/:id/read` — route
    /// `backend/routes/notifications.js:330`. Mark one as read.
    public static func markRead(id: String) -> Endpoint {
        Endpoint(method: .patch, path: "/api/notifications/\(id)/read")
    }

    /// `POST /api/notifications/read-all` — route
    /// `backend/routes/notifications.js:361`. Mark every unread row
    /// as read for the current user.
    public static let markAllRead = Endpoint(method: .post, path: "/api/notifications/read-all")
}
