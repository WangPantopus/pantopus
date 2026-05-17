//
//  MyBusinessesViewModel.swift
//  Pantopus
//
//  T6.3f / P14 — the user's index of every verified business they own
//  or staff. Backed by `GET /api/businesses/my-businesses`.
//
//  Row shape (avatar-first, no tabs):
//    leading  → 56pt category-tinted logo avatar (initials over
//               business-violet OR category-flavoured gradient)
//    title    → business name
//    subtitle → `<Category> · <Role>` (Owner / Manager / Staff)
//    body     → locality or "Online only" when no city/state
//    trailing → chevron (tap → business dashboard)
//
//  FAB: 60pt secondaryCreate, .business tint — pushes to a register-
//  business placeholder until the compose flow lands.
//

import Foundation
import Observation
import SwiftUI

@Observable
@MainActor
final class MyBusinessesViewModel: ListOfRowsDataSource {
    let title = "My businesses"
    var topBarAction: TopBarAction? {
        nil
    }

    let tabs: [ListOfRowsTab] = []
    var selectedTab: String = ""

    var fab: FABAction? {
        FABAction(
            icon: .building2,
            accessibilityLabel: "Register a business",
            variant: .secondaryCreate,
            tint: .business,
            handler: onRegister
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
            icon: .building2,
            title: count == 1 ? "1 verified business" : "\(count) verified businesses",
            subtitle: "Tap any business to manage its inbox, gigs, and reviews",
            tint: .business
        )
    }

    private(set) var state: ListOfRowsState = .loading

    private let api: APIClient
    private let onOpenBusiness: (String) -> Void
    private let onRegister: @Sendable () -> Void

    init(
        api: APIClient = .shared,
        onOpenBusiness: @escaping (String) -> Void = { _ in },
        onRegister: @escaping @Sendable () -> Void = {}
    ) {
        self.api = api
        self.onOpenBusiness = onOpenBusiness
        self.onRegister = onRegister
    }

    func load() async {
        if case .loaded = state { return }
        state = .loading
        await fetch()
    }

    func refresh() async {
        await fetch()
    }

    func loadMoreIfNeeded() async {}

    private func fetch() async {
        do {
            let response: MyBusinessesResponse = try await api.request(
                BusinessesEndpoints.myBusinesses()
            )
            let rows = response.businesses.map { row(for: $0) }
            if rows.isEmpty {
                state = .empty(
                    ListOfRowsState.EmptyContent(
                        icon: .building2,
                        headline: "No businesses yet",
                        subcopy: "Create a business profile to take quotes inside Pantopus and earn the violet verified mark.",
                        ctaTitle: "Register a business",
                        onCTA: onRegister
                    )
                )
            } else {
                state = .loaded(sections: [RowSection(rows: rows)], hasMore: false)
            }
        } catch {
            state = .error(message: (error as? APIError)?.errorDescription ?? "Something went wrong.")
        }
    }

    private func row(for membership: BusinessMembership) -> RowModel {
        let business = membership.business
        let displayTitle = business.name?.nilIfEmpty
            ?? business.username?.nilIfEmpty
            ?? "Untitled business"
        let category = categoryLabel(for: membership.profile)
        let role = roleLabel(for: membership.roleBase)
        let subtitleParts = [category, role].compactMap { $0 }
        let subtitle = subtitleParts.isEmpty ? nil : subtitleParts.joined(separator: " · ")

        let locality = [business.city, business.state]
            .compactMap { $0?.nilIfEmpty }
            .joined(separator: ", ")
            .nilIfEmpty
        let body = locality ?? "Online only"

        return RowModel(
            id: membership.businessUserId,
            title: displayTitle,
            subtitle: subtitle,
            template: .avatarKebab,
            leading: .avatarWithBadge(
                name: displayTitle,
                imageURL: business.profilePictureURL.flatMap(URL.init(string:)),
                background: .gradient(GradientPair(
                    start: Theme.Color.business,
                    end: Theme.Color.business
                )),
                size: .large,
                verified: membership.profile?.isPublished == true
            ),
            trailing: .chevron,
            onTap: { @Sendable in
                Task { @MainActor in self.onOpenBusiness(membership.businessUserId) }
            },
            body: body,
            bodyIcon: locality == nil ? .info : .mapPin
        )
    }

    private func categoryLabel(for profile: BusinessProfileDTO?) -> String? {
        if let first = profile?.categories?.first, !first.isEmpty {
            return first.replacingOccurrences(of: "_", with: " ").capitalized
        }
        if let type = profile?.businessType, !type.isEmpty {
            return type.replacingOccurrences(of: "_", with: " ").capitalized
        }
        return nil
    }

    private func roleLabel(for roleBase: String?) -> String? {
        guard let roleBase, !roleBase.isEmpty else { return nil }
        switch roleBase {
        case "owner": return "Owner"
        case "admin": return "Admin"
        case "manager": return "Manager"
        case "staff": return "Staff"
        case "viewer": return "Viewer"
        case "editor": return "Editor"
        default: return roleBase.capitalized
        }
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
