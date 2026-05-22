//
//  HubTabRoot.swift
//  Pantopus
//
//  Navigation stack for the Hub tab.
//
// swiftlint:disable cyclomatic_complexity function_body_length type_body_length file_length

import SwiftUI
import UIKit

/// Typed routes within the Hub tab's NavigationStack.
public enum HubRoute: Hashable {
    case myHomes
    case myClaims
    case mailItemDetail(mailId: String)
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
    /// P2.10 — Upload document form for a home.
    case uploadDocument(homeId: String)
    /// P2.10 — Document detail (preview + metadata + footer actions).
    case documentDetail(homeId: String, documentId: String)
    /// P4.5 — Document Search surface (search across title / tags /
    /// category) for a home's vault.
    case documentSearch(homeId: String)
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
    /// P2.4 — Add a new household task. Reached from the household
    /// tasks list FAB.
    case addHouseholdTask(homeId: String)
    /// P2.4 — Edit an existing household task. Reached from the
    /// "Edit recurring" overflow action on a Recurring row.
    case editHouseholdTask(homeId: String, taskId: String)
    /// Maintenance sub-screen for a specific home (T6.3b / P10).
    case homeMaintenance(homeId: String)
    /// P2.9 — Log a maintenance entry. Pushed from the Maintenance list
    /// FAB; on success the host pops back and refreshes the list.
    case logMaintenance(homeId: String)
    /// P2.9 — Maintenance detail for a specific task. Pushed from a
    /// per-row tap on the Maintenance list.
    case maintenanceDetail(homeId: String, taskId: String)
    /// P2.9 — Edit an existing maintenance entry. Re-uses the
    /// `LogMaintenanceFormView` shell in edit mode.
    case editMaintenance(homeId: String, taskId: String)
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
    /// Add / edit calendar event form (P2.7). `eventId` non-nil = edit;
    /// `prefilledCategory` seeds the chip selector when arriving from
    /// the empty-state quick-start tiles.
    case addCalendarEvent(homeId: String, eventId: String?, prefilledCategory: String?)
    /// Read-only event detail (P2.7). Edit + Delete actions live here.
    case calendarEventDetail(homeId: String, eventId: String)
    /// Bill detail (read-mostly summary with mark-paid / remove).
    case billDetail(homeId: String, billId: String)
    /// Add / Edit Bill wizard. When `billId` is non-nil the wizard
    /// loads the existing bill, seeds every step, and PUTs on submit.
    case addBill(homeId: String, billId: String? = nil)
    /// Polls list for a home (T6.3e / P13).
    case homePolls(homeId: String)
    /// Poll detail — read + cast vote (T6.3e / P13).
    case pollDetail(homeId: String, pollId: String)
    /// Start-a-poll composer (P2.5). Pushed from the Polls list FAB +
    /// empty-state CTA.
    case startPoll(homeId: String)
    /// Pulse tab (T1.2). Reached from Hub → pillar(.pulse).
    case pulseFeed
    /// Compose post target — placeholder until the compose flow ships.
    case composePost(intent: String)
    /// P3.5 — Edit an existing Pulse post. Re-uses the compose flow in
    /// edit mode (prefill + PATCH submit + locked intent picker).
    case editPost(postId: String)
    /// Gigs feed (T2.3). Reached from Hub → pillar(.gigs).
    case gigsFeed
    /// Gig Search (P4.4). Pushed from the Gigs feed search bar.
    case gigSearch
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
    /// P3.3 — Edit an existing listing. Reached from the listing-detail
    /// overflow ("Edit listing") for the owner, or from the listing-
    /// offers panel's "Edit price" affordance (which seeds
    /// `jumpToStep == .price`).
    case editListing(listingId: String, jumpToStep: ListingComposeStep?)
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
    /// P2.6 — Start-a-Support-Train wizard (organizer compose flow).
    /// Pushed when the Support Trains FAB / empty-state CTA fires.
    case startSupportTrain
    /// Review-signups (T6.6c / P26.5) — organizer-only review queue
    /// for one Support Train. Pushed from a Support Trains row tap.
    case reviewSignups(supportTrainId: String)
    /// P4.6 — Support Trains search. Pushed from the Support Trains list
    /// top-bar search action; reuses the shared `SearchListShell`.
    case searchSupportTrains
    /// P3.7 — Edit Signup form (organizer-side mutation of a helper
    /// reservation). Pushed from the Review-signups per-row Edit
    /// action with the seed DTO baked into the route so the form can
    /// prefill without a re-fetch.
    case editSignup(reservation: SupportTrainReservationDTO)
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
    /// Mailbox search target (P4.2). Client-side filter over the user's
    /// mailbox — sender / subject / body / category.
    case mailboxSearch
    /// Generic placeholder for any intent whose destination hasn't been
    /// built yet. The label is shown by `NotYetAvailableView`.
    case placeholder(label: String)
    /// A10.3 — Full "Today" briefing (weather, air, daylight, signals).
    /// Pushed when the Hub's Today card is tapped.
    case todayDetail
    /// A.4 — Property details for a home.
    case propertyDetails(homeId: String)
    /// A.3 — Add a guest to a home.
    case addGuest(homeId: String)
    /// A11.1 — Tasks map. Gigs-only mode of the MapListHybrid archetype,
    /// opened from the Gigs feed's list/map toggle. Carries the active
    /// category so the map renders the same filtered window.
    case tasksMap(categoryKey: String)
    /// A.x — Explore (neighbourhood discovery surface).
    case explore
    /// B.1 — unified Mailbox root (drawer chips × tabs). Entry point for
    /// all mailbox navigation; supersedes `.mailboxDrawers` and `.mailbox`.
    case mailboxRoot
    /// A.x — Mailbox map.
    case mailboxMap
    #if DEBUG
    case tokenGallery
    case iconGallery
    case componentGallery
    #endif
}

