//
//  SupportTrainsEndpoints.swift
//  Pantopus
//
//  T6.6c (P26.5) — Support trains list endpoints. The full Support
//  Trains backend exposes 39 routes under `backend/routes/supportTrains.js`;
//  this file wires the two list-feed endpoints powering the My-trains
//  and Nearby tabs, plus the helper-reservations feed driving the
//  Review-signups screen.
//

import Foundation

public enum SupportTrainsEndpoints {
    /// User-side feed of Support Trains the caller participates in
    /// (organizer or helper).
    ///
    /// Route: `backend/routes/supportTrains.js:445` —
    /// `GET /api/support-trains/me/support-trains`.
    public static func mine(
        role: SupportTrainRoleFilter? = nil,
        status: String? = nil,
        limit: Int = 20,
        offset: Int = 0
    ) -> Endpoint {
        var query: [String: String] = [
            "limit": String(limit),
            "offset": String(offset)
        ]
        if let role { query["role"] = role.rawValue }
        if let status { query["status"] = status }
        return Endpoint(
            method: .get,
            path: "/api/support-trains/me/support-trains",
            query: query
        )
    }

    /// Nearby Support Trains feed (radius defaults to 25 mi on the
    /// backend; pass an explicit `radiusMeters` to override).
    ///
    /// Route: `backend/routes/supportTrains.js:570` —
    /// `GET /api/support-trains/nearby`.
    public static func nearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double? = nil,
        limit: Int = 40
    ) -> Endpoint {
        var query: [String: String] = [
            "latitude": String(latitude),
            "longitude": String(longitude),
            "limit": String(limit)
        ]
        if let radiusMeters {
            query["radius_meters"] = String(radiusMeters)
        }
        return Endpoint(
            method: .get,
            path: "/api/support-trains/nearby",
            query: query
        )
    }

    /// Organizer-only feed of pending / confirmed helper reservations for
    /// one Support Train. Powers the Review-signups screen.
    ///
    /// Route: `backend/routes/supportTrains.js:3306` —
    /// `GET /api/support-trains/:id/reservations`.
    public static func reservations(supportTrainId: String) -> Endpoint {
        Endpoint(
            method: .get,
            path: "/api/support-trains/\(supportTrainId)/reservations"
        )
    }
}

/// Role filter for the `me/support-trains` feed.
public enum SupportTrainRoleFilter: String, Sendable {
    case organizer
    case helper
}
