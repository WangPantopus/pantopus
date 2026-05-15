//
//  NotificationsViewModel.swift
//  Pantopus
//
//  T5.1 — Notifications V2. Drives the Notifications center against the
//  shared `ListOfRows` archetype with the new design contract:
//
//    - Two equal-width tabs: "All" + "Unread (N)".
//    - List body grouped by date with overline section headers
//      ("TODAY" / "EARLIER").
//    - Each row is a Shape D notification card — colored 40pt type-icon
//      tile + bold title + 2-line body + chip-with-relative-time meta
//      + unread highlight (`primary25` background + `personalBg` border
//      + 8pt blue dot near the top-right).
//    - Top-bar trailing "Mark all read" text-button, disabled when
//      `unreadCount == 0`.
//    - Empty Unread tab: success-tinted check-check + "View all
//      notifications" CTA that re-keys the tab to All (per §1.9).
//    - Empty All tab: bell icon + "All caught up".
//
//  Backend (unchanged):
//    - `GET /api/notifications?limit=&offset=&unread=true`
//    - `PATCH /api/notifications/:id/read`
//    - `POST /api/notifications/read-all`
//

import Foundation
import Observation
import SwiftUI

// swiftlint:disable file_length

/// Stable tab ids — public so the screen + tests can address them
/// without sprinkling string literals.
public enum NotificationsTab {
    public static let all = "all"
    public static let unread = "unread"
}

/// Seven type buckets the Notifications design surfaces. Each one drives
/// the row's tile icon + chip variant + chip label.
public enum NotificationCategory: Sendable, Hashable {
    case reply
    case mention
    case claim
    case gig
    case listing
    case safety
    case system

    private static let rawTypeMap: [String: NotificationCategory] = [
        "reply": .reply,
        "comment": .reply,
        "chat": .reply,
        "chat_message": .reply,
        "dm": .reply,
        "mention": .mention,
        "follow": .mention,
        "connection": .mention,
        "connections": .mention,
        "user": .mention,
        "claim": .claim,
        "home_member_request": .claim,
        "home_claim": .claim,
        "home_ownership": .claim,
        "gig": .gig,
        "gig_bid": .gig,
        "gig_match": .gig,
        "listing": .listing,
        "listing_sale": .listing,
        "marketplace": .listing,
        "safety": .safety,
        "alert": .safety,
        "security": .safety,
        "porch_alert": .safety,
        "system": .system,
        "info": .system,
        "support_train": .system,
        "support-train": .system,
        "announcement": .system
    ]

    /// Loose mapping from the backend's `type` strings into the seven
    /// design buckets. Unknown types fall back to `.system`.
    public static func from(rawType: String?) -> NotificationCategory {
        guard let raw = rawType?.lowercased(), !raw.isEmpty else { return .system }
        return rawTypeMap[raw] ?? heuristicCategory(for: raw)
    }

    private static func heuristicCategory(for raw: String) -> NotificationCategory {
        // Heuristic fallbacks for the noisier prefixes the backend emits today.
        if raw.contains("gig") { return .gig }
        if raw.contains("listing") || raw.contains("mail") { return .listing }
        if raw.contains("home") { return .claim }
        if raw.contains("post") || raw.contains("reply") { return .reply }
        return .system
    }

    public var label: String {
        switch self {
        case .reply: "Reply"
        case .mention: "Mention"
        case .claim: "Claim"
        case .gig: "Gig"
        case .listing: "Listing"
        case .safety: "Safety"
        case .system: "System"
        }
    }

    public var icon: PantopusIcon {
        switch self {
        case .reply: .messageCircle
        case .mention: .atSign
        case .claim: .badgeCheck
        case .gig: .briefcase
        case .listing: .tag
        case .safety: .shieldAlert
        case .system: .info
        }
    }

    public var chipVariant: StatusChipVariant {
        switch self {
        case .reply: .personal
        case .mention: .business
        case .claim: .success
        case .gig: .warning
        case .listing: .home
        case .safety: .error
        case .system: .neutral
        }
    }

