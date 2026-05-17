//
//  SettingsViewModelTests.swift
//  PantopusTests
//
//  Covers the three Settings data sources: index loads chevron rows
//  + chips; notifications toggle persists optimistically with
//  rollback on PATCH failure; privacy radio + slider persist; index
//  signOut clears auth state.
//

import XCTest
@testable import Pantopus

@MainActor
final class SettingsViewModelTests: XCTestCase {
    override func setUp() {
        super.setUp()
        SequencedURLProtocol.reset()
    }

    private func makeAPI() -> APIClient {
        APIClient(
            environment: .current,
            session: SequencedURLProtocol.makeSession(),
            retryPolicy: .none
        )
    }

    private static let defaultSettingsJSON = """
    {"settings":{
      "user_id":"u_test",
      "search_visibility":"verified",
      "address_precision":"street",
      "hide_from_search":false,
      "show_online_status":true,
      "show_last_active":false,
      "show_read_receipts":true,
      "share_home_check_ins":false,
      "push_preferences":{"messages":true,"gigs":true,"listings":false,"mailbox":true,"home":true},
      "email_preferences":{"messages":false,"gigs":true,"listings":false,"mailbox":true,"home":false},
      "sms_preferences":{"messages":false,"gigs":false,"listings":false,"mailbox":true,"home":false},
      "updated_at":"2026-01-01T00:00:00Z"
    }}
    """

    // MARK: - Index

    func testIndexLoadProducesAllExpectedGroups() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: "{\"blocks\":[]}") // privacy/blocks fetch
        ]
        let vm = SettingsIndexViewModel(api: makeAPI(), auth: AuthManager.previewSignedIn)
        await vm.load()
        guard case let .loaded(groups) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return
        }
        let ids = groups.map(\.id)
        XCTAssertEqual(ids, ["account", "privacy", "notifications", "payments", "support", "session"])
        // Verify destructive row sits in its own group with no overline.
        XCTAssertNil(groups.last?.overline)
        XCTAssertEqual(groups.last?.rows.first?.id, "signOut")
        XCTAssertTrue(groups.last?.rows.first?.destructive ?? false)
    }

    /// P8 / T6.2c — every row in the support / account / privacy
    /// groups now routes to a real sub-screen rather than
    /// `NotYetAvailableView`. The router test belongs in the view
    /// itself, but here we assert the row ids the router maps from.
    func testIndexRowIdsCoverEveryWiredSubRoute() async {
        SequencedURLProtocol.sequence = [.status(200, body: "{\"blocks\":[]}")]
        let vm = SettingsIndexViewModel(api: makeAPI(), auth: AuthManager.previewSignedIn)
        await vm.load()
        guard case let .loaded(groups) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let allRowIds = Set(groups.flatMap { $0.rows.map(\.id) })
        // Six sub-routes wired in P8; two stay placeholders (export +
        // paymentsPayouts).
        let wired: Set<String> = ["blocks", "password", "verification", "help", "legal", "about"]
        XCTAssertTrue(wired.isSubset(of: allRowIds), "Missing wired row ids: \(wired.subtracting(allRowIds))")
        let placeheld: Set<String> = ["export", "paymentsPayouts"]
        XCTAssertTrue(placeheld.isSubset(of: allRowIds), "Placeholder routes must still surface in the index.")
    }

    // MARK: - Notifications

    func testNotificationToggleOptimisticPersistsOnSuccess() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.defaultSettingsJSON),
            .status(200, body: Self.defaultSettingsJSON) // PATCH echo
        ]
        let vm = NotificationSettingsViewModel(api: makeAPI(), auth: AuthManager.previewSignedIn)
        await vm.load()
        await vm.toggleRow("push.listings", isOn: true)
        guard case let .loaded(groups) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        // After persist, the server response wins — listings remains
        // false in the canned JSON, so the row reflects that.
        let pushGroup = groups.first { $0.id == "push" }
        let listings = pushGroup?.rows.first { $0.id == "push.listings" }
        if case let .toggle(isOn) = listings?.control {
            XCTAssertFalse(isOn, "server canned response has listings=false; the VM should reflect it")
        } else {
            XCTFail("Expected toggle control on push.listings")
        }
    }

    func testNotificationToggleRollsBackOnFailure() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.defaultSettingsJSON),
            .status(500, body: "{}") // PATCH fails
        ]
        let vm = NotificationSettingsViewModel(api: makeAPI(), auth: AuthManager.previewSignedIn)
        await vm.load()
        await vm.toggleRow("push.messages", isOn: false)
        guard case let .loaded(groups) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let messages = groups.first?.rows.first { $0.id == "push.messages" }
        if case let .toggle(isOn) = messages?.control {
            // After rollback, messages should be back to the original
            // value (true in the canned default).
            XCTAssertTrue(isOn, "VM should roll back to the seed value when the PATCH fails")
        } else {
            XCTFail("Expected toggle on push.messages")
        }
    }

    // MARK: - Privacy

    func testPrivacyRadioPersists() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.defaultSettingsJSON),
            .status(200, body: """
            {"settings":{
              "user_id":"u_test",
              "search_visibility":"none",
              "address_precision":"street",
              "hide_from_search":false,
              "show_online_status":true,
              "show_last_active":false,
              "show_read_receipts":true,
              "share_home_check_ins":false,
              "updated_at":"2026-01-01T00:00:00Z"
            }}
            """)
        ]
        let vm = PrivacySettingsViewModel(api: makeAPI())
        await vm.load()
        await vm.selectRadio("visibility.none")
        guard case let .loaded(groups) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let visibility = groups.first { $0.id == "visibility" }
        let selected = visibility?.rows.first { row in
            if case let .radio(isSelected) = row.control { return isSelected }
            return false
        }
        XCTAssertEqual(selected?.id, "visibility.none")
    }

    func testPrivacySliderPersists() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.defaultSettingsJSON),
            .status(200, body: """
            {"settings":{
              "user_id":"u_test",
              "search_visibility":"verified",
              "address_precision":"block",
              "updated_at":"2026-01-01T00:00:00Z"
            }}
            """)
        ]
        let vm = PrivacySettingsViewModel(api: makeAPI())
        await vm.load()
        await vm.setSlider("addressPrecision", index: 2) // "Block"
        guard case let .loaded(groups) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let address = groups.first { $0.id == "address" }
        let precisionRow = address?.row(id: "addressPrecision")
        if case let .slider(_, index) = precisionRow?.control {
            XCTAssertEqual(index, 2)
        } else {
            XCTFail("Expected slider on addressPrecision row")
        }
    }
}
