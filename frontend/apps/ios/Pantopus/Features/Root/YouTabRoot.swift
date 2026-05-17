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
    case settings
    case placeholder(label: String)
    /// T5.2.4 — cross-listing Offers (incoming + outgoing).
    case offers
    /// T5.3.1 — My bids. The "me.bids" action tile pushes here.
    case myBids
    /// T5.3.2 — My tasks V2. The "me.gigs" action tile pushes here.
    case myTasks
    /// Compose-task destination from the My tasks FAB. Today renders
    /// the not-yet-available placeholder per
    /// `docs/mobile-wiring-audit.md`; replaces with the real composer
    /// when T2.3 lands the dedicated screen.
    case composeTask
    /// T5.3.3 — My posts. The "me.posts" Activity-section row pushes here.
    case myPosts
    /// T5.2.3 — Connections. The "me.connections" Personal action tile pushes here.
    case connections
    /// T3.2 — Identity Center. The "me.identityCenter" Personal section row pushes here.
    case identityCenter
    /// T3.3 — Audience profile. The "me.audience" Personal section row pushes here.
    case audienceProfile
    /// T5.2.2 — Bills. The home-context "me.bills" action tile + Activity
    /// row push here with the primary home id resolved by the VM.
    case homeBills(homeId: String)
    /// T5.2.1 — Pets. The home-context "me.pets" action tile pushes here.
    case homePets(homeId: String)
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
    #if DEBUG
    case publicProfile(userId: String)
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
            return
        case "me.identityCenter":
            path.append(.identityCenter)
            return
        case "me.audience":
            path.append(.audienceProfile)
            return
        case "me.bills":
            if let homeId = row.routeArgs["homeId"], !homeId.isEmpty {
                path.append(.homeBills(homeId: homeId))
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

    /// No-op overlay slot — we previously routed debug affordances via
    /// a 5-tap gesture, but the designed DEBUG section in `MeView` now
    /// surfaces them directly.
    private var debugTapTarget: some View {
        EmptyView()
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
                    onOpenSearch: { path.append(.placeholder(label: "Mail search")) }
                )
            )
        case let .mailItemDetail(mailId):
            MailboxItemDetailView(
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
                    onOpenFilters: {
                        Task { @MainActor in path.append(.placeholder(label: "Offer filters")) }
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
                onMessage: { _ in
                    Task { @MainActor in path.append(.placeholder(label: "Messages")) }
                }
            )
        case let .listingDetail(listingId):
            ListingDetailView(
                viewModel: ListingDetailViewModel(listingId: listingId),
                onBack: { if !path.isEmpty { path.removeLast() } },
                onViewOffers: { dto in
                    Task { @MainActor in
                        path.append(.listingOffers(listingId: dto.id, title: dto.title))
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
                            path.append(.placeholder(label: "Edit listing"))
                        }
                    },
                    onSort: {
                        Task { @MainActor in
                            path.append(.placeholder(label: "Sort offers"))
                        }
                    }
                )
            )
        case .myPosts:
            MyPostsView(
                viewModel: MyPostsViewModel(
                    onOpenPost: { _ in
                        Task { @MainActor in path.append(.placeholder(label: "Post detail")) }
                    },
                    onOpenFilters: {
                        Task { @MainActor in path.append(.placeholder(label: "Filter posts")) }
                    },
                    onCompose: {
                        Task { @MainActor in path.append(.placeholder(label: "Write a post")) }
                    },
                    onEditPost: { _ in
                        Task { @MainActor in path.append(.placeholder(label: "Edit post")) }
                    }
                )
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
                    onOpenFilters: {
                        Task { @MainActor in path.append(.placeholder(label: "Filter bids")) }
                    },
                    onBrowseTasks: {
                        Task { @MainActor in path.append(.placeholder(label: "Browse tasks")) }
                    },
                    onMessageClient: { dto in
                        // The chat conversation surface lives on HubTabRoot
                        // today; from You we push to gig detail where the
                        // "Message poster" CTA opens the same thread.
                        Task { @MainActor in
                            if let gigId = dto.gigId {
                                path.append(.gigDetail(gigId: gigId))
                            }
                        }
                    },
                    onEditBid: { dto in
                        Task { @MainActor in
                            if let gigId = dto.gigId {
                                path.append(.gigDetail(gigId: gigId))
                            }
                        }
                    },
                    onLeaveReview: { dto in
                        Task { @MainActor in
                            if let gigId = dto.gigId {
                                path.append(.gigDetail(gigId: gigId))
                            }
                        }
                    }
                )
            )
        case .myTasks:
            MyTasksView(
                viewModel: MyTasksViewModel(
                    onOpenTask: { dto in
                        Task { @MainActor in path.append(.gigDetail(gigId: dto.id)) }
                    },
                    onOpenFilters: {
                        Task { @MainActor in path.append(.placeholder(label: "Filter tasks")) }
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
            NotYetAvailableView(tabName: "Post a task", icon: .pencil)
        case .connections:
            ConnectionsView(
                viewModel: ConnectionsViewModel(
                    onMessage: { _ in
                        Task { @MainActor in path.append(.placeholder(label: "Messages")) }
                    },
                    onFindPeople: {
                        Task { @MainActor in path.append(.placeholder(label: "Find people")) }
                    }
                )
            )
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
                    Task { @MainActor in path.append(.placeholder(label: "Thread")) }
                },
                onOpenSetup: {
                    Task { @MainActor in path.append(.placeholder(label: "Audience setup")) }
                }
            )
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
        #if DEBUG
        case let .publicProfile(userId):
            PublicProfileView(
                userId: userId
            ) { if !path.isEmpty { path.removeLast() } }
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
