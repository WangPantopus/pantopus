//
//  ListOfRowsState.swift
//  Pantopus
//
//  Shell ViewModel contract for the List-of-Rows archetype. Concrete
//  screens conform to `ListOfRowsDataSource`; the shell view observes
//  that source and renders loading / empty / error / loaded from one
//  place.
//
//  T5.0 — extended additively with optional `searchBar`, `chipStrip`,
//  and `banner` chrome slots (all default `nil`), plus a `FABAction`
//  variant enum that defaults to `.canonicalCreate` (the existing 56pt
//  geometry). Every previously-conforming data source compiles
//  unchanged because the new protocol requirements all have default
//  implementations returning `nil`.
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

// MARK: - T5 chrome slots

/// Optional search bar rendered between the top bar and the tab strip.
/// Used by Connections and Discover businesses.
public struct SearchBarConfig: Sendable {
    public let placeholder: String
    public let text: String
    public let onChange: @Sendable (String) -> Void
    public let onSubmit: (@Sendable () -> Void)?

    public init(
        placeholder: String,
        text: String,
        onChange: @escaping @Sendable (String) -> Void,
        onSubmit: (@Sendable () -> Void)? = nil
    ) {
        self.placeholder = placeholder
        self.text = text
        self.onChange = onChange
        self.onSubmit = onSubmit
    }
}

/// Horizontally scrollable chip-filter strip used as an alternative to
/// `ListOfRowsTab[]` for filtering. Used by Discover hub and Discover
/// businesses.
public struct ChipStripConfig: Sendable {
    public struct Chip: Sendable, Identifiable, Hashable {
        public let id: String
        public let label: String
        public let icon: PantopusIcon?

        public init(id: String, label: String, icon: PantopusIcon? = nil) {
            self.id = id
            self.label = label
            self.icon = icon
        }
    }

    public let chips: [Chip]
    public let selectedId: String
    public let onSelect: @Sendable (String) -> Void

    public init(
        chips: [Chip],
        selectedId: String,
        onSelect: @escaping @Sendable (String) -> Void
    ) {
        self.chips = chips
        self.selectedId = selectedId
        self.onSelect = onSelect
    }
}

/// Primary-tinted summary banner rendered above the first row in the
/// scroll area. Used by My bids, My tasks, Offers, Review claims.
public struct BannerConfig: Sendable {
    public let icon: PantopusIcon
    public let title: String
    public let subtitle: String?
    public let onTap: (@Sendable () -> Void)?

    public init(
        icon: PantopusIcon,
        title: String,
        subtitle: String? = nil,
        onTap: (@Sendable () -> Void)? = nil
    ) {
        self.icon = icon
        self.title = title
        self.subtitle = subtitle
        self.onTap = onTap
    }
}

// MARK: - Data source

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

/// T5 additive protocol surface — every existing conformer gets `nil`
/// from these defaults.
public extension ListOfRowsDataSource {
    /// Optional search bar above the tabs.
    var searchBar: SearchBarConfig? { nil }
    /// Optional chip-filter strip (mutually exclusive with `tabs`).
    var chipStrip: ChipStripConfig? { nil }
    /// Optional summary banner above the first row.
    var banner: BannerConfig? { nil }
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

/// Floating-action-button payload.
///
/// T5.0 adds a `Variant` enum:
///   - `.canonicalCreate` (56pt) — primary create action of the screen
///     (My tasks V2 "Post a task"). **Default** for backwards compat —
///     every existing `FABAction(icon:, accessibilityLabel:, handler:)`
///     call site renders 56pt exactly as before.
///   - `.secondaryCreate` (52pt) — non-canonical create action (My posts,
///     Connections, Bills, Pets).
///   - `.extendedNav(label:)` (48pt pill with label) — navigation FAB
///     that signals "go elsewhere", not "create" (My bids "Browse
///     tasks").
public struct FABAction: Sendable {
    public enum Variant: Sendable {
        case canonicalCreate
        case secondaryCreate
        case extendedNav(label: String)
    }

    public let icon: PantopusIcon
    public let accessibilityLabel: String
    public let variant: Variant
    public let handler: @Sendable () -> Void

    /// Full init — pick a variant explicitly.
    public init(
        icon: PantopusIcon = .plusCircle,
        accessibilityLabel: String,
        variant: Variant,
        handler: @escaping @Sendable () -> Void
    ) {
        self.icon = icon
        self.accessibilityLabel = accessibilityLabel
        self.variant = variant
        self.handler = handler
    }

    /// Backwards-compatible convenience matching the T1–T4.1 signature.
    /// Defaults to `.canonicalCreate` (56pt) — the historical geometry.
    public init(
        icon: PantopusIcon = .plusCircle,
        accessibilityLabel: String,
        handler: @escaping @Sendable () -> Void
    ) {
        self.init(
            icon: icon,
            accessibilityLabel: accessibilityLabel,
            variant: .canonicalCreate,
            handler: handler
        )
    }
}
