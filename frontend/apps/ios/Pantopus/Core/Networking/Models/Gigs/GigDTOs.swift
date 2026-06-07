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
    /// Set when the poster confirms completion — gates the Block 3D tip
    /// affordance (the `/tip` route requires a completed + confirmed gig).
    public let ownerConfirmedAt: String?
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
    /// True when the viewer may see exact coordinates (owner or assigned worker).
    public let locationUnlocked: Bool?
    /// Privacy-adjusted coordinates from `GET /api/gigs/:id`.
    public let location: GigCoordinate?
    public let exactCity: String?
    public let exactState: String?
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
        case ownerConfirmedAt = "owner_confirmed_at"
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
        case locationUnlocked
        case location
        case exactCity = "exact_city"
        case exactState = "exact_state"
        case creator
        case legacyCreator = "User"
    }

    public init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        title = try c.decode(String.self, forKey: .title)
        description = try c.decodeIfPresent(String.self, forKey: .description)
        price = try c.decodeIfPresent(Double.self, forKey: .price)
        category = try c.decodeIfPresent(String.self, forKey: .category)
        status = try c.decodeIfPresent(String.self, forKey: .status)
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt)
        deadline = try c.decodeIfPresent(String.self, forKey: .deadline)
        isUrgent = try c.decodeIfPresent(Bool.self, forKey: .isUrgent)
        tags = try c.decodeIfPresent([String].self, forKey: .tags)
        userId = try c.decodeIfPresent(String.self, forKey: .userId)
        acceptedBy = try c.decodeIfPresent(String.self, forKey: .acceptedBy)
        acceptedAt = try c.decodeIfPresent(String.self, forKey: .acceptedAt)
        ownerConfirmedAt = try c.decodeIfPresent(String.self, forKey: .ownerConfirmedAt)
        scheduledStart = try c.decodeIfPresent(String.self, forKey: .scheduledStart)
        paymentStatus = try c.decodeIfPresent(String.self, forKey: .paymentStatus)
        engagementMode = try c.decodeIfPresent(String.self, forKey: .engagementMode)
        scheduleType = try c.decodeIfPresent(String.self, forKey: .scheduleType)
        payType = try c.decodeIfPresent(String.self, forKey: .payType)
        taskArchetype = try c.decodeIfPresent(String.self, forKey: .taskArchetype)
        isV2 = try c.decodeIfPresent(Bool.self, forKey: .isV2)
        pickupAddress = try c.decodeIfPresent(String.self, forKey: .pickupAddress)
        dropoffAddress = try c.decodeIfPresent(String.self, forKey: .dropoffAddress)
        bidCount = try c.decodeIfPresent(Int.self, forKey: .bidCount)
        savedByUser = try c.decodeIfPresent(Bool.self, forKey: .savedByUser)
        distanceMiles = try c.decodeIfPresent(Double.self, forKey: .distanceMiles)
        latitude = try c.decodeIfPresent(Double.self, forKey: .latitude)
        longitude = try c.decodeIfPresent(Double.self, forKey: .longitude)
        approxLocation = try c.decodeIfPresent(GigApproxLocation.self, forKey: .approxLocation)
        locationUnlocked = try c.decodeIfPresent(Bool.self, forKey: .locationUnlocked)
        location = try c.decodeIfPresent(GigCoordinate.self, forKey: .location)
        exactCity = try c.decodeIfPresent(String.self, forKey: .exactCity)
        exactState = try c.decodeIfPresent(String.self, forKey: .exactState)
        creator = try c.decodeIfPresent(GigCreator.self, forKey: .creator)
            ?? c.decodeIfPresent(GigCreator.self, forKey: .legacyCreator)
    }

    public init(
        id: String,
        title: String,
        description: String?,
        price: Double?,
        category: String?,
        status: String?,
        createdAt: String?,
        deadline: String?,
        isUrgent: Bool?,
        tags: [String]?,
        userId: String?,
        acceptedBy: String?,
        acceptedAt: String?,
        ownerConfirmedAt: String?,
        scheduledStart: String?,
        paymentStatus: String?,
        engagementMode: String?,
        scheduleType: String?,
        payType: String?,
        taskArchetype: String?,
        isV2: Bool?,
        pickupAddress: String?,
        dropoffAddress: String?,
        bidCount: Int?,
        savedByUser: Bool?,
        distanceMiles: Double?,
        latitude: Double?,
        longitude: Double?,
        approxLocation: GigApproxLocation?,
        locationUnlocked: Bool?,
        location: GigCoordinate?,
        exactCity: String?,
        exactState: String?,
        creator: GigCreator?
    ) {
        self.id = id
        self.title = title
        self.description = description
        self.price = price
        self.category = category
        self.status = status
        self.createdAt = createdAt
        self.deadline = deadline
        self.isUrgent = isUrgent
        self.tags = tags
        self.userId = userId
        self.acceptedBy = acceptedBy
        self.acceptedAt = acceptedAt
        self.ownerConfirmedAt = ownerConfirmedAt
        self.scheduledStart = scheduledStart
        self.paymentStatus = paymentStatus
        self.engagementMode = engagementMode
        self.scheduleType = scheduleType
        self.payType = payType
        self.taskArchetype = taskArchetype
        self.isV2 = isV2
        self.pickupAddress = pickupAddress
        self.dropoffAddress = dropoffAddress
        self.bidCount = bidCount
        self.savedByUser = savedByUser
        self.distanceMiles = distanceMiles
        self.latitude = latitude
        self.longitude = longitude
        self.approxLocation = approxLocation
        self.locationUnlocked = locationUnlocked
        self.location = location
        self.exactCity = exactCity
        self.exactState = exactState
        self.creator = creator
    }
}

