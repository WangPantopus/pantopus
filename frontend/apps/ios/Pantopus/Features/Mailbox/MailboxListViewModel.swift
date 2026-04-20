//
//  MailboxListViewModel.swift
//  Pantopus
//
//  Backs `MailboxListView`. Fetches `GET /api/mailbox`, supports
//  All / Unread / Starred tabs, cursor-style pagination, and pull-to-refresh.
//

import Foundation
import Observation

/// ViewModel for the V1 mailbox list.
@Observable
@MainActor
final class MailboxListViewModel: ListOfRowsDataSource {
    /// Tabs exposed on the list.
    public enum Tab: String, CaseIterable, Sendable {
        case all
        case unread
        case starred

        var label: String {
            switch self {
            case .all: "All"
            case .unread: "Unread"
            case .starred: "Starred"
            }
        }
    }

    public let title = "Mailbox"
    public let tabs: [ListOfRowsTab] = Tab.allCases.map {
        ListOfRowsTab(id: $0.rawValue, label: $0.label, count: nil)
    }
    public var selectedTab: String = Tab.all.rawValue {
        didSet { if oldValue != selectedTab { Task { await reloadForTab() } } }
    }
    public var topBarAction: TopBarAction? {
        TopBarAction(
            icon: .search,
            accessibilityLabel: "Search mail",
            handler: { [weak self] in Task { @MainActor in self?.toast = "Search coming soon" } }
        )
    }
    public var fab: FABAction? { nil }
    public private(set) var state: ListOfRowsState = .loading
    /// Transient toast message. The view clears it after display.
    public var toast: String?

    private let api: APIClient
    private let pageSize = 25
    private var offset = 0
    private var hasMore = false
    private var loadedItems: [MailItem] = []
    private var isLoadingPage = false
    private let onOpenMail: (String) -> Void

    init(
        api: APIClient = .shared,
        onOpenMail: @escaping (String) -> Void = { _ in }
    ) {
        self.api = api
        self.onOpenMail = onOpenMail
    }

    public func load() async {
        if case .loaded = state, !loadedItems.isEmpty { return }
        state = .loading
        offset = 0
        loadedItems = []
        await fetchPage()
    }

    public func refresh() async {
        offset = 0
        loadedItems = []
        await fetchPage()
    }

    public func loadMoreIfNeeded() async {
        guard hasMore, !isLoadingPage else { return }
        await fetchPage()
    }

    private func reloadForTab() async {
        offset = 0
        loadedItems = []
        state = .loading
        await fetchPage()
    }

    private func fetchPage() async {
        guard !isLoadingPage else { return }
        isLoadingPage = true
        defer { isLoadingPage = false }

        let tab = Tab(rawValue: selectedTab) ?? .all
        let endpoint = MailboxEndpoints.list(
            viewed: tab == .unread ? false : nil,
            archived: false,
            starred: tab == .starred ? true : nil,
            limit: pageSize,
            offset: offset
        )
        do {
            let response: MailboxListResponse = try await api.request(endpoint)
            loadedItems.append(contentsOf: response.mail)
            offset = loadedItems.count
            hasMore = response.mail.count >= pageSize
            applyState()
        } catch {
            state = .error(message: (error as? APIError)?.errorDescription ?? "Couldn't load mail.")
        }
    }

    private func applyState() {
        if loadedItems.isEmpty {
            state = .empty(
                ListOfRowsState.EmptyContent(
                    icon: .mailbox,
                    headline: "No mail yet",
                    subcopy: "When something lands in your mailbox, it'll show up here."
                )
            )
        } else {
            state = .loaded(
                sections: [RowSection(rows: loadedItems.map(row(for:)))],
                hasMore: hasMore
            )
        }
    }

    private func row(for mail: MailItem) -> RowModel {
        let chip = Self.statusChip(for: mail)
        return RowModel(
            id: mail.id,
            title: mail.displayTitle ?? mail.subject ?? mail.senderBusinessName ?? "Mail",
            subtitle: mail.previewText ?? mail.senderBusinessName ?? mail.senderAddress,
            template: .statusChip,
            leading: .icon(.mailbox, tint: Theme.Color.primary600),
            trailing: chip,
            onTap: { @Sendable in Task { @MainActor in self.onOpenMail(mail.id) } }
        )
    }

    private static func statusChip(for mail: MailItem) -> RowTrailing {
        if mail.priority == "urgent" {
            return .statusChip(text: "Urgent", variant: .error)
        }
        if !mail.viewed {
            return .statusChip(text: "Unread", variant: .info)
        }
        if mail.starred {
            return .statusChip(text: "Starred", variant: .warning)
        }
        return .statusChip(text: "Read", variant: .neutral)
    }
}
