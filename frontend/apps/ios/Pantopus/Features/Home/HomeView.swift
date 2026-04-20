//
//  HomeView.swift
//  Pantopus
//

import SwiftUI

struct HomeView: View {
    @Environment(AuthManager.self) private var auth

    var body: some View {
        NavigationStack {
            List {
                Section("Account") {
                    if case .signedIn(let user) = auth.state {
                        LabeledContent("Email", value: user.email)
                        LabeledContent("User ID", value: user.id)
                    }
                }
                Section {
                    Button("Sign out", role: .destructive) {
                        Task { await auth.signOut() }
                    }
                }
            }
            .navigationTitle("Home")
        }
    }
}

#Preview {
    HomeView()
        .environment(AuthManager.previewSignedIn)
}
