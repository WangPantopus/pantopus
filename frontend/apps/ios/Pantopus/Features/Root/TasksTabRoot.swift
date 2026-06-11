//
//  TasksTabRoot.swift
//  Pantopus
//
//  Tasks tab — neighbour gigs feed with map drill-down. Mirrors the
//  React Native Tasks tab (formerly the Nearby tab).
//

import SwiftUI

/// Typed routes within the Tasks tab's NavigationStack.
public enum TasksRoute: Hashable {
    case gigDetail(gigId: String)
    case composeGig(category: String)
    case quickPostGig(category: String)
    case tasksMap(categoryKey: String)
    case gigSearch
    case listingDetail(listingId: String)
    case entityDetail(kind: MapEntityKind, id: String)
    case publicProfile(userId: String)
    case chatConversation(InboxConversationDestination)
    case listingOffers(listingId: String, titleHint: String?)
    case editListing(listingId: String, jumpToStep: ListingComposeStep?)
    case placeholder(label: String)
}

/// NavigationStack wrapper for the Tasks tab.
public struct TasksTabRoot: View {
    @Environment(AuthManager.self) private var auth
    @State private var path = RouteStack<TasksRoute>()
    @State private var router = DeepLinkRouter.shared
    @State private var systemSheet: SystemSheetRequest?

    public init() {}

    private var currentUserId: String {
        if case let .signedIn(user) = auth.state { return user.id }
        return ""
    }

    private static func initials(from name: String) -> String {
        let parts = name.split(separator: " ").prefix(2)
        let joined = parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
        return joined.isEmpty ? "··" : joined
    }

