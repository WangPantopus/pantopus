//
//  YouTabRoot.swift
//  Pantopus
//
//  The "You" tab — the user's identity command center. Hosts the
//  navigation stack + the sign-out confirmation + the DEBUG deep-link
//  affordances. The actual screen body is `MeView` (T1.3): one chrome
//  with three identity bindings (Personal / Home / Business).
//

// swiftlint:disable cyclomatic_complexity file_length function_body_length type_body_length

import SwiftUI

/// Typed routes within the You tab's NavigationStack.
public enum YouRoute: Hashable {
    case signOutConfirm
    case mailbox
    case mailItemDetail(mailId: String)
    /// P4.2 — Mailbox search. Client-side filter over the user's mailbox.
    case mailboxSearch
    case settings
    case placeholder(label: String)
    /// T5.2.4 — cross-listing Offers (incoming + outgoing).
    case offers
    /// T5.3.1 — My bids. The "me.bids" action tile pushes here.
    case myBids
    /// T5.3.2 — My tasks V2. The "me.gigs" action tile pushes here.
    case myTasks
    /// P2.2 — Post-a-Task wizard. Pushed from the My tasks FAB / empty
    /// CTA. Routes to the new gig's detail on success.
    case composeTask
    /// T5.3.3 — My posts. The "me.posts" Activity-section row pushes here.
    case myPosts
    /// P3.5 — Edit an existing Pulse post. Pushed from the per-row Edit
    /// CTA on My posts; re-uses the compose flow in edit mode.
    case editPost(postId: String)
    /// T5.2.3 — Connections. The "me.connections" Personal action tile pushes here.
    case connections
    /// T6.6c (P26.5) — Support Trains. The "me.supportTrains" Personal
    /// action tile pushes here. Personal pillar (mutual-aid surface).
    case supportTrains
    /// P2.6 — Start-a-Support-Train wizard (organizer compose flow).
    /// Pushed when the Support Trains FAB / empty-state CTA fires.
    case startSupportTrain
    /// T6.6c (P26.5) — Review signups (organizer-only) for one Support
    /// Train. Pushed from a Support Trains row tap.
    case reviewSignups(supportTrainId: String)
    /// P4.6 — Support Trains search. Pushed from the Support Trains list
    /// top-bar search action; reuses the shared `SearchListShell`.
    case searchSupportTrains
    /// P3.7 — Edit Signup form (organizer-side mutation of a helper
    /// reservation). Pushed from the Review-signups per-row Edit
    /// action with the seed DTO baked in so the form can prefill
    /// without a re-fetch.
    case editSignup(reservation: SupportTrainReservationDTO)
    /// T6.3f / P14 — My homes (avatar-first roster). The "me.homes"
    /// Activity-section row pushes here; tapping a row drills into the
    /// home dashboard via `homeDashboard(homeId:)`.
    case myHomes
    /// T6.3f / P14 — My listings (Active / Sold / Drafts tabs). The
    /// "me.listings" Personal action tile pushes here.
    case myListings
    /// T6.3f / P14 — My businesses (avatar-first roster). The
    /// "me.businesses" Activity-section row pushes here.
    case myBusinesses
    /// T6.3f / P14 — Home dashboard for a specific home, reached from
    /// the My homes row tap inside the You stack.
    case homeDashboard(homeId: String)
    /// T3.2 — Identity Center. The "me.identityCenter" Personal section row pushes here.
    case identityCenter
    /// T3.3 — Audience profile. The "me.audience" Personal section row pushes here.
    case audienceProfile
    /// P1.3 — Broadcast detail full-screen takeover, pushed when the
    /// creator taps an update card on the Audience Profile. The
    /// `card` payload seeds the hero + delivered/read counters so the
    /// detail can render without a second fetch, and `tierSegments`
    /// carries the persona's tier ladder so the read-share bar paints
    /// per-tier widths immediately.
    case broadcastDetail(broadcastId: String, card: UpdateCardContent, tierSegments: [TierBreakdownContent.TierSegment])
    /// P1.2 — Creator Inbox (standalone DM thread list for creators).
    /// The "me.creatorInbox" Personal section row pushes here, and the
    /// Audience Profile Threads tab "View all messages" CTA also lands
    /// here.
    case creatorInbox
    /// P1.2 — Conversation push from a Creator Inbox row tap. Reuses
    /// the existing `ChatConversationView` shell in `.person` mode.
    case creatorInboxConversation(CreatorInboxConversationDestination)
    /// T5.2.2 — Bills. The home-context "me.bills" action tile + Activity
    /// row push here with the primary home id resolved by the VM.
    case homeBills(homeId: String)
    /// T5.2.1 — Pets. The home-context "me.pets" action tile pushes here.
    case homePets(homeId: String)
    /// T6.4c (P18) — Home calendar. The home-context "me.calendar"
    /// action tile + Home Dashboard "calendar" quick-action push here
    /// with the primary home id resolved by the VM.
    case homeCalendar(homeId: String)
    /// P2.7 — Add / edit calendar event. `eventId` non-nil = edit.
    case addCalendarEvent(homeId: String, eventId: String?, prefilledCategory: String?)
    /// P2.7 — Calendar event detail with Edit + Delete actions.
    case calendarEventDetail(homeId: String, eventId: String)
    /// T6.4b — Emergency info. The home-context "me.emergency" Activity
    /// row pushes here with the primary home id resolved by the VM.
    case homeEmergency(homeId: String)
    /// P2.8 — Add Emergency Info form.
    case addEmergencyInfo(homeId: String)
    /// P2.8 — Emergency item detail.
    case emergencyItem(homeId: String, emergencyId: String)
    /// T6.4b — Documents. The home-context "me.docs" action tile pushes
    /// here with the primary home id resolved by the VM.
    case homeDocs(homeId: String)
    /// P2.10 — Upload document form for a home.
    case uploadDocument(homeId: String)
    /// P2.10 — Document detail (preview + metadata + footer actions).
    case documentDetail(homeId: String, documentId: String)
    /// P4.5 — Document Search surface (search across title / tags /
    /// category) for a home's vault.
    case documentSearch(homeId: String)
    /// T6.3d — Packages. The home-context "me.packages" Activity row +
    /// the Home Dashboard "view_packages" quick action push here.
    case homePackages(homeId: String)
    /// T6.3d — Package detail. Pushed from a row tap on the Packages list.
    case packageDetail(homeId: String, packageId: String)
    /// T6.3d — Log a package sheet target. Presented modally from the
    /// Packages list FAB and the empty-state CTA.
    case logPackage(homeId: String)
    /// T6.3e — Polls. The home-context "me.polls" action tile pushes here.
    case homePolls(homeId: String)
    /// T6.3e — Poll detail. Pushed from a Polls list row.
    case pollDetail(homeId: String, pollId: String)
    /// P2.5 — Start-a-poll composer. Pushed from the Polls list FAB +
    /// empty-state CTA.
    case startPoll(homeId: String)
    /// T6.4a — Access codes. Per-home roster of Wi-Fi / Alarm / Gate /
    /// Lockbox / Garage / Smart lock codes. The "me.access" Household-
    /// section row pushes here with the primary home id resolved by
    /// the VM; the Home Dashboard quick-action shares the same screen.
    /// `homeName` is an optional pre-resolved subtitle ("412 Birch Ln")
    /// rendered under the title while the underlying home payload is
    /// in flight or unavailable.
    case accessCodes(homeId: String, homeName: String?)
    /// P3.1 — Add (no secretId) / Edit (with secretId) access code.
    /// `category` is set when the user lands here from the empty-state
    /// quick-start chips so the form pre-selects the matching tile.
    case editAccessCode(homeId: String, secretId: String?, categoryRaw: String?)
    /// P4.6 — Access codes search. Pushed from the Access codes list
    /// top-bar search action; `homeId` scopes the corpus to one home.
    case searchAccessCodes(homeId: String)
    /// T6.3c / P11 — Household tasks (per-home chore list). The
    /// "me.tasks" Activity-section row pushes here with the primary
    /// home id resolved by the Me VM. Distinct from `.myTasks` which is
    /// the posted-to-neighbours gig list.
    case homeTasks(homeId: String)
    /// P2.4 — Add a new household task. Reached from the household
    /// tasks list FAB.
    case addHouseholdTask(homeId: String)
    /// P2.4 / P3.6 — Edit an existing household task. Reached from the
    /// "Edit recurring" overflow action on a Recurring row. Re-uses the
    /// `AddHouseholdTaskFormView` shell in Edit mode.
    case editHouseholdTask(homeId: String, taskId: String)
    /// T6.3b / P10 — Maintenance. The home-context "me.maintenance"
    /// action tile pushes here.
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
    /// P15 / T6.3g — Owners (legal-title roster). The "me.owners"
    /// Household-section row pushes here with the primary home id
    /// resolved by `MeViewModel.homeSections(...)`.
    case homeOwners(homeId: String)
    /// T6.3a / P9 — Members. The home-context "me.members" action tile +
    /// "Household" section row both push here with the resolved home id.
    case homeMembers(homeId: String)
    /// T5.3.4 — per-listing offers panel. Pushed from a listing detail
    /// "View offers" affordance (visible when the current user owns the
    /// listing). The optional `title` is a hint rendered as the
    /// subtitle while the listing payload is in flight.
    case listingOffers(listingId: String, title: String?)
    /// Gig detail destination for an offer-row tap. Reuses the existing
    /// Transactional Detail shell.
    case gigDetail(gigId: String)
    /// Listing detail destination reached from the listing-offers buyer
    /// row tap so the seller can drill back into the canonical view.
    case listingDetail(listingId: String)
    /// Push the chat conversation for a given counterparty. Payload
    /// mirrors the Inbox tab's `InboxConversationDestination` so the same
    /// `ChatConversationView` can host the thread inside the You stack.
    case chatConversation(InboxConversationDestination)
    /// P3.3 — Edit an existing listing. Reached from the listing-detail
    /// overflow ("Edit listing") for the owner, or from the listing-
    /// offers panel's "Edit price" affordance.
    case editListing(listingId: String, jumpToStep: ListingComposeStep?)
    #if DEBUG
    case publicProfile(userId: String)
    /// P1.6 — Typed Business Profile screen. Reached today only via
    /// the debug stack on the You tab so engineers can verify the
    /// VM/view wiring without first navigating through DiscoverHub.
    /// External entry points live on `HubRoute`.
    case businessProfile(businessId: String)
    case pulsePost(postId: String)
    case privacyHandshake(personaHandle: String)
    case statusWaiting
    case ceremonialMail
    case ceremonialMailOpen(mailId: String)
    #endif
}

