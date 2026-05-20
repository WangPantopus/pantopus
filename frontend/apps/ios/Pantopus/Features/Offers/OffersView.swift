//
//  OffersView.swift
//  Pantopus
//
//  T5.2.4 — Cross-listing Offers. Thin wrapper around the shared
//  `ListOfRowsView`. Two tabs (Received / Sent), no FAB, filter icon in
//  the top-bar trailing slot. Row taps surface a `BidDTO` to the parent
//  navigation stack so we can push the gig (offer) detail.
//

import SwiftUI

public struct OffersView: View {
    @State private var viewModel: OffersViewModel

    public init(viewModel: OffersViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        @Bindable var bindable = viewModel
        return ListOfRowsView(dataSource: viewModel)
            .accessibilityIdentifier("offers")
            .sheet(isPresented: $bindable.isFilterPresented) {
                ActivityFilterSheet(
                    statusTitle: viewModel.statusFilterTitle,
                    statusOptions: viewModel.statusFilterOptions,
                    sortOptions: viewModel.sortFilterOptions,
                    filter: viewModel.activityFilter,
                    onApply: { viewModel.applyFilter($0) },
                    onClose: { viewModel.isFilterPresented = false }
                )
            }
    }
}

#Preview {
    NavigationStack {
        OffersView(viewModel: OffersViewModel())
    }
}