/// Nested `{ latitude, longitude }` on gig detail responses.
public struct GigCoordinate: Decodable, Sendable, Hashable {
    public let latitude: Double?
    public let longitude: Double?
}

/// Privacy-safe coarse location surfaced on map / in-bounds responses.
public struct GigApproxLocation: Decodable, Sendable, Hashable {
    public let latitude: Double?
    public let longitude: Double?
    public let label: String?
}

/// Creator / poster identity on a gig. Detail responses use the identity
/// serializer (`creator.displayName`, `creator.handle`); list joins may
/// still nest the legacy `User` row (`name`, `username`).
public struct GigCreator: Decodable, Sendable, Hashable {
    public let id: String?
    public let username: String?
    public let name: String?
    public let displayName: String?
    public let handle: String?
    public let profilePictureUrl: String?
    public let avatarUrl: String?
    public let verified: Bool?
    public let badges: [String]?

    enum CodingKeys: String, CodingKey {
        case id, username, name, displayName, handle, badges
        case profilePictureUrl = "profile_picture_url"
        case avatarUrl
        case verified
    }

    public init(
        id: String? = nil,
        username: String? = nil,
        name: String? = nil,
        displayName: String? = nil,
        handle: String? = nil,
        profilePictureUrl: String? = nil,
        avatarUrl: String? = nil,
        verified: Bool? = nil,
        badges: [String]? = nil
    ) {
        self.id = id
        self.username = username
        self.name = name
        self.displayName = displayName
        self.handle = handle
        self.profilePictureUrl = profilePictureUrl
        self.avatarUrl = avatarUrl
        self.verified = verified
        self.badges = badges
    }

    /// Best-effort public name across identity-serializer and legacy User shapes.
    public var resolvedDisplayName: String {
        if let displayName, !displayName.isEmpty { return displayName }
        if let name, !name.isEmpty { return name }
        if let handle, !handle.isEmpty { return handle }
        if let username, !username.isEmpty { return username }
        return "Neighbor"
    }

    public var resolvedHandle: String? {
        let value = handle ?? username
        guard let value, !value.isEmpty else { return nil }
        return value
    }

    public var resolvedVerified: Bool {
        verified ?? badges?.contains("verified_resident") ?? false
    }

    public var resolvedAvatarURL: URL? {
        let raw = avatarUrl ?? profilePictureUrl
        guard let raw, !raw.isEmpty else { return nil }
        return URL(string: raw)
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
        case bidder
        case legacyBidder = "User"
    }

    public init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        userId = try c.decodeIfPresent(String.self, forKey: .userId)
        bidAmount = try c.decodeIfPresent(Double.self, forKey: .bidAmount)
        amount = try c.decodeIfPresent(Double.self, forKey: .amount)
        status = try c.decodeIfPresent(String.self, forKey: .status)
        message = try c.decodeIfPresent(String.self, forKey: .message)
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt)
        bidder = try c.decodeIfPresent(GigCreator.self, forKey: .bidder)
            ?? c.decodeIfPresent(GigCreator.self, forKey: .legacyBidder)
    }

    public init(
        id: String,
        userId: String?,
        bidAmount: Double?,
        amount: Double?,
        status: String?,
        message: String?,
        createdAt: String?,
        bidder: GigCreator?
    ) {
        self.id = id
        self.userId = userId
        self.bidAmount = bidAmount
        self.amount = amount
        self.status = status
        self.message = message
        self.createdAt = createdAt
        self.bidder = bidder
    }
}

/// Envelope from `GET /api/gigs/:gigId/bids`.
public struct GigBidsResponse: Decodable, Sendable {
    public let bids: [GigBidDTO]
}

/// Envelope from `GET /api/gigs/:gigId/chat-room` — get-or-create the
/// gig-scoped chat room (pre-bid questions, owner/worker thread).
public struct GigChatRoomResponse: Decodable, Sendable {
    public let roomId: String
    public let topicId: String?
    public let gigOwnerId: String?
}

