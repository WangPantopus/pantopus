//
//  MailDayViewModelTests.swift
//  PantopusTests
//
//  A13.16 — state-projection coverage for the My Mail Day view-model.
//  Asserts the two design frames (populated mid-afternoon + empty
//  "nothing new") project off the sample fixture cleanly, the
//  ProgressRing counters add up, and the 5-second undo countdown ticks
//  down through `tickUndo()`.
//

import XCTest
@testable import Pantopus

@MainActor
final class MailDayViewModelTests: XCTestCase {
    // MARK: - Populated frame

    func test_populated_projectsContentAndCounters() async {
        let vm = MailDayViewModel(variant: .populated)
        await vm.load()

        guard case let .populated(content) = vm.state else {
            return XCTFail("Expected .populated, got \(vm.state)")
        }
        XCTAssertEqual(content.unreviewed.count, 2)
        XCTAssertEqual(content.reviewed.count, 6)
        XCTAssertEqual(vm.total, 8)
        XCTAssertEqual(vm.done, 6)
        XCTAssertEqual(vm.remaining, 2)
        XCTAssertEqual(vm.routedCount, 4)
        XCTAssertEqual(vm.junkedCount, 1)
        XCTAssertEqual(vm.returnedCount, 1)
    }

    func test_populated_finishDayDisabledUntilEmpty() async {
        let vm = MailDayViewModel(variant: .populated)
        await vm.load()

        XCTAssertFalse(vm.canFinishDay, "Should be disabled while 2 pieces still pending")
    }

    func test_populated_latestRowCarriesUndoCountdown() async {
        let vm = MailDayViewModel(variant: .populated)
        await vm.load()

        guard case let .populated(content) = vm.state else {
            return XCTFail("Expected .populated")
        }
        XCTAssertEqual(content.reviewed.first?.undoCountdown, 5)
        XCTAssertNil(
            content.reviewed.dropFirst().first?.undoCountdown,
            "Only the latest reviewed row should carry the countdown"
        )
    }

    // MARK: - Undo countdown ticking

    func test_tickUndo_decrementsLatest() async {
        let vm = MailDayViewModel(variant: .populated)
        await vm.load()

        vm.tickUndo()
        guard case let .populated(content) = vm.state else {
            return XCTFail("Expected .populated")
        }
        XCTAssertEqual(content.reviewed.first?.undoCountdown, 4)
    }

    func test_tickUndo_clearsAtZero() async {
        let vm = MailDayViewModel(variant: .populated)
        await vm.load()

        for _ in 0..<5 {
            vm.tickUndo()
        }
        guard case let .populated(content) = vm.state else {
            return XCTFail("Expected .populated")
        }
        XCTAssertNil(content.reviewed.first?.undoCountdown, "Should clear once seconds hit 0")
    }

    func test_tickUndo_noOpWhenNoCountdown() async {
        let vm = MailDayViewModel(variant: .empty)
        await vm.load()

        vm.tickUndo() // should not crash
        guard case .empty = vm.state else {
            return XCTFail("Tick on empty should leave .empty intact")
        }
    }

    // MARK: - Accept suggestion

    func test_acceptSuggestion_movesUnreviewedToReviewed() async {
        let vm = MailDayViewModel(variant: .populated)
        await vm.load()

        guard case let .populated(initial) = vm.state else {
            return XCTFail("Expected .populated")
        }
        let target = initial.unreviewed[0]
        vm.acceptSuggestion(for: target.id)

        guard case let .populated(updated) = vm.state else {
            return XCTFail("Expected .populated after accept")
        }
        XCTAssertEqual(updated.unreviewed.count, 1)
        XCTAssertEqual(updated.reviewed.count, 7)
        XCTAssertEqual(updated.reviewed.first?.id, target.id)
        XCTAssertEqual(updated.reviewed.first?.action, .routed)
        XCTAssertEqual(updated.reviewed.first?.undoCountdown, 5)
    }

    func test_acceptSuggestion_clearsPriorCountdown() async {
        let vm = MailDayViewModel(variant: .populated)
        await vm.load()

        guard case let .populated(initial) = vm.state else {
            return XCTFail("Expected .populated")
        }
        let target = initial.unreviewed[0]
        vm.acceptSuggestion(for: target.id)

        guard case let .populated(updated) = vm.state else {
            return XCTFail("Expected .populated after accept")
        }
        XCTAssertEqual(
            updated.reviewed.dropFirst().compactMap(\.undoCountdown),
            [],
            "Older rows lose their countdown when a newer action arrives"
        )
    }

    // MARK: - Empty frame

    func test_empty_projectsRecapAndNudges() async {
        let vm = MailDayViewModel(variant: .empty)
        await vm.load()

        guard case let .empty(content) = vm.state else {
            return XCTFail("Expected .empty, got \(vm.state)")
        }
        XCTAssertEqual(content.unreviewed.count, 0)
        XCTAssertEqual(content.reviewed.count, 0)
        XCTAssertEqual(content.streakDays, 12)
        XCTAssertEqual(content.lastScanLabel, "9h ago")
        XCTAssertEqual(content.yesterdayRecap?.segments.count, 4)
        XCTAssertEqual(content.setupNudges.count, 2)
    }

    // MARK: - Scan callback

    func test_requestScan_invokesCallback() async {
        var scanCalls = 0
        let vm = MailDayViewModel(variant: .empty) {
            scanCalls += 1
        }
        await vm.load()

        vm.requestScan()
        vm.requestScan()
        XCTAssertEqual(scanCalls, 2)
    }
}
