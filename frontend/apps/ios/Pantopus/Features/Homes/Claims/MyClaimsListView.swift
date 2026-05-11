//
//  MyClaimsListView.swift
//  Pantopus
//
//  Concrete List-of-Rows screen backed by `MyClaimsListViewModel`.
//  TODO(design): detail tap currently no-ops because the claim-status
//  detail view is not drawn yet — see VM for context.
//

import SwiftUI

struct MyClaimsListView: View {
    @State private var viewModel: MyClaimsListViewModel

    init(viewModel: MyClaimsListViewModel = MyClaimsListViewModel()) {
        _viewModel = State(initialValue: viewModel)
    }

    var body: some View {
        ListOfRowsView(dataSource: viewModel)
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .onAppear { Analytics.track(.screenMyClaimsViewed) }
    }
}

#Preview {
    NavigationStack { MyClaimsListView() }
}
