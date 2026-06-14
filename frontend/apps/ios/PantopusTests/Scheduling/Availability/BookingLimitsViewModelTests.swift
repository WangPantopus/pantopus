//
//  BookingLimitsViewModelTests.swift
//  PantopusTests
//
//  Stream I3 — B7 booking-limits projection tests.
//

import XCTest
@testable import Pantopus

@MainActor
final class BookingLimitsViewModelTests: XCTestCase {
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

    private static let detail = """
    {"eventType":{"id":"et1","name":"Intro call","slug":"intro","durations":[30],
     "min_notice_min":240,"max_horizon_days":30,"slot_interval_min":30,"daily_cap":5,"per_booker_cap":2},
     "assignees":[],"questions":[]}
    """

    private static let updated = #"{"eventType":{"id":"et1","name":"Intro call","slug":"intro","durations":[30]}}"#

    func testLoadAppliesFields() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.detail)]
        let viewModel = BookingLimitsViewModel(owner: .personal, eventTypeId: "et1", client: makeClient())
        await viewModel.load()
        guard case .ready = viewModel.phase else {
            return XCTFail("Expected .ready, got \(viewModel.phase)")
        }
        XCTAssertEqual(viewModel.minNoticeHours, 4) // 240 / 60
        XCTAssertEqual(viewModel.horizonDays, 30)
        XCTAssertEqual(viewModel.slotInterval, .halfHour)
        XCTAssertTrue(viewModel.limitPerDay)
        XCTAssertEqual(viewModel.dailyCap, 5)
        XCTAssertTrue(viewModel.limitPerPerson)
        XCTAssertEqual(viewModel.perBookerCap, 2)
        XCTAssertFalse(viewModel.isDirty)
        XCTAssertFalse(viewModel.windowConflict)
    }

    func testWindowConflictDisablesSave() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.detail)]
        let viewModel = BookingLimitsViewModel(owner: .personal, eventTypeId: "et1", client: makeClient())
        await viewModel.load()
        viewModel.minNoticeHours = 1000 // > 30 days * 24h
        XCTAssertTrue(viewModel.windowConflict)
        XCTAssertFalse(viewModel.isValid)
        XCTAssertFalse(viewModel.canSave)
    }

    func testSaveSucceedsAfterEdit() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.detail), .status(200, body: Self.updated)]
        let viewModel = BookingLimitsViewModel(owner: .personal, eventTypeId: "et1", client: makeClient())
        await viewModel.load()
        viewModel.horizonDays = 45
        XCTAssertTrue(viewModel.canSave)
        let ok = await viewModel.save()
        XCTAssertTrue(ok)
        XCTAssertNil(viewModel.saveError)
    }

    /// Per-field-dirty: editing only the window must NOT re-send the untouched
    /// (and possibly lossy) min_notice / slot_interval / cap fields.
    func testUntouchedFieldsNotResent() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.detail), .status(200, body: Self.updated)]
        let viewModel = BookingLimitsViewModel(owner: .personal, eventTypeId: "et1", client: makeClient())
        await viewModel.load()
        viewModel.horizonDays = 45
        _ = await viewModel.save()

        guard let put = SequencedURLProtocol.capturedRequests.last else {
            return XCTFail("Missing PUT request")
        }
        let body = Self.bodyData(from: put)
        guard let json = try? JSONSerialization.jsonObject(with: body) as? [String: Any] else {
            return XCTFail("Missing body")
        }
        XCTAssertEqual(json["max_horizon_days"] as? Int, 45)
        XCTAssertNil(json["min_notice_min"])
        XCTAssertNil(json["slot_interval_min"])
        XCTAssertNil(json["daily_cap"])
        XCTAssertNil(json["per_booker_cap"])
    }

    /// Turning a previously-set cap OFF can't be persisted (omitted != null), so
    /// save must report failure (not a false success) and resync.
    func testCapClearReportsUnsupported() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.detail), .status(200, body: Self.detail)]
        let viewModel = BookingLimitsViewModel(owner: .personal, eventTypeId: "et1", client: makeClient())
        await viewModel.load()
        XCTAssertTrue(viewModel.limitPerDay)
        viewModel.limitPerDay = false // attempt to clear the daily cap
        XCTAssertTrue(viewModel.isDirty)
        let ok = await viewModel.save()
        XCTAssertFalse(ok)
        XCTAssertNotNil(viewModel.saveError)
        XCTAssertTrue(viewModel.limitPerDay) // reverted by resync — UI matches server
    }

    func testLoadFailureProducesError() async {
        SequencedURLProtocol.sequence = [.status(404, body: #"{"error":"NOT_FOUND"}"#)]
        let viewModel = BookingLimitsViewModel(owner: .personal, eventTypeId: "et1", client: makeClient())
        await viewModel.load()
        guard case .error = viewModel.phase else {
            return XCTFail("Expected .error, got \(viewModel.phase)")
        }
    }

    /// URLProtocol-stubbed sessions expose the body via httpBodyStream.
    private static func bodyData(from request: URLRequest) -> Data {
        if let body = request.httpBody { return body }
        guard let stream = request.httpBodyStream else { return Data() }
        var data = Data()
        stream.open()
        defer { stream.close() }
        let bufferSize = 4096
        var buffer = [UInt8](repeating: 0, count: bufferSize)
        while stream.hasBytesAvailable {
            let read = stream.read(&buffer, maxLength: bufferSize)
            if read <= 0 { break }
            data.append(buffer, count: read)
        }
        return data
    }
}
