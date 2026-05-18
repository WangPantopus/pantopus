//
//  InboxTabRoot.swift
//  Pantopus
//
//  Chat list (Inbox tab). Hosts the navigation stack — the screen body
//  is `ChatListView` (T2.1), and a row tap pushes `ChatConversationView`
//  (T2.2).
//

import SwiftUI

/// Typed routes within the Inbox tab's NavigationStack.
public enum InboxRoute: Hashable {
    case conversation(InboxConversationDestination)
    case compose
    case search
}

/// Routing payload for a conversation push — captures the mode the
/// view-model needs (room id / other-user id / AI sentinel) plus
/// header presentation data derived from the chat-list row.
public struct InboxConversationDestination: Hashable, Sendable {
    public enum Mode: Hashable, Sendable {
        case room(id: String)
        case person(otherUserId: String)
        case ai
    }

    public let mode: Mode
    public let displayName: String
    public let initials: String
    public let identityKind: String?
    public let verified: Bool

    public init(mode: Mode, displayName: String, initials: String, identityKind: String?, verified: Bool) {
        self.mode = mode
        self.displayName = displayName
        self.initials = initials
        self.identityKind = identityKind
        self.verified = verified
    }
}

/// NavigationStack wrapper for the Inbox tab.
public struct InboxTabRoot: View {
    @Environment(AuthManager.self) private var auth
    @State private var path: [InboxRoute] = []

    public init() {}

    public var body: some View {
        NavigationStack(path: $path) {
            ChatListView(
                onOpenConversation: { row in
                    path.append(.conversation(destination(from: row)))
                },
                onCompose: { path.append(.compose) },
                onOpenSearch: { path.append(.search) }
            )
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: InboxRoute.self) { route in
                destination(for: route)
                    .toolbar(.hidden, for: .navigationBar)
            }
        }
    }

    private var currentUserId: String {
        if case let .signedIn(user) = auth.state { return user.id }
        return ""
    }

    private func destination(from row: ConversationRowContent) -> InboxConversationDestination {
        let mode: InboxConversationDestination.Mode = switch row.variant {
        case .aiAssistant: .ai
        case .group: .room(id: row.id)
        case .dm: .person(otherUserId: row.id)
        }
        return InboxConversationDestination(
            mode: mode,
            displayName: row.displayName,
            initials: row.initials,
            identityKind: row.identityChip.map { $0 == .business ? "business" : "home" },
            verified: row.verified
        )
    }

    @ViewBuilder
    private func destination(for route: InboxRoute) -> some View {
        switch route {
        case let .conversation(dest):
            ChatConversationView(
                viewModel: ChatConversationViewModel(
                    mode: Self.viewModelMode(for: dest.mode),
                    counterparty: Self.counterparty(for: dest),
                    currentUserId: currentUserId
                )
            ) { if !path.isEmpty { path.removeLast() } }
        case .compose:
            NewMessageView(
                viewModel: NewMessageViewModel(
                    onSelect: { destination in
                        // Swap the picker for the chat conversation —
                        // pop the picker first so back-button on the
                        // conversation returns to the chat list, not
                        // the picker.
                        if !path.isEmpty { path.removeLast() }
                        path.append(.conversation(InboxConversationDestination(
                            mode: .person(otherUserId: destination.userId),
                            displayName: destination.displayName,
                            initials: destination.initials,
                            identityKind: nil,
                            verified: destination.verified
                        )))
                    },
                    onCancel: { if !path.isEmpty { path.removeLast() } }
                )
            )
        case .search:
            NotYetAvailableView(tabName: "Chat search", icon: .search)
        }
    }

    private static func viewModelMode(for mode: InboxConversationDestination.Mode) -> ChatThreadMode {
        switch mode {
        case .ai: .ai
        case let .room(id): .room(id: id)
        case let .person(otherUserId): .person(otherUserId: otherUserId)
        }
    }

    private static func counterparty(for dest: InboxConversationDestination) -> ChatCounterparty {
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
}

#Preview {
    InboxTabRoot()
        .environment(AuthManager.previewSignedIn)
}
