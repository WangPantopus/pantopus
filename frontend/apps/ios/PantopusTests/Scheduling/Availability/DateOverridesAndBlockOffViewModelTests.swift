//
//  DateOverridesAndBlockOffViewModelTests.swift
//  PantopusTests
//
//  Stream I3 — B6 date-overrides + B9 block-off projection tests.
//

@testable import Pantopus
import XCTest

@MainActor
final class DateOverridesAndBlockOffViewModelTests: XCTestCase {
    override func setUp() {
        super.setUp()
        SequencedURLProtocol.reset()
    }

    private func makeClient() -> SchedulingClient {
        SchedulingClient(client: APIClient(
            environment: .current,
            session: SequencedURLProtocol.makeSession(),
            retryPolicy: .none
        ))
    }

    // MARK: B6 — Date Overrides

    func testOverridesFilterBySchedule() async {
        SequencedURLProtocol.sequence = [.status(200, body: """
        {"schedules":[],"rules":[],"overrides":[
          {"schedule_id":"s1","date":"2026-07-04","is_unavailable":true},
          {"schedule_id":"s2","date":"2026-08-01","is_unavailable":true}
        ]}
        """)]
        let viewModel = DateOverridesViewModel(scheduleId: "s1", client: makeClient())
        await viewModel.load()
        guard case .ready = viewModel.phase else {
            return XCTFail("Expected .ready, got \(viewModel.phase)")
        }
        XCTAssertEqual(viewModel.overrides.count, 1)
        XCTAssertEqual(viewModel.overrides.first?.date, "2026-07-04")
    }

    func testAddOverridePersistsWholeSet() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: #"{"schedules":[],"rules":[],"overrides":[]}"#),
            .status(200, body: #"{"overrides":[{"schedule_id":"s1","date":"2026-12-25","is_unavailable":true}]}"#)
        ]
        let viewModel = DateOverridesViewModel(scheduleId: "s1", client: makeClient())
        await viewModel.load()
        viewModel.mode = .unavailable
        await viewModel.addOverride()
        XCTAssertEqual(viewModel.overrides.count, 1)
        XCTAssertEqual(viewModel.overrides.first?.isUnavailable, true)
    }

    // MARK: B9 — Block Off Time

    func testBlockOffValidity() {
        let viewModel = BlockOffTimeViewModel(client: makeClient())
        viewModel.allDay = false
        viewModel.startTime = .nineAM
        viewModel.endTime = .fivePM
        XCTAssertTrue(viewModel.isValid)
        viewModel.startTime = .fivePM
        viewModel.endTime = .nineAM
        XCTAssertFalse(viewModel.isValid)
        viewModel.allDay = true
        XCTAssertTrue(viewModel.isValid)
    }

    func testBlockOffSaveCreatesBlock() async {
        SequencedURLProtocol.sequence = [.status(200, body: """
        {"block":{"id":"b1","start_at":"2026-06-18T14:00:00Z","end_at":"2026-06-18T15:00:00Z"}}
        """)]
        let viewModel = BlockOffTimeViewModel(client: makeClient())
        viewModel.reason = "Dentist"
        let ok = await viewModel.save()
        XCTAssertTrue(ok)
        XCTAssertNil(viewModel.saveError)
    }
}
