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
                    Image(systemName: "house.fill")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 64, height: 64)
                        .foregroundStyle(.tint)
                    Text("Pantopus")
                        .font(.largeTitle.bold())
                    Text("Your neighborhood, verified.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                VStack(spacing: 12) {
                    TextField("Email", text: $viewModel.email)
                        .textContentType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.emailAddress)
                        .autocorrectionDisabled()
                        .padding()
                        .background(Color(.systemGray6), in: .rect(cornerRadius: 12))

                    SecureField("Password", text: $viewModel.password)
                        .textContentType(.password)
                        .padding()
                        .background(Color(.systemGray6), in: .rect(cornerRadius: 12))
                }

                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
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
        }
    }
}

#Preview {
    LoginView()
        .environment(AuthManager.shared)
}
