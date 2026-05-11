//
//  PostDetailDTO.swift
//  Pantopus
//
//  DTOs for `GET /api/posts/:id` — route `backend/routes/posts.js:2142`.
//
//  Only one reaction kind exists server-side today (`POST /:id/like`);
//  the design draws three (Helpful/Heart/Going). UI surfaces all three
//  but wires only Helpful — see `PulsePostDetailViewModel`.
//

import Foundation

/// Author / business-author projection on a post.
public struct PostCreatorDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let username: String?
    public let name: String?
    public let firstName: String?
    public let lastName: String?
    public let profilePictureURL: String?
    public let city: String?
    public let state: String?
    public let accountType: String?

    private enum CodingKeys: String, CodingKey {
        case id, username, name
        case firstName = "first_name"
        case lastName = "last_name"
        case profilePictureURL = "profile_picture_url"
        case city, state
        case accountType = "account_type"
    }

    public init(
        id: String,
        username: String?,
        name: String?,
        firstName: String?,
        lastName: String?,
        profilePictureURL: String?,
        city: String?,
        state: String?,
        accountType: String?
    ) {
        self.id = id
        self.username = username
        self.name = name
        self.firstName = firstName
        self.lastName = lastName
        self.profilePictureURL = profilePictureURL
        self.city = city
        self.state = state
        self.accountType = accountType
    }

    /// Best-effort display name across the various populated fields.
    public var displayName: String {
        if let name, !name.isEmpty { return name }
        let combined = [firstName, lastName].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " ")
        if !combined.isEmpty { return combined }
        if let username, !username.isEmpty { return "@\(username)" }
        return "Pantopus user"
    }

    /// Locality summary — "City, ST" if both, else city, else state, else nil.
    public var locality: String? {
        switch (city?.isEmpty, state?.isEmpty) {
        case (false, false) where city != nil && state != nil: "\(city ?? ""), \(state ?? "")"
        case (false, _) where city != nil: city
        case (_, false) where state != nil: state
        default: nil
        }
    }
}

/// Home reference attached to a post.
public struct PostHomeRefDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let address: String?
    public let city: String?
    public let state: String?
}

/// A single comment on a post. Route reference:
/// `backend/routes/posts.js:2157`.
public struct PostCommentDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let postId: String
    public let userId: String
    public let parentCommentId: String?
    public let comment: String
    public let createdAt: String
    public let isDeleted: Bool
    public let userHasLiked: Bool?
    public let likeCount: Int?
    public let author: PostCreatorDTO?

    private enum CodingKeys: String, CodingKey {
        case id
        case postId = "post_id"
        case userId = "user_id"
        case parentCommentId = "parent_comment_id"
        case comment
        case createdAt = "created_at"
        case isDeleted = "is_deleted"
        case userHasLiked
        case likeCount = "like_count"
        case author
    }
}

/// `GET /api/posts/:id` envelope — see `backend/routes/posts.js:2142`.
public struct PostDetailResponse: Decodable, Sendable, Hashable {
    public let post: PostDetailDTO
}

/// `Post` row + creator + comments — the union of columns the detail
/// handler selects. Many columns are optional because list endpoints
/// project different subsets.
public struct PostDetailDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let userId: String
    public let title: String?
    public let content: String
    public let postType: String?
    public let postFormat: String?
    public let purpose: String?
    public let mediaURLs: [String]
    public let mediaTypes: [String]?
    public let mediaLiveURLs: [String]
    public let createdAt: String
    public let updatedAt: String?
    public let isEdited: Bool?
    public let likeCount: Int
    public let commentCount: Int
    public let shareCount: Int?
    public let viewCount: Int?
    public let creator: PostCreatorDTO?
    public let home: PostHomeRefDTO?
    public let userHasLiked: Bool
    public let userHasSaved: Bool
    public let userHasReposted: Bool
    public let comments: [PostCommentDTO]

    private enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case title, content
        case postType = "post_type"
        case postFormat = "post_format"
        case purpose
        case mediaURLs = "media_urls"
        case mediaTypes = "media_types"
        case mediaLiveURLs = "media_live_urls"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
        case isEdited = "is_edited"
        case likeCount = "like_count"
        case commentCount = "comment_count"
        case shareCount = "share_count"
        case viewCount = "view_count"
        case creator, home
        case userHasLiked, userHasSaved, userHasReposted
        case comments
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        userId = try c.decode(String.self, forKey: .userId)
        title = try c.decodeIfPresent(String.self, forKey: .title)
        content = try c.decodeIfPresent(String.self, forKey: .content) ?? ""
        postType = try c.decodeIfPresent(String.self, forKey: .postType)
        postFormat = try c.decodeIfPresent(String.self, forKey: .postFormat)
        purpose = try c.decodeIfPresent(String.self, forKey: .purpose)
        mediaURLs = try (c.decodeIfPresent([String].self, forKey: .mediaURLs)) ?? []
        mediaTypes = try c.decodeIfPresent([String].self, forKey: .mediaTypes)
        mediaLiveURLs = try (c.decodeIfPresent([String].self, forKey: .mediaLiveURLs)) ?? []
        createdAt = try c.decode(String.self, forKey: .createdAt)
        updatedAt = try c.decodeIfPresent(String.self, forKey: .updatedAt)
        isEdited = try c.decodeIfPresent(Bool.self, forKey: .isEdited)
        likeCount = try c.decodeIfPresent(Int.self, forKey: .likeCount) ?? 0
        commentCount = try c.decodeIfPresent(Int.self, forKey: .commentCount) ?? 0
        shareCount = try c.decodeIfPresent(Int.self, forKey: .shareCount)
        viewCount = try c.decodeIfPresent(Int.self, forKey: .viewCount)
        creator = try c.decodeIfPresent(PostCreatorDTO.self, forKey: .creator)
        home = try c.decodeIfPresent(PostHomeRefDTO.self, forKey: .home)
        userHasLiked = try c.decodeIfPresent(Bool.self, forKey: .userHasLiked) ?? false
        userHasSaved = try c.decodeIfPresent(Bool.self, forKey: .userHasSaved) ?? false
        userHasReposted = try c.decodeIfPresent(Bool.self, forKey: .userHasReposted) ?? false
        comments = try (c.decodeIfPresent([PostCommentDTO].self, forKey: .comments)) ?? []
    }
}
