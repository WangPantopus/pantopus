//
//  MailboxDrawersView.swift
//  Pantopus
//

import SwiftUI

/// `GET /api/mailbox/v2/drawers` wrapped in the List-of-Rows archetype.
struct MailboxDrawersView: View {
    @State private var viewModel: MailboxDrawersViewModel

    init(viewModel: MailboxDrawersViewModel = MailboxDrawersViewModel()) {
        _viewModel = State(initialValue: viewModel)
    }

    var body: some View {
        ListOfRowsView(dataSource: viewModel)
    }
}

#Preview {
    NavigationStack { MailboxDrawersView() }
}
