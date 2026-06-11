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
    /// excludes the caller's own gigs by default. Besides category +
    /// sort it models price bounds (`minPrice`/`maxPrice`), a single
    /// `schedule_type`, `pay_type` (e.g. "offers"), and a `deadline`
    /// window ("today" | "tomorrow" | "this_week"). Route
    /// `backend/routes/gigs.js:2083`.
    public static func list(
        category: String? = nil,
        sort: String? = nil,
        latitude: Double? = nil,
        longitude: Double? = nil,
        radiusMiles: Double? = nil,
        includeRemote: Bool? = nil,
        minPrice: Double? = nil,
        maxPrice: Double? = nil,
        payType: String? = nil,
        scheduleType: String? = nil,
        deadline: String? = nil,
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
        if let payType { query["pay_type"] = payType }
        if let scheduleType { query["schedule_type"] = scheduleType }
        if let deadline { query["deadline"] = deadline }
        if let search, !search.isEmpty { query["search"] = search }
        return Endpoint(method: .get, path: "/api/gigs", query: query)
    }

    /// `GET /api/gigs/browse` — sectioned browse feed (best matches /
    /// urgent / clusters / high paying / new today / quick jobs) around
    /// `lat`/`lng`. `radius` is **meters** to match the backend
    /// signature. Route `backend/routes/gigs.js:3190`.
    public static func browse(lat: Double, lng: Double, radiusMeters: Int) -> Endpoint {
        Endpoint(
            method: .get,
            path: "/api/gigs/browse",
            query: [
                "lat": String(lat),
                "lng": String(lng),
                "radius": String(radiusMeters)
            ]
        )
    }

    /// `GET /api/gigs/price-benchmark` — low/median/high price benchmark
    /// for a category, optionally geo-scoped. Route
    /// `backend/routes/gigs.js:2985`.
    public static func priceBenchmark(
        category: String,
        lat: Double? = nil,
        lng: Double? = nil
    ) -> Endpoint {
        var query: [String: String] = ["category": category]
        if let lat { query["lat"] = String(lat) }
        if let lng { query["lng"] = String(lng) }
        return Endpoint(method: .get, path: "/api/gigs/price-benchmark", query: query)
    }

    /// `POST /api/gigs/:gigId/dismiss` — "Not interested": hides the gig
    /// from the caller's feed + records an affinity signal. Route
    /// `backend/routes/gigs.js:8523`.
    public static func dismissGig(gigId: String, reason: String? = nil) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/gigs/\(gigId)/dismiss",
            body: DismissGigBody(reason: reason)
        )
    }

    /// `DELETE /api/gigs/:gigId/dismiss` — undo a dismissal. Route
    /// `backend/routes/gigs.js:8572`.
    public static func undoDismissGig(gigId: String) -> Endpoint {
        Endpoint(method: .delete, path: "/api/gigs/\(gigId)/dismiss")
    }

    /// `GET /api/gigs/hidden-categories` — the caller's hidden category
    /// list. Route `backend/routes/gigs.js:3437`.
    public static func hiddenCategories() -> Endpoint {
        Endpoint(method: .get, path: "/api/gigs/hidden-categories")
    }

    /// `POST /api/gigs/hidden-categories` — hide every gig in a category
    /// from the caller's feed. Route `backend/routes/gigs.js:3461`.
    public static func hideCategory(_ category: String) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/gigs/hidden-categories",
            body: HideCategoryBody(category: category)
        )
    }

    /// `DELETE /api/gigs/hidden-categories/:category` — unhide a
    /// category. Route `backend/routes/gigs.js:3495`.
    public static func unhideCategory(_ category: String) -> Endpoint {
        let escaped = category.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? category
        return Endpoint(method: .delete, path: "/api/gigs/hidden-categories/\(escaped)")
    }

    /// `POST /api/gigs/magic-draft` — parse plain-English describe text
    /// into a structured draft (title / category / budget / schedule +
    /// per-field confidence and an optional clarifying question). Route
    /// `backend/routes/magicTask.js:335`.
    public static func magicDraft(body: MagicDraftRequestBody) -> Endpoint {
        Endpoint(method: .post, path: "/api/gigs/magic-draft", body: body)
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

    /// `GET /api/gigs/:gigId/chat-room` — get-or-create the gig chat
    /// room. Pre-bid users are added as participants (message-limited).
    public static func chatRoom(gigId: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/gigs/\(gigId)/chat-room")
    }

    /// `GET /api/gigs/:gigId/questions` — structured Q&A thread (public).
    public static func questions(gigId: String) -> Endpoint {
        Endpoint(method: .get, path: "/api/gigs/\(gigId)/questions")
    }

    /// `POST /api/gigs/:gigId/questions` — ask a question (auth).
    public static func askQuestion(gigId: String, body: AskGigQuestionBody) -> Endpoint {
        Endpoint(method: .post, path: "/api/gigs/\(gigId)/questions", body: body)
    }

    /// `POST /api/gigs/:gigId/questions/:questionId/answer` — poster answers.
    public static func answerQuestion(
        gigId: String,
        questionId: String,
        body: AnswerGigQuestionBody
    ) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/gigs/\(gigId)/questions/\(questionId)/answer",
            body: body
        )
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

    /// `POST /api/gigs/:gigId/bids/:bidId/accept` — poster accepts a bid.
    /// Paid gigs return PaymentSheet params and require `finalizeAcceptBid`
    /// after the sheet completes.
    public static func acceptBid(gigId: String, bidId: String) -> Endpoint {
        Endpoint(method: .post, path: "/api/gigs/\(gigId)/bids/\(bidId)/accept")
    }

    /// `POST /api/gigs/:gigId/bids/:bidId/finalize-accept` — completes a
    /// paid bid acceptance after PaymentSheet authorizes the charge.
    public static func finalizeAcceptBid(gigId: String, bidId: String) -> Endpoint {
        Endpoint(method: .post, path: "/api/gigs/\(gigId)/bids/\(bidId)/finalize-accept")
    }

    /// `POST /api/gigs/:gigId/bids/:bidId/abort-accept` — restores a
    /// pending-payment bid when the sheet is canceled or declined.
    public static func abortAcceptBid(gigId: String, bidId: String) -> Endpoint {
        Endpoint(method: .post, path: "/api/gigs/\(gigId)/bids/\(bidId)/abort-accept")
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
    /// assigned gig as done. Body accepts an optional `note` plus an
    /// optional array of proof-photo `photos` URLs (uploaded first via
    /// `POST /api/files/upload`); the backend stores them as
    /// `completion_photos`. Route `backend/routes/gigs.js:6092`.
    public static func markCompleted(gigId: String, note: String? = nil, photos: [String]? = nil) -> Endpoint {
        Endpoint(
            method: .post,
            path: "/api/gigs/\(gigId)/mark-completed",
            body: MarkCompletedBody(note: note, photos: photos)
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

    /// `POST /api/gigs` — create a new gig. Route
    /// `backend/routes/gigs.js:841`. The full schema accepts ~40 fields;
    /// the Post-a-Task wizard sends the eight required ones plus a
    /// handful of optional Magic Task tags (`schedule_type`, `pay_type`,
    /// `task_format`).
    public static func create(_ body: CreateGigBody) -> Endpoint {
        Endpoint(method: .post, path: "/api/gigs", body: body)
    }
}

/// Body for `POST /api/gigs/:gigId/dismiss`. Reason is optional
/// free-text the backend truncates at 500 chars.
public struct DismissGigBody: Encodable, Sendable {
    public let reason: String?

    public init(reason: String?) {
        self.reason = reason
    }
}

/// Body for `POST /api/gigs/hidden-categories`.
public struct HideCategoryBody: Encodable, Sendable {
    public let category: String

    public init(category: String) {
        self.category = category
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

/// Body for `POST /api/gigs/:gigId/mark-completed`. The backend shape
/// accepts `note`, `photos` (proof-of-delivery URLs), and `checklist`.
/// The Delivery Proof sheet sends `note` + `photos`; `checklist` is
/// omitted (encoded only when non-nil).
public struct MarkCompletedBody: Encodable, Sendable {
    public let note: String?
    public let photos: [String]?

    public init(note: String? = nil, photos: [String]? = nil) {
        self.note = note
        self.photos = photos
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
