//
//  YouTabRoot.swift
//  Pantopus
//
//  Placeholder "You" tab with account summary and sign-out.
//
// swiftlint:disable closure_end_indentation

import SwiftUI

/// Typed routes within the You tab's NavigationStack.
public enum YouRoute: Hashable {
    case signOutConfirm
    #if DEBUG
    case publicProfile(userId: String)
    case pulsePost(postId: String)
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
    @State private var debugProfileId = ""
    @State private var debugPostId = ""
    @State private var debugInviteHomeId = ""
    @State private var debugDisambiguateMailId = ""
    @State private var debugInviteFormHomeId: String?
    @State private var debugDisambiguateFormMailId: String?
    #endif

    public init() {}

    public var body: some View {
        NavigationStack(path: $path) {
            List {
                Section("Account") {
                    if case let .signedIn(user) = auth.state {
                        LabeledContent("Email", value: user.email)
                            .accessibilityLabel("Email: \(user.email)")
                        LabeledContent("User ID", value: user.id)
                            .accessibilityLabel("User ID: \(user.id)")
                    }
                    Button {
                        showsEditProfile = true
                    } label: {
                        HStack {
                            Text("Edit profile")
                                .foregroundStyle(Theme.Color.appText)
                            Spacer()
                            Icon(.chevronRight, size: 16, color: Theme.Color.appTextSecondary)
                        }
                    }
                    .accessibilityIdentifier("youEditProfileButton")
                }
                #if DEBUG
                Section("Debug") {
                    Button {
                        debugProfileSheet = true
                    } label: {
                        debugRow(label: "Open public profile by ID")
                    }
                    .accessibilityIdentifier("youDebugOpenProfile")
                    Button {
                        debugPostSheet = true
                    } label: {
                        debugRow(label: "Open Pulse post by ID")
                    }
                    .accessibilityIdentifier("youDebugOpenPost")
                    Button {
                        debugInviteHomeSheet = true
                    } label: {
                        debugRow(label: "Invite owner to home by ID")
                    }
                    .accessibilityIdentifier("youDebugInviteOwner")
                    Button {
                        debugDisambiguateSheet = true
                    } label: {
                        debugRow(label: "Disambiguate mail by ID")
                    }
                    .accessibilityIdentifier("youDebugDisambiguate")
                }
                #endif
                Section {
                    Button(role: .destructive) {
                        showsSignOutConfirm = true
                    } label: {
                        Text("Sign out")
                    }
                    .accessibilityIdentifier("youSignOutButton")
                    .accessibilityHint("Signs you out of Pantopus")
                }
            }
            .navigationTitle("You")
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

    @ViewBuilder
    private func destination(for route: YouRoute) -> some View {
        switch route {
        case .signOutConfirm:
            EmptyView()
        #if DEBUG
        case let .publicProfile(userId):
            PublicProfileView(userId: userId) {
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
        #endif
        }
    }

    private func debugRow(label: String) -> some View {
        HStack {
            Text(label)
                .foregroundStyle(Theme.Color.appText)
            Spacer()
            Icon(.chevronRight, size: 16, color: Theme.Color.appTextSecondary)
        }
    }
}

#Preview {
    YouTabRoot()
        .environment(AuthManager.previewSignedIn)
}
