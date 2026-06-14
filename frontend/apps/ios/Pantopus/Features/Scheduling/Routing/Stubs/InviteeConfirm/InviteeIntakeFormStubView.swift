//
//  InviteeIntakeFormStubView.swift
//  Pantopus
//
//  Foundation (I0b) routed stub — D1 Your Details · Stream I6.
//  Placeholder for the I6 feature stream to replace. The init is
//  wired with the route payload + `push`; the route/router are frozen.
//
//

import SwiftUI

/// Routed-screen view-model stub for D1 (Your Details). Stream I6 replaces
/// the body; `push` navigates deeper scheduling routes.
@Observable
@MainActor
final class InviteeIntakeFormStubViewModel {
    let slug: String
    let eventTypeSlug: String
    let start: String
    let tz: String
    /// Pushes a deeper scheduling route onto the host navigation stack.
    let push: @MainActor (SchedulingRoute) -> Void

    init(
        slug: String,
        eventTypeSlug: String,
        start: String,
        tz: String,
        push: @escaping @MainActor (SchedulingRoute) -> Void
    ) {
        self.slug = slug
        self.eventTypeSlug = eventTypeSlug
        self.start = start
        self.tz = tz
        self.push = push
    }
}

struct InviteeIntakeFormStubView: View {
    @State private var viewModel: InviteeIntakeFormStubViewModel

    init(viewModel: InviteeIntakeFormStubViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        SchedulingStubScaffold(screenID: "D1", title: "Your Details", stream: "I6")
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        InviteeIntakeFormStubView(viewModel: InviteeIntakeFormStubViewModel(
            slug: "preview",
            eventTypeSlug: "preview",
            start: "preview",
            tz: "preview"
        ) { _ in })
    }
}
#endif
