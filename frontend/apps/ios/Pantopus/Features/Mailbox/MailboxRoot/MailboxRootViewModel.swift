//
//  MailboxRootViewModel.swift
//  Pantopus
//
//  B.1 — Mailbox root archetype. One screen, four drawer contexts
//  (Me / Home / Biz / Earn) × three tabs (Incoming / Counter / Vault).
//  Replaces the old MailboxDrawersView (drawer list) + MailboxListView
//  (flat list) pair with a unified drawer-tabs hybrid.
//
//  Backs the screen via the List-of-Rows archetype: the drawer chip row
//  and the segmented tab bar render in the shell's `customHeader` slot,
//  and the mail list for the active (drawer, tab) flows through
//  `ListOfRowsState`. Backend has been removed from the repo, so the
//  view-model projects deterministic `MailboxRootSampleData` into the
//  render states (the four states still apply).
//

import Observation
import SwiftUI

/// The four mailbox drawers. `business` carries the "Biz" short label
/// per the design; `earn` (B.1) is new.
public enum MailboxDrawer: String, CaseIterable, Hashable, Sendable, Identifiable {
    case me
    case home
    case business
    case earn

    public var id: String {
        rawValue
    }

    public var label: String {
        switch self {
        case .me: "Me"
        case .home: "Home"
        case .business: "Biz"
        case .earn: "Earn"
        }
    }

    /// Drawer chip icon (Lucide-backed). `circle-dollar-sign` in the JSX
    /// maps to the nearest in-set glyph, `dollarSign`.
    public var icon: PantopusIcon {
        switch self {
        case .me: .user
        case .home: .home
        case .business: .briefcase
        case .earn: .dollarSign
        }
    }

    /// Fill tint when the chip is the active drawer. Me/Home/Biz use their
    /// identity-pillar colour; Earn (not an identity pillar) uses the
    /// primary tint — matching the JSX `DrawerPill` active treatment and
    /// the Earn empty-state hero.
    public var accent: Color {
        switch self {
        case .me: Theme.Color.personal
        case .home: Theme.Color.home
        case .business: Theme.Color.business
        case .earn: Theme.Color.primary600
        }
    }
}

/// The three tabs within a drawer.
public enum MailboxTab: String, CaseIterable, Hashable, Sendable, Identifiable {
    case incoming
    case counter
    case vault

    public var id: String {
        rawValue
    }

    public var label: String {
        switch self {
        case .incoming: "Incoming"
        case .counter: "Counter"
        case .vault: "Vault"
        }
    }
}

@Observable
@MainActor
public final class MailboxRootViewModel: ListOfRowsDataSource {
    public let title = "Mailbox"

    /// Active drawer. Changing it preserves the selected tab (B.1
    /// acceptance) and rebuilds the list for the new (drawer, tab) combo.
    public private(set) var selectedDrawer: MailboxDrawer {
        didSet { if oldValue != selectedDrawer { rebuild() } }
    }

    /// `ListOfRowsDataSource` tab id (holds a `MailboxTab` raw value). The
    /// shell's own tab strip is suppressed (`tabs == []`) — the segmented
    /// bar renders in the `customHeader` — but this stays the single
    /// source of truth for the active tab so drawer switches preserve it.
    public var selectedTab: String {
        didSet { if oldValue != selectedTab { rebuild() } }
    }

    /// Empty: the segmented tab bar is rendered by `MailboxRootHeader` in
    /// the shell's custom-header slot, not by the shell's tab strip.
    public let tabs: [ListOfRowsTab] = []

    public private(set) var state: ListOfRowsState = .loading

    public var topBarAction: TopBarAction? {
        TopBarAction(icon: .search, accessibilityLabel: "Search mail") { [weak self] in
            Task { @MainActor in self?.onOpenSearch() }
        }
    }

    /// Scan-line FAB (JSX `MailboxScreen` FAB). Wired to the Mailbox map
    /// so the physical-venue surface stays reachable now that the drawers
    /// root (which hosted the map top-bar action) is gone. Hidden on the
    /// empty state, mirroring the design's `mode !== 'empty'` guard.
    public var fab: FABAction? {
        if case .empty = state { return nil }
        return FABAction(
            icon: .scanLine,
            accessibilityLabel: "Find a mailbox",
            variant: .canonicalCreate
        ) { [weak self] in
            Task { @MainActor in self?.onOpenMap() }
        }
    }

    // MARK: - Header inputs

    public var drawers: [MailboxDrawer] {
        MailboxDrawer.allCases
    }

    public var mailTabs: [MailboxTab] {
        MailboxTab.allCases
    }

    public var currentTab: MailboxTab {
        MailboxTab(rawValue: selectedTab) ?? .incoming
    }

    /// Unread badge for a drawer chip — total unread across its three tabs.
    public func drawerBadge(_ drawer: MailboxDrawer) -> Int {
        MailboxTab.allCases.reduce(0) { $0 + unreadCount(drawer: drawer, tab: $1) }
    }

    /// Per-(drawer, tab) unread count for the segmented bar. Vault renders
    /// no count (saved mail isn't "unread"); a zero count hides the badge.
    public func tabBadge(_ tab: MailboxTab) -> Int? {
        guard tab != .vault else { return nil }
        let count = unreadCount(drawer: selectedDrawer, tab: tab)
        return count > 0 ? count : nil
    }

