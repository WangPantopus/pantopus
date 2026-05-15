//
//  NotificationsViewModelTests.swift
//  PantopusTests
//
//  Covers the Notifications center VM: load → loaded/empty/error,

//  MARK: - read optimistic + rollback, read-all sweep, row projection

//  flips on `is_read`, and tap-through routes through DeepLinkRouter.
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

    private static let twoUnreadJSON = """
    {"notifications":[
      {"id":"n1","type":"post","title":"New post","body":"Maya posted",
       "icon":null,"link":"/post/p_1","is_read":false,
       "created_at":"2026-05-15T10:00:00Z","user_id":"u_me"},
      {"id":"n2","type":"gig","title":"New bid","body":"Bid on your gig",
       "icon":null,"link":"/gig/g_1","is_read":false,
       "created_at":"2026-05-15T09:00:00Z","user_id":"u_me"}
    ],"unreadCount":2,"hasMore":false}
    """

    private static let emptyJSON = """
    {"notifications":[],"unreadCount":0,"hasMore":false}
    """

    func testLoadEmptyTransitionsToEmptyState() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.emptyJSON)]
        let vm = NotificationsViewModel(api: makeAPI())
        await vm.load()
        guard case let .empty(content) = vm.state else {
            XCTFail("Expected .empty, got \(vm.state)")
            return
        }
        XCTAssertEqual(content.headline, "All caught up")
        XCTAssertEqual(vm.unreadCount, 0)
        XCTAssertNil(vm.topBarAction)
    }

    func testLoadPopulatedTransitionsToLoaded() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.twoUnreadJSON)]
        let vm = NotificationsViewModel(api: makeAPI())
        await vm.load()
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(sections.first?.rows.count, 2)
        XCTAssertEqual(vm.unreadCount, 2)
        XCTAssertNotNil(vm.topBarAction)
    }

    func testLoadFailureTransitionsError() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{}")]
        let vm = NotificationsViewModel(api: makeAPI())
        await vm.load()
        guard case .error = vm.state else {
            XCTFail("Expected .error")
            return
        }
    }

    func testMarkReadFlipsRowToReadAndPersistsOnSuccess() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.twoUnreadJSON),
            .status(200, body: "{\"ok\":true}")
        ]
        let vm = NotificationsViewModel(api: makeAPI())
        await vm.load()
        XCTAssertEqual(vm.unreadCount, 2)
        await vm.markRead(id: "n1")
        XCTAssertEqual(vm.unreadCount, 1)
    }

    func testMarkReadRollsBackOnFailure() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.twoUnreadJSON),
            .status(500, body: "{}")
        ]
        let vm = NotificationsViewModel(api: makeAPI())
        await vm.load()
        await vm.markRead(id: "n1")
        XCTAssertEqual(vm.unreadCount, 2)
    }

    func testMarkAllReadClearsUnreadCount() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.twoUnreadJSON),
            .status(200, body: "{\"count\":0}")
        ]
        let vm = NotificationsViewModel(api: makeAPI())
        await vm.load()
        await vm.markAllRead()
        XCTAssertEqual(vm.unreadCount, 0)
        XCTAssertNil(vm.topBarAction)
    }

    func testMarkAllReadRollsBackOnFailure() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.twoUnreadJSON),
            .status(500, body: "{}")
        ]
        let vm = NotificationsViewModel(api: makeAPI())
        await vm.load()
        await vm.markAllRead()
        XCTAssertEqual(vm.unreadCount, 2)
    }

    func testRefreshHitsListAgain() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.twoUnreadJSON),
            .status(200, body: Self.emptyJSON)
        ]
        let vm = NotificationsViewModel(api: makeAPI())
        await vm.load()
        await vm.refresh()
        guard case .empty = vm.state else {
            XCTFail("Expected .empty after server cleared the list")
            return
        }
    }

    // MARK: - Row projection

    func testRowMappingRendersUnreadAsInfoChip() {
        let dto =
            NotificationDTO(
                id: "n1", userId: nil, type: "post", title: "Title", body: "Body",
                icon: nil, link: "/post/p_1", isRead: false,
                createdAt: "2026-05-15T10:00:00Z", context: nil
            )
        let row = NotificationsViewModel.row(dto: dto, onSelect: {})
        if case let .statusChip(text, variant) = row.trailing {
            XCTAssertEqual(text, "NEW")
            XCTAssertEqual(variant, .info)
        } else {
            XCTFail("Expected status chip trailing")
        }
    }

    func testRowMappingRendersReadAsChevron() {
        let dto =
            NotificationDTO(
                id: "n2", userId: nil, type: "post", title: "Title", body: "Body",
                icon: nil, link: "/post/p_1", isRead: true,
                createdAt: "2026-05-15T10:00:00Z", context: nil
            )
        let row = NotificationsViewModel.row(dto: dto, onSelect: {})
        if case .chevron = row.trailing {
            // ok
        } else {
            XCTFail("Expected chevron trailing for read rows")
        }
    }

    func testIconForGigMapsToHammer() {
        XCTAssertEqual(NotificationsViewModel.iconFor(type: "gig"), .hammer)
        XCTAssertEqual(NotificationsViewModel.iconFor(type: "chat_message"), .inbox)
        XCTAssertEqual(NotificationsViewModel.iconFor(type: "unknown"), .bell)
    }
}
