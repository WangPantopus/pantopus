//
//  GigsContent.swift
//  Pantopus
//
//  Render-only content models the Gigs feed view consumes. The
//  view-model projects `GigDTO` into `GigCardContent` so the view never
//  reaches into the network DTO.
//

import Foundation

/// One row in the populated feed.
public struct GigCardContent: Identifiable, Sendable, Hashable {
    public let id: String
    public let category: GigsCategory
    /// "0.2mi · 2h ago" — composed meta line up top, optional pieces.
    public let metaLine: String
    public let title: String
    public let body: String
    /// Free-form price string: "$60", "$22 / walk".
    public let price: String
    public let bidCount: Int
    /// Right-aligned distance label ("0.2mi"). `nil` hides the row.
    public let distanceLabel: String?

    public init(
        id: String,
        category: GigsCategory,
        metaLine: String,
        title: String,
        body: String,
        price: String,
        bidCount: Int,
        distanceLabel: String?
    ) {
        self.id = id
        self.category = category
        self.metaLine = metaLine
        self.title = title
        self.body = body
        self.price = price
        self.bidCount = bidCount
        self.distanceLabel = distanceLabel
    }
}

/// Empty-state content. `radiusMiles` drives the radius-hint pill at the
/// bottom — design spec calls for "Within 1 mi · widen in filter".
public struct GigsFeedEmpty: Sendable, Hashable {
    public let radiusMiles: Double

    public init(radiusMiles: Double = 1) {
        self.radiusMiles = radiusMiles
    }
}

/// Render state for the Gigs feed screen.
public enum GigsFeedState: Sendable {
    case loading
    case empty(GigsFeedEmpty)
    case loaded([GigCardContent])
    case error(message: String)
}
