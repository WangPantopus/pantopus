//
//  DiscoverHubView.swift
//  Pantopus
//
//  T5.4.1 — Discover hub. Thin wrapper around `ListOfRowsView`. The shell
//  renders the back chevron, centered "Discover hub" title, trailing
//  `sliders-horizontal` action, the chip-strip filter row, and the
//  grouped section cards.
//

import SwiftUI

public struct DiscoverHubView: View {
    @State private var viewModel: DiscoverHubViewModel

    public init(viewModel: DiscoverHubViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        ListOfRowsView(dataSource: viewModel)
            .accessibilityIdentifier("discoverHub")
    }
}

#Preview {
    NavigationStack {
        DiscoverHubView(viewModel: DiscoverHubViewModel())
    }
}
