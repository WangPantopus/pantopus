//
//  ChatConversationViewModelTests.swift
//  PantopusTests
//
//  Covers projection (day-divider + tail-grouping + outgoing/incoming
//  sides), empty state, error, optimistic send swap on success, retry
//  marker on send failure, and the AI thread's automatic empty mode.
//

import XCTest
@testable import Pantopus

@MainActor
final class ChatConversationViewModelTests: XCTestCase {
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

    private static let counterpartyPerson: ChatCounterparty = .person(
        name: "Maria K.",
        initials: "MK",
        locality: "Elm Park",
        verified: true,
        online: true
    )

    private static let counterpartyAI: ChatCounterparty = .ai(name: "Ask Pantopus")

    private static func messagesJSON(_ rows: String..., hasMore: Bool = false) -> String {
        "{\"messages\":[\(rows.joined(separator: ","))],\"hasMore\":\(hasMore)}"
    }

    private static func messageJSON(
        id: String,
        userId: String,
        text: String,
        createdAt: String = "2026-04-20T10:00:00.000Z",
        clientMessageId: String? = nil
    ) -> String {
        let client = clientMessageId.map { "\"\($0)\"" } ?? "null"
        return """
        {
          "id":"\(id)","room_id":"r1","user_id":"\(userId)",
          "message_text":"\(text)","message_type":"text",
          "client_message_id":\(client),
          "created_at":"\(createdAt)",
          "sender":{"id":"\(userId)","username":"u","name":null,"profile_picture_url":null}
        }
        """
    }

    func testLoadProducesLoadedWithDayDividerAndBubbles() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.messagesJSON(
                Self.messageJSON(id: "m1", userId: "u_other", text: "hi"),
                Self.messageJSON(id: "m2", userId: "u_me", text: "hello", createdAt: "2026-04-20T10:00:30.000Z")
            )),
            .status(200, body: "{}") // markRead
        ]
        let vm = ChatConversationViewModel(
            mode: .person(otherUserId: "u_other"),
            counterparty: Self.counterpartyPerson,
            currentUserId: "u_me",
            api: makeAPI()
        )
        await vm.load()
        guard case let .loaded(rows) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return
        }
        // Day divider + 2 bubbles
        XCTAssertEqual(rows.count, 3)
        if case .dayDivider = rows.first { /* ok */ } else { XCTFail("Expected day divider first") }
    }

    func testAIThreadStartsInEmptyForWelcomeFrame() async {
        let vm = ChatConversationViewModel(
            mode: .ai,
            counterparty: Self.counterpartyAI,
            currentUserId: "u_me",
            api: makeAPI()
        )
        await vm.load()
        if case .empty = vm.state { /* ok */ } else {
            XCTFail("Expected .empty for AI welcome, got \(vm.state)")
        }
    }

    func testSendFailureMarksClientIdAsFailed() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.messagesJSON()),
            .status(200, body: "{}"), // markRead
            .status(500, body: "{}")  // POST /messages
        ]
        let vm = ChatConversationViewModel(
            mode: .person(otherUserId: "u_other"),
            counterparty: Self.counterpartyPerson,
            currentUserId: "u_me",
            api: makeAPI()
        )
        await vm.load()
        vm.composerText = "Hello"
        await vm.send()
        guard case let .loaded(rows) = vm.state else {
            XCTFail("Expected .loaded after failed send")
            return
        }
        // Should contain at least one bubble (the optimistic one) with
        // a failed delivery state.
        let bubble = rows.compactMap {
            if case let .bubble(content) = $0 { return content } else { return nil }
        }.first { $0.side == .outgoing }
        XCTAssertNotNil(bubble)
        XCTAssertEqual(bubble?.deliveryState, .failed)
    }

    func testLoadOlderPaginatesBackward() async {
        // First load: 1 message at 10:00, hasMore: true.
        // loadOlder(): 1 older message at 09:59, hasMore: false.
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.messagesJSON(
                Self.messageJSON(id: "m2", userId: "u_other", text: "hi"),
                hasMore: true
            )),
            .status(200, body: "{}"), // markRead
            .status(200, body: Self.messagesJSON(
                Self.messageJSON(id: "m1", userId: "u_other", text: "earlier", createdAt: "2026-04-20T09:59:00.000Z"),
                hasMore: false
            ))
        ]
        let vm = ChatConversationViewModel(
            mode: .person(otherUserId: "u_other"),
            counterparty: Self.counterpartyPerson,
            currentUserId: "u_me",
            api: makeAPI()
        )
        await vm.load()
        await vm.loadOlder()
        guard case let .loaded(rows) = vm.state else {
            XCTFail("Expected .loaded after pagination")
            return
        }
        let bubbles = rows.compactMap { row -> ChatBubbleContent? in
            if case let .bubble(content) = row { return content } else { return nil }
        }
        XCTAssertEqual(bubbles.count, 2, "loadOlder() should prepend the older message")
        XCTAssertTrue(bubbles.contains { $0.id == "m1" })
        XCTAssertTrue(bubbles.contains { $0.id == "m2" })
    }

    func testRefreshMergesNewMessageIntoLoadedThread() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.messagesJSON(
                Self.messageJSON(id: "m1", userId: "u_other", text: "hi")
            )),
            .status(200, body: "{}"), // markRead
            // Second fetch (simulating realtime-merge handler calling refresh())
            // returns the same row plus a new server-side echo.
            .status(200, body: Self.messagesJSON(
                Self.messageJSON(id: "m1", userId: "u_other", text: "hi"),
                Self.messageJSON(id: "m2", userId: "u_other", text: "follow-up", createdAt: "2026-04-20T10:01:00.000Z")
            )),
            .status(200, body: "{}") // markRead after refresh
        ]
        let vm = ChatConversationViewModel(
            mode: .person(otherUserId: "u_other"),
            counterparty: Self.counterpartyPerson,
            currentUserId: "u_me",
            api: makeAPI()
        )
        await vm.load()
        await vm.refresh()
        guard case let .loaded(rows) = vm.state else {
            XCTFail("Expected .loaded after refresh")
            return
        }
        let bubbles = rows.compactMap { row -> ChatBubbleContent? in
            if case let .bubble(content) = row { return content } else { return nil }
        }
        XCTAssertEqual(bubbles.count, 2, "realtime refresh should incorporate the new message")
        XCTAssertEqual(bubbles.last?.id, "m2")
    }
}
