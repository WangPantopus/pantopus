//
//  FirstRunWizardStubView.swift
//  Pantopus
//
//  Foundation (I0b) routed stub — A2 Set Up Booking Link · Stream I1.
//  Placeholder for the I1 feature stream to replace. The init is
//  wired with the route payload + `push`; the route/router are frozen.
//
//

import SwiftUI

/// Routed-screen view-model stub for A2 (Set Up Booking Link). Stream I1 replaces
/// the body; `push` navigates deeper scheduling routes.
@Observable
@MainActor
final class FirstRunWizardStubViewModel {
    let owner: SchedulingOwner
    /// Pushes a deeper scheduling route onto the host navigation stack.
    let push: @MainActor (SchedulingRoute) -> Void

    init(
        owner: SchedulingOwner,
        push: @escaping @MainActor (SchedulingRoute) -> Void
    ) {
        self.owner = owner
        self.push = push
    }
}

struct FirstRunWizardStubView: View {
    @State private var viewModel: FirstRunWizardStubViewModel

    init(viewModel: FirstRunWizardStubViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        SchedulingStubScaffold(screenID: "A2", title: "Set Up Booking Link", stream: "I1")
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        FirstRunWizardStubView(viewModel: FirstRunWizardStubViewModel(owner: .personal) { _ in })
    }
}
#endif
