//
//  MyGigsDTOs.swift
//  Pantopus
//
//  Decoder shapes for `GET /api/gigs/my-gigs` and the boost endpoint
//  added in T5.3.2. The list response is a richer projection than the
//  buyer-side `GigsListResponse` — it carries `bid_count`, `top_bidders`
//  (inlined for the BidderStack row primitive), and the optional
//  boost timestamps. Keep this separate from `GigDTO` so the buyer-side
//  decoders don't pay the larger payload.
//

import Foundation

/// One row from `GET /api/gigs/my-gigs`. Mirrors the GIG_LIST projection
/// plus the my-gigs handler's enrichment (bid_count, top_bid_amount,
/// top_bidders) and the boost fields landed alongside this screen.
public struct MyGigDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let title: String
    public let description: String?
    public let price: Double?
    public let category: String?
    public let status: String?
    public let createdAt: String?
    public let updatedAt: String?
    public let deadline: String?
    public let isUrgent: Bool?
    public let userId: String?
    public let acceptedBy: String?
    public let acceptedAt: String?
    public let scheduledStart: String?
    public let payType: String?
    public let bidCount: Int?
    public let topBidAmount: Double?
    public let topBidders: [TopBidderDTO]?
    public let boostedAt: String?
    public let boostExpiresAt: String?

    public init(
        id: String,
        title: String,
        description: String? = nil,
        price: Double? = nil,
        category: String? = nil,
        status: String? = nil,
        createdAt: String? = nil,
        updatedAt: String? = nil,
        deadline: String? = nil,
        isUrgent: Bool? = nil,
        userId: String? = nil,
        acceptedBy: String? = nil,
        acceptedAt: String? = nil,
        scheduledStart: String? = nil,
        payType: String? = nil,
        bidCount: Int? = nil,
        topBidAmount: Double? = nil,
        topBidders: [TopBidderDTO]? = nil,
        boostedAt: String? = nil,
        boostExpiresAt: String? = nil
    ) {
        self.id = id
        self.title = title
        self.description = description
        self.price = price
        self.category = category
        self.status = status
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deadline = deadline
        self.isUrgent = isUrgent
        self.userId = userId
        self.acceptedBy = acceptedBy
        self.acceptedAt = acceptedAt
        self.scheduledStart = scheduledStart
        self.payType = payType
        self.bidCount = bidCount
        self.topBidAmount = topBidAmount
        self.topBidders = topBidders
        self.boostedAt = boostedAt
        self.boostExpiresAt = boostExpiresAt
    }

    enum CodingKeys: String, CodingKey {
        case id, title, description, price, category, status
        case createdAt = "created_at"
        case updatedAt = "updated_at"
        case deadline
        case isUrgent = "is_urgent"
        case userId = "user_id"
        case acceptedBy = "accepted_by"
        case acceptedAt = "accepted_at"
        case scheduledStart = "scheduled_start"
        case payType = "pay_type"
        case bidCount = "bid_count"
        case topBidAmount = "top_bid_amount"
        case topBidders = "top_bidders"
        case boostedAt = "boosted_at"
        case boostExpiresAt = "boost_expires_at"
    }
}

/// Envelope from `GET /api/gigs/my-gigs`.
public struct MyGigsResponse: Decodable, Sendable {
    public let gigs: [MyGigDTO]
    public let total: Int?
}

/// Envelope from `POST /api/gigs/:gigId/boost`.
public struct BoostGigResponse: Decodable, Sendable {
    public let boostExpiresAt: String?

    enum CodingKeys: String, CodingKey {
        case boostExpiresAt = "boost_expires_at"
    }
}

/// Cancellation reasons the backend whitelists for the poster on
/// `POST /api/gigs/:gigId/cancel`.
public enum CancelGigReason: String, Sendable, CaseIterable {
    case changedPlans = "changed_plans"
    case foundSomeoneElse = "found_someone_else"
    case tooExpensive = "too_expensive"
    case emergency
    case other

    public var label: String {
        switch self {
        case .changedPlans: "Changed plans"
        case .foundSomeoneElse: "Found someone else"
        case .tooExpensive: "Too expensive"
        case .emergency: "Emergency"
        case .other: "Other reason"
        }
    }
}

/// Body for `POST /api/gigs/:gigId/cancel`.
public struct CancelGigBody: Encodable, Sendable {
    public let reason: String?

    public init(reason: CancelGigReason?) {
        self.reason = reason?.rawValue
    }
}
