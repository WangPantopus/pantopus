//
//  MailboxDrawersView.swift
//  Pantopus
//

import SwiftUI

/// `GET /api/mailbox/v2/drawers` wrapped in the List-of-Rows archetype.
///
/// B.1 — superseded by `MailboxRootView` (the unified drawer-tabs hybrid).
/// Its route case (`HubRoute.mailboxDrawers`) has been removed, so this is
/// no longer reachable. Kept for one migration cycle; delete once the new
/// root has shipped.
@available(*, deprecated, message: "Replaced by MailboxRootView (B.1). Scheduled for deletion next cycle.")
struct MailboxDrawersView: View {
    @State private var viewModel: MailboxDrawersViewModel

    /// Split init (see GigsFeedView): avoids a Swift 6.1.2 / Xcode 16.4 SILGen
    /// crash in the defaulted-view-model argument generator. Behaviour is
    /// unchanged.
    init(viewModel: MailboxDrawersViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    init() {
        self.init(viewModel: MailboxDrawersViewModel())
    }

    var body: some View {
        ListOfRowsView(dataSource: viewModel)
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .onAppear { Analytics.track(.screenMailboxDrawersViewed) }
    }
}