    /// Background colour for the row's 40pt leading tile. The shell's
    /// `RowLeading.typeIcon` paints this directly.
    public var tileBackground: Color {
        switch self {
        case .reply: Theme.Color.personalBg
        case .mention: Theme.Color.businessBg
        case .claim: Theme.Color.successBg
        case .gig: Theme.Color.warningBg
        case .listing: Theme.Color.homeBg
        case .safety: Theme.Color.errorBg
        case .system: Theme.Color.appSurfaceSunken
        }
    }

    /// Foreground colour for the row's 40pt leading tile.
    public var tileForeground: Color {
        switch self {
        case .reply: Theme.Color.personal
        case .mention: Theme.Color.business
        case .claim: Theme.Color.success
        case .gig: Theme.Color.warning
        case .listing: Theme.Color.home
        case .safety: Theme.Color.error
        case .system: Theme.Color.appTextSecondary
        }
    }
}

private let notificationsUnreadEmptySubcopy =
    "No unread notifications. Replies, mentions, claim updates, " +
    "and safety alerts from your neighborhood will land here."

@Observable
@MainActor
public final class NotificationsViewModel: ListOfRowsDataSource {
    // MARK: - Public state

    public let title = "Notifications"

    public var tabs: [ListOfRowsTab] {
        [
            ListOfRowsTab(id: NotificationsTab.all, label: "All", count: notifications.count),
            ListOfRowsTab(id: NotificationsTab.unread, label: "Unread", count: unreadCount)
        ]
    }

    public var selectedTab: String = NotificationsTab.all {
        didSet {
            guard oldValue != selectedTab else { return }
            Task { @MainActor in await reloadForTab() }
        }
    }

    public var fab: FABAction? {
        nil
    }

    public private(set) var state: ListOfRowsState = .loading

    public var topBarAction: TopBarAction? {
        TopBarAction(
            label: "Mark all read",
            accessibilityLabel: "Mark all read",
            isEnabled: unreadCount > 0
        ) { [weak self] in
            Task { @MainActor in await self?.markAllRead() }
        }
    }

    /// Latest unread count — drives the bell badge + the read-all
    /// action's enabled state.
    public private(set) var unreadCount: Int = 0

    // MARK: - Dependencies

    private let api: APIClient
    private let onSelect: @MainActor (NotificationDTO) -> Void
    private let now: @Sendable () -> Date
    private let calendar: Calendar
    private let timeZone: TimeZone

    private var notifications: [NotificationDTO] = []
    private var hasMore: Bool = false
    private var loadingPage: Bool = false

    init(
        api: APIClient = .shared,
        onSelect: @escaping @MainActor (NotificationDTO) -> Void = { _ in },
        now: @escaping @Sendable () -> Date = { Date() },
        calendar: Calendar = .current,
        timeZone: TimeZone = .current
    ) {
        self.api = api
        self.onSelect = onSelect
        self.now = now
        self.calendar = calendar
        self.timeZone = timeZone
    }

    // MARK: - ListOfRowsDataSource

    public func load() async {
        state = .loading
        await fetch(reset: true)
    }

    public func refresh() async {
        await fetch(reset: true)
    }

    public func loadMoreIfNeeded() async {
        guard hasMore, !loadingPage else { return }
        await fetch(reset: false)
    }

    // MARK: - Mark read

    /// Mark a single row as read. The row stays in the list but the
    /// unread highlight + 8pt dot disappear. Optimistic — rolls back on
    /// failure.
    public func markRead(id: String) async {
        let previous = notifications
        let previousUnread = unreadCount
        let target = notifications.first { $0.id == id }
        guard let target, target.isRead != true else { return }
        notifications = notifications.map { row in
            row.id == id ? row.markedRead() : row
        }
        unreadCount = max(0, previousUnread - 1)
        rebuild()
        do {
            let _: NotificationActionEcho = try await api.request(
                NotificationsEndpoints.markRead(id: id)
            )
        } catch {
            notifications = previous
            unreadCount = previousUnread
            rebuild()
        }
    }

