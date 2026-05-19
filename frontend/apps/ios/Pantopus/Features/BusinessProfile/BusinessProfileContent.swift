//
//  BusinessProfileContent.swift
//  Pantopus
//
//  P1.6 — Render-only models for the typed Business Profile screen.
//  The view-model projects `BusinessDetailResponse` (+ optional
//  `BusinessPublicResponse` + `PublicProfile`) onto these so the view
//  layer never touches DTOs directly.
//

import Foundation

/// Tabs surfaced on the Business Profile detail.
public enum BusinessProfileTab: String, Sendable, CaseIterable, Identifiable {
    case overview
    case services
    case reviews

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .overview: "Overview"
        case .services: "Services"
        case .reviews: "Reviews"
        }
    }
}

/// Hero-band content — what renders inside the violet identity band
/// before the body scrolls.
public struct BusinessProfileHeader: Sendable, Hashable {
    public let displayName: String
    public let handle: String?
    public let locality: String?
    public let logoURL: URL?
    public let isVerified: Bool
    public let categoryChips: [String]

    public init(
        displayName: String,
        handle: String?,
        locality: String?,
        logoURL: URL?,
        isVerified: Bool,
        categoryChips: [String]
    ) {
        self.displayName = displayName
        self.handle = handle
        self.locality = locality
        self.logoURL = logoURL
        self.isVerified = isVerified
        self.categoryChips = categoryChips
    }
}

/// One cell in the raised stats strip — Followers · Reviews · Years.
public struct BusinessStatCell: Sendable, Hashable, Identifiable {
    public let id: String
    public let value: String
    public let label: String

    public init(id: String, value: String, label: String) {
        self.id = id
        self.value = value
        self.label = label
    }
}

/// One review row.
public struct BusinessReviewCard: Sendable, Hashable, Identifiable {
    public let id: String
    public let reviewerName: String
    public let reviewerAvatarURL: URL?
    public let rating: Int
    public let body: String
    public let timestamp: String

    public init(
        id: String,
        reviewerName: String,
        reviewerAvatarURL: URL?,
        rating: Int,
        body: String,
        timestamp: String
    ) {
        self.id = id
        self.reviewerName = reviewerName
        self.reviewerAvatarURL = reviewerAvatarURL
        self.rating = max(0, min(5, rating))
        self.body = body
        self.timestamp = timestamp
    }
}

/// One Services-tab service offering.
public struct BusinessServiceRow: Sendable, Hashable, Identifiable {
    public let id: String
    public let name: String
    public let detail: String?
    public let priceLabel: String

    public init(id: String, name: String, detail: String?, priceLabel: String) {
        self.id = id
        self.name = name
        self.detail = detail
        self.priceLabel = priceLabel
    }
}

/// One Overview-tab hours row.
public struct BusinessHoursRow: Sendable, Hashable, Identifiable {
    public let id: String
    public let dayLabel: String
    public let timeLabel: String
    public let isClosed: Bool

    public init(id: String, dayLabel: String, timeLabel: String, isClosed: Bool) {
        self.id = id
        self.dayLabel = dayLabel
        self.timeLabel = timeLabel
        self.isClosed = isClosed
    }
}

/// Address + map preview marker for the Overview tab.
public struct BusinessAddress: Sendable, Hashable {
    public let lines: [String]
    public let latitude: Double?
    public let longitude: Double?

    public init(lines: [String], latitude: Double?, longitude: Double?) {
        self.lines = lines
        self.latitude = latitude
        self.longitude = longitude
    }

    public var hasCoordinates: Bool {
        latitude != nil && longitude != nil
    }
}

/// Contact-info rows on Overview (phone / email / website).
public struct BusinessContactRow: Sendable, Hashable, Identifiable {
    public enum Kind: String, Sendable, Hashable {
        case phone, email, website
    }

    public let id: String
    public let kind: Kind
    public let value: String
    public let actionURL: URL?

    public init(id: String, kind: Kind, value: String, actionURL: URL?) {
        self.id = id
        self.kind = kind
        self.value = value
        self.actionURL = actionURL
    }
}

/// Top-level content payload emitted by `BusinessProfileViewModel`.
public struct BusinessProfileContent: Sendable, Hashable {
    public let businessId: String
    public let header: BusinessProfileHeader
    public let stats: [BusinessStatCell]
    public let about: String?
    public let hours: [BusinessHoursRow]
    public let address: BusinessAddress?
    public let contact: [BusinessContactRow]
    public let services: [BusinessServiceRow]
    public let reviews: [BusinessReviewCard]
    /// Used by the action footer: when the business has a website, we
    /// render the "Visit" link button.
    public let websiteURL: URL?
    /// Surfaced so the host can switch the action footer (Edit / Manage)
    /// when the viewer owns the business. P1.6 doesn't wire that yet —
    /// the field is kept so the VM doesn't drop the access info.
    public let viewerIsOwner: Bool

    public init(
        businessId: String,
        header: BusinessProfileHeader,
        stats: [BusinessStatCell],
        about: String?,
        hours: [BusinessHoursRow],
        address: BusinessAddress?,
        contact: [BusinessContactRow],
        services: [BusinessServiceRow],
        reviews: [BusinessReviewCard],
        websiteURL: URL?,
        viewerIsOwner: Bool
    ) {
        self.businessId = businessId
        self.header = header
        self.stats = stats
        self.about = about
        self.hours = hours
        self.address = address
        self.contact = contact
        self.services = services
        self.reviews = reviews
        self.websiteURL = websiteURL
        self.viewerIsOwner = viewerIsOwner
    }
}

/// Top-level render state for the Business Profile screen.
public enum BusinessProfileState: Sendable, Equatable {
    case loading
    case loaded(BusinessProfileContent)
    case notFound
    case error(message: String)
}
