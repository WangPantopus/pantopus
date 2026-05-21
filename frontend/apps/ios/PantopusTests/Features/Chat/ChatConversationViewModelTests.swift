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
        URLProtocolStub.reset()
    }

    private func makeAPI() -> APIClient {
        APIClient(
            environment: .current,
            session: TestSession.make(),
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
        URLProtocolStub.stub(
            path: "/api/chat/conversations/u_other/messages",
            response: .json(Self.messagesJSON(
                Self.messageJSON(id: "m1", userId: "u_other", text: "hi"),
                Self.messageJSON(id: "m2", userId: "u_me", text: "hello", createdAt: "2026-04-20T10:00:30.000Z")
            ))
        )
        URLProtocolStub.stub(path: "/api/chat/conversations/u_other/read", response: .json("{}"))
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

    func testSendCapabilityPromptStartsAIThreadWithUserBubble() async {
        let vm = ChatConversationViewModel(
            mode: .ai,
            counterparty: Self.counterpartyAI,
            currentUserId: "u_me",
            api: makeAPI()
        )
        await vm.load()
        // Welcome/empty before any capability is tapped.
        if case .empty = vm.state { /* ok */ } else { XCTFail("Expected .empty welcome state") }
        await vm.sendCapabilityPrompt(ChatPromptChip(id: "price", label: "Price a task", icon: .hammer))
        guard case let .loaded(rows) = vm.state else {
            XCTFail("Expected .loaded after tapping a capability, got \(vm.state)")
            return
        }
        let bubbles = rows.compactMap { row -> ChatBubbleContent? in
            if case let .bubble(content) = row { return content } else { return nil }
        }
        let outgoing = bubbles.first { $0.side == .outgoing }
        XCTAssertNotNil(outgoing, "capability tap should append an outgoing user bubble")
        if case let .text(text)? = outgoing?.body {
            XCTAssertEqual(text, "Price a task")
        } else {
            XCTFail("Expected a text bubble carrying the capability label")
        }
    }

    func testAIActiveSampleRendersStructuredReplyWithEstimate() {
        let rows = ChatConversationSampleData.aiActiveRows
        let bubbles = rows.compactMap { row -> ChatBubbleContent? in
            if case let .bubble(content) = row { return content } else { return nil }
        }
        let aiBubble = bubbles.first { content in
            if case .aiReply = content.body { true } else { false }
        }
        XCTAssertNotNil(aiBubble, "active AI sample should contain an aiReply bubble")
        if case let .aiReply(_, estimate)? = aiBubble?.body {
            XCTAssertNotNil(estimate, "the sample AI reply should carry an estimate card")
            XCTAssertEqual(estimate?.amount, "$55–70")
        } else {
            XCTFail("Expected an aiReply body")
        }
    }

    func testSendFailureMarksClientIdAsFailed() async {
        URLProtocolStub.stub(path: "/api/chat/conversations/u_other/messages", response: .json(Self.messagesJSON()))
        URLProtocolStub.stub(path: "/api/chat/conversations/u_other/read", response: .json("{}"))
        URLProtocolStub.stub(path: "/api/chat/messages", response: .json("{}", status: 500))
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
        let bubbles = rows.compactMap {
            if case let .bubble(content) = $0 { content } else { nil }
        }
        let bubble = bubbles.first { $0.side == .outgoing }
        XCTAssertNotNil(bubble)
        XCTAssertEqual(bubble?.deliveryState, .failed)
    }

    func testLoadOlderPaginatesBackward() async {
        // First load: 1 message at 10:00, hasMore: true.
        // loadOlder(): 1 older message at 09:59, hasMore: false.
        URLProtocolStub.stub(
            path: "/api/chat/conversations/u_other/messages",
            responses: [
                .json(Self.messagesJSON(
                    Self.messageJSON(id: "m2", userId: "u_other", text: "hi"),
                    hasMore: true
                )),
                .json(Self.messagesJSON(
                    Self.messageJSON(id: "m1", userId: "u_other", text: "earlier", createdAt: "2026-04-20T09:59:00.000Z"),
                    hasMore: false
                ))
            ]
        )
        URLProtocolStub.stub(path: "/api/chat/conversations/u_other/read", response: .json("{}"))
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
        URLProtocolStub.stub(
            path: "/api/chat/conversations/u_other/messages",
            responses: [
                .json(Self.messagesJSON(
                    Self.messageJSON(id: "m1", userId: "u_other", text: "hi")
                )),
                // Second fetch (simulating realtime-merge handler calling refresh())
                // returns the same row plus a new server-side echo.
                .json(Self.messagesJSON(
                    Self.messageJSON(id: "m2", userId: "u_other", text: "follow-up", createdAt: "2026-04-20T10:01:00.000Z"),
                    Self.messageJSON(id: "m1", userId: "u_other", text: "hi")
                ))
            ]
        )
        URLProtocolStub.stub(path: "/api/chat/conversations/u_other/read", response: .json("{}"))
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
