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
    /// T6.5e (P19.5) Mailbox Vault — saved mail list. Personal pillar.
    case mailboxVault
    case addHome
    case claimOwnership(homeId: String)
    case homeDashboard(homeId: String)
    /// Pets sub-screen for a specific home (T5.2.1).
    case homePets(homeId: String)
    /// Emergency info sub-screen for a specific home (T6.4b / P17).
    case homeEmergency(homeId: String)
    /// Add Emergency Info form (P2.8) — single-page editor backed by
    /// `AddEmergencyInfoFormView`.
    case addEmergencyInfo(homeId: String)
    /// Emergency item detail (P2.8) — read-only summary backed by
    /// `EmergencyInfoDetailView`.
    case emergencyItem(homeId: String, emergencyId: String)
    /// Documents sub-screen for a specific home (T6.4b / P17).
    case homeDocs(homeId: String)
    /// Packages list for a home (T6.3d / P14).
    case homePackages(homeId: String)
    /// Package detail (read-mostly summary with mark-picked-up).
    case packageDetail(homeId: String, packageId: String)
    /// Log-a-package sheet target.
    case logPackage(homeId: String)
    /// Household tasks (per-home chore list) for a specific home
    /// (T6.3c / P11). Distinct from `.myBids` / `.myTasks` (the gig
    /// surfaces in the You tab).
    case homeTasks(homeId: String)
    /// Maintenance sub-screen for a specific home (T6.3b / P10).
    case homeMaintenance(homeId: String)
    /// Members sub-screen for a specific home (T6.3a / P9).
    case homeMembers(homeId: String)
    case publicProfile(userId: String)
    /// P1.6 — Typed Business Profile screen. Pushed from DiscoverHub
    /// business cards, DiscoverBusinesses row taps, and any other
    /// surface that previously routed to a `Business: <name>`
    /// placeholder.
    case businessProfile(businessId: String)
    case pulsePost(postId: String)
    /// Bills list for a home (T5.2.2 / P13).
    case homeBills(homeId: String)
    /// Home calendar list (T6.4c / P18).
    case homeCalendar(homeId: String)
    /// Bill detail (read-mostly summary with mark-paid / remove).
    case billDetail(homeId: String, billId: String)
    /// Add Bill wizard.
    case addBill(homeId: String)
    /// Polls list for a home (T6.3e / P13).
    case homePolls(homeId: String)
    /// Poll detail — read + cast vote (T6.3e / P13).
    case pollDetail(homeId: String, pollId: String)
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
    /// Support Trains list (T6.6c / P26.5) — mutual-aid rotations.
    /// Personal pillar. Reached from the You tab action grid or via
    /// the `pantopus://support-trains` deep link.
    case supportTrains
    /// Review-signups (T6.6c / P26.5) — organizer-only review queue
    /// for one Support Train. Pushed from a Support Trains row tap.
    case reviewSignups(supportTrainId: String)
    /// Admin home-ownership-claims review queue. Gated by
    /// `auth.user.isAdmin` and reached from the Settings menu's Admin
    /// group. Mirrors the web `/app/admin/review-claims` page.
    case reviewClaims
    /// Admin claim detail — pushed from a `reviewClaims` row tap.
    case reviewClaimDetail(claimId: String)
    /// My bids — outgoing bids on neighbour gigs (T5.3.1). Reached from
    /// the You / Me action grid or from Hub's marketplace pillar shelf.
    case myBids
    /// T5.3.4 — per-listing offers panel reached from a listing detail
    /// "View offers" affordance (only the listing's owner sees it).
    case listingOffers(listingId: String, title: String?)
    /// Discover hub — typed-section discovery list (T5.4.1 / P11).
    /// Reached from the Hub Discovery rail's "See all" CTA or via the
    /// `pantopus://discover-hub` deep link.
    case discoverHub
    /// Discover businesses — full business search list (T5.4.2 / P12).
    /// Reached from the Hub Discovery rail's "See all Businesses" CTA,
    /// from the Marketplace tab, or via the Discover hub Businesses
    /// section's "See all" target.
    case discoverBusinesses
    /// Push the chat conversation for a given counterparty. Used by the
    /// Connections row's message-CTA — payload mirrors the Inbox tab's
    /// `InboxConversationDestination` so the same `ChatConversationView`
    /// can host the thread inside the Hub stack.
    case chatConversation(InboxConversationDestination)
    /// P1.5 — Recent activity log. Pushed when the Hub's
    /// `HubRecentActivity` "See all" CTA fires.
    case recentActivity
    /// Hub top-bar menu icon target. Replaced by Settings in T3.1.
    case menu
    /// Edit profile form — pushed by Settings → "Edit profile". P1.4.
    case editProfile
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
        case .discoverHub:
            path.append(.discoverHub)
            _ = router.consume()
        case let .supportTrain(id):
            // pantopus://support-trains/:id deep links land on the
            // organizer review queue when the caller has access; the
            // backend's `/:id/reservations` handler returns 403 for
            // non-organizers and the screen surfaces an error state.
            path.append(.supportTrains)
            if !id.isEmpty {
                path.append(.reviewSignups(supportTrainId: id))
            }
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
            case .openDiscoverHub: path.append(.discoverHub)
            case let .jumpBackIn(item): path.append(Self.route(forJumpBackIn: item))
            // `openToday` taps the weather/today card. The design's tap
            // destination is "home calendar", scheduled for P11 — until
            // then this is a no-op so the chevron-right feels live but
            // doesn't push to a stub.
            case .openToday: break
            case .openRecentActivity: path.append(.recentActivity)
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
        case .business: .businessProfile(businessId: item.id)
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
                onOpenPolls: { Task { @MainActor in push(.homePolls(homeId: homeId)) } },
                onOpenPlaceholder: { label in
                    Task { @MainActor in push(.placeholder(label: label)) }
                },
                onOpenPets: { id in
                    Task { @MainActor in push(.homePets(homeId: id)) }
                },
                onOpenCalendar: { id in
                    Task { @MainActor in push(.homeCalendar(homeId: id)) }
                },
                onOpenDocs: { id in
                    Task { @MainActor in push(.homeDocs(homeId: id)) }
                },
                onOpenEmergency: { id in
                    Task { @MainActor in push(.homeEmergency(homeId: id)) }
                },
                onOpenPackages: { id in
                    Task { @MainActor in push(.homePackages(homeId: id)) }
                },
                onOpenTasks: { id in
                    Task { @MainActor in push(.homeTasks(homeId: id)) }
                },
                onOpenMaintenance: { id in
                    Task { @MainActor in push(.homeMaintenance(homeId: id)) }
                },
                onOpenMembers: { id in
                    Task { @MainActor in push(.homeMembers(homeId: id)) }
                }
            )
        case let .homeMaintenance(homeId):
            MaintenanceListView(
                viewModel: MaintenanceListViewModel(
                    homeId: homeId,
                    onOpenTask: { _ in
                        Task { @MainActor in push(.placeholder(label: "Maintenance detail")) }
                    },
                    onAddTask: {
                        Task { @MainActor in push(.placeholder(label: "Log maintenance")) }
                    }
                )
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
        case let .homePolls(homeId):
            PollsListView(
                viewModel: Self.pollsListViewModel(homeId: homeId, push: push)
            )
        case let .pollDetail(homeId, pollId):
            PollDetailView(
                homeId: homeId,
                pollId: pollId
            ) {
                if !path.isEmpty { path.removeLast() }
            }
        case let .homePets(homeId):
            PetsListView(homeId: homeId)
        case let .homeCalendar(homeId):
            HomeCalendarView(
                viewModel: HomeCalendarViewModel(
                    homeId: homeId,
                    onAddEvent: {
                        Task { @MainActor in push(.placeholder(label: "Add event")) }
                    },
                    onOpenEvent: { _ in
                        Task { @MainActor in push(.placeholder(label: "Event detail")) }
                    }
                )
            )
        case let .homeEmergency(homeId):
            EmergencyInfoView(
                viewModel: EmergencyInfoViewModel(
                    homeId: homeId,
                    onAction: { dto in
                        Task { @MainActor in
                            push(.emergencyItem(homeId: homeId, emergencyId: dto.id))
                        }
                    },
                    onAdd: {
                        Task { @MainActor in
                            push(.addEmergencyInfo(homeId: homeId))
                        }
                    },
                    onShare: {
                        Task { @MainActor in
                            push(.placeholder(label: "Share emergency info"))
                        }
                    },
                    onPrintCard: {
                        Task { @MainActor in
                            push(.placeholder(label: "Print emergency card"))
                        }
                    }
                )
            )
        case let .addEmergencyInfo(homeId):
            AddEmergencyInfoFormView(
                viewModel: AddEmergencyInfoFormViewModel(
                    homeId: homeId,
                    onCreated: { _ in
                        Task { @MainActor in
                            if !path.isEmpty { path.removeLast() }
                        }
                    }
                )
            )
        case let .emergencyItem(homeId, emergencyId):
            EmergencyInfoDetailView(
                homeId: homeId,
                emergencyId: emergencyId,
                onBack: {
                    Task { @MainActor in
                        if !path.isEmpty { path.removeLast() }
                    }
                }
            )
        case let .homeDocs(homeId):
            DocumentsView(
                viewModel: DocumentsViewModel(
                    homeId: homeId,
                    onOpenDocument: { _ in
                        Task { @MainActor in
                            push(.placeholder(label: "Document detail"))
                        }
                    },
                    onUpload: {
                        Task { @MainActor in
                            push(.placeholder(label: "Upload document"))
                        }
                    },
                    onSearch: {
                        Task { @MainActor in
                            push(.placeholder(label: "Search documents"))
                        }
                    },
                    onExport: {
                        Task { @MainActor in
                            push(.placeholder(label: "Export documents"))
                        }
                    },
                    onDocumentAction: { _, _ in
                        Task { @MainActor in
                            push(.placeholder(label: "Document action"))
                        }
                    }
                )
            )
        case let .homePackages(homeId):
            PackagesListView(
                viewModel: Self.packagesListViewModel(
                    homeId: homeId,
                    currentUserId: currentUserId.isEmpty ? nil : currentUserId,
                    push: push
                )
            )
        case let .packageDetail(homeId, packageId):
            PackageDetailView(
                homeId: homeId,
                packageId: packageId
            ) { if !path.isEmpty { path.removeLast() } }
        case let .logPackage(homeId):
            LogPackageSheetView(
                homeId: homeId,
                onClose: { if !path.isEmpty { path.removeLast() } },
                onCreated: { packageId in
                    Task { @MainActor in
                        path.removeAll { route in
                            if case .logPackage = route { return true }
                            return false
                        }
                        path.append(.packageDetail(homeId: homeId, packageId: packageId))
                    }
                }
            )
        case let .homeTasks(homeId):
            HouseholdTasksListView(
                viewModel: HouseholdTasksListViewModel(
                    homeId: homeId,
                    onOpenTask: { _ in
                        Task { @MainActor in push(.placeholder(label: "Task detail")) }
                    },
                    onAddTask: {
                        Task { @MainActor in push(.placeholder(label: "Add a task")) }
                    },
                    onEditRecurring: { _ in
                        Task { @MainActor in push(.placeholder(label: "Edit recurring task")) }
                    }
                )
            )
        case let .homeMembers(homeId):
            MembersListView(homeId: homeId)
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
            // T6.5b (P20) — Generic A17.1 mail detail. P21–P23 will
            // extend this with package / coupon / booklet / certified
            // variants that compose the same shell with their own slots.
            MailDetailView(
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
        case let .businessProfile(businessId):
            BusinessProfileDestination(
                businessId: businessId,
                onBack: { if !path.isEmpty { path.removeLast() } },
                onOpenMessages: { Task { @MainActor in push(.placeholder(label: "Messages")) } },
                onShare: { Task { @MainActor in push(.placeholder(label: "Share business")) } },
                onOpenReport: { Task { @MainActor in push(.placeholder(label: "Report business")) } }
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
                viewModel: MailboxDrawersViewModel(
                    onOpenDrawer: { drawer in
                        Task { @MainActor in push(.drawerDetail(drawer: drawer)) }
                    },
                    onOpenVault: {
                        Task { @MainActor in push(.mailboxVault) }
                    }
                )
            )
        case let .drawerDetail(drawer):
            NotYetAvailableView(tabName: "Drawer · \(drawer)", icon: .mailbox)
        case .mailboxVault:
            // T6.5e (P19.5) — Mailbox Vault list. Personal-pillar
            // surface — both the FAB ("Save mail to vault") and the
            // empty-state CTA ("Open Mailbox") navigate to the inbox
            // list, popping to an existing instance if one is already
            // on the stack so we don't pile mailbox screens on top of
            // each other.
            let goToMailbox: @MainActor () -> Void = {
                if path.contains(.mailbox) {
                    while let last = path.last, last != .mailbox {
                        path.removeLast()
                    }
                } else {
                    push(.mailbox)
                }
            }
            VaultListView(
                viewModel: VaultListViewModel(
                    onOpenItem: { mailId in
                        Task { @MainActor in push(.mailItemDetail(mailId: mailId)) }
                    },
                    onAddTapped: { Task { @MainActor in goToMailbox() } },
                    onOpenMailbox: { Task { @MainActor in goToMailbox() } }
                )
            )
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
                onMessage: { _ in Task { @MainActor in push(.placeholder(label: "Messages")) } },
                onViewOffers: { dto in
                    Task { @MainActor in
                        push(.listingOffers(listingId: dto.id, title: dto.title))
                    }
                }
            )
        case let .listingOffers(listingId, titleHint):
            ListingOffersView(
                viewModel: ListingOffersViewModel(
                    listingId: listingId,
                    listingTitleHint: titleHint,
                    onShareListing: {
                        Task { @MainActor in push(.placeholder(label: "Share listing")) }
                    },
                    onOpenBuyer: { _ in
                        Task { @MainActor in push(.placeholder(label: "Buyer profile")) }
                    },
                    onOpenTransaction: { _ in
                        Task { @MainActor in push(.placeholder(label: "Transaction detail")) }
                    },
                    onEditPrice: {
                        Task { @MainActor in push(.placeholder(label: "Edit listing")) }
                    },
                    onSort: {
                        Task { @MainActor in push(.placeholder(label: "Sort offers")) }
                    }
                )
            )
        case .composeListing:
            NotYetAvailableView(tabName: "Snap & sell", icon: .camera)
        case let .invoiceDetail(invoiceId):
            InvoiceDetailView(
                viewModel: InvoiceDetailViewModel(invoiceId: invoiceId)
            ) { if !path.isEmpty { path.removeLast() } }
        case .recentActivity:
            RecentActivityView(
                viewModel: RecentActivityViewModel { destination in
                    switch destination {
                    case let .gigDetail(id): push(.gigDetail(gigId: id))
                    case let .listingDetail(id): push(.listingDetail(listingId: id))
                    case let .mailItemDetail(id): push(.mailItemDetail(mailId: id))
                    case let .pulsePost(id): push(.pulsePost(postId: id))
                    case let .homeDashboard(id): push(.homeDashboard(homeId: id))
                    case let .placeholder(label): push(.placeholder(label: label))
                    }
                }
            )
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
        case .supportTrains:
            SupportTrainsView(
                viewModel: SupportTrainsViewModel(
                    onStartTrain: {
                        Task { @MainActor in push(.placeholder(label: "Start a support train")) }
                    },
                    onOpenTrain: { trainId in
                        Task { @MainActor in push(.reviewSignups(supportTrainId: trainId)) }
                    },
                    onSearch: {
                        Task { @MainActor in push(.placeholder(label: "Search support trains")) }
                    }
                )
            )
        case let .reviewSignups(supportTrainId):
            ReviewSignupsView(
                viewModel: ReviewSignupsViewModel(
                    supportTrainId: supportTrainId,
                    onShareTrain: {
                        Task { @MainActor in push(.placeholder(label: "Share train")) }
                    },
                    onConfirm: { _ in
                        // POST `/api/support-trains/:id/reservations/:reservationId/confirm`
                        // wiring lands with the editor surface; the VM's
                        // optimistic patch is the user-facing feedback today.
                    },
                    onMessage: { _ in
                        Task { @MainActor in push(.placeholder(label: "Message helper")) }
                    },
                    onEdit: { reservationId in
                        Task { @MainActor in
                            push(.placeholder(label: "Edit signup · \(reservationId)"))
                        }
                    }
                )
            )
        case .discoverHub:
            DiscoverHubView(
                viewModel: DiscoverHubViewModel { target in
                    Task { @MainActor in
                        switch target {
                        case let .person(userId, _):
                            push(.publicProfile(userId: userId))
                        case let .business(businessId, _):
                            push(.businessProfile(businessId: businessId))
                        case let .gig(gigId):
                            push(.gigDetail(gigId: gigId))
                        case let .listing(listingId):
                            push(.listingDetail(listingId: listingId))
                        case .seeAllPeople:
                            push(.connections)
                        case .seeAllBusinesses:
                            push(.discoverBusinesses)
                        case .seeAllGigs:
                            push(.gigsFeed)
                        case .seeAllListings:
                            push(.marketplace)
                        case .openFilters:
                            push(.placeholder(label: "Discovery filters"))
                        }
                    }
                }
            )
        case .discoverBusinesses:
            DiscoverBusinessesView(
                viewModel: DiscoverBusinessesViewModel { target in
                    Task { @MainActor in
                        switch target {
                        case let .business(businessId, _):
                            push(.businessProfile(businessId: businessId))
                        case .openFilters:
                            push(.placeholder(label: "Business filters"))
                        case .widenRadius:
                            push(.placeholder(label: "Set home address"))
                        case .inviteBusiness:
                            push(.placeholder(label: "Invite a business"))
                        }
                    }
                }
            )
        case .myBids:
            MyBidsView(
                viewModel: MyBidsViewModel(
                    onOpenBid: { bid in
                        Task { @MainActor in
                            if let gigId = bid.gigId {
                                push(.gigDetail(gigId: gigId))
                            }
                        }
                    },
                    onOpenFilters: {
                        Task { @MainActor in push(.placeholder(label: "Filter bids")) }
                    },
                    onBrowseTasks: {
                        Task { @MainActor in push(.gigsFeed) }
                    },
                    onMessageClient: { bid in
                        Task { @MainActor in
                            guard let posterId = bid.gig?.userId else { return }
                            push(.chatConversation(InboxConversationDestination(
                                mode: .person(otherUserId: posterId),
                                displayName: bid.gig?.title ?? "Conversation",
                                initials: "··",
                                identityKind: nil,
                                verified: false
                            )))
                        }
                    },
                    onEditBid: { bid in
                        Task { @MainActor in
                            if let gigId = bid.gigId {
                                push(.gigDetail(gigId: gigId))
                            }
                        }
                    },
                    onLeaveReview: { bid in
                        Task { @MainActor in
                            if let gigId = bid.gigId {
                                push(.gigDetail(gigId: gigId))
                            }
                        }
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
                onEditProfile: { Task { @MainActor in push(.editProfile) } },
                onOpenReviewClaims: {
                    // Close the settings sheet/screen then push the admin
                    // queue at the Hub level so its top-bar back chevron
                    // returns to the Hub root, not back into Settings.
                    Task { @MainActor in
                        if !path.isEmpty { path.removeLast() }
                        push(.reviewClaims)
                    }
                },
                onSignedOut: { if !path.isEmpty { path.removeLast() } }
            )
        case .editProfile:
            // `EditProfileView` reads `@Environment(\.dismiss)` and uses
            // it for both Close and save-success pop. Inside a
            // NavigationStack push, `dismiss()` pops to the previous
            // route, so wiring an explicit `onClose` is unnecessary.
            EditProfileView()
        case .reviewClaims:
            ReviewClaimsView(
                viewModel: ReviewClaimsViewModel { claimId in
                    Task { @MainActor in push(.reviewClaimDetail(claimId: claimId)) }
                }
            )
        case let .reviewClaimDetail(claimId):
            ReviewClaimDetailView(
                viewModel: ReviewClaimDetailViewModel(claimId: claimId)
            ) { if !path.isEmpty { path.removeLast() } }
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

    private static func packagesListViewModel(
        homeId: String,
        currentUserId: String?,
        push: @escaping (HubRoute) -> Void
    ) -> PackagesListViewModel {
        let openPackage: @Sendable (String) -> Void = { packageId in
            Task { @MainActor in push(.packageDetail(homeId: homeId, packageId: packageId)) }
        }
        let logPackage: @Sendable () -> Void = {
            Task { @MainActor in push(.logPackage(homeId: homeId)) }
        }
        return PackagesListViewModel(
            homeId: homeId,
            currentUserId: currentUserId,
            onOpenPackage: openPackage,
            onLogPackage: logPackage
        )
    }

    /// Construct the Polls list VM with navigation callbacks wired to
    /// `push(.pollDetail(…))` for row taps. The FAB currently routes to
    /// the not-yet-built composer placeholder — that screen lands in a
    /// follow-up PR.
    private static func pollsListViewModel(
        homeId: String,
        push: @escaping (HubRoute) -> Void
    ) -> PollsListViewModel {
        let openPoll: @Sendable (String) -> Void = { pollId in
            Task { @MainActor in push(.pollDetail(homeId: homeId, pollId: pollId)) }
        }
        let startPoll: @Sendable () -> Void = {
            Task { @MainActor in push(.placeholder(label: "Start a poll")) }
        }
        return PollsListViewModel(
            homeId: homeId,
            onOpenPoll: openPoll,
            onStartPoll: startPoll
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

/// Small wrapper that injects the `openURL` environment action into the
/// Business Profile screen so the "Visit" button can punch out to Safari
/// without the navigation host having to depend on `UIApplication`.
@MainActor
private struct BusinessProfileDestination: View {
    let businessId: String
    let onBack: @MainActor () -> Void
    let onOpenMessages: @MainActor () -> Void
    let onShare: @MainActor () -> Void
    let onOpenReport: @MainActor () -> Void

    @Environment(\.openURL) private var openURL

    var body: some View {
        BusinessProfileView(
            businessId: businessId,
            onBack: onBack,
            onOpenMessages: onOpenMessages,
            onShare: onShare,
            onOpenReport: onOpenReport
        ) { url in
            openURL(url)
        }
    }
}

#Preview {
    HubTabRoot()
}