#if DEBUG
private struct DebugInviteHomeItem: Identifiable, Hashable {
    let id: String
}

private struct DebugDisambiguateItem: Identifiable, Hashable {
    let id: String
}
#endif

/// NavigationStack wrapper for the You tab.
public struct YouTabRoot: View {
    @Environment(AuthManager.self) private var auth
    @State private var path: [YouRoute] = []
    @State private var showsSignOutConfirm = false
    @State private var showsEditProfile = false
    #if DEBUG
    @State private var debugProfileSheet = false
    @State private var debugPostSheet = false
    @State private var debugInviteHomeSheet = false
    @State private var debugDisambiguateSheet = false
    @State private var debugHandshakeSheet = false
    @State private var debugInviteTokenSheet = false
    @State private var debugProfileId = ""
    @State private var debugPostId = ""
    @State private var debugInviteHomeId = ""
    @State private var debugDisambiguateMailId = ""
    @State private var debugHandshakeHandle = ""
    @State private var debugInviteToken = ""
    @State private var debugCeremonialMailOpenSheet = false
    @State private var debugCeremonialMailOpenId = ""
    @State private var debugInviteFormHomeId: String?
    @State private var debugDisambiguateFormMailId: String?
    #endif

    private var currentUserId: String? {
        if case let .signedIn(user) = auth.state { return user.id }
        return nil
    }

