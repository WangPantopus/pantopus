//
//  InviteeSlotTakenStubView.swift
//  Pantopus
//
//  Foundation (I0b) routed stub — D5 Slot Taken · Stream I7.
//  Placeholder for the I7 feature stream to replace. The init is
//  wired with the route payload + `push`; the route/router are frozen.
//
//

import SwiftUI

/// Routed-screen view-model stub for D5 (Slot Taken). Stream I7 replaces
/// the body; `push` navigates deeper scheduling routes.
@Observable
@MainActor
final class InviteeSlotTakenStubViewModel {
    let slug: String
    let eventTypeSlug: String
    let tz: String
    /// Pushes a deeper scheduling route onto the host navigation stack.
    let push: @MainActor (SchedulingRoute) -> Void

    init(
        slug: String,
        eventTypeSlug: String,
        tz: String,
        push: @escaping @MainActor (SchedulingRoute) -> Void
    ) {
        self.slug = slug
        self.eventTypeSlug = eventTypeSlug
        self.tz = tz
        self.push = push
    }
}

struct InviteeSlotTakenStubView: View {
    @State private var viewModel: InviteeSlotTakenStubViewModel

    init(viewModel: InviteeSlotTakenStubViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        SchedulingStubScaffold(screenID: "D5", title: "Slot Taken", stream: "I7")
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        InviteeSlotTakenStubView(viewModel: InviteeSlotTakenStubViewModel(
            slug: "preview",
            eventTypeSlug: "preview",
            tz: "preview"
        ) { _ in })
    }
}
#endif
