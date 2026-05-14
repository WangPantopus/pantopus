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
}
