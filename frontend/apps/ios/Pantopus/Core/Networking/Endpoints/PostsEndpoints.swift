//
//  PostsEndpoints.swift
//  Pantopus
//
//  Endpoint builders for `backend/routes/posts.js` — feed list, detail,
//  reactions, comments, compose.
//

import Foundation

/// Endpoints under `/api/posts/*`.
public enum PostsEndpoints {
    /// `GET /api/posts/feed` — paged feed for the Pulse tab. The backend
    /// requires `surface` and (for `place`) coordinates. Route
    /// `backend/routes/posts.js:1449`.
    public static func feed(
        surface: String = "place",
        latitude: Double? = nil,
        longitude: Double? = nil,
        radiusMiles: Double? = nil,
        postType: String? = nil,
        limit: Int = 20,
        cursorCreatedAt: String? = nil,
        cursorId: String? = nil
    ) -> Endpoint {
        var query: [String: String] = [
            "surface": surface,
            "limit": String(limit)
        ]
        if let latitude { query["latitude"] = String(latitude) }
        if let longitude { query["longitude"] = String(longitude) }
        if let radiusMiles { query["radiusMiles"] = String(radiusMiles) }
        if let postType { query["postType"] = postType }
        if let cursorCreatedAt { query["cursorCreatedAt"] = cursorCreatedAt }
        if let cursorId { query["cursorId"] = cursorId }
        return Endpoint(method: .get, path: "/api/posts/feed", query: query)
    }

    /// `GET /api/posts/:id` — route `backend/routes/posts.js:2354`.
    public static func detail(id: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/posts/\(id)")
    }

    /// `POST /api/posts/:id/like` — toggles the like; returns the new
    /// `{liked, likeCount}` state. Route `backend/routes/posts.js:2595`.
    public static func toggleLike(id: String) -> Endpoint {
        Endpoint(method: .post, path: "/api/posts/\(id)/like")
    }

    /// `GET /api/posts/:id/comments` — paged. Route
    /// `backend/routes/posts.js:2743`.
    public static func comments(id: String, limit: Int = 50, offset: Int = 0) -> Endpoint {
        Endpoint(
            method: .get,
            path: "/api/posts/\(id)/comments",
            query: ["limit": String(limit), "offset": String(offset)]
        )
    }

    /// `POST /api/posts/:id/comments` — create a new comment. Route
    /// `backend/routes/posts.js:2651`.
    public static func createComment(id: String, body: PostCommentRequest) -> Endpoint {
        Endpoint(method: .post, path: "/api/posts/\(id)/comments", body: body)
    }
}

/// Endpoint builders for public profile fetch. Lives alongside posts
/// because both screens land together in P17 and share a navigation
/// surface.
public enum PublicProfileEndpoints {
    /// `GET /api/users/id/:id` — route `backend/routes/users.js:2041`.
    public static func profile(id: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/users/id/\(id)")
    }
}
