//
//  ChatEndpoints.swift
//  Pantopus
//
//  Endpoint builders for `backend/routes/chats.js`. Mounted at
//  `/api/chat` (see `backend/app.js:325`).
//

import Foundation

/// Endpoints under `/api/chat/*` consumed by the Chat List + Chat
/// Conversation screens (T2.1 / T2.2).
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

    /// `GET /api/chat/rooms/:roomId/messages` — paginated room thread.
    /// Route `backend/routes/chats.js:1340`.
    public static func roomMessages(
        roomId: String,
        limit: Int = 60,
        before: String? = nil,
        after: String? = nil
    ) -> Endpoint {
        var query: [String: String] = ["limit": String(limit)]
        if let before { query["before"] = before }
        if let after { query["after"] = after }
        return Endpoint(method: .get, path: "/api/chat/rooms/\(roomId)/messages", query: query)
    }

    /// `GET /api/chat/conversations/:otherUserId/messages` — paginated
    /// person-grouped thread. Route `backend/routes/chats.js:1157`.
    public static func conversationMessages(
        otherUserId: String,
        limit: Int = 60,
        before: String? = nil,
        after: String? = nil
    ) -> Endpoint {
        var query: [String: String] = ["limit": String(limit)]
        if let before { query["before"] = before }
        if let after { query["after"] = after }
        return Endpoint(method: .get, path: "/api/chat/conversations/\(otherUserId)/messages", query: query)
    }

    /// `POST /api/chat/messages` — send a message. Route
    /// `backend/routes/chats.js:1438`. `clientMessageId` is the
    /// optimistic id the mobile client minted; the server echoes it
    /// back so the UI can swap the temp row for the persisted one.
    public static func sendMessage(body: SendChatMessageBody) -> Endpoint {
        Endpoint(method: .post, path: "/api/chat/messages", body: body)
    }

    /// `POST /api/chat/messages/:id/react` — toggle a single-character
    /// reaction on a message. Route `backend/routes/chats.js:2558`.
    public static func reactToMessage(id: String, reaction: String) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/chat/messages/\(id)/react",
            body: ReactToChatMessageBody(reaction: reaction)
        )
    }

    /// `POST /api/chat/rooms/:roomId/read` — mark every message in the
    /// room as read for the current user. Route
    /// `backend/routes/chats.js:1953`.
    public static func markRoomRead(roomId: String) -> Endpoint {
        Endpoint(method: .post, path: "/api/chat/rooms/\(roomId)/read")
    }

    /// `POST /api/chat/conversations/:otherUserId/read` — mark every
    /// shared-room message read for a person-grouped thread. Route
    /// `backend/routes/chats.js:1265`.
    public static func markConversationRead(otherUserId: String) -> Endpoint {
        Endpoint(method: .post, path: "/api/chat/conversations/\(otherUserId)/read")
    }
}

/// `POST /api/chat/messages` body. Matches the Joi validator at
/// `backend/routes/chats.js:146`.
public struct SendChatMessageBody: Encodable, Sendable {
    public let roomId: String
    public let messageText: String?
    public let messageType: String
    public let clientMessageId: String?
    public let replyToId: String?

    public init(
        roomId: String,
        messageText: String?,
        messageType: String = "text",
        clientMessageId: String? = nil,
        replyToId: String? = nil
    ) {
        self.roomId = roomId
        self.messageText = messageText
        self.messageType = messageType
        self.clientMessageId = clientMessageId
        self.replyToId = replyToId
    }
}

/// `POST /api/chat/messages/:id/react` body.
public struct ReactToChatMessageBody: Encodable, Sendable {
    public let reaction: String

    public init(reaction: String) {
        self.reaction = reaction
    }
}
