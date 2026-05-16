//
//  VerifyEmailView.swift
//  Pantopus
//
//  P3 stub. P5 implements the Check-your-email surface against
//  `auth-frames.jsx` frame 5.
//

import SwiftUI

struct VerifyEmailView: View {
    @State private var viewModel = VerifyEmailViewModel()

    var body: some View {
        Text("Stub — to be implemented in T6.1b/c")
            .accessibilityIdentifier("verifyEmailStub")
            .navigationTitle("Verify email")
    }
}

@Observable
@MainActor
final class VerifyEmailViewModel {}

#Preview {
    NavigationStack { VerifyEmailView() }
}