    public init() {}

    public var body: some View {
        NavigationStack(path: $path) {
            MeView(
                onAction: { tile in handleAction(tile) },
                onSection: { row in handleSection(row) },
                onLogOut: { showsSignOutConfirm = true }
            )
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: YouRoute.self) { route in
                destination(for: route)
            }
            .confirmationDialog(
                "Sign out of Pantopus?",
                isPresented: $showsSignOutConfirm,
                titleVisibility: .visible
            ) {
                Button("Sign out", role: .destructive) {
                    Task { await auth.signOut() }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("You'll need to sign in again to access your hub.")
            }
            .sheet(isPresented: $showsEditProfile) {
                EditProfileView()
            }
            .overlay(alignment: .topLeading) { debugTapTarget }
            #if DEBUG
                .alert("Open profile", isPresented: $debugProfileSheet) {
                    TextField("User ID", text: $debugProfileId)
                    Button("Open") {
                        let id = debugProfileId.trimmingCharacters(in: .whitespaces)
                        if !id.isEmpty {
                            path.append(.publicProfile(userId: id))
                            debugProfileId = ""
                        }
                    }
                    Button("Cancel", role: .cancel) {}
                } message: {
                    Text("Paste a Pantopus user UUID")
                }
                .alert("Open post", isPresented: $debugPostSheet) {
                    TextField("Post ID", text: $debugPostId)
                    Button("Open") {
                        let id = debugPostId.trimmingCharacters(in: .whitespaces)
                        if !id.isEmpty {
                            path.append(.pulsePost(postId: id))
                            debugPostId = ""
                        }
                    }
                    Button("Cancel", role: .cancel) {}
                } message: {
                    Text("Paste a Pulse post UUID")
                }
                .alert("Invite owner", isPresented: $debugInviteHomeSheet) {
                    TextField("Home ID", text: $debugInviteHomeId)
                    Button("Open") {
                        let id = debugInviteHomeId.trimmingCharacters(in: .whitespaces)
                        if !id.isEmpty {
                            debugInviteFormHomeId = id
                            debugInviteHomeId = ""
                        }
                    }
                    Button("Cancel", role: .cancel) {}
                } message: {
                    Text("Paste a home UUID")
                }
                .alert("Disambiguate mail", isPresented: $debugDisambiguateSheet) {
                    TextField("Mail ID", text: $debugDisambiguateMailId)
                    Button("Open") {
                        let id = debugDisambiguateMailId.trimmingCharacters(in: .whitespaces)
                        if !id.isEmpty {
                            debugDisambiguateFormMailId = id
                            debugDisambiguateMailId = ""
                        }
                    }
                    Button("Cancel", role: .cancel) {}
                } message: {
                    Text("Paste a Mail UUID to route")
                }
                .alert("Open Privacy Handshake", isPresented: $debugHandshakeSheet) {
                    TextField("Persona handle", text: $debugHandshakeHandle)
                    Button("Open") {
                        let handle = debugHandshakeHandle.trimmingCharacters(in: .whitespaces)
                        if !handle.isEmpty {
                            path.append(.privacyHandshake(personaHandle: handle))
                            debugHandshakeHandle = ""
                        }
                    }
                    Button("Cancel", role: .cancel) {}
                } message: {
                    Text("Type a persona handle to open the handshake")
                }
                .alert("Open Ceremonial Mail", isPresented: $debugCeremonialMailOpenSheet) {
                    TextField("Mail ID", text: $debugCeremonialMailOpenId)
                    Button("Open") {
                        let id = debugCeremonialMailOpenId.trimmingCharacters(in: .whitespaces)
                        if !id.isEmpty {
                            path.append(.ceremonialMailOpen(mailId: id))
                            debugCeremonialMailOpenId = ""
                        }
                    }
                    Button("Cancel", role: .cancel) {}
                } message: {
                    Text("Paste a Mail UUID to open the ceremonial reader")
                }
                .alert("Open invite by token", isPresented: $debugInviteTokenSheet) {
                    TextField("Invite token", text: $debugInviteToken)
                    Button("Open") {
                        let token = debugInviteToken.trimmingCharacters(in: .whitespaces)
                        if !token.isEmpty, let url = URL(string: "pantopus://invite/\(token)") {
                            DeepLinkRouter.shared.handle(url: url)
                            debugInviteToken = ""
                        }
                    }
                    Button("Cancel", role: .cancel) {}
                } message: {
                    Text("Type a token to fire pantopus://invite/<token>")
                }
                .sheet(item: Binding<DebugInviteHomeItem?>(
                    get: { debugInviteFormHomeId.map { DebugInviteHomeItem(id: $0) } },
                    set: { debugInviteFormHomeId = $0?.id }
                )) { item in
                    let email: String = {
                        if case let .signedIn(user) = auth.state { return user.email }
                        return ""
                    }()
                    InviteOwnerFormView(
                        homeId: item.id,
                        currentUserEmail: email
                    ) { debugInviteFormHomeId = nil }
                }
                .sheet(item: Binding<DebugDisambiguateItem?>(
                    get: { debugDisambiguateFormMailId.map { DebugDisambiguateItem(id: $0) } },
                    set: { debugDisambiguateFormMailId = $0?.id }
                )) { item in
                    DisambiguateMailFormView(
                        mailId: item.id
                    ) { debugDisambiguateFormMailId = nil }
                }
            #endif
        }
    }

