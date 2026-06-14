//
//  NotificationPermissionPromptStubView.swift
//  Pantopus
//
//  Foundation (I0b) routed stub — H15 Notifications · Stream I18.
//  Placeholder for the I18 feature stream to replace. The init is
//  wired with the route payload + `push`; the route/router are frozen.
//
//

import SwiftUI

/// Routed-screen view-model stub for H15 (Notifications). Stream I18 replaces
/// the body; `push` navigates deeper scheduling routes.
@Observable
@MainActor
final class NotifPermissionPromptStubViewModel {
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

struct NotifPermissionPromptStubView: View {
    @State private var viewModel: NotifPermissionPromptStubViewModel

    init(viewModel: NotifPermissionPromptStubViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        SchedulingStubScaffold(screenID: "H15", title: "Notifications", stream: "I18")
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        NotifPermissionPromptStubView(viewModel: NotifPermissionPromptStubViewModel(owner: .personal) { _ in })
    }
}
#endif
