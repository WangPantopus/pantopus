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
    /// Explicit V2 ("Magic Task") discriminator. When `true` the detail
    /// renders the rich V2 surface (stat strip, Magic Task modules, bid
    /// tags); otherwise it falls back to the sparse V1 legacy layout.
    /// Backend may omit it on legacy gigs — treat `nil` as V1.
    public let isV2: Bool?
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
        case isV2 = "is_v2"
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

/// Bidder thumbnail surfaced on the My tasks V2 row's bidder stack.
/// Initials + tone are derived server-side (gigs.js) so iOS / Android /
/// web all render identical avatars without each platform reinventing
/// the derivation.
public struct TopBidderDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let initials: String
    public let color: String
}

/// Top-level envelope from `/api/gigs`.
public struct GigsListResponse: Decodable, Sendable {
    public let gigs: [GigDTO]
    public let total: Int?
    public let radiusMeters: Int?

    enum CodingKeys: String, CodingKey {
        case gigs
        case total
        case radiusMeters
    }
}

/// Save / unsave envelope from `POST /api/gigs/:id/save`.
public struct GigSaveResponse: Decodable, Sendable {
    public let message: String?
    public let saved: Bool?
}

/// Envelope from `GET /api/gigs/in-bounds`. Carries a backend hint for
/// where to recenter when the current viewport is empty.
public struct GigsInBoundsResponse: Decodable, Sendable {
    public let gigs: [GigDTO]
    public let nearestActivityCenter: NearestActivityCenter?

    enum CodingKeys: String, CodingKey {
        case gigs
        case nearestActivityCenter = "nearest_activity_center"
    }
}

/// Envelope from `GET /api/gigs/:id`.
public struct GigDetailResponse: Decodable, Sendable {
    public let gig: GigDTO
}

/// One bid on a gig.
public struct GigBidDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let userId: String?
    public let bidAmount: Double?
    public let amount: Double?
    public let status: String?
    public let message: String?
    public let createdAt: String?
    public let bidder: GigCreator?

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case bidAmount = "bid_amount"
        case amount
        case status
        case message
        case createdAt = "created_at"
        case bidder = "User"
    }
}

/// Envelope from `GET /api/gigs/:gigId/bids`.
public struct GigBidsResponse: Decodable, Sendable {
    public let bids: [GigBidDTO]
}

/// Envelope from `POST /api/gigs/:gigId/bids`.
public struct PlaceBidResponse: Decodable, Sendable {
    public let bid: GigBidDTO?
    public let message: String?
}

/// Body for `POST /api/gigs`. Mirrors the subset of `createGigSchema`
/// the Post-a-Task wizard surfaces (`backend/routes/gigs.js:425`). All
/// optional fields are omitted from the encoded JSON when nil.
public struct CreateGigBody: Encodable, Sendable, Equatable {
    public let title: String
    public let description: String
    public let category: String?
    public let price: Double
    public let payType: String?
    public let scheduleType: String?
    public let scheduledStart: String?
    public let taskFormat: String?
    public let attachments: [String]?
    public let location: CreateGigLocation

    public init(
        title: String,
        description: String,
        category: String?,
        price: Double,
        payType: String?,
        scheduleType: String?,
        scheduledStart: String?,
        taskFormat: String?,
        attachments: [String]?,
        location: CreateGigLocation
    ) {
        self.title = title
        self.description = description
        self.category = category
        self.price = price
        self.payType = payType
        self.scheduleType = scheduleType
        self.scheduledStart = scheduledStart
        self.taskFormat = taskFormat
        self.attachments = attachments
        self.location = location
    }

    enum CodingKeys: String, CodingKey {
        case title, description, category, price
        case payType = "pay_type"
        case scheduleType = "schedule_type"
        case scheduledStart = "scheduled_start"
        case taskFormat = "task_format"
        case attachments
        case location
    }
}

/// Nested `location` object the backend requires
/// (`backend/routes/gigs.js:521`).
public struct CreateGigLocation: Encodable, Sendable, Equatable {
    public let mode: String
    public let latitude: Double
    public let longitude: Double
    public let address: String
    public let city: String?
    public let state: String?
    public let zip: String?
    public let homeId: String?

    public init(
        mode: String,
        latitude: Double,
        longitude: Double,
        address: String,
        city: String? = nil,
        state: String? = nil,
        zip: String? = nil,
        homeId: String? = nil
    ) {
        self.mode = mode
        self.latitude = latitude
        self.longitude = longitude
        self.address = address
        self.city = city
        self.state = state
        self.zip = zip
        self.homeId = homeId
    }
}

/// Envelope from `POST /api/gigs`. The backend wraps the freshly
/// created gig under `gig`.
public struct CreateGigResponse: Decodable, Sendable {
    public let gig: GigDTO
    public let message: String?
}
