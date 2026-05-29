//
//  ContentDetailProjectionTests.swift
//  PantopusTests
//
//  Covers the T2.6 / A09 ContentDetail projection across every designed
//  state: gig V2 (populated + no-bids), gig V1 (populated + awarded),
//  listing (populated + sold), invoice (due + paid). Also asserts the
//  GigDetailSampleData / ListingDetailSampleData frames carry the
//  signature design elements (two-stop card, bid tags, sold stamp,
//  receipt capsule).
//

import XCTest
@testable import Pantopus

@MainActor
final class ContentDetailProjectionTests: XCTestCase {
    // MARK: - Gig V2 (Task)

    func testTaskV2ProjectionFillsStatusHeroBidsAndTwoStop() {
        let gig = makeGig(GigSpec(
            title: "Move queen mattress + frame",
            description: "Queen mattress plus metal bed frame, disassembled.",
            price: 85,
            category: "moving",
            status: "open",
            isV2: true,
            pickupAddress: "712 Maplewood, Apt 2B",
            dropoffAddress: "209 Cedar Ave, Apt 7",
            bidCount: 6,
            distanceMiles: 0.6
        ))
        let bids = (1...6).map { makeBid(id: "b\($0)", userId: "u\($0)", amount: Double(50 + $0 * 5), name: "Bidder \($0)") }
        let content = GigDetailViewModel.project(gig: gig, bids: bids)
        XCTAssertEqual(content.kind, .gig)
        XCTAssertEqual(content.statusPill?.label, "Open · 6 bids")
        XCTAssertEqual(content.statusPill?.tone, .warning)
        XCTAssertEqual(content.hero.categoryChip?.category, .moving)
        XCTAssertEqual(content.dock.primary.label, "Place bid")
        XCTAssertTrue(content.dock.primary.enabled)
        // Pickup → drop-off two-stop card is the V2 signature.
        let twoStop = content.modules.compactMap { mod -> ContentDetailTwoStop? in
            if case let .twoStop(m) = mod { return m } else { return nil }
        }.first
        XCTAssertEqual(twoStop?.stops.count, 2)
        XCTAssertEqual(twoStop?.stops.first?.tone, .primary)
        XCTAssertEqual(twoStop?.stops.last?.tone, .success)
        XCTAssertTrue(content.modules.contains { if case .bids = $0 { true } else { false } })
    }

    func testTaskV2NoBidsRendersBeFirstCallout() {
        let gig = makeGig(GigSpec(
            title: "Move queen mattress + frame",
            price: 85,
            category: "moving",
            status: "open",
            isV2: true,
            bidCount: 0
        ))
        let content = GigDetailViewModel.project(gig: gig, bids: [])
        XCTAssertEqual(content.statusPill?.label, "Open · No bids yet")
        XCTAssertFalse(content.modules.contains { if case .bids = $0 { true } else { false } })
        let callout = content.modules.compactMap { mod -> ContentDetailCallout? in
            if case let .callout(m) = mod { return m } else { return nil }
        }.first
        XCTAssertEqual(callout?.style, .empty)
        XCTAssertEqual(callout?.title, "Be the first to bid")
    }

    // MARK: - Gig V1 (legacy)

    func testGigV1ProjectionIsSparse() {
        let gig = makeGig(GigSpec(
            title: "Dog walk · 45 min",
            description: "Walk Biscuit.",
            price: 22,
            category: "petcare",
            status: "open",
            isV2: false,
            bidCount: 3
        ))
        let bids = (1...3).map { makeBid(id: "b\($0)", userId: "u\($0)", amount: Double(18 + $0 * 2), name: "Bidder \($0)") }
        let content = GigDetailViewModel.project(gig: gig, bids: bids)
        XCTAssertEqual(content.statusPill?.label, "Open")
        XCTAssertNil(content.hero.categoryChip, "V1 has no category chip")
        XCTAssertTrue(content.statStrip.isEmpty, "V1 has no stat strip")
        XCTAssertTrue(content.trustCapsules.isEmpty, "V1 has no trust capsules")
        XCTAssertTrue(content.dock.primary.enabled)
        XCTAssertEqual(content.dock.primary.label, "Place bid")
    }

