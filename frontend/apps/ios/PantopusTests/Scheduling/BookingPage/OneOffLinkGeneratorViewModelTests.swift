//
//  OneOffLinkGeneratorViewModelTests.swift
//  PantopusTests
//
//  C4 · Stream I4. Verifies load → configure → generate, the token→link
//  mapping, and inline 409 handling.
//

import XCTest
@testable import Pantopus

@MainActor
final class OneOffLinkGeneratorViewModelTests: XCTestCase {
    override func setUp() {
        super.setUp()
        SequencedURLProtocol.reset()
    }

    override func tearDown() {
        SequencedURLProtocol.reset()
        super.tearDown()
    }

    private func makeClient() -> APIClient {
        APIClient(session: SequencedURLProtocol.makeSession(), retryPolicy: .none)
    }

    private let pageJSON = #"{"page":{"id":"p1","owner_type":"user","slug":"maria-k","is_live":true,"is_paused":false}}"#
    private let eventTypesJSON = #"""
    {"eventTypes":[{"id":"et_1","name":"Intro call","slug":"intro","durations":[30],"default_duration":30,
    "location_mode":"video","is_active":true}]}
    """#

    private func loadedVM() async -> OneOffLinkGeneratorViewModel {
        SequencedURLProtocol.sequence = [.status(200, body: pageJSON), .status(200, body: eventTypesJSON)]
        let viewModel = OneOffLinkGeneratorViewModel(owner: .personal, api: makeClient()) { _ in }
        await viewModel.load()
        return viewModel
    }

    func testLoadPopulatesOptions() async {
        let viewModel = await loadedVM()
        XCTAssertEqual(viewModel.state, .configuring)
        XCTAssertEqual(viewModel.eventTypeOptions.count, 1)
        XCTAssertEqual(viewModel.selectedEventTypeId, "et_1")
        XCTAssertTrue(viewModel.canGenerate)
    }

    func testGenerateProducesShareableLink() async {
        let viewModel = await loadedVM()
        SequencedURLProtocol.sequence = [
            .status(200, body: #"{"token":"abc123","path":"/book/o/abc123","expires_at":"2026-06-21T12:00:00Z","single_use":true}"#)
        ]
        await viewModel.generate()
        guard case let .generated(link) = viewModel.state else { return XCTFail("expected generated") }
        XCTAssertTrue(link.displayURL.contains("/book/o/abc123"))
        XCTAssertTrue(link.shareURL.hasPrefix("https://"))
        XCTAssertTrue(link.caption.lowercased().contains("single use"))
    }

    func testGenerateConflictShowsInlineError() async {
        let viewModel = await loadedVM()
        SequencedURLProtocol.sequence = [.status(409, body: #"{"error":"LINK_LIMIT","message":"Too many links"}"#)]
        await viewModel.generate()
        XCTAssertEqual(viewModel.state, .configuring)
        XCTAssertNotNil(viewModel.generateError)
    }

    func testLoadSlotsPopulatesOfferedTimes() async {
        let viewModel = await loadedVM()
        let slotsJSON = #"""
        {"eventType":{"id":"et_1","name":"Intro call","slug":"intro","durations":[30]},
        "timezone":"America/New_York","status":"active",
        "slots":[{"start":"2026-07-01T16:00:00Z","end":"2026-07-01T16:30:00Z","startLocal":"2026-07-01T12:00:00-04:00"}]}
        """#
        SequencedURLProtocol.sequence = [.status(200, body: slotsJSON)]
        await viewModel.loadSlots()
        XCTAssertEqual(viewModel.slotOptions.count, 1)
        XCTAssertEqual(viewModel.slotOptions.first?.start, "2026-07-01T16:00:00Z")
        viewModel.toggleSlot("2026-07-01T16:00:00Z")
        XCTAssertTrue(viewModel.selectedSlotIds.contains("2026-07-01T16:00:00Z"))
    }

    func testExpiryMinutesMapping() {
        XCTAssertEqual(OneOffExpiry.h24.minutes, 1440)
        XCTAssertEqual(OneOffExpiry.d7.minutes, 10080)
        XCTAssertEqual(OneOffExpiry.d30.minutes, 43200)
        XCTAssertNil(OneOffExpiry.never.minutes)
    }
}
