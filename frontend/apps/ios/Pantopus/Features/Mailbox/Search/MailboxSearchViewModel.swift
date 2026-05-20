//
//  MailboxSearchViewModel.swift
//  Pantopus
//
//  P4.2 — Backs `MailboxSearchView`. Fetches the user's mailbox once via
//  `GET /api/mailbox`, then filters that corpus client-side by query
//  (sender, subject, body, category). The V1 list route has no `q`
//  parameter yet, so search is local. Result rows reuse the canonical
//  mailbox row projection (`MailboxListViewModel.makeRow`) so they render
//  identically to the list.
//

import Foundation
import Observation

/// ViewModel for the Mailbox Search surface.
@Observable
@MainActor
final class MailboxSearchViewModel {
    /// One-time corpus-fetch lifecycle. The per-query result phases
    /// (typing-shimmer / results / empty) are derived by `SearchListShell`
    /// from `query` + `results` + `isCorpusLoading`; this enum only models
    /// the fetch of the searchable set.
    enum LoadPhase: Sendable, Equatable {
        case loading
        case ready
        case error(message: String)
    }

    private(set) var loadPhase: LoadPhase = .loading

    /// Live query, bound to the shell's field. The setter recomputes
    /// `results` synchronously so filtering feels instant; the shell's own
    /// 250ms debounce gates the empty-state flash.
    var query: String = "" {
        didSet { recompute() }
    }

    /// Filtered matches for the current query. Empty while the query is
    /// blank — the shell shows its recent/blank phase then.
    private(set) var results: [MailItem] = []

    /// Drives the shell's typing-shimmer while the corpus is still loading.
    var isCorpusLoading: Bool { loadPhase == .loading }

    private let api: APIClient
    private let onOpenMail: (String) -> Void
    private let onCancel: () -> Void
    private var corpus: [MailItem] = []
    private let corpusLimit = 100

    init(
        api: APIClient = .shared,
        onOpenMail: @escaping (String) -> Void = { _ in },
        onCancel: @escaping () -> Void = {}
    ) {
        self.api = api
        self.onOpenMail = onOpenMail
        self.onCancel = onCancel
    }

    /// Fetch the searchable mailbox corpus. Idempotent once loaded.
    func load() async {
        if case .ready = loadPhase { return }
        await fetchCorpus()
    }

    /// Re-fetch after an error (wired to the error state's Retry CTA).
    func retry() async {
        loadPhase = .loading
        await fetchCorpus()
    }

    /// Back out of search.
    func cancel() {
        onCancel()
    }

    /// Result row reusing the mailbox list row template, routing taps to
    /// this surface's `onOpenMail`.
    func row(for mail: MailItem) -> RowModel {
        MailboxListViewModel.makeRow(for: mail) { [weak self] mailId in
            Task { @MainActor in self?.onOpenMail(mailId) }
        }
    }

    private func fetchCorpus() async {
        let endpoint = MailboxEndpoints.list(archived: false, limit: corpusLimit, offset: 0)
        do {
            let response: MailboxListResponse = try await api.request(endpoint)
            corpus = response.mail
            loadPhase = .ready
            recompute()
        } catch {
            loadPhase = .error(
                message: (error as? APIError)?.errorDescription ?? "Couldn't load your mailbox."
            )
        }
    }

    private func recompute() {
        let needle = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !needle.isEmpty else {
            results = []
            return
        }
        results = corpus.filter { Self.matches($0, needle: needle) }
    }

    /// Case-insensitive substring match across the four fields the prompt
    /// calls out: sender, subject/title, body, and category.
    static func matches(_ mail: MailItem, needle: String) -> Bool {
        let category = MailItemCategory.fromRaw(mail.mailType ?? mail.type)
        let fields: [String?] = [
            mail.senderBusinessName,
            mail.senderAddress,
            mail.subject,
            mail.displayTitle,
            mail.previewText,
            mail.content,
            category.label
        ]
        return fields.contains { $0?.lowercased().contains(needle) == true }
    }
}
