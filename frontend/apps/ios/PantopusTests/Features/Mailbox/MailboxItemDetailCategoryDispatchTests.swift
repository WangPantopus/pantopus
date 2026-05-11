//
//  MailboxItemDetailCategoryDispatchTests.swift
//  PantopusTests
//
//  Asserts that the P18 category-aware projection wires up the right
//  payload, trust pill, sender flags, and CTA gate state for each of
//  Coupon / Booklet / Certified.
//

import XCTest
@testable import Pantopus

@MainActor
final class MailboxItemDetailCategoryDispatchTests: XCTestCase {
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

    // MARK: - Coupon

    private static let couponJSON = """
    {"mail":{
      "id":"m-coupon","type":"coupon","mail_type":"coupon",
      "created_at":"2026-04-30T10:00:00Z",
      "sender_display":"Whole Foods","sender_trust":"unverified",
      "display_title":"30% off this week","tags":[],
      "object_payload":{
        "headline":"30% OFF",
        "subcopy":"at any participating Whole Foods",
        "code":"PANTO30",
        "expires_at":"2026-05-31",
        "merchant":"Whole Foods Market",
        "fine_print":"One per customer."
      }
    }}
    """

    func testCouponDispatchProjectsCouponPayload() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.couponJSON)]
        let vm = MailboxItemDetailViewModel(mailId: "m-coupon", api: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            XCTFail("Expected loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(content.category, .coupon)
        guard case let .coupon(coupon) = content.payload else {
            XCTFail("Expected .coupon payload, got \(content.payload)")
            return
        }
        XCTAssertEqual(coupon.headline, "30% OFF")
        XCTAssertFalse(content.sender.showStamp)
        // Code and merchant should land in keyFacts.
        XCTAssertTrue(content.keyFacts.contains { $0.label == "Code" })
        XCTAssertTrue(content.keyFacts.contains { $0.label == "Merchant" })
    }

    // MARK: - Booklet

    private static let bookletJSON = """
    {"mail":{
      "id":"m-booklet","type":"booklet","mail_type":"booklet",
      "created_at":"2026-04-30T10:00:00Z",
      "sender_display":"REI","sender_trust":"verified_business","display_title":"Spring catalog",
      "tags":[],
      "object_payload":{
        "pages":[
          "https://example.com/p1.png",
          "https://example.com/p2.png"
        ],
        "summary":"Spring catalog","page_count":24
      }
    }}
    """

    func testBookletDispatchProjectsBookletPayload() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.bookletJSON)]
        let vm = MailboxItemDetailViewModel(mailId: "m-booklet", api: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            XCTFail("Expected loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(content.category, .booklet)
        guard case let .booklet(booklet) = content.payload else {
            XCTFail("Expected .booklet payload, got \(content.payload)")
            return
        }
        XCTAssertEqual(booklet.pages.count, 2)
        XCTAssertEqual(booklet.pageCount, 24)
        XCTAssertNil(content.aiElf)
        XCTAssertFalse(content.sender.showStamp)
    }

    // MARK: - Certified

    private static let certifiedJSON = """
    {"mail":{
      "id":"m-cert","type":"certified","mail_type":"certified",
      "created_at":"2026-05-08T10:00:00Z",
      "sender_display":"Cambridge District Court","sender_trust":"verified_gov",
      "display_title":"Court summons","tags":[],
      "object_payload":{
        "reference_number":"CRT-2026-0091",
        "document_type":"Court summons",
        "acknowledge_by":"2026-05-25",
        "chain":[
          {"id":"sent","label":"Sent","occurred_at":"2026-05-08"},
          {"id":"delivered","label":"Delivered","occurred_at":"2026-05-10"},
          {"id":"ack","label":"Acknowledged"}
        ],
        "notice_body":"You are summoned.",
        "is_acknowledged":false
      }
    }}
    """

    func testCertifiedDispatchProjectsCertifiedPayloadAndStamp() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.certifiedJSON)]
        let vm = MailboxItemDetailViewModel(mailId: "m-cert", api: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            XCTFail("Expected loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(content.category, .certified)
        XCTAssertEqual(content.trust, .certifiedChain)
        XCTAssertTrue(content.sender.showStamp)
        XCTAssertNotNil(content.aiElf)
        XCTAssertEqual(content.timeline.count, 3)
        XCTAssertTrue(content.keyFacts.contains { $0.label == "Reference #" })
        guard case .certified = content.payload else {
            XCTFail("Expected .certified payload")
            return
        }
    }

    func testCertifiedPrimaryActionGatedOnAckCheckbox() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.certifiedJSON)]
        let vm = MailboxItemDetailViewModel(mailId: "m-cert", api: makeAPI())
        await vm.load()

        // Without checking the gate, the primary action must no-op.
        XCTAssertFalse(vm.certifiedAckChecked)
        await vm.performPrimaryAction()
        XCTAssertFalse(vm.ctaFlags.primaryCompleted, "Primary CTA fired without ack — should be gated.")

        // After checking, the primary action should fire and call the
        // V2 item-action endpoint with `acknowledge`.
        SequencedURLProtocol.sequence = [
            .status(200, body: "{\"message\":\"Action 'acknowledge' recorded\",\"action\":\"acknowledge\"}")
        ]
        vm.certifiedAckChecked = true
        await vm.performPrimaryAction()
        XCTAssertTrue(vm.ctaFlags.primaryCompleted)
        // Last captured request must hit /api/mailbox/v2/item/m-cert/action.
        let lastPath = SequencedURLProtocol.capturedRequests.last?.url?.path
        XCTAssertEqual(lastPath, "/api/mailbox/v2/item/m-cert/action")
    }
}
