//
//  ListOfRowsState.swift
//  Pantopus
//
//  Shell ViewModel contract for the List-of-Rows archetype. Concrete
//  screens conform to `ListOfRowsDataSource`; the shell view observes
//  that source and renders loading / empty / error / loaded from one
//  place.
//

import SwiftUI

/// Lifecycle state for the List-of-Rows shell.
public enum ListOfRowsState: Sendable {
    case loading
    case loaded(sections: [RowSection], hasMore: Bool)
    case empty(EmptyContent)
    case error(message: String)

    /// Empty-state configuration. Mirrors the P5 `EmptyState` props.
    public struct EmptyContent: Sendable {
        public let icon: PantopusIcon
        public let headline: String
        public let subcopy: String
        public let ctaTitle: String?
        public let onCTA: (@Sendable () -> Void)?

        public init(
            icon: PantopusIcon,
            headline: String,
            subcopy: String,
            ctaTitle: String? = nil,
            onCTA: (@Sendable () -> Void)? = nil
        ) {
            self.icon = icon
            self.headline = headline
            self.subcopy = subcopy
            self.ctaTitle = ctaTitle
            self.onCTA = onCTA
        }
    }
}

/// Tab strip entry rendered above the list body.
public struct ListOfRowsTab: Identifiable, Hashable, Sendable {
    public let id: String
    public let label: String
    public let count: Int?

    public init(id: String, label: String, count: Int? = nil) {
        self.id = id
        self.label = label
        self.count = count
    }
}

/// Protocol any List-of-Rows ViewModel conforms to. Marked `@MainActor` so
/// SwiftUI bindings stay on the UI thread.
@MainActor
public protocol ListOfRowsDataSource: AnyObject, Observable {
    /// Title rendered in the 44pt top bar.
    var title: String { get }
    /// Optional trailing top-bar action (icon + handler). Nil = no button.
    var topBarAction: TopBarAction? { get }
    /// Optional tab strip. Empty = no tabs.
    var tabs: [ListOfRowsTab] { get }
    /// Id of the currently-selected tab. Ignored when `tabs` is empty.
    var selectedTab: String { get set }
    /// Optional FAB. Nil = no FAB.
    var fab: FABAction? { get }
    /// Observed UI state.
    var state: ListOfRowsState { get }

    /// Triggered on first appear and on pull-to-refresh.
    func load() async
    /// Triggered when the user pulls to refresh.
    func refresh() async
    /// Triggered when the list scrolls near the bottom.
    func loadMoreIfNeeded() async
}

/// Top-bar trailing action payload.
public struct TopBarAction: Sendable {
    public let icon: PantopusIcon
    public let accessibilityLabel: String
    public let handler: @Sendable () -> Void

    public init(icon: PantopusIcon, accessibilityLabel: String, handler: @escaping @Sendable () -> Void) {
        self.icon = icon
        self.accessibilityLabel = accessibilityLabel
        self.handler = handler
    }
}

/// Floating-action-button payload. 56pt primary circle with a Pantopus icon.
public struct FABAction: Sendable {
    public let icon: PantopusIcon
    public let accessibilityLabel: String
    public let handler: @Sendable () -> Void

    public init(
        icon: PantopusIcon = .plusCircle,
        accessibilityLabel: String,
        handler: @escaping @Sendable () -> Void
    ) {
        self.icon = icon
        self.accessibilityLabel = accessibilityLabel
        self.handler = handler
    }
}
