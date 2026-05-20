//
//  MailboxSearchView.swift
//  Pantopus
//
//  P4.2 — Mailbox Search. Hosts the shared `SearchListShell` over the
//  user's mailbox, filtering by sender / subject / body / category.
//  Result rows reuse the mailbox list row template; tapping one opens the
//  mail detail. The mailbox-corpus fetch surfaces its own loading
//  (shimmer) and error (retry) states around the shell.
//

import SwiftUI

/// Mailbox Search screen.
public struct MailboxSearchView: View {
    @State private var viewModel: MailboxSearchViewModel

    public init(viewModel: MailboxSearchViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        content
            // The shell (and the error layout) carry their own back
            // affordance, so suppress the system navigation bar.
            .toolbar(.hidden, for: .navigationBar)
            .background(Theme.Color.appBg)
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .task { await viewModel.load() }
    }

    @ViewBuilder private var content: some View {
        switch viewModel.loadPhase {
        case let .error(message):
            errorLayout(message: message)
        case .loading, .ready:
            shell
        }
    }

    private var shell: some View {
        SearchListShell(
            placeholder: "Search mail",
            query: Binding(
                get: { viewModel.query },
                set: { viewModel.query = $0 }
            ),
            results: viewModel.results,
            isLoading: viewModel.isCorpusLoading,
            emptyState: EmptyStateContent(
                icon: .search,
                headline: "No matching mail",
                subcopy: emptySubcopy
            ),
            row: { mail in
                RowView(row: viewModel.row(for: mail))
                    .padding(.horizontal, Spacing.s4)
                    .padding(.top, Spacing.s2)
            },
            onCancel: { viewModel.cancel() }
        )
        .accessibilityIdentifier("mailboxSearch")
    }

    private var emptySubcopy: String {
        let trimmed = viewModel.query.trimmingCharacters(in: .whitespacesAndNewlines)
        return "No mail matches \u{201C}\(trimmed)\u{201D}. Try a sender, subject, or category."
    }

    private func errorLayout(message: String) -> some View {
        VStack(spacing: 0) {
            // Minimal header mirroring the shell so the user can still back
            // out when the mailbox fetch fails.
            HStack(spacing: Spacing.s2) {
                Button(action: { viewModel.cancel() }) {
                    Icon(.chevronLeft, size: 22, color: Theme.Color.appText)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Cancel search")
                .accessibilityIdentifier("searchListCancel")
                Spacer(minLength: 0)
            }
            .padding(.horizontal, Spacing.s2)
            .padding(.vertical, Spacing.s2)
            .background(Theme.Color.appSurface)

            Divider().background(Theme.Color.appBorderSubtle)

            EmptyState(
                icon: .alertCircle,
                headline: "Couldn't load mail",
                subcopy: message,
                cta: .init(title: "Try again") { await viewModel.retry() }
            )
            .accessibilityIdentifier("mailboxSearchError")
        }
        .background(Theme.Color.appBg)
    }
}

#Preview {
    NavigationStack {
        MailboxSearchView(viewModel: MailboxSearchViewModel())
    }
}
