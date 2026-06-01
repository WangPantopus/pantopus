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
    /// B.1 — unified Mailbox root (drawer chips × tabs). Entry point for
    /// mailbox navigation from the You tab.
    case mailboxRoot
    /// A.x — Mailbox map (physical postal venues), reached from the root.
    case mailboxMap
    /// A13.16 — My Mail Day editor (mid-afternoon triage + empty hero).
    /// Pushed from the Mailbox root header CTA + the
    /// `pantopus://mailbox/mailday` deep link.
    case mailDay(variant: MailDayVariant)
    case mailItemDetail(mailId: String)
    /// P4.2 — Mailbox search. Client-side filter over the user's mailbox.
    case mailboxSearch
    /// A14.8 — Vacation hold (scheduling + active variants). Reached
    /// from the Mailbox root top-bar settings menu.
    case vacationHold
    case settings
    case placeholder(label: String)
    case helpCenter
    case privacySettings
    case legal
    case legalContent(LegalDocument)
    case addHome
    case myClaims
    case claimStatus(claimId: String)
    case claimOwnership(homeId: String)
    /// T5.2.4 — cross-listing Offers (incoming + outgoing).
    case offers
    /// T5.3.1 — My bids. The "me.bids" action tile pushes here.
    case myBids
    /// T5.3.2 — My tasks V2. The "me.gigs" action tile pushes here.
    case myTasks
    /// Browse available neighbour gigs.
    case gigsFeed
    /// Search available neighbour gigs.
    case gigSearch
    /// Map/list browse for available neighbour gigs.
    case tasksMap(categoryKey: String)
    /// P2.2 — Post-a-Task wizard. Pushed from the My tasks FAB / empty
    /// CTA. Routes to the new gig's detail on success.
    case composeTask
    /// T5.3.3 — My posts. The "me.posts" Activity-section row pushes here.
    case myPosts
    /// Compose a Pulse post from the You tab's My posts surface.
    case composePost(intent: String)
    /// P3.5 — Edit an existing Pulse post. Pushed from the per-row Edit
    /// CTA on My posts; re-uses the compose flow in edit mode.
    case editPost(postId: String)
    case pulsePost(postId: String)
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
    /// A13.13 / P4.3 — Manage train (organizer surface). Pushed from
    /// the A10.9 detail dock overflow when the viewer is the organizer
    /// and from the `pantopus://support-trains/:id/manage` deep link.
    case manageTrain(trainId: String)
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
    /// Public business profile reached from My businesses.
    case businessProfile(businessId: String)
    /// P4.2 — A13.10 Edit Business Page (owner-only). Pushed from the
    /// `BusinessProfileView` overflow when the viewer owns the business
    /// and from the `pantopus://businesses/:id/page-editor` deep link.
    case editBusinessPage(businessId: String)
    /// P6.6 — "Register a business · coming soon" waitlist surface. The
    /// full registration wizard is a future Phase 9 item.
    case businessWaitlist
    /// A12.10 — Create Business wizard. Reached from the My Businesses
    /// FAB / empty-state CTA in the You tab.
    case createBusiness
    /// T6.3f / P14 — Home dashboard for a specific home, reached from
    /// the My homes row tap inside the You stack.
    case homeDashboard(homeId: String)
    /// T3.2 — Identity Center. The "me.identityCenter" Personal section row pushes here.
    case identityCenter
    /// T3.3 — Audience profile. The "me.audience" Personal section row pushes here.
    case audienceProfile
    /// A03.2 — Beacon Updates feed (`surface=personas`), reached from the
    /// Audience Profile "Beacon Updates" entry row.
    case beaconsFeed
    case privacyHandshake(personaHandle: String)
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
    /// Bill detail (read-mostly summary with mark-paid / remove).
    case billDetail(homeId: String, billId: String)
    /// Add / edit Bill wizard. `billId == nil` creates a new bill.
    case addBill(homeId: String, billId: String? = nil)
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
    /// Marketplace browse surface reached from Offers.
    case marketplace
    /// Listing detail destination reached from the listing-offers buyer
    /// row tap so the seller can drill back into the canonical view.
    case listingDetail(listingId: String)
    /// Snap & sell composer reached from My listings / Marketplace.
    case composeListing
    /// Push the chat conversation for a given counterparty. Payload
    /// mirrors the Inbox tab's `InboxConversationDestination` so the same
    /// `ChatConversationView` can host the thread inside the You stack.
    case chatConversation(InboxConversationDestination)
    case publicProfile(userId: String)
    /// P3.3 — Edit an existing listing. Reached from the listing-detail
    /// overflow ("Edit listing") for the owner, or from the listing-
    /// offers panel's "Edit price" affordance.
    case editListing(listingId: String, jumpToStep: ListingComposeStep?)
    /// A.x — Membership detail for a persona.
    case membershipDetail(personaId: String)
    /// A.5 — Professional profile.
    case professionalProfile
    /// A.6 — Edit persona.
    case editPersona(personaId: String)
    /// A.7 — Compose broadcast from a persona.
    case composeBroadcast(personaId: String)

    // MARK: - B1.6 batch-2 routing seam

    /// A17.11 — Stamps / postage wallet. `pantopus://mailbox/stamps`.
    case stamps
    /// A17.12 — Mail-derived task detail. `pantopus://mailbox/tasks/:id`.
    case mailTask(taskId: String)
    /// A17.13 — Auto-translated mail view. `pantopus://mailbox/translation?id=`.
    case mailTranslation(mailId: String)
    /// A17.14 — Scan-first capture (unboxing) flow. `pantopus://mailbox/unboxing`.
    case unboxing(mailId: String?)
    /// A10.11 — Earn dashboard (Wallet sibling). `pantopus://mailbox/earn`.
    case earn
    /// A10.7 — Business owner view. `pantopus://businesses/:id`.
    case businessOwner(businessId: String)
    /// A18.5 — "View as" identity preview. `pantopus://identity/preview`.
    case viewAs
    /// A18.4 — Persistent "waiting for approval" room.
    /// `pantopus://homes/:id/waiting-room`.
    case waitingRoom(homeId: String)
    #if DEBUG
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
    @Environment(\.openURL) private var openURL
    @State private var path = RouteStack<YouRoute>()
    @State private var showsSignOutConfirm = false
    @State private var showsEditProfile = false
    /// P6.6 — share system sheet driven by "Share train".
    @State private var systemSheet: SystemSheetRequest?
    /// P6.6 — "Find people" → contacts picker → invite share.
    @State private var showFindPeople = false
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

    /// Current user's handle — used to open the public-profile setup
    /// (privacy handshake) for "Set up Public Profile".
    private var currentUserHandle: String {
        if case let .signedIn(user) = auth.state { return user.username }
        return ""
    }

    public init() {}

    public var body: some View {
        NavigationStack(path: navigationPathBinding) {
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
            .sheet(item: $systemSheet) { request in request.makeView() }
            .findPeopleSheet(isPresented: $showFindPeople)
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

    private var navigationPathBinding: Binding<NavigationPath> {
        Binding(
            get: { path.navigationPath },
            set: { path.replaceNavigationPath($0) }
        )
    }

    /// Dispatch a tap on an action-grid tile to the matching route.
    /// Tiles whose dedicated screen doesn't exist yet land on the
    /// generic placeholder, labelled per the destination they will
    /// resolve to once their T6 sub-PR lands (see PR description for
    /// the full table — `me.members` → P9, `me.tasks` → P11, etc.).
    private func handleAction(_ tile: MeActionTile) {
        switch tile.routeKey {
        case "me.mail":
            path.append(.mailboxRoot)
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
        case "me.help":
            path.append(.helpCenter)
            return
        case "me.legal":
            path.append(.legal)
            return
        case "me.privacy", "me.home.privacy":
            path.append(.privacySettings)
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

    @MainActor
    private func pop() {
        if !path.isEmpty { path.removeLast() }
    }

    /// Called by `ListingComposeWizardView` on success. Pops the wizard and
    /// pushes the new listing's detail so Back returns to My Listings, not
    /// the success step. Defined as a method (not a closure literal at the
    /// call site) so SwiftLint's `trailing_closure` rule doesn't try to
    /// convert the call — the trailing-closure form would bind to
    /// `onListingUpdated` (the last function-typed init param) instead of
    /// `onOpenListingDetail`.
    private func handleListingCreated(_ listingId: String) {
        Task { @MainActor in
            pop()
            path.append(.listingDetail(listingId: listingId))
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
        case .mailboxRoot:
            MailboxRootView(
                viewModel: MailboxRootViewModel(
                    onOpenMail: { mailId in
                        Task { @MainActor in path.append(.mailItemDetail(mailId: mailId)) }
                    },
                    onOpenSearch: { path.append(.mailboxSearch) },
                    onOpenMap: { path.append(.mailboxMap) },
                    onOpenMailDay: { path.append(.mailDay(variant: .populated)) },
                    onOpenEarn: { path.append(.earn) },
                    onOpenVacationHold: { path.append(.vacationHold) },
                    onOpenStamps: { path.append(.stamps) },
                    onOpenUnboxing: { path.append(.unboxing(mailId: nil)) }
                )
            )
        case .mailboxMap:
            MailboxMapView { Task { @MainActor in pop() } }
        case let .mailDay(variant):
            MailDayView(viewModel: MailDayViewModel(variant: variant)) {
                Task { @MainActor in pop() }
            }
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
        case .vacationHold:
            VacationHoldView(
                viewModel: VacationHoldViewModel {
                    Task { @MainActor in pop() }
                }
            )
        case let .mailItemDetail(mailId):
            // T6.5b (P20) — Generic A17.1 mail detail. P21–P23 will
            // extend this with package / coupon / booklet / certified
            // variants that compose the same shell with their own slots.
            MailDetailView(
                mailId: mailId,
                onBack: { Task { @MainActor in pop() } },
                onOpenSenderProfile: { userId in
                    Task { @MainActor in path.append(.publicProfile(userId: userId)) }
                },
                onTranslate: {
                    Task { @MainActor in path.append(.mailTranslation(mailId: mailId)) }
                },
                onOpenExtractedTask: { sourceMailId in
                    // A17.12 — the certified-notice "view task" affordance
                    // opens the mail-derived task keyed by its source mail.
                    Task { @MainActor in path.append(.mailTask(taskId: sourceMailId)) }
                }
            )
        case .settings:
            SettingsView(
                onClose: { Task { @MainActor in pop() } },
                onEditProfile: { showsEditProfile = true },
                onSignedOut: { Task { @MainActor in pop() } }
            )
        case let .placeholder(label):
            NotYetAvailableView(tabName: label, icon: .info)
        case .helpCenter:
            HelpCenterView { Task { @MainActor in pop() } }
        case .privacySettings:
            GroupedListView(
                dataSource: PrivacySettingsViewModel()
            ) { Task { @MainActor in pop() } }
        case .legal:
            LegalIndexView(
                onBack: { Task { @MainActor in pop() } },
                onSelect: { doc in path.append(.legalContent(doc)) }
            )
        case let .legalContent(doc):
            LegalContentView(document: doc) {
                if !path.isEmpty { path.removeLast() }
            }
        case .addHome:
            AddHomeWizardView { homeId in
                path.removeAll { $0 == .addHome }
                path.append(.homeDashboard(homeId: homeId))
            }
        case .myClaims:
            MyClaimsListView(
                viewModel: MyClaimsListViewModel(
                    onStartNewClaim: {
                        Task { @MainActor in path.append(.addHome) }
                    },
                    onOpenClaim: { claimId in
                        Task { @MainActor in path.append(.claimStatus(claimId: claimId)) }
                    }
                )
            )
        case let .claimStatus(claimId):
            StatusWaitingView(
                content: .underReview(homeName: nil),
                onAction: { card in
                    if card.id == "addEvidence", !path.isEmpty {
                        path.removeLast()
                    }
                },
                onPrimary: { _ in
                    if !path.isEmpty { path.removeLast() }
                },
                onSecondary: { _ in
                    if !claimId.isEmpty, !path.isEmpty { path.removeLast() }
                }
            )
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
        // Wave A — pre-staged placeholder destinations. When an A.x screen
        // ships, swap its single line below for the real view.
        case let .membershipDetail(personaId):
            MembershipDetailView(
                viewModel: MembershipDetailViewModel(personaId: personaId),
                onBack: { Task { @MainActor in pop() } },
                onShare: {
                    systemSheet = .share(
                        items: ["Check out this membership on Pantopus — \(InviteLinks.downloadURLString)"]
                    )
                },
                onOpenPersona: {
                    Task { @MainActor in path.append(.placeholder(label: "Creator profile")) }
                },
                onChangeTier: {
                    Task { @MainActor in path.append(.placeholder(label: "Change tier")) }
                },
                onUpdatePayment: {
                    Task { @MainActor in path.append(.placeholder(label: "Update payment")) }
                },
                onCancel: {
                    Task { @MainActor in path.append(.placeholder(label: "Membership cancelled")) }
                },
                onRequestRefund: {
                    Task { @MainActor in path.append(.placeholder(label: "Request refund")) }
                }
            )
        case .professionalProfile:
            ProfessionalProfileView { Task { @MainActor in pop() } }
        case let .editPersona(personaId):
            EditPersonaView(viewModel: EditPersonaViewModel(personaId: personaId)) {
                if !path.isEmpty { path.removeLast() }
            }
        case let .composeBroadcast(personaId):
            ComposeBroadcastView(
                viewModel: .live(personaId: personaId) {
                    if !path.isEmpty { path.removeLast() }
                }
            ) {
                if !path.isEmpty { path.removeLast() }
            }
        case .offers:
            OffersView(
                viewModel: OffersViewModel(
                    onOpenOfferDetail: { dto in
                        guard let gigId = dto.gigId ?? dto.gig?.id else { return }
                        Task { @MainActor in path.append(.gigDetail(gigId: gigId)) }
                    },
                    onBrowseListings: {
                        Task { @MainActor in path.append(.marketplace) }
                    },
                    onPostTask: {
                        Task { @MainActor in path.append(.composeTask) }
                    }
                )
            )
        case let .gigDetail(gigId):
            GigDetailView(
                viewModel: GigDetailViewModel(gigId: gigId),
                onBack: { Task { @MainActor in pop() } },
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
        case .marketplace:
            MarketplaceView(
                onOpenListing: { listingId in
                    Task { @MainActor in path.append(.listingDetail(listingId: listingId)) }
                },
                onCompose: {
                    Task { @MainActor in path.append(.composeListing) }
                },
                onBack: { Task { @MainActor in pop() } }
            )
        case let .listingDetail(listingId):
            ListingDetailView(
                viewModel: ListingDetailViewModel(listingId: listingId),
                onBack: { Task { @MainActor in pop() } },
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
                        let name = titleHint ?? "this listing"
                        systemSheet = .share(
                            items: ["Check out \(name) on Pantopus — \(InviteLinks.downloadURLString)"]
                        )
                    },
                    onOpenBuyer: { buyer in
                        Task { @MainActor in
                            path.append(.publicProfile(userId: buyer.id))
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
        case .composeListing:
            ListingComposeWizardView { listingId in
                path.removeAll { $0 == .composeListing }
                path.append(.listingDetail(listingId: listingId))
            }
        case let .editListing(listingId, jumpToStep):
            ListingComposeWizardView(
                mode: .edit(listingId: listingId, jumpToStep: jumpToStep),
                onListingUpdated: popAfterListingUpdate
            )
        case .myPosts:
            MyPostsView(
                viewModel: MyPostsViewModel(
                    onOpenPost: { dto in
                        Task { @MainActor in path.append(.pulsePost(postId: dto.id)) }
                    },
                    onCompose: {
                        Task { @MainActor in path.append(.composePost(intent: PulseComposeIntent.ask.rawValue)) }
                    },
                    onEditPost: { dto in
                        Task { @MainActor in path.append(.editPost(postId: dto.id)) }
                    }
                )
            )
        case let .composePost(intent):
            PulseComposeView(intent: PulseComposeIntent.from(rawValue: intent)) { _ in
                if !path.isEmpty { path.removeLast() }
            }
        case let .editPost(postId):
            PulseComposeView(postId: postId) { _ in
                if !path.isEmpty { path.removeLast() }
            }
        case let .pulsePost(postId):
            PulsePostDetailView(
                postId: postId,
                currentUserId: currentUserId,
                onBack: { Task { @MainActor in pop() } },
                onOpenProfile: { userId in
                    Task { @MainActor in path.append(.publicProfile(userId: userId)) }
                },
                onEdit: { id in
                    Task { @MainActor in path.append(.editPost(postId: id)) }
                }
            )
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
                        Task { @MainActor in path.append(.gigsFeed) }
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
        case .gigsFeed:
            GigsFeedView(
                onOpenGig: { gigId in
                    Task { @MainActor in path.append(.gigDetail(gigId: gigId)) }
                },
                onCompose: { _ in
                    Task { @MainActor in path.append(.composeTask) }
                },
                onOpenMap: { category in
                    Task { @MainActor in path.append(.tasksMap(categoryKey: category.rawValue)) }
                },
                onOpenSearch: {
                    Task { @MainActor in path.append(.gigSearch) }
                },
                onBack: { Task { @MainActor in pop() } }
            )
        case .gigSearch:
            GigSearchView(
                onOpenGig: { gigId in
                    Task { @MainActor in path.append(.gigDetail(gigId: gigId)) }
                },
                onBack: { Task { @MainActor in pop() } }
            )
        case let .tasksMap(categoryKey):
            NearbyMapView(
                viewModel: NearbyMapViewModel(
                    initialCategory: GigsCategory(rawValue: categoryKey) ?? .all
                ),
                onOpenEntity: { entity in
                    Task { @MainActor in
                        switch entity.kind {
                        case .gig: path.append(.gigDetail(gigId: entity.id))
                        case .listing: path.append(.listingDetail(listingId: entity.id))
                        }
                    }
                },
                onBack: { Task { @MainActor in pop() } }
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
                    onFindPeople: { showFindPeople = true }
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
                    onCancel: { Task { @MainActor in pop() } }
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
        case let .manageTrain(trainId):
            ManageTrainView(
                viewModel: ManageTrainViewModel(trainId: trainId),
                onClose: { Task { @MainActor in pop() } },
                onOpenAnalytics: { id in
                    Task { @MainActor in path.append(.placeholder(label: "Train analytics · \(id)")) }
                },
                onEditDates: { id in
                    Task { @MainActor in path.append(.placeholder(label: "Edit dates · \(id)")) }
                },
                onInviteHelpers: { id in
                    Task { @MainActor in path.append(.placeholder(label: "Invite helpers · \(id)")) }
                }
            )
        case .identityCenter:
            IdentityCenterView(
                onBack: { Task { @MainActor in pop() } },
                onOpenIdentity: { card in
                    Task { @MainActor in
                        switch card.kind {
                        case .professional:
                            // A.5 — "Edit professional profile" from the
                            // Professional identity card.
                            path.append(.professionalProfile)
                        case .local, .personal, .publicProfile:
                            path.append(.placeholder(label: "Identity"))
                        }
                    }
                }
            )
        case .audienceProfile:
            AudienceProfileView(
                onBack: { Task { @MainActor in pop() } },
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
                    Task { @MainActor in path.append(.privacyHandshake(personaHandle: currentUserHandle)) }
                },
                onOpenCreatorInbox: {
                    Task { @MainActor in path.append(.creatorInbox) }
                },
                onOpenMembership: { personaId in
                    Task { @MainActor in path.append(.membershipDetail(personaId: personaId)) }
                },
                onComposeBroadcast: { personaId in
                    Task { @MainActor in path.append(.composeBroadcast(personaId: personaId)) }
                },
                onOpenEditPersona: {
                    Task { @MainActor in path.append(.editPersona(personaId: EditPersonaSampleData.personaId)) }
                },
                onOpenBeacons: {
                    Task { @MainActor in path.append(.beaconsFeed) }
                }
            )
        case .beaconsFeed:
            BeaconsFeedView(
                onOpenPost: { _ in
                    Task { @MainActor in path.append(.placeholder(label: "Post")) }
                },
                onCompose: { _ in
                    Task { @MainActor in path.append(.placeholder(label: "Compose")) }
                },
                onDiscover: {
                    Task { @MainActor in path.append(.placeholder(label: "Discover beacons")) }
                },
                onBack: { Task { @MainActor in pop() } }
            )
        case let .broadcastDetail(broadcastId, card, tierSegments):
            BroadcastDetailView(
                viewModel: BroadcastDetailViewModel(
                    broadcastId: broadcastId,
                    seed: card,
                    tierSegments: tierSegments
                ),
                onBack: { Task { @MainActor in pop() } },
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
                onBack: { Task { @MainActor in pop() } },
                onOpenThread: { row in
                    Task { @MainActor in
                        let dest = CreatorInboxConversationDestination(
                            userId: row.counterpartyUserId ?? row.id,
                            displayName: row.displayName.isEmpty ? row.handle : row.displayName,
                            initials: row.initials,
                            verified: row.verifiedLocal,
                            tierName: row.tierName ?? "Free",
                            tierRank: row.tierRank
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
                ),
                mode: .creatorThread,
                creatorContext: .defaults(fanTierName: dest.tierName, fanTierRank: dest.tierRank),
                onOpenAudienceProfile: {
                    path.append(.audienceProfile)
                },
                onBack: { Task { @MainActor in pop() } }
            )
        case let .chatConversation(dest):
            ChatConversationView(
                viewModel: ChatConversationViewModel(
                    mode: Self.chatMode(for: dest.mode),
                    counterparty: Self.chatCounterparty(for: dest),
                    currentUserId: currentUserId ?? ""
                ),
                mode: dest.kind
            ) { Task { @MainActor in pop() } }
        case let .homeBills(homeId):
            BillsListView(
                viewModel: BillsListViewModel(
                    homeId: homeId,
                    onOpenBill: { billId in
                        Task { @MainActor in path.append(.billDetail(homeId: homeId, billId: billId)) }
                    },
                    onAddBill: {
                        Task { @MainActor in path.append(.addBill(homeId: homeId, billId: nil)) }
                    }
                )
            )
        case let .billDetail(homeId, billId):
            BillDetailView(
                homeId: homeId,
                billId: billId,
                onBack: { Task { @MainActor in pop() } },
                onEdit: {
                    Task { @MainActor in path.append(.addBill(homeId: homeId, billId: billId)) }
                }
            )
        case let .addBill(homeId, billId):
            AddBillWizardView(
                homeId: homeId,
                billId: billId,
                onClose: { Task { @MainActor in pop() } },
                onCreated: { newBillId in
                    path.removeAll { route in
                        if case .addBill = route { return true }
                        return false
                    }
                    path.append(.billDetail(homeId: homeId, billId: newBillId))
                },
                onUpdated: {
                    if !path.isEmpty { path.removeLast() }
                }
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
                onClose: { Task { @MainActor in pop() } },
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
                onBack: { Task { @MainActor in pop() } },
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
                    }
                )
            )
        case let .addEmergencyInfo(homeId):
            AddEmergencyInfoFormView(
                viewModel: AddEmergencyInfoFormViewModel(homeId: homeId) { _ in
                    Task { @MainActor in pop() }
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
                onClose: { Task { @MainActor in pop() } },
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
                onBack: { Task { @MainActor in pop() } },
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
            ) { Task { @MainActor in pop() } }
        case let .logPackage(homeId):
            LogPackageSheetView(
                homeId: homeId,
                onClose: { Task { @MainActor in pop() } },
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
            ) { Task { @MainActor in pop() } }
        case let .startPoll(homeId):
            StartPollFormView(homeId: homeId) { Task { @MainActor in pop() } }
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
                    onCancel: { Task { @MainActor in pop() } }
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
                        Task { @MainActor in path.append(.addHome) }
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
                        Task { @MainActor in path.append(.composeListing) }
                    }
                )
            )
        case .myBusinesses:
            MyBusinessesView(
                viewModel: MyBusinessesViewModel(
                    onOpenBusiness: { businessId in
                        // B3.2 — an owned business opens its owner dashboard
                        // (A10.7), not the public profile.
                        Task { @MainActor in path.append(.businessOwner(businessId: businessId)) }
                    },
                    onRegister: {
                        Task { @MainActor in path.append(.createBusiness) }
                    }
                )
            )
        case .businessWaitlist:
            BusinessWaitlistView { Task { @MainActor in pop() } }
        case .createBusiness:
            CreateBusinessWizardView(
                onClose: { Task { @MainActor in pop() } },
                onOpenBusiness: { businessId in
                    // Replace the wizard with the business profile so Back
                    // returns to My Businesses, not the success step.
                    path.removeAll { route in
                        if case .createBusiness = route { return true }
                        return false
                    }
                    path.append(.businessProfile(businessId: businessId))
                }
            )
        case let .homeDashboard(homeId):
            HomeDashboardView(
                homeId: homeId,
                onBack: { Task { @MainActor in pop() } },
                onClaimOwnership: {
                    Task { @MainActor in path.append(.claimOwnership(homeId: homeId)) }
                },
                onOpenClaimsList: {
                    Task { @MainActor in path.append(.myClaims) }
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
                onOpenAccessCodes: { accessHomeId, homeName in
                    Task { @MainActor in path.append(.accessCodes(homeId: accessHomeId, homeName: homeName)) }
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
                    onOpenTask: { taskId in
                        Task { @MainActor in
                            path.append(.editHouseholdTask(homeId: homeId, taskId: taskId))
                        }
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
                onClose: { Task { @MainActor in pop() } },
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
                onClose: { Task { @MainActor in pop() } },
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
                onBack: { Task { @MainActor in pop() } },
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
                onClose: { Task { @MainActor in pop() } },
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
        case let .publicProfile(userId):
            PublicProfileView(
                userId: userId,
                onBack: { Task { @MainActor in pop() } },
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
        case let .businessProfile(businessId):
            BusinessProfileView(
                businessId: businessId,
                onBack: { Task { @MainActor in pop() } },
                onOpenMessages: {
                    Task { @MainActor in path.append(.placeholder(label: "Messages")) }
                },
                onShare: {
                    systemSheet = .share(
                        items: ["Check out this business on Pantopus — \(InviteLinks.downloadURLString)"]
                    )
                },
                onOpenReport: {
                    Task { @MainActor in path.append(.placeholder(label: "Report business")) }
                },
                onOpenWebsite: { url in openURL(url) },
                onBook: {
                    Task { @MainActor in path.append(.placeholder(label: "Book")) }
                },
                onEdit: {
                    Task { @MainActor in path.append(.editBusinessPage(businessId: businessId)) }
                }
            )
        case let .editBusinessPage(businessId):
            EditBusinessPageView(
                businessId: businessId,
                onBack: { Task { @MainActor in pop() } },
                onPreview: {
                    // Bounce the owner to the live profile they're editing.
                    Task { @MainActor in
                        if !path.isEmpty { path.removeLast() }
                    }
                }
            )
        case let .privacyHandshake(personaHandle):
            PrivacyHandshakeWizardView(
                viewModel: PrivacyHandshakeViewModel(
                    personaHandle: personaHandle
                ) { Task { @MainActor in pop() } }
            )

        // MARK: - B1.6 batch-2 routing seam
        // Placeholder destinations. Each screen prompt (B2–B5) swaps the one
        // line below for its real view without editing the route declarations.
        case .stamps:
            StampsView(viewModel: StampsViewModel { Task { @MainActor in pop() } })
        case let .mailTask(taskId):
            // A17.12 — Mail-derived task detail. Source-mail + next-up
            // taps push the originating mail item onto this same stack.
            MailTaskView(
                viewModel: MailTaskViewModel(
                    taskId: taskId,
                    onOpenMail: { mailId in
                        Task { @MainActor in path.append(.mailItemDetail(mailId: mailId)) }
                    },
                    onBack: { Task { @MainActor in pop() } }
                )
            )
        case let .mailTranslation(mailId):
            MailTranslationView(
                mailId: mailId,
                onBack: { if !path.isEmpty { path.removeLast() } },
                onReply: { _ in Task { @MainActor in path.append(.placeholder(label: "Reply in English")) } }
            )
        case .unboxing:
            // A17.14 — the scan-capture flow seeds from `UnboxingSampleData`
            // (OCR / classification / vault upload are out of scope), so the
            // `mailId` payload is unused today; it rides the route for when a
            // real originating-mail fetch lands.
            let openDrawer: @MainActor () -> Void = {
                Task { @MainActor in path.append(.placeholder(label: "Home drawer")) }
            }
            UnboxingView(
                viewModel: UnboxingViewModel(onOpenDrawer: openDrawer)
            ) { Task { @MainActor in pop() } }
        case .earn:
            EarnView(
                onBack: { Task { @MainActor in pop() } },
                onHelp: { path.append(.placeholder(label: "Earn help")) },
                onCashOut: { path.append(.placeholder(label: "Payments")) },
                onBrowseTasks: { path.append(.gigsFeed) },
                onReferNeighbor: { path.append(.placeholder(label: "Refer a neighbor")) },
                onOfferService: { path.append(.placeholder(label: "Offer a service")) },
                onManagePayout: { path.append(.placeholder(label: "Payments")) },
                onAddBank: { path.append(.placeholder(label: "Payments")) },
                onSeeAllEarnings: { path.append(.placeholder(label: "All earnings")) },
                onOpenTaxDocs: { path.append(.placeholder(label: "Tax documents")) }
            )
        case let .businessOwner(businessId):
            BusinessOwnerView(
                businessId: businessId,
                onBack: { Task { @MainActor in pop() } },
                onEditPage: { Task { @MainActor in path.append(.editBusinessPage(businessId: businessId)) } },
                onOpenInsights: { Task { @MainActor in path.append(.placeholder(label: "Insights")) } },
                onOpenSettings: { Task { @MainActor in path.append(.placeholder(label: "Business settings")) } }
            )
        case .viewAs:
            NotYetAvailableView(tabName: "View as", icon: .eye)
        case let .waitingRoom(homeId):
            WaitingRoomView(
                viewModel: WaitingRoomViewModel(homeId: homeId, state: .active)
            ) {
                pop()
            }
        #if DEBUG
        case .statusWaiting:
            StatusWaitingView(
                content: .claimSubmitted(homeName: "412 Elm St"),
                onPrimary: { _ in if !path.isEmpty { path.removeLast() } },
                onSecondary: { _ in if !path.isEmpty { path.removeLast() } }
            )
        case .ceremonialMail:
            CeremonialMailWizardView(
                onDismiss: { Task { @MainActor in pop() } },
                onOpenMail: { _ in if !path.isEmpty { path.removeLast() } }
            )
        case let .ceremonialMailOpen(mailId):
            CeremonialMailOpenView(
                viewModel: CeremonialMailOpenViewModel(mailId: mailId),
                onBack: { Task { @MainActor in pop() } },
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
