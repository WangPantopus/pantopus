//
//  ListingOffersViewModelTests.swift
//  PantopusTests
//
//  T5.3.4 — Listing offers. Covers:
//    - load → loaded / empty / error transitions (listing + offers
//      fetched in parallel, error if either cold-fetch fails)
//    - row mapping: avatar leading, priceStack trailing, status chip,
//      counter pill on `countered`, leading highlight on the highest
//      pending offer, "N days old · X of Y offers" meta tail
//    - footer mapping: respondPending / undoCounter / viewTransaction /
//      none per status
//    - optimistic mutations: accept / decline / counter all flip the
//      row locally and roll back on failure
//    - listing-context header rendering against the loaded listing
//

import XCTest
@testable import Pantopus

// swiftlint:disable type_body_length

@MainActor
final class ListingOffersViewModelTests: XCTestCase {
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

    /// 2026-05-15 12:00:00 UTC — Friday. Fixed so age-in-days reads
    /// deterministically.
    private static let fixedNow: Date = {
        var components = DateComponents()
        components.year = 2026
        components.month = 5
        components.day = 15
        components.hour = 12
        components.minute = 0
        components.second = 0
        components.timeZone = TimeZone(secondsFromGMT: 0) ?? .current
        return Calendar(identifier: .gregorian).date(from: components)
            ?? Date(timeIntervalSince1970: 1_778_846_400)
    }()

    private func makeVM(api: APIClient? = nil) -> ListingOffersViewModel {
        ListingOffersViewModel(
            listingId: "listing-1",
            listingTitleHint: "Mid-century walnut credenza",
            api: api ?? makeAPI()
        ) {
            Self.fixedNow
        }
    }

    private static let listingJSON = """
    {"listing":{
      "id":"listing-1","user_id":"u_me","title":"Mid-century walnut credenza",
      "price":250,"is_free":false,"category":"furniture","status":"active",
      "media_urls":[],"first_image":null,"layer":"goods",
      "created_at":"2026-05-11T12:00:00Z"
    }}
    """

    private static let listingEmptyJSON = """
    {"listing":{
      "id":"listing-1","user_id":"u_me","title":"Mid-century walnut credenza",
      "price":250,"is_free":false,"category":"furniture","status":"active",
      "media_urls":[],"first_image":null,"layer":"goods",
      "created_at":"2026-05-15T06:00:00Z"
    }}
    """

    /// Three offers: $240 pending (leading), $225 countered, $200
    /// declined. Buyers in three different name shapes to exercise the
    /// `displayName` fallback chain.
    private static let threeOffersJSON = """
    {"offers":[
      {"id":"o-anika","listing_id":"listing-1","buyer_id":"u_anika",
       "seller_id":"u_me","amount":240,"message":"Love the dovetail joinery.",
       "status":"pending","counter_amount":null,
       "created_at":"2026-05-15T11:48:00Z",
       "buyer":{"id":"u_anika","first_name":"Anika","last_name":"Reyes",
                "username":"anika"},
       "seller":{"id":"u_me","first_name":"Me","last_name":"Seller"}},
      {"id":"o-marcus","listing_id":"listing-1","buyer_id":"u_marcus",
       "seller_id":"u_me","amount":225,"message":null,
       "status":"countered","counter_amount":235,
       "created_at":"2026-05-13T12:00:00Z",
       "buyer":{"id":"u_marcus","first_name":"Marcus","last_name":"Tate",
                "username":"marcus_t"},
       "seller":{"id":"u_me"}},
      {"id":"o-daniel","listing_id":"listing-1","buyer_id":"u_daniel",
       "seller_id":"u_me","amount":175,"message":null,
       "status":"declined","counter_amount":null,
       "created_at":"2026-05-12T12:00:00Z",
       "buyer":{"id":"u_daniel","username":"dan_k"},
       "seller":{"id":"u_me"}}
    ]}
    """

