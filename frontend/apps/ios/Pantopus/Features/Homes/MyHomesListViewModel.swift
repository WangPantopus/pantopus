//
//  MyHomesListViewModel.swift
//  Pantopus
//
//  Backs `MyHomesListView`. Fetches `GET /api/homes/my-homes` and
//  projects each home into the avatar-first List-of-Rows row shape
//  defined for T6.3f / P14:
//
//    • Leading — identity-green avatar tile (initials from address)
//    • Title   — nickname or formatted address
//    • Subtitle — role chip + locality (joined via "·")
//    • Body    — "Active home" home-tinted chip on the primary-owner row
//    • Trailing — chevron (tap → home dashboard)
//
//  Plus a `BannerConfig` intro card ("homes you belong to") and a 60pt
//  secondary-create FAB tinted home green.
//

import Foundation
import Observation
import SwiftUI

/// ViewModel for the "My homes" list.
@Observable
@MainActor
final class MyHomesListViewModel: ListOfRowsDataSource {
    let title = "My homes"
    var topBarAction: TopBarAction? {
        nil
    }

    let tabs: [ListOfRowsTab] = []
    var selectedTab: String = ""
    var fab: FABAction? {
        FABAction(
            icon: .plusCircle,
            accessibilityLabel: "Claim a home",
            variant: .secondaryCreate,
            tint: .home,
            handler: onAddHome
        )
    }

    var banner: BannerConfig? {
        guard case let .loaded(sections, _) = state,
              let count = sections.first?.rows.count,
              count > 0
        else {
            return nil
        }
        return BannerConfig(
            icon: .home,
            title: count == 1 ? "1 home you belong to" : "\(count) homes you belong to",
            subtitle: "Tap any home to jump into that household",
            tint: .home
        )
    }

    private(set) var state: ListOfRowsState = .loading

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

    func load() async {
        if case .loaded = state { return }
        state = .loading
        await fetch()
    }

    func refresh() async {
        await fetch()
    }

    func loadMoreIfNeeded() async {} // `my-homes` is not paginated.

    private func fetch() async {
        do {
            let response: MyHomesResponse = try await api.request(HomesEndpoints.myHomes())
            let rows = response.homes.map { row(for: $0) }
            if rows.isEmpty {
                state = .empty(
                    ListOfRowsState.EmptyContent(
                        icon: .home,
                        headline: "You don’t belong to any homes yet",
                        subcopy: "Claim or join a verified home to unlock packages, bills, tasks, and member chat.",
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

    private func row(for entry: MyHome) -> RowModel {
        let home = entry.home
        let displayTitle = home.name?.nilIfEmpty
            ?? home.address?.nilIfEmpty
            ?? "Unnamed home"
        let locality = [home.city, home.state]
            .compactMap { $0?.nilIfEmpty }
            .joined(separator: ", ")
            .nilIfEmpty
        let role = roleLabel(for: entry)
        let subtitleParts = [role, locality].compactMap { $0 }
        let subtitle = subtitleParts.isEmpty ? nil : subtitleParts.joined(separator: " · ")

        let chips: [RowChip]? = if entry.isPrimaryOwner == true {
            [
                RowChip(
                    text: "Active home",
                    icon: .home,
                    tint: .custom(
                        background: Theme.Color.homeBg,
                        foreground: Theme.Color.home
                    )
                )
            ]
        } else {
            nil
        }

        return RowModel(
            id: entry.id,
            title: displayTitle,
            subtitle: subtitle,
            template: .avatarKebab,
            leading: .avatar(
                name: displayTitle,
                imageURL: nil,
                identity: .home,
                ringProgress: entry.ownershipStatus == "verified" ? 1.0 : 0.3
            ),
            trailing: .chevron,
            onTap: { @Sendable in
                Task { @MainActor in self.onOpenHome(entry.id) }
            },
            chips: chips
        )
    }

    /// Maps the backend's role enum onto the canonical four-role label
    /// vocabulary the design uses: Owner / Tenant / Housemate / Guest.
    /// Order: ownership_status wins; otherwise occupancy.role_base; final
    /// fallback "Member".
    private func roleLabel(for entry: MyHome) -> String? {
        if let status = entry.ownershipStatus {
            switch status {
            case "verified":
                return "Owner"
            case "pending":
                return "Owner (pending)"
            default:
                break
            }
        }
        switch entry.occupancy?.roleBase {
        case "lease_resident":
            return "Tenant"
        case "household_member":
            return "Housemate"
        case "guest":
            return "Guest"
        case "owner":
            return "Owner"
        case "admin", "manager":
            return "Manager"
        case nil:
            return nil
        case let roleBase?:
            return roleBase.capitalized
        }
    }
}

private extension String {
    /// Returns `nil` when the string is empty; otherwise the string.
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
