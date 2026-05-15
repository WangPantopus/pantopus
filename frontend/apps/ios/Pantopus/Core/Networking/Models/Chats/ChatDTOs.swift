//
//  ChatDTOs.swift
//  Pantopus
//
//  DTOs for `GET /api/chat/unified-conversations`, `/stats`, and the
//  socket payloads `badge:update` and `message:new`.
//
//  The unified-conversations response is a hetero union — each row is
//  either a person-grouped `conversation` (merging direct + gig rooms
//  by other participant) or a `room` (group / home). We decode both
//  variants into a single `UnifiedConversation` value with a `kind`
//  discriminator.
//

import Foundation

// MARK: - Unified conversations

/// One entry in the unified conversations list. The backend tags rows
/// with `_type: "conversation" | "room"`; we map both into one struct
/// so the view can render either with a `ConversationRow`.
public struct UnifiedConversation: Decodable, Sendable, Hashable, Identifiable {
    public enum Kind: String, Sendable, Hashable {
        case conversation, room
    }

    public let kind: Kind
    /// Stable id — for `.conversation` rows, the other participant id;
    /// for `.room` rows, the room id.
    public let id: String
    /// For `.room` rows: `group` | `home`. `nil` for `.conversation`.
    public let roomType: String?
    /// For `.room` rows: associated gig id when the room is gig-typed.
    public let gigId: String?
    /// For `.room` rows: associated home id when the room is home-typed.
    public let homeId: String?
    /// Display name. For `.conversation`, the other participant's name;
    /// for `.room`, the room name.
    public let name: String?
    public let avatarURL: String?
    /// Last-message preview text — may be `nil` for empty rooms.
    public let lastMessagePreview: String?
    /// ISO8601 timestamp of the last message in the conversation, if any.
    public let lastMessageAt: String?
    /// Aggregate unread across all rooms in this conversation.
    public let totalUnread: Int
    /// Topic tags attached to a person-conversation. `topic_type`
    /// values include `gig` and `marketplace`; we project them into
    /// `topicKinds` for filtering.
    public let topicKinds: [String]
    /// Identity disclosure — sourced from the conversation row's
    /// `other_participant_identity.identity_kind` (`personal` /
    /// `home` / `business`). `nil` for `.room` entries.
    public let identityKind: String?
    /// Whether the other participant is verified (for `.conversation`
    /// rows). `nil` for rooms.
    public let isVerified: Bool?

    private enum RootKeys: String, CodingKey {
        case type = "_type"
        // .conversation keys
        case otherParticipantId = "other_participant_id"
        case otherParticipantName = "other_participant_name"
        case otherParticipantAvatar = "other_participant_avatar"
        case otherParticipantIdentity = "other_participant_identity"
        case totalUnread = "total_unread"
        case lastMessageAt = "last_message_at"
        case lastMessagePreview = "last_message_preview"
        case topics
        // .room keys
        case id, roomType = "room_type"
        case roomName = "room_name"
        case gigId = "gig_id"
        case homeId = "home_id"
    }

    private enum TopicKeys: String, CodingKey {
        case topicType = "topic_type"
    }

    private enum IdentityKeys: String, CodingKey {
        case identityKind = "identity_kind"
        case verified
    }

