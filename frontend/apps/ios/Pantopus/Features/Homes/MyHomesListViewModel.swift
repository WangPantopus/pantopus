//
//  MyHomesListViewModel.swift
//  Pantopus
//
//  Backs `MyHomesListView`. Fetches `GET /api/homes/my-homes` and maps
//  each home to an `avatar_kebab` row.
//

import Foundation
import Observation
import SwiftUI

/// ViewModel for the "My homes" list.
@Observable
@MainActor
final class MyHomesListViewModel: ListOfRowsDataSource {
    public let title = "My homes"
    public var topBarAction: TopBarAction? { nil }
    public let tabs: [ListOfRowsTab] = []
    public var selectedTab: String = ""
    public var fab: FABAction? {
        FABAction(icon: .plusCircle, accessibilityLabel: "Claim a home", handler: onAddHome)
    }
    public private(set) var state: ListOfRowsState = .loading

    private let api: APIClient
    private let onOpenHome: (String) -> Void
    private let onAddHome: @Sendable () -> Void

    init(
        api: APIClient = .shared,
        onOpenHome: @escaping (String) -> Void = { _ in },
        onAddHome: @escaping @Sendable () -> Void = {}
    ) {
        self.api = api
        self.onOpenHome = onOpenHome
        self.onAddHome = onAddHome
    }

    public func load() async {
        if case .loaded = state { return }
        state = .loading
        await fetch()
    }

    public func refresh() async { await fetch() }
    public func loadMoreIfNeeded() async {} // `my-homes` is not paginated.

    private func fetch() async {
        do {
            let response: MyHomesResponse = try await api.request(HomesEndpoints.myHomes())
            let rows = response.homes.map { row(for: $0) }
            if rows.isEmpty {
                state = .empty(
                    ListOfRowsState.EmptyContent(
                        icon: .home,
                        headline: "No homes claimed yet",
                        subcopy: "Claim your address to unlock neighborhood features.",
                        ctaTitle: "Claim a home",
                        onCTA: onAddHome
                    )
                )
            } else {
                state = .loaded(sections: [RowSection(rows: rows)], hasMore: false)
            }
        } catch {
            state = .error(message: (error as? APIError)?.errorDescription ?? "Something went wrong.")
        }
    }

    private func row(for home: MyHome) -> RowModel {
        let title = home.home.name?.nilIfEmpty ?? home.home.address ?? "Unnamed home"
        let subtitleParts = [home.home.city, home.home.state]
            .compactMap(\.self)
            .filter { !$0.isEmpty }
        return RowModel(
            id: home.id,
            title: title,
            subtitle: subtitleParts.isEmpty ? nil : subtitleParts.joined(separator: ", "),
            template: .avatarKebab,
            leading: .avatar(
                name: title,
                imageURL: nil,
                identity: .home,
                ringProgress: home.ownershipStatus == "verified" ? 1.0 : 0.3
            ),
            trailing: .kebab,
            onTap: { @Sendable in
                Task { @MainActor in self.onOpenHome(home.id) }
            },
            onSecondary: { @Sendable in
                // Kebab menu: wired to a bottom sheet when P9 lands. For now
                // no-op so the row retains its affordance.
            }
        )
    }
}

private extension String {
    /// Returns `nil` when the string is empty; otherwise the string.
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
