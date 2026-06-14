//
//  PackageEditorStubView.swift
//  Pantopus
//
//  Foundation (I0b) routed stub — G9 Package Editor · Stream I15.
//  Placeholder for the I15 feature stream to replace. The init is
//  wired with the route payload + `push`; the route/router are frozen.
//
//

import SwiftUI

/// Routed-screen view-model stub for G9 (Package Editor). `packageId` is nil
/// when creating a new package. Stream I15 replaces the body.
@Observable
@MainActor
final class PackageEditorStubViewModel {
    let owner: SchedulingOwner
    let packageId: String?
    /// Pushes a deeper scheduling route onto the host navigation stack.
    let push: @MainActor (SchedulingRoute) -> Void

    init(
        owner: SchedulingOwner,
        packageId: String?,
        push: @escaping @MainActor (SchedulingRoute) -> Void
    ) {
        self.owner = owner
        self.packageId = packageId
        self.push = push
    }
}

struct PackageEditorStubView: View {
    @State private var viewModel: PackageEditorStubViewModel

    init(viewModel: PackageEditorStubViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        SchedulingStubScaffold(screenID: "G9", title: "Package Editor", stream: "I15")
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        PackageEditorStubView(viewModel: PackageEditorStubViewModel(owner: .personal, packageId: nil) { _ in })
    }
}
#endif
