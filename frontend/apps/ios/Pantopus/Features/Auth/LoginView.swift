//
//  LoginView.swift
//  Pantopus
//
//  T6.1b Log-in screen redesigned against `auth-frames.jsx` frame 1
//  (default) and frame 6 (inline error banner on submit failure). Per Q3
//  the v1 surface is email-only — no phone field, no SSO row.
//

import SwiftUI

struct LoginView: View {
    @Environment(AuthManager.self) private var auth
    @State private var viewModel = LoginViewModel()
    @State private var path: [AuthRoute] = []
    @State private var showPassword: Bool = false
    @State private var deepLink = DeepLinkRouter.shared

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(spacing: 0) {
                    Spacer(minLength: Spacing.s10)
                    BrandLockup()
                        .padding(.bottom, Spacing.s10)

                    VStack(spacing: Spacing.s2) {
                        Text("WELCOME BACK")
                            .pantopusTextStyle(.overline)
                            .foregroundStyle(Theme.Color.primary600)
                            .tracking(1.2)
                        Text("Log in to Pantopus")
                            .pantopusTextStyle(.h2)
                            .foregroundStyle(Theme.Color.appText)
                            .accessibilityAddTraits(.isHeader)
                        Text("Pick up where you left off on your block.")
                            .pantopusTextStyle(.small)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.horizontal, Spacing.s5)
                    .padding(.bottom, Spacing.s5)

                    if let error = viewModel.errorMessage {
                        ErrorBanner(error: error, onDismiss: viewModel.clearError)
                            .padding(.horizontal, Spacing.s5)
                            .padding(.bottom, Spacing.s3)
                            .accessibilityIdentifier("loginErrorBanner")
                    }

                    VStack(spacing: Spacing.s3) {
                        PantopusTextField(
                            "Email",
                            text: $viewModel.email,
                            placeholder: "you@email.com",
                            state: viewModel.emailFieldState,
                            keyboardType: .emailAddress,
                            contentType: .emailAddress,
                            identifier: "loginEmailField"
                        )
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .onChange(of: viewModel.email) { _, _ in viewModel.clearError() }

                        PasswordField(
                            value: $viewModel.password,
                            isVisible: $showPassword,
                            state: viewModel.passwordFieldState,
                            identifier: "loginPasswordField",
                            onChange: { viewModel.clearError() },
                            trailingLink: ("Forgot password?", { path.append(.forgotPassword) })
                        )
                    }
                    .padding(.horizontal, Spacing.s5)

                    Button(action: signIn) {
                        Group {
                            if viewModel.isLoading {
                                ProgressView()
                                    .tint(Theme.Color.appTextInverse)
                            } else {
                                HStack(spacing: Spacing.s1) {
                                    Text("Log in")
                                        .pantopusTextStyle(.body)
                                        .fontWeight(.semibold)
                                    Icon(.arrowRight, size: 16, color: Theme.Color.appTextInverse)
                                }
                            }
                        }
                        .foregroundStyle(Theme.Color.appTextInverse)
                        .frame(maxWidth: .infinity, minHeight: 48)
                    }
                    .background(
                        viewModel.canSubmit ? Theme.Color.primary600 : Theme.Color.appBorderStrong
                    )
                    .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
                    .padding(.horizontal, Spacing.s5)
                    .padding(.top, Spacing.s5)
                    .disabled(!viewModel.canSubmit)
                    .accessibilityIdentifier("loginSubmitButton")
                    .accessibilityLabel(viewModel.isLoading ? "Signing in" : "Log in")

                    HStack(spacing: Spacing.s1) {
                        Text("New to Pantopus?")
                            .pantopusTextStyle(.small)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                        Button {
                            path.append(.signUp)
                        } label: {
                            Text("Create account")
                                .pantopusTextStyle(.small)
                                .fontWeight(.semibold)
                                .foregroundStyle(Theme.Color.primary600)
                        }
                        .accessibilityIdentifier("loginCreateAccountLink")
                    }
                    .padding(.top, Spacing.s4)

                    Spacer(minLength: Spacing.s10)

                    AuthTrustFooter()
                        .padding(.bottom, Spacing.s4)
                }
                .frame(maxWidth: .infinity)
            }
            .background(Theme.Color.appSurface)
            .navigationBarHidden(true)
            .navigationDestination(for: AuthRoute.self) { route in
                switch route {
                case .login:
                    EmptyView()
                case .signUp:
                    SignUpView(
                        onClose: { if !path.isEmpty { path.removeLast() } },
                        onSuccess: { email in
                            // Backend hard-gates login on email_confirmed_at
                            // today (see docs/mobile/auth-backend-contracts.md
                            // §"Backend gap discovered"). Route through the
                            // verify-email surface until soft-gate lands;
                            // we hand it the email so the body copy + resend
                            // CTA render correctly.
                            path = [.verifyEmail(email: email, token: nil)]
                        }
                    )
                case .forgotPassword:
                    ForgotPasswordView {
                        if !path.isEmpty { path.removeLast() }
                    }
                case let .resetPassword(token):
                    ResetPasswordView(
                        token: token,
                        onClose: { if !path.isEmpty { path.removeLast() } },
                        onDone: { path = [] }
                    )
                case let .verifyEmail(email, token):
                    VerifyEmailView(
                        email: email,
                        token: token,
                        softGate: true,
                        onDone: { path = [] },
                        onChangeEmail: { _ in
                            // Route back to signup so the user can re-enter
                            // an email. The backend's email-change flow is
                            // documented in `docs/mobile/auth-backend-contracts.md`
                            // §2; today we restart signup with the new value.
                            path = [.signUp]
                        }
                    )
                case let .error(authError):
                    AuthErrorView(
                        error: authError,
                        onRetry: nil
                    ) { if !path.isEmpty { path.removeLast() } }
                }
            }
            .onAppear { consumeAuthDeepLinkIfNeeded() }
            .onChange(of: deepLink.pending) { _, _ in consumeAuthDeepLinkIfNeeded() }
        }
    }

    /// Pulls the `auth/reset-password` / `auth/verify-email` destinations
    /// off `DeepLinkRouter` and pushes the matching `AuthRoute` onto the
    /// stack. Anything else stays pending for the signed-in router to
    /// consume after sign-in. Idempotent on re-entry.
    private func consumeAuthDeepLinkIfNeeded() {
        guard let pending = deepLink.pending else { return }
        switch pending {
        case let .resetPassword(token):
            _ = deepLink.consume()
            path = [.resetPassword(token: token)]
        case let .verifyEmail(token, email):
            _ = deepLink.consume()
            path = [.verifyEmail(email: email, token: token)]
        default:
            break
        }
    }

    private func signIn() {
        Task { await viewModel.signIn(using: auth) }
    }
}

