//
//  HomeCalendarBookingUnionTests.swift
//  PantopusTests
//
//  Stream I10 — Home calendar booking-union + member filter behaviour on the
//  (extended) HomeCalendarViewModel.
//

import XCTest
@testable import Pantopus

@MainActor
final class HomeCalendarBookingUnionTests: XCTestCase {
    override func setUp() {
        super.setUp()
        SequencedURLProtocol.reset()
    }

    private static let fixedNow: Date = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f.date(from: "2025-10-12T12:00:00Z") ?? Date(timeIntervalSince1970: 1_760_270_400)
    }()

    private func makeAPI() -> APIClient {
        APIClient(
            environment: .current,
            session: SequencedURLProtocol.makeSession(),
            retryPolicy: .none
        )
    }

    private func makeVM() -> HomeCalendarViewModel {
        let frozen = Self.fixedNow
        // NB: pass `now:` as a *labelled* argument. The unlabelled trailing
        // closure form `… makeAPI()) { frozen }` binds (via deprecated
        // backward matching) to `onAddEvent`, NOT `now`, leaving the clock at
        // the real `Date()` — which buckets the 2025-10-12 fixtures into the
        // past so the agenda comes back empty.
        let now: @Sendable () -> Date = { frozen }
        return HomeCalendarViewModel(homeId: "home-1", api: makeAPI(), now: now)
    }

    private static let bookingBody = """
    {"events":[
      {"id":"bk-1","home_id":"home-1","event_type":"appointment",
       "title":"Plumber visit","start_at":"2025-10-12T17:00:00Z",
       "source":"booking","booking_status":"pending","booking_id":"bk-1"}
    ]}
    """

    private static let twoMembersBody = """
    {"events":[
      {"id":"e1","home_id":"home-1","event_type":"chore",
       "title":"Trash","start_at":"2025-10-12T09:00:00Z","assigned_to":["u1"]},
      {"id":"e2","home_id":"home-1","event_type":"meal",
       "title":"Dinner","start_at":"2025-10-12T18:00:00Z","assigned_to":["u2"]}
    ]}
    """

    private static let occupantsBody = """
    {"occupants":[
      {"id":"o1","user_id":"u1","is_active":true,"display_name":"Maria Patel"},
      {"id":"o2","user_id":"u2","is_active":true,"display_name":"David Patel"}
    ],"pendingInvites":[]}
    """

    func testBookingRowDeepLinksToBookingDetail() async throws {
        SequencedURLProtocol.sequence = [.status(200, body: Self.bookingBody)]
        let vm = makeVM()
        await vm.load()
        let booking = vm.agendaSections.flatMap(\.items).first { $0.isBooking }
        let item = try XCTUnwrap(booking)
        XCTAssertEqual(item.bookingStatus, "pending")
        vm.openAgendaItem(item)
        guard case let .bookingDetail(owner, bookingId) = vm.presentedRoute?.route else {
            XCTFail("Expected a bookingDetail route, got \(String(describing: vm.presentedRoute?.route))")
            return
        }
        XCTAssertEqual(bookingId, "bk-1")
        guard case let .home(homeId) = owner else {
            XCTFail("Expected a home owner context")
            return
        }
        XCTAssertEqual(homeId, "home-1")
    }

    func testMemberFilterRestrictsAgendaAndClearResets() async {
        // Sequential fetch: events first, occupants second.
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.twoMembersBody),
            .status(200, body: Self.occupantsBody)
        ]
        let vm = makeVM()
        await vm.load()
        XCTAssertEqual(vm.agendaSections.flatMap(\.items).count, 2)

        vm.selectFilter(.member(id: "u1", name: "Maria Patel"))
        let ids = vm.agendaSections.flatMap(\.items).map(\.id)
        XCTAssertEqual(ids, ["e1"])

        vm.clearMemberFilter()
        XCTAssertEqual(vm.memberFilter, .all)
        XCTAssertEqual(vm.agendaSections.flatMap(\.items).count, 2)
        XCTAssertNil(vm.agendaEmpty)
    }

    func testFilterWithNoMatchesYieldsFilteredEmpty() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.twoMembersBody),
            .status(200, body: Self.occupantsBody)
        ]
        let vm = makeVM()
        await vm.load()
        vm.selectFilter(.member(id: "u2", name: "David Patel"))
        // u2 only has the evening dinner — still one row.
        XCTAssertEqual(vm.agendaSections.flatMap(\.items).map(\.id), ["e2"])

        // A member id with no events → filtered-empty.
        vm.selectFilter(.member(id: "ghost", name: "Nobody"))
        XCTAssertTrue(vm.agendaSections.isEmpty)
        XCTAssertEqual(vm.agendaEmpty, .filteredMember(name: "Nobody"))
    }
}
