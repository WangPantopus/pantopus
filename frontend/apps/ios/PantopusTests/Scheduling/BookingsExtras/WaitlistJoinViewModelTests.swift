//
//  WaitlistJoinViewModelTests.swift
//  PantopusTests
//
//  E13 invitee waitlist join · Stream I9 (public POST .../waitlist).
//

import XCTest
@testable import Pantopus

@MainActor
final class WaitlistJoinViewModelTests: XCTestCase {
    override func setUp() {
        super.setUp()
        SequencedURLProtocol.reset()
    }

    override func tearDown() {
        SequencedURLProtocol.reset()
        super.tearDown()
    }

    private func makeVM() -> WaitlistJoinViewModel {
        WaitlistJoinViewModel(
            slug: "acme-studio",
            eventTypeSlug: "group-class",
            windowLabel: "Sat, Jun 14 · 10:00 AM",
            timeZoneLabel: "Pacific Time",
            client: SchedulingClient(client: APIClient(session: SequencedURLProtocol.makeSession(), retryPolicy: .none))
        )
    }

    func testCanJoinRequiresValidEmail() {
        let viewModel = makeVM()
        XCTAssertFalse(viewModel.canJoin)
        viewModel.email = "not-an-email"
        XCTAssertFalse(viewModel.canJoin)
        viewModel.email = "rosa@example.com"
        XCTAssertTrue(viewModel.canJoin)
    }

    func testJoinSuccess() async {
        SequencedURLProtocol.sequence = [.status(201, body: #"{"waitlist":{"id":"w1","status":"waiting"}}"#)]
        let viewModel = makeVM()
        viewModel.name = "Rosa"
        viewModel.email = "rosa@example.com"
        await viewModel.join()
        XCTAssertTrue(viewModel.didJoin)
        XCTAssertNil(viewModel.errorMessage)
    }

    func testJoinErrorSurfacesMessage() async {
        SequencedURLProtocol.sequence = [.status(404, body: #"{"error":"NOT_FOUND","message":"No such page"}"#)]
        let viewModel = makeVM()
        viewModel.email = "rosa@example.com"
        await viewModel.join()
        XCTAssertFalse(viewModel.didJoin)
        XCTAssertNotNil(viewModel.errorMessage)
    }
}
