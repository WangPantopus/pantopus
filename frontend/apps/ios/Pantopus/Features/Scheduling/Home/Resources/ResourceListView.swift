//
//  ResourceListView.swift
//  Pantopus
//
//  Stream I12 — F9 Bookable Home Resources · List. Renders the ListOfRows
//  archetype with the home-pillar identity; loading / empty / loaded / error
//  states come from the shell, wrapped in the offline banner.
//

import SwiftUI

struct ResourceListView: View {
    @State private var viewModel: ResourceListViewModel

    init(viewModel: ResourceListViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        ListOfRowsView(dataSource: viewModel)
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .accessibilityIdentifier("scheduling.resourceList")
    }
}
