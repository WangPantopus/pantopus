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
    let title = "Mailbox"
    /// A11.4 — surface the Mailbox map (physical postal venues) from the
    /// drawers root. The dedicated mailbox-root archetype (Wave B) will
    /// host this entry too once it lands.
    var topBarAction: TopBarAction? {
        TopBarAction(
            icon: .map,
            accessibilityLabel: "Map view"
        ) { [weak self] in
            Task { @MainActor in self?.onOpenMap() }
        }
    }

    let tabs: [ListOfRowsTab] = []
    var selectedTab: String = ""
    var fab: FABAction? {
        nil
    }

    private(set) var state: ListOfRowsState = .loading

    private let api: APIClient
    private let onOpenDrawer: (String) -> Void
    private let onOpenVault: () -> Void
    private let onOpenMap: @MainActor () -> Void

    init(
        api: APIClient = .shared,
        onOpenDrawer: @escaping (String) -> Void = { _ in },
        onOpenVault: @escaping () -> Void = {},
        onOpenMap: @escaping @MainActor () -> Void = {}
    ) {
        self.api = api
        self.onOpenDrawer = onOpenDrawer
        self.onOpenVault = onOpenVault
        self.onOpenMap = onOpenMap
    }

    func load() async {
        if case .loaded = state { return }
        state = .loading
        await fetch()
    }

    func refresh() async {
        await fetch()
    }

    func loadMoreIfNeeded() async {} // drawers are a fixed set.

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
                // T6.5e (P19.5) — Mailbox root surfaces a "Vault" entry
                // alongside the four drawers so the saved-mail list is
                // reachable without leaving the inbox shell.
                let drawerRows = response.drawers.map(row(for:))
                let vaultRow = RowModel(
                    id: "vault",
                    title: "Vault",
                    subtitle: "Saved mail",
                    template: .fileChevron,
                    leading: .icon(.archive, tint: Theme.Color.primary600),
                    trailing: .chevron
                ) { @Sendable in Task { @MainActor in self.onOpenVault() } }
                state = .loaded(
                    sections: [RowSection(rows: drawerRows + [vaultRow])],
                    hasMore: false
                )
            }
        } catch {
            state = .error(message: (error as? APIError)?.errorDescription ?? "Couldn't load drawers.")
        }
    }

    private func row(for drawer: DrawerListResponse.Drawer) -> RowModel {
        let subtitle: String? = switch (drawer.unreadCount, drawer.urgentCount) {
        case (0, 0): nil
        case let (unread, 0): "\(unread) unread"
        case let (0, urgent): "\(urgent) urgent"
        case let (unread, urgent): "\(unread) unread · \(urgent) urgent"
        }

        let icon: PantopusIcon = switch drawer.drawer {
        case "personal": .user
        case "home": .home
        case "business": .shoppingBag
        case "earn": .megaphone
        default: .inbox
        }

        return RowModel(
            id: drawer.id,
            title: drawer.displayName,
            subtitle: subtitle,
            template: .fileChevron,
            leading: .icon(icon, tint: Theme.Color.primary600),
            trailing: .chevron
        ) { @Sendable in Task { @MainActor in self.onOpenDrawer(drawer.drawer) } }
    }
}
