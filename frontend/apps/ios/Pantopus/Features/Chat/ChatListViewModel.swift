//
//  ChatListViewModel.swift
//  Pantopus
//
//  Loads `GET /api/chat/unified-conversations` + `/stats` and reacts to
//  socket events (`badge:update`, `message:new`). Projects the
//  hetero DTO into a homogeneous list of `ConversationRowContent` plus
//  a synthetic, pinned "Ask Pantopus" AI assistant row.
//

import Foundation
import Logging
import Observation

/// View-model for the Chat List (Inbox tab) screen.
@Observable
@MainActor
public final class ChatListViewModel {
    /// Current render state.
    public private(set) var state: ChatListState = .loading

    /// Active filter tab.
    public private(set) var activeFilter: ChatFilter = .all

    /// Unread counts for the filter-tab badges. The `unread` tab uses
    /// `unread` from this map; other tabs hide their badge.
    public private(set) var unreadByFilter: [ChatFilter: Int] = [:]

    private let api: APIClient
    private let socket: SocketClient
    private let logger = Logger(label: "app.pantopus.ios.ChatList")
    private var allRows: [ConversationRowContent] = []
    /// Always-pinned synthetic AI assistant row.
    private let aiRow: ConversationRowContent = .init(
        id: "ai_assistant",
        variant: .aiAssistant,
        displayName: "Ask Pantopus",
        initials: "AP",
        avatarURL: nil,
        identityChip: nil,
        verified: false,
        preview: "Summaries, drafts, neighborhood help.",
        timeLabel: "now",
        unread: 0,
        pinned: true,
        topicKinds: []
    )

    private var badgeTask: Task<Void, Never>?
    private var messageTask: Task<Void, Never>?

    init(api: APIClient = .shared, socket: SocketClient = .shared) {
        self.api = api
        self.socket = socket
    }

    deinit {
        badgeTask?.cancel()
        messageTask?.cancel()
    }

    // MARK: - Public API

    /// First-time load — no-op when we already have content.
    public func load() async {
        if case .loaded = state { return }
        await fetch()
        subscribeToSockets()
    }

    /// Pull-to-refresh / retry.
    public func refresh() async {
        await fetch()
    }

    /// Tap a filter tab. Pure UI — no refetch.
    public func selectFilter(_ filter: ChatFilter) {
        guard filter != activeFilter else { return }
        activeFilter = filter
        applyFilter()
    }

    /// Tear down socket subscriptions when the view disappears.
    public func teardown() {
        badgeTask?.cancel()
        messageTask?.cancel()
        badgeTask = nil
        messageTask = nil
    }

    // MARK: - Fetch

    private func fetch() async {
        async let conversationsTask: UnifiedConversationsResponse? = optional {
            try await self.api.request(ChatEndpoints.unifiedConversations())
        }
        async let statsTask: ChatStatsResponse? = optional {
            try await self.api.request(ChatEndpoints.stats())
        }
        guard let response = await conversationsTask else {
            state = .error(message: "Couldn't load conversations.")
            return
        }
        let stats = await statsTask?.stats
        let rows = response.conversations.map(Self.project)
        allRows = rows
        unreadByFilter = [
            .all: 0,
            .unread: stats?.totalUnread ?? response.totalUnread ?? rows.reduce(0) { $0 + $1.unread },
            .gigs: stats?.gigChats ?? rows.filter { $0.topicKinds.contains("gig") }.count,
            .market: rows.filter { $0.topicKinds.contains("marketplace") }.count
        ]
        applyFilter()
    }

    private func optional<T: Sendable>(_ operation: @Sendable () async throws -> T) async -> T? {
        do {
            return try await operation()
        } catch {
            logger.warning("Chat-list fetch failed: \(error)")
            return nil
        }
    }

    private func applyFilter() {
        let filtered: [ConversationRowContent] =
            switch activeFilter {
            case .all: allRows
            case .unread: allRows.filter { $0.unread > 0 }
            case .gigs: allRows.filter { $0.topicKinds.contains("gig") }
            case .market: allRows.filter { $0.topicKinds.contains("marketplace") }
            }
        // Pin synthetic AI row on top of every filter. The verified
        // floor is enforced by the empty-state copy + the absence of
        // any unverified rows in `allRows` (the backend serializer
        // already excludes unverified DMs).
        let rows = [aiRow] + filtered
        // Pinned-first ordering — AI is always pinned; user-pinned
        // conversations come next; rest follow.
        let pinnedFirst = rows.sorted { lhs, rhs in
            if lhs.pinned == rhs.pinned { return false }
            return lhs.pinned && !rhs.pinned
        }
        if filtered.isEmpty {
            state = .empty
        } else {
            state = .loaded(rows: pinnedFirst)
        }
    }

