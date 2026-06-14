//
//  RecurringSetupStubView.swift
//  Pantopus
//
//  Foundation (I0b) routed stub — D12 Recurring Setup · Stream I7.
//  Placeholder for the I7 feature stream to replace. The init is
//  wired with the route payload + `push`; the route/router are frozen.
//
//

import SwiftUI

/// Routed-screen view-model stub for D12 (Recurring Setup). Stream I7 replaces
/// the body; `push` navigates deeper scheduling routes.
@Observable
@MainActor
final class RecurringSetupStubViewModel {
    let owner: SchedulingOwner
    let eventTypeId: String
    /// Pushes a deeper scheduling route onto the host navigation stack.
    let push: @MainActor (SchedulingRoute) -> Void

    init(
        owner: SchedulingOwner,
        eventTypeId: String,
        push: @escaping @MainActor (SchedulingRoute) -> Void
    ) {
        self.owner = owner
        self.eventTypeId = eventTypeId
        self.push = push
    }
}

struct RecurringSetupStubView: View {
    @State private var viewModel: RecurringSetupStubViewModel

    init(viewModel: RecurringSetupStubViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        SchedulingStubScaffold(screenID: "D12", title: "Recurring Setup", stream: "I7")
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        RecurringSetupStubView(viewModel: RecurringSetupStubViewModel(owner: .personal, eventTypeId: "preview") { _ in })
    }
}
#endif
