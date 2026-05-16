//
//  GigsEndpoints.swift
//  Pantopus
//
//  Endpoint builders for `backend/routes/gigs.js` — list (with category +
//  sort + radius), nearby, in-bounds map mode, save/unsave, detail.
//

import Foundation

/// Endpoints under `/api/gigs/*`.
public enum GigsEndpoints {
    /// `GET /api/gigs` — paginated list with filter + sort. The backend
    /// excludes the caller's own gigs by default. Route
    /// `backend/routes/gigs.js`.
    public static func list(
        category: String? = nil,
        sort: String? = nil,
        latitude: Double? = nil,
        longitude: Double? = nil,
        radiusMiles: Double? = nil,
        includeRemote: Bool? = nil,
        minPrice: Double? = nil,
        maxPrice: Double? = nil,
        search: String? = nil,
        limit: Int = 20,
        offset: Int = 0
    ) -> Endpoint {
        var query: [String: String] = [
            "limit": String(limit),
            "offset": String(offset)
        ]
        if let category, category != "all" { query["category"] = category }
        if let sort { query["sort"] = sort }
        if let latitude { query["latitude"] = String(latitude) }
        if let longitude { query["longitude"] = String(longitude) }
        if let radiusMiles { query["radiusMiles"] = String(radiusMiles) }
        if let includeRemote { query["includeRemote"] = includeRemote ? "true" : "false" }
        if let minPrice { query["minPrice"] = String(minPrice) }
        if let maxPrice { query["maxPrice"] = String(maxPrice) }
        if let search, !search.isEmpty { query["search"] = search }
        return Endpoint(method: .get, path: "/api/gigs", query: query)
    }

    /// `GET /api/gigs/nearby` — radius search around `latitude/longitude`.
    /// `radius` is **meters** to match the backend signature.
    public static func nearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 5000,
        status: String = "open",
        limit: Int = 20
    ) -> Endpoint {
        Endpoint(
            method: .get,
            path: "/api/gigs/nearby",
            query: [
                "latitude": String(latitude),
                "longitude": String(longitude),
                "radius": String(radiusMeters),
                "status": status,
                "limit": String(limit)
            ]
        )
    }

    /// `GET /api/gigs/in-bounds` — map-viewport pins for T2.4 Map+List.
    public static func inBounds(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
        category: String? = nil,
        includeRemote: Bool? = nil,
        status: String = "open"
    ) -> Endpoint {
        var query: [String: String] = [
            "min_lat": String(minLat),
            "min_lon": String(minLon),
            "max_lat": String(maxLat),
            "max_lon": String(maxLon),
            "status": status
        ]
        if let category, category != "all" { query["category"] = category }
        if let includeRemote { query["includeRemote"] = includeRemote ? "true" : "false" }
        return Endpoint(method: .get, path: "/api/gigs/in-bounds", query: query)
    }

    /// `GET /api/gigs/:id` — single-gig detail wrapper.
    public static func detail(id: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/gigs/\(id)")
    }

    /// `GET /api/gigs/:gigId/bids` — list bids on a gig. Owner-only —
    /// other callers get 403 and the detail view hides the section.
    public static func bids(gigId: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/gigs/\(gigId)/bids")
    }

    /// `POST /api/gigs/:gigId/bids` — place a bid.
    public static func placeBid(gigId: String, body: PlaceBidBody) -> Endpoint {
        Endpoint(method: .post, path: "/api/gigs/\(gigId)/bids", body: body)
    }

    /// `PUT /api/gigs/:gigId/bids/:bidId` — update a placed bid. Backend
    /// only allows updates while the bid is `pending` or `countered`.
    /// Route `backend/routes/gigs.js:3971`.
    public static func updateBid(gigId: String, bidId: String, body: PlaceBidBody) -> Endpoint {
        Endpoint(method: .put, path: "/api/gigs/\(gigId)/bids/\(bidId)", body: body)
    }

    /// `DELETE /api/gigs/:gigId/bids/:bidId` — withdraw a placed bid.
    /// Body carries a structured `WithdrawBidReason`. Backend records
    /// `withdrawal_reason` + `withdrawn_at` and returns a
    /// `rebid_available_at` timestamp (5 min cooldown). Route
    /// `backend/routes/gigs.js:5245`.
    public static func withdrawBid(gigId: String, bidId: String, reason: WithdrawBidReason?) -> Endpoint {
        Endpoint(
            method: .delete,
            path: "/api/gigs/\(gigId)/bids/\(bidId)",
            body: WithdrawBidBody(reason: reason)
        )
    }

    /// `POST /api/gigs/:gigId/mark-completed` — worker marks an
    /// assigned gig as done. No body is required; we accept an optional
    /// note for the photo/notes flow when a future PR brings it in.
    /// Route `backend/routes/gigs.js:5926`.
    public static func markCompleted(gigId: String, note: String? = nil) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/gigs/\(gigId)/mark-completed",
            body: MarkCompletedBody(note: note)
        )
    }

    /// `POST /api/gigs/:id/save` — bookmark.
    public static func save(id: String) -> Endpoint {
        Endpoint(method: .post, path: "/api/gigs/\(id)/save")
    }

    /// `DELETE /api/gigs/:id/save` — un-bookmark.
    public static func unsave(id: String) -> Endpoint {
        Endpoint(method: .delete, path: "/api/gigs/\(id)/save")
    }

    /// `GET /api/gigs/my-gigs` — list the caller's posted gigs with
    /// inlined bid count, top bid amount, top-3 bidder thumbnails, and
    /// boost timestamps. Route `backend/routes/gigs.js:1169`.
    public static func myGigs(limit: Int = 100, status: String? = nil) -> Endpoint {
        var query: [String: String] = ["limit": String(limit)]
        if let status, !status.isEmpty { query["status"] = status }
        return Endpoint(method: .get, path: "/api/gigs/my-gigs", query: query)
    }

    /// `POST /api/gigs/:gigId/boost` — poster promotes their gig in the
    /// feed for 24h. Route `backend/routes/gigs.js`.
    public static func boostGig(gigId: String) -> Endpoint {
        Endpoint(method: .post, path: "/api/gigs/\(gigId)/boost")
    }

    /// `POST /api/gigs/:gigId/complete` — poster confirms completion.
    /// Alias of `/confirm-completion`. Route
    /// `backend/routes/gigs.js:6170`. Distinct from `markCompleted(...)`
    /// which is the worker-only `/mark-completed` route.
    public static func completeGigAsPoster(gigId: String) -> Endpoint {
        Endpoint(method: .post, path: "/api/gigs/\(gigId)/complete")
    }

    /// `POST /api/gigs/:gigId/cancel` — poster cancels the gig. Body
    /// carries a structured `CancelGigReason`. Route
    /// `backend/routes/gigs.js:6233`.
    public static func cancelGig(gigId: String, reason: CancelGigReason?) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/gigs/\(gigId)/cancel",
            body: CancelGigBody(reason: reason)
        )
    }
}