/// NavigationStack wrapper for the Hub tab.
public struct HubTabRoot: View {
    @Environment(AuthManager.self) private var auth
    @State private var path = RouteStack<HubRoute>()
    @State private var router = DeepLinkRouter.shared
    /// P6.6 — share / mail system sheet driven by "Share listing",
    /// "Share train", and "Invite a business".
    @State private var systemSheet: SystemSheetRequest?
    /// Full-screen modal routes. A13.1 Add Guest uses this so the tab
    /// bar is not visible while the access-grant form is open.
    @State private var modalRoute: HubModalRoute?
    /// P6.6 — "Find people" → contacts picker → invite share.
    @State private var showFindPeople = false
    #if DEBUG
    @State private var debugSheet: HubRoute?
    #endif

    public init() {}

    private var currentUserId: String {
        if case let .signedIn(user) = auth.state { return user.id }
        return ""
    }

    @MainActor
    private func pop() {
        if !path.isEmpty { path.removeLast() }
    }

    private func handleListingCreated(_ listingId: String, push: @escaping (HubRoute) -> Void) {
        Task { @MainActor in
            pop()
            push(.listingDetail(listingId: listingId))
        }
    }

    private func listingCreatedHandler(push: @escaping (HubRoute) -> Void) -> (String) -> Void {
        { listingId in
            handleListingCreated(listingId, push: push)
        }
    }

    private func handleListingUpdated(_: String) {
        // Pop the wizard — the listing-detail (or offers) screen
        // underneath refreshes on next `.task`.
        Task { @MainActor in pop() }
    }

