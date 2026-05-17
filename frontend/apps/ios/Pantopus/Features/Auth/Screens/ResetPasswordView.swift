//
//  ResetPasswordView.swift
//  Pantopus
//
//  T6.1c P5 — Reset password (auth-frames.jsx frame 4). Deep-linked from
//  the password-reset email via `pantopus://auth/reset-password?token=…`.
//  `token` is the hashed Supabase recovery token; the view trusts the
//  caller to have parsed it from the deep link.
//
//  Submit is gated on the two passwords matching AND the strength bucket
//  reaching at least "Fair" (≥ 8 chars, ≥ 1 letter, ≥ 1 number — same
//  rules as signup). On success the view transitions to the shared
//  Status/Wait "Password reset" surface and auto-redirects to login
//  after 3 seconds (the caller's `onDone` closure fires from the timer).
//

import SwiftUI

struct ResetPasswordView: View {
    @Environment(AuthManager.self) private var auth
    @State private var viewModel: ResetPasswordViewModel
    let onClose: () -> Void
    let onDone: () -> Void

    /// Caller-provided auto-redirect delay. Production paths use the
    /// 3 second default; tests pass `0` so the redirect kicks immediately.
    let redirectDelay: TimeInterval

    init(
        token: String,
        onClose: @escaping () -> Void = {},
        onDone: @escaping () -> Void = {},
        redirectDelay: TimeInterval = 3
    ) {
        _viewModel = State(initialValue: ResetPasswordViewModel(token: token))
        self.onClose = onClose
        self.onDone = onDone
        self.redirectDelay = redirectDelay
    }

    var body: some View {
        VStack(spacing: 0) {
            ResetPasswordTopBar(onClose: onClose)
            switch viewModel.phase {
            case .form:
                formBody
            case .reset:
                StatusWaitingView(
                    content: .passwordReset(),
                    onPrimary: { _ in onDone() }
                )
                .accessibilityIdentifier("resetPasswordSuccessStatus")
                .task {
                    if redirectDelay > 0 {
                        try? await Task.sleep(nanoseconds: UInt64(redirectDelay * 1_000_000_000))
                    }
                    onDone()
                }
            }
        }
        .background(Theme.Color.appSurface)
        .navigationBarHidden(true)
        .accessibilityIdentifier("resetPasswordScreen")
    }

    private var formBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s5) {
                VStack(alignment: .leading, spacing: Spacing.s2) {
                    Text("Set a new password")
                        .pantopusTextStyle(.h2)
                        .foregroundStyle(Theme.Color.appText)
                        .accessibilityAddTraits(.isHeader)
                    Text("Choose a password you haven't used here before.")
                        .pantopusTextStyle(.small)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                .padding(.top, Spacing.s5)

                if let error = viewModel.errorMessage {
                    ErrorBanner(error: error, onDismiss: viewModel.clearError)
                        .accessibilityIdentifier("resetPasswordErrorBanner")
                }

                VStack(alignment: .leading, spacing: Spacing.s1) {
                    PantopusTextField(
                        "New password",
                        text: $viewModel.password,
                        placeholder: "8+ characters",
                        state: viewModel.passwordFieldState,
                        isSecure: true,
                        contentType: .newPassword,
                        identifier: "resetPasswordPasswordField"
                    )
                    .onChange(of: viewModel.password) { _, _ in viewModel.clearError() }
                    PasswordStrengthRow(
                        score: viewModel.passwordStrength,
                        label: viewModel.passwordStrengthLabel
                    )
                    .accessibilityIdentifier("resetPasswordStrengthMeter")
                }

                PantopusTextField(
                    "Confirm new password",
                    text: $viewModel.confirmPassword,
                    placeholder: "Repeat your password",
                    state: viewModel.confirmFieldState,
                    isSecure: true,
                    contentType: .newPassword,
                    identifier: "resetPasswordConfirmField"
                )
                .onChange(of: viewModel.confirmPassword) { _, _ in viewModel.clearError() }

                Button(action: submit) {
                    Group {
                        if viewModel.isLoading {
                            ProgressView().tint(Theme.Color.appTextInverse)
                        } else {
                            Text("Set password")
                                .pantopusTextStyle(.body)
                                .fontWeight(.semibold)
                                .foregroundStyle(Theme.Color.appTextInverse)
                        }
                    }
                    .frame(maxWidth: .infinity, minHeight: 48)
                }
                .background(viewModel.canSubmit ? Theme.Color.primary600 : Theme.Color.appBorderStrong)
                .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
                .disabled(!viewModel.canSubmit)
                .accessibilityIdentifier("resetPasswordSubmitButton")
                .accessibilityLabel(viewModel.isLoading ? "Resetting password" : "Set password")

                Spacer(minLength: 0)
            }
            .padding(.horizontal, Spacing.s5)
            .padding(.bottom, Spacing.s5)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func submit() {
        Task { await viewModel.submit(using: auth) }
    }
}

