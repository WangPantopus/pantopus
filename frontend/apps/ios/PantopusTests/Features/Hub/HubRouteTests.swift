//
//  HubRouteTests.swift
//  PantopusTests
//
//  Locks the `HubRoute.editProfile` case introduced in P1.4 so the
//  Settings → Edit profile push lands on the real `EditProfileView`
//  rather than the previous `NotYetAvailableView` placeholder.
//

import XCTest
@testable import Pantopus

@MainActor
final class HubRouteTests: XCTestCase {
    /// `Hashable` conformance is what keeps the route in
    /// `NavigationStack(path:)`. Without it the push compiles but
    /// silently no-ops.
    func testEditProfileRouteIsHashableAndDistinctFromPlaceholder() {
        let route: HubRoute = .editProfile
        let placeholder: HubRoute = .placeholder(label: "Edit profile")
        XCTAssertNotEqual(route, placeholder)
        // Equality is reflexive and stable across construction sites —
        // important for `NavigationStack` to track the entry.
        XCTAssertEqual(HubRoute.editProfile, HubRoute.editProfile)
        var set: Set<HubRoute> = []
        set.insert(.editProfile)
        XCTAssertTrue(set.contains(.editProfile))
    }
}
