//
//  MapListHybridContent.swift
//  Pantopus
//
//  T6.6a (P24) — Shared archetype shell types for the map+list hybrid
//  surface. Three detent stops, pin model, and the pure detent-snap
//  resolver. The shell itself (`MapListHybridShell`) lives alongside.
//
//  Decision context: docs/t6-open-questions-decisions.md Q9. The three
//  detents are absolute heights (160 / 296 / 518 pt) — the same fixed
//  values that the design's 740pt mockup mapped to (~20% / 40% / 70%).
//

import CoreGraphics
import Foundation
import SwiftUI

/// Three snap stops for the map+list hybrid sheet.
///
/// Heights are absolute per the T6.6a Q9 contract:
/// - `.collapsed` (160 pt) shows header + drag-to-expand prompt
/// - `.standard` (296 pt) shows header + carousel of rail cards
/// - `.expanded` (518 pt) shows header + full vertical list
public enum MapListHybridDetent: CaseIterable, Sendable, Hashable {
    case collapsed
    case standard
    case expanded

    /// Pixel height the sheet occupies at this stop.
    public var height: CGFloat {
        switch self {
        case .collapsed: 160
        case .standard: 296
        case .expanded: 518
        }
    }
}

/// One pin on the map. Coordinates are raw lat/lon doubles so the model
/// stays free of MapKit imports for consumers that only need the data
/// shape (view-models, tests).
public struct MapPin: Identifiable, Sendable, Hashable {
    public let id: String
    public let latitude: Double
    public let longitude: Double
    public let color: Color
    public let state: MapPinState

    public init(
        id: String,
        latitude: Double,
        longitude: Double,
        color: Color,
        state: MapPinState = .confirmed
    ) {
        self.id = id
        self.latitude = latitude
        self.longitude = longitude
        self.color = color
        self.state = state
    }
}

/// Per-pin lifecycle state. Drives the visual treatment per design:
/// confirmed gets a white ring, pending dashes its color outline.
public enum MapPinState: Sendable, Hashable {
    case confirmed
    case pending
}

/// User-coordinate anchor for the "you are here" overlay disc. The
/// shell defaults to `nil` (no overlay) — callers pass in their
/// `LocationProviding` snapshot when available.
public struct MapAnchor: Sendable, Hashable {
    public let latitude: Double
    public let longitude: Double

    public init(latitude: Double, longitude: Double) {
        self.latitude = latitude
        self.longitude = longitude
    }
}

/// Pure resolver for the next detent after a drag release. Extracted so
/// unit tests can verify the snap-to-nearest + velocity-nudge math
/// without spinning up the SwiftUI hierarchy.
///
/// Sign convention is shared with the Android + web resolvers so the
/// same gesture lands at the same stop on every platform:
///
/// - **Positive velocity = downward flick** → sheet shrinks
///   (`expanded` → `standard` → `collapsed`)
/// - **Negative velocity = upward flick** → sheet grows
///   (`collapsed` → `standard` → `expanded`)
///
/// This matches Compose's `draggable` and the browser's pointer
/// `clientY` delta conventions. iOS shells should pass the raw
/// `gesture.predictedEndTranslation.height` (which UIKit makes positive
/// when the finger moves down) without flipping the sign.
///
/// - Parameters:
///   - current: detent at drag start
///   - velocity: predicted vertical velocity in points / second
///     (positive = downward)
///   - displacedHeight: live sheet height in points the moment the
///     gesture released
/// - Returns: the detent the sheet should snap to.
public enum MapListHybridDetentResolver {
    /// Points-per-second threshold for a flick gesture to nudge one
    /// detent past the snap-to-nearest target.
    public static let velocityThreshold: CGFloat = 600

    public static func resolve(
        from current: MapListHybridDetent,
        velocity: CGFloat,
        displacedHeight: CGFloat
    ) -> MapListHybridDetent {
        let sorted = MapListHybridDetent.allCases.sorted { a, b in
            abs(a.height - displacedHeight) < abs(b.height - displacedHeight)
        }
        var target = sorted.first ?? current

        if velocity > velocityThreshold {
            // Flick down → shrink one detent.
            switch current {
            case .expanded: target = .standard
            case .standard: target = .collapsed
            case .collapsed: target = .collapsed
            }
        } else if velocity < -velocityThreshold {
            // Flick up → grow one detent.
            switch current {
            case .collapsed: target = .standard
            case .standard: target = .expanded
            case .expanded: target = .expanded
            }
        }
        return target
    }
}