    /// Mark every unread row as read. Same optimistic + rollback pattern.
    public func markAllRead() async {
        guard unreadCount > 0 else { return }
        let previous = notifications
        let previousCount = unreadCount
        notifications = notifications.map { $0.markedRead() }
        unreadCount = 0
        rebuild()
        do {
            let _: NotificationActionEcho = try await api.request(
                NotificationsEndpoints.markAllRead
            )
        } catch {
            notifications = previous
            unreadCount = previousCount
            rebuild()
        }
    }

    // MARK: - Socket integration

    /// Hand a freshly-arrived notification to the VM. Used by the
    /// socket bridge in `RootView` so the list updates in real time.
    public func handleIncoming(_ dto: NotificationDTO) {
        // Dedupe — sockets and the GET can race.
        if notifications.contains(where: { $0.id == dto.id }) { return }
        notifications.insert(dto, at: 0)
        if dto.isRead != true { unreadCount += 1 }
        rebuild()
    }

    // MARK: - Tab switching

    private func reloadForTab() async {
        notifications = []
        hasMore = false
        state = .loading
        await fetch(reset: true)
    }

    // MARK: - Fetching

    private func fetch(reset: Bool) async {
        let unreadOnly = selectedTab == NotificationsTab.unread
        let offset = reset ? 0 : notifications.count
        loadingPage = true
        defer { loadingPage = false }
        do {
            let response: NotificationsListResponse = try await api.request(
                NotificationsEndpoints.list(
                    limit: 20,
                    offset: offset,
                    unreadOnly: unreadOnly
                )
            )
            if reset {
                notifications = response.notifications
            } else {
                notifications.append(contentsOf: response.notifications)
            }
            hasMore = response.hasMore ?? false
            unreadCount = response.unreadCount ?? notifications.filter { $0.isRead != true }.count
            rebuild()
        } catch {
            if reset {
                state = .error(
                    message: (error as? APIError)?.errorDescription ?? "Couldn't load notifications."
                )
            }
        }
    }

    // MARK: - State projection

    private func rebuild() {
        if notifications.isEmpty {
            state = .empty(emptyContent(for: selectedTab))
            return
        }
        let sections = Self.makeSections(
            notifications,
            now: now(),
            calendar: calendar,
            timeZone: timeZone
        ) { [weak self] dto in
            Task { @MainActor in self?.handleTap(dto: dto) }
        }
        state = .loaded(sections: sections, hasMore: hasMore)
    }

    private func emptyContent(for tab: String) -> ListOfRowsState.EmptyContent {
        switch tab {
        case NotificationsTab.unread:
            ListOfRowsState.EmptyContent(
                icon: .checkCheck,
                headline: "You\u{2019}re all caught up",
                subcopy: notificationsUnreadEmptySubcopy,
                ctaTitle: "View all notifications"
            ) { [weak self] in
                Task { @MainActor in
                    self?.selectedTab = NotificationsTab.all
                }
            }
        default:
            ListOfRowsState.EmptyContent(
                icon: .bell,
                headline: "All caught up",
                subcopy: "When something needs your attention, it'll show up here."
            )
        }
    }

    private func handleTap(dto: NotificationDTO) {
        if dto.isRead != true {
            Task { @MainActor in await markRead(id: dto.id) }
        }
        if let link = dto.link, !link.isEmpty {
            DeepLinkRouter.shared.handle(path: link)
        }
        onSelect(dto)
    }

    // MARK: - Pure projections (test surface)

