//
//  FeedDTOs.swift
//  Pantopus
//
//  DTOs for `GET /api/posts/feed` — route `backend/routes/posts.js:1449`.
//  Drives the Pulse tab and (later) the Gigs feed surface.
//

import Foundation

/// One row in `/api/posts/feed`. The backend serializer projects every
/// `Post` column the feed needs; this DTO keeps the fields the Pulse UI
/// actually reads.
public struct FeedPostDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let userId: String
    public let title: String?
    public let content: String
    public let postType: String?
    public let createdAt: String
    public let likeCount: Int
    public let commentCount: Int
    public let userHasLiked: Bool
    public let locationName: String?
    public let eventDate: String?
    public let eventVenue: String?
    public let lostFoundType: String?
    public let creator: PostCreatorDTO?

    private enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case title, content
        case postType = "post_type"
        case createdAt = "created_at"
        case likeCount = "like_count"
        case commentCount = "comment_count"
        case userHasLiked
        case locationName = "location_name"
        case eventDate = "event_date"
        case eventVenue = "event_venue"
        case lostFoundType = "lost_found_type"
        case creator
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        userId = try c.decode(String.self, forKey: .userId)
        title = try c.decodeIfPresent(String.self, forKey: .title)
        content = try c.decodeIfPresent(String.self, forKey: .content) ?? ""
        postType = try c.decodeIfPresent(String.self, forKey: .postType)
        createdAt = try c.decode(String.self, forKey: .createdAt)
        likeCount = try c.decodeIfPresent(Int.self, forKey: .likeCount) ?? 0
        commentCount = try c.decodeIfPresent(Int.self, forKey: .commentCount) ?? 0
        userHasLiked = try c.decodeIfPresent(Bool.self, forKey: .userHasLiked) ?? false
        locationName = try c.decodeIfPresent(String.self, forKey: .locationName)
        eventDate = try c.decodeIfPresent(String.self, forKey: .eventDate)
        eventVenue = try c.decodeIfPresent(String.self, forKey: .eventVenue)
        lostFoundType = try c.decodeIfPresent(String.self, forKey: .lostFoundType)
        creator = try c.decodeIfPresent(PostCreatorDTO.self, forKey: .creator)
    }
}

/// Paging envelope returned by `GET /api/posts/feed`.
public struct FeedPagination: Decodable, Sendable, Hashable {
    public let nextCursor: String?
    public let hasMore: Bool?

    private enum CodingKeys: String, CodingKey {
        case nextCursor
        case hasMore
    }
}

/// `GET /api/posts/feed` response envelope.
public struct FeedResponse: Decodable, Sendable, Hashable {
    public let posts: [FeedPostDTO]
    public let pagination: FeedPagination?
}
