//
//  AuthErrorView.swift
//  Pantopus
//
//  P3 stub. P4 implements the inline error banner + dedicated error surface
//  against `auth-frames.jsx` frame 6.
//

import SwiftUI

struct AuthErrorView: View {
    let error: AuthError
    @State private var viewModel = AuthErrorViewModel()

    var body: some View {
        Text("Stub — to be implemented in T6.1b/c")
            .accessibilityIdentifier("authErrorStub")
            .navigationTitle("Auth error")
    }
}

@Observable
@MainActor
final class AuthErrorViewModel {}

#Preview {
    NavigationStack { AuthErrorView(error: .invalidCredentials) }
}