// MARK: - Login subcomponents

/// Centered brand lockup — 48pt mark + wordmark + tagline. Mirrors
/// `auth-frames.jsx:75-91`.
private struct BrandLockup: View {
    var body: some View {
        VStack(spacing: Spacing.s2) {
            Icon(.home, size: 48, color: Theme.Color.primary600)
                .accessibilityHidden(true)
            Text("Pantopus")
                .pantopusTextStyle(.h1)
                .foregroundStyle(Theme.Color.appText)
            Text("Your neighborhood, verified.")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Pantopus — Your neighborhood, verified.")
    }
}

/// Password input with show/hide eye toggle. Optional trailing link (used
/// for "Forgot password?" inline link in the design).
struct PasswordField: View {
    @Binding var value: String
    @Binding var isVisible: Bool
    let state: PantopusFieldState
    let identifier: String
    let onChange: () -> Void
    /// Optional inline link rendered next to the label — typically
    /// `"Forgot password?"`.
    let trailingLink: (label: String, action: () -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            HStack {
                Text("Password")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Spacer()
                if let trailingLink {
                    Button(action: trailingLink.action) {
                        Text(trailingLink.label)
                            .pantopusTextStyle(.caption)
                            .fontWeight(.semibold)
                            .foregroundStyle(Theme.Color.primary600)
                    }
                    .accessibilityIdentifier("loginForgotPasswordLink")
                }
            }

            HStack(spacing: Spacing.s2) {
                Group {
                    if isVisible {
                        TextField("••••••••", text: $value)
                            .textContentType(.password)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                    } else {
                        SecureField("••••••••", text: $value)
                            .textContentType(.password)
                    }
                }
                .accessibilityLabel("Password")
                .accessibilityIdentifier(identifier)
                .onChange(of: value) { _, _ in onChange() }

                Button {
                    isVisible.toggle()
                } label: {
                    Icon(.eye, size: 16, color: Theme.Color.appTextSecondary)
                        .frame(width: 28, height: 28)
                }
                .accessibilityLabel(isVisible ? "Hide password" : "Show password")
                .accessibilityIdentifier("loginPasswordVisibilityToggle")

                if case .error = state {
                    Icon(.alertCircle, size: 18, color: Theme.Color.error)
                } else if case .valid = state {
                    Icon(.check, size: 18, color: Theme.Color.success)
                }
            }
            .padding(.horizontal, Spacing.s3)
            .frame(minHeight: 44)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(borderColor, lineWidth: 1)
            )

            if case let .error(message) = state {
                Text(message)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.error)
            }
        }
    }

    private var borderColor: SwiftUI.Color {
        switch state {
        case .error: Theme.Color.error
        case .valid: Theme.Color.success
        case .default: Theme.Color.appBorder
        }
    }
}

/// Trust footer used at the bottom of every auth surface — mirrors
/// `auth-frames.jsx:247-260`.
struct AuthTrustFooter: View {
    var body: some View {
        HStack(spacing: Spacing.s1) {
            Icon(.shieldCheck, size: 12, color: Theme.Color.appTextSecondary)
            Text("Verified by address")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .accessibilityLabel("Verified by address")
    }
}

@Observable
@MainActor
final class LoginViewModel {
    var email: String = ""
    var password: String = ""
    var isLoading: Bool = false
    /// Typed error surface so the redesigned banner can render an icon +
    /// headline + body rather than a single string. Older callers can read
    /// `errorMessage?.errorDescription` for the legacy stringy form.
    private(set) var errorMessage: AuthError?

    var canSubmit: Bool {
        !isLoading && AuthValidation.email(email) == nil && password.count >= 6
    }

    var emailFieldState: PantopusFieldState {
        guard !email.isEmpty, errorMessage != nil else { return .default }
        // Don't mark the email field red on a generic login error; only
        // when the local-only validator fails.
        if let message = AuthValidation.email(email) {
            return .error(message)
        }
        return .default
    }

    var passwordFieldState: PantopusFieldState {
        guard errorMessage != nil else { return .default }
        return .error("")
    }

    func signIn(using auth: AuthManager) async {
        clearError()
        isLoading = true
        defer { isLoading = false }
        do {
            try await auth.signIn(email: email.lowercased(), password: password)
        } catch let error as AuthError {
            errorMessage = error
            Observability.shared.capture(error)
        } catch {
            errorMessage = .unknown
            Observability.shared.capture(error)
        }
    }

    func clearError() {
        if errorMessage != nil {
            errorMessage = nil
        }
    }
}

#Preview {
    LoginView()
        .environment(AuthManager.shared)
}
