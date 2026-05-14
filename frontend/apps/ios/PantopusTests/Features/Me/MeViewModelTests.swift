//
//  MeViewModelTests.swift
//  PantopusTests
//
//  Covers the Me tab VM: load (with + without home), identity switching
//  rebinds in place (no refetch), and the build* projections produce
//  the right stat / action / section keys.
//

import XCTest
@testable import Pantopus

@MainActor
final class MeViewModelTests: XCTestCase {
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

    private static let profileJSON = """
    {
      "user": {
        "id": "u1",
        "email": "alice@example.com",
        "username": "alice",
        "firstName": "Alice",
        "middleName": null,
        "lastName": "Doe",
        "name": "Alice Doe",
        "phoneNumber": null,
        "dateOfBirth": null,
        "address": null,
        "city": "Cambridge",
        "state": "MA",
        "zipcode": null,
        "accountType": "personal",
        "role": "member",
        "verified": true,
        "residency": null,
        "avatar_url": null,
        "profile_picture_url": null,
        "profilePicture": null,
        "bio": "Gardener, baker.",
        "tagline": null,
        "socialLinks": null,
        "skills": [],
        "followers_count": 0,
        "average_rating": 4.9,
        "gigs_posted": 12,
        "gigs_completed": 8,
        "profileVisibility": "registered",
        "createdAt": "2025-01-01T00:00:00.000Z",
        "updatedAt": "2026-01-01T00:00:00.000Z"
      },
      "inviteProgress": null
    }
    """

    private static let homesJSON = """
    {
      "homes": [
        {
          "id": "h1",
          "name": "412 Birch Ln",
          "address": "412 Birch Ln",
          "city": "Cambridge",
          "state": "MA",
          "zipcode": "02139",
          "is_primary_owner": true,
          "ownership_status": "verified"
        }
      ]
    }
    """

    private static let emptyHomesJSON = """
    {"homes": []}
    """

    private static let statsJSON = """
    {
      "total_gigs_posted": 12,
      "total_gigs_completed": 8,
      "total_earnings": 240.0,
      "average_rating": 4.9,
      "total_ratings": 5
    }
    """

    func testLoadProducesAllThreeIdentitiesWhenHomeExists() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.profileJSON),
            .status(200, body: Self.homesJSON),
            .status(200, body: Self.statsJSON)
        ]
        let vm = MeViewModel(api: makeAPI())
        await vm.load()
        guard case let .loaded(personal, home, business) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(personal.identity, .personal)
        XCTAssertEqual(personal.displayName, "Alice Doe")
        XCTAssertTrue(personal.verified)
        XCTAssertEqual(personal.stats.count, 4)
        XCTAssertEqual(personal.stats.first(where: { $0.id == "rating" })?.value, "4.9")
        XCTAssertEqual(personal.actionTiles.count, 6)
        XCTAssertEqual(personal.actionTiles.first(where: { $0.id == "mail" })?.routeKey, "me.mail")

        XCTAssertEqual(home.identity, .home)
        XCTAssertEqual(home.displayName, "412 Birch Ln")
        XCTAssertFalse(home.isUnbound)
        XCTAssertEqual(home.actionTiles.count, 6)
        XCTAssertEqual(home.actionTiles.first?.routeKey, "me.home.access")

        XCTAssertEqual(business.identity, .business)
        XCTAssertTrue(business.isUnbound, "business stays unbound until mobile business read APIs land")
    }

    func testLoadProducesUnboundHomeWhenNoHomeClaimed() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.profileJSON),
            .status(200, body: Self.emptyHomesJSON),
            .status(200, body: Self.statsJSON)
        ]
        let vm = MeViewModel(api: makeAPI())
        await vm.load()
        guard case let .loaded(_, home, _) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        XCTAssertTrue(home.isUnbound)
        XCTAssertEqual(home.displayName, "Claim a home")
    }

    func testSelectIdentityFlipsActiveWithoutRefetch() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.profileJSON),
            .status(200, body: Self.homesJSON),
            .status(200, body: Self.statsJSON)
        ]
        let vm = MeViewModel(api: makeAPI())
        await vm.load()
        XCTAssertEqual(vm.activeIdentity, .personal)
        vm.selectIdentity(.home)
        XCTAssertEqual(vm.activeIdentity, .home)
        vm.selectIdentity(.business)
        XCTAssertEqual(vm.activeIdentity, .business)
        vm.selectIdentity(.personal)
        XCTAssertEqual(vm.activeIdentity, .personal)
    }

    func testLoadFailureWhenProfileFailsTransitionsError() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{}")]
        let vm = MeViewModel(api: makeAPI())
        await vm.load()
        if case .error = vm.state { /* ok */ } else {
            XCTFail("Expected .error, got \(vm.state)")
        }
    }
}
