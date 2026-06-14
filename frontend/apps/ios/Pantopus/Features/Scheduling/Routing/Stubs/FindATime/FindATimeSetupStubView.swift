//
//  FindATimeSetupStubView.swift
//  Pantopus
//
//  Foundation (I0b) routed stub — F4 Find a Time · Stream I11.
//  Placeholder for the I11 feature stream to replace. The init is
//  wired with the route payload + `push`; the route/router are frozen.
//
//

import SwiftUI

/// Routed-screen view-model stub for F4 (Find a Time). Stream I11 replaces
/// the body; `push` navigates deeper scheduling routes.
@Observable
@MainActor
final class FindATimeSetupStubViewModel {
    let homeId: String
    /// Pushes a deeper scheduling route onto the host navigation stack.
    let push: @MainActor (SchedulingRoute) -> Void

    init(
        homeId: String,
        push: @escaping @MainActor (SchedulingRoute) -> Void
    ) {
        self.homeId = homeId
        self.push = push
    }
}

struct FindATimeSetupStubView: View {
    @State private var viewModel: FindATimeSetupStubViewModel

    init(viewModel: FindATimeSetupStubViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        SchedulingStubScaffold(screenID: "F4", title: "Find a Time", stream: "I11")
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        FindATimeSetupStubView(viewModel: FindATimeSetupStubViewModel(homeId: "preview") { _ in })
    }
}
#endif
