//
//  MyBusinessesView.swift
//  Pantopus
//
//  T6.3f / P14 — concrete List-of-Rows screen backed by
//  `MyBusinessesViewModel`. Identity-violet FAB pushes to the register-
//  business placeholder; tapping a row pushes to that business's
//  dashboard.
//

import SwiftUI

/// `GET /api/businesses/my-businesses` wrapped in the List-of-Rows
/// archetype.
struct MyBusinessesView: View {
    @State private var viewModel: MyBusinessesViewModel

    init(viewModel: MyBusinessesViewModel = MyBusinessesViewModel()) {
        _viewModel = State(initialValue: viewModel)
    }

    var body: some View {
        ListOfRowsView(dataSource: viewModel)
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .onAppear { Analytics.track(.screenMyBusinessesViewed) }
    }
}

#Preview {
    NavigationStack { MyBusinessesView() }
}