    // MARK: - Realtime

    private func subscribeToSockets() {
        if badgeTask == nil {
            badgeTask = Task { [weak self] in
                guard let self else { return }
                let stream = socket.events(named: "badge:update", as: ChatBadgeUpdate.self)
                for await update in stream {
                    unreadByFilter[.unread] = update.totalUnread
                }
            }
        }
        if messageTask == nil {
            messageTask = Task { [weak self] in
                guard let self else { return }
                let stream = socket.events(named: "message:new", as: ChatMessageEvent.self)
                for await event in stream {
                    handleMessage(event)
                }
            }
        }
    }

    private func handleMessage(_ event: ChatMessageEvent) {
        // Best-effort: bump the matching row's preview + timestamp +
        // unread count. We match by conversation other-user-id (DM /
        // gig merged rows) or by room id (group / home). When neither
        // matches, trigger a full refetch so the new conversation
        // appears.
        let targetId = event.otherUserId ?? event.roomId
        guard let index = allRows.firstIndex(where: { $0.id == targetId }) else {
            Task { await self.refresh() }
            return
        }
        let original = allRows[index]
        let nextUnread = event.unreadFor ?? (original.unread + 1)
        let preview = event.preview ?? original.preview
        let time = event.createdAt.flatMap(Self.relative(timestamp:)) ?? "now"
        let updated = ConversationRowContent(
            id: original.id,
            variant: original.variant,
            displayName: original.displayName,
            initials: original.initials,
            avatarURL: original.avatarURL,
            identityChip: original.identityChip,
            verified: original.verified,
            preview: preview,
            timeLabel: time,
            unread: nextUnread,
            pinned: original.pinned,
            topicKinds: original.topicKinds
        )
        allRows[index] = updated
        applyFilter()
    }

    // MARK: - Projection

    private static func project(_ dto: UnifiedConversation) -> ConversationRowContent {
        let unread = dto.totalUnread
        let preview = dto.lastMessagePreview ?? defaultPreview(for: dto)
        let time = dto.lastMessageAt.flatMap(relative(timestamp:)) ?? ""
        let identityChip = identityChip(for: dto)
        let displayName = dto.name?.nilIfEmpty ?? defaultName(for: dto)
        return ConversationRowContent(
            id: dto.id,
            variant: variant(for: dto),
            displayName: displayName,
            initials: initials(from: displayName),
            avatarURL: dto.avatarURL,
            identityChip: identityChip,
            verified: dto.isVerified ?? false,
            preview: preview,
            timeLabel: time,
            unread: unread,
            pinned: false,
            topicKinds: Set(dto.topicKinds)
        )
    }

    private static func variant(for dto: UnifiedConversation) -> ConversationRowVariant {
        switch dto.kind {
        case .conversation:
            .dm
        case .room:
            .group(extraAvatars: [], extraCount: 0)
        }
    }

    private static func identityChip(for dto: UnifiedConversation) -> ConversationIdentityChip? {
        switch (dto.kind, dto.identityKind, dto.roomType) {
        case (.conversation, "business"?, _): .business
        case (.conversation, "home"?, _): .home
        case (.room, _, "home"?): .home
        default: nil
        }
    }

    private static func defaultName(for dto: UnifiedConversation) -> String {
        switch dto.kind {
        case .room: dto.name ?? "Group"
        case .conversation: dto.name ?? "Pantopus user"
        }
    }

    private static func defaultPreview(for dto: UnifiedConversation) -> String {
        switch dto.kind {
        case .conversation: "Start the conversation"
        case .room: "No messages yet"
        }
    }

    private static func initials(from name: String) -> String {
        let parts = name.split(separator: " ").prefix(2)
        let result = parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
        return result.isEmpty ? "?" : result
    }

    private static func relative(timestamp: String) -> String {
        let parsers: [ISO8601DateFormatter] = {
            let withFraction = ISO8601DateFormatter()
            withFraction.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            let plain = ISO8601DateFormatter()
            return [withFraction, plain]
        }()
        guard let date = parsers.lazy.compactMap({ $0.date(from: timestamp) }).first else {
            return timestamp
        }
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
