//
//  TasksMapViewModelTests.swift
//  PantopusTests
//
//  A11.1 Tasks map view-model — load → populated / empty / error, the
//  live category filter, pin↔card selection, and client-side sort.
//

import XCTest
@testable import Pantopus

@MainActor
final class TasksMapViewModelTests: XCTestCase {
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

    func testLoadProducesPopulatedWithDefaultSelection() async {
        let vm = TasksMapViewModel()
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
        let vm = TasksMapViewModel(failWith: "Couldn't load tasks")
        await vm.load()
        XCTAssertEqual(errorMessage(vm.state), "Couldn't load tasks")
    }

    func testCategoryFilterNarrowsVisibleItems() async {
        let vm = TasksMapViewModel()
        await vm.load()
        vm.selectCategory(.cleaning)
        let visible = items(vm.state)
        XCTAssertEqual(visible?.count, 2)
        XCTAssertEqual(visible?.allSatisfy { $0.category == .cleaning }, true)
        // Selection follows the filter to a visible task.
        XCTAssertEqual(vm.selectedId, visible?.first?.id)
    }

    func testCategoryWithNoMatchesProducesEmpty() async {
        let vm = TasksMapViewModel()
        await vm.load()
        vm.selectCategory(.tech) // no tech task in the seed
        XCTAssertTrue(isEmpty(vm.state))
        XCTAssertNil(vm.selectedId)
    }

    func testWidenFromEmptyRestoresPopulated() async {
        let vm = TasksMapViewModel()
        await vm.load()
        vm.selectCategory(.tech)
        XCTAssertTrue(isEmpty(vm.state))
        vm.selectCategory(.all) // "Widen search"
        XCTAssertEqual(items(vm.state)?.count, TasksMapSampleData.items.count)
    }

    func testSelectUpdatesSelectedId() async {
        let vm = TasksMapViewModel()
        await vm.load()
        vm.select("cleaning-1")
        XCTAssertEqual(vm.selectedId, "cleaning-1")
    }

    func testSortFewestBidsOrdersAscending() async {
        let vm = TasksMapViewModel()
        await vm.load()
        vm.selectSort(.fewestBids)
        let bids = items(vm.state)?.map(\.bidCount) ?? []
        XCTAssertEqual(bids, bids.sorted())
    }

    func testSortHighestPayLeadsWithPriciestTask() async {
        let vm = TasksMapViewModel()
        await vm.load()
        vm.selectSort(.highestPay)
        XCTAssertEqual(items(vm.state)?.first?.id, "cleaning-1") // $180
    }

    func testInitialCategoryAppliedOnLoad() async {
        let vm = TasksMapViewModel(initialCategory: .petcare)
        await vm.load()
        let visible = items(vm.state)
        XCTAssertEqual(visible?.count, 2)
        XCTAssertEqual(visible?.allSatisfy { $0.category == .petcare }, true)
        XCTAssertEqual(vm.activeCategory, .petcare)
    }

    func testPinProjectionCarriesCategoryColorAndState() async {
        let vm = TasksMapViewModel()
        await vm.load()
        let pins = items(vm.state)?.map(\.pin) ?? []
        XCTAssertEqual(pins.count, TasksMapSampleData.items.count)
        // The moving + tutoring seeds are pending; the rest confirmed.
        let pending = pins.filter { $0.state == .pending }.count
        XCTAssertEqual(pending, 2)
    }
}
