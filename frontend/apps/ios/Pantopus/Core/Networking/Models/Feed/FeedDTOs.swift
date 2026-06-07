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
    /// Null for cold-start seeded neighborhood facts (`is_seeded`).
    public let userId: String?
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
    public let mediaURLs: [String]
    public let mediaThumbnails: [String]
    public let mediaTypes: [String]
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
        case mediaURLs = "media_urls"
        case mediaThumbnails = "media_thumbnails"
        case mediaTypes = "media_types"
        case creator
    }

    public init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        userId = try c.decodeIfPresent(String.self, forKey: .userId)
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
        mediaURLs = try c.decodeIfPresent([String].self, forKey: .mediaURLs) ?? []
        mediaThumbnails = try c.decodeIfPresent([String].self, forKey: .mediaThumbnails) ?? []
        mediaTypes = try c.decodeIfPresent([String].self, forKey: .mediaTypes) ?? []
        creator = try c.decodeIfPresent(PostCreatorDTO.self, forKey: .creator)
    }
}

/// Paging envelope returned by `GET /api/posts/feed`.
public struct FeedPagination: Decodable, Sendable, Hashable {
    /// Opaque cursor id for the next page. The backend v1.1 feed returns an
    /// object `{ createdAt, id, rankBucket? }`; older stubs used a string.
    public let nextCursor: String?
    public let hasMore: Bool?

    private enum CodingKeys: String, CodingKey {
        case nextCursor
        case hasMore
    }

    private struct CursorObject: Decodable {
        let createdAt: String
        let id: String
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        hasMore = try container.decodeIfPresent(Bool.self, forKey: .hasMore)
        if let cursorString = try? container.decode(String.self, forKey: .nextCursor) {
            nextCursor = cursorString
        } else if let cursorObject = try? container.decode(CursorObject.self, forKey: .nextCursor) {
            nextCursor = cursorObject.id
        } else {
            nextCursor = nil
        }
    }
}

/// `GET /api/posts/feed` response envelope.
public struct FeedResponse: Decodable, Sendable, Hashable {
    public let posts: [FeedPostDTO]
    public let pagination: FeedPagination?
}
