//
//  BlockedUsersViewModel.swift
//  Pantopus
//
//  P8 / T6.2c — Settings → Blocked users sub-route.
//  Backs the screen with `ListOfRowsDataSource`. Reads
//  `GET /api/privacy/blocks` (privacy.js:154) and unblocks via
//  `DELETE /api/privacy/blocks/:blockId` (privacy.js:251). Unblock is
//  optimistic: the row disappears immediately and re-appears if the
//  DELETE fails.
//

import Foundation
import Observation

@Observable
@MainActor
public final class BlockedUsersViewModel: ListOfRowsDataSource {
    public var title: String {
        "Blocked users"
    }

    public var topBarAction: TopBarAction? {
        nil
    }

    public var tabs: [ListOfRowsTab] {
        []
    }

    public var selectedTab: String = ""
    public var fab: FABAction? {
        nil
    }

    public private(set) var state: ListOfRowsState = .loading

    private let api: APIClient
    private var blocks: [PrivacyBlock] = []

    init(api: APIClient = .shared) {
        self.api = api
    }

    public func load() async {
        state = .loading
        await fetch()
    }

    public func refresh() async {
        await fetch()
    }

    public func loadMoreIfNeeded() async {}

    private func fetch() async {
        do {
            let response: PrivacyBlocksResponse = try await api.request(PrivacyEndpoints.blocks)
            blocks = response.blocks
            rebuild()
        } catch {
            state = .error(message: "Couldn't load your blocked list.")
        }
    }

    /// Optimistic unblock. Removes the row immediately; restores it on
    /// network failure (kept original index so the order doesn't shuffle).
    public func unblock(_ blockId: String) async {
        guard let index = blocks.firstIndex(where: { $0.id == blockId }) else { return }
        let removed = blocks.remove(at: index)
        rebuild()
        do {
            _ = try await api.request(PrivacyEndpoints.deleteBlock(blockId: blockId))
        } catch {
            blocks.insert(removed, at: min(index, blocks.count))
            rebuild()
        }
    }

    private func rebuild() {
        guard !blocks.isEmpty else {
            state = .empty(.init(
                icon: .shield,
                headline: "No one blocked",
                subcopy: "When you block someone, they'll appear here. Unblock from this list anytime."
            ))
            return
        }
        let rows = blocks.map { block -> RowModel in
            let name = block.blocked?.name
                ?? block.blocked?.username.map { "@\($0)" }
                ?? "Blocked user"
            let avatarURL = block.blocked?.profilePictureUrl.flatMap(URL.init(string:))
            let subtitle = block.reason?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
                ?? Self.scopeLabel(block.blockScope)
            let blockId = block.id
            return RowModel(
                id: blockId,
                title: name,
                subtitle: subtitle,
                template: .avatarKebab,
                leading: .avatarWithBadge(
                    name: name,
                    imageURL: avatarURL,
                    background: .solid(Theme.Color.appSurfaceSunken),
                    size: .medium,
                    verified: false
                ),
                trailing: .kebab,
                onTap: {},
                onSecondary: { [weak self] in
                    Task { @MainActor in await self?.unblock(blockId) }
                }
            )
        }
        state = .loaded(sections: [RowSection(id: "blocked", rows: rows)], hasMore: false)
    }

    private static func scopeLabel(_ scope: String?) -> String? {
        switch scope {
        case "search_only": "Hidden from search"
        case "business_context": "Blocked in business contexts"
        case "full", nil: "Blocked"
        default: scope?.capitalized
        }
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
