//
//  HomeDashboardViewModel.swift
//  Pantopus
//
//  Fetches `GET /api/homes/:id` (detail) and falls back to
//  `GET /api/homes/:id/public-profile` when the user isn't authorised for
//  the private detail.
//

import Foundation
import Observation

/// Projection of the home header + stats + tab strip.
public struct HomeDashboardContent: Sendable {
    public let address: String
    public let verified: Bool
    public let stats: [HomeHeroStat]
    public let quickActions: [QuickActionTile]
    public let tabs: [GridTabsTab]
}

/// Observed state for the Home Dashboard screen.
public enum HomeDashboardState: Sendable {
    case loading
    case loaded(HomeDashboardContent)
    case error(message: String)
}

/// Backs [`HomeDashboardView`].
@Observable
@MainActor
final class HomeDashboardViewModel {
    /// Currently displayed state.
    private(set) var state: HomeDashboardState = .loading
    /// Currently selected grid tab.
    var selectedTab: String = "overview"

    private let homeId: String
    private let api: APIClient

    init(homeId: String, api: APIClient = .shared) {
        self.homeId = homeId
        self.api = api
    }

    /// Initial load; no-op when we already have content.
    func load() async {
        if case .loaded = state { return }
        state = .loading
        await fetch()
    }

    /// Pull-to-refresh / retry.
    func refresh() async { await fetch() }

    private func fetch() async {
        do {
            let response: HomeDetailResponse = try await api.request(HomesEndpoints.detail(homeId: homeId))
            applyDetail(response.home)
        } catch APIError.forbidden, APIError.notFound {
            await fetchPublicProfile()
        } catch {
            state = .error(message: (error as? APIError)?.errorDescription ?? "Couldn't load home.")
        }
    }

    private func fetchPublicProfile() async {
        do {
            let response: HomePublicProfileResponse =
                try await api.request(HomesEndpoints.publicProfile(homeId: homeId))
            applyPublic(response.home)
        } catch {
            state = .error(message: (error as? APIError)?.errorDescription ?? "Couldn't load home.")
        }
    }

    // MARK: - Projections

    private func applyDetail(_ detail: HomeDetail) {
        let address = detail.base.address ?? detail.base.name ?? "Home"
        let stats: [HomeHeroStat] = [
            HomeHeroStat(id: "members", value: "\(detail.occupants.count + 1)", label: "Members"),
            HomeHeroStat(
                id: "owners",
                value: "\(detail.owners.count)",
                label: detail.owners.count == 1 ? "Owner" : "Owners"
            ),
            HomeHeroStat(
                id: "verified",
                value: detail.isOwner ? "Owner" : "Member",
                label: "Your role"
            ),
        ]
        state = .loaded(content(address: address, verified: detail.isOwner, stats: stats))
    }

    private func applyPublic(_ public_: HomePublicProfileResponse.HomePublicProfile) {
        let stats: [HomeHeroStat] = [
            HomeHeroStat(id: "members", value: "\(public_.memberCount)", label: "Members"),
            HomeHeroStat(id: "gigs", value: "\(public_.nearbyGigs)", label: "Nearby gigs"),
            HomeHeroStat(
                id: "visibility",
                value: public_.visibility.capitalized,
                label: "Visibility"
            ),
        ]
        state = .loaded(content(address: public_.address, verified: public_.hasVerifiedOwner, stats: stats))
    }

    private func content(address: String, verified: Bool, stats: [HomeHeroStat]) -> HomeDashboardContent {
        HomeDashboardContent(
            address: address,
            verified: verified,
            stats: stats,
            quickActions: [
                QuickActionTile(id: "add_mail", label: "Add mail", icon: .mailbox, tint: .home),
                QuickActionTile(id: "log_package", label: "Log package", icon: .shoppingBag, tint: .business),
                QuickActionTile(id: "add_member", label: "Add member", icon: .userPlus, tint: .personal),
                QuickActionTile(id: "verify", label: "Verify", icon: .shieldCheck, tint: .home),
            ],
            tabs: [
                GridTabsTab(id: "overview", label: "Overview"),
                GridTabsTab(id: "members", label: "Members"),
                GridTabsTab(id: "mail", label: "Mail"),
                GridTabsTab(id: "access", label: "Access"),
            ]
        )
    }
}
