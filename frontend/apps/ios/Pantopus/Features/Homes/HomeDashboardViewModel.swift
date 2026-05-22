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
    /// True when the home has any verified owner; drives the header
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
    public let overview: HomeDashboardOverviewContent
    public let attentionSummary: HomeDashboardAttentionSummary?
}

public struct HomeDashboardOverviewContent: Sendable {
    public let upcoming: [HomeDashboardTimelineItem]
    public let activity: [HomeDashboardActivityItem]
    public let emergency: HomeDashboardEmergencyInfo
}

public struct HomeDashboardTimelineItem: Sendable, Identifiable {
    public let id: String
    public let icon: PantopusIcon
    public let tone: QuickActionTone
    public let title: String
    public let subtitle: String
    public let trailing: String?
}

public struct HomeDashboardActivityItem: Sendable, Identifiable {
    public let id: String
    public let initials: String
    public let tone: QuickActionTone
    public let title: String
    public let detail: String
    public let time: String
}

public struct HomeDashboardEmergencyInfo: Sendable {
    public let title: String
    public let body: String
    public let isConfigured: Bool
}

public struct HomeDashboardAttentionSummary: Sendable {
    public let message: String
    public let chips: [HomeDashboardQuickJump]
}

public struct HomeDashboardQuickJump: Sendable, Identifiable {
    public let id: String
    public let label: String
    public let icon: PantopusIcon
    public let actionId: String
}

public struct HomeDashboardBrandNewContent: Sendable {
    public let content: HomeDashboardContent
    public let onboardingSteps: [HomeDashboardOnboardingStep]
}

public struct HomeDashboardOnboardingStep: Sendable, Identifiable {
    public let id: String
    public let title: String
    public let body: String
    public let cta: String
    public let icon: PantopusIcon
    public let tone: QuickActionTone
    public let actionId: String
}

/// Observed state for the Home Dashboard screen.
public enum HomeDashboardState: Sendable {
    case loading
    case loaded(HomeDashboardContent)
    case empty(HomeDashboardBrandNewContent)
    case needsAttention(HomeDashboardContent)
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
        if isContentState { return }
        if let sampleState = HomeDashboardSampleData.state(for: homeId) {
            state = sampleState
            return
        }
        state = .loading
        await fetch()
    }

    /// Pull-to-refresh / retry.
    func refresh() async {
        if let sampleState = HomeDashboardSampleData.state(for: homeId) {
            state = sampleState
            return
        }
        await fetch()
    }

    private var isContentState: Bool {
        switch state {
        case .loaded, .empty, .needsAttention:
            true
        case .loading, .error:
            false
        }
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
            HomeHeroStat(id: "packages", value: "4", label: "Packages"),
            HomeHeroStat(id: "access_codes", value: "2", label: "Access codes"),
            HomeHeroStat(id: "tasks", value: "7", label: "Tasks")
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
            HomeHeroStat(id: "packages", value: "0", label: "Packages"),
            HomeHeroStat(id: "access_codes", value: "0", label: "Access codes"),
            HomeHeroStat(id: "tasks", value: "0", label: "Tasks")
        ]
        state = .loaded(content(
            address: public_.address,
            verified: public_.hasVerifiedOwner,
            // Public-profile path is hit when the user is NOT a verified
            // owner; the private detail call returned 403/404 first.
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
            quickActions: HomeDashboardSampleData.populatedQuickActions,
            tabs: HomeDashboardSampleData.tabs,
            overview: HomeDashboardSampleData.populatedOverview,
            attentionSummary: nil
        )
    }
}
