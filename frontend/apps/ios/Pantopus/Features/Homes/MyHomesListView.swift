//
//  MyHomesListView.swift
//  Pantopus
//
//  Concrete List-of-Rows screen backed by `MyHomesListViewModel`.
//

import SwiftUI

/// `GET /api/homes/my-homes` wrapped in the List-of-Rows archetype.
struct MyHomesListView: View {
    @State private var viewModel: MyHomesListViewModel

    init(viewModel: MyHomesListViewModel = MyHomesListViewModel()) {
        _viewModel = State(initialValue: viewModel)
    }

    var body: some View {
        ListOfRowsView(dataSource: viewModel)
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .onAppear { Analytics.track(.screenMyHomesViewed) }
    }
}

#Preview {
    NavigationStack { MyHomesListView() }
}
