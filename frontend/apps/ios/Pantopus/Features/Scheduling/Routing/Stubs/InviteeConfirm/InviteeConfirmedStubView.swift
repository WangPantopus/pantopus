//
//  InviteeConfirmedStubView.swift
//  Pantopus
//
//  Foundation (I0b) routed stub — D3 Confirmed · Stream I6.
//  Placeholder for the I6 feature stream to replace. The init is
//  wired with the route payload + `push`; the route/router are frozen.
//
//

import SwiftUI

/// Routed-screen view-model stub for D3 (Confirmed). Stream I6 replaces
/// the body; `push` navigates deeper scheduling routes.
@Observable
@MainActor
final class InviteeConfirmedStubViewModel {
    let manageToken: String
    /// Pushes a deeper scheduling route onto the host navigation stack.
    let push: @MainActor (SchedulingRoute) -> Void

    init(
        manageToken: String,
        push: @escaping @MainActor (SchedulingRoute) -> Void
    ) {
        self.manageToken = manageToken
        self.push = push
    }
}

struct InviteeConfirmedStubView: View {
    @State private var viewModel: InviteeConfirmedStubViewModel

    init(viewModel: InviteeConfirmedStubViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        SchedulingStubScaffold(screenID: "D3", title: "Confirmed", stream: "I6")
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        InviteeConfirmedStubView(viewModel: InviteeConfirmedStubViewModel(manageToken: "preview") { _ in })
    }
}
#endif
