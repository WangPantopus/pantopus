//
//  PaymentsViewModelTests.swift
//  PantopusTests
//
//  Asserts the A14.6 Payments view-model projects the populated and
//  empty fixtures end-to-end: balance hero present/absent, methods
//  count, Stripe Connect chip swap, payouts gating, activity
//  collapse, and the destructive-card gate.
//

import XCTest
@testable import Pantopus

@MainActor
final class PaymentsViewModelTests: XCTestCase {
    func testLoadPopulatedProjectsAllSections() async {
        let vm = PaymentsViewModel(seed: .populated)
        await vm.load()
        guard case let .loaded(loaded) = vm.state else {
            XCTFail("Expected .loaded for populated seed")
            return
        }

        // Balance hero present with payout footer payload.
        XCTAssertNotNil(loaded.balance)
        XCTAssertEqual(loaded.balance?.overline, "Available to pay out")
        XCTAssertEqual(loaded.balance?.amount, "124.50")
        XCTAssertEqual(loaded.balance?.frequencyPill, "Weekly")

        // Three methods, first is default.
        XCTAssertEqual(loaded.methods.count, 3)
        XCTAssertEqual(loaded.methods.first?.brand, .visa)
        XCTAssertEqual(loaded.methods.first?.chip?.label, "Default")
        XCTAssertEqual(loaded.methods.first?.chip?.tone, .primary)
        XCTAssertEqual(loaded.methods.last?.brand, .applePay)

        // Stripe connected — success chip, schedule row present.
        if case let .chipChevron(label, tone) = loaded.payouts.stripe.trailing {
            XCTAssertEqual(label, "Connected")
            XCTAssertEqual(tone, .success)
        } else {
            XCTFail("Stripe row should show success chip + chevron")
        }
        XCTAssertNotNil(loaded.payouts.payoutSchedule)
        XCTAssertEqual(loaded.payouts.payoutSchedule?.subtext, "Weekly · Mondays")

        // Tax info: on-file chip.
        if case let .chipChevron(label, tone) = loaded.payouts.taxInfo.trailing {
            XCTAssertEqual(label, "On file")
            XCTAssertEqual(tone, .success)
        } else {
            XCTFail("Tax row should show on-file chip + chevron")
        }

        // Activity: 3 stat rows.
        if case let .stats(stats) = loaded.activity {
            XCTAssertEqual(stats.count, 3)
            XCTAssertEqual(stats[0].label, "Lifetime")
            XCTAssertEqual(stats[1].label, "Year to date")
            XCTAssertEqual(stats[2].label, "Last payout")
        } else {
            XCTFail("Activity should be stats[]")
        }

        XCTAssertTrue(loaded.canCloseAccount, "Populated frame surfaces the destructive card")
    }

    func testLoadEmptyHidesHeroAndGatesPayoutRows() async {
        let vm = PaymentsViewModel(seed: .empty)
        await vm.load()
        guard case let .loaded(loaded) = vm.state else {
            XCTFail("Expected .loaded for empty seed")
            return
        }

        // No hero, no methods, no schedule row.
        XCTAssertNil(loaded.balance, "Empty frame omits the balance hero")
        XCTAssertTrue(loaded.methods.isEmpty)
        XCTAssertNil(loaded.payouts.payoutSchedule, "Schedule row gates behind Stripe Connect")

        // Stripe shows the primary CTA chip.
        if case let .ctaChip(label, tone) = loaded.payouts.stripe.trailing {
            XCTAssertEqual(label, "Connect")
            XCTAssertEqual(tone, .primary)
        } else {
            XCTFail("Empty Stripe row should expose a Connect CTA chip")
        }

        // Payout method + tax info gated.
        XCTAssertEqual(loaded.payouts.payoutMethod.trailing, .gatedDash)
        XCTAssertEqual(loaded.payouts.taxInfo.trailing, .gatedDash)

        // Activity collapses to the empty row.
        if case let .empty(title, _) = loaded.activity {
            XCTAssertEqual(title, "No transactions yet")
        } else {
            XCTFail("Activity should collapse to the empty row")
        }

        XCTAssertFalse(loaded.canCloseAccount, "Empty frame hides the destructive card")
    }

    func testInitialStateIsLoading() {
        let vm = PaymentsViewModel(seed: .populated)
        if case .loading = vm.state { return }
        XCTFail("VM should start in .loading until load() runs")
    }
}
