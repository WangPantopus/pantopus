//
//  IconTests.swift
//  PantopusTests
//
//  Inventory + renderability checks for `PantopusIcon`.
//

import SwiftUI
import UIKit
import XCTest
@testable import Pantopus

final class IconTests: XCTestCase {
    /// Expected Lucide icon inventory, derived from the design JSX. Keep in
    /// lock-step with the enum — the test catches additions/removals.
    private let expectedInventory: [String] = [
        "home", "map", "inbox", "user", "bell", "menu", "shield-check", "x",
        "plus-circle", "camera", "scan-line", "plus-square", "sun",
        "chevron-right", "chevron-left", "megaphone", "shopping-bag", "hammer",
        "mailbox", "search", "user-plus", "file", "copy", "check",
        "more-horizontal", "arrow-left", "arrow-right", "send", "chevron-down", "chevron-up",
        "trash-2", "edit-2", "upload", "shield", "lock", "check-circle",
        "alert-circle", "circle", "info", "wifi-off", "heart", "thumbs-up", "star",
        "help-circle", "calendar", "lightbulb", "eye", "share", "radio", "map-pin",
        "pencil", "briefcase", "gavel", "sliders-horizontal", "message-circle",
        "at-sign", "badge-check", "tag", "shield-alert", "check-check", "history",
        "receipt", "clock", "users", "dollar-sign",
        "dog", "cat", "bird", "fish", "turtle", "paw-print",
        "sparkles", "timer", "repeat", "hourglass", "hand-coins", "package",
        "compass", "filter",
        // T5.3.1 My bids — bid lifecycle chips + footer.
        "crown", "trending-down", "ban", "file-text",
        // T5.3.2 My tasks — poster-side chips + footer.
        "plus", "rocket", "clipboard-list", "clock-plus", "circle-slash", "play",
        // T5.3.3 My posts — archive chip + empty-state compose icon.
        "archive", "message-square-plus",
        // T5.3.4 Listing offers — listing-context header icons.
        "bookmark",
        // T6.0a Bills — utility category iconography + banner/auto-pay markers.
        "zap", "flame", "droplet", "wifi", "building-2", "smartphone", "wallet", "hash",
        // T6.0b My tasks V2 — Magic Task archetype + task-format icons.
        "tv", "laptop", "monitor", "shuffle", "wand-sparkles", "arrow-up-right",
        // T6.4a Access codes — reveal toggle + empty state.
        "eye-off", "key-round",
        // T6.4b Emergency info — per-category tiles, quick actions, and markers.
        "pin", "power", "phone-call", "phone", "navigation", "heart-pulse",
        "siren", "stethoscope", "cross", "flag", "user-round", "flask-conical",
        "flame-kindling", "printer", "alert-triangle",
        // T6.4b Documents — file-type tiles, category rows, and document actions.
        "image", "file-type", "file-spreadsheet", "file-signature", "landmark",
        "stamp", "id-card", "folder-lock", "upload-cloud", "calendar-clock", "download",
        // T6.3c Household tasks — chore-category iconography + banner glyph.
        "leaf", "list-checks", "utensils", "baby",
        // T6.3b Maintenance — task category iconography.
        "wrench", "fan", "cloud-rain", "refrigerator", "bug", "trees", "paint-roller", "bell-ring"
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