/// Reasons the backend whitelists for `DELETE /api/gigs/:gigId/bids/:bidId`.
/// Anything else falls through as `null` on the server side.
public enum WithdrawBidReason: String, Sendable, CaseIterable {
    case scheduleConflict = "schedule_conflict"
    case underpriced
    case mistake
    case other

    /// User-facing label rendered in the withdraw sheet.
    public var label: String {
        switch self {
        case .scheduleConflict: "Schedule conflict"
        case .underpriced: "Underpriced my bid"
        case .mistake: "Made a mistake"
        case .other: "Other reason"
        }
    }
}

/// Body for `DELETE /api/gigs/:gigId/bids/:bidId`. Reason is optional —
/// the backend whitelist accepts a small set of values.
public struct WithdrawBidBody: Encodable, Sendable {
    public let reason: String?

    public init(reason: WithdrawBidReason?) {
        self.reason = reason?.rawValue
    }
}

/// Body for `POST /api/gigs/:gigId/mark-completed`. The full backend
/// shape also accepts `photos` and `checklist`; T5.3.1 only sends the
/// optional `note`. Later flows (photo strip) can extend this.
public struct MarkCompletedBody: Encodable, Sendable {
    public let note: String?

    public init(note: String? = nil) {
        self.note = note
    }
}

/// Body for `POST /api/gigs/:gigId/bids`. The backend reads
/// `bid_amount` or `amount` — we send the canonical key.
public struct PlaceBidBody: Encodable, Sendable {
    public let bidAmount: Double
    public let message: String?
    public let proposedTime: String?

    public init(bidAmount: Double, message: String? = nil, proposedTime: String? = nil) {
        self.bidAmount = bidAmount
        self.message = message
        self.proposedTime = proposedTime
    }

    enum CodingKeys: String, CodingKey {
        case bidAmount = "bid_amount"
        case message
        case proposedTime = "proposed_time"
    }
}
