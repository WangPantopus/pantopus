//
//  CreatorInboxViewModel.swift
//  Pantopus
//
//  Backs the P1.2 Creator Inbox screen. Loads the owner persona +
//  followers DM threads from the same endpoints the Audience Profile
//  Threads tab uses (`/api/personas/me` + `/api/personas/:id/dms/threads`)
//  and projects them into filter-aware row models. Filter chip counts
//  derive from the loaded thread list so they always match what the
//  user sees.
//

import Foundation
import Observation

@Observable
@MainActor
public final class CreatorInboxViewModel {
    public private(set) var state: CreatorInboxState = .loading
    public var activeFilter: CreatorInboxFilter = .all

    private let api: APIClient
    private var threads: [PersonaThreadDTO] = []
    private var header = CreatorInboxHeader(
        title: "Creator Inbox",
        handle: nil,
        isCrossPersona: false
    )

    public init() {
        api = .shared
    }

    init(api: APIClient) {
        self.api = api
    }

    public func load() async {
        state = .loading
        do {
            let me: PersonaMeResponse = try await api.request(AudienceProfileEndpoints.me)
            guard let persona = me.persona, let handle = persona.handle else {
                let emptyHeader = CreatorInboxHeader(
                    title: "Creator Inbox",
                    handle: nil,
                    isCrossPersona: false
                )
                header = emptyHeader
                state = .empty(header: emptyHeader)
                return
            }
            let resolvedHeader = CreatorInboxHeader(
                title: "Creator Inbox",
                handle: "@\(handle)",
                isCrossPersona: false
            )
            header = resolvedHeader
            let response: PersonaThreadsResponse =
                try await api.request(AudienceProfileEndpoints.threads(personaId: persona.id))
            threads = response.threads
            rebuild()
        } catch {
            let message = (error as? APIError)?.errorDescription ?? "Couldn't load your inbox."
            state = .error(message: message)
        }
    }

    public func refresh() async {
        await load()
    }

    public func selectFilter(_ filter: CreatorInboxFilter) {
        activeFilter = filter
        rebuild()
    }

    /// Resolve a thread row's counterparty for the `ChatConversationView`
    /// push — prefer the explicit `counterpartyUserId`, fall back to the
    /// row id (server defaults that to the membership id today).
    public func conversationDestination(for row: CreatorInboxRowContent) -> CreatorInboxConversationDestination {
        let userId = row.counterpartyUserId ?? row.id
        return CreatorInboxConversationDestination(
            userId: userId,
            displayName: row.displayName.isEmpty ? row.handle : row.displayName,
            initials: row.initials,
            verified: row.verifiedLocal
        )
    }

    // MARK: - Projection

    private func rebuild() {
        if threads.isEmpty {
            state = .empty(header: header)
            return
        }
        let rows = threads.compactMap(Self.row)
        let counts = CreatorInboxCounts(
            total: rows.count,
            unread: rows.filter(\.unread).count,
            flagged: rows.filter(\.flagged).count
        )
        let chips = Self.chips(rows: rows, counts: counts)
        let filtered = rows.filter { Self.matches($0, filter: activeFilter) }
        let loaded = CreatorInboxLoaded(
            header: header,
            rows: filtered,
            counts: counts,
            chips: chips
        )
        state = .loaded(loaded)
    }

    static func chips(rows: [CreatorInboxRowContent], counts: CreatorInboxCounts) -> [CreatorInboxChipContent] {
        let bronzePlus = rows.filter { $0.tierRank >= 2 }.count
        return [
            CreatorInboxChipContent(filter: .all, count: counts.total),
            CreatorInboxChipContent(filter: .unread, count: counts.unread),
            CreatorInboxChipContent(filter: .bronzePlus, count: bronzePlus),
            CreatorInboxChipContent(filter: .flagged, count: counts.flagged)
        ]
    }

    static func matches(_ row: CreatorInboxRowContent, filter: CreatorInboxFilter) -> Bool {
        switch filter {
        case .all: true
        case .unread: row.unread
        case .bronzePlus: row.tierRank >= 2
        case .flagged: row.flagged
        }
    }

    static func row(_ dto: PersonaThreadDTO) -> CreatorInboxRowContent? {
        let handle = dto.fanHandle ?? ""
        let displayName = dto.fanDisplayName ?? (handle.isEmpty ? "Follower" : handle)
        let initials = initials(of: displayName, handle: handle)
        return CreatorInboxRowContent(
            id: dto.id,
            displayName: displayName,
            handle: handle.isEmpty ? "" : "@\(handle)",
            initials: initials,
            avatarUrl: dto.fanAvatarUrl,
            tierName: dto.tier?.name,
            tierRank: dto.tier?.rank ?? 1,
            preview: dto.lastMessagePreview ?? "",
            timeAgo: timeAgo(from: dto.lastMessageAt),
            unread: (dto.unreadCount ?? 0) > 0,
            flagged: dto.flagged ?? false,
            verifiedLocal: dto.verifiedLocal ?? false,
            counterpartyUserId: dto.counterpartyUserId,
            personaChip: nil
        )
    }

    static func initials(of name: String, handle: String) -> String {
        let source = name.isEmpty ? handle : name
        let parts = source.split(separator: " ").prefix(2)
        let letters = parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
        if !letters.isEmpty { return letters }
        return String(source.prefix(2)).uppercased()
    }

    static func timeAgo(from iso: String?) -> String {
        guard let iso, let date = ISO8601DateFormatter().date(from: iso) else { return "" }
        let interval = Date().timeIntervalSince(date)
        let minutes = Int(interval / 60)
        if minutes < 1 { return "Just now" }
        if minutes < 60 { return "\(minutes)m" }
        let hours = minutes / 60
        if hours < 24 { return "\(hours)h" }
        let days = hours / 24
        if days < 7 { return days == 1 ? "Yesterday" : "\(days)d" }
        let weeks = days / 7
        return "\(weeks)w"
    }
}
