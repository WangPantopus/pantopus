//
//  PostCreateRequest.swift
//  Pantopus
//
//  Request + response payloads for `POST /api/posts`. Route:
//  `backend/routes/posts.js:862`. The schema is defined by
//  `createPostSchema` at `backend/routes/posts.js:196-300` — every
//  optional field below maps to one of its keys.
//

import Foundation

/// `POST /api/posts` body. Fields are intentionally optional so the
/// Pulse compose flow can submit just the keys relevant to the chosen
/// intent without sending nulls the backend may reject.
public struct PostCreateRequest: Encodable, Sendable, Hashable {
    public let content: String
    public let title: String?
    public let postType: String
    public let visibility: String
    public let postAs: String
    public let mediaUrls: [String]?
    // Event-specific
    public let eventDate: String?
    public let eventVenue: String?
    // Lost & Found
    public let lostFoundType: String?
    // Recommend
    public let businessName: String?
    // Ask category
    public let serviceCategory: String?
    // Announce audience
    public let audience: String?
    // v1.2 purpose tag (mirrors postType for sortability)
    public let purpose: String?

    public init(
        content: String,
        title: String? = nil,
        postType: String,
        visibility: String,
        postAs: String,
        mediaUrls: [String]? = nil,
        eventDate: String? = nil,
        eventVenue: String? = nil,
        lostFoundType: String? = nil,
        businessName: String? = nil,
        serviceCategory: String? = nil,
        audience: String? = nil,
        purpose: String? = nil
    ) {
        self.content = content
        self.title = title
        self.postType = postType
        self.visibility = visibility
        self.postAs = postAs
        self.mediaUrls = mediaUrls
        self.eventDate = eventDate
        self.eventVenue = eventVenue
        self.lostFoundType = lostFoundType
        self.businessName = businessName
        self.serviceCategory = serviceCategory
        self.audience = audience
        self.purpose = purpose
    }

    private enum CodingKeys: String, CodingKey {
        case content
        case title
        case postType
        case visibility
        case postAs
        case mediaUrls
        case eventDate
        case eventVenue
        case lostFoundType
        case businessName
        case serviceCategory
        case audience
        case purpose
    }

    /// Encode dropping `nil` keys so optional fields aren't sent as
    /// `"foo": null` (which `createPostSchema` rejects for some keys).
    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(content, forKey: .content)
        try container.encodeIfPresent(title, forKey: .title)
        try container.encode(postType, forKey: .postType)
        try container.encode(visibility, forKey: .visibility)
        try container.encode(postAs, forKey: .postAs)
        try container.encodeIfPresent(mediaUrls, forKey: .mediaUrls)
        try container.encodeIfPresent(eventDate, forKey: .eventDate)
        try container.encodeIfPresent(eventVenue, forKey: .eventVenue)
        try container.encodeIfPresent(lostFoundType, forKey: .lostFoundType)
        try container.encodeIfPresent(businessName, forKey: .businessName)
        try container.encodeIfPresent(serviceCategory, forKey: .serviceCategory)
        try container.encodeIfPresent(audience, forKey: .audience)
        try container.encodeIfPresent(purpose, forKey: .purpose)
    }
}

/// `POST /api/posts` response envelope — the backend echoes a
/// thin acknowledgement plus the created row's id.
public struct PostCreateResponse: Decodable, Sendable, Hashable {
    public let message: String?
    public let postId: String?

    private enum CodingKeys: String, CodingKey {
        case message
        case postId = "post_id"
    }

    public init(message: String? = nil, postId: String? = nil) {
        self.message = message
        self.postId = postId
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        message = try container.decodeIfPresent(String.self, forKey: .message)
        // The backend's response shape varies — sometimes `post_id`, sometimes a nested
        // `post.id`. We only need the id when the caller wants to navigate to the new post.
        if let snake = try? container.decodeIfPresent(String.self, forKey: .postId) {
            postId = snake
        } else {
            postId = nil
        }
    }
}
