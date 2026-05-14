//
//  UsersEndpoints.swift
//  Pantopus
//

import Foundation

/// Endpoint builders for `backend/routes/users.js` profile routes.
public enum UsersEndpoints {
    /// `GET /api/users/profile` — route `backend/routes/users.js:1962`.
    public static func profile() -> Endpoint {
        Endpoint(method: .get, path: "/api/users/profile")
    }

    /// `PATCH /api/users/profile` — route `backend/routes/users.js:2052`.
    public static func updateProfile(_ update: ProfileUpdateRequest) -> Endpoint {
        Endpoint(method: .patch, path: "/api/users/profile", body: update)
    }

    /// `GET /api/users/:id/stats` — route `backend/routes/users.js:2787`.
    /// Returns `{total_gigs_posted, total_gigs_completed, total_earnings,
    /// average_rating, total_ratings}`.
    public static func stats(userId: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/users/\(userId)/stats")
    }
}

/// `GET /api/users/:id/stats` response envelope.
public struct UserStatsDTO: Decodable, Sendable, Hashable {
    public let totalGigsPosted: Int
    public let totalGigsCompleted: Int
    public let totalEarnings: Double
    public let averageRating: Double
    public let totalRatings: Int

    private enum CodingKeys: String, CodingKey {
        case totalGigsPosted = "total_gigs_posted"
        case totalGigsCompleted = "total_gigs_completed"
        case totalEarnings = "total_earnings"
        case averageRating = "average_rating"
        case totalRatings = "total_ratings"
    }
}
