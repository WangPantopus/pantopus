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
    /// Presentation mode for the conversation chrome. `.aiAssistant` for
    /// the Ask Pantopus thread; `.dm` for human DMs/groups.
    public let kind: ChatConversationMode
    public let displayName: String
    public let initials: String
    public let identityKind: String?
    public let verified: Bool
    /// Message to scroll to on open (set when arriving from Chat Search
    /// with a body match). `nil` opens the conversation at the latest
    /// message.
    public let scrollToMessageId: String?

    public init(
        mode: Mode,
        kind: ChatConversationMode = .dm,
        displayName: String,
        initials: String,
        identityKind: String?,
        verified: Bool,
        scrollToMessageId: String? = nil
    ) {
        self.mode = mode
        self.kind = kind
        self.displayName = displayName
        self.initials = initials
        self.identityKind = identityKind
        self.verified = verified
        self.scrollToMessageId = scrollToMessageId
    }
}

/// NavigationStack wrapper for the Inbox tab.
public struct InboxTabRoot: View {
    @Environment(AuthManager.self) private var auth
    @State private var path = RouteStack<InboxRoute>()
    /// P6.6 — "Invite to Pantopus" opens the system share sheet with the
    /// store link prefilled.
    @State private var systemSheet: SystemSheetRequest?

    public init() {}

    public var body: some View {
        NavigationStack(path: $path.navigationPath) {
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
        .onChange(of: path.navigationPath.count) { _, count in
            path.syncToNavigationPathCount(count)
        }
        .sheet(item: $systemSheet) { request in request.makeView() }
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
        let kind: ChatConversationMode = row.variant == .aiAssistant ? .aiAssistant : .dm
        return InboxConversationDestination(
            mode: mode,
            kind: kind,
            displayName: row.displayName,
            initials: row.initials,
            identityKind: row.identityChip.map { $0 == .business ? "business" : "home" },
            verified: row.verified
        )
    }

    private func destination(from result: ChatSearchResult) -> InboxConversationDestination {
        let mode: InboxConversationDestination.Mode = switch result.kind {
        case .group: .room(id: result.conversationId)
        case .dm: .person(otherUserId: result.conversationId)
        }
        return InboxConversationDestination(
            mode: mode,
            displayName: result.displayName,
            initials: result.initials,
            identityKind: result.identityChip.map { $0 == .business ? "business" : "home" },
            verified: result.verified,
            scrollToMessageId: result.matchedMessageId
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
                    currentUserId: currentUserId,
                    scrollToMessageId: dest.scrollToMessageId
                ),
                mode: dest.kind
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
                    onCancel: { if !path.isEmpty { path.removeLast() } },
                    onInvite: { systemSheet = .share(items: InviteLinks.shareItems) }
                )
            )
        case .search:
            ChatSearchView(
                viewModel: ChatSearchViewModel(
                    onOpenResult: { result in
                        Task { @MainActor in path.append(.conversation(destination(from: result))) }
                    },
                    onCancel: {
                        Task { @MainActor in if !path.isEmpty { path.removeLast() } }
                    }
                )
            )
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