    func testGigV1AwardedDimsLosersAndLocksDock() {
        let gig = makeGig(GigSpec(
            title: "Dog walk · 45 min",
            price: 22,
            category: "petcare",
            status: "accepted",
            isV2: false,
            acceptedBy: "u1",
            bidCount: 3
        ))
        let bids = [
            makeBid(id: "b1", userId: "u1", amount: 20, name: "Tomás G."),
            makeBid(id: "b2", userId: "u2", amount: 22, name: "Rae N."),
            makeBid(id: "b3", userId: "u3", amount: 25, name: "Carla W.")
        ]
        let content = GigDetailViewModel.project(gig: gig, bids: bids)
        XCTAssertEqual(content.statusPill?.label, "Awarded")
        XCTAssertEqual(content.statusPill?.tone, .success)
        XCTAssertEqual(content.hero.priceCaption, "winning bid")
        // Award banner callout.
        XCTAssertTrue(content.modules.contains {
            if case let .callout(m) = $0 { m.identifier == "awarded" } else { false }
        })
        // Winner highlighted, losers dimmed + struck.
        let bidsModule = content.modules.compactMap { mod -> ContentDetailBidsModule? in
            if case let .bids(m) = mod { return m } else { return nil }
        }.first
        XCTAssertEqual(bidsModule?.sub, "closed")
        XCTAssertEqual(bidsModule?.bids.first?.won, true)
        XCTAssertEqual(bidsModule?.bids.dropFirst().allSatisfy(\.dimmed), true)
        // Dock primary disabled with lock.
        XCTAssertFalse(content.dock.primary.enabled)
        XCTAssertEqual(content.dock.primary.label, "Bidding closed")
        XCTAssertEqual(content.dock.primary.icon, .lock)
    }

    // MARK: - Listing

    func testListingProjectionCarriesCoverInlinePillsAndOfferDock() {
        let listing = makeListing(ListingSpec(
            title: "Mid-century sofa", price: 320, condition: "like_new", locationName: "West Adams", distanceMeters: 644
        ))
        let content = ListingDetailViewModel.project(listing)
        XCTAssertEqual(content.kind, .listing)
        XCTAssertNotNil(content.cover)
        XCTAssertEqual(content.cover?.sold, false)
        XCTAssertEqual(content.cover?.glassActions, [.share, .bookmark])
        XCTAssertEqual(content.hero.priceLine, "$320")
        XCTAssertFalse(content.hero.priceStrikethrough)
        XCTAssertTrue(content.hero.inlinePills.contains { $0.label == "Like new" })
        XCTAssertEqual(content.dock.primary.label, "Make offer")
    }

    func testListingSoldDesaturatesStrikesPriceAndPivotsDock() {
        let listing = makeListing(ListingSpec(
            id: "l3", title: "Bianchi", price: 410, condition: "good", status: "sold", soldAt: "2025-12-14T10:00:00Z"
        ))
        let content = ListingDetailViewModel.project(listing)
        XCTAssertEqual(content.cover?.sold, true)
        XCTAssertEqual(content.statusPill?.label, "Sold")
        XCTAssertEqual(content.statusPill?.tone, .error)
        XCTAssertTrue(content.hero.priceStrikethrough)
        XCTAssertEqual(content.dock.secondary?.label, "Seller")
        XCTAssertEqual(content.dock.primary.label, "Find similar")
        XCTAssertTrue(content.modules.contains {
            if case let .callout(m) = $0 { m.identifier == "alert-similar" } else { false }
        })
    }