private struct ResetPasswordTopBar: View {
    let onClose: () -> Void

    var body: some View {
        ZStack {
            Text("Set new password")
                .pantopusTextStyle(.body)
                .fontWeight(.semibold)
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
            HStack {
                Button(action: onClose) {
                    Icon(.x, size: 22, color: Theme.Color.appText)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Close")
                .accessibilityIdentifier("resetPasswordCloseButton")
                Spacer()
                Color.clear.frame(width: 44, height: 44)
            }
            .padding(.horizontal, Spacing.s2)
        }
        .frame(height: 44)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
        }
    }
}

/// Three-band password strength meter — same visual contract as the
/// SignUp screen's `PasswordStrengthMeter`. Kept local since the SignUp
/// version is `private` to that file; the duplication is intentional and
/// cheap.
private struct PasswordStrengthRow: View {
    let score: Int
    let label: String

    private var color: SwiftUI.Color {
        switch score {
        case 1: Theme.Color.error
        case 2: Theme.Color.warning
        case 3: Theme.Color.success
        default: Theme.Color.appBorder
        }
    }

    var body: some View {
        HStack(spacing: 6) {
            ForEach(0 ..< 3, id: \.self) { index in
                Capsule()
                    .fill(index < score ? color : Theme.Color.appSurfaceSunken)
                    .frame(height: 5)
            }
            Text(label)
                .pantopusTextStyle(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(color)
                .frame(width: 48, alignment: .trailing)
        }
    }
}

@Observable
@MainActor
final class ResetPasswordViewModel {
    enum Phase: Equatable {
        case form
        case reset
    }

    let token: String
    var password: String = ""
    var confirmPassword: String = ""
    private(set) var phase: Phase = .form
    private(set) var isLoading: Bool = false
    private(set) var errorMessage: AuthError?

    init(token: String) {
        self.token = token
    }

    var canSubmit: Bool {
        !isLoading && passwordsMeetStrength && passwordsMatch && !token.isEmpty
    }

    /// True when the new password passes the same client-side strength
    /// rules as signup (≥ 8 chars, ≥ 1 letter, ≥ 1 number).
    var passwordsMeetStrength: Bool {
        AuthValidation.password(password) == nil
    }

    var passwordsMatch: Bool {
        !confirmPassword.isEmpty && password == confirmPassword
    }

    var passwordStrength: Int {
        AuthValidation.passwordStrength(password)
    }

    var passwordStrengthLabel: String {
        switch passwordStrength {
        case 1: "Weak"
        case 2: "Fair"
        case 3: "Strong"
        default: "—"
        }
    }

    var passwordFieldState: PantopusFieldState {
        if password.isEmpty { return .default }
        if let message = AuthValidation.password(password) {
            return .error(message)
        }
        return .default
    }

    var confirmFieldState: PantopusFieldState {
        if confirmPassword.isEmpty { return .default }
        if confirmPassword != password {
            return .error("Passwords don't match.")
        }
        return .default
    }

    func submit(using auth: AuthManager) async {
        guard canSubmit else { return }
        clearError()
        isLoading = true
        defer { isLoading = false }
        do {
            try await auth.resetPassword(token: token, newPassword: password)
            phase = .reset
        } catch let error as AuthError {
            errorMessage = error
            Observability.shared.capture(error)
        } catch {
            errorMessage = .unknown
            Observability.shared.capture(error)
        }
    }

    func clearError() {
        if errorMessage != nil { errorMessage = nil }
    }
}

#Preview {
    NavigationStack {
        ResetPasswordView(token: "preview-token", redirectDelay: 0)
            .environment(AuthManager.previewSignedOut)
    }
}
