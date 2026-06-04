//
//  WalletPayoutTests.swift
//  PantopusTests
//
//  Block 3C — the wallet payout side: withdraw earned funds + Stripe Connect
//  onboarding / dashboard. Drives the live `WalletViewModel` with
//  `SequencedURLProtocol` + a stub web presenter so the round-trips are
//  exercised without the SDK or a device.
//

import XCTest
@testable import Pantopus

@MainActor
final class WalletPayoutTests: XCTestCase {
    override func setUp() {
        super.setUp()
        SequencedURLProtocol.reset()
    }

    // MARK: - Fixtures

    private static let balanceJSON = #"{"wallet":{"id":"w1","balance":84750,"currency":"usd","frozen":false}}"#
    private static let txJSON = #"{"transactions":[],"total":0}"#
    private static let pendingJSON =
        #"{"in_review_cents":0,"releasing_soon_cents":0,"total_pending_cents":0,"in_review_count":0,"releasing_soon_count":0}"#
    private static let connectEnabledJSON =
        #"{"account":{"stripe_account_id":"acct_1","charges_enabled":true,"payouts_enabled":true,"details_submitted":true}}"#
    private static let withdrawOkJSON =
        #"{"success":true,"message":"$847.50 withdrawal initiated.","#
            + #""transaction":{"id":"wtx1","type":"withdrawal","amount":84750,"status":"completed"}}"#

    /// Live-fetch reads 4 endpoints in order: balance, transactions,
    /// pending-release, connect/account.
    private static func liveLoad(payoutsEnabled: Bool) -> [SequencedURLProtocol.Response] {
        [
            .status(200, body: balanceJSON),
            .status(200, body: txJSON),
            .status(200, body: pendingJSON),
            payoutsEnabled ? .status(200, body: connectEnabledJSON) : .status(404, body: "{}"),
        ]
    }

    private func makeAPI() -> APIClient {
        APIClient(environment: .current, session: SequencedURLProtocol.makeSession(), retryPolicy: .none)
    }

    private func makeVM(presenter: StubConnectPresenter = StubConnectPresenter()) -> WalletViewModel {
        WalletViewModel(api: makeAPI(), connectPresenter: presenter)
    }

    // MARK: - Gating

    func testLoadReflectsPayoutsEnabled() async {
        SequencedURLProtocol.sequence = Self.liveLoad(payoutsEnabled: true)
        let vm = makeVM()
        await vm.load()
        guard case let .populated(content) = vm.state else {
            return XCTFail("Expected .populated, got \(vm.state)")
        }
        XCTAssertTrue(content.payoutsEnabled, "Connected account with payouts_enabled → Withdraw gate open")
        XCTAssertEqual(content.available, "847.50")
    }

    func testLoadWithoutConnectAccountGatesPayouts() async {
        SequencedURLProtocol.sequence = Self.liveLoad(payoutsEnabled: false)
        let vm = makeVM()
        await vm.load()
        guard case let .populated(content) = vm.state else {
            return XCTFail("Expected .populated, got \(vm.state)")
        }
        XCTAssertFalse(content.payoutsEnabled, "No connected account → 'Set up payouts' gate")
    }

    // MARK: - Withdraw

    func testWithdrawSucceedsAndRefreshes() async {
        SequencedURLProtocol.sequence =
            Self.liveLoad(payoutsEnabled: true)
                + [.status(200, body: Self.withdrawOkJSON)]
                + Self.liveLoad(payoutsEnabled: true) // post-withdraw refresh
        let vm = makeVM()
        await vm.load()
        await vm.withdraw()
        XCTAssertEqual(vm.action, .withdrawSucceeded(message: "$847.50 withdrawal initiated."))
        guard case .populated = vm.state else {
            return XCTFail("Expected the wallet to re-load after withdraw, got \(vm.state)")
        }
    }

    func testWithdrawBlockedWhenPayoutsDisabled() async {
        SequencedURLProtocol.sequence = Self.liveLoad(payoutsEnabled: false)
        let vm = makeVM()
        await vm.load()
        await vm.withdraw()
        guard case .withdrawFailed = vm.action else {
            return XCTFail("Withdraw must be gated when payouts are disabled, got \(vm.action)")
        }
    }

    func testWithdrawSurfacesServerError() async {
        SequencedURLProtocol.sequence =
            Self.liveLoad(payoutsEnabled: true)
                + [.status(400, body: #"{"error":"Insufficient balance"}"#)]
        let vm = makeVM()
        await vm.load()
        await vm.withdraw()
        guard case .withdrawFailed = vm.action else {
            return XCTFail("Expected .withdrawFailed, got \(vm.action)")
        }
    }

    // MARK: - Connect onboarding + dashboard

    func testSetupPayoutsOpensOnboardingAndRefreshes() async {
        let presenter = StubConnectPresenter()
        SequencedURLProtocol.sequence =
            [
                .status(201, body: #"{"stripeAccountId":"acct_1"}"#), // create/ensure account
                .status(200, body: #"{"onboardingUrl":"https://connect.stripe.com/setup/x","expiresAt":123}"#),
            ]
                + Self.liveLoad(payoutsEnabled: true) // refresh after return
        let vm = makeVM(presenter: presenter)
        await vm.setupPayouts()
        XCTAssertEqual(presenter.presentedURLs.map(\.absoluteString), ["https://connect.stripe.com/setup/x"])
        guard case let .populated(content) = vm.state else {
            return XCTFail("Expected refreshed wallet after onboarding, got \(vm.state)")
        }
        XCTAssertTrue(content.payoutsEnabled, "Returning from onboarding flips the gate when payouts are enabled")
    }

    func testOpenDashboardPresentsExpressLink() async {
        let presenter = StubConnectPresenter()
        SequencedURLProtocol.sequence = [
            .status(200, body: #"{"dashboardUrl":"https://connect.stripe.com/express/x"}"#),
        ]
        let vm = makeVM(presenter: presenter)
        await vm.openDashboard()
        XCTAssertEqual(presenter.presentedURLs.map(\.absoluteString), ["https://connect.stripe.com/express/x"])
    }
}

/// Records the Stripe-hosted URLs the view-model asks to present, so the
/// onboarding / dashboard branches are testable without SafariServices.
@MainActor
private final class StubConnectPresenter: ConnectWebPresenting {
    private(set) var presentedURLs: [URL] = []

    func present(url: URL) async {
        presentedURLs.append(url)
    }
}
