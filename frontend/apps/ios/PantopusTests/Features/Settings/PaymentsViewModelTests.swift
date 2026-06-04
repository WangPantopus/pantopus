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

    // MARK: - Live path (Phase 3 / 3A)

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

    private struct CardFixture {
        let id: String
        let brand: String
        let last4: String
        let expMonth: Int
        let expYear: Int
        let isDefault: Bool
    }

    private static func cardJSON(_ card: CardFixture) -> String {
        """
        {"id":"\(card.id)","payment_method_type":"card","card_brand":"\(card.brand)",
         "card_last4":"\(card.last4)","card_exp_month":\(card.expMonth),
         "card_exp_year":\(card.expYear),"is_default":\(card.isDefault)}
        """
    }

    private static func methodsResponse(_ cards: String...) -> String {
        "{\"paymentMethods\":[\(cards.joined(separator: ","))]}"
    }

    private static let defaultVisa = CardFixture(
        id: "pm_1",
        brand: "visa",
        last4: "4242",
        expMonth: 3,
        expYear: 2027,
        isDefault: true
    )

    private static let alternateMastercard = CardFixture(
        id: "pm_2",
        brand: "mastercard",
        last4: "4444",
        expMonth: 11,
        expYear: 2026,
        isDefault: false
    )

    private static let defaultMastercard = CardFixture(
        id: "pm_2",
        brand: "mastercard",
        last4: "4444",
        expMonth: 11,
        expYear: 2026,
        isDefault: true
    )

    private static let alternateVisa = CardFixture(
        id: "pm_1",
        brand: "visa",
        last4: "4242",
        expMonth: 3,
        expYear: 2027,
        isDefault: false
    )

    private static let methodsJSON = methodsResponse(
        cardJSON(defaultVisa),
        cardJSON(alternateMastercard)
    )

    private static let addCardParamsJSON = """
    {"setupIntent":"seti_123_secret_abc","ephemeralKey":"ek_test_123","customer":"cus_123","publishableKey":"pk_test_x"}
    """

    func testLiveLoadProjectsRealMethods() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.methodsJSON)]
        let vm = PaymentsViewModel(api: makeAPI(), sheetPresenter: StubPaymentSheetPresenter())
        await vm.load()
        guard case let .loaded(loaded) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(loaded.methods.count, 2)
        XCTAssertEqual(loaded.methods[0].brand, .visa)
        XCTAssertEqual(loaded.methods[0].label, "Visa •• 4242")
        XCTAssertEqual(loaded.methods[0].subtext, "Expires 03/27")
        XCTAssertEqual(loaded.methods[0].chip?.label, "Default")
        XCTAssertEqual(loaded.methods[1].brand, .mastercard)
        XCTAssertNil(loaded.methods[1].chip, "Only the default method carries a chip")
        // Live frame never fabricates a balance — Payouts/Connect land in 3C.
        XCTAssertNil(loaded.balance)
        if case let .ctaChip(label, _) = loaded.payouts.stripe.trailing {
            XCTAssertEqual(label, "Connect")
        } else {
            XCTFail("Live frame should expose the Stripe Connect CTA")
        }
    }

    func testLiveLoadEmptyMethods() async {
        SequencedURLProtocol.sequence = [.status(200, body: "{\"paymentMethods\":[]}")]
        let vm = PaymentsViewModel(api: makeAPI(), sheetPresenter: StubPaymentSheetPresenter())
        await vm.load()
        guard case let .loaded(loaded) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return
        }
        XCTAssertTrue(loaded.methods.isEmpty)
    }

    func testLiveLoadFailureTransitionsError() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{}")]
        let vm = PaymentsViewModel(api: makeAPI(), sheetPresenter: StubPaymentSheetPresenter())
        await vm.load()
        guard case .error = vm.state else {
            XCTFail("Expected .error, got \(vm.state)")
            return
        }
    }

    func testAddCardCompletedRefreshesMethods() async {
        // load (empty) → add-card params → reload (now one card).
        SequencedURLProtocol.sequence = [
            .status(200, body: "{\"paymentMethods\":[]}"),
            .status(200, body: Self.addCardParamsJSON),
            .status(200, body: Self.methodsJSON)
        ]
        let presenter = StubPaymentSheetPresenter()
        presenter.addCardOutcome = .completed
        let vm = PaymentsViewModel(api: makeAPI(), sheetPresenter: presenter)
        await vm.load()
        await vm.tapAddMethod()
        XCTAssertEqual(presenter.presentAddCardCallCount, 1)
        guard case let .loaded(loaded) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(loaded.methods.count, 2, "Completed add-card refreshes from the backend")
        XCTAssertNil(vm.actionError)
    }

    func testAddCardCanceledDoesNotRefresh() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: "{\"paymentMethods\":[]}"),
            .status(200, body: Self.addCardParamsJSON)
        ]
        let presenter = StubPaymentSheetPresenter()
        presenter.addCardOutcome = .canceled
        let vm = PaymentsViewModel(api: makeAPI(), sheetPresenter: presenter)
        await vm.load()
        await vm.tapAddMethod()
        guard case let .loaded(loaded) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return
        }
        XCTAssertTrue(loaded.methods.isEmpty, "Cancel leaves the list untouched")
        XCTAssertNil(vm.actionError)
    }

    func testSetDefaultOptimisticThenReconcile() async {
        // load → PUT default → reload (pm_2 now default).
        let reorderedJSON = Self.methodsResponse(
            Self.cardJSON(Self.defaultMastercard),
            Self.cardJSON(Self.alternateVisa)
        )
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.methodsJSON),
            .status(200, body: "{\"message\":\"ok\"}"),
            .status(200, body: reorderedJSON)
        ]
        let vm = PaymentsViewModel(api: makeAPI(), sheetPresenter: StubPaymentSheetPresenter())
        await vm.load()
        await vm.setDefault("pm_2")
        guard case let .loaded(loaded) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return
        }
        let defaultMethod = loaded.methods.first { $0.chip?.tone == .primary }
        XCTAssertEqual(defaultMethod?.id, "pm_2")
    }

    func testSetDefaultFailureRevertsAndSurfacesError() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.methodsJSON),
            .status(500, body: "{\"error\":\"boom\"}")
        ]
        let vm = PaymentsViewModel(api: makeAPI(), sheetPresenter: StubPaymentSheetPresenter())
        await vm.load()
        await vm.setDefault("pm_2")
        guard case let .loaded(loaded) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return
        }
        // Reverted: pm_1 is still the default.
        XCTAssertEqual(loaded.methods.first { $0.chip?.tone == .primary }?.id, "pm_1")
        XCTAssertNotNil(vm.actionError)
    }

    func testRemoveMethodOptimisticThenReconcile() async {
        let afterRemovalJSON = Self.methodsResponse(
            Self.cardJSON(Self.defaultVisa)
        )
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.methodsJSON),
            .status(200, body: "{\"message\":\"ok\"}"),
            .status(200, body: afterRemovalJSON)
        ]
        let vm = PaymentsViewModel(api: makeAPI(), sheetPresenter: StubPaymentSheetPresenter())
        await vm.load()
        await vm.removeMethod("pm_2")
        guard case let .loaded(loaded) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(loaded.methods.count, 1)
        XCTAssertEqual(loaded.methods.first?.id, "pm_1")
    }
}

/// Records presentation calls and returns a scripted outcome so the
/// view-model's add-card branch is unit-testable without the Stripe SDK.
@MainActor
private final class StubPaymentSheetPresenter: PaymentSheetPresenting {
    var addCardOutcome: PaymentSheetOutcome = .completed
    private(set) var presentAddCardCallCount = 0

    func presentAddCard(
        setupIntentClientSecret _: String,
        customer _: String,
        ephemeralKey _: String
    ) async -> PaymentSheetOutcome {
        presentAddCardCallCount += 1
        return addCardOutcome
    }

    func presentPayment(
        clientSecret _: String,
        customer _: String,
        ephemeralKey _: String,
        isSetupIntent _: Bool
    ) async -> PaymentSheetOutcome {
        .completed
    }
}
