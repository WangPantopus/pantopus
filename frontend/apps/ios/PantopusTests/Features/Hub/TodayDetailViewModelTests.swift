//
//  TodayDetailViewModelTests.swift
//  PantopusTests
//
//  Covers the Today detail VM (P6.6):
//    - four-state transitions (loading / loaded / empty / error)
//    - `projectToday` extraction from the opaque Hub today payload
//    - `todaysEvents` filtering to the current day + start-time sort
//

import XCTest
@testable import Pantopus

@MainActor
final class TodayDetailViewModelTests: XCTestCase {
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

    private func makeVM() -> TodayDetailViewModel {
        TodayDetailViewModel(api: makeAPI())
    }

    // MARK: - Four states

    func testLoadErrorWhenTodayFails() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{}")]
        let vm = makeVM()
        await vm.load()
        guard case .error = vm.state else {
            return XCTFail("Expected error, got \(vm.state)")
        }
    }

    func testLoadEmptyWhenNoContext() async {
        // today=null and overview unstubbed (599 → swallowed by `try?`).
        SequencedURLProtocol.sequence = [.status(200, body: "{\"today\":null}")]
        let vm = makeVM()
        await vm.load()
        guard case .empty = vm.state else {
            return XCTFail("Expected empty, got \(vm.state)")
        }
    }

    func testLoadLoadedWithWeather() async {
        let body = """
        {"today":{"weather":{"temperatureF":72,"conditions":"Sunny"},\
        "aqi":{"label":"Good","value":35},"commute":{"label":"12 min drive"}}}
        """
        SequencedURLProtocol.sequence = [.status(200, body: body)]
        let vm = makeVM()
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            return XCTFail("Expected loaded, got \(vm.state)")
        }
        XCTAssertEqual(content.temperatureF, 72)
        XCTAssertEqual(content.conditions, "Sunny")
        XCTAssertEqual(content.aqiLabel, "Good")
        XCTAssertEqual(content.aqiValue, 35)
        XCTAssertEqual(content.commute, "12 min drive")
    }

    // MARK: - projectToday

    func testProjectTodayExtractsFields() throws {
        let json = """
        {"today":{"weather":{"temperatureF":58,"conditions":"Cloudy"},\
        "aqi":{"label":"Moderate","value":80},"commute":{"label":"20 min"}}}
        """
        let response = try JSONDecoder().decode(HubTodayResponse.self, from: Data(json.utf8))
        let projection = TodayDetailViewModel.projectToday(response)
        XCTAssertEqual(projection.temperatureF, 58)
        XCTAssertEqual(projection.conditions, "Cloudy")
        XCTAssertEqual(projection.aqiLabel, "Moderate")
        XCTAssertEqual(projection.aqiValue, 80)
        XCTAssertEqual(projection.commute, "20 min")
    }

    func testProjectTodayNilPayload() throws {
        let response = try JSONDecoder().decode(
            HubTodayResponse.self,
            from: Data("{\"today\":null}".utf8)
        )
        let projection = TodayDetailViewModel.projectToday(response)
        XCTAssertNil(projection.temperatureF)
        XCTAssertNil(projection.conditions)
        XCTAssertNil(projection.aqiLabel)
        XCTAssertNil(projection.commute)
    }

    // MARK: - todaysEvents

    private func utcCalendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC") ?? .current
        return calendar
    }

    private func event(id: String, type: String, start: String) -> CalendarEventDTO {
        CalendarEventDTO(id: id, homeId: "home-1", eventType: type, title: "Event \(id)", startAt: start)
    }

    func testTodaysEventsFiltersToTodayAndSorts() throws {
        let now = try XCTUnwrap(ISO8601DateFormatter().date(from: "2026-05-20T12:00:00Z"))
        let events = [
            event(id: "e1", type: "social", start: "2026-05-20T16:00:00Z"),
            event(id: "e2", type: "chore", start: "2026-05-20T09:00:00Z"),
            event(id: "e3", type: "repair", start: "2026-05-21T10:00:00Z"),
        ]
        let rows = TodayDetailViewModel.todaysEvents(events, now: now, calendar: utcCalendar())
        XCTAssertEqual(rows.map(\.id), ["e2", "e1"])
        XCTAssertEqual(rows.first?.typeLabel, "Chore")
    }

    func testTodaysEventsEmptyWhenNoneToday() throws {
        let now = try XCTUnwrap(ISO8601DateFormatter().date(from: "2026-05-20T12:00:00Z"))
        let events = [event(id: "e3", type: "repair", start: "2026-05-25T10:00:00Z")]
        let rows = TodayDetailViewModel.todaysEvents(events, now: now, calendar: utcCalendar())
        XCTAssertTrue(rows.isEmpty)
    }

    func testEventIconMapping() {
        XCTAssertEqual(TodayDetailViewModel.icon(for: "repair"), .hammer)
        XCTAssertEqual(TodayDetailViewModel.icon(for: "pet"), .pawPrint)
        XCTAssertEqual(TodayDetailViewModel.icon(for: "social"), .calendarDays)
    }
}
