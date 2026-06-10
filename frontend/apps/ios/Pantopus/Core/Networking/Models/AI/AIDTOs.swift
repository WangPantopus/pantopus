//
//  AIDTOs.swift
//  Pantopus
//
//  DTOs for `GET /api/ai/conversations` (`backend/routes/ai.js:358`).
//  The serializer selects `id, title, message_count, last_message_at,
//  created_at, updated_at` from `AIConversation`
//  (`backend/services/ai/agentService.js:1003`).
//

import Foundation

/// One Ask-Pantopus conversation summary. There is no messages payload —
/// the backend keeps no per-message rows for AI threads.
public struct AIConversationSummaryDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let title: String?
    public let messageCount: Int?
    public let lastMessageAt: String?
    public let createdAt: String?
    public let updatedAt: String?

    private enum CodingKeys: String, CodingKey {
        case id, title
        case messageCount = "message_count"
        case lastMessageAt = "last_message_at"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
    }
}

/// `GET /api/ai/conversations` envelope — ordered newest-updated first.
public struct AIConversationsResponse: Decodable, Sendable, Hashable {
    public let conversations: [AIConversationSummaryDTO]
}
