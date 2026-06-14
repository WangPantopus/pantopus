//
//  MyPackagesStubView.swift
//  Pantopus
//
//  Foundation (I0b) routed stub — G11 My Packages · Stream I15.
//  Placeholder for the I15 feature stream to replace. The init is
//  wired with `push`; the route/router are frozen. (Customer-facing, so
//  no owner payload.)
//
//

import SwiftUI

/// Routed-screen view-model stub for G11 (My Packages). Stream I15 replaces
/// the body.
@Observable
@MainActor
final class MyPackagesStubViewModel {
    /// Pushes a deeper scheduling route onto the host navigation stack.
    let push: @MainActor (SchedulingRoute) -> Void

    init(push: @escaping @MainActor (SchedulingRoute) -> Void) {
        self.push = push
    }
}

struct MyPackagesStubView: View {
    @State private var viewModel: MyPackagesStubViewModel

    init(viewModel: MyPackagesStubViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        SchedulingStubScaffold(screenID: "G11", title: "My Packages", stream: "I15")
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        MyPackagesStubView(viewModel: MyPackagesStubViewModel { _ in })
    }
}
#endif
