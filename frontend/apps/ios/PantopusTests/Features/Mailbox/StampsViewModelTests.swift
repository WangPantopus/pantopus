//
//  StampsViewModelTests.swift
//  PantopusTests
//
//  A17.11 — state-projection coverage for the Stamps view-model. Asserts
//  the populated + empty frames project off the sample fixtures, the book
//  balance maths line up, and the buy-CTA stubs mutate local state (refill
//  the book / acquire the starter book) per the brief.
//

import XCTest
@testable import Pantopus

@MainActor
final class StampsViewModelTests: XCTestCase {
    // MARK: - Initial / loading

    func test_initialState_isLoading() {
        let vm = StampsViewModel(seed: .populated)
        guard case .loading = vm.state else {
            return XCTFail("Expected .loading before load(), got \(vm.state)")
        }
    }

    // MARK: - Populated frame

    func test_load_populated_projectsContent() async {
        let vm = StampsViewModel(seed: .populated)
        await vm.load()

        guard case let .loaded(content) = vm.state else {
            return XCTFail("Expected .loaded, got \(vm.state)")
        }
        XCTAssertEqual(content.book.total, 12)
        XCTAssertEqual(content.book.used, 4)
        XCTAssertEqual(content.book.remaining, 8)
        XCTAssertEqual(content.wallet.count, 4)
        XCTAssertEqual(content.usage.count, 4)
        XCTAssertEqual(content.insights.count, 3)
        XCTAssertEqual(content.trust, .verified)
        XCTAssertEqual(content.categoryLabel, "Stamps")
    }

    func test_book_remainingFraction() {
        let book = StampsSampleData.populated.book
        XCTAssertEqual(book.remainingFraction, 8.0 / 12.0, accuracy: 0.0001)
    }

    // MARK: - Empty frame

    func test_load_empty_projectsEmpty() async {
        let vm = StampsViewModel(seed: .empty)
        await vm.load()

        guard case let .empty(content) = vm.state else {
            return XCTFail("Expected .empty, got \(vm.state)")
        }
        XCTAssertEqual(content.headline, "No stamps yet")
        XCTAssertEqual(content.starterBook.priceLabel, "$4.80")
    }

    // MARK: - Buy stubs (local state, no Stripe)

    func test_buyMore_refillsTheBook() async {
        let vm = StampsViewModel(seed: .populated)
        await vm.load()

        vm.buyMore()
        guard case let .loaded(content) = vm.state else {
            return XCTFail("Expected .loaded after buyMore")
        }
        XCTAssertEqual(content.book.used, 0, "Buying more refills the featured book")
        XCTAssertEqual(content.book.remaining, content.book.total)
    }

    func test_buyMore_noOpWhenEmpty() async {
        let vm = StampsViewModel(seed: .empty)
        await vm.load()

        vm.buyMore() // should not crash or change the empty frame
        guard case .empty = vm.state else {
            return XCTFail("buyMore on empty should leave .empty intact")
        }
    }

    func test_purchaseStarterBook_flipsEmptyToPopulated() async {
        let vm = StampsViewModel(seed: .empty)
        await vm.load()
        guard case .empty = vm.state else { return XCTFail("Expected .empty start") }

        vm.purchaseStarterBook()
        guard case let .loaded(content) = vm.state else {
            return XCTFail("Expected .loaded after acquiring the starter book")
        }
        XCTAssertEqual(content.book.total, 12)
    }

    // MARK: - Navigation

    func test_tapBack_invokesCallback() {
        var backs = 0
        let vm = StampsViewModel(seed: .populated) { backs += 1 }
        vm.tapBack()
        XCTAssertEqual(backs, 1)
    }
}
