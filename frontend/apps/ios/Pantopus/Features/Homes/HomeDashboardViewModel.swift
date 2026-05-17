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
    /// True when the home has any verified owner — drives the header
    /// "Verified" badge and the summary status row. Distinct from
    /// `isVerifiedOwner` because the home can have a verified owner
    /// who isn't the signed-in user.
    public let verified: Bool
    /// True when the signed-in user is the verified owner of this home.
    /// Drives the claim-ownership banner gate: shown when this is false
    /// regardless of whether anyone else is a verified owner.
    public let isVerifiedOwner: Bool
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
    func refresh() async {
        await fetch()
    }

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
            )
        ]
        state = .loaded(content(
            address: address,
            // Header badge / summary row: home has any verified owner.
            verified: detail.isOwner || detail.owners.contains { $0.ownerStatus == "verified" },
            // Banner gate: I'm the verified owner only when isOwner is
            // true and there's no pending claim still in flight.
            isVerifiedOwner: detail.isOwner && !detail.isPendingOwner,
            stats: stats
        ))
    }

    private func applyPublic(_ public_: HomePublicProfileResponse.HomePublicProfile) {
        let stats: [HomeHeroStat] = [
            HomeHeroStat(id: "members", value: "\(public_.memberCount)", label: "Members"),
            HomeHeroStat(id: "gigs", value: "\(public_.nearbyGigs)", label: "Nearby gigs"),
            HomeHeroStat(
                id: "visibility",
                value: public_.visibility.capitalized,
                label: "Visibility"
            )
        ]
        state = .loaded(content(
            address: public_.address,
            verified: public_.hasVerifiedOwner,
            // Public-profile path is hit when the user is NOT a verified
            // owner — the private detail call returned 403/404 first.
            isVerifiedOwner: false,
            stats: stats
        ))
    }

    private func content(
        address: String,
        verified: Bool,
        isVerifiedOwner: Bool,
        stats: [HomeHeroStat]
    ) -> HomeDashboardContent {
        HomeDashboardContent(
            address: address,
            verified: verified,
            isVerifiedOwner: isVerifiedOwner,
            stats: stats,
            quickActions: [
                QuickActionTile(id: "view_bills", label: "Bills", icon: .receipt, tint: .home),
                QuickActionTile(id: "view_polls", label: "Polls", icon: .checkCircle, tint: .home),
                QuickActionTile(id: "view_tasks", label: "Tasks", icon: .listChecks, tint: .home),
                QuickActionTile(id: "view_maintenance", label: "Maintenance", icon: .hammer, tint: .home),
                QuickActionTile(id: "add_mail", label: "Add mail", icon: .mailbox, tint: .home),
                QuickActionTile(id: "add_member", label: "Add member", icon: .userPlus, tint: .personal),
                QuickActionTile(id: "pets", label: "Pets", icon: .pawPrint, tint: .home)
            ],
            tabs: [
                GridTabsTab(id: "overview", label: "Overview"),
                GridTabsTab(id: "members", label: "Members"),
                GridTabsTab(id: "mail", label: "Mail"),
                GridTabsTab(id: "access", label: "Access")
            ]
        )
    }
}
