//
//  TasksMapContent.swift
//  Pantopus
//
//  A11.1 Tasks map — the Gigs-only mode of the map+list hybrid archetype
//  (`Features/Shared/MapListHybrid`). Reached from the Gigs feed's
//  list/map toggle. Same canvas as the generic Nearby map, filtered to
//  tasks with a "Post task" button below the locate / layers controls.
//
//  Content the view consumes: one `TaskMapItem` per pin / rail card, the
//  four render states, and the category→glyph mapping for the rail-card
//  tiles. No backend — the view-model seeds from `TasksMapSampleData`.
//

import SwiftUI

/// One task — a pin on the map and a card in the sheet rail, rolled up.
/// Coordinates are raw lat/lon doubles so the model stays free of MapKit.
public struct TaskMapItem: Identifiable, Sendable, Hashable {
    public let id: String
    public let category: GigsCategory
    public let state: MapPinState
    public let latitude: Double
    public let longitude: Double
    public let title: String
    /// Free-form price string ("$60", "$22/walk", "$180").
    public let price: String
    public let distanceLabel: String
    public let bidCount: Int

    public init(
        id: String,
        category: GigsCategory,
        state: MapPinState = .confirmed,
        latitude: Double,
        longitude: Double,
        title: String,
        price: String,
        distanceLabel: String,
        bidCount: Int
    ) {
        self.id = id
        self.category = category
        self.state = state
        self.latitude = latitude
        self.longitude = longitude
        self.title = title
        self.price = price
        self.distanceLabel = distanceLabel
        self.bidCount = bidCount
    }

    /// Projection into the shell's pin model — the colour comes from the
    /// gig category swatch, the white-ring / dashed-outline treatment from
    /// the task's `state`.
    public var pin: MapPin {
        MapPin(
            id: id,
            latitude: latitude,
            longitude: longitude,
            color: category.color,
            state: state
        )
    }
}

/// Render state for the Tasks map. Mirrors the four-state contract every
/// fetchable Pantopus surface ships.
public enum TasksMapState: Sendable {
    case loading
    case populated([TaskMapItem])
    case empty
    case error(message: String)
}

/// Rail-card tile glyph for a task category. Best-fit from the typed icon
/// set for the design's per-category Lucide glyphs — `spray-can`,
/// `truck`, and `book-open` aren't in `PantopusIcon`, so `sparkles`,
/// `package`, and `graduation-cap` stand in.
func taskCategoryGlyph(_ category: GigsCategory) -> PantopusIcon {
    switch category {
    case .all: .circle
    case .handyman: .hammer
    case .cleaning: .sparkles
    case .moving: .package
    case .petcare: .pawPrint
    case .childcare: .baby
    case .tutoring: .graduationCap
    case .tech: .laptop
    case .delivery: .send
    }
}
