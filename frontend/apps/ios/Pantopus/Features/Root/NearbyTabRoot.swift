//
//  NearbyTabRoot.swift
//  Pantopus
//
//  Nearby tab — hosts the T2.4 Map+List Hybrid. Single-canvas MapKit
//  view with a 3-stop bottom sheet. Entity taps push to Transactional
//  Detail (T2.6) — placeholder for now.
//

import SwiftUI

/// Typed routes within the Nearby tab's NavigationStack.
public enum NearbyRoute: Hashable {
    case entityDetail(kind: MapEntityKind, id: String)
    case filters
    case placeholder(label: String)
    /// T5.3.4 — per-listing offers panel reached from a listing detail
    /// "View offers" affordance.
    case listingOffers(listingId: String, title: String?)
}

/// NavigationStack wrapper for the Nearby tab.
public struct NearbyTabRoot: View {
    @State private var path: [NearbyRoute] = []

    public init() {}

    public var body: some View {
        NavigationStack(path: $path) {
            NearbyMapView(
                onOpenEntity: { entity in
                    path.append(.entityDetail(kind: entity.kind, id: entity.id))
                },
                onOpenFilters: { path.append(.filters) },
                onBack: nil
            )
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: NearbyRoute.self) { route in
                destination(for: route)
                    .toolbar(.hidden, for: .navigationBar)
            }
        }
    }

    @ViewBuilder
    private func destination(for route: NearbyRoute) -> some View {
        switch route {
        case let .entityDetail(kind, id):
            switch kind {
            case .gig:
                GigDetailView(
                    viewModel: GigDetailViewModel(gigId: id)
                ) { if !path.isEmpty { path.removeLast() } }
            case .listing:
                ListingDetailView(
                    viewModel: ListingDetailViewModel(listingId: id),
                    onBack: { if !path.isEmpty { path.removeLast() } },
                    onViewOffers: { dto in
                        Task { @MainActor in
                            path.append(.listingOffers(listingId: dto.id, title: dto.title))
                        }
                    }
                )
            }
        case .filters:
            NotYetAvailableView(tabName: "Map filters", icon: .slidersHorizontal)
        case let .placeholder(label):
            NotYetAvailableView(tabName: label, icon: .info)
        case let .listingOffers(listingId, titleHint):
            ListingOffersView(
                viewModel: ListingOffersViewModel(
                    listingId: listingId,
                    listingTitleHint: titleHint,
                    onShareListing: {
                        Task { @MainActor in path.append(.placeholder(label: "Share listing")) }
                    },
                    onOpenBuyer: { _ in
                        Task { @MainActor in path.append(.placeholder(label: "Buyer profile")) }
                    },
                    onOpenTransaction: { _ in
                        Task { @MainActor in path.append(.placeholder(label: "Transaction detail")) }
                    },
                    onEditPrice: {
                        Task { @MainActor in path.append(.placeholder(label: "Edit listing")) }
                    },
                    onSort: {
                        Task { @MainActor in path.append(.placeholder(label: "Sort offers")) }
                    }
                )
            )
        }
    }
}

#Preview {
    NearbyTabRoot()
}
