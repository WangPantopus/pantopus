//
//  OffersDTOs.swift
//  Pantopus
//
//  Decoder shapes for `/api/gigs/my-bids` and `/api/gigs/received-offers`.
//  The two responses are similar but not identical — the received-offers
//  payload also inlines the bidder user, and the my-bids payload exposes
//  the counter-offer + withdrawal fields we need to derive the row's
//  status chip.
//

import Foundation

// MARK: - Top-level responses

public struct MyBidsResponse: Decodable, Sendable {
    public let bids: [BidDTO]
    public let total: Int?

    enum CodingKeys: String, CodingKey {
        case bids, total
    }
}

public struct ReceivedOffersResponse: Decodable, Sendable {
    public let offers: [BidDTO]
    public let total: Int?

    enum CodingKeys: String, CodingKey {
        case offers, total
    }
}

// MARK: - Bid (shared shape across both endpoints)

/// One bid as returned by `/api/gigs/my-bids` and `/api/gigs/received-offers`.
/// The Sent endpoint omits `bidder` (the current user is the bidder); the
/// Received endpoint inlines the bidder's identity card. Counter / expiry
/// fields are present on the Sent payload only, but kept optional here so
/// the same DTO works for both tabs.
public struct BidDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let gigId: String?
    public let userId: String?
    public let bidAmount: Double?
    public let message: String?
    public let proposedTime: String?
    public let status: String?
    public let createdAt: String?
    public let updatedAt: String?
    public let expiresAt: String?
    public let counterAmount: Double?
    public let counterStatus: String?
    public let counteredAt: String?
    public let withdrawnAt: String?
    public let gig: BidGigDTO?
    public let bidder: BidderUserDTO?

    enum CodingKeys: String, CodingKey {
        case id, message, status, gig, bidder
        case gigId = "gig_id"
        case userId = "user_id"
        case bidAmount = "bid_amount"
        case proposedTime = "proposed_time"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
        case expiresAt = "expires_at"
        case counterAmount = "counter_amount"
        case counterStatus = "counter_status"
        case counteredAt = "countered_at"
        case withdrawnAt = "withdrawn_at"
    }
}

/// Inlined gig summary on each bid. Mirrors the `Gig` columns the
/// backend selects (`id, title, description, price, category, status,
/// user_id`).
public struct BidGigDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let title: String?
    public let description: String?
    public let price: Double?
    public let category: String?
    public let status: String?
    public let userId: String?

    enum CodingKeys: String, CodingKey {
        case id, title, description, price, category, status
        case userId = "user_id"
    }
}

/// Inlined bidder identity on Received-offers rows. Mirrors the `User`
/// columns the backend selects (`id, username, name, first_name,
/// profile_picture_url, city, state`).
public struct BidderUserDTO: Decodable, Sendable, Hashable, Identifiable {
    public let id: String
    public let username: String?
    public let name: String?
    public let firstName: String?
    public let profilePictureUrl: String?
    public let city: String?
    public let state: String?

    enum CodingKeys: String, CodingKey {
        case id, username, name, city, state
        case firstName = "first_name"
        case profilePictureUrl = "profile_picture_url"
    }
}
