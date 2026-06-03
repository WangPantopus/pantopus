//
//  TasksMapViewModelTests.swift
//  PantopusTests
//
//  A11.1 Tasks map view-model — the live `GET /api/gigs/in-bounds` fetch
//  + projection, plus the client-side category filter, pin↔card
//  selection, and sort that run on the fetched (or seeded) set. The
//  filter/sort/selection tests drive an explicit `seed` (offline mode);
//  the fetch tests drive a stubbed `APIClient`.
//

import XCTest
@testable import Pantopus

@MainActor
final class TasksMapViewModelTests: XCTestCase {
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

    private func seeded(_ initialCategory: GigsCategory = .all) -> TasksMapViewModel {
        TasksMapViewModel(initialCategory: initialCategory, seed: TasksMapSampleData.items)
    }

    private func items(_ state: TasksMapState) -> [TaskMapItem]? {
        if case let .populated(items) = state { return items }
        return nil
    }

    private func isEmpty(_ state: TasksMapState) -> Bool {
        if case .empty = state { return true }
        return false
    }

    private func errorMessage(_ state: TasksMapState) -> String? {
        if case let .error(message) = state { return message }
        return nil
    }

    // MARK: - Live in-bounds fetch

    func testLoadFetchesInBoundsAndProjectsPins() async {
        // Two gigs near the default anchor (40.7484, -73.9857); g1 sits
        // closer so closest-sort (the default) leads with it.
        SequencedURLProtocol.sequence = [
            .status(200, body: """
            {"gigs":[
              {"id":"g1","title":"Fix a leaky sink","category":"handyman","price":60,
               "pay_type":"fixed","bid_count":4,"status":"open",
               "latitude":40.749,"longitude":-73.988},
              {"id":"g2","title":"Walk my dog","category":"pet","price":22,
               "pay_type":"per_walk","bid_count":1,"status":"open",
               "latitude":40.740,"longitude":-73.978}
            ]}
            """)
        ]
        let vm = TasksMapViewModel(api: makeAPI())
        await vm.load()
        let visible = items(vm.state)
        XCTAssertEqual(visible?.count, 2)
        XCTAssertEqual(visible?.first?.id, "g1", "Closest task leads + pulses")
        XCTAssertEqual(vm.selectedId, "g1")
        XCTAssertEqual(visible?.first?.category, .handyman)
        XCTAssertEqual(visible?.first?.price, "$60")
        XCTAssertEqual(visible?.first(where: { $0.id == "g2" })?.price, "$22/walk")
        XCTAssertEqual(visible?.first(where: { $0.id == "g2" })?.category, .petcare)
        // Distance was computed client-side, not "—".
        XCTAssertNotEqual(visible?.first?.distanceLabel, "—")
    }

    func testLoadDropsGigsWithoutCoordinates() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: """
            {"gigs":[
              {"id":"g1","title":"Has coords","category":"handyman","price":50,"status":"open",
               "latitude":40.749,"longitude":-73.988},
              {"id":"g2","title":"No coords","category":"cleaning","price":50,"status":"open"}
            ]}
            """)
        ]
        let vm = TasksMapViewModel(api: makeAPI())
        await vm.load()
        XCTAssertEqual(items(vm.state)?.map(\.id), ["g1"])
    }

    func testLoadEmptyResultProducesEmpty() async {
        SequencedURLProtocol.sequence = [.status(200, body: "{\"gigs\":[]}")]
        let vm = TasksMapViewModel(api: makeAPI())
        await vm.load()
        XCTAssertTrue(isEmpty(vm.state))
        XCTAssertNil(vm.selectedId)
    }

    func testLoadServerErrorProducesError() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{\"error\":\"boom\"}")]
        let vm = TasksMapViewModel(api: makeAPI())
        await vm.load()
        XCTAssertNotNil(errorMessage(vm.state))
    }

    // MARK: - Offline seed: states + selection

    func testLoadProducesPopulatedWithDefaultSelection() async {
        let vm = seeded()
        await vm.load()
        XCTAssertEqual(items(vm.state)?.count, TasksMapSampleData.items.count)
        // Closest-first by default → the 0.2 mi handyman task leads + pulses.
        XCTAssertEqual(vm.selectedId, items(vm.state)?.first?.id)
        XCTAssertEqual(vm.selectedId, "handyman-1")
    }

    func testLoadEmptySeedProducesEmpty() async {
        let vm = TasksMapViewModel(seed: [])
        await vm.load()
        XCTAssertTrue(isEmpty(vm.state))
        XCTAssertNil(vm.selectedId)
    }

    func testLoadFailureProducesError() async {
        let vm = TasksMapViewModel(seed: TasksMapSampleData.items, failWith: "Couldn't load tasks")
        await vm.load()
        XCTAssertEqual(errorMessage(vm.state), "Couldn't load tasks")
    }

    func testCategoryFilterNarrowsVisibleItems() async {
        let vm = seeded()
        await vm.load()
        vm.selectCategory(.cleaning)
        let visible = items(vm.state)
        XCTAssertEqual(visible?.count, 2)
        XCTAssertEqual(visible?.allSatisfy { $0.category == .cleaning }, true)
        // Selection follows the filter to a visible task.
        XCTAssertEqual(vm.selectedId, visible?.first?.id)
    }

    func testCategoryWithNoMatchesProducesEmpty() async {
        let vm = seeded()
        await vm.load()
        vm.selectCategory(.tech) // no tech task in the seed
        XCTAssertTrue(isEmpty(vm.state))
        XCTAssertNil(vm.selectedId)
    }

    func testWidenFromEmptyRestoresPopulated() async {
        let vm = seeded()
        await vm.load()
        vm.selectCategory(.tech)
        XCTAssertTrue(isEmpty(vm.state))
        vm.selectCategory(.all) // "Widen search"
        XCTAssertEqual(items(vm.state)?.count, TasksMapSampleData.items.count)
    }

    func testSelectUpdatesSelectedId() async {
        let vm = seeded()
        await vm.load()
        vm.select("cleaning-1")
        XCTAssertEqual(vm.selectedId, "cleaning-1")
    }

    func testSortFewestBidsOrdersAscending() async {
        let vm = seeded()
        await vm.load()
        vm.selectSort(.fewestBids)
        let bids = items(vm.state)?.map(\.bidCount) ?? []
        XCTAssertEqual(bids, bids.sorted())
    }

    func testSortHighestPayLeadsWithPriciestTask() async {
        let vm = seeded()
        await vm.load()
        vm.selectSort(.highestPay)
        XCTAssertEqual(items(vm.state)?.first?.id, "cleaning-1") // $180
    }

    func testInitialCategoryAppliedOnLoad() async {
        let vm = seeded(.petcare)
        await vm.load()
        let visible = items(vm.state)
        XCTAssertEqual(visible?.count, 2)
        XCTAssertEqual(visible?.allSatisfy { $0.category == .petcare }, true)
        XCTAssertEqual(vm.activeCategory, .petcare)
    }

    func testPinProjectionCarriesCategoryColorAndState() async {
        let vm = seeded()
        await vm.load()
        let pins = items(vm.state)?.map(\.pin) ?? []
        XCTAssertEqual(pins.count, TasksMapSampleData.items.count)
        // The moving + tutoring seeds are pending; the rest confirmed.
        let pending = pins.filter { $0.state == .pending }.count
        XCTAssertEqual(pending, 2)
    }
}
