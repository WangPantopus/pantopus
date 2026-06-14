//
//  InviteeReviewConfirmStubView.swift
//  Pantopus
//
//  Foundation (I0b) routed stub — D2 Review & Confirm · Stream I6.
//  Placeholder for the I6 feature stream to replace. The init is
//  wired with the route payload + `push`; the route/router are frozen.
//
//

import SwiftUI

/// Routed-screen view-model stub for D2 (Review & Confirm). Stream I6 replaces
/// the body; `push` navigates deeper scheduling routes.
@Observable
@MainActor
final class InviteeReviewConfirmStubViewModel {
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

struct InviteeReviewConfirmStubView: View {
    @State private var viewModel: InviteeReviewConfirmStubViewModel

    init(viewModel: InviteeReviewConfirmStubViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        SchedulingStubScaffold(screenID: "D2", title: "Review & Confirm", stream: "I6")
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        InviteeReviewConfirmStubView(viewModel: InviteeReviewConfirmStubViewModel(
            slug: "preview",
            eventTypeSlug: "preview",
            start: "preview",
            tz: "preview"
        ) { _ in })
    }
}
#endif
