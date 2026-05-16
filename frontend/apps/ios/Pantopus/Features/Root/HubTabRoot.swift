//
//  HubTabRoot.swift
//  Pantopus
//
//  Navigation stack for the Hub tab.
//
// swiftlint:disable cyclomatic_complexity function_body_length type_body_length file_length

import SwiftUI

/// Typed routes within the Hub tab's NavigationStack.
public enum HubRoute: Hashable {
    case myHomes
    case myClaims
    case mailboxDrawers
    case mailbox
    case mailItemDetail(mailId: String)
    case drawerDetail(drawer: String)
    case addHome
    case claimOwnership(homeId: String)
    case homeDashboard(homeId: String)
    /// Pets sub-screen for a specific home (T5.2.1).
    case homePets(homeId: String)
    case publicProfile(userId: String)
    case pulsePost(postId: String)
    /// Bills list for a home (T5.2.2 / P13).
    case homeBills(homeId: String)
    /// Bill detail (read-mostly summary with mark-paid / remove).
    case billDetail(homeId: String, billId: String)
    /// Add Bill wizard.
    case addBill(homeId: String)
    /// Pulse tab (T1.2). Reached from Hub → pillar(.pulse).
    case pulseFeed
    /// Compose post target — placeholder until the compose flow ships.
    case composePost(intent: String)
    /// Gigs feed (T2.3). Reached from Hub → pillar(.gigs).
    case gigsFeed
    /// Gig detail target — placeholder until the Transactional Detail (T2.6) ships.
    case gigDetail(gigId: String)
    /// Map+List Hybrid opened from the Gigs feed map-toggle. Carries the
    /// active category so the map renders the same filtered window.
    case nearbyMapForGigs(categoryKey: String)
    /// Compose gig target — placeholder until the compose flow ships.
    case composeGig(category: String)
    /// Marketplace tab (T2.5). Reached from Hub → pillar(.marketplace).
    case marketplace
    /// Listing detail (T2.6 TransactionalDetailShell · listing variant).
    case listingDetail(listingId: String)
    /// Snap & sell — placeholder until the marketplace compose flow ships.
    case composeListing
    /// Invoice detail (T2.6 TransactionalDetailShell · invoice variant).
    /// Reached from wallet / payments surfaces when those land.
    case invoiceDetail(invoiceId: String)
    /// Bell icon target. Replaced by the real notifications screen in T4.1.
    case notifications
    /// Connections center (T5.2.3). Reached from the You / Me action grid
    /// or via the `pantopus://connections` deep link.
    case connections
    /// Push the chat conversation for a given counterparty. Used by the
    /// Connections row's message-CTA — payload mirrors the Inbox tab's
    /// `InboxConversationDestination` so the same `ChatConversationView`
    /// can host the thread inside the Hub stack.
    case chatConversation(InboxConversationDestination)
    /// Hub top-bar menu icon target. Replaced by Settings in T3.1.
    case menu
    /// Mailbox search target. Replaced when `/api/mailbox` accepts a query.
    case mailboxSearch
    /// Generic placeholder for any intent whose destination hasn't been
    /// built yet. The label is shown by `NotYetAvailableView`.
    case placeholder(label: String)
    #if DEBUG
    case tokenGallery
    case iconGallery
    case componentGallery
    #endif
}

/// NavigationStack wrapper for the Hub tab.
public struct HubTabRoot: View {
    @Environment(AuthManager.self) private var auth
    @State private var path: [HubRoute] = []
    @State private var router = DeepLinkRouter.shared
    #if DEBUG
    @State private var debugSheet: HubRoute?
    #endif

    public init() {}

    private var currentUserId: String {
        if case let .signedIn(user) = auth.state { return user.id }
        return ""
    }

