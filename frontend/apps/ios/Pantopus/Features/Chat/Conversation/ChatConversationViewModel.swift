//
//  ChatConversationViewModel.swift
//  Pantopus
//
//  Backs the chat conversation screen (T2.2). Loads either the
//  room-message or person-conversation endpoint depending on the
//  route mode, projects rows into bubbles + day dividers + tail
//  groupings, dispatches optimistic sends with client-id → server-id
//  swap, debounces markRead, and reacts to socket events.
//

// swiftlint:disable file_length type_body_length

import Foundation
import Logging
import Observation

/// Source-of-truth identifier for the thread, mirroring backend dual
/// mode. `.room` hits `/rooms/:id/messages`; `.person` hits
/// `/conversations/:otherUserId/messages`. `.ai` is a synthetic mode
/// for the "Ask Pantopus" thread (no backend wiring today — SSE
/// streaming via `/api/ai/chat` lands later).
public enum ChatThreadMode: Sendable, Hashable {
    case room(id: String)
    case person(otherUserId: String)
    case ai
}

@Observable
@MainActor
public final class ChatConversationViewModel {
    /// Current render state.
    public private(set) var state: ChatConversationState = .loading

    /// Header counterparty data (drives the top-bar variant).
    public private(set) var counterparty: ChatCounterparty

    /// Live composer text. Bound by the view's `TextField`.
    public var composerText: String = ""

    /// True while a send is in flight.
    public private(set) var isSending: Bool = false

    /// Set when typing indicator should render above the composer.
    public private(set) var isCounterpartyTyping: Bool = false

    /// Suggested prompts for the AI welcome card.
    public let aiPrompts: [ChatPromptChip]

    /// Quick-start chips for the empty state.
    public let emptyChips: [ChatPromptChip]

    /// Whether the composer's send disc is enabled (text present + not
    /// in flight). Bound by the view.
    public var canSend: Bool {
        !composerText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isSending
    }

    private let api: APIClient
    private let socket: SocketClient
    private let mode: ChatThreadMode
    private let currentUserId: String
    private let logger = Logger(label: "app.pantopus.ios.ChatConversation")

    private var messages: [ChatMessageDTO] = []
    private var pendingByClientId: [String: ChatMessageDTO] = [:]
    private var failedClientIds: Set<String> = []
    private var hasMore: Bool = false
    private var oldestCursor: String?
    private var markReadTask: Task<Void, Never>?
    private var socketTasks: [Task<Void, Never>] = []

    init(
        mode: ChatThreadMode,
        counterparty: ChatCounterparty,
        currentUserId: String,
        api: APIClient = .shared,
        socket: SocketClient = .shared
    ) {
        self.mode = mode
        self.counterparty = counterparty
        self.currentUserId = currentUserId
        self.api = api
        self.socket = socket
        aiPrompts = [
            ChatPromptChip(id: "mail", label: "Summarize my inbox", icon: .mailbox),
            ChatPromptChip(id: "task", label: "Post a task", icon: .edit2),
            ChatPromptChip(id: "handy", label: "Find a handyman nearby", icon: .hammer)
        ]
        emptyChips = [
            ChatPromptChip(id: "intro", label: "Introduce yourself", icon: .user),
            ChatPromptChip(id: "gig", label: "Ask about the gig", icon: .hammer),
            ChatPromptChip(id: "listing", label: "Share a listing", icon: .shoppingBag)
        ]
    }

    // No `deinit { cancel }` — Swift 6's strict concurrency disallows
    // touching `@MainActor`-isolated stored properties from the
    // nonisolated `deinit`. The view calls `teardown()` from
    // `.onDisappear`, and each task captures `[weak self]` so it
    // exits cleanly once the VM is deallocated.

    // MARK: - Public API

    public func load() async {
        if case .loaded = state { return }
        await fetch(initial: true)
        subscribeToSockets()
    }

    public func refresh() async {
        await fetch(initial: true)
    }

    /// Scroll-to-top trigger — fetch the next older page.
    public func loadOlder() async {
        guard hasMore, let cursor = oldestCursor else { return }
        await fetch(initial: false, before: cursor)
    }

