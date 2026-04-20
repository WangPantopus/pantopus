//
//  MailboxDrawersViewModel.swift
//  Pantopus
//
//  Backs `MailboxDrawersView`. Fetches `GET /api/mailbox/v2/drawers` and
//  renders each drawer as a `file_chevron` row.
//

import Foundation
import Observation

/// ViewModel for the V2 mailbox drawers list.
@Observable
@MainActor
final class MailboxDrawersViewModel: ListOfRowsDataSource {
    public let title = "Mailbox"
    public var topBarAction: TopBarAction? { nil }
    public let tabs: [ListOfRowsTab] = []
    public var selectedTab: String = ""
    public var fab: FABAction? { nil }
    public private(set) var state: ListOfRowsState = .loading

    private let api: APIClient
    private let onOpenDrawer: (String) -> Void

    init(api: APIClient = .shared, onOpenDrawer: @escaping (String) -> Void = { _ in }) {
        self.api = api
        self.onOpenDrawer = onOpenDrawer
    }

    public func load() async {
        if case .loaded = state { return }
        state = .loading
        await fetch()
    }
    public func refresh() async { await fetch() }
    public func loadMoreIfNeeded() async {} // drawers are a fixed set.

    private func fetch() async {
        do {
            let response: DrawerListResponse = try await api.request(MailboxV2Endpoints.drawers())
            if response.drawers.isEmpty {
                state = .empty(
                    ListOfRowsState.EmptyContent(
                        icon: .mailbox,
                        headline: "No drawers yet",
                        subcopy: "Your drawers will show up here once mail arrives."
                    )
                )
            } else {
                state = .loaded(
                    sections: [RowSection(rows: response.drawers.map(row(for:)))],
                    hasMore: false
                )
            }
        } catch {
            state = .error(message: (error as? APIError)?.errorDescription ?? "Couldn't load drawers.")
        }
    }

    private func row(for drawer: DrawerListResponse.Drawer) -> RowModel {
        let subtitle: String? = {
            switch (drawer.unreadCount, drawer.urgentCount) {
            case (0, 0): return nil
            case (let unread, 0): return "\(unread) unread"
            case (0, let urgent): return "\(urgent) urgent"
            case (let unread, let urgent): return "\(unread) unread · \(urgent) urgent"
            }
        }()

        let icon: PantopusIcon = {
            switch drawer.drawer {
            case "personal": .user
            case "home": .home
            case "business": .shoppingBag
            case "earn": .megaphone
            default: .inbox
            }
        }()

        return RowModel(
            id: drawer.id,
            title: drawer.displayName,
            subtitle: subtitle,
            template: .fileChevron,
            leading: .icon(icon, tint: Theme.Color.primary600),
            trailing: .chevron,
            onTap: { @Sendable in Task { @MainActor in self.onOpenDrawer(drawer.drawer) } }
        )
    }
}