    public func unreadCount(drawer: MailboxDrawer, tab: MailboxTab) -> Int {
        dataProvider(drawer, tab).reduce(0) { running, section in
            running + section.items.filter { !$0.item.viewed }.count
        }
    }

    // MARK: - Dependencies

    private let onOpenMail: (String) -> Void
    private let onOpenSearch: @MainActor () -> Void
    private let onOpenMap: @MainActor () -> Void
    let onOpenMailDay: @MainActor () -> Void
    private let onBrowseGigs: @MainActor () -> Void
    /// A14.8 — settings-menu entry for the Vacation hold screen. The
    /// Mailbox root top bar surfaces a `…` overflow that opens a menu
    /// containing this single entry today (more settings can land later).
    private let onOpenVacationHoldHandler: @MainActor () -> Void
    private let dataProvider: (MailboxDrawer, MailboxTab) -> [MailboxSampleSection]
    /// When set, `load()` surfaces this state verbatim — lets previews and
    /// tests pin the loading / error frames.
    private let seededState: ListOfRowsState?

    public init(
        initialDrawer: MailboxDrawer = .me,
        initialTab: MailboxTab = .incoming,
        onOpenMail: @escaping (String) -> Void = { _ in },
        onOpenSearch: @escaping @MainActor () -> Void = {},
        onOpenMap: @escaping @MainActor () -> Void = {},
        onOpenMailDay: @escaping @MainActor () -> Void = {},
        onBrowseGigs: @escaping @MainActor () -> Void = {},
        onOpenVacationHold: @escaping @MainActor () -> Void = {},
        dataProvider: @escaping (MailboxDrawer, MailboxTab) -> [MailboxSampleSection]
            = MailboxRootSampleData.sections,
        seededState: ListOfRowsState? = nil
    ) {
        selectedDrawer = initialDrawer
        selectedTab = initialTab.rawValue
        self.onOpenMail = onOpenMail
        self.onOpenSearch = onOpenSearch
        self.onOpenMap = onOpenMap
        self.onOpenMailDay = onOpenMailDay
        self.onBrowseGigs = onBrowseGigs
        onOpenVacationHoldHandler = onOpenVacationHold
        self.dataProvider = dataProvider
        self.seededState = seededState
    }

    /// A14.8 — invoked from `MailboxRootView`'s overflow menu when the
    /// user taps "Vacation hold". Exposed publicly so the SwiftUI Menu
    /// in the view layer can call it directly.
    public func openVacationHold() {
        onOpenVacationHoldHandler()
    }

    // MARK: - Lifecycle

    public func load() async {
        if let seededState {
            state = seededState
            return
        }
        rebuild()
    }

    public func refresh() async {
        guard seededState == nil else { return }
        rebuild()
    }

    public func loadMoreIfNeeded() async {} // a drawer/tab is a fixed window.

    public func selectDrawer(_ drawer: MailboxDrawer) {
        selectedDrawer = drawer
    }

    public func selectTab(_ tab: MailboxTab) {
        selectedTab = tab.rawValue
    }

    // MARK: - State projection

    private func rebuild() {
        let drawer = selectedDrawer
        let tab = currentTab
        let sections = dataProvider(drawer, tab).filter { !$0.items.isEmpty }
        if sections.isEmpty {
            state = .empty(emptyContent(drawer: drawer, tab: tab))
        } else {
            state = .loaded(
                sections: sections.map { rowSection($0, drawer: drawer, tab: tab) },
                hasMore: false
            )
        }
    }

    private func rowSection(
        _ section: MailboxSampleSection,
        drawer: MailboxDrawer,
        tab: MailboxTab
    ) -> RowSection {
        RowSection(
            id: "\(drawer.rawValue).\(tab.rawValue).\(section.id)",
            header: section.header,
            rows: section.items.map { sample in
                MailboxListViewModel.makeRow(for: sample.item, trust: sample.trust) { [weak self] mailId in
                    Task { @MainActor in self?.onOpenMail(mailId) }
                }
            }
        )
    }

    private func emptyContent(
        drawer: MailboxDrawer,
        tab: MailboxTab
    ) -> ListOfRowsState.EmptyContent {
        switch (drawer, tab) {
        case (.earn, .incoming):
            ListOfRowsState.EmptyContent(
                icon: .wallet,
                headline: "No earn items yet",
                subcopy: "Complete gigs to see payouts, 1099s, and tax docs land here automatically.",
                ctaTitle: "Browse gigs"
            ) { [weak self] in Task { @MainActor in self?.onBrowseGigs() } }
        case (.earn, .counter):
            ListOfRowsState.EmptyContent(
                icon: .wallet,
                headline: "Nothing to action",
                subcopy: "Payout approvals and tax to-dos for your gigs show up here."
            )
        case (.earn, .vault):
            ListOfRowsState.EmptyContent(
                icon: .archive,
                headline: "No saved earn mail",
                subcopy: "Save payout statements and 1099s to find them fast."
            )
        default:
            ListOfRowsState.EmptyContent(
                icon: tab == .vault ? .archive : .mailbox,
                headline: "No mail in \(drawer.label) → \(tab.label) yet",
                subcopy: "When something lands here, it shows up in this view."
            )
        }
    }
}
