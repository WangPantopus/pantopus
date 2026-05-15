//
//  ContentDetailProjectionTests.swift
//  PantopusTests
//
//  Covers the T2.6 ContentDetail projection: gig with bids, listing
//  with cover + condition trust capsule, invoice fixture with line
//  items + summary + full-width Pay dock.
//

import XCTest
@testable import Pantopus

@MainActor
final class ContentDetailProjectionTests: XCTestCase {
    // MARK: - Gig

    func testGigProjectionFillsStatusHeroAndBids() {
        let gig = GigDTO(
            id: "g1",
            title: "Hang 3 shelves",
            description: "Three IKEA Lack shelves on drywall.",
            price: 60,
            category: "handyman",
            status: "open",
            createdAt: nil,
            deadline: nil,
            isUrgent: false,
            tags: [],
            userId: "u1",
            acceptedBy: nil,
            acceptedAt: nil,
            scheduledStart: nil,
            paymentStatus: nil,
            engagementMode: nil,
            scheduleType: nil,
            payType: nil,
            taskArchetype: nil,
            pickupAddress: "Rose Court, Unit 4B",
            dropoffAddress: nil,
            bidCount: 4,
            savedByUser: false,
            distanceMiles: 0.2,
            latitude: nil,
            longitude: nil,
            approxLocation: nil,
            creator: nil
        )
        let bids: [GigBidDTO] = (1...4).map { i in
            GigBidDTO(
                id: "b\(i)",
                userId: "u\(i)",
                bidAmount: Double(50 + i * 5),
                amount: nil,
                status: "pending",
                message: nil,
                createdAt: nil,
                bidder: GigCreator(
                    id: "u\(i)", username: "u\(i)", name: "Bidder \(i)",
                    profilePictureUrl: nil, verified: true
                )
            )
        }
        let content = GigDetailViewModel.project(gig: gig, bids: bids)
        XCTAssertEqual(content.kind, .gig)
        XCTAssertEqual(content.statusPill?.label, "Open · 4 bids")
        XCTAssertEqual(content.hero.title, "Hang 3 shelves")
        XCTAssertEqual(content.hero.priceLine, "$60")
        XCTAssertEqual(content.hero.priceCaption, "budget")
        XCTAssertEqual(content.hero.categoryChip?.category, .handyman)
        XCTAssertFalse(content.trustCapsules.isEmpty)
        XCTAssertEqual(content.dock.secondary?.label, "Message")
        XCTAssertEqual(content.dock.primary.label, "Place bid")
        // Bid module rendered
        let bidsModule = content.modules.compactMap { mod -> ContentDetailBidsModule? in
            if case let .bids(m) = mod { return m } else { return nil }
        }.first
        XCTAssertEqual(bidsModule?.bids.count, 4)
        XCTAssertEqual(bidsModule?.bids.first?.amount, "$55")
    }

    func testGigProjectionWithoutBidsHidesBidModule() {
        let gig = GigDTO(
            id: "g2",
            title: "Walk Lily Tue/Thu",
            description: nil,
            price: 22,
            category: "petcare",
            status: "open",
            createdAt: nil,
            deadline: nil,
            isUrgent: false,
            tags: nil,
            userId: "u1",
            acceptedBy: nil,
            acceptedAt: nil,
            scheduledStart: nil,
            paymentStatus: nil,
            engagementMode: nil,
            scheduleType: nil,
            payType: "per_walk",
            taskArchetype: nil,
            pickupAddress: nil,
            dropoffAddress: nil,
            bidCount: 0,
            savedByUser: nil,
            distanceMiles: nil,
            latitude: nil,
            longitude: nil,
            approxLocation: nil,
            creator: nil
        )
        let content = GigDetailViewModel.project(gig: gig, bids: [])
        XCTAssertEqual(content.statusPill?.label, "Open")
        XCTAssertEqual(content.hero.priceLine, "$22 / walk")
        let bidsModule = content.modules.compactMap { mod -> ContentDetailBidsModule? in
            if case let .bids(m) = mod { return m } else { return nil }
        }.first
        XCTAssertNil(bidsModule, "zero-bid gigs hide the bids section")
    }

    // MARK: - Listing

    func testListingProjectionCarriesCoverAndConditionTrustCapsule() {
        let listing = ListingDTO(
            id: "l1",
            userId: "u1",
            title: "Mid-century sofa",
            description: "Walnut frame, original cushions.",
            price: 320,
            isFree: false,
            category: "furniture",
            condition: "like_new",
            status: "active",
            mediaUrls: ["https://example.com/sofa.jpg"],
            firstImage: "https://example.com/sofa.jpg",
            layer: "goods",
            listingType: "sell_item",
            latitude: nil,
            longitude: nil,
            locationName: "West Adams",
            distanceMeters: 644,
            createdAt: nil,
            userHasSaved: false,
            approxLocation: nil
        )
        let content = ListingDetailViewModel.project(listing)
        XCTAssertEqual(content.kind, .listing)
        XCTAssertNotNil(content.cover, "listing variant must carry a cover slot")
        XCTAssertEqual(content.hero.priceLine, "$320")
        XCTAssertNotNil(content.counterparty)
        XCTAssertTrue(content.trustCapsules.contains { $0.label == "Like new" })
        XCTAssertEqual(content.dock.primary.label, "Make offer")
    }

    func testListingProjectionFreeRendersFreePrice() {
        let listing = ListingDTO(
            id: "l2",
            userId: "u2",
            title: "Moving boxes",
            description: nil,
            price: 0,
            isFree: true,
            category: "free_stuff",
            condition: nil,
            status: "active",
            mediaUrls: nil,
            firstImage: nil,
            layer: "goods",
            listingType: "free_item",
            latitude: nil,
            longitude: nil,
            locationName: nil,
            distanceMeters: nil,
            createdAt: nil,
            userHasSaved: nil,
            approxLocation: nil
        )
        let content = ListingDetailViewModel.project(listing)
        XCTAssertEqual(content.hero.priceLine, "Free")
        XCTAssertTrue(content.trustCapsules.contains { $0.label == "Free" })
    }

    // MARK: - Invoice

    func testInvoiceFixtureCarriesFromToLineItemsAndFullWidthDock() {
        let content = InvoiceDetailViewModel.fixture(invoiceId: "INV-00247")
        XCTAssertEqual(content.kind, .invoice)
        XCTAssertEqual(content.statusPill?.label, "Due in 3 days")
        XCTAssertEqual(content.hero.title, "Bathroom retile")
        XCTAssertTrue(content.hero.monoId?.contains("INV-00247") == true)
        XCTAssertTrue(content.modules.contains {
            if case .fromTo = $0 { return true } else { return false }
        })
        XCTAssertTrue(content.modules.contains {
            if case .lineItems = $0 { return true } else { return false }
        })
        XCTAssertTrue(content.modules.contains {
            if case .summary = $0 { return true } else { return false }
        })
        XCTAssertNil(content.dock.secondary, "invoice dock is full-width — no secondary button")
        XCTAssertTrue(content.dock.primary.label.contains("Pay"))
    }
}