    func testListingFreeRendersFreePrice() {
        let listing = makeListing(ListingSpec(
            id: "l2", title: "Moving boxes", price: 0, isFree: true, category: "free_stuff", layer: "goods"
        ))
        let content = ListingDetailViewModel.project(listing)
        XCTAssertEqual(content.hero.priceLine, "Free")
        XCTAssertTrue(content.hero.inlinePills.contains { $0.label == "Free" })
    }

    // MARK: - Invoice

    func testInvoiceDueCarriesTotalHeroLineItemsAndPayDock() {
        let content = InvoiceDetailViewModel.fixture(invoiceId: "INV-00318")
        XCTAssertEqual(content.kind, .invoice)
        XCTAssertEqual(content.statusPill?.label, "Due in 7 days")
        XCTAssertEqual(content.hero.priceLine, "$642.85", "total hero must be present")
        XCTAssertEqual(content.hero.priceCaption, "total · USD")
        XCTAssertFalse(content.hero.priceCheckDisc)
        XCTAssertTrue(content.hero.monoId?.contains("INV-00318") == true)
        // Line items carry the fees + total footer.
        let items = content.modules.compactMap { mod -> ContentDetailLineItems? in
            if case let .lineItems(m) = mod { return m } else { return nil }
        }.first
        XCTAssertEqual(items?.fees.count, 3)
        XCTAssertEqual(items?.totalValue, "$642.85")
        XCTAssertEqual(items?.totalTone, .primary)
        XCTAssertTrue(content.modules.contains { if case .fromTo = $0 { true } else { false } })
        XCTAssertNil(content.dock.secondary, "due invoice dock is full-width")
        XCTAssertTrue(content.dock.primary.label.contains("Pay"))
    }

    func testInvoicePaidRecolorsTotalAddsReceiptAndPivotsDock() {
        let content = InvoiceDetailViewModel.paidFixture(invoiceId: "INV-00318")
        XCTAssertEqual(content.statusPill?.label, "Paid · Dec 14")
        XCTAssertEqual(content.statusPill?.tone, .success)
        XCTAssertEqual(content.hero.priceTone, .success)
        XCTAssertTrue(content.hero.priceCheckDisc)
        XCTAssertEqual(content.hero.priceTrailingLabel, "paid in full")
        // Pantopus Pay receipt capsule.
        XCTAssertTrue(content.modules.contains {
            if case let .callout(m) = $0 { m.identifier == "pantopus-pay-receipt" } else { false }
        })
        let items = content.modules.compactMap { mod -> ContentDetailLineItems? in
            if case let .lineItems(m) = mod { return m } else { return nil }
        }.first
        XCTAssertEqual(items?.totalLabel, "Paid")
        XCTAssertEqual(items?.totalTone, .success)
        XCTAssertEqual(content.dock.secondary?.label, "Share")
        XCTAssertEqual(content.dock.primary.label, "Download receipt")
    }

    // MARK: - Sample frames

    func testSampleFramesCarrySignatureElements() {
        // V2 populated: two-stop + tagged bids + trust ratings.
        let v2 = GigDetailSampleData.taskV2Populated
        XCTAssertEqual(v2.statusPill?.label, "Open · 6 bids")
        XCTAssertTrue(v2.modules.contains { if case .twoStop = $0 { true } else { false } })
        XCTAssertTrue(v2.modules.contains { if case .photoStrip = $0 { true } else { false } })
        let v2Bids = v2.modules.compactMap { mod -> ContentDetailBidsModule? in
            if case let .bids(m) = mod { return m } else { return nil }
        }.first
        XCTAssertEqual(v2Bids?.bids.first?.tag, "fastest reply")
        let trustRow = v2.modules.compactMap { mod -> ContentDetailCapsuleRow? in
            if case let .capsuleRow(m) = mod { return m } else { return nil }
        }.first
        XCTAssertTrue(trustRow?.capsules.contains { $0.label == "5.0★ rating" } ?? false)

        // Awarded sample.
        let awarded = GigDetailSampleData.gigV1Awarded
        XCTAssertEqual(awarded.statusPill?.label, "Awarded")
        XCTAssertFalse(awarded.dock.primary.enabled)

        // Listing sold sample.
        let sold = ListingDetailSampleData.sold
        XCTAssertEqual(sold.cover?.sold, true)
        XCTAssertEqual(sold.hero.saleTag, "Sold for $385")

        // Invoice paid sample.
        let paid = InvoiceDetailViewModel.paidFixture(invoiceId: "INV-00318")
        XCTAssertEqual(paid.hero.priceTone, .success)
    }