    public init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: RootKeys.self)
        let rawType = try c.decodeIfPresent(String.self, forKey: .type) ?? "conversation"
        kind = Kind(rawValue: rawType) ?? .conversation
        totalUnread = try c.decodeIfPresent(Int.self, forKey: .totalUnread) ?? 0
        lastMessageAt = try c.decodeIfPresent(String.self, forKey: .lastMessageAt)
        lastMessagePreview = try c.decodeIfPresent(String.self, forKey: .lastMessagePreview)

        // Topics → kinds. Each topic object has a `topic_type`.
        if let topics = try? c.decode([[String: JSONValue]].self, forKey: .topics) {
            topicKinds = topics.compactMap { dict in
                if case let .string(value) = dict["topic_type"] ?? .null { return value }
                return nil
            }
        } else {
            topicKinds = []
        }

        switch kind {
        case .conversation:
            id = try c.decode(String.self, forKey: .otherParticipantId)
            name = try c.decodeIfPresent(String.self, forKey: .otherParticipantName)
            avatarURL = try c.decodeIfPresent(String.self, forKey: .otherParticipantAvatar)
            roomType = nil
            gigId = nil
            homeId = nil
            // Identity is a nested object emitted by the local-identity
            // serializer. Decode only the keys we render.
            if let identityContainer = try? c.nestedContainer(
                keyedBy: IdentityKeys.self, forKey: .otherParticipantIdentity
            ) {
                identityKind = try identityContainer.decodeIfPresent(String.self, forKey: .identityKind)
                isVerified = try identityContainer.decodeIfPresent(Bool.self, forKey: .verified)
            } else {
                identityKind = nil
                isVerified = nil
            }
        case .room:
            id = try c.decode(String.self, forKey: .id)
            name = try c.decodeIfPresent(String.self, forKey: .roomName)
            avatarURL = nil
            roomType = try c.decodeIfPresent(String.self, forKey: .roomType)
            gigId = try c.decodeIfPresent(String.self, forKey: .gigId)
            homeId = try c.decodeIfPresent(String.self, forKey: .homeId)
            identityKind = nil
            isVerified = nil
        }
    }
}

/// `GET /api/chat/unified-conversations` envelope.
public struct UnifiedConversationsResponse: Decodable, Sendable, Hashable {
    public let conversations: [UnifiedConversation]
    public let total: Int?
    public let totalUnread: Int?

    private enum CodingKeys: String, CodingKey {
        case conversations, total, totalUnread
    }
}

// MARK: - Stats

/// `GET /api/chat/stats` envelope.
public struct ChatStatsResponse: Decodable, Sendable, Hashable {
    public let stats: Stats

    public struct Stats: Decodable, Sendable, Hashable {
        public let totalUnread: Int
        public let totalChats: Int?
        public let totalMessages: Int?
        public let directChats: Int?
        public let gigChats: Int?
        public let homeChats: Int?

        private enum CodingKeys: String, CodingKey {
            case totalUnread = "total_unread"
            case totalChats = "total_chats"
            case totalMessages = "total_messages"
            case directChats = "direct_chats"
            case gigChats = "gig_chats"
            case homeChats = "home_chats"
        }
    }
}

// MARK: - Socket payloads

// `SocketClient` enables `keyDecodingStrategy = .convertFromSnakeCase`,
// so these payloads omit explicit CodingKeys — property names already
// match the converted form (`total_unread` → `totalUnread`, etc.).

/// `badge:update` socket event payload.
public struct ChatBadgeUpdate: Decodable, Sendable, Hashable {
    public let totalUnread: Int
}

/// `message:new` socket event payload — just the fields the chat list
/// needs to update a row. The full message envelope decoded by the
/// conversation screen is richer; we only consume the room reference +
/// preview here.
public struct ChatMessageEvent: Decodable, Sendable, Hashable {
    public let roomId: String
    public let otherUserId: String?
    public let preview: String?
    public let createdAt: String?
    public let unreadFor: Int?
}

// MARK: - Messages (T2.2)

/// One sender row inline on a `ChatMessageDTO`. Only the fields the
/// conversation view actually reads.
public struct ChatMessageSender: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let username: String?
    public let name: String?
    public let profilePictureURL: String?

    private enum CodingKeys: String, CodingKey {
        case id, username, name
        case profilePictureURL = "profile_picture_url"
    }
}