    /// Dispatch a tap on an action-grid tile to the matching route.
    /// Tiles whose dedicated screen doesn't exist yet land on the
    /// generic placeholder, labelled per the destination they will
    /// resolve to once their T6 sub-PR lands (see PR description for
    /// the full table — `me.members` → P9, `me.tasks` → P11, etc.).
    private func handleAction(_ tile: MeActionTile) {
        switch tile.routeKey {
        case "me.mail":
            path.append(.mailbox)
        case "me.bids":
            path.append(.myBids)
        case "me.gigs":
            path.append(.myTasks)
        case "me.posts":
            path.append(.myPosts)
        case "me.offers":
            path.append(.offers)
        case "me.connections":
            path.append(.connections)
        case "me.supportTrains":
            path.append(.supportTrains)
        case "me.listings":
            path.append(.myListings)
        case "me.businesses":
            path.append(.myBusinesses)
        case "me.homes":
            path.append(.myHomes)
        case "me.bills":
            if let homeId = tile.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homeBills(homeId: homeId))
            } else {
                path.append(.placeholder(label: tile.label))
            }
        case "me.pets":
            if let homeId = tile.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homePets(homeId: homeId))
            } else {
                path.append(.placeholder(label: tile.label))
            }
        case "me.calendar":
            if let homeId = tile.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homeCalendar(homeId: homeId))
            } else {
                path.append(.placeholder(label: tile.label))
            }
        case "me.docs":
            if let homeId = tile.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homeDocs(homeId: homeId))
            } else {
                path.append(.placeholder(label: tile.label))
            }
        case "me.emergency":
            if let homeId = tile.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homeEmergency(homeId: homeId))
            } else {
                path.append(.placeholder(label: tile.label))
            }
        case "me.packages":
            if let homeId = tile.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homePackages(homeId: homeId))
            } else {
                path.append(.placeholder(label: tile.label))
            }
        case "me.polls":
            if let homeId = tile.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homePolls(homeId: homeId))
            } else {
                path.append(.placeholder(label: tile.label))
            }
        case "me.tasks":
            if let homeId = tile.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homeTasks(homeId: homeId))
            } else {
                path.append(.placeholder(label: tile.label))
            }
        case "me.maintenance":
            if let homeId = tile.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homeMaintenance(homeId: homeId))
            } else {
                path.append(.placeholder(label: tile.label))
            }
        case "me.members":
            if let homeId = tile.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homeMembers(homeId: homeId))
            } else {
                path.append(.placeholder(label: tile.label))
            }
        default:
            path.append(.placeholder(label: tile.label))
        }
    }

    private func handleSection(_ row: MeSectionRow) {
        switch row.routeKey {
        case "me.posts":
            path.append(.myPosts)
            return
        case "me.bids":
            path.append(.myBids)
            return
        case "me.gigs":
            path.append(.myTasks)
            return
        case "me.offers":
            path.append(.offers)
            return
        case "me.connections":
            path.append(.connections)
        case "me.supportTrains":
            path.append(.supportTrains)
            return
        case "me.homes":
            path.append(.myHomes)
            return
        case "me.listings":
            path.append(.myListings)
            return
        case "me.businesses":
            path.append(.myBusinesses)
            return
        case "me.identityCenter":
            path.append(.identityCenter)
            return
        case "me.audience":
            path.append(.audienceProfile)
            return
        case "me.creatorInbox":
            path.append(.creatorInbox)
            return
        case "me.bills":
            if let homeId = row.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homeBills(homeId: homeId))
                return
            }
        case "me.docs":
            if let homeId = row.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homeDocs(homeId: homeId))
                return
            }
        case "me.emergency":
            if let homeId = row.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homeEmergency(homeId: homeId))
                return
            }
        case "me.packages":
            if let homeId = row.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homePackages(homeId: homeId))
                return
            }
        case "me.polls":
            if let homeId = row.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homePolls(homeId: homeId))
                return
            }
        case "me.access":
            if let homeId = row.routeArgs["homeId"], !homeId.isEmpty {
                let homeName = row.routeArgs["homeName"]
                path.append(.accessCodes(homeId: homeId, homeName: homeName))
                return
            }
        case "me.tasks":
            if let homeId = row.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homeTasks(homeId: homeId))
                return
            }
        case "me.maintenance":
            if let homeId = row.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homeMaintenance(homeId: homeId))
                return
            }
        case "me.owners":
            if let homeId = row.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homeOwners(homeId: homeId))
                return
            }
        case "me.members":
            if let homeId = row.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homeMembers(homeId: homeId))
                return
            }
        case "me.editProfile":
            showsEditProfile = true
            return
        case "me.settings":
            path.append(.settings)
            return
        default:
            break
        }
        #if DEBUG
        switch row.routeKey {
        case "me.debug.openProfile":
            debugProfileSheet = true
            return
        case "me.debug.openPost":
            debugPostSheet = true
            return
        case "me.debug.inviteOwner":
            debugInviteHomeSheet = true
            return
        case "me.debug.disambiguate":
            debugDisambiguateSheet = true
            return
        case "me.debug.openHandshake":
            debugHandshakeSheet = true
            return
        case "me.debug.openInviteToken":
            debugInviteTokenSheet = true
            return
        case "me.debug.openStatusWaiting":
            path.append(.statusWaiting)
            return
        case "me.debug.openCeremonialMail":
            path.append(.ceremonialMail)
            return
        case "me.debug.openCeremonialMailOpen":
            debugCeremonialMailOpenSheet = true
            return
        default:
            break
        }
        #endif
        path.append(.placeholder(label: row.label))
    }

    private func popAfterListingUpdate(_: String) {
        Task { @MainActor in
            if !path.isEmpty { path.removeLast() }
        }
    }

    /// No-op overlay slot — we previously routed debug affordances via
    /// a 5-tap gesture, but the designed DEBUG section in `MeView` now
    /// surfaces them directly.
    private var debugTapTarget: some View {
        EmptyView()
    }

    /// Two-letter initials derived from a display name. Falls back to
    /// `··` when the input has no alphanumeric content so the chat header's
    /// avatar still renders.
    fileprivate static func initials(from name: String) -> String {
        let parts = name.split(separator: " ").prefix(2)
        let joined = parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
        return joined.isEmpty ? "··" : joined
    }

    /// Project an `InboxConversationDestination.Mode` onto the
    /// `ChatThreadMode` consumed by `ChatConversationViewModel`.
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

    @ViewBuilder
    private func destination(for route: YouRoute) -> some View {
        switch route {
        case .signOutConfirm:
            EmptyView()
        case .mailbox:
            MailboxListView(
                viewModel: MailboxListViewModel(
                    onOpenMail: { mailId in
                        Task { @MainActor in path.append(.mailItemDetail(mailId: mailId)) }
                    },
                    onOpenSearch: { path.append(.mailboxSearch) }
                )
            )
        case .mailboxSearch:
            MailboxSearchView(
                viewModel: MailboxSearchViewModel(
                    onOpenMail: { mailId in
                        Task { @MainActor in path.append(.mailItemDetail(mailId: mailId)) }
                    },
                    onCancel: {
                        Task { @MainActor in
                            if !path.isEmpty { path.removeLast() }
                        }
                    }
                )
            )
        case let .mailItemDetail(mailId):
            // T6.5b (P20) — Generic A17.1 mail detail. P21–P23 will
            // extend this with package / coupon / booklet / certified
            // variants that compose the same shell with their own slots.
            MailDetailView(
                mailId: mailId,
                onBack: { if !path.isEmpty { path.removeLast() } },
                onOpenSenderProfile: { _ in
                    // Public-profile routing from You's mailbox is
                    // deferred until the You tab gets its own user-
                    // detail destination.
                }
            )
        case .settings:
            SettingsView(
                onClose: { if !path.isEmpty { path.removeLast() } },
                onEditProfile: { showsEditProfile = true },
                onSignedOut: { if !path.isEmpty { path.removeLast() } }
            )
        case let .placeholder(label):
            NotYetAvailableView(tabName: label, icon: .info)
        case .offers:
            OffersView(
                viewModel: OffersViewModel(
                    onOpenOfferDetail: { dto in
                        guard let gigId = dto.gigId ?? dto.gig?.id else { return }
                        Task { @MainActor in path.append(.gigDetail(gigId: gigId)) }
                    },
                    onBrowseListings: {
                        Task { @MainActor in path.append(.placeholder(label: "Browse listings")) }
                    },
                    onPostTask: {
                        Task { @MainActor in path.append(.placeholder(label: "Post a task")) }
                    }
                )
            )
        case let .gigDetail(gigId):
            GigDetailView(
                viewModel: GigDetailViewModel(gigId: gigId),
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
        case let .listingDetail(listingId):
            ListingDetailView(
                viewModel: ListingDetailViewModel(listingId: listingId),
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
        case let .listingOffers(listingId, titleHint):
            ListingOffersView(
                viewModel: ListingOffersViewModel(
                    listingId: listingId,
                    listingTitleHint: titleHint,
                    onShareListing: {
                        Task { @MainActor in
                            path.append(.placeholder(label: "Share listing"))
                        }
                    },
                    onOpenBuyer: { _ in
                        Task { @MainActor in
                            path.append(.placeholder(label: "Buyer profile"))
                        }
                    },
                    onOpenTransaction: { _ in
                        Task { @MainActor in
                            path.append(.placeholder(label: "Transaction detail"))
                        }
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
        case .myPosts:
            MyPostsView(
                viewModel: MyPostsViewModel(
                    onOpenPost: { _ in
                        Task { @MainActor in path.append(.placeholder(label: "Post detail")) }
                    },
                    onCompose: {
                        Task { @MainActor in path.append(.placeholder(label: "Write a post")) }
                    },
                    onEditPost: { dto in
                        Task { @MainActor in path.append(.editPost(postId: dto.id)) }
                    }
                )
            )
        case let .editPost(postId):
            PulseComposeView(postId: postId) { _ in
                if !path.isEmpty { path.removeLast() }
            }
        case .myBids:
            MyBidsView(
                viewModel: MyBidsViewModel(
                    onOpenBid: { dto in
                        Task { @MainActor in
                            if let gigId = dto.gigId {
                                path.append(.gigDetail(gigId: gigId))
                            }
                        }
                    },
                    onBrowseTasks: {
                        Task { @MainActor in path.append(.placeholder(label: "Browse tasks")) }
                    },
                    onMessageClient: { dto in
                        Task { @MainActor in
                            guard let posterId = dto.gig?.userId else { return }
                            let name = dto.gig?.title ?? "Conversation"
                            path.append(.chatConversation(InboxConversationDestination(
                                mode: .person(otherUserId: posterId),
                                displayName: name,
                                initials: Self.initials(from: name),
                                identityKind: nil,
                                verified: false
                            )))
                        }
                    }
                    // Edit-bid + Leave-review are presented as sheets from
                    // inside the screen (P3.4) — no router wiring needed.
                )
            )
        case .myTasks:
            MyTasksView(
                viewModel: MyTasksViewModel(
                    onOpenTask: { dto in
                        Task { @MainActor in path.append(.gigDetail(gigId: dto.id)) }
                    },
                    onOpenBids: { dto in
                        // Gig detail's "Manage bids" sheet renders the
                        // full bid list — the dedicated bids surface
                        // lands with T2.3.
                        Task { @MainActor in path.append(.gigDetail(gigId: dto.id)) }
                    },
                    onEditTask: { dto in
                        Task { @MainActor in path.append(.gigDetail(gigId: dto.id)) }
                    },
                    onMessageWorker: { dto in
                        Task { @MainActor in path.append(.gigDetail(gigId: dto.id)) }
                    },
                    onLeaveReview: { dto in
                        Task { @MainActor in path.append(.gigDetail(gigId: dto.id)) }
                    },
                    onPostTask: {
                        Task { @MainActor in path.append(.composeTask) }
                    },
                    onRepost: { _ in
                        Task { @MainActor in path.append(.composeTask) }
                    }
                )
            )
        case .composeTask:
            GigComposeWizardView(preselectedCategoryKey: nil) { gigId in
                // Replace the wizard with the gig's detail so Back goes
                // back to My tasks, not the success screen.
                path.removeAll { $0 == .composeTask }
                path.append(.gigDetail(gigId: gigId))
            }
        case .connections:
            ConnectionsView(
                viewModel: ConnectionsViewModel(
                    onMessage: { target in
                        Task { @MainActor in
                            path.append(.chatConversation(InboxConversationDestination(
                                mode: .person(otherUserId: target.userId),
                                displayName: target.displayName,
                                initials: target.initials,
                                identityKind: nil,
                                verified: target.verified
                            )))
                        }
                    },
                    onFindPeople: {
                        Task { @MainActor in path.append(.placeholder(label: "Find people")) }
                    }
                )
            )
        case .supportTrains:
            SupportTrainsView(
                viewModel: SupportTrainsViewModel(
                    onStartTrain: {
                        Task { @MainActor in path.append(.startSupportTrain) }
                    },
                    onOpenTrain: { trainId in
                        Task { @MainActor in path.append(.reviewSignups(supportTrainId: trainId)) }
                    },
                    onSearch: {
                        Task { @MainActor in path.append(.searchSupportTrains) }
                    }
                )
            )
        case .searchSupportTrains:
            SupportTrainsSearchView(
                viewModel: SupportTrainsSearchViewModel(
                    onOpenTrain: { trainId in
                        Task { @MainActor in path.append(.reviewSignups(supportTrainId: trainId)) }
                    },
                    onCancel: { if !path.isEmpty { path.removeLast() } }
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
                        Task { @MainActor in path.append(.placeholder(label: "Share train")) }
                    },
                    onConfirm: { _ in
                        // POST `/api/support-trains/:id/reservations/:id/confirm`
                        // wiring lands with the editor surface — the VM's
                        // optimistic patch is the visible feedback today.
                    },
                    onMessage: { _ in
                        Task { @MainActor in path.append(.placeholder(label: "Message helper")) }
                    },
                    onEdit: { reservation in
                        Task { @MainActor in
                            path.append(.editSignup(reservation: reservation))
                        }
                    }
                )
            )
        case let .editSignup(reservation):
            EditSignupFormView(reservation: reservation) {
                if !path.isEmpty { path.removeLast() }
            }
        case .identityCenter:
            IdentityCenterView(
                onBack: { if !path.isEmpty { path.removeLast() } },
                onOpenIdentity: { _ in
                    Task { @MainActor in path.append(.placeholder(label: "Identity")) }
                }
            )
        case .audienceProfile:
            AudienceProfileView(
                onBack: { if !path.isEmpty { path.removeLast() } },
                onOpenFollower: { _ in
                    Task { @MainActor in path.append(.placeholder(label: "Follower")) }
                },
                onOpenThread: { _ in
                    Task { @MainActor in path.append(.creatorInbox) }
                },
                onOpenBroadcast: { card, tierSegments in
                    Task { @MainActor in
                        path.append(.broadcastDetail(
                            broadcastId: card.id,
                            card: card,
                            tierSegments: tierSegments
                        ))
                    }
                },
                onOpenSetup: {
                    Task { @MainActor in path.append(.placeholder(label: "Audience setup")) }
                },
                onOpenCreatorInbox: {
                    Task { @MainActor in path.append(.creatorInbox) }
                }
            )
        case let .broadcastDetail(broadcastId, card, tierSegments):
            BroadcastDetailView(
                viewModel: BroadcastDetailViewModel(
                    broadcastId: broadcastId,
                    seed: card,
                    tierSegments: tierSegments
                ),
                onBack: { if !path.isEmpty { path.removeLast() } },
                onOverflow: {
                    Task { @MainActor in path.append(.placeholder(label: "Broadcast actions")) }
                },
                onReply: {
                    Task { @MainActor in path.append(.placeholder(label: "Reply to broadcast")) }
                },
                onBoost: {
                    Task { @MainActor in path.append(.placeholder(label: "Boost broadcast")) }
                },
                onPin: {
                    Task { @MainActor in path.append(.placeholder(label: "Pin broadcast")) }
                }
            )
        case .creatorInbox:
            CreatorInboxView(
                onBack: { if !path.isEmpty { path.removeLast() } },
                onOpenThread: { row in
                    Task { @MainActor in
                        let dest = CreatorInboxConversationDestination(
                            userId: row.counterpartyUserId ?? row.id,
                            displayName: row.displayName.isEmpty ? row.handle : row.displayName,
                            initials: row.initials,
                            verified: row.verifiedLocal
                        )
                        path.append(.creatorInboxConversation(dest))
                    }
                },
                onOpenBroadcast: {
                    Task { @MainActor in path.append(.audienceProfile) }
                },
                onOpenSettings: {
                    Task { @MainActor in path.append(.placeholder(label: "Inbox settings")) }
                }
            )
        case let .creatorInboxConversation(dest):
            ChatConversationView(
                viewModel: ChatConversationViewModel(
                    mode: .person(otherUserId: dest.userId),
                    counterparty: .person(
                        name: dest.displayName,
                        initials: dest.initials,
                        locality: nil,
                        verified: dest.verified,
                        online: false
                    ),
                    currentUserId: currentUserId ?? ""
                )
            ) { if !path.isEmpty { path.removeLast() } }
        case let .chatConversation(dest):
            ChatConversationView(
                viewModel: ChatConversationViewModel(
                    mode: Self.chatMode(for: dest.mode),
                    counterparty: Self.chatCounterparty(for: dest),
                    currentUserId: currentUserId ?? ""
                )
            ) { if !path.isEmpty { path.removeLast() } }
        case let .homeBills(homeId):
            BillsListView(
                viewModel: BillsListViewModel(
                    homeId: homeId,
                    onOpenBill: { _ in
                        Task { @MainActor in path.append(.placeholder(label: "Bill detail")) }
                    },
                    onAddBill: {
                        Task { @MainActor in path.append(.placeholder(label: "Add a bill")) }
                    }
                )
            )
        case let .homePets(homeId):
            PetsListView(homeId: homeId)
        case let .homeCalendar(homeId):
            HomeCalendarView(
                viewModel: HomeCalendarViewModel(
                    homeId: homeId,
                    onAddEvent: {
                        Task { @MainActor in
                            path.append(.addCalendarEvent(
                                homeId: homeId,
                                eventId: nil,
                                prefilledCategory: nil
                            ))
                        }
                    },
                    onOpenEvent: { eventId in
                        Task { @MainActor in
                            path.append(.calendarEventDetail(homeId: homeId, eventId: eventId))
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
                        path.append(.addCalendarEvent(
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
                            path.append(.emergencyItem(homeId: homeId, emergencyId: dto.id))
                        }
                    },
                    onAdd: {
                        Task { @MainActor in
                            path.append(.addEmergencyInfo(homeId: homeId))
                        }
                    },
                    onShare: {
                        Task { @MainActor in
                            path.append(.placeholder(label: "Share emergency info"))
                        }
                    },
                    onPrintCard: {
                        Task { @MainActor in
                            path.append(.placeholder(label: "Print emergency card"))
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
                            path.append(.documentDetail(homeId: homeId, documentId: dto.id))
                        }
                    },
                    onUpload: {
                        Task { @MainActor in
                            path.append(.uploadDocument(homeId: homeId))
                        }
                    },
                    onSearch: {
                        Task { @MainActor in
                            path.append(.documentSearch(homeId: homeId))
                        }
                    },
                    onExport: {
                        Task { @MainActor in
                            path.append(.placeholder(label: "Export documents"))
                        }
                    },
                    onDocumentAction: { dto, _ in
                        Task { @MainActor in
                            path.append(.documentDetail(homeId: homeId, documentId: dto.id))
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
                        path.append(.uploadDocument(homeId: homeId))
                    }
                }
            )
        case let .documentSearch(homeId):
            DocumentSearchView(
                viewModel: DocumentSearchViewModel(
                    homeId: homeId,
                    onOpenDocument: { dto in
                        Task { @MainActor in
                            path.append(.documentDetail(homeId: homeId, documentId: dto.id))
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
                viewModel: PackagesListViewModel(
                    homeId: homeId,
                    currentUserId: currentUserId,
                    onOpenPackage: { packageId in
                        Task { @MainActor in
                            path.append(.packageDetail(homeId: homeId, packageId: packageId))
                        }
                    },
                    onLogPackage: {
                        Task { @MainActor in path.append(.logPackage(homeId: homeId)) }
                    }
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
                        // Replace the log-package destination with the
                        // new package's detail so Back returns to the
                        // Packages list, not the form.
                        path.removeAll { route in
                            if case .logPackage = route { return true }
                            return false
                        }
                        path.append(.packageDetail(homeId: homeId, packageId: packageId))
                    }
                }
            )
        case let .homePolls(homeId):
            PollsListView(
                viewModel: PollsListViewModel(
                    homeId: homeId,
                    onOpenPoll: { pollId in
                        Task { @MainActor in path.append(.pollDetail(homeId: homeId, pollId: pollId)) }
                    },
                    onStartPoll: {
                        Task { @MainActor in path.append(.startPoll(homeId: homeId)) }
                    }
                )
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
        case let .accessCodes(homeId, homeName):
            AccessCodesView(
                viewModel: AccessCodesViewModel(
                    homeId: homeId,
                    homeName: homeName
                ) { target in
                    Task { @MainActor in
                        switch target {
                        case let .addCode(homeId: targetHomeId, category: category):
                            path.append(.editAccessCode(
                                homeId: targetHomeId,
                                secretId: nil,
                                categoryRaw: category?.rawValue
                            ))
                        case let .editCode(homeId: targetHomeId, secretId: secretId):
                            path.append(.editAccessCode(
                                homeId: targetHomeId,
                                secretId: secretId,
                                categoryRaw: nil
                            ))
                        case let .search(homeId: targetHomeId):
                            path.append(.searchAccessCodes(homeId: targetHomeId))
                        }
                    }
                }
            )
        case let .searchAccessCodes(homeId):
            AccessCodesSearchView(
                viewModel: AccessCodesSearchViewModel(
                    homeId: homeId,
                    onOpenCode: { secretId in
                        Task { @MainActor in
                            path.append(.editAccessCode(
                                homeId: homeId,
                                secretId: secretId,
                                categoryRaw: nil
                            ))
                        }
                    },
                    onCancel: { if !path.isEmpty { path.removeLast() } }
                )
            )
        case let .editAccessCode(homeId, secretId, categoryRaw):
            EditAccessCodeFormView(
                homeId: homeId,
                secretId: secretId,
                initialCategory: categoryRaw.flatMap { AccessCategory(rawValue: $0) }
            ) {
                if !path.isEmpty { path.removeLast() }
            }
        case .myHomes:
            MyHomesListView(
                viewModel: MyHomesListViewModel(
                    onOpenHome: { homeId in
                        Task { @MainActor in path.append(.homeDashboard(homeId: homeId)) }
                    },
                    onAddHome: {
                        Task { @MainActor in path.append(.placeholder(label: "Claim a home")) }
                    }
                )
            )
        case .myListings:
            MyListingsView(
                viewModel: MyListingsViewModel(
                    onOpenListing: { listingId in
                        Task { @MainActor in path.append(.listingDetail(listingId: listingId)) }
                    },
                    onCompose: {
                        Task { @MainActor in path.append(.placeholder(label: "List something")) }
                    }
                )
            )
        case .myBusinesses:
            MyBusinessesView(
                viewModel: MyBusinessesViewModel(
                    onOpenBusiness: { _ in
                        Task { @MainActor in path.append(.placeholder(label: "Business dashboard")) }
                    },
                    onRegister: {
                        Task { @MainActor in path.append(.placeholder(label: "Register a business")) }
                    }
                )
            )
        case let .homeDashboard(homeId):
            HomeDashboardView(
                homeId: homeId,
                onBack: { if !path.isEmpty { path.removeLast() } },
                onClaimOwnership: {
                    Task { @MainActor in path.append(.placeholder(label: "Claim ownership")) }
                },
                onOpenClaimsList: {
                    Task { @MainActor in path.append(.placeholder(label: "My claims")) }
                },
                onOpenBills: {
                    Task { @MainActor in path.append(.homeBills(homeId: homeId)) }
                },
                onOpenPolls: {
                    Task { @MainActor in path.append(.homePolls(homeId: homeId)) }
                },
                onOpenPlaceholder: { label in
                    Task { @MainActor in path.append(.placeholder(label: label)) }
                },
                onOpenPets: { petHomeId in
                    Task { @MainActor in path.append(.homePets(homeId: petHomeId)) }
                },
                onOpenDocs: { docsHomeId in
                    Task { @MainActor in path.append(.homeDocs(homeId: docsHomeId)) }
                },
                onOpenEmergency: { emergencyHomeId in
                    Task { @MainActor in path.append(.homeEmergency(homeId: emergencyHomeId)) }
                },
                onOpenPackages: { packagesHomeId in
                    Task { @MainActor in path.append(.homePackages(homeId: packagesHomeId)) }
                },
                onOpenTasks: { tasksHomeId in
                    Task { @MainActor in path.append(.homeTasks(homeId: tasksHomeId)) }
                },
                onOpenMaintenance: { maintenanceHomeId in
                    Task { @MainActor in path.append(.homeMaintenance(homeId: maintenanceHomeId)) }
                },
                onOpenMembers: { membersHomeId in
                    Task { @MainActor in path.append(.homeMembers(homeId: membersHomeId)) }
                }
            )
        case let .homeTasks(homeId):
            HouseholdTasksListView(
                viewModel: HouseholdTasksListViewModel(
                    homeId: homeId,
                    onOpenTask: { _ in
                        Task { @MainActor in path.append(.placeholder(label: "Task detail")) }
                    },
                    onAddTask: {
                        Task { @MainActor in path.append(.addHouseholdTask(homeId: homeId)) }
                    },
                    onEditRecurring: { taskId in
                        Task { @MainActor in
                            path.append(.editHouseholdTask(homeId: homeId, taskId: taskId))
                        }
                    }
                )
            )
        case let .addHouseholdTask(homeId):
            AddHouseholdTaskFormView(
                homeId: homeId,
                onClose: { if !path.isEmpty { path.removeLast() } },
                onCreated: { _ in
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
        case let .homeMaintenance(homeId):
            MaintenanceListView(
                viewModel: MaintenanceListViewModel(
                    homeId: homeId,
                    onOpenTask: { taskId in
                        Task { @MainActor in
                            path.append(.maintenanceDetail(homeId: homeId, taskId: taskId))
                        }
                    },
                    onAddTask: {
                        Task { @MainActor in path.append(.logMaintenance(homeId: homeId)) }
                    }
                )
            )
        case let .logMaintenance(homeId):
            LogMaintenanceFormView(
                viewModel: LogMaintenanceFormViewModel(homeId: homeId),
                onClose: { if !path.isEmpty { path.removeLast() } },
                onSubmitted: { taskId in
                    Task { @MainActor in
                        if !path.isEmpty { path.removeLast() }
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
                        path.append(.editMaintenance(homeId: homeId, taskId: taskId))
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
        case let .homeOwners(homeId):
            let currentUserId: String? = {
                if case let .signedIn(user) = auth.state { return user.id }
                return nil
            }()
            OwnersListView(
                homeId: homeId,
                currentUserId: currentUserId
            )
        case let .homeMembers(homeId):
            MembersListView(homeId: homeId)
        #if DEBUG
        case let .publicProfile(userId):
            PublicProfileView(
                userId: userId
            ) { if !path.isEmpty { path.removeLast() } }
        case let .businessProfile(businessId):
            BusinessProfileView(businessId: businessId) {
                if !path.isEmpty { path.removeLast() }
            }
        case let .pulsePost(postId):
            PulsePostDetailView(
                postId: postId,
                onBack: { if !path.isEmpty { path.removeLast() } },
                onOpenProfile: { userId in
                    Task { @MainActor in path.append(.publicProfile(userId: userId)) }
                }
            )
        case let .privacyHandshake(personaHandle):
            PrivacyHandshakeWizardView(
                viewModel: PrivacyHandshakeViewModel(
                    personaHandle: personaHandle
                ) { if !path.isEmpty { path.removeLast() } }
            )
        case .statusWaiting:
            StatusWaitingView(
                content: .claimSubmitted(homeName: "412 Elm St"),
                onPrimary: { _ in if !path.isEmpty { path.removeLast() } },
                onSecondary: { _ in if !path.isEmpty { path.removeLast() } }
            )
        case .ceremonialMail:
            CeremonialMailWizardView(
                onDismiss: { if !path.isEmpty { path.removeLast() } },
                onOpenMail: { _ in if !path.isEmpty { path.removeLast() } }
            )
        case let .ceremonialMailOpen(mailId):
            CeremonialMailOpenView(
                viewModel: CeremonialMailOpenViewModel(mailId: mailId),
                onBack: { if !path.isEmpty { path.removeLast() } },
                onWriteBack: { _ in path.append(.ceremonialMail) }
            )
        #endif
        }
    }
}

#Preview {
    YouTabRoot()
        .environment(AuthManager.previewSignedIn)
}
