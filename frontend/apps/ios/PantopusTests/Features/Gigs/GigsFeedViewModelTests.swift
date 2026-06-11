//
//  GigsFeedViewModelTests.swift
//  PantopusTests
//
//  Covers the Gigs feed VM (T2.3): load → loaded/empty/error, category
//  chip + sort dropdown each drive a refetch, projection produces the
//  expected category enum and bid count.
//

import XCTest
@testable import Pantopus

@MainActor
final class GigsFeedViewModelTests: XCTestCase {
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

    private static let handymanGigJSON = """
    {
      "id": "g1",
      "title": "Hang 3 floating shelves in living room",
      "description": "Need 3 IKEA Lack shelves mounted on drywall.",
      "price": 60,
      "category": "handyman",
      "status": "open",
      "created_at": "2026-05-14T08:00:00Z",
      "user_id": "u1",
      "bid_count": 4,
      "distance_miles": 0.2
    }
    """

    private static let cleaningGigJSON = """
    {
      "id": "g2",
      "title": "Deep clean 2BR apartment",
      "description": "Kitchen, bath, baseboards, inside oven.",
      "price": 180,
      "category": "cleaning",
      "status": "open",
      "created_at": "2026-05-14T05:00:00Z",
      "user_id": "u2",
      "bid_count": 0,
      "distance_miles": 0.5
    }
    """

    private static func gigsJSON(_ rows: String...) -> String {
        "{\"gigs\":[\(rows.joined(separator: ","))],\"total\":\(rows.count)}"
    }

