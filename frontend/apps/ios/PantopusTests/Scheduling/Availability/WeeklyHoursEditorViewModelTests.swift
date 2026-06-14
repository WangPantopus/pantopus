//
//  WeeklyHoursEditorViewModelTests.swift
//  PantopusTests
//
//  Stream I3 — B5 weekly-hours editor projection tests.
//

@testable import Pantopus
import XCTest

@MainActor
final class WeeklyHoursEditorViewModelTests: XCTestCase {
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

    private static let composite = """
    {"schedules":[{"id":"s1","name":"Working hours","timezone":"America/New_York","is_default":true}],
     "rules":[{"schedule_id":"s1","weekday":1,"start_time":"09:00","end_time":"17:00"}],
     "overrides":[]}
    """

    func testLoadBuildsSevenDaysWithMondayEnabled() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.composite)]
        let viewModel = WeeklyHoursEditorViewModel(scheduleId: "s1", push: { _ in }, client: makeClient())
        await viewModel.load()
        guard case .ready = viewModel.phase else {
            return XCTFail("Expected .ready, got \(viewModel.phase)")
        }
        XCTAssertEqual(viewModel.days.count, 7)
        let monday = viewModel.days.first { $0.weekday == 1 }
        XCTAssertEqual(monday?.isEnabled, true)
        XCTAssertEqual(monday?.ranges.count, 1)
        let sunday = viewModel.days.first { $0.weekday == 0 }
        XCTAssertEqual(sunday?.isEnabled, false)
        XCTAssertFalse(viewModel.isDirty)
        XCTAssertFalse(viewModel.allOff)
    }

    func testEditMarksDirtyThenSaveClearsIt() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.composite),
            .status(200, body: #"{"rules":[{"schedule_id":"s1","weekday":1,"start_time":"09:00","end_time":"17:00"}]}"#)
        ]
        let viewModel = WeeklyHoursEditorViewModel(scheduleId: "s1", push: { _ in }, client: makeClient())
        await viewModel.load()
        viewModel.setEnabled(2, true) // enable Tuesday
        XCTAssertTrue(viewModel.isDirty)
        XCTAssertTrue(viewModel.formValid)
        await viewModel.save()
        XCTAssertNil(viewModel.saveError)
        XCTAssertFalse(viewModel.isDirty)
    }

    func testAllOffWhenEveryDayDisabled() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.composite)]
        let viewModel = WeeklyHoursEditorViewModel(scheduleId: "s1", push: { _ in }, client: makeClient())
        await viewModel.load()
        for weekday in Weekday.displayOrder { viewModel.setEnabled(weekday, false) }
        XCTAssertTrue(viewModel.allOff)
        XCTAssertTrue(viewModel.formValid) // all-off is valid
    }

    func testMissingScheduleProducesError() async {
        SequencedURLProtocol.sequence = [.status(200, body: #"{"schedules":[],"rules":[],"overrides":[]}"#)]
        let viewModel = WeeklyHoursEditorViewModel(scheduleId: "missing", push: { _ in }, client: makeClient())
        await viewModel.load()
        guard case .error = viewModel.phase else {
            return XCTFail("Expected .error, got \(viewModel.phase)")
        }
    }
}
