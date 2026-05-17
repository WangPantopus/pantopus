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
    enum Tab: String, CaseIterable {
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

    let title = "Mailbox"
    let tabs: [ListOfRowsTab] = Tab.allCases.map {
        ListOfRowsTab(id: $0.rawValue, label: $0.label, count: nil)
    }

    var selectedTab: String = Tab.all.rawValue {
        didSet { if oldValue != selectedTab { Task { await reloadForTab() } } }
    }

    var topBarAction: TopBarAction? {
        TopBarAction(
            icon: .search,
            accessibilityLabel: "Search mail"
        ) { [weak self] in
            Task { @MainActor in self?.onOpenSearch() }
        }
    }

    var fab: FABAction? {
        nil
    }

    private(set) var state: ListOfRowsState = .loading
    /// Transient toast message. The view clears it after display.
    var toast: String?

    private let api: APIClient
    private let pageSize = 25
    private var offset = 0
    private var hasMore = false
    private var loadedItems: [MailItem] = []
    private var isLoadingPage = false
    private let onOpenMail: (String) -> Void
    private let onOpenSearch: @MainActor () -> Void

    init(
        api: APIClient = .shared,
        onOpenMail: @escaping (String) -> Void = { _ in },
        onOpenSearch: @escaping @MainActor () -> Void = {}
    ) {
        self.api = api
        self.onOpenMail = onOpenMail
        self.onOpenSearch = onOpenSearch
    }

    func load() async {
        if case .loaded = state, !loadedItems.isEmpty { return }
        state = .loading
        offset = 0
        loadedItems = []
        await fetchPage()
    }

    func refresh() async {
        offset = 0
        loadedItems = []
        await fetchPage()
    }

    func loadMoreIfNeeded() async {
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

    /// Map one mail DTO to the design's row anatomy (T6.5b / P20 re-skin):
    ///   - leading: 40pt category typeIcon (per `mailbox.jsx:4-16` accent
    ///     palette),
    ///   - sender as uppercase overline-style subtitle,
    ///   - title (display_title || subject),
    ///   - body (preview_text, 2 lines),
    ///   - chips: trust + category,
    ///   - `timeMeta`: relative time,
    ///   - `unread` highlight when `!viewed`.
    func row(for mail: MailItem) -> RowModel {
        let category = MailItemCategory.fromRaw(mail.mailType ?? mail.type)
        let trust = MailTrust.fromRaw(nil) // V1 list endpoint doesn't surface sender_trust.
        let chips: [RowChip] = [
            RowChip(
                text: category.label,
                icon: category.icon,
                tint: .custom(background: category.rowBackground, foreground: category.accent)
            ),
            RowChip(
                text: trust.label,
                icon: trust.icon,
                tint: .custom(background: trust.background, foreground: trust.foreground)
            )
        ]
        let mailId = mail.id
        return RowModel(
            id: mail.id,
            title: mail.displayTitle ?? mail.subject ?? mail.senderBusinessName ?? "Mail",
            subtitle: mail.senderBusinessName ?? mail.senderAddress,
            template: .statusChip,
            leading: .typeIcon(
                category.icon,
                background: category.rowBackground,
                foreground: category.accent
            ),
            trailing: .none,
            onTap: { @Sendable in Task { @MainActor in self.onOpenMail(mailId) } },
            body: mail.previewText,
            chips: chips,
            timeMeta: Self.formatRelativeTime(mail.createdAt),
            highlight: mail.viewed ? nil : .unread
        )
    }

    /// Lightweight relative-time formatter for the mail-row meta. Matches
    /// the Notifications V2 convention — `<1m → "now"`, `<1h → "Nm"`,
    /// `<24h → "Nh"`, `<7d → "Nd"`, else "MMM d".
    static func formatRelativeTime(_ iso: String) -> String? {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = f.date(from: iso) ?? {
            let plain = ISO8601DateFormatter()
            plain.formatOptions = [.withInternetDateTime]
            return plain.date(from: iso)
        }()
        guard let date else { return nil }
        let interval = Date().timeIntervalSince(date)
        if interval < 60 { return "now" }
        if interval < 3600 { return "\(Int(interval / 60))m" }
        if interval < 86_400 { return "\(Int(interval / 3600))h" }
        if interval < 7 * 86_400 { return "\(Int(interval / 86_400))d" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "MMM d"
        return formatter.string(from: date)
    }
}
