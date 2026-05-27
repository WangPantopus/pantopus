//
//  MailboxRootView.swift
//  Pantopus
//
//  B.1 — Mailbox root archetype. One screen: a 4-drawer chip row
//  (Me / Home / Biz / Earn) + a 3-tab segmented bar (Incoming / Counter
//  / Vault) + the mail list for the active (drawer, tab). Replaces the
//  MailboxDrawersView (drawer list) + MailboxListView (flat list) pair.
//
//  Built on the List-of-Rows archetype: the drawer chips and tab bar
//  render in the shell's `customHeader`; the list, loading, empty, and
//  error states all come from the shell.
//

import SwiftUI

public struct MailboxRootView: View {
    @State private var viewModel: MailboxRootViewModel

    public init(viewModel: MailboxRootViewModel = MailboxRootViewModel()) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        ListOfRowsView(dataSource: viewModel) {
            MailboxRootHeader(viewModel: viewModel)
        }
        .accessibilityIdentifier("mailboxRoot")
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .onAppear { Analytics.track(.screenMailboxRootViewed) }
    }
}

#if DEBUG
#Preview("Me · Incoming") {
    NavigationStack {
        MailboxRootView(viewModel: MailboxRootViewModel(initialDrawer: .me, initialTab: .incoming))
    }
}

#Preview("Biz · Counter") {
    NavigationStack {
        MailboxRootView(viewModel: MailboxRootViewModel(initialDrawer: .business, initialTab: .counter))
    }
}

#Preview("Earn · empty") {
    NavigationStack {
        MailboxRootView(viewModel: MailboxRootViewModel(initialDrawer: .earn, initialTab: .incoming))
    }
}
#endif
