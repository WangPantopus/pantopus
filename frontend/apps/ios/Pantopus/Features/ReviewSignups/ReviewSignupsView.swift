//
//  ReviewSignupsView.swift
//  Pantopus
//
//  T6.6c (P26.5) — Review signups. Thin wrapper around the shared
//  `ListOfRowsView`. The shell renders the back chevron, title,
//  trailing share action, filter chip strip, and avatar-first signup
//  rows with per-row status chip + Confirm / Edit footer.
//

import SwiftUI

public struct ReviewSignupsView: View {
    @State private var viewModel: ReviewSignupsViewModel

    public init(viewModel: ReviewSignupsViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        ListOfRowsView(dataSource: viewModel)
            .accessibilityIdentifier("reviewSignups")
    }
}

#Preview {
    NavigationStack {
        ReviewSignupsView(
            viewModel: ReviewSignupsViewModel(supportTrainId: "preview")
        )
    }
}
