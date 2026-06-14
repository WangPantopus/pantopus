//
//  FindATimePollResponseStubView.swift
//  Pantopus
//
//  Foundation (I0b) routed stub — F6 Poll · Stream I11.
//  Placeholder for the I11 feature stream to replace. The init is
//  wired with the route payload + `push`; the route/router are frozen.
//
//

import SwiftUI

/// Routed-screen view-model stub for F6 (Poll). Stream I11 replaces
/// the body; `push` navigates deeper scheduling routes.
@Observable
@MainActor
final class FindATimePollResponseStubViewModel {
    let pollId: String
    /// Pushes a deeper scheduling route onto the host navigation stack.
    let push: @MainActor (SchedulingRoute) -> Void

    init(
        pollId: String,
        push: @escaping @MainActor (SchedulingRoute) -> Void
    ) {
        self.pollId = pollId
        self.push = push
    }
}

struct FindATimePollResponseStubView: View {
    @State private var viewModel: FindATimePollResponseStubViewModel

    init(viewModel: FindATimePollResponseStubViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        SchedulingStubScaffold(screenID: "F6", title: "Poll", stream: "I11")
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        FindATimePollResponseStubView(viewModel: FindATimePollResponseStubViewModel(pollId: "preview") { _ in })
    }
}
#endif
