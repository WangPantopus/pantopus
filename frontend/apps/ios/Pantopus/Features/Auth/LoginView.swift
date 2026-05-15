//
//  LoginView.swift
//  Pantopus
//

import SwiftUI

struct LoginView: View {
    @Environment(AuthManager.self) private var auth
    @State private var viewModel = LoginViewModel()

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer()
                VStack(spacing: 8) {
                    Icon(.home, size: 64, color: Theme.Color.primary600)
                    Text("Pantopus")
                        .font(.largeTitle.bold())
                        .accessibilityAddTraits(.isHeader)
                    Text("Your neighborhood, verified.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .accessibilityElement(children: .combine)

                VStack(spacing: 12) {
                    TextField("Email", text: $viewModel.email)
                        .textContentType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.emailAddress)
                        .autocorrectionDisabled()
                        .padding()
                        .background(Color(.systemGray6), in: .rect(cornerRadius: 12))
                        .accessibilityIdentifier("loginEmailField")
                        .accessibilityLabel("Email")
                        .accessibilityHint("Enter the email address for your Pantopus account")

                    SecureField("Password", text: $viewModel.password)
                        .textContentType(.password)
                        .padding()
                        .background(Color(.systemGray6), in: .rect(cornerRadius: 12))
                        .accessibilityIdentifier("loginPasswordField")
                        .accessibilityLabel("Password")
                        .accessibilityHint("At least six characters")
                }

                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                        .accessibilityIdentifier("loginErrorMessage")
                        .accessibilityLabel("Sign-in error: \(error)")
                }

                Button {
                    Task { await viewModel.signIn(using: auth) }
                } label: {
                    Group {
                        if viewModel.isLoading {
                            ProgressView()
                        } else {
                            Text("Sign in")
                                .fontWeight(.semibold)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                }
                .background(Color.accentColor, in: .rect(cornerRadius: 12))
                .foregroundStyle(.white)
                .disabled(!viewModel.canSubmit)
                .accessibilityIdentifier("loginSubmitButton")
                .accessibilityLabel(viewModel.isLoading ? "Signing in" : "Sign in")
                .accessibilityHint(viewModel.canSubmit ? "Submits the sign-in form" : "Fill in email and password to enable")

                Spacer()
            }
            .padding(24)
            .navigationBarHidden(true)
        }
    }
}

@Observable
@MainActor
final class LoginViewModel {
    var email: String = ""
    var password: String = ""
    var isLoading: Bool = false
    var errorMessage: String?

    var canSubmit: Bool {
        !isLoading && email.contains("@") && password.count >= 6
    }

    func signIn(using auth: AuthManager) async {
        errorMessage = nil
        isLoading = true
        defer { isLoading = false }
        do {
            try await auth.signIn(email: email, password: password)
        } catch {
            errorMessage = error.localizedDescription
            Observability.shared.capture(error)
        }
    }
}

#Preview {
    LoginView()
        .environment(AuthManager.shared)
}
