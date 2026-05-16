//
//  ResetPasswordView.swift
//  Pantopus
//
//  P3 stub. P4 implements the Reset-password form against
//  `auth-frames.jsx` frame 4. `token` is the hashed recovery token from
//  the verify-email deep link.
//

import SwiftUI

struct ResetPasswordView: View {
    let token: String
    @State private var viewModel = ResetPasswordViewModel()

    var body: some View {
        Text("Stub — to be implemented in T6.1b/c")
            .accessibilityIdentifier("resetPasswordStub")
            .navigationTitle("Reset password")
    }
}

@Observable
@MainActor
final class ResetPasswordViewModel {}

#Preview {
    NavigationStack { ResetPasswordView(token: "preview-token") }
}
