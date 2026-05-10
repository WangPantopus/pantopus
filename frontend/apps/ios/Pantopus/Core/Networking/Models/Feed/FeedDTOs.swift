//
//  FeedDTOs.swift
//  Pantopus
//
//  DTOs for `/api/posts`. Not part of Prompt P3's scope but retained for
//  the existing `FeedView` screen; migrate to `/api/hub/discovery` when
//  we replace the placeholder feed.
//

import Foundation

/// Pre-existing feed post.
public struct FeedPost: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let authorId: String
    public let authorName: String?
    public let content: String
    public let createdAt: Date
    public let likeCount: Int

    private enum CodingKeys: String, CodingKey {
        case id
        case authorId = "author_id"
        case authorName = "author_name"
        case content
        case createdAt = "created_at"
        case likeCount = "like_count"
    }
}

/// Pre-existing feed response envelope.
public struct FeedResponse: Decodable, Sendable, Hashable {
    public let posts: [FeedPost]
    public let nextCursor: String?

    private enum CodingKeys: String, CodingKey {
        case posts
        case nextCursor = "next_cursor"
    }
}
