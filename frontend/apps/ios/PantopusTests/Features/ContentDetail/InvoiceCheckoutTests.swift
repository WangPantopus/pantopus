//
//  InvoiceCheckoutTests.swift
//  PantopusTests
//
//  Block 3B — the invoice "Pay" CTA wires the shared `CheckoutCoordinator`:
//  create a PaymentIntent (`POST /api/payments/intent`) → present
//  PaymentSheet → re-read server state. These tests drive the success /
//  declined / canceled / intent-failure branches with a stub presenter +
//  `SequencedURLProtocol`, so the round-trip is exercised without the SDK.
//

import XCTest
@testable import Pantopus

@MainActor
final class InvoiceCheckoutTests: XCTestCase {
    override func setUp() {
        super.setUp()
        SequencedURLProtocol.reset()
    }

    private static let intentJSON = """
    {"clientSecret":"pi_secret_1","paymentIntentId":"pi_1","customer":"cus_1",\
    "ephemeralKey":"ek_1","publishableKey":"pk_test"}
    """

    private func makeAPI() -> APIClient {
        APIClient(
            environment: .current,
            session: SequencedURLProtocol.makeSession(),
            retryPolicy: .none
        )
    }

    private func makeVM(presenter: StubCheckoutPresenter) -> InvoiceDetailViewModel {
        InvoiceDetailViewModel(
            invoiceId: "inv-1",
            paid: false,
            checkout: CheckoutCoordinator(api: makeAPI(), presenter: presenter),
            checkoutRequest: CheckoutRequest(
                listingId: "listing-1",
                offerId: "offer-1"
            )
        )
    }

    /// checkout.paySuccess
    func testPayCompletesAndRefreshes() async {
        // Only the create-intent call hits the network; load() re-reads the
        // invoice from the fixture frame (no request).
        SequencedURLProtocol.sequence = [.status(201, body: Self.intentJSON)]
        let presenter = StubCheckoutPresenter()
        presenter.outcome = .completed
        let vm = makeVM(presenter: presenter)
        await vm.load()
        await vm.payNow()

        XCTAssertEqual(presenter.presentPaymentCallCount, 1)
        XCTAssertEqual(presenter.lastClientSecret, "pi_secret_1")
        XCTAssertEqual(presenter.lastPublishableKey, "pk_test")
        XCTAssertEqual(vm.paymentStatus, .paid)
        guard case .loaded = vm.state else {
            return XCTFail("Expected the invoice to re-load after payment, got \(vm.state)")
        }
    }

    /// checkout.payDeclined — card declined / SCA failed
    func testPayDeclinedSurfacesMessage() async {
        SequencedURLProtocol.sequence = [.status(201, body: Self.intentJSON)]
        let presenter = StubCheckoutPresenter()
        presenter.outcome = .failed(message: "Your card was declined.")
        let vm = makeVM(presenter: presenter)
        await vm.payNow()
        XCTAssertEqual(vm.paymentStatus, .declined(message: "Your card was declined."))
    }

    /// checkout.cancel — buyer dismissed the sheet
    func testPayCanceledLeavesInvoiceUnpaid() async {
        SequencedURLProtocol.sequence = [.status(201, body: Self.intentJSON)]
        let presenter = StubCheckoutPresenter()
        presenter.outcome = .canceled
        let vm = makeVM(presenter: presenter)
        await vm.payNow()
        XCTAssertEqual(vm.paymentStatus, .canceled)
    }

    /// Intent creation fails → never presents the sheet, surfaces an error.
    func testIntentFailureDoesNotPresentSheet() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{\"error\":\"boom\"}")]
        let presenter = StubCheckoutPresenter()
        let vm = makeVM(presenter: presenter)
        await vm.payNow()
        XCTAssertEqual(presenter.presentPaymentCallCount, 0)
        guard case .declined = vm.paymentStatus else {
            return XCTFail("Expected .declined, got \(vm.paymentStatus)")
        }
    }

    /// The coordinator maps a missing client secret to .failed (no present).
    func testCoordinatorRejectsEmptyClientSecret() async {
        let presenter = StubCheckoutPresenter()
        let coordinator = CheckoutCoordinator(api: makeAPI(), presenter: presenter)
        let outcome = await coordinator.present(PaymentIntentSheetParams(clientSecret: nil))
        XCTAssertEqual(presenter.presentPaymentCallCount, 0)
        guard case .failed = outcome else {
            return XCTFail("Expected .failed for a missing client secret, got \(outcome)")
        }
    }
}

/// Records `presentPayment` calls and returns a scripted outcome so the
/// checkout branches are unit-testable without the Stripe SDK.
@MainActor
private final class StubCheckoutPresenter: PaymentSheetPresenting {
    var outcome: PaymentSheetOutcome = .completed
    private(set) var presentPaymentCallCount = 0
    private(set) var lastClientSecret: String?
    private(set) var lastPublishableKey: String?

    func presentAddCard(
        setupIntentClientSecret _: String,
        customer _: String,
        ephemeralKey _: String,
        publishableKey _: String?
    ) async -> PaymentSheetOutcome {
        .completed
    }

    func presentPayment(
        clientSecret: String,
        customer _: String,
        ephemeralKey _: String,
        isSetupIntent _: Bool,
        publishableKey: String?
    ) async -> PaymentSheetOutcome {
        presentPaymentCallCount += 1
        lastClientSecret = clientSecret
        lastPublishableKey = publishableKey
        return outcome
    }
}