    public var body: some View {
        NavigationStack(path: $path) {
            hub
                .navigationTitle("Hub")
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: HubRoute.self) { route in
                    destination(for: route) { path.append($0) }
                }
            #if DEBUG
                .sheet(item: $debugSheet) { route in
                    destination(for: route) { _ in }
                }
            #endif
        }
        .onChange(of: router.pending) { _, pending in
            consumeDeepLinkIfNeeded(pending: pending)
        }
        .task {
            consumeDeepLinkIfNeeded(pending: router.pending)
        }
    }

    /// Consume the subset of deep-link destinations that map onto a
    /// concrete push within the Hub tab. Tab-level dispatch (selecting
    /// `Hub` over `Inbox`/`You`) stays in `RootTabView` — this only
    /// fires once Hub is the active tab.
    private func consumeDeepLinkIfNeeded(pending: DeepLinkRouter.Destination?) {
        guard let pending else { return }
        switch pending {
        case .connections:
            path.append(.connections)
            _ = router.consume()
        default:
            break
        }
    }

    private var hub: some View {
        HubView { intent in
            switch intent {
            case .openNotifications: path.append(.notifications)
            case .openMenu: path.append(.menu)
            case .startVerification: path.append(.addHome)
            case .action(.addHome): path.append(.addHome)
            case .action(.scanMail): path.append(.mailboxDrawers)
            case .action(.postTask): path.append(.placeholder(label: "Post a gig"))
            case .action(.snapAndSell): path.append(.placeholder(label: "Snap & sell"))
            case .pillar(.mail): path.append(.mailbox)
            case .pillar(.pulse): path.append(.pulseFeed)
            case .pillar(.gigs): path.append(.gigsFeed)
            case .pillar(.marketplace): path.append(.marketplace)
            case let .openDiscovery(item): path.append(Self.route(forDiscovery: item))
            case let .jumpBackIn(item): path.append(Self.route(forJumpBackIn: item))
            }
        }
        .overlay(alignment: .topLeading) { debugTapTarget }
    }

    /// Project an `InboxConversationDestination.Mode` onto the
    /// `ChatThreadMode` consumed by `ChatConversationViewModel`. Mirrors
    /// the helper of the same name on `InboxTabRoot` so the chat shell
    /// behaves identically when reached from Connections vs Inbox.
    private static func chatMode(
        for mode: InboxConversationDestination.Mode
    ) -> ChatThreadMode {
        switch mode {
        case .ai: .ai
        case let .room(id): .room(id: id)
        case let .person(otherUserId): .person(otherUserId: otherUserId)
        }
    }

    /// Project an `InboxConversationDestination` onto the
    /// `ChatCounterparty` consumed by `ChatConversationViewModel`.
    private static func chatCounterparty(
        for dest: InboxConversationDestination
    ) -> ChatCounterparty {
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

    /// Dispatch a discovery card tap to the matching detail route.
    private static func route(forDiscovery item: DiscoveryCardContent) -> HubRoute {
        switch item.kind {
        case .post: .pulsePost(postId: item.id)
        case .person: .publicProfile(userId: item.id)
        case .gig: .placeholder(label: "Gig detail")
        case .business: .placeholder(label: "Business")
        case .unknown: .placeholder(label: item.title)
        }
    }

    /// Backend `jumpBackIn` items carry a canonical web route (e.g.
    /// `/app/mailbox?scope=home&homeId=…`, `/app/homes/<id>/dashboard`,
    /// `/app/chat`, `/gigs/new`). Map that onto a native destination;
    /// fall back to a labeled placeholder when nothing matches.
    private static func route(forJumpBackIn item: JumpBackItem) -> HubRoute {
        let path = item.route
        if path.hasPrefix("/app/mailbox") {
            return .mailbox
        }
        if let homeId = Self.homeId(in: path) {
            return .homeDashboard(homeId: homeId)
        }
        if path.hasPrefix("/app/chat") {
            return .placeholder(label: "Messages")
        }
        if path.hasPrefix("/gigs/new") {
            return .composeGig(category: GigsCategory.all.rawValue)
        }
        if path.hasPrefix("/gigs") {
            return .gigsFeed
        }
        return .placeholder(label: item.title)
    }

    /// Extracts `<id>` from `/app/homes/<id>/dashboard`. Returns `nil`
    /// when the prefix doesn't match.
    private static func homeId(in route: String) -> String? {
        let prefix = "/app/homes/"
        guard route.hasPrefix(prefix) else { return nil }
        let after = route.dropFirst(prefix.count)
        let segment = after.split(separator: "/").first.map(String.init)
        return segment?.isEmpty == false ? segment : nil
    }

    /// 44pt invisible 5-tap target in the top-leading safe area — the
    /// production hub hides its nav bar so there's no visible title to
    /// tap. Hidden from accessibility so VoiceOver users can't trip
    /// the debug menu by accident. No-op in release.
    @ViewBuilder
    private var debugTapTarget: some View {
        #if DEBUG
        Color.clear
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
            .onTapGesture(count: 5) { debugSheet = .tokenGallery }
            .accessibilityHidden(true)
        #else
        EmptyView()
        #endif
    }

    @ViewBuilder
    private func destination(
        for route: HubRoute,
        push: @escaping (HubRoute) -> Void
    ) -> some View {
        switch route {
        case .myHomes:
            MyHomesListView(
                viewModel: MyHomesListViewModel(
                    onOpenHome: { homeId in Task { @MainActor in push(.homeDashboard(homeId: homeId)) } },
                    onAddHome: { Task { @MainActor in push(.addHome) } }
                )
            )
        case .myClaims:
            MyClaimsListView(
                viewModel: MyClaimsListViewModel(
                    onStartNewClaim: { Task { @MainActor in push(.addHome) } },
                    onOpenClaim: { _ in
                        Task { @MainActor in push(.placeholder(label: "Claim status")) }
                    }
                )
            )
        case let .homeDashboard(homeId):
            HomeDashboardView(
                homeId: homeId,
                onClaimOwnership: { Task { @MainActor in push(.claimOwnership(homeId: homeId)) } },
                onOpenClaimsList: { Task { @MainActor in push(.myClaims) } },
                onOpenBills: { Task { @MainActor in push(.homeBills(homeId: homeId)) } },
                onOpenPlaceholder: { label in
                    Task { @MainActor in push(.placeholder(label: label)) }
                },
                onOpenPets: { id in
                    Task { @MainActor in push(.homePets(homeId: id)) }
                }
            )
        case let .homeBills(homeId):
            BillsListView(
                viewModel: Self.billsListViewModel(homeId: homeId, push: push)
            )
        case let .billDetail(homeId, billId):
            BillDetailView(
                homeId: homeId,
                billId: billId
            ) {
                if !path.isEmpty { path.removeLast() }
            }
        case let .addBill(homeId):
            AddBillWizardView(
                homeId: homeId,
                onClose: { if !path.isEmpty { path.removeLast() } },
                onCreated: { billId in
                    // Replace the wizard with the new bill's detail so
                    // Back returns to the Bills list, not the success
                    // step.
                    path.removeAll { route in
                        if case .addBill = route { return true }
                        return false
                    }
                    path.append(.billDetail(homeId: homeId, billId: billId))
                }
            )
        case let .homePets(homeId):
            PetsListView(homeId: homeId)
        case let .claimOwnership(homeId):
            ClaimOwnershipWizardView(
                homeId: homeId,
                onClose: {
                    if !path.isEmpty { path.removeLast() }
                },
                onOpenClaimsList: {
                    path.removeAll { route in
                        if case .claimOwnership = route { return true }
                        return false
                    }
                    path.append(.myClaims)
                }
            )
        case .mailbox:
            MailboxListView(
                viewModel: MailboxListViewModel(
                    onOpenMail: { mailId in
                        Task { @MainActor in push(.mailItemDetail(mailId: mailId)) }
                    },
                    onOpenSearch: { push(.mailboxSearch) }
                )
            )
        case let .mailItemDetail(mailId):
            MailboxItemDetailView(
                mailId: mailId,
                onBack: {
                    if !path.isEmpty { path.removeLast() }
                },
                onOpenSenderProfile: { userId in
                    Task { @MainActor in push(.publicProfile(userId: userId)) }
                }
            )
        case let .publicProfile(userId):
            PublicProfileView(
                userId: userId,
                onBack: { if !path.isEmpty { path.removeLast() } },
                onOpenMessages: { Task { @MainActor in push(.placeholder(label: "Messages")) } },
                onOpenReport: { Task { @MainActor in push(.placeholder(label: "Report")) } }
            )
        case let .pulsePost(postId):
            PulsePostDetailView(
                postId: postId,
                onBack: {
                    if !path.isEmpty { path.removeLast() }
                },
                onOpenProfile: { userId in
                    Task { @MainActor in push(.publicProfile(userId: userId)) }
                }
            )
        case .mailboxDrawers:
            MailboxDrawersView(
                viewModel: MailboxDrawersViewModel { drawer in
                    Task { @MainActor in push(.drawerDetail(drawer: drawer)) }
                }
            )
        case let .drawerDetail(drawer):
            NotYetAvailableView(tabName: "Drawer · \(drawer)", icon: .mailbox)
        case .pulseFeed:
            FeedView(
                onOpenPost: { postId in
                    Task { @MainActor in push(.pulsePost(postId: postId)) }
                },
                onCompose: { intent in
                    Task { @MainActor in push(.composePost(intent: intent.rawValue)) }
                },
                onBack: { if !path.isEmpty { path.removeLast() } }
            )
        case let .composePost(intent):
            NotYetAvailableView(tabName: "Compose · \(intent.capitalized)", icon: .pencil)
        case .gigsFeed:
            GigsFeedView(
                onOpenGig: { gigId in
                    Task { @MainActor in push(.gigDetail(gigId: gigId)) }
                },
                onCompose: { category in
                    Task { @MainActor in push(.composeGig(category: category.rawValue)) }
                },
                onOpenMap: { category in
                    Task { @MainActor in push(.nearbyMapForGigs(categoryKey: category.rawValue)) }
                },
                onOpenSearch: { Task { @MainActor in push(.placeholder(label: "Gig search")) } },
                onOpenFilters: { Task { @MainActor in push(.placeholder(label: "Gig filters")) } },
                onBack: { if !path.isEmpty { path.removeLast() } }
            )
        case let .gigDetail(gigId):
            GigDetailView(
                viewModel: GigDetailViewModel(gigId: gigId),
                onBack: { if !path.isEmpty { path.removeLast() } },
                onMessage: { _ in Task { @MainActor in push(.placeholder(label: "Messages")) } }
            )
        case let .composeGig(category):
            NotYetAvailableView(tabName: "Post a task · \(category.capitalized)", icon: .pencil)
        case let .nearbyMapForGigs(categoryKey):
            NearbyMapView(
                viewModel: NearbyMapViewModel(
                    initialCategory: GigsCategory(rawValue: categoryKey) ?? .all
                ),
                onOpenEntity: { entity in
                    Task { @MainActor in
                        switch entity.kind {
                        case .gig: push(.gigDetail(gigId: entity.id))
                        case .listing: push(.listingDetail(listingId: entity.id))
                        }
                    }
                },
                onOpenFilters: { Task { @MainActor in push(.placeholder(label: "Map filters")) } },
                onBack: { if !path.isEmpty { path.removeLast() } }
            )
        case .marketplace:
            MarketplaceView(
                onOpenListing: { listingId in
                    Task { @MainActor in push(.listingDetail(listingId: listingId)) }
                },
                onCompose: { Task { @MainActor in push(.composeListing) } },
                onBack: { if !path.isEmpty { path.removeLast() } }
            )
        case let .listingDetail(listingId):
            ListingDetailView(
                viewModel: ListingDetailViewModel(listingId: listingId),
                onBack: { if !path.isEmpty { path.removeLast() } },
                onMessage: { _ in Task { @MainActor in push(.placeholder(label: "Messages")) } }
            )
        case .composeListing:
            NotYetAvailableView(tabName: "Snap & sell", icon: .camera)
        case let .invoiceDetail(invoiceId):
            InvoiceDetailView(
                viewModel: InvoiceDetailViewModel(invoiceId: invoiceId)
            ) { if !path.isEmpty { path.removeLast() } }
        case .notifications:
            NotificationsView(
                viewModel: NotificationsViewModel()
            ) { if !path.isEmpty { path.removeLast() } }
        case .connections:
            ConnectionsView(
                viewModel: ConnectionsViewModel(
                    onMessage: { target in
                        Task { @MainActor in
                            push(.chatConversation(InboxConversationDestination(
                                mode: .person(otherUserId: target.userId),
                                displayName: target.displayName,
                                initials: target.initials,
                                identityKind: nil,
                                verified: target.verified
                            )))
                        }
                    },
                    onFindPeople: {
                        Task { @MainActor in push(.placeholder(label: "Find people")) }
                    }
                )
            )
        case let .chatConversation(dest):
            ChatConversationView(
                viewModel: ChatConversationViewModel(
                    mode: Self.chatMode(for: dest.mode),
                    counterparty: Self.chatCounterparty(for: dest),
                    currentUserId: currentUserId
                )
            ) { if !path.isEmpty { path.removeLast() } }
        case .menu:
            SettingsView(
                onClose: { if !path.isEmpty { path.removeLast() } },
                onEditProfile: { Task { @MainActor in push(.placeholder(label: "Edit profile")) } },
                onSignedOut: { if !path.isEmpty { path.removeLast() } }
            )
        case .mailboxSearch:
            NotYetAvailableView(tabName: "Mail search", icon: .search)
        case let .placeholder(label):
            NotYetAvailableView(tabName: label, icon: .info)
        case .addHome:
            AddHomeWizardView { homeId in
                // Replace the wizard with the dashboard so Back goes to
                // MyHomes, not the success screen.
                path.removeAll { $0 == .addHome }
                path.append(.homeDashboard(homeId: homeId))
            }
        #if DEBUG
        case .tokenGallery: TokenGalleryView()
        case .iconGallery: IconGalleryView()
        case .componentGallery: ComponentGalleryView()
        #endif
        }
    }

    private static func billsListViewModel(
        homeId: String,
        push: @escaping (HubRoute) -> Void
    ) -> BillsListViewModel {
        let openBill: @Sendable (String) -> Void = { billId in
            Task { @MainActor in push(.billDetail(homeId: homeId, billId: billId)) }
        }
        let addBill: @Sendable () -> Void = {
            Task { @MainActor in push(.addBill(homeId: homeId)) }
        }
        return BillsListViewModel(
            homeId: homeId,
            onOpenBill: openBill,
            onAddBill: addBill
        )
    }
}

#if DEBUG
extension HubRoute: Identifiable {
    public var id: Self {
        self
    }
}
#endif

#Preview {
    HubTabRoot()
}
