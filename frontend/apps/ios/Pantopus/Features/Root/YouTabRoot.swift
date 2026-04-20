//
//  YouTabRoot.swift
//  Pantopus
//
//  Placeholder "You" tab with account summary and sign-out.
//

import SwiftUI

/// Typed routes within the You tab's NavigationStack.
public enum YouRoute: Hashable {
    case signOutConfirm
}

/// NavigationStack wrapper for the You tab.
public struct YouTabRoot: View {
    @Environment(AuthManager.self) private var auth
    @State private var showsSignOutConfirm = false
    @State private var showsEditProfile = false

    public init() {}

    public var body: some View {
        NavigationStack {
            List {
                Section("Account") {
                    if case .signedIn(let user) = auth.state {
                        LabeledContent("Email", value: user.email)
                            .accessibilityLabel("Email: \(user.email)")
                        LabeledContent("User ID", value: user.id)
                            .accessibilityLabel("User ID: \(user.id)")
                    }
                    Button {
                        showsEditProfile = true
                    } label: {
                        HStack {
                            Text("Edit profile")
                                .foregroundStyle(Theme.Color.appText)
                            Spacer()
                            Icon(.chevronRight, size: 16, color: Theme.Color.appTextSecondary)
                        }
                    }
                    .accessibilityIdentifier("youEditProfileButton")
                }
                Section {
                    Button(role: .destructive) {
                        showsSignOutConfirm = true
                    } label: {
                        Text("Sign out")
                    }
                    .accessibilityIdentifier("youSignOutButton")
                    .accessibilityHint("Signs you out of Pantopus")
                }
            }
            .navigationTitle("You")
            .confirmationDialog(
                "Sign out of Pantopus?",
                isPresented: $showsSignOutConfirm,
                titleVisibility: .visible
            ) {
                Button("Sign out", role: .destructive) {
                    Task { await auth.signOut() }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("You'll need to sign in again to access your hub.")
            }
            .sheet(isPresented: $showsEditProfile) {
                EditProfileView()
            }
        }
    }
}

#Preview {
    YouTabRoot()
        .environment(AuthManager.previewSignedIn)
}
