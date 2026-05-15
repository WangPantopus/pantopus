//
//  NotificationsViewModel.swift
//  Pantopus
//
//  Drives the T4.1 Notifications center. Conforms to
//  ListOfRowsDataSource so the screen reuses the shared archetype
//  (loading/empty/error states + pull-to-refresh).
//

import Foundation
import Observation

@Observable
@MainActor
public final class NotificationsViewModel: ListOfRowsDataSource {
    // MARK: - Public state

    public var title: String { "Notifications" }
    public var tabs: [ListOfRowsTab] { [] }
    public var selectedTab: String = ""
    public var fab: FABAction? { nil }
    public private(set) var state: ListOfRowsState = .loading

    public var topBarAction: TopBarAction? {
        guard hasUnread else { return nil }
        return TopBarAction(
            icon: .check,
            accessibilityLabel: "Mark all read"
        ) { [weak self] in
            Task { @MainActor in await self?.markAllRead() }
        }
    }

    /// Latest unread count — drives the bell badge + the read-all
    /// action visibility.
    public private(set) var unreadCount: Int = 0

    private let api: APIClient
    private let onSelect: @MainActor (NotificationDTO) -> Void
    private var notifications: [NotificationDTO] = []
    private var hasMore: Bool = false

    public init(
        api: APIClient = .shared,
        onSelect: @escaping @MainActor (NotificationDTO) -> Void = { _ in }
    ) {
        self.api = api
        self.onSelect = onSelect
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
        guard hasMore else { return }
        await fetch(reset: false)
    }

    // MARK: - Mark read

    /// Mark a single row as read. The row stays in the list but the
    /// "unread" pill disappears. Optimistic — rolls back on failure.
    public func markRead(id: String) async {
        let previous = notifications
        notifications = notifications.map { row in
            row.id == id ? row.markedRead() : row
        }
        if let idx = notifications.firstIndex(where: { $0.id == id }), previous[idx].isRead != true {
            unreadCount = max(0, unreadCount - 1)
        }
        rebuild()
        do {
            let _: NotificationActionEcho = try await api.request(NotificationsEndpoints.markRead(id: id))
        } catch {
            notifications = previous
            unreadCount = previous.filter { $0.isRead != true }.count
            rebuild()
        }
    }

    /// Mark every unread row as read. Same optimistic + rollback
    /// pattern as `markRead(id:)`.
    public func markAllRead() async {
        let previous = notifications
        let previousCount = unreadCount
        notifications = notifications.map { $0.markedRead() }
        unreadCount = 0
        rebuild()
        do {
            let _: NotificationActionEcho = try await api.request(NotificationsEndpoints.markAllRead)
        } catch {
            notifications = previous
            unreadCount = previousCount
            rebuild()
        }
    }

    // MARK: - Internal

    private var hasUnread: Bool { unreadCount > 0 }

    private func fetch(reset: Bool) async {
        let offset = reset ? 0 : notifications.count
        do {
            let response: NotificationsListResponse = try await api.request(
                NotificationsEndpoints.list(limit: 20, offset: offset)
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
                state = .error(message: (error as? APIError)?.errorDescription ?? "Couldn't load notifications.")
            }
        }
    }

    private func rebuild() {
        if notifications.isEmpty {
            state = .empty(
                ListOfRowsState.EmptyContent(
                    icon: .bell,
                    headline: "All caught up",
                    subcopy: "When something needs your attention, it'll show up here."
                )
            )
            return
        }
        let rows = notifications.map { dto in Self.row(dto: dto) { [weak self] in
            self?.handleTap(dto: dto)
        }
        }
        state = .loaded(
            sections: [RowSection(rows: rows)],
            hasMore: hasMore
        )
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

    /// Public so the test suite can assert the row mapping without
    /// constructing the full VM.
    public static func row(
        dto: NotificationDTO,
        onSelect: @Sendable @escaping () -> Void
    ) -> RowModel {
        let unread = dto.isRead != true
        let trailing: RowTrailing = unread
            ? .statusChip(text: "NEW", variant: .info)
            : .chevron
        let icon: PantopusIcon = iconFor(type: dto.type)
        return RowModel(
            id: dto.id,
            title: dto.title ?? "Notification",
            subtitle: dto.body,
            template: .statusChip,
            leading: .icon(icon, tint: unread ? Theme.Color.primary600 : Theme.Color.appTextSecondary),
            trailing: trailing,
            onTap: onSelect
        )
    }

    /// Type → icon picker. Matches the routing table flavors so the
    /// list reads as the same vocabulary as the destinations.
    static func iconFor(type: String?) -> PantopusIcon {
        switch type {
        case "support_train", "support-train": .heart
        case "post", "comment": .send
        case "gig", "gig_bid": .hammer
        case "listing", "listing_sale": .shoppingBag
        case "home", "home_member_request", "home_dashboard": .home
        case "chat", "chat_message", "dm": .inbox
        case "user", "follow", "connection": .user
        case "connections": .userPlus
        case "mail_claimed", "mail": .mailbox
        default: .bell
        }
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

public extension NotificationDTO {
    init(
        id: String,
        userId: String?,
        type: String?,
        title: String?,
        body: String?,
        icon: String?,
        link: String?,
        isRead: Bool?,
        createdAt: String?,
        context: String?
    ) {
        self.id = id
        self.userId = userId
        self.type = type
        self.title = title
        self.body = body
        self.icon = icon
        self.link = link
        self.isRead = isRead
        self.createdAt = createdAt
        self.context = context
    }
}
