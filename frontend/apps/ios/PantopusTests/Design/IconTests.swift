//
//  IconTests.swift
//  PantopusTests
//
//  Inventory + renderability checks for `PantopusIcon`.
//

import XCTest
import SwiftUI
import UIKit
@testable import Pantopus

final class IconTests: XCTestCase {

    /// Expected Lucide icon inventory, derived from the design JSX. Keep in
    /// lock-step with the enum — the test catches additions/removals.
    private let expectedInventory: [String] = [
        "home", "map", "inbox", "user", "bell", "menu", "shield-check", "x",
        "plus-circle", "camera", "scan-line", "plus-square", "sun",
        "chevron-right", "chevron-left", "megaphone", "shopping-bag", "hammer",
        "mailbox", "search", "user-plus", "file", "copy", "check",
        "more-horizontal", "arrow-left", "send", "chevron-down", "chevron-up",
        "trash-2", "edit-2", "upload", "shield", "lock", "check-circle",
        "alert-circle", "circle", "info",
    ]

    func testInventoryMatches() {
        let actual = PantopusIcon.allCases.map(\.rawValue)
        XCTAssertEqual(
            Set(actual),
            Set(expectedInventory),
            "PantopusIcon inventory drifted from the design-spec list."
        )
        XCTAssertEqual(
            actual.count,
            expectedInventory.count,
            "PantopusIcon case count mismatch."
        )
    }

    func testInventoryHasNoDuplicates() {
        let raws = PantopusIcon.allCases.map(\.rawValue)
        XCTAssertEqual(Set(raws).count, raws.count, "PantopusIcon rawValues must be unique.")
    }

    func testEverySymbolResolvesToAValidSFSymbol() {
        // SF Symbols available on iOS 17+. If a mapping names a symbol that
        // doesn't exist, UIImage(systemName:) returns nil.
        for icon in PantopusIcon.allCases {
            XCTAssertNotNil(
                UIImage(systemName: icon.sfSymbolName),
                "No SF Symbol resolves for \(icon.rawValue) → \(icon.sfSymbolName)"
            )
        }
    }

    @MainActor
    func testEveryIconRendersWithoutCrashing() {
        for icon in PantopusIcon.allCases {
            let host = UIHostingController(rootView: Icon(icon))
            host.loadViewIfNeeded()
            XCTAssertNotNil(host.view, "Icon \(icon.rawValue) failed to build a view tree")
        }
    }
}
