//
//  ReviewsEndpoints.swift
//  Pantopus
//
//  Endpoint builders for `backend/routes/reviews.js` — the lightweight
//  review surface invoked from My bids (after the gig is `completed`).
//

import Foundation

/// Endpoints under `/api/reviews/*`.
public enum ReviewsEndpoints {
    /// `POST /api/reviews` — create a review. Backend constraints:
    /// gig must be `completed`, reviewer must be owner or accepted
    /// worker, only one review per gig per reviewer. Route
    /// `backend/routes/reviews.js:35`.
    public static func create(body: CreateReviewBody) -> Endpoint {
        Endpoint(method: .post, path: "/api/reviews", body: body)
    }
}

/// Body for `POST /api/reviews`. Mirrors the backend's required keys
/// (snake_case wire format).
public struct CreateReviewBody: Encodable, Sendable {
    public let gigId: String
    public let revieweeId: String
    public let rating: Int
    public let comment: String?

    public init(gigId: String, revieweeId: String, rating: Int, comment: String? = nil) {
        self.gigId = gigId
        self.revieweeId = revieweeId
        self.rating = rating
        self.comment = comment
    }

    enum CodingKeys: String, CodingKey {
        case gigId = "gig_id"
        case revieweeId = "reviewee_id"
        case rating
        case comment
    }
}