    private static let oneAcceptedJSON = """
    {"offers":[
      {"id":"o-anika","listing_id":"listing-1","buyer_id":"u_anika",
       "seller_id":"u_me","amount":240,"message":null,
       "status":"accepted","counter_amount":null,
       "created_at":"2026-05-15T11:48:00Z",
       "buyer":{"id":"u_anika","first_name":"Anika","last_name":"Reyes"},
       "seller":{"id":"u_me"}}
    ]}
    """

    private static let emptyOffersJSON = "{\"offers\":[]}"

    /// Three pending offers with amount and recency deliberately crossed
    /// so every sort yields a distinct order:
    ///   - o-low-new : $100, newest (05-15)
    ///   - o-mid-old : $200, oldest (05-10)
    ///   - o-high-mid: $300, middle (05-13) — top offer, wins LEADING
    private static let sortFixtureJSON = """
    {"offers":[
      {"id":"o-low-new","listing_id":"listing-1","buyer_id":"u_low",
       "seller_id":"u_me","amount":100,"status":"pending",
       "created_at":"2026-05-15T10:00:00Z",
       "buyer":{"id":"u_low","first_name":"Lena","last_name":"New"}},
      {"id":"o-mid-old","listing_id":"listing-1","buyer_id":"u_mid",
       "seller_id":"u_me","amount":200,"status":"pending",
       "created_at":"2026-05-10T10:00:00Z",
       "buyer":{"id":"u_mid","first_name":"Milo","last_name":"Old"}},
      {"id":"o-high-mid","listing_id":"listing-1","buyer_id":"u_high",
       "seller_id":"u_me","amount":300,"status":"pending",
       "created_at":"2026-05-13T10:00:00Z",
       "buyer":{"id":"u_high","first_name":"Hana","last_name":"Mid"}}
    ]}
    """

    private func loadedRowIDs(_ vm: ListingOffersViewModel) -> [String] {
        guard case let .loaded(sections, _) = vm.state else { return [] }
        return sections.first?.rows.map(\.id) ?? []
    }

    // MARK: - Lifecycle

