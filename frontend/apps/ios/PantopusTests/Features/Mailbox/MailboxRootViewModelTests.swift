//
//  MailboxRootViewModelTests.swift
//  PantopusTests
//
//  B.1 — state-projection coverage for the Mailbox root (drawer-tabs
//  hybrid). The repo's "snapshot" tests are design-reference PNG
//  tripwires that need a simulator render to generate; these assert the
//  same contract deterministically via the view-model's render states,
//  covering the three design frames (Me/Incoming, Biz/Counter,
//  Earn/Incoming-Empty) plus the preserve-tab and per-(drawer, tab)
//  count behaviours.
//

import XCTest
@testable import Pantopus

@MainActor
final class MailboxRootViewModelTests: XCTestCase {
    private func rows(_ state: ListOfRowsState) -> [RowModel] {
        guard case let .loaded(sections, _) = state else { return [] }
        return sections.flatMap(\.rows)
    }

    private func sectionHeaders(_ state: ListOfRowsState) -> [String] {
        guard case let .loaded(sections, _) = state else { return [] }
        return sections.compactMap(\.header)
    }

    // MARK: - Frame 01 · Me / Incoming

    func test_meIncoming_populatedFrame() async {
        let vm = MailboxRootViewModel(initialDrawer: .me, initialTab: .incoming)
        await vm.load()

        guard case .loaded = vm.state else {
            return XCTFail("Expected loaded, got \(vm.state)")
        }
        XCTAssertEqual(sectionHeaders(vm.state), ["Today", "Yesterday"])
        XCTAssertEqual(rows(vm.state).count, 5)
        XCTAssertEqual(rows(vm.state).first?.title, "Echo Pop arriving today by 8pm")
    }

    // MARK: - Frame 02 · Biz / Counter

    func test_bizCounter_populatedFrame() async {
        let vm = MailboxRootViewModel(initialDrawer: .business, initialTab: .counter)
        await vm.load()

        guard case .loaded = vm.state else {
            return XCTFail("Expected loaded, got \(vm.state)")
        }
        XCTAssertEqual(sectionHeaders(vm.state), ["Due this week", "Awaiting your response"])
        XCTAssertEqual(rows(vm.state).count, 5)
    }

    // MARK: - Frame 03 · Earn / Incoming (empty)

    func test_earnIncoming_emptyFrame() async {
        let vm = MailboxRootViewModel(initialDrawer: .earn, initialTab: .incoming)
        await vm.load()

        guard case let .empty(content) = vm.state else {
            return XCTFail("Expected empty, got \(vm.state)")
        }
        XCTAssertEqual(content.headline, "No earn items yet")
        XCTAssertEqual(content.ctaTitle, "Browse gigs")
    }

    func test_everyEarnTabIsEmpty() async {
        for tab in MailboxTab.allCases {
            let vm = MailboxRootViewModel(initialDrawer: .earn, initialTab: tab)
            await vm.load()
            guard case .empty = vm.state else {
                return XCTFail("Earn/\(tab.rawValue) should be empty, got \(vm.state)")
            }
        }
    }

    // MARK: - Drawer switch preserves the selected tab

    func test_drawerSwitchPreservesSelectedTab() async {
        let vm = MailboxRootViewModel(initialDrawer: .me, initialTab: .incoming)
        await vm.load()

        vm.selectTab(.counter)
        XCTAssertEqual(vm.currentTab, .counter)

        vm.selectDrawer(.business)
        XCTAssertEqual(vm.currentTab, .counter, "Drawer switch must keep the active tab")
        XCTAssertEqual(vm.selectedDrawer, .business)
        XCTAssertEqual(sectionHeaders(vm.state), ["Due this week", "Awaiting your response"])
    }

    func test_selectingTabRebuildsState() async {
        let vm = MailboxRootViewModel(initialDrawer: .me, initialTab: .incoming)
        await vm.load()
        XCTAssertEqual(sectionHeaders(vm.state), ["Today", "Yesterday"])

        vm.selectTab(.counter)
        XCTAssertEqual(sectionHeaders(vm.state), ["Awaiting your response"])
    }

    // MARK: - Per-(drawer, tab) unread counts

    func test_tabBadgesForMeDrawer() async {
        let vm = MailboxRootViewModel(initialDrawer: .me, initialTab: .incoming)
        await vm.load()

        XCTAssertEqual(vm.tabBadge(.incoming), 3)
        XCTAssertEqual(vm.tabBadge(.counter), 2)
        XCTAssertNil(vm.tabBadge(.vault), "Vault never shows an unread count")
    }

    func test_drawerBadgesAggregateUnread() {
        let vm = MailboxRootViewModel()
        XCTAssertEqual(vm.drawerBadge(.me), 5)
        XCTAssertEqual(vm.drawerBadge(.home), 3)
        XCTAssertEqual(vm.drawerBadge(.earn), 0, "Earn has no mail, so no badge")
    }

    func test_unreadCountForBizCounter() {
        let vm = MailboxRootViewModel()
        XCTAssertEqual(vm.unreadCount(drawer: .business, tab: .counter), 4)
    }

    // MARK: - FAB visibility

    func test_fabHiddenOnEmptyShownOnPopulated() async {
        let populated = MailboxRootViewModel(initialDrawer: .me, initialTab: .incoming)
        await populated.load()
        XCTAssertNotNil(populated.fab)

        let empty = MailboxRootViewModel(initialDrawer: .earn, initialTab: .incoming)
        await empty.load()
        XCTAssertNil(empty.fab)
    }

    // MARK: - Trust override flows into the row chips

    func test_sampleRowCarriesPerItemTrust() async {
        let vm = MailboxRootViewModel(initialDrawer: .me, initialTab: .incoming)
        await vm.load()

        let coupon = rows(vm.state).first { $0.id == "me-in-3" }
        XCTAssertEqual(coupon?.chips?.last?.text, "Partial")
    }

    // MARK: - Seeded states

    func test_seededErrorStateSurfacesVerbatim() async {
        let vm = MailboxRootViewModel(seededState: .error(message: "Couldn't load mail."))
        await vm.load()
        guard case let .error(message) = vm.state else {
            return XCTFail("Expected error, got \(vm.state)")
        }
        XCTAssertEqual(message, "Couldn't load mail.")
    }

    func test_initialStateIsLoading() {
        let vm = MailboxRootViewModel()
        guard case .loading = vm.state else {
            return XCTFail("Expected loading before load(), got \(vm.state)")
        }
    }
}
