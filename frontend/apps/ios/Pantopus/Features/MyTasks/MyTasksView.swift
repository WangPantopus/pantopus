//
//  MyTasksView.swift
//  Pantopus
//
//  T5.3.2 — My tasks V2. Thin wrapper around `ListOfRowsView`. The
//  shell renders the back chevron, centered "My tasks" title, trailing
//  filter icon, four tabs, the open-tab banner, the row cards (with
//  the bidder stack + status chip + footer actions), and the 56pt
//  canonical-create FAB ("Post a task"). All bespoke logic lives in
//  the ViewModel.
//

import SwiftUI

public struct MyTasksView: View {
    @State private var viewModel: MyTasksViewModel

    public init(viewModel: MyTasksViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        ListOfRowsView(dataSource: viewModel)
            .accessibilityIdentifier("my-tasks")
    }
}

#Preview {
    NavigationStack {
        MyTasksView(viewModel: MyTasksViewModel())
    }
}
