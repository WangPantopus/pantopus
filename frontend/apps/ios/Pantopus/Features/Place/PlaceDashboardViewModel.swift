//
//  PlaceDashboardViewModel.swift
//  Pantopus
//
//  Drives the Place dashboard (C1 verified / C1a claimed). Fetches the
//  living section-envelope payload for a home, derives tier + the
//  Today's Pulse hero, and exposes the four render states. Mirrors the
//  `HomeDashboardViewModel` lifecycle and the Android
//  `PlaceDashboardViewModel`.
//

import SwiftUI

@Observable
@MainActor
final class PlaceDashboardViewModel {
    enum State: Sendable {
        case loading
        case loaded(PlaceIntelligence)
        case error(message: String)
    }

    private(set) var state: State = .loading
    let homeId: String

    private let api: APIClient
    let onOpenDetail: (PlaceDetailGroup) -> Void
    /// Open the full Today's Pulse stream (the hero taps here).
    let onOpenPulse: () -> Void
    /// Switch the dashboard to another of the user's homes.
    let onSelectHome: (String) -> Void
    /// Claim/verify another address (the switcher's "Add a place").
    let onAddPlace: () -> Void
    let onVerify: () -> Void
    let onOpenHubHome: () -> Void

    init(
        homeId: String,
        api: APIClient = .shared,
        onOpenDetail: @escaping (PlaceDetailGroup) -> Void = { _ in },
        onOpenPulse: @escaping () -> Void = {},
        onSelectHome: @escaping (String) -> Void = { _ in },
        onAddPlace: @escaping () -> Void = {},
        onVerify: @escaping () -> Void = {},
        onOpenHubHome: @escaping () -> Void = {}
    ) {
        self.homeId = homeId
        self.api = api
        self.onOpenDetail = onOpenDetail
        self.onOpenPulse = onOpenPulse
        self.onSelectHome = onSelectHome
        self.onAddPlace = onAddPlace
        self.onVerify = onVerify
        self.onOpenHubHome = onOpenHubHome
    }

    func load() async {
        if case .loaded = state { return }
        await fetch()
    }

    func refresh() async {
        await fetch()
    }

    private func fetch() async {
        do {
            let intelligence: PlaceIntelligence = try await api.request(
                PlaceEndpoints.intelligence(homeId: homeId)
            )
            state = .loaded(intelligence)
        } catch let error as APIError {
            state = .error(message: error.errorDescription ?? "Couldn't load your place.")
        } catch {
            state = .error(message: "Couldn't load your place.")
        }
    }
}
