//
//  NotificationSettingsViewModelTests.swift
//  PantopusTests
//
//  P7.5 / A14.5 — the reshaped notification matrix. Covers the
//  populated + paused frames, the channel-header / triad projection,
//  the locked Emergency-push chip, optimistic chip + pause toggles, and
//  the helper-line parity contract (mirrored on Android).
//

import XCTest
@testable import Pantopus

@MainActor
final class NotificationSettingsViewModelTests: XCTestCase {
    private func loadedGroups(_ vm: NotificationSettingsViewModel) async -> [GroupedListGroup] {
        await vm.load()
        guard case let .loaded(groups) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return []
        }
        return groups
    }

    private func row(_ groups: [GroupedListGroup], _ id: String) -> GroupedListRow? {
        groups.flatMap(\.rows).first { $0.id == id }
    }

    // MARK: - Populated frame

    func testPopulatedProducesMasterPlusFiveCategories() async {
        let vm = NotificationSettingsViewModel(variant: .populated)
        let groups = await loadedGroups(vm)
        XCTAssertEqual(
            groups.map(\.id),
            ["master", "tasks", "pulse", "marketplace", "homeMailbox", "accountSecurity"]
        )
        XCTAssertNil(vm.banner)
        XCTAssertFalse(vm.contentDimmed)
    }

    func testCategoryCardsCarryChannelHeaderAndTriadRows() async {
        let vm = NotificationSettingsViewModel(variant: .populated)
        let groups = await loadedGroups(vm)
        let categories = groups.filter { $0.id != "master" }
        XCTAssertEqual(categories.count, 5)
        for category in categories {
            XCTAssertTrue(category.showsChannelHeader, "\(category.id) should show the P/E/S header")
            for row in category.rows {
                guard case .channelTriad = row.control else {
                    XCTFail("\(row.id) should be a channelTriad row")
                    continue
                }
            }
        }
    }

    func testMasterCardIsToggleAndChevron() async {
        let vm = NotificationSettingsViewModel(variant: .populated)
        let groups = await loadedGroups(vm)
        let master = groups.first { $0.id == "master" }
        XCTAssertEqual(master?.showsChannelHeader, false)
        guard case .toggle(false)? = row(groups, NotificationSettingsViewModel.RowID.pauseAll)?.control else {
            return XCTFail("Pause-all should be an off toggle in the populated frame")
        }
        guard case .chipStatus? = row(groups, NotificationSettingsViewModel.RowID.quietHours)?.control else {
            return XCTFail("Quiet hours should be a chip + chevron row")
        }
    }

    func testSeedPatternsMatchDesign() async {
        let vm = NotificationSettingsViewModel(variant: .populated)
        let groups = await loadedGroups(vm)
        assertPattern(row(groups, "tasks.bids"), p: true, e: false, s: false)
        assertPattern(row(groups, "tasks.messages"), p: true, e: true, s: false)
        assertPattern(row(groups, "tasks.receipts"), p: false, e: true, s: false)
        assertPattern(row(groups, "pulse.lostFound"), p: false, e: false, s: false)
        assertPattern(row(groups, "marketplace.offers"), p: true, e: true, s: false)
        assertPattern(row(groups, "account.billing"), p: false, e: true, s: false)
    }

    func testEmergencyKeepsPushLocked() async {
        let vm = NotificationSettingsViewModel(variant: .populated)
        let groups = await loadedGroups(vm)
        guard case let .channelTriad(p, e, s, locked) = row(groups, NotificationSettingsViewModel.RowID.emergency)?.control else {
            return XCTFail("Emergency should be a channelTriad row")
        }
        XCTAssertTrue(p)
        XCTAssertTrue(e)
        XCTAssertTrue(s)
        XCTAssertEqual(locked, [.p], "Emergency push must be locked on")
    }

    // MARK: - Mutations (stubbed, local only)

    func testToggleChannelFlipsLocalState() async {
        let vm = NotificationSettingsViewModel(variant: .populated)
        _ = await loadedGroups(vm)
        await vm.toggleChannel("tasks.receipts", channel: .p, isOn: true)
        guard case let .loaded(groups) = vm.state else { return XCTFail("Expected .loaded") }
        assertPattern(row(groups, "tasks.receipts"), p: true, e: true, s: false)
    }

    func testLockedChannelCannotBeToggledOff() async {
        let vm = NotificationSettingsViewModel(variant: .populated)
        _ = await loadedGroups(vm)
        await vm.toggleChannel(NotificationSettingsViewModel.RowID.emergency, channel: .p, isOn: false)
        guard case let .loaded(groups) = vm.state else { return XCTFail("Expected .loaded") }
        guard case let .channelTriad(p, _, _, locked) = row(groups, NotificationSettingsViewModel.RowID.emergency)?.control else {
            return XCTFail("Emergency should be a channelTriad row")
        }
        XCTAssertTrue(p, "Locked push can't be turned off")
        XCTAssertEqual(locked, [.p])
    }

    // MARK: - Paused frame

    func testPauseAllSwapsMasterForBannerAndDims() async {
        let vm = NotificationSettingsViewModel(variant: .populated)
        _ = await loadedGroups(vm)
        await vm.toggleRow(NotificationSettingsViewModel.RowID.pauseAll, isOn: true)
        guard case let .loaded(groups) = vm.state else { return XCTFail("Expected .loaded") }
        XCTAssertFalse(groups.contains { $0.id == "master" }, "Master card is replaced by the banner")
        XCTAssertEqual(groups.first?.id, "tasks")
        XCTAssertTrue(vm.contentDimmed)
        XCTAssertEqual(vm.banner?.title, "Paused for 2 hours")
        XCTAssertEqual(vm.banner?.subtitle, "Resumes 11:42 AM · Emergency alerts still come through")
        XCTAssertEqual(vm.banner?.actionLabel, "Resume")
        XCTAssertEqual(vm.banner?.icon, .bellOff)
    }

    func testPausedVariantBootsPaused() async {
        let vm = NotificationSettingsViewModel(variant: .paused)
        let groups = await loadedGroups(vm)
        XCTAssertTrue(vm.contentDimmed)
        XCTAssertNotNil(vm.banner)
        XCTAssertFalse(groups.contains { $0.id == "master" })
        // The configured pattern is still readable underneath.
        XCTAssertEqual(groups.map(\.id), ["tasks", "pulse", "marketplace", "homeMailbox", "accountSecurity"])
    }

    func testResumeRestoresMaster() async {
        let vm = NotificationSettingsViewModel(variant: .paused)
        _ = await loadedGroups(vm)
        await vm.tapBanner()
        guard case let .loaded(groups) = vm.state else { return XCTFail("Expected .loaded") }
        XCTAssertNil(vm.banner)
        XCTAssertFalse(vm.contentDimmed)
        XCTAssertEqual(groups.first?.id, "master")
    }

    // MARK: - Copy parity contract

    func testFooterLegend() {
        let vm = NotificationSettingsViewModel()
        XCTAssertEqual(vm.footerCaption, "P · Push   E · Email   S · SMS")
    }

    func testHelperCopyMatchesDesign() async {
        let vm = NotificationSettingsViewModel(variant: .populated)
        let groups = await loadedGroups(vm)
        func helper(_ id: String) -> String? { groups.first { $0.id == id }?.helper }
        XCTAssertEqual(
            helper("master"),
            "Pause all silences every channel except emergency alerts. Quiet hours just delays them."
        )
        XCTAssertEqual(
            helper("tasks"),
            "Push only for things that need a fast reply. Receipts go to email so they're searchable."
        )
        XCTAssertEqual(helper("pulse"), "Pulse is quiet by default. Mentions break through, browsing doesn't.")
        XCTAssertNil(helper("marketplace"), "Marketplace card has no helper line in the design")
        XCTAssertEqual(helper("homeMailbox"), "Emergency alerts can't be muted on push.")
        XCTAssertEqual(helper("accountSecurity"), "Security alerts always come through. You can choose how.")
    }

    // MARK: - Helpers

    private func assertPattern(
        _ row: GroupedListRow?,
        p expectedP: Bool,
        e expectedE: Bool,
        s expectedS: Bool,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        guard case let .channelTriad(p, e, s, _) = row?.control else {
            XCTFail("Expected channelTriad for \(row?.id ?? "nil")", file: file, line: line)
            return
        }
        XCTAssertEqual(p, expectedP, "push for \(row?.id ?? "")", file: file, line: line)
        XCTAssertEqual(e, expectedE, "email for \(row?.id ?? "")", file: file, line: line)
        XCTAssertEqual(s, expectedS, "sms for \(row?.id ?? "")", file: file, line: line)
    }
}
