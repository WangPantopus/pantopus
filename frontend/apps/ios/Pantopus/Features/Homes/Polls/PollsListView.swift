//
//  PollsListView.swift
//  Pantopus
//
//  Concrete List-of-Rows screen backed by `PollsListViewModel`. Wired
//  to `GET /api/homes/:id/polls` (route `backend/routes/home.js:6984`).
//

import SwiftUI

struct PollsListView: View {
    @State private var viewModel: PollsListViewModel

    init(viewModel: PollsListViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    var body: some View {
        ListOfRowsView(dataSource: viewModel)
            .accessibilityIdentifier("pollsList")
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .onAppear { Analytics.track(.screenPollsViewed) }
    }
}

#Preview {
    NavigationStack {
        PollsListView(viewModel: PollsListViewModel(homeId: "preview"))
    }
}
