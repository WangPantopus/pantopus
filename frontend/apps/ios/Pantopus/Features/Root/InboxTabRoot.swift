//
//  InboxTabRoot.swift
//  Pantopus
//
//  Chat list (Inbox tab). Hosts the navigation stack — the screen body
//  is `ChatListView` (T2.1). Conversation-detail destinations land
//  with T2.2; today every row tap pushes a labelled placeholder.
//

import SwiftUI

/// Typed routes within the Inbox tab's NavigationStack.
public enum InboxRoute: Hashable {
    case conversation(id: String, name: String)
    case compose
    case search
}

/// NavigationStack wrapper for the Inbox tab.
public struct InboxTabRoot: View {
    @State private var path: [InboxRoute] = []

    public init() {}

    public var body: some View {
        NavigationStack(path: $path) {
            ChatListView(
                onOpenConversation: { row in
                    path.append(.conversation(id: row.id, name: row.displayName))
                },
                onCompose: { path.append(.compose) },
                onOpenSearch: { path.append(.search) }
            )
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: InboxRoute.self) { route in
                destination(for: route)
            }
        }
    }

    @ViewBuilder
    private func destination(for route: InboxRoute) -> some View {
        switch route {
        case let .conversation(_, name):
            NotYetAvailableView(tabName: name, icon: .inbox)
        case .compose:
            NotYetAvailableView(tabName: "New message", icon: .edit2)
        case .search:
            NotYetAvailableView(tabName: "Chat search", icon: .search)
        }
    }
}

#Preview {
    InboxTabRoot()
}
