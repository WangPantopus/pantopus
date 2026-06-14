//
//  InviteeLandingStubView.swift
//  Pantopus
//
//  Foundation (I0b) routed stub — C5 Book · Stream I5.
//  Placeholder for the I5 feature stream to replace. The init is
//  wired with the route payload + `push`; the route/router are frozen.
//
//

import SwiftUI

/// Routed-screen view-model stub for C5 (Book). Stream I5 replaces
/// the body; `push` navigates deeper scheduling routes.
@Observable
@MainActor
final class InviteeLandingStubViewModel {
    let slug: String
    /// Pushes a deeper scheduling route onto the host navigation stack.
    let push: @MainActor (SchedulingRoute) -> Void

    init(
        slug: String,
        push: @escaping @MainActor (SchedulingRoute) -> Void
    ) {
        self.slug = slug
        self.push = push
    }
}

struct InviteeLandingStubView: View {
    @State private var viewModel: InviteeLandingStubViewModel

    init(viewModel: InviteeLandingStubViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        SchedulingStubScaffold(screenID: "C5", title: "Book", stream: "I5")
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        InviteeLandingStubView(viewModel: InviteeLandingStubViewModel(slug: "preview") { _ in })
    }
}
#endif
