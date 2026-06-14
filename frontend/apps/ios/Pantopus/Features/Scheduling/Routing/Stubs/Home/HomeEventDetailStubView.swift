//
//  HomeEventDetailStubView.swift
//  Pantopus
//
//  Foundation (I0b) routed stub — F2 Event · Stream I10.
//  Placeholder for the I10 feature stream to replace. The init is
//  wired with the route payload + `push`; the route/router are frozen.
//
//

import SwiftUI

/// Routed-screen view-model stub for F2 (Event). Stream I10 replaces
/// the body; `push` navigates deeper scheduling routes.
@Observable
@MainActor
final class HomeEventDetailStubViewModel {
    let homeId: String
    let eventId: String
    /// Pushes a deeper scheduling route onto the host navigation stack.
    let push: @MainActor (SchedulingRoute) -> Void

    init(
        homeId: String,
        eventId: String,
        push: @escaping @MainActor (SchedulingRoute) -> Void
    ) {
        self.homeId = homeId
        self.eventId = eventId
        self.push = push
    }
}

struct HomeEventDetailStubView: View {
    @State private var viewModel: HomeEventDetailStubViewModel

    init(viewModel: HomeEventDetailStubViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        SchedulingStubScaffold(screenID: "F2", title: "Event", stream: "I10")
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        HomeEventDetailStubView(viewModel: HomeEventDetailStubViewModel(homeId: "preview", eventId: "preview") { _ in })
    }
}
#endif
