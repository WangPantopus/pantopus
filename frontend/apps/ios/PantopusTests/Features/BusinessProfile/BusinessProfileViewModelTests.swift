//
//  BusinessProfileViewModelTests.swift
//  PantopusTests
//
//  P1.6 — VM-projection regression suite. Covers the loaded path
//  (detail + public + reviews-fallback all succeed), the not-found
//  path (404 on the primary fetch), the unpublished path (public
//  fetch 404s but detail succeeds — services tab empty), and tab
//  switching (must not refetch).
//

import XCTest
@testable import Pantopus

@MainActor
final class BusinessProfileViewModelTests: XCTestCase {
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

    private static let detailJSON = """
    {
      "business": {
        "id": "biz-1",
        "username": "elmpark-coffee",
        "name": "Elm Park Coffee",
        "email": null,
        "bio": "Slow coffee on the corner.",
        "tagline": "Made by neighbors",
        "profile_picture_url": "https://example.test/elm.png",
        "cover_photo_url": null,
        "account_type": "business",
        "city": "Cambridge",
        "state": "MA",
        "verified": true,
        "average_rating": 4.7,
        "review_count": 12,
        "followers_count": 240,
        "gigs_completed": 0,
        "created_at": "2023-04-10T00:00:00.000Z"
      },
      "profile": {
        "business_user_id": "biz-1",
        "business_type": "cafe",
        "categories": ["Coffee", "Bakery"],
        "description": "Pour-over coffee and laminated pastry from the neighborhood.",
        "logo_file_id": null,
        "banner_file_id": null,
        "public_email": "hi@elmpark.test",
        "public_phone": "+1-555-0101",
        "website": "elmparkcoffee.test",
        "founded_year": 2021,
        "employee_count": "1-5",
        "service_area": null,
        "founding_badge": true,
        "is_published": true,
        "verification_status": "address_verified",
        "primary_location": {
          "id": "loc-1",
          "label": "Main shop",
          "is_primary": true,
          "address": "41 Elm Street",
          "address2": null,
          "city": "Cambridge",
          "state": "MA",
          "zipcode": "02139",
          "country": "US",
          "location": { "lat": 42.37, "lng": -71.11 },
          "phone": null,
          "email": null,
          "timezone": "America/New_York"
        }
      },
      "locations": [],
      "access": { "hasAccess": true, "isOwner": false, "role_base": null }
    }
    """

    private static let publicJSON = """
    {
      "hours": [
        { "id": "h-mon", "location_id": "loc-1", "day_of_week": 1, "open_time": "07:00", "close_time": "16:00", "is_closed": false },
        { "id": "h-tue", "location_id": "loc-1", "day_of_week": 2, "open_time": "07:00", "close_time": "16:00", "is_closed": false },
        { "id": "h-sun", "location_id": "loc-1", "day_of_week": 0, "open_time": null,    "close_time": null,    "is_closed": true }
      ],
      "catalog": [
        {
          "id": "svc-1",
          "name": "Pour over",
          "description": "Single-origin, sliding scale.",
          "kind": "service",
          "price_cents": 500,
          "price_max_cents": null,
          "price_unit": null,
          "currency": "USD",
          "image_url": null,
          "is_featured": true
        }
      ]
    }
    """

    private static let publicEmptyServicesJSON = """
    {
      "hours": [],
      "catalog": []
    }
    """

    private static let reviewsJSON = """
    {
      "id": "biz-1",
      "username": "elmpark-coffee",
      "firstName": null,
      "lastName": null,
      "name": "Elm Park Coffee",
      "bio": null,
      "tagline": null,
      "avatar_url": null,
      "profile_picture_url": null,
      "city": "Cambridge",
      "state": "MA",
      "accountType": "business",
      "verified": true,
      "residency": null,
      "created_at": "2023-04-10T00:00:00.000Z",
      "gigs_posted": 0,
      "gigs_completed": 0,
      "average_rating": 4.7,
      "review_count": 12,
      "followers_count": 240,
      "reviews": [
        {
          "id": "r1",
          "reviewer_id": "u9",
          "reviewee_id": "biz-1",
          "rating": 5,
          "content": "Best coffee on Elm.",
          "created_at": "2026-05-01T00:00:00.000Z",
          "reviewer_name": "Sam",
          "reviewer_avatar": null,
          "reviewer_username": "sam"
        }
      ],
      "socialLinks": {},
      "skills": []
    }
    """