    func testLoadPopulatedTransitionsToLoaded() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.threeOffersJSON)
        ]
        let vm = makeVM()
        await vm.load()
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(sections.first?.rows.count, 3)
        // Highest offer (default): $240, $225, $175
        XCTAssertEqual(sections.first?.rows[0].id, "o-anika")
        XCTAssertEqual(sections.first?.rows[1].id, "o-marcus")
        XCTAssertEqual(sections.first?.rows[2].id, "o-daniel")
    }

    func testLoadEmptyTransitionsToEmptyWithShareCTA() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.emptyOffersJSON)
        ]
        let vm = makeVM()
        await vm.load()
        guard case let .empty(content) = vm.state else {
            XCTFail("Expected .empty, got \(vm.state)")
            return
        }
        XCTAssertEqual(content.headline, "No offers on this listing yet")
        XCTAssertEqual(content.ctaTitle, "Share listing")
    }

    func testListingFetchFailureCausesError() async {
        SequencedURLProtocol.sequence = [
            .status(500, body: "{}"),
            .status(200, body: Self.threeOffersJSON)
        ]
        let vm = makeVM()
        await vm.load()
        guard case .error = vm.state else {
            XCTFail("Expected .error when listing fetch fails cold, got \(vm.state)")
            return
        }
    }

    func testOffersFetchFailureCausesError() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(500, body: "{}")
        ]
        let vm = makeVM()
        await vm.load()
        guard case .error = vm.state else {
            XCTFail("Expected .error when offers fetch fails cold, got \(vm.state)")
            return
        }
    }

    // MARK: - Listing-context header

    func testListingContextHeaderRendersListingTitleAskAndStatus() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.threeOffersJSON)
        ]
        let vm = makeVM()
        await vm.load()
        let context = vm.listingContext
        XCTAssertEqual(context?.title, "Mid-century walnut credenza")
        XCTAssertEqual(context?.askPrice, "$250")
        XCTAssertEqual(context?.statusChip.label, "Active")
        XCTAssertEqual(context?.offerCount, 3)
        XCTAssertEqual(context?.sortLabel, "Highest offer")
    }

    func testListingContextHeaderShowsLoadingHintBeforeFetch() {
        let vm = makeVM()
        XCTAssertEqual(vm.listingContext?.title, "Mid-century walnut credenza")
        XCTAssertEqual(vm.listingContext?.askPrice, "")
        XCTAssertEqual(vm.listingContext?.statusChip.label, "Loading…")
    }

    // MARK: - Row mapping

    func testFirstPendingRowGetsLeadingHighlight() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.threeOffersJSON)
        ]
        let vm = makeVM()
        await vm.load()
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let leading = sections.first?.rows.first
        XCTAssertEqual(leading?.id, "o-anika")
        XCTAssertEqual(leading?.highlight, .leading)
        XCTAssertEqual(sections.first?.rows[1].highlight, nil)
    }

    func testRowMapping_PendingHasRespondFooter() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.threeOffersJSON)
        ]
        let vm = makeVM()
        await vm.load()
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let pending = sections.first?.rows.first { $0.id == "o-anika" }
        XCTAssertEqual(pending?.footer?.actions.count, 2)
        XCTAssertEqual(pending?.footer?.actions.first?.title, "Counter")
        XCTAssertEqual(pending?.footer?.actions.first?.variant, .ghost)
        XCTAssertEqual(pending?.footer?.actions.last?.title, "Accept")
        XCTAssertEqual(pending?.footer?.actions.last?.variant, .primary)
    }

    func testRowMapping_CounteredHasUndoCounterFooterAndCounterChip() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.threeOffersJSON)
        ]
        let vm = makeVM()
        await vm.load()
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let countered = sections.first?.rows.first { $0.id == "o-marcus" }
        XCTAssertEqual(countered?.footer?.actions.count, 2)
        XCTAssertEqual(countered?.footer?.actions.first?.title, "Withdraw counter")
        XCTAssertEqual(countered?.footer?.actions.first?.variant, .destructive)
        XCTAssertEqual(countered?.footer?.actions.last?.title, "Send counter")
        XCTAssertEqual(countered?.footer?.actions.last?.variant, .primary)
        // Counter pill chip is present alongside the status chip.
        XCTAssertEqual(countered?.chips?.count, 2)
        XCTAssertEqual(countered?.chips?.first?.text, "Countered")
        XCTAssertEqual(countered?.chips?.last?.text, "Your counter $235")
    }

    func testRowMapping_DeclinedHasNoFooter() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.threeOffersJSON)
        ]
        let vm = makeVM()
        await vm.load()
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let declined = sections.first?.rows.first { $0.id == "o-daniel" }
        XCTAssertNil(declined?.footer)
        XCTAssertEqual(declined?.chips?.first?.text, "Declined")
    }

    func testRowMapping_AcceptedHasViewTransactionFooter() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.oneAcceptedJSON)
        ]
        let vm = makeVM()
        await vm.load()
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let accepted = sections.first?.rows.first
        XCTAssertEqual(accepted?.footer?.actions.count, 1)
        XCTAssertEqual(accepted?.footer?.actions.first?.title, "View transaction")
    }

    func testRowMapping_PriceStackAndAvatar() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.threeOffersJSON)
        ]
        let vm = makeVM()
        await vm.load()
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let row = sections.first?.rows.first
        XCTAssertEqual(row?.title, "Anika Reyes")
        guard case let .priceStack(amount, sublabel) = row?.trailing else {
            XCTFail("Expected priceStack trailing")
            return
        }
        XCTAssertEqual(amount, "$240")
        XCTAssertEqual(sublabel, "asking $250")
        guard case let .avatarWithBadge(name, _, _, size, _) = row?.leading else {
            XCTFail("Expected avatarWithBadge leading")
            return
        }
        XCTAssertEqual(name, "Anika Reyes")
        XCTAssertEqual(size, .large)
    }

    func testRowMapping_NoteRendersWhenMessagePresent() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.threeOffersJSON)
        ]
        let vm = makeVM()
        await vm.load()
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let row = sections.first?.rows.first { $0.id == "o-anika" }
        XCTAssertEqual(row?.note, "Love the dovetail joinery.")
        let counteredRow = sections.first?.rows.first { $0.id == "o-marcus" }
        XCTAssertNil(counteredRow?.note)
    }

    func testRowMapping_MetaTailIncludesAgeAndIndex() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.threeOffersJSON)
        ]
        let vm = makeVM()
        await vm.load()
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        // Marcus's $225 offer is 2 days old, position 2 of 3.
        let row = sections.first?.rows.first { $0.id == "o-marcus" }
        XCTAssertEqual(row?.metaTail, "2 days old · 2 of 3 offers")
    }

    // MARK: - Status derivation

    func testStatusFromRaw_AllVariants() {
        XCTAssertEqual(ListingOfferStatus.fromRaw("pending"), .pending)
        XCTAssertEqual(ListingOfferStatus.fromRaw("countered"), .countered)
        XCTAssertEqual(ListingOfferStatus.fromRaw("accepted"), .accepted)
        XCTAssertEqual(ListingOfferStatus.fromRaw("declined"), .declined)
        XCTAssertEqual(ListingOfferStatus.fromRaw("rejected"), .declined)
        XCTAssertEqual(ListingOfferStatus.fromRaw("expired"), .expired)
        XCTAssertEqual(ListingOfferStatus.fromRaw("withdrawn"), .withdrawn)
        XCTAssertEqual(ListingOfferStatus.fromRaw("completed"), .completed)
        XCTAssertEqual(ListingOfferStatus.fromRaw("unknown"), .pending)
    }

    // MARK: - Optimistic mutations

    func testAccept_OptimisticThenRolledBackOnFailure() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.threeOffersJSON),
            .status(500, body: "{}")
        ]
        let vm = makeVM()
        await vm.load()
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let dto = ListingOfferDTO(id: "o-anika", status: "pending")
        await vm.acceptOffer(dto)
        // Verify the chip rolled back to pending after the 500.
        guard case let .loaded(after, _) = vm.state else {
            XCTFail("Expected .loaded after rollback")
            return
        }
        XCTAssertEqual(after.first?.rows.first?.chips?.first?.text, "Pending")
        XCTAssertEqual(sections.first?.rows.first?.chips?.first?.text, "Pending")
    }

    func testAccept_OptimisticThenConfirmedOn200() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.threeOffersJSON),
            .status(200, body: "{\"offer\":{\"id\":\"o-anika\",\"status\":\"accepted\"}}")
        ]
        let vm = makeVM()
        await vm.load()
        let dto = ListingOfferDTO(id: "o-anika", status: "pending")
        await vm.acceptOffer(dto)
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded after accept")
            return
        }
        XCTAssertEqual(sections.first?.rows.first?.chips?.first?.text, "Accepted")
        // Accepted footer = single "View transaction" button.
        XCTAssertEqual(sections.first?.rows.first?.footer?.actions.count, 1)
        XCTAssertEqual(sections.first?.rows.first?.footer?.actions.first?.title, "View transaction")
    }

    func testDecline_OptimisticThenConfirmedOn200() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.threeOffersJSON),
            .status(200, body: "{\"offer\":{\"id\":\"o-anika\",\"status\":\"declined\"}}")
        ]
        let vm = makeVM()
        await vm.load()
        let dto = ListingOfferDTO(id: "o-anika", status: "pending")
        await vm.declineOffer(dto)
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded after decline")
            return
        }
        XCTAssertEqual(sections.first?.rows.first?.chips?.first?.text, "Declined")
        XCTAssertNil(sections.first?.rows.first?.footer)
    }

    func testCounter_OptimisticThenConfirmedOn200() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.threeOffersJSON),
            .status(200, body: """
            {"offer":{"id":"o-anika","status":"countered","counter_amount":230}}
            """)
        ]
        let vm = makeVM()
        await vm.load()
        let dto = ListingOfferDTO(id: "o-anika", status: "pending")
        vm.requestCounter(dto)
        XCTAssertNotNil(vm.counterTarget)
        await vm.confirmCounter(amount: 230)
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded after counter")
            return
        }
        XCTAssertEqual(sections.first?.rows.first?.chips?.first?.text, "Countered")
        XCTAssertEqual(sections.first?.rows.first?.chips?.last?.text, "Your counter $230")
    }

    // MARK: - Sort menu

    func testDefaultSortIsHighestOffer() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.sortFixtureJSON)
        ]
        let vm = makeVM()
        await vm.load()
        XCTAssertEqual(loadedRowIDs(vm), ["o-high-mid", "o-mid-old", "o-low-new"])
        XCTAssertEqual(vm.listingContext?.sortLabel, "Highest offer")
    }

    func testSelectSortLowestOffer() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.sortFixtureJSON)
        ]
        let vm = makeVM()
        await vm.load()
        vm.selectSort(.lowestOffer)
        XCTAssertEqual(loadedRowIDs(vm), ["o-low-new", "o-mid-old", "o-high-mid"])
        XCTAssertEqual(vm.listingContext?.sortLabel, "Lowest offer")
    }

    func testSelectSortNewestFirst() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.sortFixtureJSON)
        ]
        let vm = makeVM()
        await vm.load()
        vm.selectSort(.newestFirst)
        XCTAssertEqual(loadedRowIDs(vm), ["o-low-new", "o-high-mid", "o-mid-old"])
        XCTAssertEqual(vm.listingContext?.sortLabel, "Newest first")
    }

    func testSelectSortOldestFirst() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.sortFixtureJSON)
        ]
        let vm = makeVM()
        await vm.load()
        vm.selectSort(.oldestFirst)
        XCTAssertEqual(loadedRowIDs(vm), ["o-mid-old", "o-high-mid", "o-low-new"])
        XCTAssertEqual(vm.listingContext?.sortLabel, "Oldest first")
    }

    /// LEADING badge always tracks the highest-amount pending offer, even
    /// when the active sort buries it in the middle of the list.
    func testLeadingHighlightTracksTopOfferRegardlessOfSort() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.sortFixtureJSON)
        ]
        let vm = makeVM()
        await vm.load()
        vm.selectSort(.oldestFirst)
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let leading = sections.first?.rows.first { $0.highlight == .leading }
        XCTAssertEqual(leading?.id, "o-high-mid")
    }

    /// Selection survives a pull-to-refresh within the same session.
    func testSortPersistsAcrossRefresh() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.sortFixtureJSON),
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.sortFixtureJSON)
        ]
        let vm = makeVM()
        await vm.load()
        vm.selectSort(.lowestOffer)
        await vm.refresh()
        XCTAssertEqual(loadedRowIDs(vm), ["o-low-new", "o-mid-old", "o-high-mid"])
        XCTAssertEqual(vm.listingContext?.sortLabel, "Lowest offer")
    }

    func testSortMenuOptionsExposeFourEntriesWithSelection() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.listingJSON),
            .status(200, body: Self.sortFixtureJSON)
        ]
        let vm = makeVM()
        await vm.load()
        let options = vm.listingContext?.sortOptions ?? []
        XCTAssertEqual(options.map(\.label), ["Highest offer", "Lowest offer", "Newest first", "Oldest first"])
        XCTAssertEqual(options.filter(\.isSelected).map(\.id), ["highestOffer"])
        vm.selectSort(.newestFirst)
        let after = vm.listingContext?.sortOptions ?? []
        XCTAssertEqual(after.filter(\.isSelected).map(\.id), ["newestFirst"])
    }

    // MARK: - No tabs / no FAB

    func testNoTabsExposed() {
        let vm = makeVM()
        XCTAssertEqual(vm.tabs.count, 0)
    }

    func testNoFABExposed() {
        let vm = makeVM()
        XCTAssertNil(vm.fab)
    }

    func testShareTopBarActionPresent() {
        let vm = makeVM()
        XCTAssertEqual(vm.topBarAction?.icon, .share)
    }
}
