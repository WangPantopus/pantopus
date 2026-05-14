//
//  ChatEndpoints.swift
//  Pantopus
//
//  Endpoint builders for `backend/routes/chats.js`. Mounted at
//  `/api/chat` (see `backend/app.js:325`).
//

import Foundation

/// Endpoints under `/api/chat/*` consumed by the Chat List tab (T2.1).
public enum ChatEndpoints {
    /// `GET /api/chat/unified-conversations` — person-grouped chat list.
    /// Route `backend/routes/chats.js:2211`.
    public static func unifiedConversations(limit: Int = 100) -> Endpoint {
        Endpoint(
            method: .get,
            path: "/api/chat/unified-conversations",
            query: ["limit": String(limit)]
        )
    }

    /// `GET /api/chat/stats` — lightweight badge counts.
    /// Route `backend/routes/chats.js:2140`.
    public static func stats() -> Endpoint {
        Endpoint(method: .get, path: "/api/chat/stats")
    }
}