    func testLoadTransitionsLoadedWhenGigsReturned() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.gigsJSON(Self.handymanGigJSON, Self.cleaningGigJSON))
        ]
        let vm = GigsFeedViewModel(api: makeAPI())
        await vm.load()
        guard case let .loaded(rows) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(rows.count, 2)
        XCTAssertEqual(rows[0].category, .handyman)
        XCTAssertEqual(rows[0].bidCount, 4)
        XCTAssertEqual(rows[0].price, "$60")
        XCTAssertEqual(rows[1].category, .cleaning)
        XCTAssertEqual(rows[1].bidCount, 0, "zero-bid row keeps a 0 count so the view can swap to the 'Be the first' affordance")
    }

    func testLoadEmptyTransitionsEmptyWithRadiusHint() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.gigsJSON())]
        let vm = GigsFeedViewModel(api: makeAPI(), radiusMiles: 2)
        await vm.load()
        guard case let .empty(empty) = vm.state else {
            XCTFail("Expected .empty, got \(vm.state)")
            return
        }
        XCTAssertEqual(empty.radiusMiles, 2)
    }

    func testLoadFailureTransitionsError() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{}")]
        let vm = GigsFeedViewModel(api: makeAPI())
        await vm.load()
        guard case .error = vm.state else {
            XCTFail("Expected .error, got \(vm.state)")
            return
        }
    }

    func testSelectCategoryRefetches() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.gigsJSON(Self.handymanGigJSON, Self.cleaningGigJSON)),
            .status(200, body: Self.gigsJSON(Self.cleaningGigJSON))
        ]
        let vm = GigsFeedViewModel(api: makeAPI())
        await vm.load()
        await vm.selectCategory(.cleaning)
        XCTAssertEqual(vm.activeCategory, .cleaning)
        guard case let .loaded(rows) = vm.state else {
            XCTFail("Expected .loaded after category switch, got \(vm.state)")
            return
        }
        XCTAssertEqual(rows.count, 1)
        XCTAssertEqual(rows.first?.category, .cleaning)
    }

    func testSelectSortRefetches() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.gigsJSON(Self.handymanGigJSON)),
            .status(200, body: Self.gigsJSON(Self.cleaningGigJSON))
        ]
        let vm = GigsFeedViewModel(api: makeAPI())
        await vm.load()
        await vm.selectSort(.highestPay)
        XCTAssertEqual(vm.activeSort, .highestPay)
        guard case let .loaded(rows) = vm.state else {
            XCTFail("Expected .loaded after sort switch, got \(vm.state)")
            return
        }
        XCTAssertEqual(rows.first?.category, .cleaning)
    }

    // MARK: - P0 server-side filters

    /// Decompose the last captured request's query string.
    private func lastQueryItems() -> [String: String] {
        guard let url = SequencedURLProtocol.capturedRequests.last?.url,
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        else { return [:] }
        var items: [String: String] = [:]
        for item in components.queryItems ?? [] {
            items[item.name] = item.value
        }
        return items
    }

    func testApplyFiltersForwardsServerParamsAndRefetches() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.gigsJSON(Self.handymanGigJSON, Self.cleaningGigJSON)),
            .status(200, body: Self.gigsJSON(Self.handymanGigJSON))
        ]
        let vm = GigsFeedViewModel(api: makeAPI())
        await vm.load()
        await vm.applyFilters(GigFilterCriteria(
            budgetLower: 50,
            budgetUpper: 300,
            schedules: [.oneTime],
            openToBids: true
        ))
        let query = lastQueryItems()
        XCTAssertEqual(query["minPrice"], "50.0")
        XCTAssertEqual(query["maxPrice"], "300.0")
        XCTAssertEqual(query["pay_type"], "offers")
        XCTAssertEqual(query["schedule_type"], "scheduled", "Single one-time selection maps to the backend value.")
        XCTAssertEqual(vm.activeFilterCount, 3, "budget + schedule + open-to-bids each count once")
        guard case let .loaded(rows) = vm.state else {
            return XCTFail("Expected .loaded from the filtered refetch, got \(vm.state)")
        }
        XCTAssertEqual(rows.map(\.id), ["g1"], "Rows come straight from the server's filtered page.")
    }

    func testApplyFiltersOmitsServerParamsAtDefaults() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.gigsJSON(Self.handymanGigJSON)),
            .status(200, body: Self.gigsJSON(Self.handymanGigJSON))
        ]
        let vm = GigsFeedViewModel(api: makeAPI())
        await vm.load()
        await vm.applyFilters(GigFilterCriteria())
        let query = lastQueryItems()
        XCTAssertNil(query["minPrice"], "Untouched lower handle sends no minPrice.")
        XCTAssertNil(query["maxPrice"], "The $500+ ceiling is open-ended — no maxPrice.")
        XCTAssertNil(query["pay_type"])
        XCTAssertNil(query["schedule_type"])
    }

    func testMultiScheduleSelectionStaysClientSide() async {
        // Two schedule chips → no single schedule_type value exists, so
        // the param is omitted and the rows narrow client-side.
        let scheduledGig = """
        {
          "id": "g3", "title": "One-time pickup", "description": "Single run.",
          "price": 30, "category": "delivery", "status": "open",
          "created_at": "2026-06-09T08:00:00Z", "user_id": "u3",
          "schedule_type": "scheduled"
        }
        """
        let asapGig = """
        {
          "id": "g4", "title": "ASAP errand", "description": "Right now.",
          "price": 25, "category": "delivery", "status": "open",
          "created_at": "2026-06-09T08:00:00Z", "user_id": "u4",
          "schedule_type": "asap"
        }
        """
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.gigsJSON(scheduledGig, asapGig)),
            .status(200, body: Self.gigsJSON(scheduledGig, asapGig))
        ]
        let vm = GigsFeedViewModel(api: makeAPI())
        await vm.load()
        await vm.applyFilters(GigFilterCriteria(schedules: [.oneTime, .flexible]))
        XCTAssertNil(lastQueryItems()["schedule_type"], "Multi-select can't ride the single-value param.")
        guard case let .loaded(rows) = vm.state else {
            return XCTFail("Expected .loaded, got \(vm.state)")
        }
        XCTAssertEqual(rows.map(\.id), ["g3"], "asap row falls out client-side; scheduled row maps to one-time.")
    }

    func testSingleRecurringScheduleStaysClientSide() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.gigsJSON(Self.handymanGigJSON)),
            .status(200, body: Self.gigsJSON(Self.handymanGigJSON))
        ]
        let vm = GigsFeedViewModel(api: makeAPI())
        await vm.load()
        await vm.applyFilters(GigFilterCriteria(schedules: [.recurring]))
        XCTAssertNil(
            lastQueryItems()["schedule_type"],
            "recurring has no backend schedule_type value — it filters client-side."
        )
    }

    func testPostedWithinFiltersClientSide() async {
        // No backend posted-within param exists — the cutoff narrows the
        // fetched rows locally.
        let freshGig = """
        {
          "id": "g5", "title": "Fresh gig", "description": "Posted just now.",
          "price": 40, "category": "handyman", "status": "open",
          "created_at": "\(ISO8601DateFormatter().string(from: Date().addingTimeInterval(-600)))",
          "user_id": "u5"
        }
        """
        let staleGig = """
        {
          "id": "g6", "title": "Old gig", "description": "Posted weeks ago.",
          "price": 40, "category": "handyman", "status": "open",
          "created_at": "2026-04-01T00:00:00Z", "user_id": "u6"
        }
        """
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.gigsJSON(freshGig, staleGig)),
            .status(200, body: Self.gigsJSON(freshGig, staleGig))
        ]
        let vm = GigsFeedViewModel(api: makeAPI())
        await vm.load()
        await vm.applyFilters(GigFilterCriteria(postedWithin: .today))
        XCTAssertNil(lastQueryItems()["deadline"], "posted-within is not the deadline param.")
        guard case let .loaded(rows) = vm.state else {
            return XCTFail("Expected .loaded, got \(vm.state)")
        }
        XCTAssertEqual(rows.map(\.id), ["g5"])
    }
}
