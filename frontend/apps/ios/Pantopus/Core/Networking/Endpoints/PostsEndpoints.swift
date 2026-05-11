//
//  PostsEndpoints.swift
//  Pantopus
//
//  Endpoint builders for `backend/routes/posts.js` detail / reaction /
//  comment routes. Pulse feed list endpoints are NOT covered here — they
//  land when the feed surface ships.
//

import Foundation

/// Endpoints under `/api/posts/*` consumed by P17.
public enum PostsEndpoints {
    /// `GET /api/posts/:id` — route `backend/routes/posts.js:2142`.
    public static func detail(id: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/posts/\(id)")
    }

    /// `POST /api/posts/:id/like` — toggles the like; returns the new
    /// `{liked, likeCount}` state. Route `backend/routes/posts.js:2375`.
    public static func toggleLike(id: String) -> Endpoint {
        Endpoint(method: .post, path: "/api/posts/\(id)/like")
    }

    /// `GET /api/posts/:id/comments` — paged. Route
    /// `backend/routes/posts.js:2520`.
    public static func comments(id: String, limit: Int = 50, offset: Int = 0) -> Endpoint {
        Endpoint(
            method: .get,
            path: "/api/posts/\(id)/comments",
            query: ["limit": String(limit), "offset": String(offset)]
        )
    }

    /// `POST /api/posts/:id/comments` — create a new comment. Route
    /// `backend/routes/posts.js:2431`.
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
