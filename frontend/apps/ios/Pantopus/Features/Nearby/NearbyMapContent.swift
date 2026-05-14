//
//  NearbyMapContent.swift
//  Pantopus
//
//  Content models the Nearby map view consumes. The view-model
//  projects backend gig/listing rows into a homogeneous
//  `MapEntity` so the map / sheet vocabulary stays unified.
//

import CoreLocation
import Foundation

/// Entity kind discriminator. Drives the detail-screen routing and
/// keeps the per-platform pin geometry uniform.
public enum MapEntityKind: String, Sendable, Hashable, Codable {
    case gig
    case listing
}

/// Per-pin lifecycle state. Drives the visual treatment described in
/// the design — confirmed gets a white ring, pending dashes its color
/// outline, active runs the 1.6s pulse halo.
public enum MapEntityState: Sendable, Hashable {
    case confirmed
    case pending
}

/// One pin / one card / one row, all rolled up. The view-model never
/// hands raw DTOs to the view.
public struct MapEntity: Identifiable, Sendable, Hashable {
    public let id: String
    public let kind: MapEntityKind
    public let category: GigsCategory
    public let state: MapEntityState
    public let latitude: Double
    public let longitude: Double
    public let title: String
    public let summary: String?
    /// Free-form price string ("$60", "$22 / walk", "$180"). Listings
    /// reuse this for their sale price.
    public let price: String?
    public let distanceLabel: String?
    public let bidCount: Int

    public init(
        id: String,
        kind: MapEntityKind,
        category: GigsCategory,
        state: MapEntityState,
        latitude: Double,
        longitude: Double,
        title: String,
        summary: String?,
        price: String?,
        distanceLabel: String?,
        bidCount: Int
    ) {
        self.id = id
        self.kind = kind
        self.category = category
        self.state = state
        self.latitude = latitude
        self.longitude = longitude
        self.title = title
        self.summary = summary
        self.price = price
        self.distanceLabel = distanceLabel
        self.bidCount = bidCount
    }

    public var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}

/// Bottom-sheet snap stop. The three positions follow the design:
/// ~20% collapsed (header + prompt), ~40% rail of cards, ~70% full
/// vertical list.
public enum SheetStop: CaseIterable, Sendable, Hashable {
    case collapsed
    case standard
    case expanded

    /// Fraction of the screen height the sheet occupies at this stop.
    public var heightFraction: CGFloat {
        switch self {
        case .collapsed: return 0.20
        case .standard: return 0.40
        case .expanded: return 0.70
        }
    }
}

/// Render state for the Nearby map screen.
public enum NearbyMapState: Sendable {
    case loading
    case loaded(NearbyMapLoaded)
    case error(message: String)
}

/// Successful load — splits the entities into the ordered list the
/// sheet renders and keeps the user's anchor coordinate so the "you
/// are here" disc lands in the right spot. `selectedId` mirrors the
/// pin↔card link.
public struct NearbyMapLoaded: Sendable, Hashable {
    public let entities: [MapEntity]
    public let userCoordinate: UserCoordinate?
    public let selectedId: String?

    public init(
        entities: [MapEntity],
        userCoordinate: UserCoordinate?,
        selectedId: String? = nil
    ) {
        self.entities = entities
        self.userCoordinate = userCoordinate
        self.selectedId = selectedId
    }
}
