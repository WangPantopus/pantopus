//
//  YouTabRoot.swift
//  Pantopus
//
//  The "You" tab — the user's identity command center. Hosts the
//  navigation stack + the sign-out confirmation + the DEBUG deep-link
//  affordances. The actual screen body is `MeView` (T1.3): one chrome
//  with three identity bindings (Personal / Home / Business).
//

import SwiftUI

/// Typed routes within the You tab's NavigationStack.
public enum YouRoute: Hashable {
    case signOutConfirm
    case mailbox
    case mailItemDetail(mailId: String)
    case settings
    case placeholder(label: String)
    #if DEBUG
    case publicProfile(userId: String)
    case pulsePost(postId: String)
    case privacyHandshake(personaHandle: String)
    case statusWaiting
    case ceremonialMail
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
            .alert("Open invite by token", isPresented: $debugInviteTokenSheet) {
                TextField("Invite token", text: $debugInviteToken)
                Button("Open") {
                    let token = debugInviteToken.trimmingCharacters(in: .whitespaces)
                    if !token.isEmpty {
                        DeepLinkRouter.shared.handle(url: URL(string: "pantopus://invite/\(token)")!)
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
    /// generic placeholder.
    private func handleAction(_ tile: MeActionTile) {
        switch tile.routeKey {
        case "me.mail":
            path.append(.mailbox)
        default:
            path.append(.placeholder(label: tile.label))
        }
    }

    private func handleSection(_ row: MeSectionRow) {
        #if DEBUG
        switch row.routeKey {
        case "me.editProfile":
            showsEditProfile = true
            return
        case "me.settings":
            path.append(.settings)
            return
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
        default:
            break
        }
        #else
        if row.routeKey == "me.editProfile" {
            showsEditProfile = true
            return
        }
        if row.routeKey == "me.settings" {
            path.append(.settings)
            return
        }
        #endif
        path.append(.placeholder(label: row.label))
    }

    /// No-op overlay slot — we previously routed debug affordances via
    /// a 5-tap gesture, but the designed DEBUG section in `MeView` now
    /// surfaces them directly.
    @ViewBuilder
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
        #if DEBUG
        case let .publicProfile(userId):
            PublicProfileView(
                userId: userId,
                onBack: { if !path.isEmpty { path.removeLast() } }
            )
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
                    personaHandle: personaHandle,
                    onDismiss: { if !path.isEmpty { path.removeLast() } }
                )
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
        #endif
        }
    }
}

#Preview {
    YouTabRoot()
        .environment(AuthManager.previewSignedIn)
}
