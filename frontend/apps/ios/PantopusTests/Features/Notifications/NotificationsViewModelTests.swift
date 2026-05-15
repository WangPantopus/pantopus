//
//  NotificationsViewModelTests.swift
//  PantopusTests
//
//  T5.1 — Notifications V2. Covers:
//    - load → loaded / empty / error transitions
//    - tab switching (All / Unread) refetches with `?unread=true`
//    - mark-read + mark-all-read optimistic + rollback
//    - row mapping per type (icon + chip variant + tile colours)
//    - date bucketing across midnight + timezone + "no today" cases
//    - relative-time formatting
//    - empty Unread CTA re-keys back to the All tab
//

import XCTest
@testable import Pantopus

@MainActor
final class NotificationsViewModelTests: XCTestCase {
    override func setUp() {
        super.setUp()
        SequencedURLProtocol.reset()
        _ = DeepLinkRouter.shared.consume()
    }

    private func makeAPI() -> APIClient {
        APIClient(
            environment: .current,
            session: SequencedURLProtocol.makeSession(),
            retryPolicy: .none
        )
    }

    /// Fixed clock + calendar so date-bucketing tests are deterministic.
    /// 2026-05-15 12:00:00 UTC — Friday.
    private static let fixedNow: Date = {
        var components = DateComponents()
        components.year = 2026
        components.month = 5
        components.day = 15
        components.hour = 12
        components.minute = 0
        components.second = 0
        components.timeZone = TimeZone(identifier: "UTC")
        return Calendar(identifier: .gregorian).date(from: components)!
    }()

    private static let utcCalendar: Calendar = {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        return cal
    }()

    private static let utc = TimeZone(identifier: "UTC")!

    private func makeVM(api: APIClient? = nil) -> NotificationsViewModel {
        NotificationsViewModel(
            api: api ?? makeAPI(),
            now: { Self.fixedNow },
            calendar: Self.utcCalendar,
            timeZone: Self.utc
        )
    }

    private static let twoUnreadJSON = """
    {"notifications":[
      {"id":"n1","type":"reply","title":"Maria replied",
       "body":"Sounds great","icon":null,"link":"/post/p_1","is_read":false,
       "created_at":"2026-05-15T10:00:00Z","user_id":"u_me"},
      {"id":"n2","type":"gig","title":"New gig",
       "body":"$80 gig 0.4mi","icon":null,"link":"/gig/g_1","is_read":false,
       "created_at":"2026-05-15T09:00:00Z","user_id":"u_me"}
    ],"unreadCount":2,"hasMore":false}
    """

    private static let mixedTodayAndEarlierJSON = """
    {"notifications":[
      {"id":"n1","type":"reply","title":"Today reply",
       "body":"hi","icon":null,"link":null,"is_read":false,
       "created_at":"2026-05-15T10:00:00Z","user_id":"u_me"},
      {"id":"n2","type":"listing","title":"Yesterday listing",
       "body":"price drop","icon":null,"link":null,"is_read":true,
       "created_at":"2026-05-14T15:00:00Z","user_id":"u_me"},
      {"id":"n3","type":"safety","title":"Tuesday alert",
       "body":"alert","icon":null,"link":null,"is_read":true,
       "created_at":"2026-05-12T18:00:00Z","user_id":"u_me"}
    ],"unreadCount":1,"hasMore":false}
    """

    private static let emptyJSON = """
    {"notifications":[],"unreadCount":0,"hasMore":false}
    """

    // MARK: - Lifecycle