    public var body: some View {
        NavigationStack(path: $path.navigationPath) {
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
        .onChange(of: path.navigationPath.count) { _, count in
            path.syncToNavigationPathCount(count)
        }
        .onChange(of: router.pending) { _, pending in
            consumeDeepLinkIfNeeded(pending: pending)
        }
        .task {
            consumeDeepLinkIfNeeded(pending: router.pending)
        }
        .fullScreenCover(item: $modalRoute) { item in
            destination(for: item.route) { path.append($0) }
        }
        .sheet(item: $systemSheet) { request in request.makeView() }
        .findPeopleSheet(isPresented: $showFindPeople)
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
            case .action(.scanMail): path.append(.mailboxRoot)
            case .action(.postTask): path.append(.placeholder(label: "Post a gig"))
            case .action(.snapAndSell): path.append(.placeholder(label: "Snap & sell"))
            case .pillar(.mail): path.append(.mailboxRoot)
            case .pillar(.pulse): path.append(.pulseFeed)
            case .pillar(.gigs): path.append(.gigsFeed)
            case .pillar(.marketplace): path.append(.marketplace)
            case let .openDiscovery(item): path.append(Self.route(forDiscovery: item))
            case .openDiscoverHub: path.append(.discoverHub)
            case let .jumpBackIn(item): path.append(Self.route(forJumpBackIn: item))
            // `openToday` taps the weather/today card → full Today briefing
            // (A10.3 — weather, air, daylight, and neighbourhood signals).
            case .openToday: path.append(.todayDetail)
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
            return .mailboxRoot
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

    /// Two-letter initials derived from a display name. Falls back to
    /// `··` when the input has no alphanumeric content so the chat header's
    /// avatar still renders.
    fileprivate static func initials(from name: String) -> String {
        let parts = name.split(separator: " ").prefix(2)
        let joined = parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
        return joined.isEmpty ? "··" : joined
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
                onOpenAccessCodes: { _, _ in
                    Task { @MainActor in push(.placeholder(label: "Access codes")) }
                },
                onOpenTasks: { id in
                    Task { @MainActor in push(.homeTasks(homeId: id)) }
                },
                onOpenMaintenance: { id in
                    Task { @MainActor in push(.homeMaintenance(homeId: id)) }
                },
                onOpenMembers: { id in
                    Task { @MainActor in push(.homeMembers(homeId: id)) }
                },
                onOpenPropertyDetails: { id in
                    Task { @MainActor in push(.propertyDetails(homeId: id)) }
                }
            )
        case let .homeMaintenance(homeId):
            MaintenanceListView(
                viewModel: MaintenanceListViewModel(
                    homeId: homeId,
                    onOpenTask: { taskId in
                        Task { @MainActor in
                            push(.maintenanceDetail(homeId: homeId, taskId: taskId))
                        }
                    },
                    onAddTask: {
                        Task { @MainActor in push(.logMaintenance(homeId: homeId)) }
                    }
                )
            )
        case let .logMaintenance(homeId):
            LogMaintenanceFormView(
                viewModel: LogMaintenanceFormViewModel(homeId: homeId),
                onClose: { if !path.isEmpty { path.removeLast() } },
                onSubmitted: { taskId in
                    Task { @MainActor in
                        path.removeAll { route in
                            if case .logMaintenance = route { return true }
                            return false
                        }
                        path.append(.maintenanceDetail(homeId: homeId, taskId: taskId))
                    }
                }
            )
        case let .maintenanceDetail(homeId, taskId):
            MaintenanceDetailView(
                homeId: homeId,
                taskId: taskId,
                onBack: { if !path.isEmpty { path.removeLast() } },
                onEdit: {
                    Task { @MainActor in
                        push(.editMaintenance(homeId: homeId, taskId: taskId))
                    }
                }
            )
        case let .editMaintenance(homeId, taskId):
            LogMaintenanceFormView(
                viewModel: LogMaintenanceFormViewModel(
                    homeId: homeId,
                    mode: .edit(taskId: taskId)
                ),
                onClose: { if !path.isEmpty { path.removeLast() } },
                onSubmitted: { _ in
                    Task { @MainActor in
                        if !path.isEmpty { path.removeLast() }
                    }
                }
            )
        case let .homeBills(homeId):
            BillsListView(
                viewModel: Self.billsListViewModel(homeId: homeId, push: push)
            )
        case let .billDetail(homeId, billId):
            BillDetailView(
                homeId: homeId,
                billId: billId,
                onBack: { if !path.isEmpty { path.removeLast() } },
                onEdit: {
                    Task { @MainActor in
                        push(.addBill(homeId: homeId, billId: billId))
                    }
                }
            )
        case let .addBill(homeId, billId):
            AddBillWizardView(
                homeId: homeId,
                billId: billId,
                onClose: { if !path.isEmpty { path.removeLast() } },
                onCreated: { newBillId in
                    // Replace the wizard with the new bill's detail so
                    // Back returns to the Bills list, not the success
                    // step.
                    path.removeAll { route in
                        if case .addBill = route { return true }
                        return false
                    }
                    path.append(.billDetail(homeId: homeId, billId: newBillId))
                },
                onUpdated: {
                    // Edit mode pops back to the bill detail in place
                    // — no new detail to push since the same bill is
                    // already on the stack underneath the wizard.
                    if !path.isEmpty { path.removeLast() }
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
        case let .startPoll(homeId):
            StartPollFormView(homeId: homeId) {
                if !path.isEmpty { path.removeLast() }
            }
        case let .homePets(homeId):
            PetsListView(homeId: homeId)
        case let .homeCalendar(homeId):
            HomeCalendarView(
                viewModel: HomeCalendarViewModel(
                    homeId: homeId,
                    onAddEvent: {
                        Task { @MainActor in
                            push(.addCalendarEvent(
                                homeId: homeId,
                                eventId: nil,
                                prefilledCategory: nil
                            ))
                        }
                    },
                    onOpenEvent: { eventId in
                        Task { @MainActor in
                            push(.calendarEventDetail(homeId: homeId, eventId: eventId))
                        }
                    }
                )
            )
        case let .addCalendarEvent(homeId, eventId, prefilledCategory):
            CalendarEventFormRoute(
                homeId: homeId,
                eventId: eventId,
                prefilledCategory: prefilledCategory,
                onClose: { if !path.isEmpty { path.removeLast() } },
                onCommitted: { event in
                    switch event {
                    case let .created(newId):
                        // Replace the form with the new event's detail so
                        // Back returns to the calendar list.
                        path.removeAll { route in
                            if case .addCalendarEvent = route { return true }
                            return false
                        }
                        path.append(.calendarEventDetail(homeId: homeId, eventId: newId))
                    case let .updated(updatedId):
                        // Pop both the form AND the stale detail, then
                        // push the detail again so it re-fetches.
                        path.removeAll { route in
                            if case .addCalendarEvent = route { return true }
                            if case .calendarEventDetail = route { return true }
                            return false
                        }
                        path.append(.calendarEventDetail(homeId: homeId, eventId: updatedId))
                    }
                }
            )
        case let .calendarEventDetail(homeId, eventId):
            EventDetailView(
                homeId: homeId,
                eventId: eventId,
                onBack: { if !path.isEmpty { path.removeLast() } },
                onEdit: { event in
                    Task { @MainActor in
                        push(.addCalendarEvent(
                            homeId: homeId,
                            eventId: event.id,
                            prefilledCategory: event.eventType
                        ))
                    }
                }
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
                    }
                )
            )
        case let .addEmergencyInfo(homeId):
            AddEmergencyInfoFormView(
                viewModel: AddEmergencyInfoFormViewModel(homeId: homeId) { _ in
                    Task { @MainActor in
                        if !path.isEmpty { path.removeLast() }
                    }
                }
            )
        case let .emergencyItem(homeId, emergencyId):
            EmergencyInfoDetailView(
                homeId: homeId,
                emergencyId: emergencyId
            ) {
                Task { @MainActor in
                    if !path.isEmpty { path.removeLast() }
                }
            }
        case let .homeDocs(homeId):
            DocumentsView(
                viewModel: DocumentsViewModel(
                    homeId: homeId,
                    onOpenDocument: { dto in
                        Task { @MainActor in
                            push(.documentDetail(homeId: homeId, documentId: dto.id))
                        }
                    },
                    onUpload: {
                        Task { @MainActor in
                            push(.uploadDocument(homeId: homeId))
                        }
                    },
                    onSearch: {
                        Task { @MainActor in
                            push(.documentSearch(homeId: homeId))
                        }
                    },
                    onExport: {
                        Task { @MainActor in
                            push(.placeholder(label: "Export documents"))
                        }
                    },
                    onDocumentAction: { dto, action in
                        Task { @MainActor in
                            switch action {
                            case .view:
                                push(.documentDetail(homeId: homeId, documentId: dto.id))
                            case .share, .download, .delete:
                                push(.documentDetail(homeId: homeId, documentId: dto.id))
                            }
                        }
                    }
                )
            )
        case let .uploadDocument(homeId):
            UploadDocumentFormView(
                homeId: homeId,
                onClose: { if !path.isEmpty { path.removeLast() } },
                onUploaded: { _ in
                    Task { @MainActor in
                        path.removeAll { route in
                            if case .uploadDocument = route { return true }
                            return false
                        }
                    }
                }
            )
        case let .documentDetail(homeId, documentId):
            DocumentDetailView(
                homeId: homeId,
                documentId: documentId,
                onBack: { if !path.isEmpty { path.removeLast() } },
                onReplace: {
                    Task { @MainActor in
                        push(.uploadDocument(homeId: homeId))
                    }
                }
            )
        case let .documentSearch(homeId):
            DocumentSearchView(
                viewModel: DocumentSearchViewModel(
                    homeId: homeId,
                    onOpenDocument: { dto in
                        Task { @MainActor in
                            push(.documentDetail(homeId: homeId, documentId: dto.id))
                        }
                    },
                    onCancel: {
                        Task { @MainActor in
                            if !path.isEmpty { path.removeLast() }
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
                        Task { @MainActor in push(.addHouseholdTask(homeId: homeId)) }
                    },
                    onEditRecurring: { taskId in
                        Task { @MainActor in
                            push(.editHouseholdTask(homeId: homeId, taskId: taskId))
                        }
                    }
                )
            )
        case let .addHouseholdTask(homeId):
            AddHouseholdTaskFormView(
                homeId: homeId,
                onClose: { if !path.isEmpty { path.removeLast() } },
                onCreated: { _ in
                    // Pop back to the tasks list; the list refreshes
                    // on `.refreshable` / next visit.
                    if !path.isEmpty { path.removeLast() }
                }
            )
        case let .editHouseholdTask(homeId, taskId):
            AddHouseholdTaskFormView(
                homeId: homeId,
                taskId: taskId
            ) {
                if !path.isEmpty { path.removeLast() }
            }
        case let .homeMembers(homeId):
            MembersListView(homeId: homeId) {
                modalRoute = HubModalRoute(route: .addGuest(homeId: homeId))
            }
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
                onOpenMessages: { profile in
                    Task { @MainActor in
                        push(.chatConversation(InboxConversationDestination(
                            mode: .person(otherUserId: profile.id),
                            displayName: profile.displayName,
                            initials: Self.initials(from: profile.displayName),
                            identityKind: nil,
                            verified: profile.verified ?? false
                        )))
                    }
                }
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
                currentUserId: currentUserId.isEmpty ? nil : currentUserId,
                onBack: {
                    if !path.isEmpty { path.removeLast() }
                },
                onOpenProfile: { userId in
                    Task { @MainActor in push(.publicProfile(userId: userId)) }
                },
                onEdit: { id in
                    Task { @MainActor in push(.editPost(postId: id)) }
                }
            )
        case .mailboxVault:
            // T6.5e (P19.5) — Mailbox Vault list. Personal-pillar
            // surface — both the FAB ("Save mail to vault") and the
            // empty-state CTA ("Open Mailbox") navigate to the inbox
            // list, popping to an existing instance if one is already
            // on the stack so we don't pile mailbox screens on top of
            // each other.
            let goToMailbox: @MainActor () -> Void = {
                if path.contains(.mailboxRoot) {
                    while let last = path.last, last != .mailboxRoot {
                        path.removeLast()
                    }
                } else {
                    push(.mailboxRoot)
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
            PulseComposeView(intent: PulseComposeIntent.from(rawValue: intent)) { _ in
                pop()
            }
        case let .editPost(postId):
            PulseComposeView(postId: postId) { _ in
                pop()
            }
        case .gigsFeed:
            GigsFeedView(
                onOpenGig: { gigId in
                    Task { @MainActor in push(.gigDetail(gigId: gigId)) }
                },
                onCompose: { category in
                    Task { @MainActor in push(.composeGig(category: category.rawValue)) }
                },
                onOpenMap: { category in
                    Task { @MainActor in push(.tasksMap(categoryKey: category.rawValue)) }
                },
                onOpenSearch: { Task { @MainActor in push(.gigSearch) } },
                onBack: pop
            )
        case .gigSearch:
            GigSearchView(
                onOpenGig: { gigId in
                    Task { @MainActor in push(.gigDetail(gigId: gigId)) }
                },
                onBack: pop
            )
        case let .gigDetail(gigId):
            GigDetailView(
                viewModel: GigDetailViewModel(gigId: gigId),
                onBack: { if !path.isEmpty { path.removeLast() } },
                onMessage: { gig in
                    Task { @MainActor in
                        guard let posterId = gig.userId else { return }
                        let name = gig.creator?.name ?? gig.creator?.username ?? gig.title
                        push(.chatConversation(InboxConversationDestination(
                            mode: .person(otherUserId: posterId),
                            displayName: name,
                            initials: Self.initials(from: name),
                            identityKind: nil,
                            verified: gig.creator?.verified ?? false
                        )))
                    }
                }
            )
        case let .composeGig(category):
            GigComposeWizardView(preselectedCategoryKey: category) { gigId in
                // Replace the wizard with the gig's detail so Back goes
                // to the Gigs feed, not the success screen.
                path.removeAll { route in
                    if case .composeGig = route { return true }
                    return false
                }
                path.append(.gigDetail(gigId: gigId))
            }
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
                onMessage: { listing in
                    Task { @MainActor in
                        guard let sellerId = listing.userId else { return }
                        let name = listing.title ?? "Seller"
                        push(.chatConversation(InboxConversationDestination(
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
                        push(.listingOffers(listingId: dto.id, title: dto.title))
                    }
                },
                onEditListing: { dto in
                    Task { @MainActor in
                        push(.editListing(listingId: dto.id, jumpToStep: nil))
                    }
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
                    onOpenBuyer: { _ in
                        Task { @MainActor in push(.placeholder(label: "Buyer profile")) }
                    },
                    onOpenTransaction: { _ in
                        Task { @MainActor in push(.placeholder(label: "Transaction detail")) }
                    },
                    onEditPrice: {
                        Task { @MainActor in
                            push(.editListing(listingId: listingId, jumpToStep: .price))
                        }
                    }
                )
            )
        case .composeListing:
            ListingComposeWizardView(
                onOpenListingDetail: listingCreatedHandler(push: push)
            )
        case let .editListing(listingId, jumpToStep):
            ListingComposeWizardView(
                mode: .edit(listingId: listingId, jumpToStep: jumpToStep),
                onListingUpdated: handleListingUpdated
            )
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
                    onFindPeople: { showFindPeople = true }
                )
            )
        case .supportTrains:
            SupportTrainsView(
                viewModel: SupportTrainsViewModel(
                    onStartTrain: {
                        Task { @MainActor in push(.startSupportTrain) }
                    },
                    onOpenTrain: { trainId in
                        Task { @MainActor in push(.reviewSignups(supportTrainId: trainId)) }
                    },
                    onSearch: {
                        Task { @MainActor in push(.searchSupportTrains) }
                    }
                )
            )
        case .searchSupportTrains:
            SupportTrainsSearchView(
                viewModel: SupportTrainsSearchViewModel(
                    onOpenTrain: { trainId in
                        Task { @MainActor in push(.reviewSignups(supportTrainId: trainId)) }
                    },
                    onCancel: { pop() }
                )
            )
        case .startSupportTrain:
            StartSupportTrainWizardView(
                onDismiss: {
                    Task { @MainActor in
                        if !path.isEmpty { path.removeLast() }
                    }
                },
                onOpenTrain: { trainId in
                    Task { @MainActor in
                        if !path.isEmpty { path.removeLast() }
                        path.append(.reviewSignups(supportTrainId: trainId))
                    }
                }
            )
        case let .reviewSignups(supportTrainId):
            ReviewSignupsView(
                viewModel: ReviewSignupsViewModel(
                    supportTrainId: supportTrainId,
                    onShareTrain: {
                        systemSheet = .share(
                            items: ["Join my support train on Pantopus — \(InviteLinks.downloadURLString)"]
                        )
                    },
                    onConfirm: { _ in
                        // POST `/api/support-trains/:id/reservations/:reservationId/confirm`
                        // wiring lands with the editor surface; the VM's
                        // optimistic patch is the user-facing feedback today.
                    },
                    onMessage: { _ in
                        Task { @MainActor in push(.placeholder(label: "Message helper")) }
                    },
                    onEdit: { reservation in
                        Task { @MainActor in
                            push(.editSignup(reservation: reservation))
                        }
                    }
                )
            )
        case let .editSignup(reservation):
            EditSignupFormView(reservation: reservation) {
                if !path.isEmpty { path.removeLast() }
            }
        case .discoverHub:
            DiscoverHubView(
                viewModel: DiscoverHubViewModel(
                    onSelect: { target in
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
                            }
                        }
                    },
                    onOpenMap: { Task { @MainActor in push(.explore) } }
                )
            )
        case .discoverBusinesses:
            DiscoverBusinessesView(
                viewModel: DiscoverBusinessesViewModel { target in
                    Task { @MainActor in
                        switch target {
                        case let .business(businessId, _):
                            push(.businessProfile(businessId: businessId))
                        case .setHomeAddress:
                            push(.addHome)
                        case .inviteBusiness:
                            let draft = MailDraft(
                                subject: "Join Pantopus",
                                body: "I'd love to see your business on Pantopus — neighbors near me are "
                                    + "looking for trusted local pros. \(InviteLinks.downloadURLString)"
                            )
                            if MailDraft.canSendMail {
                                systemSheet = .mail(draft)
                            } else if let url = draft.mailtoURL {
                                UIApplication.shared.open(url)
                            }
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
                    }
                    // Edit-bid + Leave-review are presented as sheets from
                    // inside the screen (P3.4) — no router wiring needed.
                )
            )
        case let .chatConversation(dest):
            ChatConversationView(
                viewModel: ChatConversationViewModel(
                    mode: Self.chatMode(for: dest.mode),
                    counterparty: Self.chatCounterparty(for: dest),
                    currentUserId: currentUserId
                ),
                mode: dest.kind
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
            MailboxSearchView(
                viewModel: MailboxSearchViewModel(
                    onOpenMail: { mailId in
                        Task { @MainActor in push(.mailItemDetail(mailId: mailId)) }
                    },
                    onCancel: {
                        Task { @MainActor in
                            if !path.isEmpty { path.removeLast() }
                        }
                    }
                )
            )
        case let .placeholder(label):
            NotYetAvailableView(tabName: label, icon: .info)
        // Wave A — pre-staged placeholder destinations. When an A.x screen
        // ships, swap its single line below for the real view.
        case .todayDetail:
            TodayDetailView { pop() }
        case let .propertyDetails(homeId):
            PropertyDetailsView(
                homeId: homeId,
                onBack: { if !path.isEmpty { path.removeLast() } },
                onRequestCorrection: {
                    Task { @MainActor in push(.placeholder(label: "Request correction")) }
                }
            )
        case let .addGuest(homeId):
            // A13.1 — Add Guest form. Normally presented via
            // `fullScreenCover` from the Members screen's Guests tab; this
            // route case remains concrete for debug/deep-link parity.
            AddGuestFormView(
                viewModel: AddGuestFormViewModel(homeId: homeId)
            )
        case let .tasksMap(categoryKey):
            TasksMapView(
                viewModel: TasksMapViewModel(
                    initialCategory: GigsCategory(rawValue: categoryKey) ?? .all
                ),
                onOpenTask: { taskId in
                    Task { @MainActor in push(.gigDetail(gigId: taskId)) }
                },
                onCompose: { category in
                    Task { @MainActor in push(.composeGig(category: category.rawValue)) }
                },
                onBack: pop
            )
        case .explore:
            ExploreMapView(
                onOpenEntity: { entity in
                    Task { @MainActor in
                        switch entity.kind {
                        case .task: push(.gigDetail(gigId: entity.id))
                        case .item: push(.listingDetail(listingId: entity.id))
                        case .post: push(.pulsePost(postId: entity.id))
                        case .spot: push(.businessProfile(businessId: entity.id))
                        }
                    }
                },
                onBack: { pop() }
            )
        case .mailboxRoot:
            MailboxRootView(
                viewModel: MailboxRootViewModel(
                    onOpenMail: { mailId in
                        Task { @MainActor in push(.mailItemDetail(mailId: mailId)) }
                    },
                    onOpenSearch: { push(.mailboxSearch) },
                    onOpenMap: { push(.mailboxMap) },
                    onBrowseGigs: { push(.gigsFeed) }
                )
            )
        case .mailboxMap:
            MailboxMapView { pop() }
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
    /// `push(.pollDetail(…))` for row taps and `push(.startPoll(…))` for
    /// the FAB + empty-state CTA (P2.5 composer).
    private static func pollsListViewModel(
        homeId: String,
        push: @escaping (HubRoute) -> Void
    ) -> PollsListViewModel {
        let openPoll: @Sendable (String) -> Void = { pollId in
            Task { @MainActor in push(.pollDetail(homeId: homeId, pollId: pollId)) }
        }
        let startPoll: @Sendable () -> Void = {
            Task { @MainActor in push(.startPoll(homeId: homeId)) }
        }
        return PollsListViewModel(
            homeId: homeId,
            onOpenPoll: openPoll,
            onStartPoll: startPoll
        )
    }
}

private struct HubModalRoute: Identifiable, Equatable {
    let route: HubRoute

    var id: HubRoute {
        route
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
