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
    case placeholder(label: String)
    case publicProfile(userId: String)
    case chatConversation(InboxConversationDestination)
    /// T5.3.4 — per-listing offers panel reached from a listing detail
    /// "View offers" affordance.
    case listingOffers(listingId: String, title: String?)
    /// P3.3 — Edit an existing listing. Reached from the listing-detail
    /// overflow ("Edit listing") for the owner, or from the listing-
    /// offers panel's "Edit price" affordance.
    case editListing(listingId: String, jumpToStep: ListingComposeStep?)
}

/// NavigationStack wrapper for the Nearby tab.
public struct NearbyTabRoot: View {
    @Environment(AuthManager.self) private var auth
    @State private var path = RouteStack<NearbyRoute>()
    /// P6.6 — share system sheet driven by "Share listing".
    @State private var systemSheet: SystemSheetRequest?

    public init() {}

    private func popAfterListingUpdate(_: String) {
        Task { @MainActor in
            if !path.isEmpty { path.removeLast() }
        }
    }

    private var currentUserId: String {
        if case let .signedIn(user) = auth.state { return user.id }
        return ""
    }

    private static func initials(from name: String) -> String {
        let parts = name.split(separator: " ").prefix(2)
        let joined = parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
        return joined.isEmpty ? "··" : joined
    }

    private static func chatMode(for mode: InboxConversationDestination.Mode) -> ChatThreadMode {
        switch mode {
        case .ai: .ai
        case let .room(id): .room(id: id)
        case let .person(otherUserId): .person(otherUserId: otherUserId)
        }
    }

    private static func chatCounterparty(for dest: InboxConversationDestination) -> ChatCounterparty {
        switch dest.mode {
        case .ai:
            .ai(name: dest.displayName)
        case .room:
            .group(name: dest.displayName, memberCount: nil)
        case .person:
            .person(
                name: dest.displayName,
                initials: dest.initials,
                locality: nil,
                verified: dest.verified,
                online: false
            )
        }
    }

    public var body: some View {
        NavigationStack(path: navigationPathBinding) {
            NearbyMapView(
                onOpenEntity: { entity in
                    path.append(.entityDetail(kind: entity.kind, id: entity.id))
                },
                onBack: nil
            )
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: NearbyRoute.self) { route in
                destination(for: route)
                    .toolbar(.hidden, for: .navigationBar)
            }
        }
        .sheet(item: $systemSheet) { request in request.makeView() }
    }

    private var navigationPathBinding: Binding<NavigationPath> {
        Binding(
            get: { path.navigationPath },
            set: { path.replaceNavigationPath($0) }
        )
    }

    @ViewBuilder
    private func destination(for route: NearbyRoute) -> some View {
        switch route {
        case let .entityDetail(kind, id):
            switch kind {
            case .gig:
                GigDetailView(
                    viewModel: GigDetailViewModel(gigId: id),
                    onBack: { if !path.isEmpty { path.removeLast() } },
                    onMessage: { gig in
                        Task { @MainActor in
                            guard let posterId = gig.userId else { return }
                            let name = gig.creator?.name ?? gig.creator?.username ?? gig.title
                            path.append(.chatConversation(InboxConversationDestination(
                                mode: .person(otherUserId: posterId),
                                displayName: name,
                                initials: Self.initials(from: name),
                                identityKind: nil,
                                verified: gig.creator?.verified ?? false
                            )))
                        }
                    }
                )
            case .listing:
                ListingDetailView(
                    viewModel: ListingDetailViewModel(listingId: id),
                    onBack: { if !path.isEmpty { path.removeLast() } },
                    onMessage: { listing in
                        Task { @MainActor in
                            guard let sellerId = listing.userId else { return }
                            let name = listing.title ?? "Seller"
                            path.append(.chatConversation(InboxConversationDestination(
                                mode: .person(otherUserId: sellerId),
                                displayName: name,
                                initials: Self.initials(from: name),
                                identityKind: nil,
                                verified: false
                            )))
                        }
                    },
                    onViewOffers: { dto in
                        Task { @MainActor in
                            path.append(.listingOffers(listingId: dto.id, title: dto.title))
                        }
                    },
                    onEditListing: { dto in
                        Task { @MainActor in
                            path.append(.editListing(listingId: dto.id, jumpToStep: nil))
                        }
                    }
                )
            }
        case let .placeholder(label):
            NotYetAvailableView(tabName: label, icon: .info)
        case let .publicProfile(userId):
            PublicProfileView(
                userId: userId,
                onBack: { if !path.isEmpty { path.removeLast() } },
                onOpenMessages: { profile in
                    Task { @MainActor in
                        path.append(.chatConversation(InboxConversationDestination(
                            mode: .person(otherUserId: profile.id),
                            displayName: profile.displayName,
                            initials: Self.initials(from: profile.displayName),
                            identityKind: nil,
                            verified: profile.verified ?? false
                        )))
                    }
                }
            )
        case let .chatConversation(dest):
            ChatConversationView(
                viewModel: ChatConversationViewModel(
                    mode: Self.chatMode(for: dest.mode),
                    counterparty: Self.chatCounterparty(for: dest),
                    currentUserId: currentUserId
                ),
                mode: dest.kind,
                onBack: {
                    if !path.isEmpty { path.removeLast() }
                }
            )
        case let .listingOffers(listingId, titleHint):
            ListingOffersView(
                viewModel: ListingOffersViewModel(
                    listingId: listingId,
                    listingTitleHint: titleHint,
                    onShareListing: {
                        let name = titleHint ?? "this listing"
                        systemSheet = .share(
                            items: ["Check out \(name) on Pantopus — \(InviteLinks.downloadURLString)"]
                        )
                    },
                    onOpenBuyer: { buyer in
                        Task { @MainActor in path.append(.publicProfile(userId: buyer.id)) }
                    },
                    onOpenTransaction: { _ in
                        Task { @MainActor in path.append(.placeholder(label: "Transaction detail")) }
                    },
                    onEditPrice: {
                        Task { @MainActor in
                            path.append(.editListing(listingId: listingId, jumpToStep: .price))
                        }
                    }
                )
            )
        case let .editListing(listingId, jumpToStep):
            ListingComposeWizardView(
                mode: .edit(listingId: listingId, jumpToStep: jumpToStep),
                onListingUpdated: popAfterListingUpdate
            )
        }
    }
}

#Preview {
    NearbyTabRoot()
}