    func testLoadEmptyTransitionsToAllTabEmpty() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.emptyJSON)]
        let vm = makeVM()
        await vm.load()
        guard case let .empty(content) = vm.state else {
            XCTFail("Expected .empty, got \(vm.state)")
            return
        }
        XCTAssertEqual(content.headline, "All caught up")
        XCTAssertEqual(vm.unreadCount, 0)
        XCTAssertNotNil(vm.topBarAction)
        XCTAssertEqual(vm.topBarAction?.isEnabled, false)
    }

    func testLoadPopulatedTransitionsToLoaded() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.twoUnreadJSON)]
        let vm = makeVM()
        await vm.load()
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(sections.first?.rows.count, 2)
        XCTAssertEqual(vm.unreadCount, 2)
        XCTAssertEqual(vm.topBarAction?.isEnabled, true)
    }

    func testLoadFailureTransitionsToError() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{}")]
        let vm = makeVM()
        await vm.load()
        guard case .error = vm.state else {
            XCTFail("Expected .error")
            return
        }
    }

    // MARK: - Tabs

    func testTabsExposeAllAndUnreadWithCounts() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.twoUnreadJSON)]
        let vm = makeVM()
        await vm.load()
        XCTAssertEqual(vm.tabs.count, 2)
        XCTAssertEqual(vm.tabs[0].id, NotificationsTab.all)
        XCTAssertEqual(vm.tabs[1].id, NotificationsTab.unread)
        XCTAssertEqual(vm.tabs[0].label, "All")
        XCTAssertEqual(vm.tabs[1].label, "Unread")
        XCTAssertEqual(vm.tabs[0].count, 2)
        XCTAssertEqual(vm.tabs[1].count, 2)
        XCTAssertEqual(vm.selectedTab, NotificationsTab.all)
    }

    func testSelectingUnreadTabRefetchesWithUnreadFilter() async {
        let unreadOnlyJSON = """
        {"notifications":[
          {"id":"n1","type":"reply","title":"Unread only",
           "body":"hi","icon":null,"link":null,"is_read":false,
           "created_at":"2026-05-15T10:00:00Z","user_id":"u_me"}
        ],"unreadCount":1,"hasMore":false}
        """
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.twoUnreadJSON),
            .status(200, body: unreadOnlyJSON)
        ]
        let vm = makeVM()
        await vm.load()
        vm.selectedTab = NotificationsTab.unread
        try? await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertEqual(vm.selectedTab, NotificationsTab.unread)
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded after tab switch")
            return
        }
        XCTAssertEqual(sections.flatMap(\.rows).count, 1)
    }

    func testEmptyUnreadCTASwitchesBackToAllTab() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.twoUnreadJSON),
            .status(200, body: Self.emptyJSON),
            .status(200, body: Self.twoUnreadJSON)
        ]
        let vm = makeVM()
        await vm.load()
        vm.selectedTab = NotificationsTab.unread
        try? await Task.sleep(nanoseconds: 50_000_000)
        guard case let .empty(content) = vm.state else {
            XCTFail("Expected .empty unread state")
            return
        }
        XCTAssertEqual(content.ctaTitle, "View all notifications")
        content.onCTA?()
        try? await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertEqual(vm.selectedTab, NotificationsTab.all)
    }

    // MARK: - Mark read

    func testMarkReadFlipsRowAndPersists() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.twoUnreadJSON),
            .status(200, body: "{\"ok\":true}")
        ]
        let vm = makeVM()
        await vm.load()
        await vm.markRead(id: "n1")
        XCTAssertEqual(vm.unreadCount, 1)
    }

    func testMarkReadRollsBackOnFailure() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.twoUnreadJSON),
            .status(500, body: "{}")
        ]
        let vm = makeVM()
        await vm.load()
        await vm.markRead(id: "n1")
        XCTAssertEqual(vm.unreadCount, 2)
    }

    func testMarkAllReadClearsUnreadAndDisablesAction() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.twoUnreadJSON),
            .status(200, body: "{\"count\":0}")
        ]
        let vm = makeVM()
        await vm.load()
        await vm.markAllRead()
        XCTAssertEqual(vm.unreadCount, 0)
        XCTAssertEqual(vm.topBarAction?.isEnabled, false)
    }

    func testMarkAllReadRollsBackOnFailure() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.twoUnreadJSON),
            .status(500, body: "{}")
        ]
        let vm = makeVM()
        await vm.load()
        await vm.markAllRead()
        XCTAssertEqual(vm.unreadCount, 2)
        XCTAssertEqual(vm.topBarAction?.isEnabled, true)
    }

    // MARK: - Row projection per type

    func testRowMappingForReplyTypeUsesPersonalChipAndMessageCircleIcon() {
        let dto = makeDTO(id: "n", type: "reply", isRead: false)
        let row = NotificationsViewModel.row(
            dto: dto,
            now: Self.fixedNow,
            calendar: Self.utcCalendar,
            timeZone: Self.utc
        ) {}
        guard case let .typeIcon(icon, _, _) = row.leading else {
            XCTFail("Expected typeIcon leading")
            return
        }
        XCTAssertEqual(icon, .messageCircle)
        XCTAssertEqual(row.chips?.first?.icon, .messageCircle)
        XCTAssertEqual(row.chips?.first?.text, "Reply")
        if case let .status(variant) = row.chips?.first?.tint {
            XCTAssertEqual(variant, .personal)
        } else {
            XCTFail("Expected status chip tint")
        }
    }

    func testRowMappingForMentionUsesBusinessAndAtSign() {
        let dto = makeDTO(id: "n", type: "mention")
        let row = NotificationsViewModel.row(
            dto: dto,
            now: Self.fixedNow,
            calendar: Self.utcCalendar,
            timeZone: Self.utc
        ) {}
        if case let .typeIcon(icon, _, _) = row.leading {
            XCTAssertEqual(icon, .atSign)
        }
        if case let .status(variant) = row.chips?.first?.tint {
            XCTAssertEqual(variant, .business)
        }
    }

    func testRowMappingForClaimUsesSuccessAndBadgeCheck() {
        let dto = makeDTO(id: "n", type: "claim")
        let row = NotificationsViewModel.row(
            dto: dto,
            now: Self.fixedNow,
            calendar: Self.utcCalendar,
            timeZone: Self.utc
        ) {}
        if case let .typeIcon(icon, _, _) = row.leading {
            XCTAssertEqual(icon, .badgeCheck)
        }
        if case let .status(variant) = row.chips?.first?.tint {
            XCTAssertEqual(variant, .success)
        }
    }

    func testRowMappingForGigUsesWarningAndBriefcase() {
        let dto = makeDTO(id: "n", type: "gig")
        let row = NotificationsViewModel.row(
            dto: dto,
            now: Self.fixedNow,
            calendar: Self.utcCalendar,
            timeZone: Self.utc
        ) {}
        if case let .typeIcon(icon, _, _) = row.leading {
            XCTAssertEqual(icon, .briefcase)
        }
        if case let .status(variant) = row.chips?.first?.tint {
            XCTAssertEqual(variant, .warning)
        }
    }

    func testRowMappingForListingUsesHomeAndTag() {
        let dto = makeDTO(id: "n", type: "listing")
        let row = NotificationsViewModel.row(
            dto: dto,
            now: Self.fixedNow,
            calendar: Self.utcCalendar,
            timeZone: Self.utc
        ) {}
        if case let .typeIcon(icon, _, _) = row.leading {
            XCTAssertEqual(icon, .tag)
        }
        if case let .status(variant) = row.chips?.first?.tint {
            XCTAssertEqual(variant, .home)
        }
    }

    func testRowMappingForSafetyUsesErrorAndShieldAlert() {
        let dto = makeDTO(id: "n", type: "safety")
        let row = NotificationsViewModel.row(
            dto: dto,
            now: Self.fixedNow,
            calendar: Self.utcCalendar,
            timeZone: Self.utc
        ) {}
        if case let .typeIcon(icon, _, _) = row.leading {
            XCTAssertEqual(icon, .shieldAlert)
        }
        if case let .status(variant) = row.chips?.first?.tint {
            XCTAssertEqual(variant, .error)
        }
    }

    func testRowMappingForSystemUsesNeutralAndInfo() {
        let dto = makeDTO(id: "n", type: "system")
        let row = NotificationsViewModel.row(
            dto: dto,
            now: Self.fixedNow,
            calendar: Self.utcCalendar,
            timeZone: Self.utc
        ) {}
        if case let .typeIcon(icon, _, _) = row.leading {
            XCTAssertEqual(icon, .info)
        }
        if case let .status(variant) = row.chips?.first?.tint {
            XCTAssertEqual(variant, .neutral)
        }
    }

    func testUnreadRowGetsUnreadHighlight() {
        let dto = makeDTO(id: "n", type: "reply", isRead: false)
        let row = NotificationsViewModel.row(
            dto: dto,
            now: Self.fixedNow,
            calendar: Self.utcCalendar,
            timeZone: Self.utc
        ) {}
        XCTAssertEqual(row.highlight, .unread)
    }

    func testReadRowHasNoHighlight() {
        let dto = makeDTO(id: "n", type: "reply", isRead: true)
        let row = NotificationsViewModel.row(
            dto: dto,
            now: Self.fixedNow,
            calendar: Self.utcCalendar,
            timeZone: Self.utc
        ) {}
        XCTAssertNil(row.highlight)
    }

    // MARK: - Date bucketing

    func testMakeSectionsBucketsTodayAndEarlier() {
        let dtos = [
            makeDTO(id: "today", createdAt: "2026-05-15T08:00:00Z"),
            makeDTO(id: "yesterday", createdAt: "2026-05-14T20:00:00Z"),
            makeDTO(id: "tuesday", createdAt: "2026-05-12T20:00:00Z")
        ]
        let sections = NotificationsViewModel.makeSections(
            dtos,
            now: Self.fixedNow,
            calendar: Self.utcCalendar,
            timeZone: Self.utc
        ) { _ in }
        XCTAssertEqual(sections.count, 2)
        XCTAssertEqual(sections[0].header, "Today")
        XCTAssertEqual(sections[0].rows.map(\.id), ["today"])
        XCTAssertEqual(sections[1].header, "Earlier")
        XCTAssertEqual(sections[1].rows.map(\.id), ["yesterday", "tuesday"])
    }

    func testMakeSectionsOmitsTodayWhenNoTodayItems() {
        let dtos = [
            makeDTO(id: "y", createdAt: "2026-05-14T08:00:00Z"),
            makeDTO(id: "w", createdAt: "2026-05-10T08:00:00Z")
        ]
        let sections = NotificationsViewModel.makeSections(
            dtos,
            now: Self.fixedNow,
            calendar: Self.utcCalendar,
            timeZone: Self.utc
        ) { _ in }
        XCTAssertEqual(sections.count, 1)
        XCTAssertEqual(sections[0].header, "Earlier")
    }

    func testMakeSectionsCrossesMidnightByCalendarNotByElapsedTime() {
        // 25 hours before fixedNow (12:00 UTC May 15) is 11:00 May 14 —
        // still "Earlier" because it's a different calendar day.
        let dtos = [makeDTO(id: "n", createdAt: "2026-05-14T11:00:00Z")]
        let sections = NotificationsViewModel.makeSections(
            dtos,
            now: Self.fixedNow,
            calendar: Self.utcCalendar,
            timeZone: Self.utc
        ) { _ in }
        XCTAssertEqual(sections.first?.header, "Earlier")
    }

    func testMakeSectionsRespectsLocalTimezone() {
        // 2026-05-15T01:00:00Z is 17:00 May 14 in PST. Under PST it's
        // "Earlier"; under UTC it's "Today".
        let dtos = [makeDTO(id: "n", createdAt: "2026-05-15T01:00:00Z")]
        let pst = TimeZone(identifier: "America/Los_Angeles")!
        var pstCal = Calendar(identifier: .gregorian)
        pstCal.timeZone = pst
        let pstSections = NotificationsViewModel.makeSections(
            dtos,
            now: Self.fixedNow,
            calendar: pstCal,
            timeZone: pst
        ) { _ in }
        XCTAssertEqual(pstSections.first?.header, "Earlier")
        let utcSections = NotificationsViewModel.makeSections(
            dtos,
            now: Self.fixedNow,
            calendar: Self.utcCalendar,
            timeZone: Self.utc
        ) { _ in }
        XCTAssertEqual(utcSections.first?.header, "Today")
    }

    // MARK: - Relative time

    func testRelativeTimeFormatting() {
        let utc = TimeZone(identifier: "UTC")!
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = utc
        XCTAssertEqual(
            NotificationsViewModel.formatRelativeTime(
                "2026-05-15T11:55:00Z",
                now: Self.fixedNow,
                calendar: cal,
                timeZone: utc
            ),
            "5m"
        )
        XCTAssertEqual(
            NotificationsViewModel.formatRelativeTime(
                "2026-05-15T09:00:00Z",
                now: Self.fixedNow,
                calendar: cal,
                timeZone: utc
            ),
            "3h"
        )
        XCTAssertEqual(
            NotificationsViewModel.formatRelativeTime(
                "2026-05-14T08:00:00Z",
                now: Self.fixedNow,
                calendar: cal,
                timeZone: utc
            ),
            "Yesterday"
        )
        XCTAssertEqual(
            NotificationsViewModel.formatRelativeTime(
                "2026-05-12T08:00:00Z",
                now: Self.fixedNow,
                calendar: cal,
                timeZone: utc
            ),
            "Tue"
        )
        XCTAssertEqual(
            NotificationsViewModel.formatRelativeTime(
                "2026-04-20T08:00:00Z",
                now: Self.fixedNow,
                calendar: cal,
                timeZone: utc
            ),
            "Apr 20"
        )
    }

    // MARK: - Refresh

    func testRefreshHitsListAgain() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.twoUnreadJSON),
            .status(200, body: Self.emptyJSON)
        ]
        let vm = makeVM()
        await vm.load()
        await vm.refresh()
        if case .empty = vm.state { /* ok */ } else {
            XCTFail("Expected .empty after server cleared the list")
        }
    }

    // MARK: - Helpers

    private func makeDTO(
        id: String,
        type: String? = "reply",
        isRead: Bool = false,
        createdAt: String = "2026-05-15T10:00:00Z",
        title: String? = "Title",
        body: String? = "Body"
    ) -> NotificationDTO {
        NotificationDTO(
            id: id,
            userId: "u_me",
            type: type,
            title: title,
            body: body,
            icon: nil,
            link: "/post/p_\(id)",
            isRead: isRead,
            createdAt: createdAt,
            context: nil
        )
    }
}
