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

    /// P6.5 — Local profile fixture. The `residency.verified == true`
    /// blob is the signal the VM uses to switch the kind from
    /// `.persona` to `.local`.
    private static let profileLocalNeighbor = """
    {
      "id": "u3",
      "username": "mariak",
      "firstName": "Maria",
      "lastName": "K.",
      "name": "Maria K.",
      "bio": "Apt 3B at 412 Elm. Around most evenings.",
      "tagline": null,
      "avatar_url": null,
      "profile_picture_url": null,
      "profilePicture": null,
      "city": "Cambridge",
      "state": "MA",
      "accountType": "personal",
      "verified": true,
      "residency": { "verified": true, "address": "412 Elm St" },
      "created_at": "2025-01-01T00:00:00.000Z",
      "gigs_posted": 0,
      "gigs_completed": 0,
      "average_rating": 4.9,
      "review_count": 12,
      "followers_count": 128,
      "reviews": [],
      "socialLinks": {},
      "skills": []
    }
    """

    /// A21.2 — `GET /api/posts/user/:id` payload used to hydrate the
    /// Local archetype's post feed.
    private static let userPosts = """
    {
      "posts": [
        {
          "id": "p1",
          "user_id": "u3",
          "content": "Free pile on the curb.",
          "post_type": "service_offer",
          "created_at": "2025-01-01T00:00:00.000Z",
          "like_count": 28,
          "comment_count": 12,
          "location_name": "88 Beech St"
        },
        {
          "id": "p2",
          "user_id": "u3",
          "content": "Water main flagged on Beech.",
          "post_type": "recommendation",
          "created_at": "2025-01-01T00:00:00.000Z",
          "like_count": 47,
          "comment_count": 18
        }
      ]
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

    // MARK: - Action wiring

    func testConnectSendsRequestAndMarksSucceeded() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.profileWithReviews),
            .status(201, body: "{\"message\":\"Connection request sent\"}")
        ]
        let vm = PublicProfileViewModel(userId: "u1", client: makeAPI())
        await vm.load()
        await vm.connect()
        XCTAssertEqual(vm.connectState, .succeeded)
        XCTAssertEqual(vm.toastMessage, "Connection request sent")
    }

    func testConnectShowsErrorToastOnFailure() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.profileWithReviews),
            .status(400, body: "{\"error\":\"Connection request already exists\"}")
        ]
        let vm = PublicProfileViewModel(userId: "u1", client: makeAPI())
        await vm.load()
        await vm.connect()
        if case .failed = vm.connectState { /* ok */ } else {
            XCTFail("Expected .failed connect state")
        }
        XCTAssertNotNil(vm.toastMessage)
    }

    func testBlockSendsRequestAndMarksSucceeded() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.profileWithReviews),
            .status(200, body: "{}")
        ]
        let vm = PublicProfileViewModel(userId: "u1", client: makeAPI())
        await vm.load()
        await vm.block()
        XCTAssertEqual(vm.blockState, .succeeded)
        XCTAssertEqual(vm.toastMessage, "User blocked")
    }

    func testOverflowFlagToggles() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.profileWithReviews)]
        let vm = PublicProfileViewModel(userId: "u1", client: makeAPI())
        await vm.load()
        XCTAssertFalse(vm.showOverflow)
        vm.showOverflow = true
        XCTAssertTrue(vm.showOverflow)
    }

    // MARK: - P6.5 — Persona vs Local kind discrimination

    func testProfileWithoutResidencyIsPersonaKind() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.profileWithReviews)]
        let vm = PublicProfileViewModel(userId: "u1", client: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        XCTAssertEqual(content.kind, .persona)
        XCTAssertEqual(content.header.tierLabel, "Persona · Verified")
        XCTAssertFalse(content.header.isVerifiedNeighbor)
    }

    func testProfileWithVerifiedResidencyIsLocalKind() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.profileLocalNeighbor)]
        let vm = PublicProfileViewModel(userId: "u3", client: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        XCTAssertEqual(content.kind, .local)
        XCTAssertTrue(content.header.isVerifiedNeighbor)
        XCTAssertNil(content.header.tierLabel)
    }

    // MARK: - A21.2 — the Local archetype's post feed

    func testLocalProfileProjectsUserPostsOntoTheFeed() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.profileLocalNeighbor),
            .status(200, body: Self.userPosts)
        ]
        let vm = PublicProfileViewModel(userId: "u3", client: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        XCTAssertEqual(content.posts.count, 2)
        let first = content.posts[0]
        XCTAssertEqual(first.body, "Free pile on the curb.")
        XCTAssertEqual(first.locality, "88 Beech St")
        XCTAssertEqual(first.reactions, 28)
        XCTAssertEqual(first.replies, 12)
        XCTAssertEqual(first.intent, .offer)
        // Never invent a tier chip for a plain neighbourhood post.
        XCTAssertNil(first.visibility)
        XCTAssertFalse(first.isLocked)
        // A post type with no honest chip renders without one.
        XCTAssertNil(content.posts[1].intent)
        // The neighbour projection sees the same feed.
        XCTAssertEqual(content.neighbor?.posts.count, 2)
    }

    func testLocalProfilePostFailureDegradesToEmptyFeed() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.profileLocalNeighbor),
            .status(500, body: "{\"error\":\"boom\"}")
        ]
        let vm = PublicProfileViewModel(userId: "u3", client: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        XCTAssertTrue(content.posts.isEmpty)
        XCTAssertEqual(content.kind, .local)
    }

    func testPersonaProfileDoesNotFetchUserPosts() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.profileWithReviews)]
        let vm = PublicProfileViewModel(userId: "u1", client: makeAPI())
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        XCTAssertTrue(content.posts.isEmpty)
        let hitPostsEndpoint = SequencedURLProtocol.capturedRequests.contains {
            $0.url?.path.hasPrefix("/api/posts/user/") ?? false
        }
        XCTAssertFalse(hitPostsEndpoint, "Persona profiles must not fetch the local post feed.")
    }

    func testLocalTabDefaultsToPostsAndSwitchesWithoutRefetch() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.profileLocalNeighbor),
            .status(200, body: Self.userPosts)
        ]
        let vm = PublicProfileViewModel(userId: "u3", client: makeAPI())
        await vm.load()
        XCTAssertEqual(vm.selectedLocalTab, .posts)
        let requestCount = SequencedURLProtocol.capturedRequests.count
        vm.selectedLocalTab = .about
        XCTAssertEqual(vm.selectedLocalTab, .about)
        XCTAssertEqual(SequencedURLProtocol.capturedRequests.count, requestCount)
    }

    // MARK: - WS3.1 — Follow opens the privacy handshake (Stripe tiers)

    /// `GET /api/users/id/:id` carries no Beacon handle, and `User.username`
    /// is a different namespace from `PublicPersona.handle` — so the
    /// handshake must not open against a handle we can't attribute.
    func testFollowDoesNotUseUsernameAsBeaconHandle() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.profileWithReviews)]
        let vm = PublicProfileViewModel(userId: "u1", client: makeAPI())
        await vm.load()
        XCTAssertFalse(vm.showFollowHandshake)
        vm.follow()
        XCTAssertFalse(vm.showFollowHandshake)
        XCTAssertEqual(vm.loadedPersonaHandle, "")
        XCTAssertEqual(vm.toastMessage, PublicProfileViewModel.handshakeUnavailableMessage)
    }

    func testUnlockBroadcastWithoutBeaconHandleStaysClosed() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.profileWithReviews)]
        let vm = PublicProfileViewModel(userId: "u1", client: makeAPI())
        await vm.load()
        vm.unlockBroadcast(tierRank: 2)
        XCTAssertFalse(vm.showFollowHandshake)
        XCTAssertNil(vm.handshakePreselectedTierRank)
        XCTAssertEqual(vm.toastMessage, PublicProfileViewModel.handshakeUnavailableMessage)
    }

    func testOwnerCannotOpenFollowHandshake() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.profileWithReviews)]
        let vm = PublicProfileViewModel(userId: "u1", currentUserId: "u1", client: makeAPI())
        await vm.load()
        vm.follow()
        XCTAssertFalse(vm.showFollowHandshake)
    }
}