    /// Send the current composer text. Optimistic — prepends a row
    /// with a client-side id; on success swaps to the server id, on
    /// failure marks the row as failed for the view's retry CTA.
    public func send() async {
        guard canSend else { return }
        let trimmed = composerText.trimmingCharacters(in: .whitespacesAndNewlines)
        composerText = ""
        isSending = true
        defer { isSending = false }

        // AI thread: no backend wired in T2.2; surface a placeholder
        // optimistic row so the design's "Ask anything…" composer
        // still feels alive.
        if case .ai = mode {
            let pending = optimisticMessage(text: trimmed, roomId: "ai")
            pendingByClientId[pending.clientMessageId ?? pending.id] = pending
            rebuild()
            return
        }

        guard let roomId = await resolveRoomId() else {
            logger.warning("send aborted — no resolvable room id for mode \(String(describing: mode))")
            return
        }

        let pending = optimisticMessage(text: trimmed, roomId: roomId)
        pendingByClientId[pending.clientMessageId ?? pending.id] = pending
        rebuild()

        do {
            let response: SendChatMessageResponse = try await api.request(
                ChatEndpoints.sendMessage(
                    body: SendChatMessageBody(
                        roomId: roomId,
                        messageText: trimmed,
                        clientMessageId: pending.clientMessageId
                    )
                )
            )
            // Swap optimistic → server message.
            if let clientId = pending.clientMessageId {
                pendingByClientId[clientId] = nil
            }
            messages.append(response.message)
            rebuild()
            scheduleMarkRead(for: roomId)
        } catch {
            if let clientId = pending.clientMessageId {
                failedClientIds.insert(clientId)
            }
            logger.warning("chat send failed: \(error)")
            rebuild()
        }
    }

    /// Retry a failed optimistic send.
    public func retry(clientId: String) async {
        guard let pending = pendingByClientId[clientId], let text = pending.messageText else { return }
        failedClientIds.remove(clientId)
        composerText = text
        pendingByClientId[clientId] = nil
        rebuild()
        await send()
    }

    /// Toggle a reaction on a message.
    public func react(messageId: String, reaction: String) async {
        do {
            _ = try await api.request(
                ChatEndpoints.reactToMessage(id: messageId, reaction: reaction),
                as: ReactToChatMessageResponse.self
            )
        } catch {
            logger.warning("chat react failed: \(error)")
        }
    }

