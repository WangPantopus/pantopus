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
        ListOfRowsView(dataSource: viewModel)
            .accessibilityIdentifier("offers")
    }
}

#Preview {
    NavigationStack {
        OffersView(viewModel: OffersViewModel())
    }
}