    public var body: some View {
        NavigationStack(path: navigationPathBinding) {
            GigsFeedView(
                onOpenGig: { gigId in
                    path.append(.gigDetail(gigId: gigId))
                },
                onCompose: { category in
                    path.append(.composeGig(category: category.rawValue))
                },
                onOpenMap: { category in
                    path.append(.tasksMap(categoryKey: category.rawValue))
                },
                onOpenSearch: { path.append(.gigSearch) },
                onBack: nil
            )
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: TasksRoute.self) { route in
                destination(for: route)
                    .toolbar(.hidden, for: .navigationBar)
            }
        }
        .onChange(of: router.pending) { _, pending in
            consumeDeepLinkIfNeeded(pending: pending)
        }
        .task {
            consumeDeepLinkIfNeeded(pending: router.pending)
        }
        .sheet(item: $systemSheet) { request in request.makeView() }
    }

    private var navigationPathBinding: Binding<NavigationPath> {
        Binding(
            get: { path.navigationPath },
            set: { path.replaceNavigationPath($0) }
        )
    }

    private func consumeDeepLinkIfNeeded(pending: DeepLinkRouter.Destination?) {
        guard let pending else { return }
        switch pending {
        case let .gig(id):
            path.replaceNavigationPath(NavigationPath())
            path.append(.gigDetail(gigId: id))
            _ = router.consume()
        default:
            break
        }
    }

    @MainActor
    private func pop() {
        if !path.isEmpty { path.removeLast() }
    }

    @ViewBuilder
    private func destination(for route: TasksRoute) -> some View {
        switch route {
        case let .gigDetail(gigId):
            gigDetailDestination(gigId: gigId)
        case let .composeGig(category):
            composeGigDestination(category: category)
        case let .quickPostGig(category):
            quickPostGigDestination(category: category)
        case let .tasksMap(categoryKey):
            tasksMapDestination(categoryKey: categoryKey)
        case .gigSearch:
            gigSearchDestination()
        case let .entityDetail(kind, id):
            entityDestination(kind: kind, id: id)
        case let .listingDetail(listingId):
            listingDetailDestination(listingId: listingId)
        case let .placeholder(label):
            NotYetAvailableView(tabName: label, icon: .info)
        case let .publicProfile(userId):
            publicProfileDestination(userId: userId)
        case let .chatConversation(dest):
            chatDestination(dest)
        case let .listingOffers(listingId, titleHint):
            listingOffersDestination(listingId: listingId, titleHint: titleHint)
        case let .editListing(listingId, jumpToStep):
            editListingDestination(listingId: listingId, jumpToStep: jumpToStep)
        }
    }

    private func composeGigDestination(category: String) -> some View {
        GigComposeWizardView(preselectedCategoryKey: category) { gigId in
            path.removeAll { item in
                if case .composeGig = item { return true }
                return false
            }
            path.append(.gigDetail(gigId: gigId))
        }
    }

    private func quickPostGigDestination(category: String) -> some View {
        PostGigV1View(
            viewModel: PostGigV1ViewModel(
                initialState: PostGigV1State(
                    form: PostGigV1Form(category: GigsCategory(rawValue: category) ?? .all)
                )
            ),
            onClose: pop
        ) { gigId in
            path.removeAll { item in
                if case .quickPostGig = item { return true }
                return false
            }
            path.append(.gigDetail(gigId: gigId))
        }
    }

    private func tasksMapDestination(categoryKey: String) -> some View {
        TasksMapView(
            viewModel: TasksMapViewModel(
                initialCategory: GigsCategory(rawValue: categoryKey) ?? .all
            ),
            onOpenTask: { taskId in
                Task { @MainActor in path.append(.gigDetail(gigId: taskId)) }
            },
            onCompose: { category in
                Task { @MainActor in path.append(.composeGig(category: category.rawValue)) }
            },
            onBack: pop
        )
    }

    private func gigSearchDestination() -> some View {
        GigSearchView(
            onOpenGig: { gigId in
                Task { @MainActor in path.append(.gigDetail(gigId: gigId)) }
            },
            onBack: pop
        )
    }

    private func publicProfileDestination(userId: String) -> some View {
        PublicProfileView(userId: userId, onBack: pop) { profile in
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
    }

    private func editListingDestination(listingId: String, jumpToStep: ListingComposeStep?) -> some View {
        ListingComposeWizardView(
            mode: .edit(listingId: listingId, jumpToStep: jumpToStep),
            // swiftlint:disable:next trailing_closure
            onListingUpdated: { _ in pop() }
        )
    }

    @ViewBuilder
    private func entityDestination(kind: MapEntityKind, id: String) -> some View {
        switch kind {
        case .gig:
            gigDetailDestination(gigId: id)
        case .listing:
            listingDetailDestination(listingId: id)
        }
    }

    private func gigDetailDestination(gigId: String) -> some View {
        GigDetailView(viewModel: GigDetailViewModel(gigId: gigId), onBack: pop) { destination in
            Task { @MainActor in
                path.append(.chatConversation(destination))
            }
        }
    }

    private func listingDetailDestination(listingId: String) -> some View {
        ListingDetailView(
            viewModel: ListingDetailViewModel(listingId: listingId),
            onBack: pop,
            onMessage: { listing in
                Task { @MainActor in
                    guard let sellerId = listing.userId else { return }
                    let name = listing.title ?? "Seller"
                    path.append(.chatConversation(InboxConversationDestination(
                        mode: .person(otherUserId: sellerId),
                        displayName: name,
                        initials: Self.initials(from: name),
                        identityKind: nil,
                        verified: false,
                        initialTopic: ChatInitialTopic(topicType: "listing", topicRefId: listing.id, title: name)
                    )))
                }
            },
            onViewOffers: { dto in
                Task { @MainActor in
                    path.append(.listingOffers(listingId: dto.id, titleHint: dto.title))
                }
            },
            onEditListing: { dto in
                Task { @MainActor in
                    path.append(.editListing(listingId: dto.id, jumpToStep: nil))
                }
            }
        )
    }

    private func chatDestination(_ dest: InboxConversationDestination) -> some View {
        ChatConversationView(
            viewModel: ChatConversationViewModel(
                mode: Self.chatMode(for: dest.mode),
                counterparty: Self.chatCounterparty(for: dest),
                currentUserId: currentUserId,
                initialTopic: dest.initialTopic
            ),
            mode: dest.kind,
            onUseAIDraft: { draft in
                path.append(Self.route(for: draft))
            }
        ) {
            pop()
        }
    }

    private func listingOffersDestination(listingId: String, titleHint: String?) -> some View {
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

    private static func route(for draft: ChatAIDraftCard) -> TasksRoute {
        switch draft.type {
        case "gig": .composeGig(category: GigsCategory.all.rawValue)
        case "listing": .placeholder(label: "Open Marketplace to create listing")
        case "post": .placeholder(label: "Open Pulse composer from Messages tab")
        default: .placeholder(label: "Draft")
        }
    }
}

#Preview {
    TasksTabRoot()
        .environment(AuthManager.previewSignedIn)
}