    /// Mark every message in the thread as read for the current user.
    /// Debounced — repeated calls within 600 ms collapse to one.
    public func scheduleMarkRead() {
        markReadTask?.cancel()
        markReadTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 600_000_000)
            await self?.performMarkRead()
        }
    }

    public func teardown() {
        markReadTask?.cancel()
        socketTasks.forEach { $0.cancel() }
        socketTasks.removeAll()
    }

    public func tapAIPrompt(_ chip: ChatPromptChip) {
        composerText = chip.label
    }

    // MARK: - Fetch

    private func fetch(initial: Bool, before: String? = nil) async {
        if initial {
            state = .loading
            messages = []
            pendingByClientId = [:]
            failedClientIds = []
            oldestCursor = nil
            hasMore = false
        }
        switch mode {
        case .ai:
            // No persisted history for AI threads yet — go straight to
            // empty so the welcome card renders.
            state = .empty
            return
        case let .room(roomId):
            await fetchRoom(roomId: roomId, before: before)
        case let .person(otherUserId):
            await fetchPerson(otherUserId: otherUserId, before: before)
        }
    }

    private func fetchRoom(roomId: String, before: String?) async {
        do {
            let response: ChatMessagesResponse = try await api.request(
                ChatEndpoints.roomMessages(roomId: roomId, before: before)
            )
            apply(response: response)
            scheduleMarkRead(for: roomId)
        } catch {
            state = .error(message: friendlyMessage(error))
        }
    }

    private func fetchPerson(otherUserId: String, before: String?) async {
        do {
            let response: ChatMessagesResponse = try await api.request(
                ChatEndpoints.conversationMessages(otherUserId: otherUserId, before: before)
            )
            apply(response: response)
            scheduleMarkRead(for: otherUserId)
        } catch {
            state = .error(message: friendlyMessage(error))
        }
    }

    private func apply(response: ChatMessagesResponse) {
        // Backend returns newest-first; we keep an oldest-first array
        // for stable projection.
        let ordered = response.messages.reversed()
        messages.insert(contentsOf: ordered, at: 0)
        hasMore = response.hasMore ?? false
        oldestCursor = messages.first?.createdAt
        rebuild()
    }

    // MARK: - Mark-read

    private func performMarkRead() async {
        guard let target = await resolveRoomIdForReadMark() else { return }
        let endpoint =
            switch mode {
            case let .person(otherUserId):
                ChatEndpoints.markConversationRead(otherUserId: otherUserId)
            default:
                ChatEndpoints.markRoomRead(roomId: target)
            }
        do {
            _ = try await api.request(endpoint, as: EmptyResponse.self)
        } catch {
            logger.warning("markRead failed: \(error)")
        }
    }

    private func scheduleMarkRead(for _: String) {
        scheduleMarkRead()
    }

    private func resolveRoomIdForReadMark() async -> String? {
        switch mode {
        case let .room(id): id
        case let .person(otherUserId): otherUserId
        case .ai: nil
        }
    }

    private func resolveRoomId() async -> String? {
        switch mode {
        case let .room(id): return id
        case let .person(otherUserId):
            // Person mode requires a room id for `/messages`. Use the
            // first room id surfaced by the most recent message, or
            // fall back to the otherUserId stand-in if the thread is
            // empty (server will create a direct room on first send).
            if let firstRoom = messages.first?.roomId { return firstRoom }
            return otherUserId
        case .ai: return nil
        }
    }

    // MARK: - Realtime

    private func subscribeToSockets() {
        socketTasks.append(Task { [weak self] in
            guard let self else { return }
            for await event in socket.events(named: "message:new", as: ChatRealtimeMessage.self) {
                handleIncoming(event)
            }
        })
        socketTasks.append(Task { [weak self] in
            guard let self else { return }
            for await event in socket.events(named: "messageUpdated", as: ChatRealtimeMessageUpdate.self) {
                handleUpdate(event)
            }
        })
        socketTasks.append(Task { [weak self] in
            guard let self else { return }
            for await event in socket.events(named: "messageDeleted", as: ChatRealtimeMessageDelete.self) {
                handleDelete(event)
            }
        })
        socketTasks.append(Task { [weak self] in
            guard let self else { return }
            for await event in socket.events(named: "message:react", as: ChatRealtimeReaction.self) {
                handleReaction(event)
            }
        })
    }

    /// Merge a reaction count update into the matching message. The
    /// minimal patch is the count — the projection re-derives the
    /// delivery state + stamp from the existing message fields.
    private func handleReaction(_ event: ChatRealtimeReaction) {
        guard messages.contains(where: { $0.id == event.messageId }) else { return }
        // Reactions don't change the message body — just trigger a
        // re-fetch so the server count is canonical.
        Task { await self.refresh() }
    }

    private func handleIncoming(_ event: ChatRealtimeMessage) {
        // Server-side echo of our own optimistic message. Swap the
        // matching pending row out.
        if let clientId = event.clientMessageId, pendingByClientId[clientId] != nil {
            pendingByClientId[clientId] = nil
            Task { await self.refresh() }
            return
        }
        // Someone else's message in this thread → append + rebuild.
        if messages.contains(where: { $0.id == event.id }) { return }
        Task { await self.refresh() }
    }

    private func handleUpdate(_ event: ChatRealtimeMessageUpdate) {
        guard let index = messages.firstIndex(where: { $0.id == event.id }) else { return }
        let original = messages[index]
        // Construct an updated DTO by re-encoding/decoding the
        // delta — the simpler path is to just refetch.
        Task { await self.refresh() }
        _ = original
    }

    private func handleDelete(_ event: ChatRealtimeMessageDelete) {
        messages.removeAll { $0.id == event.id }
        rebuild()
    }

    // MARK: - Projection

    private func rebuild() {
        let combined = combinedMessages()
        if combined.isEmpty {
            state = .empty
            return
        }
        var rows: [ChatTimelineRow] = []
        var lastDayKey: String?
        for (index, message) in combined.enumerated() {
            let dayKey = Self.dayKey(for: message.createdAt)
            if dayKey != lastDayKey {
                rows.append(.dayDivider(ChatDayDivider(id: dayKey, label: Self.dayLabel(for: message.createdAt))))
                lastDayKey = dayKey
            }
            let side: ChatMessageSide = (message.userId == currentUserId) ? .outgoing : .incoming
            let nextSameSide =
                index + 1 < combined.count &&
                combined[index + 1].userId == message.userId &&
                Self.dayKey(for: combined[index + 1].createdAt) == dayKey
            let hasTail = !nextSameSide
            let stamp: String? = hasTail ? Self.stampLabel(for: message, currentUserId: currentUserId) : nil
            let body = Self.bodyForMessage(message)
            let deliveryState: ChatDeliveryState? = {
                guard side == .outgoing else { return nil }
                if let clientId = message.clientMessageId, failedClientIds.contains(clientId) {
                    return .failed
                }
                if message.id.hasPrefix("client_") { return .sending }
                if message.readAt != nil { return .read }
                return .delivered
            }()
            rows.append(.bubble(ChatBubbleContent(
                id: message.id,
                side: side,
                body: body,
                hasTail: hasTail,
                stamp: stamp,
                deliveryState: deliveryState
            )))
        }
        state = .loaded(rows: rows)
    }

    /// Merge persisted messages with optimistic pending rows. Failed
    /// rows stay in the list with the same id so the retry CTA can
    /// target them.
    private func combinedMessages() -> [ChatMessageDTO] {
        let pending = pendingByClientId.values.sorted { $0.createdAt < $1.createdAt }
        return messages + pending
    }

    private func optimisticMessage(text: String, roomId: String) -> ChatMessageDTO {
        let clientId = "client_\(UUID().uuidString)"
        let nowISO = ISO8601DateFormatter().string(from: Date())
        let json = """
        {
          "id": "\(clientId)",
          "room_id": "\(roomId)",
          "user_id": "\(currentUserId)",
          "message_text": \(Self.jsonString(text)),
          "message_type": "text",
          "client_message_id": "\(clientId)",
          "created_at": "\(nowISO)"
        }
        """
        let decoder = JSONDecoder()
        return (try? decoder.decode(ChatMessageDTO.self, from: Data(json.utf8))) ?? Self.fallback(
            clientId: clientId,
            text: text,
            roomId: roomId,
            nowISO: nowISO,
            userId: currentUserId
        )
    }

    private static func fallback(clientId: String, text _: String, roomId: String, nowISO: String, userId: String) -> ChatMessageDTO {
        // Last-resort placeholder when JSON encoding the optimistic
        // payload fails (should be unreachable — payload is constant).
        let json = """
        {
          "id":"\(clientId)","room_id":"\(roomId)","user_id":"\(userId)",
          "message_text":"send failed","message_type":"text",
          "client_message_id":"\(clientId)","created_at":"\(nowISO)"
        }
        """
        // swiftlint:disable:next force_try
        return try! JSONDecoder().decode(ChatMessageDTO.self, from: Data(json.utf8))
    }

    private static func jsonString(_ raw: String) -> String {
        let data = try? JSONEncoder().encode(raw)
        return data.flatMap { String(data: $0, encoding: .utf8) } ?? "\"\""
    }

    // MARK: - Body / stamp helpers

    private static func bodyForMessage(_ message: ChatMessageDTO) -> ChatBubbleContent.Body {
        switch message.messageType {
        case "image":
            let url = (message.metadata?.dictValue?["image_url"]?.stringValue).flatMap(URL.init(string:))
            return .image(url: url)
        case "file", "audio":
            let name = message.metadata?.dictValue?["filename"]?.stringValue ?? "Attachment"
            return .attachment(filename: name, sizeLabel: nil)
        case "gig_offer":
            let title = message.metadata?.dictValue?["title"]?.stringValue ?? "Shared gig"
            return .systemLink(label: "Shared gig ·", sub: title, accent: .primary)
        case "listing_offer":
            let title = message.metadata?.dictValue?["title"]?.stringValue ?? "Shared listing"
            return .systemLink(label: "Shared listing ·", sub: title, accent: .success)
        case "location":
            let where_ = message.metadata?.dictValue?["address"]?.stringValue ?? "Pinned location"
            return .systemLink(label: "Location ·", sub: where_, accent: .warning)
        default:
            return .text(message.messageText ?? "")
        }
    }

    private static func dayKey(for iso: String) -> String {
        guard let date = parseISO(iso) else { return iso }
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }

    private static func dayLabel(for iso: String) -> String {
        guard let date = parseISO(iso) else { return iso }
        let calendar = Calendar.current
        if calendar.isDateInToday(date) { return "Today" }
        if calendar.isDateInYesterday(date) { return "Yesterday" }
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d"
        return formatter.string(from: date).uppercased()
    }

    private static func stampLabel(for message: ChatMessageDTO, currentUserId: String) -> String? {
        guard let date = parseISO(message.createdAt) else { return nil }
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        let time = formatter.string(from: date)
        let isOutgoing = message.userId == currentUserId
        let sender = message.sender?.name.flatMap { $0.isEmpty ? nil : $0 }
        if isOutgoing {
            return time
        } else if let sender {
            return "\(sender) · \(time)"
        } else {
            return time
        }
    }

    private static func parseISO(_ raw: String) -> Date? {
        let f1 = ISO8601DateFormatter()
        f1.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f1.date(from: raw) ?? ISO8601DateFormatter().date(from: raw)
    }

    private func friendlyMessage(_ error: any Error) -> String {
        (error as? APIError)?.errorDescription ?? "Couldn't load this conversation."
    }
}
