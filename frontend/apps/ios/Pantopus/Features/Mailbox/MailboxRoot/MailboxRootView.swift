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
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    viewModel.openStamps()
                } label: {
                    Icon(.gift, size: 22, color: Theme.Color.appText)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Stamps")
                .accessibilityIdentifier("mailboxRootStamps")
            }
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button {
                        viewModel.openUnboxing()
                    } label: {
                        Label("Scan an item", systemImage: "camera.viewfinder")
                    }
                    .accessibilityIdentifier("mailboxRootSettings.scanUnboxing")
                    Button {
                        viewModel.openStamps()
                    } label: {
                        Label("Stamps", systemImage: "giftcard")
                    }
                    .accessibilityIdentifier("mailboxRootSettings.stamps")
                    Button {
                        viewModel.openVacationHold()
                    } label: {
                        Label("Vacation hold", systemImage: "calendar")
                    }
                    .accessibilityIdentifier("mailboxRootSettings.vacationHold")
                } label: {
                    Icon(.moreVertical, size: 22, color: Theme.Color.appText)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Mailbox settings")
                .accessibilityIdentifier("mailboxRootSettings")
            }
        }
        .accessibilityIdentifier("mailboxRoot")
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .onAppear { Analytics.track(.screenMailboxRootViewed) }
    }
}

#if DEBUG
// Previews drive the deterministic sample projection (the documented
// preview/test seam) by injecting `dataProvider` — the default init is the
// live path that fetches `GET /api/mailbox/v2/drawer/:drawer`.
#Preview("Me · Incoming") {
    NavigationStack {
        MailboxRootView(viewModel: MailboxRootViewModel(
            initialDrawer: .me,
            initialTab: .incoming,
            dataProvider: MailboxRootSampleData.sections
        ))
    }
}

#Preview("Biz · Counter") {
    NavigationStack {
        MailboxRootView(viewModel: MailboxRootViewModel(
            initialDrawer: .business,
            initialTab: .counter,
            dataProvider: MailboxRootSampleData.sections
        ))
    }
}

#Preview("Earn · empty") {
    NavigationStack {
        MailboxRootView(viewModel: MailboxRootViewModel(
            initialDrawer: .earn,
            initialTab: .incoming,
            dataProvider: MailboxRootSampleData.sections
        ))
    }
}
#endif
