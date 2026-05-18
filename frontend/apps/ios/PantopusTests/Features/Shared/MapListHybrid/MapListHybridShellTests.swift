//
//  MapListHybridShellTests.swift
//  PantopusTests
//
//  T6.6a (P24) — pure detent-resolver tests. The shell's drag-release
//  math is extracted into `MapListHybridDetentResolver.resolve(...)` so
//  these tests verify snap-to-nearest, velocity-nudge, and edge clamps
//  without spinning up SwiftUI.
//

import CoreLocation
import XCTest
@testable import Pantopus

final class MapListHybridShellTests: XCTestCase {
    // MARK: - Snap-to-nearest

    func testResolveSnapsToCollapsedWhenSheetReleasedNearCollapsedHeight() {
        let target = MapListHybridDetentResolver.resolve(
            from: .standard,
            velocity: 0,
            displacedHeight: 170 // near 160
        )
        XCTAssertEqual(target, .collapsed)
    }

    func testResolveSnapsToStandardWhenSheetReleasedNearStandardHeight() {
        let target = MapListHybridDetentResolver.resolve(
            from: .collapsed,
            velocity: 0,
            displacedHeight: 290 // near 296
        )
        XCTAssertEqual(target, .standard)
    }

    func testResolveSnapsToExpandedWhenSheetReleasedNearExpandedHeight() {
        let target = MapListHybridDetentResolver.resolve(
            from: .standard,
            velocity: 0,
            displacedHeight: 500 // near 518
        )
        XCTAssertEqual(target, .expanded)
    }

    func testResolveSnapsToMidpointPreferringStandard() {
        // 228 = midpoint between 160 and 296; snap to whichever is closer.
        let target = MapListHybridDetentResolver.resolve(
            from: .collapsed,
            velocity: 0,
            displacedHeight: 250
        )
        XCTAssertEqual(target, .standard)
    }

    // MARK: - Velocity nudge

    //
    // Sign convention: positive velocity = downward flick (shrinks the
    // sheet); negative = upward (grows). Matches Compose's `draggable`
    // and the browser's `clientY` delta — see the resolver docstring.

    func testFlickUpFromCollapsedAdvancesToStandard() {
        let target = MapListHybridDetentResolver.resolve(
            from: .collapsed,
            velocity: -800, // upward → grow
            displacedHeight: 170
        )
        XCTAssertEqual(target, .standard)
    }

    func testFlickUpFromStandardAdvancesToExpanded() {
        let target = MapListHybridDetentResolver.resolve(
            from: .standard,
            velocity: -1000,
            displacedHeight: 320
        )
        XCTAssertEqual(target, .expanded)
    }

    func testFlickUpFromExpandedStaysExpanded() {
        let target = MapListHybridDetentResolver.resolve(
            from: .expanded,
            velocity: -1200,
            displacedHeight: 520
        )
        XCTAssertEqual(target, .expanded)
    }

    func testFlickDownFromExpandedRetreatsToStandard() {
        let target = MapListHybridDetentResolver.resolve(
            from: .expanded,
            velocity: 800, // downward → shrink
            displacedHeight: 500
        )
        XCTAssertEqual(target, .standard)
    }

    func testFlickDownFromStandardRetreatsToCollapsed() {
        let target = MapListHybridDetentResolver.resolve(
            from: .standard,
            velocity: 1000,
            displacedHeight: 280
        )
        XCTAssertEqual(target, .collapsed)
    }

    func testFlickDownFromCollapsedStaysCollapsed() {
        let target = MapListHybridDetentResolver.resolve(
            from: .collapsed,
            velocity: 1200,
            displacedHeight: 150
        )
        XCTAssertEqual(target, .collapsed)
    }

    // MARK: - Threshold boundary

    func testVelocityAtThresholdDoesNotNudge() {
        // At the threshold exactly, snap-to-nearest should win
        // (resolver uses strict `>` so equal-to-threshold defers).
        let target = MapListHybridDetentResolver.resolve(
            from: .collapsed,
            velocity: MapListHybridDetentResolver.velocityThreshold,
            displacedHeight: 165
        )
        XCTAssertEqual(target, .collapsed)
    }

    // MARK: - Detent heights

    func testDetentHeightsMatchQ9Contract() {
        XCTAssertEqual(MapListHybridDetent.collapsed.height, 160)
        XCTAssertEqual(MapListHybridDetent.standard.height, 296)
        XCTAssertEqual(MapListHybridDetent.expanded.height, 518)
    }

    func testDetentAllCasesOrdered() {
        XCTAssertEqual(
            MapListHybridDetent.allCases,
            [.collapsed, .standard, .expanded]
        )
    }

    // MARK: - Pin model

    func testMapPinExposesCoordinate() {
        let pin = MapPin(id: "p1", latitude: 40.75, longitude: -73.98, color: .red)
        XCTAssertEqual(pin.coordinate.latitude, 40.75)
        XCTAssertEqual(pin.coordinate.longitude, -73.98)
        XCTAssertEqual(pin.state, .confirmed)
    }

    func testMapPinPendingStateOverride() {
        let pin = MapPin(id: "p2", latitude: 0, longitude: 0, color: .blue, state: .pending)
        XCTAssertEqual(pin.state, .pending)
    }
}