/// One message row in a chat thread. Backed by the `ChatMessage` row
/// joined with its sender (`user_id` → `sender`).
public struct ChatMessageDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let roomId: String
    public let userId: String?
    public let messageText: String?
    public let messageType: String
    public let metadata: JSONValue?
    public let replyToId: String?
    public let clientMessageId: String?
    public let createdAt: String
    public let editedAt: String?
    public let deletedAt: String?
    public let deliveredAt: String?
    public let readAt: String?
    public let sender: ChatMessageSender?

    private enum CodingKeys: String, CodingKey {
        case id
        case roomId = "room_id"
        case userId = "user_id"
        case messageText = "message_text"
        case messageType = "message_type"
        case metadata
        case replyToId = "reply_to_id"
        case clientMessageId = "client_message_id"
        case createdAt = "created_at"
        case editedAt = "edited_at"
        case deletedAt = "deleted_at"
        case deliveredAt = "delivered_at"
        case readAt = "read_at"
        case sender
    }

    public init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        roomId = try c.decode(String.self, forKey: .roomId)
        userId = try c.decodeIfPresent(String.self, forKey: .userId)
        messageText = try c.decodeIfPresent(String.self, forKey: .messageText)
        messageType = try c.decodeIfPresent(String.self, forKey: .messageType) ?? "text"
        metadata = try c.decodeIfPresent(JSONValue.self, forKey: .metadata)
        replyToId = try c.decodeIfPresent(String.self, forKey: .replyToId)
        clientMessageId = try c.decodeIfPresent(String.self, forKey: .clientMessageId)
        createdAt = try c.decode(String.self, forKey: .createdAt)
        editedAt = try c.decodeIfPresent(String.self, forKey: .editedAt)
        deletedAt = try c.decodeIfPresent(String.self, forKey: .deletedAt)
        deliveredAt = try c.decodeIfPresent(String.self, forKey: .deliveredAt)
        readAt = try c.decodeIfPresent(String.self, forKey: .readAt)
        sender = try c.decodeIfPresent(ChatMessageSender.self, forKey: .sender)
    }
}

/// `GET /api/chat/rooms/:roomId/messages` /
/// `GET /api/chat/conversations/:otherUserId/messages` envelope.
public struct ChatMessagesResponse: Decodable, Sendable, Hashable {
    public let messages: [ChatMessageDTO]
    public let hasMore: Bool?
    public let roomIds: [String]?

    private enum CodingKeys: String, CodingKey {
        case messages, hasMore, roomIds
    }
}

/// `POST /api/chat/messages` envelope.
public struct SendChatMessageResponse: Decodable, Sendable, Hashable {
    public let message: ChatMessageDTO
}

/// `POST /api/chat/messages/:id/react` envelope.
public struct ReactToChatMessageResponse: Decodable, Sendable, Hashable {
    public let messageId: String?
    public let reaction: String?
    public let counts: [String: Int]?
}

// MARK: - Realtime message envelopes

/// `message:new` socket payload at the conversation view-model layer
/// — richer than `ChatMessageEvent` used by the list. The socket
/// decoder converts snake_case → camelCase automatically.
public struct ChatRealtimeMessage: Decodable, Sendable, Hashable {
    public let id: String
    public let roomId: String
    public let userId: String?
    public let messageText: String?
    public let messageType: String?
    public let createdAt: String?
    public let clientMessageId: String?
}

/// `messageUpdated` socket payload.
public struct ChatRealtimeMessageUpdate: Decodable, Sendable, Hashable {
    public let id: String
    public let roomId: String?
    public let messageText: String?
    public let editedAt: String?
    public let deliveredAt: String?
    public let readAt: String?
}

/// `messageDeleted` socket payload.
public struct ChatRealtimeMessageDelete: Decodable, Sendable, Hashable {
    public let id: String
    public let roomId: String?
}

/// `message:react` socket payload.
public struct ChatRealtimeReaction: Decodable, Sendable, Hashable {
    public let messageId: String
    public let reaction: String?
    public let counts: [String: Int]?
}
