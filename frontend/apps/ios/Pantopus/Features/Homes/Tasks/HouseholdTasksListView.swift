//
//  HouseholdTasksListView.swift
//  Pantopus
//
//  T6.3c — Concrete List-of-Rows screen backed by
//  `HouseholdTasksListViewModel`. Wired to `GET /api/homes/:id/tasks`
//  (route `backend/routes/home.js:4170`).
//
//  Distinct from `MyTasksView` (T5.3.2) which lists the user's
//  posted-to-neighbours gigs reached via `me.gigs`.
//

import SwiftUI

struct HouseholdTasksListView: View {
    @State private var viewModel: HouseholdTasksListViewModel

    init(viewModel: HouseholdTasksListViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    var body: some View {
        ListOfRowsView(dataSource: viewModel)
            .accessibilityIdentifier("householdTasksList")
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .onAppear { Analytics.track(.screenHouseholdTasksViewed) }
    }
}

#Preview {
    NavigationStack {
        HouseholdTasksListView(viewModel: HouseholdTasksListViewModel(homeId: "preview"))
    }
}
