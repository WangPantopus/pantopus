//
//  GigDTOs.swift
//  Pantopus
//
//  Decoder shapes for the `/api/gigs` endpoints. Mirrors the GIG_LIST
//  projection from `backend/routes/gigs.js` — category, price, bid
//  counts, scheduling, geolocation hints, optional creator nesting.
//

import Foundation

/// One row from `GET /api/gigs` / `GET /api/gigs/nearby`.
public struct GigDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let title: String
    public let description: String?
    public let price: Double?
    public let category: String?
    public let status: String?
    public let createdAt: String?
    public let deadline: String?
    public let isUrgent: Bool?
    public let tags: [String]?
    public let userId: String?
    public let acceptedBy: String?
    public let acceptedAt: String?
    public let scheduledStart: String?
    public let paymentStatus: String?
    public let engagementMode: String?
    public let scheduleType: String?
    public let payType: String?
    public let taskArchetype: String?
    public let pickupAddress: String?
    public let dropoffAddress: String?
    public let bidCount: Int?
    public let savedByUser: Bool?
    public let distanceMiles: Double?
    public let latitude: Double?
    public let longitude: Double?
    public let approxLocation: GigApproxLocation?
    public let creator: GigCreator?

    enum CodingKeys: String, CodingKey {
        case id, title, description, price, category, status
        case createdAt = "created_at"
        case deadline
        case isUrgent = "is_urgent"
        case tags
        case userId = "user_id"
        case acceptedBy = "accepted_by"
        case acceptedAt = "accepted_at"
        case scheduledStart = "scheduled_start"
        case paymentStatus = "payment_status"
        case engagementMode = "engagement_mode"
        case scheduleType = "schedule_type"
        case payType = "pay_type"
        case taskArchetype = "task_archetype"
        case pickupAddress = "pickup_address"
        case dropoffAddress = "dropoff_address"
        case bidCount = "bid_count"
        case savedByUser = "saved_by_user"
        case distanceMiles = "distance_miles"
        case latitude
        case longitude
        case approxLocation = "approx_location"
        case creator = "User"
    }
}

/// Privacy-safe coarse location surfaced on map / in-bounds responses.
public struct GigApproxLocation: Decodable, Sendable, Hashable {
    public let latitude: Double?
    public let longitude: Double?
    public let label: String?
}

/// Creator projection from the backend `User` join.
public struct GigCreator: Decodable, Sendable, Hashable {
    public let id: String?
    public let username: String?
    public let name: String?
    public let profilePictureUrl: String?
    public let verified: Bool?

    enum CodingKeys: String, CodingKey {
        case id
        case username
        case name
        case profilePictureUrl = "profile_picture_url"
        case verified
    }
}

/// Top-level envelope from `/api/gigs`.
public struct GigsListResponse: Decodable, Sendable {
    public let gigs: [GigDTO]
    public let total: Int?
    public let radiusMeters: Int?

    enum CodingKeys: String, CodingKey {
        case gigs
        case total
        case radiusMeters = "radiusMeters"
    }
}

/// Save / unsave envelope from `POST /api/gigs/:id/save`.
public struct GigSaveResponse: Decodable, Sendable {
    public let message: String?
    public let saved: Bool?
}
