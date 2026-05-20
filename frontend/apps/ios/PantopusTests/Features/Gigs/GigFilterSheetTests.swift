//
//  GigFilterSheetTests.swift
//  PantopusTests
//
//  P5.3 — Contract tests for the Gig filter sheet. Covers the
//  criteria ↔ sections projection (build / parse round-trip,
//  active-count), the per-dimension predicates, and the feed
//  view-model applying a filter to the already-loaded gigs.
//

import SwiftUI
import UIKit
import XCTest
@testable import Pantopus

@MainActor
final class GigFilterSheetTests: XCTestCase {
    // MARK: - Criteria ↔ sections

    func testDefaultCriteriaHasNoActiveFilters() {
        XCTAssertEqual(GigFilterCriteria().activeCount, 0)
    }

    func testSectionsCoverEveryDimensionInOrder() {
        let ids = GigFilterCriteria().sections().map(\.id)
        XCTAssertEqual(ids, ["category", "budget", "schedule", "openToBids", "postedWithin"])
    }

    func testCategoryChipsExcludeAllSentinel() {
        guard case let .chipGroup(options, _)? = GigFilterCriteria().sections().first(where: { $0.id == "category" })?.control
        else { return XCTFail("Expected a category chip group") }
        XCTAssertFalse(options.contains { $0.id == GigsCategory.all.rawValue })
        XCTAssertEqual(options.count, GigsCategory.allCases.count - 1)
    }

    func testParseRoundTripPreservesEverySelection() {
        let criteria = GigFilterCriteria(
            categories: [.handyman, .cleaning],
            budgetLower: 50,
            budgetUpper: 300,
            schedules: [.oneTime, .flexible],
            openToBids: true,
            postedWithin: .week
        )
        XCTAssertEqual(GigFilterCriteria(sections: criteria.sections()), criteria)
    }

    func testActiveCountCountsEachActiveDimension() {
        let criteria = GigFilterCriteria(
            categories: [.handyman],
            budgetLower: 50,
            budgetUpper: 300,
            schedules: [.oneTime],
            openToBids: true,
            postedWithin: .today
        )
        XCTAssertEqual(criteria.activeCount, 5)
    }

    // MARK: - Predicates

    func testBudgetCeilingIsOpenEnded() {
        let criteria = GigFilterCriteria(budgetLower: 100, budgetUpper: GigFilterCriteria.budgetMax)
        XCTAssertTrue(criteria.matchesBudget(1000), "upper at max should impose no ceiling")
        XCTAssertTrue(criteria.matchesBudget(100))
        XCTAssertFalse(criteria.matchesBudget(50), "below the lower handle")
    }

    func testBudgetExcludesUnpricedGigsWhenActive() {
        XCTAssertFalse(GigFilterCriteria(budgetLower: 50, budgetUpper: 300).matchesBudget(nil))
        XCTAssertTrue(GigFilterCriteria().matchesBudget(nil), "inactive budget passes everything")
    }

    func testScheduleMappingIsTolerant() {
        XCTAssertEqual(GigScheduleFilter.from(backendKey: "scheduled"), .oneTime)
        XCTAssertEqual(GigScheduleFilter.from(backendKey: "one_time"), .oneTime)
        XCTAssertEqual(GigScheduleFilter.from(backendKey: "Recurring"), .recurring)
        XCTAssertEqual(GigScheduleFilter.from(backendKey: "flexible"), .flexible)
        XCTAssertNil(GigScheduleFilter.from(backendKey: "mystery"))
        XCTAssertNil(GigScheduleFilter.from(backendKey: nil))
    }

    func testPostedWithinCutoffs() {
        let now = Date(timeIntervalSince1970: 1_700_000_000)
        XCTAssertNil(GigPostedWithin.anytime.cutoff(from: now))
        XCTAssertEqual(GigPostedWithin.today.cutoff(from: now), now.addingTimeInterval(-86_400))
        XCTAssertEqual(GigPostedWithin.week.cutoff(from: now), now.addingTimeInterval(-604_800))
    }

    // MARK: - Sheet construction

    func testSheetConstructsAndParsesOnApply() {
        var captured: GigFilterCriteria?
        let sheet = GigFilterSheet(
            criteria: GigFilterCriteria(categories: [.handyman]),
            onApply: { captured = $0 },
            onClose: {}
        )
        _ = UIHostingController(rootView: sheet)
        XCTAssertNil(captured, "onApply only fires on the shell's Apply button")
    }

    // MARK: - View-model integration

    private func makeAPI() -> APIClient {
        APIClient(environment: .current, session: SequencedURLProtocol.makeSession(), retryPolicy: .none)
    }

    private static let handymanGigJSON = """
    {
      "id": "g1", "title": "Hang shelves", "description": "Mount shelves.",
      "price": 60, "category": "handyman", "status": "open",
      "created_at": "2026-05-19T08:00:00Z", "user_id": "u1", "bid_count": 4,
      "distance_miles": 0.2
    }
    """

    private static let cleaningGigJSON = """
    {
      "id": "g2", "title": "Deep clean apartment", "description": "Kitchen + bath.",
      "price": 180, "category": "cleaning", "status": "open",
      "created_at": "2026-05-19T05:00:00Z", "user_id": "u2", "bid_count": 0,
      "distance_miles": 0.5
    }
    """

    private static func gigsJSON(_ rows: String...) -> String {
        "{\"gigs\":[\(rows.joined(separator: ","))],\"total\":\(rows.count)}"
    }

    func testApplyBudgetFilterNarrowsLoadedList() async {
        SequencedURLProtocol.reset()
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.gigsJSON(Self.handymanGigJSON, Self.cleaningGigJSON))
        ]
        let vm = GigsFeedViewModel(api: makeAPI())
        await vm.load()
        vm.applyFilters(GigFilterCriteria(budgetLower: 0, budgetUpper: 100))
        guard case let .loaded(rows) = vm.state else { return XCTFail("Expected .loaded, got \(vm.state)") }
        XCTAssertEqual(rows.map(\.id), ["g1"], "only the $60 gig survives a $0–$100 budget")
        XCTAssertEqual(vm.activeFilterCount, 1)
    }

    func testApplyCategoryFilterWithNoMatchesFallsToEmpty() async {
        SequencedURLProtocol.reset()
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.gigsJSON(Self.handymanGigJSON, Self.cleaningGigJSON))
        ]
        let vm = GigsFeedViewModel(api: makeAPI())
        await vm.load()
        vm.applyFilters(GigFilterCriteria(categories: [.tech]))
        guard case .empty = vm.state else { return XCTFail("Expected .empty when nothing matches, got \(vm.state)") }
    }

    func testResettingFiltersRestoresFullList() async {
        SequencedURLProtocol.reset()
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.gigsJSON(Self.handymanGigJSON, Self.cleaningGigJSON))
        ]
        let vm = GigsFeedViewModel(api: makeAPI())
        await vm.load()
        vm.applyFilters(GigFilterCriteria(categories: [.tech]))
        vm.applyFilters(GigFilterCriteria())
        guard case let .loaded(rows) = vm.state else { return XCTFail("Expected .loaded, got \(vm.state)") }
        XCTAssertEqual(rows.count, 2)
        XCTAssertEqual(vm.activeFilterCount, 0)
    }
}
