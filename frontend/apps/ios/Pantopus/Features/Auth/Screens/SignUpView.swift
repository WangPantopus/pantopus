//
//  SignUpView.swift
//  Pantopus
//
//  P3 stub. P4 implements the Create-account form against `auth-frames.jsx`
//  frame 2.
//

import SwiftUI

struct SignUpView: View {
    @State private var viewModel = SignUpViewModel()

    var body: some View {
        Text("Stub — to be implemented in T6.1b/c")
            .accessibilityIdentifier("signUpStub")
            .navigationTitle("Create account")
    }
}

@Observable
@MainActor
final class SignUpViewModel {}

#Preview {
    NavigationStack { SignUpView() }
}
