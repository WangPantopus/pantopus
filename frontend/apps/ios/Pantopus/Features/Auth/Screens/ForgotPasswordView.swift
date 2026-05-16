//
//  ForgotPasswordView.swift
//  Pantopus
//
//  P3 stub. P4 implements the Forgot-password form against
//  `auth-frames.jsx` frame 3.
//

import SwiftUI

struct ForgotPasswordView: View {
    @State private var viewModel = ForgotPasswordViewModel()

    var body: some View {
        Text("Stub — to be implemented in T6.1b/c")
            .accessibilityIdentifier("forgotPasswordStub")
            .navigationTitle("Forgot password")
    }
}

@Observable
@MainActor
final class ForgotPasswordViewModel {}

#Preview {
    NavigationStack { ForgotPasswordView() }
}