    /// Group a list of DTOs into Today + Earlier sections, in that
    /// order. Public so the test suite can assert bucketing directly.
    public static func makeSections(
        _ dtos: [NotificationDTO],
        now: Date,
        calendar: Calendar,
        timeZone: TimeZone,
        onTap: @escaping @Sendable (NotificationDTO) -> Void
    ) -> [RowSection] {
        var cal = calendar
        cal.timeZone = timeZone
        let startOfToday = cal.startOfDay(for: now)
        var todayRows: [RowModel] = []
        var earlierRows: [RowModel] = []
        for dto in dtos {
            let created = parseDate(dto.createdAt) ?? now
            let dtoSnapshot = dto
            let row = row(
                dto: dto,
                now: now,
                calendar: cal,
                timeZone: timeZone
            ) { onTap(dtoSnapshot) }
            if created >= startOfToday {
                todayRows.append(row)
            } else {
                earlierRows.append(row)
            }
        }
        var sections: [RowSection] = []
        if !todayRows.isEmpty {
            sections.append(RowSection(id: "today", header: "Today", rows: todayRows))
        }
        if !earlierRows.isEmpty {
            sections.append(RowSection(id: "earlier", header: "Earlier", rows: earlierRows))
        }
        return sections
    }

    /// Pure projection from a `NotificationDTO` to a `RowModel`. Public
    /// so the test suite can assert the mapping without standing up the
    /// full VM.
    public static func row(
        dto: NotificationDTO,
        now: Date = Date(),
        calendar: Calendar = .current,
        timeZone: TimeZone = .current,
        onSelect: @Sendable @escaping () -> Void
    ) -> RowModel {
        let unread = dto.isRead != true
        let category = NotificationCategory.from(rawType: dto.type)
        return RowModel(
            id: dto.id,
            title: dto.title ?? "Notification",
            template: .statusChip,
            leading: .typeIcon(
                category.icon,
                background: category.tileBackground,
                foreground: category.tileForeground
            ),
            trailing: .none,
            onTap: onSelect,
            body: dto.body,
            chips: [
                RowChip(
                    text: category.label,
                    icon: category.icon,
                    tint: .status(category.chipVariant)
                )
            ],
            timeMeta: formatRelativeTime(
                dto.createdAt,
                now: now,
                calendar: calendar,
                timeZone: timeZone
            ),
            highlight: unread ? .unread : nil
        )
    }

    // MARK: - Date helpers

    private static let iso8601: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let iso8601NoFraction: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    /// Parse a backend `created_at` timestamp. Tries fractional-second
    /// ISO-8601 first, then falls back to the no-fraction form Supabase
    /// emits for whole-second rows.
    public static func parseDate(_ raw: String?) -> Date? {
        guard let raw, !raw.isEmpty else { return nil }
        return iso8601.date(from: raw) ?? iso8601NoFraction.date(from: raw)
    }

    /// Format the per-row time meta:
    ///   < 1m  → "now"
    ///   < 1h  → "Nm"
    ///   < 24h → "Nh"
    ///   yesterday → "Yesterday"
    ///   2–6 days → weekday short ("Tue")
    ///   ≥ 7 days → "MMM d" ("Mar 10")
    public static func formatRelativeTime(
        _ raw: String?,
        now: Date,
        calendar: Calendar,
        timeZone: TimeZone
    ) -> String? {
        guard let date = parseDate(raw) else { return nil }
        let interval = now.timeIntervalSince(date)
        if interval < 60 { return "now" }
        if interval < 3600 { return "\(Int(interval / 60))m" }
        if interval < 86400 { return "\(Int(interval / 3600))h" }
        var cal = calendar
        cal.timeZone = timeZone
        let startOfNow = cal.startOfDay(for: now)
        let startOfDate = cal.startOfDay(for: date)
        let dayDelta = cal.dateComponents([.day], from: startOfDate, to: startOfNow).day ?? 0
        if dayDelta == 1 { return "Yesterday" }
        if dayDelta < 7 {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.timeZone = timeZone
            formatter.dateFormat = "EEE"
            return formatter.string(from: date)
        }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = timeZone
        formatter.dateFormat = "MMM d"
        return formatter.string(from: date)
    }
}

private extension NotificationDTO {
    func markedRead() -> NotificationDTO {
        NotificationDTO(
            id: id,
            userId: userId,
            type: type,
            title: title,
            body: body,
            icon: icon,
            link: link,
            isRead: true,
            createdAt: createdAt,
            context: context
        )
    }
}
