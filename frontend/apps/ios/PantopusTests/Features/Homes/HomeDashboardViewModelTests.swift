//
//  HomeDashboardViewModelTests.swift
//  PantopusTests
//
//  State-transition coverage for `HomeDashboardViewModel`. Covers the
//  private-detail happy path, the 403 → public-profile fallback, and
//  the final 500 error state.
//

import XCTest
@testable import Pantopus

@MainActor
final class HomeDashboardViewModelTests: XCTestCase {

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

    func testPrivateDetailHappyPath() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: """
            {"home":{
              "id":"h1","name":"Main","address":"1 Main","city":"X","state":"CA","zipcode":"90000",
              "owner":{"id":"u1","username":"alice","name":"Alice"},
              "occupants":[],"location":null,"isOwner":true,"isPendingOwner":false,
              "pendingClaimId":null,"isOccupant":false,
              "owners":[{"id":"o1","subject_type":"user","subject_id":"u1","owner_status":"verified","is_primary_owner":true,"verification_tier":"attom"}],
              "can_delete_home":true
            }}
            """),
        ]
        let vm = HomeDashboardViewModel(homeId: "h1", api: makeAPI())
        await vm.load()
        guard case .loaded(let content) = vm.state else {
            XCTFail("Expected loaded, got \(vm.state)"); return
        }
        XCTAssertEqual(content.address, "1 Main")
        XCTAssertTrue(content.verified)
        XCTAssertEqual(content.stats.count, 3)
        XCTAssertEqual(content.tabs.count, 4)
    }

    func testForbiddenFallsBackToPublicProfile() async {
        SequencedURLProtocol.sequence = [
            .status(403, body: "{\"error\":\"no access\"}"),
            .status(200, body: """
            {"home":{
              "id":"h1","name":null,"address":"200 Public St","city":"Y","state":"CA","zipcode":"90000",
              "home_type":"single_family","visibility":"public","description":null,
              "created_at":"2025-01-01T00:00:00Z","hasVerifiedOwner":true,"verifiedOwner":null,
              "userMembershipStatus":"none","userResidencyClaim":null,"memberCount":2,"nearbyGigs":5
            }}
            """),
        ]
        let vm = HomeDashboardViewModel(homeId: "h1", api: makeAPI())
        await vm.load()
        guard case .loaded(let content) = vm.state else {
            XCTFail("Expected loaded, got \(vm.state)"); return
        }
        XCTAssertEqual(content.address, "200 Public St")
        XCTAssertTrue(content.verified, "Public profile with a verified owner should flip verified=true")
        XCTAssertEqual(content.stats.first?.value, "2")
    }

    func testServerErrorSurfacesError() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{}")]
        let vm = HomeDashboardViewModel(homeId: "h1", api: makeAPI())
        await vm.load()
        if case .error = vm.state {
            // pass
        } else {
            XCTFail("Expected error, got \(vm.state)")
        }
    }

    func testRetryRecovers() async {
        SequencedURLProtocol.sequence = [
            .status(500, body: "{}"),
            .status(200, body: """
            {"home":{
              "id":"h1","name":"Main","address":"1 Main","city":"X","state":"CA","zipcode":"90000",
              "occupants":[],"location":null,"isOwner":false,"isPendingOwner":false,
              "pendingClaimId":null,"isOccupant":true,"owners":[],"can_delete_home":false
            }}
            """),
        ]
        let vm = HomeDashboardViewModel(homeId: "h1", api: makeAPI())
        await vm.load()
        guard case .error = vm.state else {
            XCTFail("Expected error first"); return
        }
        await vm.refresh()
        guard case .loaded = vm.state else {
            XCTFail("Expected loaded after retry"); return
        }
    }
}
