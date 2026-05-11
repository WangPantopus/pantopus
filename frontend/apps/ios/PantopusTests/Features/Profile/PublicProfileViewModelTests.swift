//
//  PublicProfileViewModelTests.swift
//  PantopusTests
//
//  Covers happy-path load, tab content switching (no refetch), and the
//  empty-Reviews case.
//

import XCTest
@testable import Pantopus

@MainActor
final class PublicProfileViewModelTests: XCTestCase {
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

    private static let profileWithReviews = """
    {
      "id": "u1",
      "username": "alex",
      "firstName": "Alex",
      "lastName": "Rivera",
      "name": "Alex Rivera",
      "bio": "Cambridge transplant.",
      "tagline": "Builder",
      "avatar_url": null,
      "profile_picture_url": null,
      "profilePicture": null,
      "city": "Cambridge",
      "state": "MA",
      "accountType": "personal",
      "verified": true,
      "residency": null,
      "created_at": "2025-01-01T00:00:00.000Z",
      "gigs_posted": 2,
      "gigs_completed": 5,
      "average_rating": 4.8,
      "review_count": 7,
      "followers_count": 12,
      "reviews": [
        {
          "id": "r1",
          "reviewer_id": "u9",
          "reviewee_id": "u1",
          "rating": 5,
          "content": "Great help",
          "created_at": "2026-04-01T00:00:00.000Z",
          "reviewer_name": "Sam",
          "reviewer_avatar": null,
          "reviewer_username": "sam"
        }
      ],
      "socialLinks": {},
      "skills": ["Carpentry","Spanish"]
    }
    """

    private static let profileNoReviews = """
    {
      "id": "u2",
      "username": "ben",
      "firstName": "Ben",
      "lastName": null,
      "name": "Ben",
      "bio": null,
      "tagline": null,
      "avatar_url": null,
      "profile_picture_url": null,
      "profilePicture": null,
      "city": null,
      "state": null,
      "accountType": "personal",
      "verified": false,
      "residency": null,
      "created_at": "2025-01-01T00:00:00.000Z",
      "gigs_posted": 0,
      "gigs_completed": 0,
      "average_rating": 0,
      "review_count": 0,
      "followers_count": 0,
      "reviews": [],
      "socialLinks": {},
      "skills": []
    }
    """

    // MARK: - Load

    func testLoadHappyPath() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.profileWithReviews)]
        let vm = PublicProfileViewModel(userId: "u1", client: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        XCTAssertEqual(content.header.displayName, "Alex Rivera")
        XCTAssertEqual(content.header.handle, "alex")
        XCTAssertEqual(content.header.locality, "Cambridge, MA")
        XCTAssertTrue(content.header.isVerified)
        XCTAssertEqual(content.stats.stats.map(\.label), ["Reviews", "Rating", "Gigs"])
        XCTAssertEqual(content.stats.reviews.count, 1)
        XCTAssertEqual(content.stats.skills, ["Carpentry", "Spanish"])
        // Regression guard: backend sends `created_at` snake-case but the
        // CodingKey was missing originally and silently nil'd this field.
        XCTAssertEqual(content.profile.createdAt, "2025-01-01T00:00:00.000Z")
    }

    // MARK: - Tabs

    func testTabSwitchingDoesNotRefetch() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.profileWithReviews)]
        let vm = PublicProfileViewModel(userId: "u1", client: makeAPI())
        await vm.load()
        let initialRequestCount = SequencedURLProtocol.capturedRequests.count
        vm.selectedTab = .reviews
        vm.selectedTab = .gigs
        XCTAssertEqual(
            SequencedURLProtocol.capturedRequests.count,
            initialRequestCount,
            "Switching tabs must not trigger a network fetch."
        )
    }

    // MARK: - Empty Reviews

    func testEmptyReviewsState() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.profileNoReviews)]
        let vm = PublicProfileViewModel(userId: "u2", client: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        XCTAssertTrue(content.stats.reviews.isEmpty)
        XCTAssertFalse(content.header.isVerified)
    }

    // MARK: - Errors

    func testNotFoundEmitsFriendlyMessage() async {
        SequencedURLProtocol.sequence = [.status(404, body: "{\"error\":\"missing\"}")]
        let vm = PublicProfileViewModel(userId: "nope", client: makeAPI())
        await vm.load()
        guard case let .error(message) = vm.state else {
            XCTFail("Expected .error")
            return
        }
        XCTAssertTrue(message.contains("profile"))
    }

    // MARK: - Placeholders

    func testMessageButtonShowsToast() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.profileWithReviews)]
        let vm = PublicProfileViewModel(userId: "u1", client: makeAPI())
        await vm.load()
        vm.tapMessage()
        XCTAssertEqual(vm.toastMessage, "Messaging coming soon")
        vm.tapConnect()
        XCTAssertEqual(vm.toastMessage, "Connect coming soon")
    }
}