    // MARK: - Fixtures

    /// Test fixture spec — keeps `makeGig` to a single parameter (under
    /// SwiftLint's function_parameter_count threshold) while letting each
    /// test set only the fields it cares about.
    private struct GigSpec {
        var id = "g1"
        var title = "Task"
        var description: String?
        var price: Double?
        var category: String?
        var status: String?
        var isV2: Bool?
        var acceptedBy: String?
        var pickupAddress: String?
        var dropoffAddress: String?
        var bidCount: Int?
        var distanceMiles: Double?
    }

    private func makeGig(_ spec: GigSpec) -> GigDTO {
        GigDTO(
            id: spec.id,
            title: spec.title,
            description: spec.description,
            price: spec.price,
            category: spec.category,
            status: spec.status,
            createdAt: nil,
            deadline: nil,
            isUrgent: false,
            tags: nil,
            userId: "owner",
            acceptedBy: spec.acceptedBy,
            acceptedAt: spec.acceptedBy == nil ? nil : "2025-11-14T17:30:00Z",
            scheduledStart: nil,
            paymentStatus: nil,
            engagementMode: nil,
            scheduleType: nil,
            payType: nil,
            taskArchetype: nil,
            isV2: spec.isV2,
            pickupAddress: spec.pickupAddress,
            dropoffAddress: spec.dropoffAddress,
            bidCount: spec.bidCount,
            savedByUser: false,
            distanceMiles: spec.distanceMiles,
            latitude: nil,
            longitude: nil,
            approxLocation: nil,
            creator: GigCreator(id: "owner", username: "hana", name: "Hana O.", profilePictureUrl: nil, verified: true)
        )
    }

    private func makeBid(id: String, userId: String, amount: Double, name: String) -> GigBidDTO {
        GigBidDTO(
            id: id,
            userId: userId,
            bidAmount: amount,
            amount: nil,
            status: "pending",
            message: nil,
            createdAt: nil,
            bidder: GigCreator(id: userId, username: userId, name: name, profilePictureUrl: nil, verified: true)
        )
    }

    private struct ListingSpec {
        var id = "l1"
        var title = "Listing"
        var price: Double?
        var isFree = false
        var category = "furniture"
        var condition: String?
        var status = "active"
        var layer = "goods"
        var locationName: String?
        var distanceMeters: Double?
        var soldAt: String?
    }

    private func makeListing(_ spec: ListingSpec) -> ListingDTO {
        ListingDTO(
            id: spec.id,
            userId: "u1",
            title: spec.title,
            description: "Sample description.",
            price: spec.price,
            isFree: spec.isFree,
            category: spec.category,
            condition: spec.condition,
            status: spec.status,
            mediaUrls: ["https://example.com/a.jpg", "https://example.com/b.jpg"],
            firstImage: "https://example.com/a.jpg",
            layer: spec.layer,
            listingType: "sell_item",
            latitude: nil,
            longitude: nil,
            locationName: spec.locationName,
            distanceMeters: spec.distanceMeters,
            createdAt: nil,
            userHasSaved: false,
            approxLocation: nil,
            viewCount: nil,
            activeOfferCount: nil,
            soldAt: spec.soldAt,
            archivedAt: nil
        )
    }
}