    // MARK: - Happy path

    func testLoadedProjectsHeaderStatsAboutHoursAndServices() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.detailJSON),
            .status(200, body: Self.publicJSON),
            .status(200, body: Self.reviewsJSON)
        ]
        let vm = BusinessProfileViewModel(businessId: "biz-1", client: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            return XCTFail("Expected .loaded, got \(vm.state)")
        }
        XCTAssertEqual(content.header.displayName, "Elm Park Coffee")
        XCTAssertEqual(content.header.handle, "elmpark-coffee")
        XCTAssertEqual(content.header.locality, "Cambridge, MA")
        XCTAssertTrue(content.header.isVerified)
        XCTAssertEqual(content.header.categoryChips, ["Coffee", "Bakery"])

        XCTAssertEqual(content.stats.map(\.label), ["Followers", "Reviews", "Years"])
        XCTAssertEqual(content.stats.first?.value, "240")

        XCTAssertEqual(content.about, "Pour-over coffee and laminated pastry from the neighborhood.")
        XCTAssertEqual(content.hours.count, 3)
        XCTAssertEqual(content.hours.map(\.dayLabel), ["Sun", "Mon", "Tue"])
        XCTAssertTrue(content.hours.first?.isClosed == true)

        XCTAssertEqual(content.services.count, 1)
        XCTAssertEqual(content.services.first?.name, "Pour over")
        XCTAssertEqual(content.services.first?.priceLabel, "$5")

        XCTAssertEqual(content.reviews.count, 1)
        XCTAssertEqual(content.reviews.first?.reviewerName, "Sam")

        XCTAssertNotNil(content.websiteURL)
    }

    // MARK: - Empty services

    func testEmptyServicesWhenPublicCatalogReturnsEmpty() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.detailJSON),
            .status(200, body: Self.publicEmptyServicesJSON),
            .status(200, body: Self.reviewsJSON)
        ]
        let vm = BusinessProfileViewModel(businessId: "biz-1", client: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            return XCTFail("Expected .loaded")
        }
        XCTAssertTrue(content.services.isEmpty)
        XCTAssertTrue(content.hours.isEmpty)
        // The Overview about + address still render — only Services
        // hits the empty state in the view.
        XCTAssertNotNil(content.about)
    }

    // MARK: - Unpublished business → public 404

    func testUnpublishedBusinessAbsorbsPublic404() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.detailJSON),
            .status(404, body: "{\"error\":\"unpublished\"}"),
            .status(200, body: Self.reviewsJSON)
        ]
        let vm = BusinessProfileViewModel(businessId: "biz-1", client: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            return XCTFail("Expected .loaded; got \(vm.state)")
        }
        XCTAssertTrue(content.hours.isEmpty)
        XCTAssertTrue(content.services.isEmpty)
        XCTAssertEqual(content.reviews.count, 1)
    }

    // MARK: - Not found

    func testPrimary404EmitsNotFoundState() async {
        SequencedURLProtocol.sequence = [.status(404, body: "{\"error\":\"missing\"}")]
        let vm = BusinessProfileViewModel(businessId: "nope", client: makeAPI())
        await vm.load()
        if case .notFound = vm.state { return }
        XCTFail("Expected .notFound; got \(vm.state)")
    }

    // MARK: - Tab switching does not refetch

    func testTabSwitchingDoesNotRefetch() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.detailJSON),
            .status(200, body: Self.publicJSON),
            .status(200, body: Self.reviewsJSON)
        ]
        let vm = BusinessProfileViewModel(businessId: "biz-1", client: makeAPI())
        await vm.load()
        let initialRequestCount = SequencedURLProtocol.capturedRequests.count
        vm.selectedTab = .services
        vm.selectedTab = .reviews
        XCTAssertEqual(
            SequencedURLProtocol.capturedRequests.count,
            initialRequestCount,
            "Switching tabs must not trigger a network fetch."
        )
    }

    // MARK: - Save action

    func testSaveTransitionsToSavedAndEmitsToast() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.detailJSON),
            .status(200, body: Self.publicJSON),
            .status(200, body: Self.reviewsJSON)
        ]
        let vm = BusinessProfileViewModel(businessId: "biz-1", client: makeAPI())
        await vm.load()
        await vm.save()
        XCTAssertEqual(vm.saveState, .saved)
        XCTAssertEqual(vm.toastMessage, "Saved")
    }
}
