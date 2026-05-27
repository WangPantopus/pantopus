//
//  CeremonialVariantsProjectionTests.swift
//  PantopusTests
//
//  A17.5–A17.8 — Projection tests for the four new ceremonial variant
//  payloads (Coupon / Gig / Memory / Package). Mirrors
//  `MailDetailVariantsTests` for the booklet/certified families: drives
//  the static `project(detail:now:)` projection with hand-rolled
//  `mail.object` payloads and asserts the per-variant DTO is decoded.
//

import XCTest
@testable import Pantopus

@MainActor
final class CeremonialVariantsProjectionTests: XCTestCase {
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

    func testCouponProjectionDecodesHeadline() async {
        let body = """
        {
          "mail": {
            "id": "m1",
            "type": "coupon",
            "mail_type": "coupon",
            "display_title": "25% OFF",
            "preview_text": null,
            "subject": null,
            "sender_business_name": "Brass Owl Bakery",
            "sender_address": null,
            "content": null,
            "viewed": false,
            "archived": false,
            "starred": false,
            "tags": [],
            "priority": "normal",
            "attachments": null,
            "ack_required": false,
            "created_at": "2026-05-15T12:00:00Z",
            "object": {
              "headline": "25% OFF",
              "subcopy": "Next purchase",
              "code": "BRASS25",
              "expires_at": "2026-06-30",
              "merchant": "Brass Owl Bakery",
              "minimum_spend": "$8 minimum"
            }
          }
        }
        """
        SequencedURLProtocol.sequence = [.status(200, body: body)]
        let vm = MailDetailViewModel(mailId: "m1", api: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            XCTFail("Expected loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(content.category, .coupon)
        XCTAssertNotNil(content.couponDetail)
        XCTAssertEqual(content.couponDetail?.headline, "25% OFF")
        XCTAssertEqual(content.couponDetail?.code, "BRASS25")
        XCTAssertNil(content.gigDetail)
        XCTAssertNil(content.memoryDetail)
        XCTAssertNil(content.packageDetail)
    }

    func testCouponPayloadMissingHeadlineLeavesNil() throws {
        let body = """
        {
          "mail": {
            "id": "m1",
            "type": "coupon",
            "mail_type": "coupon",
            "display_title": "Untitled",
            "sender_business_name": "Sender",
            "viewed": false, "archived": false, "starred": false,
            "tags": [], "priority": "normal", "ack_required": false,
            "created_at": "2026-05-15T12:00:00Z",
            "object": {"merchant": "Brass Owl"}
          }
        }
        """
        let response = try JSONDecoder().decode(MailDetailResponse.self, from: Data(body.utf8))
        let content = MailDetailViewModel.project(detail: response.mail)
        XCTAssertNil(content.couponDetail)
    }

    // MARK: - Gig

    func testGigProjectionDecodesBidPostBidder() async {
        let body = """
        {
          "mail": {
            "id": "m1",
            "type": "gig",
            "mail_type": "gig",
            "display_title": "New bid",
            "sender_business_name": "Marcus T.",
            "viewed": false, "archived": false, "starred": false,
            "tags": [], "priority": "normal", "ack_required": false,
            "created_at": "2026-05-15T12:00:00Z",
            "object": {
              "is_accepted": false,
              "bidder": {"name": "Marcus T.", "rating": 4.9, "jobs": 47},
              "bid": {"amount": 65, "unit": "flat", "eta": "Saturday", "expires": "in 22h"},
              "post": {"title": "Sofa move", "category": "Moving", "budget": "$40-80"},
              "other_bids": [
                {"who": "Devon R.", "amount": 55, "rating": 4.7, "jobs": 18}
              ]
            }
          }
        }
        """
        SequencedURLProtocol.sequence = [.status(200, body: body)]
        let vm = MailDetailViewModel(mailId: "m1", api: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            XCTFail("Expected loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(content.category, .gig)
        XCTAssertNotNil(content.gigDetail)
        XCTAssertEqual(content.gigDetail?.bid.amount, 65)
        XCTAssertEqual(content.gigDetail?.otherBids.count, 1)
        XCTAssertFalse(content.gigDetail?.isAccepted ?? true)
    }

    // MARK: - Memory

    func testMemoryProjectionDecodesKeepsake() async {
        let body = """
        {
          "mail": {
            "id": "m1",
            "type": "memory",
            "mail_type": "memory",
            "display_title": "A year ago",
            "sender_business_name": "Mei L.",
            "viewed": false, "archived": false, "starred": false,
            "tags": [], "priority": "normal", "ack_required": false,
            "created_at": "2026-05-15T12:00:00Z",
            "object": {
              "title": "One year ago, you found Pepper.",
              "reference": "MEM-0518",
              "is_saved": false,
              "elf_fresh": {"headline": "Pantopus surfaced this", "summary": "Anniversary release."},
              "elf_saved": {"headline": "Kept in your Vault", "summary": "Filed in Memories."},
              "vault": {
                "trail": [{"glyph": "inbox", "label": "Mailbox"}, {"glyph": "heart", "label": "Memories"}],
                "stats": [{"value": "12", "label": "Items"}]
              }
            }
          }
        }
        """
        SequencedURLProtocol.sequence = [.status(200, body: body)]
        let vm = MailDetailViewModel(mailId: "m1", api: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            XCTFail("Expected loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(content.category, .memory)
        XCTAssertNotNil(content.memoryDetail)
        XCTAssertEqual(content.memoryDetail?.title, "One year ago, you found Pepper.")
        XCTAssertFalse(content.memoryDetail?.isSaved ?? true)
    }

    func testMemoryMissingPresentationBlocksLeavesNil() async {
        let body = """
        {
          "mail": {
            "id": "m1",
            "type": "memory",
            "mail_type": "memory",
            "display_title": "Bare memory",
            "sender_business_name": "Sender",
            "viewed": false, "archived": false, "starred": false,
            "tags": [], "priority": "normal", "ack_required": false,
            "created_at": "2026-05-15T12:00:00Z",
            "object": {"title": "no elf/vault"}
          }
        }
        """
        SequencedURLProtocol.sequence = [.status(200, body: body)]
        let vm = MailDetailViewModel(mailId: "m1", api: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            XCTFail("Expected loaded")
            return
        }
        XCTAssertNil(content.memoryDetail)
    }

    // MARK: - Package

    func testPackageProjectionDecodesCarrierAndTracking() async {
        let body = """
        {
          "mail": {
            "id": "m1",
            "type": "package",
            "mail_type": "package",
            "display_title": "Package en route",
            "sender_business_name": "Lerina Books",
            "viewed": false, "archived": false, "starred": false,
            "tags": [], "priority": "normal", "ack_required": false,
            "created_at": "2026-05-15T12:00:00Z",
            "object": {
              "carrier": "USPS Priority Mail",
              "tracking_number": "9505 5125",
              "status": "out_for_delivery",
              "eta_line": "ETA 1-3 PM"
            }
          }
        }
        """
        SequencedURLProtocol.sequence = [.status(200, body: body)]
        let vm = MailDetailViewModel(mailId: "m1", api: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            XCTFail("Expected loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(content.category, .package)
        XCTAssertNotNil(content.packageDetail)
        XCTAssertEqual(content.packageDetail?.carrier, "USPS Priority Mail")
        XCTAssertEqual(content.packageDetail?.status, .outForDelivery)
        XCTAssertEqual(content.packageDetail?.trackingNumber, "9505 5125")
        // Tracking timeline is derived when the backend omits it.
        XCTAssertFalse(content.packageDetail?.trackingSteps.isEmpty ?? true)
    }

    func testPackagePayloadWithoutCarrierLeavesNil() async {
        let body = """
        {
          "mail": {
            "id": "m1",
            "type": "package",
            "mail_type": "package",
            "display_title": "Bare package",
            "sender_business_name": "Sender",
            "viewed": false, "archived": false, "starred": false,
            "tags": [], "priority": "normal", "ack_required": false,
            "created_at": "2026-05-15T12:00:00Z",
            "object": {"status": "in_transit"}
          }
        }
        """
        SequencedURLProtocol.sequence = [.status(200, body: body)]
        let vm = MailDetailViewModel(mailId: "m1", api: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            XCTFail("Expected loaded")
            return
        }
        XCTAssertNil(content.packageDetail)
    }

    // MARK: - Cross-category guards

    func testNonGigCategoryNeverDecodesGig() async {
        let body = """
        {
          "mail": {
            "id": "m1",
            "type": "notice",
            "mail_type": "notice",
            "display_title": "Notice",
            "sender_business_name": "City",
            "viewed": false, "archived": false, "starred": false,
            "tags": [], "priority": "normal", "ack_required": false,
            "created_at": "2026-05-15T12:00:00Z",
            "object": {
              "bidder": {"name": "Marcus"},
              "bid": {"amount": 65},
              "post": {"title": "Sofa"}
            }
          }
        }
        """
        SequencedURLProtocol.sequence = [.status(200, body: body)]
        let vm = MailDetailViewModel(mailId: "m1", api: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            XCTFail("Expected loaded")
            return
        }
        XCTAssertNil(content.gigDetail)
    }
}