// MARK: - Structured Q&A

/// User summary nested on a gig question row.
public struct GigQuestionUser: Decodable, Sendable, Hashable {
    public let id: String?
    public let username: String?
    public let firstName: String?
    public let lastName: String?
    public let name: String?
    public let profilePictureUrl: String?

    enum CodingKeys: String, CodingKey {
        case id, username, name
        case firstName = "first_name"
        case lastName = "last_name"
        case profilePictureUrl = "profile_picture_url"
    }
}

/// One row from `GET /api/gigs/:gigId/questions`.
public struct GigQuestionDTO: Decodable, Sendable, Identifiable, Hashable {
    public let id: String
    public let gigId: String
    public let question: String
    public let answer: String?
    public let questionAttachments: [String]?
    public let answerAttachments: [String]?
    public let answeredAt: String?
    public let isPinned: Bool?
    public let upvoteCount: Int?
    public let status: String
    public let createdAt: String?
    public let updatedAt: String?
    public let asker: GigQuestionUser?
    public let answerer: GigQuestionUser?
    public let answererDisplayName: String?

    enum CodingKeys: String, CodingKey {
        case id, question, answer, status, asker, answerer
        case gigId = "gig_id"
        case questionAttachments = "question_attachments"
        case answerAttachments = "answer_attachments"
        case answeredAt = "answered_at"
        case isPinned = "is_pinned"
        case upvoteCount = "upvote_count"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
        case answererDisplayName = "answerer_display_name"
    }

    public var isAnswered: Bool { status == "answered" }
}

/// Envelope from `GET /api/gigs/:gigId/questions`.
public struct GigQuestionsResponse: Decodable, Sendable {
    public let questions: [GigQuestionDTO]
}

/// Envelope from ask/answer/pin mutations.
public struct GigQuestionMutationResponse: Decodable, Sendable {
    public let question: GigQuestionDTO
}

/// Body for `POST /api/gigs/:gigId/questions`.
public struct AskGigQuestionBody: Encodable, Sendable {
    public let question: String
    public let attachments: [String]?

    public init(question: String, attachments: [String]? = nil) {
        self.question = question
        self.attachments = attachments
    }
}

/// Body for `POST /api/gigs/:gigId/questions/:questionId/answer`.
public struct AnswerGigQuestionBody: Encodable, Sendable {
    public let answer: String
    public let attachments: [String]?

    public init(answer: String, attachments: [String]? = nil) {
        self.answer = answer
        self.attachments = attachments
    }
}

/// Envelope from `POST /api/gigs/:gigId/bids`.
public struct PlaceBidResponse: Decodable, Sendable {
    public let bid: GigBidDTO?
    public let message: String?
}

/// Response from `POST /api/gigs/:gigId/bids/:bidId/accept`.
/// Paid gigs return PaymentSheet params and stay in `pending_payment` until
/// `finalize-accept` succeeds; free gigs may return an already accepted bid.
public struct GigBidAcceptResponse: Decodable, Sendable, Hashable {
    public let bid: GigBidDTO?
    public let message: String?
    public let requiresPaymentSetup: Bool?
    public let isSetupIntent: Bool?
    public let payment: PaymentPayload?
    public let publishableKey: String?
    public let clientSecret: String?
    public let paymentId: String?
    public let setupIntentId: String?
    public let paymentIntentId: String?
    public let ephemeralKey: String?
    public let customer: String?
    public let customerId: String?

    public var sheetParams: PaymentIntentSheetParams {
        PaymentIntentSheetParams(
            clientSecret: clientSecret ?? payment?.clientSecret,
            paymentIntentId: paymentIntentId ?? payment?.paymentIntentId,
            customer: customer ?? customerId,
            ephemeralKey: ephemeralKey,
            publishableKey: publishableKey,
            isSetupIntent: isSetupIntent
        )
    }

    public struct PaymentPayload: Decodable, Sendable, Hashable {
        public let clientSecret: String?
        public let paymentId: String?
        public let setupIntentId: String?
        public let paymentIntentId: String?
    }
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
    public let deadline: String?
    public let cancellationPolicy: String?
    public let isUrgent: Bool?
    public let tags: [String]?
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
        deadline: String? = nil,
        cancellationPolicy: String? = nil,
        isUrgent: Bool? = nil,
        tags: [String]? = nil,
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
        self.deadline = deadline
        self.cancellationPolicy = cancellationPolicy
        self.isUrgent = isUrgent
        self.tags = tags
        self.location = location
    }

    enum CodingKeys: String, CodingKey {
        case title, description, category, price
        case payType = "pay_type"
        case scheduleType = "schedule_type"
        case scheduledStart = "scheduled_start"
        case taskFormat = "task_format"
        case attachments
        case deadline
        case cancellationPolicy = "cancellation_policy"
        case isUrgent = "is_urgent"
        case tags
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
