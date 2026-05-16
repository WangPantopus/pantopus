//
//  ConnectionsView.swift
//  Pantopus
//
//  T5.2.3 — Connections. Thin wrapper around the shared `ListOfRowsView`.
//  The shell renders the back chevron, centered title, trailing
//  `user-plus` action, search bar, three tabs, and the row card body.
//

import SwiftUI

public struct ConnectionsView: View {
    @State private var viewModel: ConnectionsViewModel

    public init(viewModel: ConnectionsViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        ListOfRowsView(dataSource: viewModel)
            .accessibilityIdentifier("connections")
    }
}

#Preview {
    NavigationStack {
        ConnectionsView(viewModel: ConnectionsViewModel())
    }
}
