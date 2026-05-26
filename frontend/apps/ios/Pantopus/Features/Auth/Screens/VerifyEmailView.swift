//
//  VerifyEmailView.swift
//  Pantopus
//
//  T6.1c P5 — Verify email (auth-frames.jsx frame 5). Surfaced
//  post-signup as a full screen (current backend behaviour — see the
//  Q4 + backend-gap discussion in `docs/mobile/auth-backend-contracts.md`)
//  and also as the destination of the verification email's deep link
//  (`pantopus://auth/verify-email?token=…`). When a `token` is supplied
//  the view-model fires `AuthManager.verifyEmail` on appear; without one
//  it renders the soft-gate "we sent you a link" surface with a resend
//  CTA.
//
//  Per Q4 (soft-gate decision) the "I'll do this later" tertiary link is
//  visible — the backend's current hard-gate behaviour is documented in
//  the backend-gap section of the contracts doc and tracked separately.
//

import SwiftUI
import UIKit

struct VerifyEmailView: View {
    @Environment(AuthManager.self) private var auth
    @State private var viewModel: VerifyEmailViewModel
    @State private var showChangeEmailSheet: Bool = false
    let onDone: () -> Void
    let onChangeEmail: ((String) -> Void)?

    /// - Parameters:
    ///   - email: Address the verification link was sent to. Surfaced in
    ///     the body copy; passed to the resend endpoint.
    ///   - token: Hashed Supabase OTP from the verification link.
    ///     When non-nil, the screen auto-verifies on appear.
    ///   - softGate: When true (Q4 = soft-gate, the active decision) shows
    ///     the "I'll do this later" tertiary link. Hard-gate hosts pass
    ///     `false` to hide it.
    ///   - onDone: Tapped when the user either completes verification or
    ///     bails via "I'll do this later". Host pops the auth stack.
    ///   - onChangeEmail: Optional handoff for the "Wrong email? Change it"
    ///     row — host should route back to the create-account flow.
    init(
        email: String? = nil,
        token: String? = nil,
        softGate: Bool = true,
        onDone: @escaping () -> Void = {},
        onChangeEmail: ((String) -> Void)? = nil
    ) {
        _viewModel = State(
            initialValue: VerifyEmailViewModel(
                email: email,
                token: token,
                softGate: softGate
            )
        )
        self.onDone = onDone
        self.onChangeEmail = onChangeEmail
    }

    var body: some View {
        VStack(spacing: Spacing.s0) {
            ScrollView {
                VStack(spacing: Spacing.s5) {
                    Spacer(minLength: Spacing.s10)
                    illustration
                    headlineBlock
                    if let banner = bannerCopy {
                        Text(banner.text)
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(banner.color)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, Spacing.s4)
                            .padding(.vertical, Spacing.s2)
                            .background(banner.background)
                            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                            .accessibilityIdentifier("verifyEmailBanner")
                    }
                    Spacer(minLength: Spacing.s10)
                }
                .padding(.horizontal, Spacing.s5)
                .frame(maxWidth: .infinity)
            }
            actionStack
        }
        .background(Theme.Color.appSurface)
        .navigationBarHidden(true)
        .accessibilityIdentifier("verifyEmailScreen")
        .task {
            await viewModel.verifyOnAppearIfNeeded(using: auth)
        }
        .onChange(of: viewModel.didComplete) { _, complete in
            if complete { onDone() }
        }
        .sheet(isPresented: $showChangeEmailSheet) {
            ChangeEmailSheet(
                current: viewModel.email ?? "",
                onCancel: { showChangeEmailSheet = false },
                onSubmit: { newEmail in
                    showChangeEmailSheet = false
                    onChangeEmail?(newEmail)
                }
            )
        }
    }

    private var illustration: some View {
        ZStack {
            Circle()
                .fill(Theme.Color.primary50)
                .frame(width: 140, height: 140)
            Icon(.mailbox, size: 80, color: Theme.Color.primary500)
        }
        .accessibilityHidden(true)
        .accessibilityIdentifier("verifyEmailIllustration")
    }

    private var headlineBlock: some View {
        VStack(spacing: Spacing.s2) {
            Text("Verify your email")
                .pantopusTextStyle(.h2)
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
            Text(bodyText)
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
        }
    }

    private var bodyText: String {
        let address = viewModel.email ?? "your email"
        return "We sent a verification link to \(address). Click it to unlock all features."
    }

    private struct BannerCopy {
        let text: String
        let color: SwiftUI.Color
        let background: SwiftUI.Color
    }

    private var bannerCopy: BannerCopy? {
        if viewModel.isVerifying {
            return BannerCopy(
                text: "Verifying your email…",
                color: Theme.Color.primary700,
                background: Theme.Color.primary50
            )
        }
        if viewModel.didVerify {
            return BannerCopy(
                text: "Email verified. You can now sign in.",
                color: Theme.Color.success,
                background: Theme.Color.successBg
            )
        }
        if let error = viewModel.errorMessage {
            return BannerCopy(
                text: error.errorDescription ?? "Something went wrong.",
                color: Theme.Color.error,
                background: Theme.Color.errorBg
            )
        }
        if viewModel.didResend {
            return BannerCopy(
                text: "Verification email sent.",
                color: Theme.Color.primary700,
                background: Theme.Color.primary50
            )
        }
        return nil
    }

    private var actionStack: some View {
        VStack(spacing: Spacing.s2) {
            Button(action: openMailApp) {
                Text("Open mail app")
                    .pantopusTextStyle(.body)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .frame(maxWidth: .infinity, minHeight: 48)
            }
            .background(Theme.Color.primary600)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .accessibilityIdentifier("verifyEmailOpenMailButton")

            Button(action: resend) {
                Group {
                    if viewModel.isResending {
                        ProgressView().tint(Theme.Color.appText)
                    } else {
                        Text(resendLabel)
                            .pantopusTextStyle(.body)
                            .fontWeight(.semibold)
                            .foregroundStyle(viewModel.canResend ? Theme.Color.appText : Theme.Color.appTextMuted)
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 44)
            }
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorderStrong, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .disabled(!viewModel.canResend)
            .accessibilityIdentifier("verifyEmailResendButton")

            if viewModel.softGate {
                Button(action: onDone) {
                    Text("I'll do this later")
                        .pantopusTextStyle(.small)
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.Color.primary600)
                        .frame(maxWidth: .infinity, minHeight: 36)
                }
                .accessibilityIdentifier("verifyEmailDoLaterButton")
            }

            Button(
                action: { showChangeEmailSheet = true },
                label: {
                    Text("Wrong email? Change it")
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .frame(maxWidth: .infinity, minHeight: 32)
                }
            )
            .accessibilityIdentifier("verifyEmailChangeEmailButton")
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s3)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .top) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
        }
    }

    private var resendLabel: String {
        if let remaining = viewModel.cooldownRemaining(now: Date()), remaining > 0 {
            return "Resend in \(Int(remaining))s"
        }
        return "Resend email"
    }

    private func resend() {
        Task { await viewModel.resend(using: auth) }
    }

    /// Open the user's preferred mail app via the `mailto:` URL scheme.
    /// `UIApplication.shared.open` falls back silently when no mail app
    /// is installed (`canOpenURL` returns false on simulator without an
    /// account, but the open call is harmless).
    private func openMailApp() {
        guard let url = URL(string: "mailto:") else { return }
        UIApplication.shared.open(url, options: [:], completionHandler: nil)
        Observability.shared.track("auth.verify.open_mail_tapped")
    }
}

private struct ChangeEmailSheet: View {
    let current: String
    let onCancel: () -> Void
    let onSubmit: (String) -> Void
    @State private var draft: String = ""

    var body: some View {
        VStack(spacing: Spacing.s4) {
            HStack {
                Button("Cancel", action: onCancel)
                    .accessibilityIdentifier("verifyEmailChangeCancel")
                Spacer()
                Text("Change email")
                    .pantopusTextStyle(.body)
                    .fontWeight(.semibold)
                Spacer()
                Button("Submit") { onSubmit(draft) }
                    .disabled(AuthValidation.email(draft) != nil)
                    .accessibilityIdentifier("verifyEmailChangeSubmit")
            }
            Text("We'll restart signup so you can verify a different address.")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
            PantopusTextField(
                "New email",
                text: $draft,
                placeholder: "you@email.com",
                state: draft.isEmpty ? .default : (AuthValidation.email(draft).map(PantopusFieldState.error) ?? .default),
                keyboardType: .emailAddress,
                contentType: .emailAddress,
                identifier: "verifyEmailChangeField"
            )
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            Spacer()
        }
        .padding(Spacing.s4)
        .onAppear { draft = current }
        .presentationDetents([.medium])
    }
}

@Observable
@MainActor
final class VerifyEmailViewModel {
    let email: String?
    let token: String?
    let softGate: Bool

    private(set) var isVerifying: Bool = false
    private(set) var didVerify: Bool = false
    private(set) var didResend: Bool = false
    private(set) var isResending: Bool = false
    private(set) var didComplete: Bool = false
    private(set) var errorMessage: AuthError?
    /// Earliest wall-clock time the user may resend at. Drives the
    /// client-side cooldown label and short-circuits redundant requests.
    private(set) var resendCooldownUntil: Date?
    private var hasAutoVerified: Bool = false

    /// Cooldown between successful resends. Matches the web's value.
    static let resendCooldown: TimeInterval = 30

    init(email: String?, token: String?, softGate: Bool) {
        self.email = email
        self.token = token
        self.softGate = softGate
    }

    /// True when the resend CTA is tappable (no in-flight call AND no
    /// active cooldown AND we have an email to resend to).
    var canResend: Bool {
        !isResending && cooldownRemaining(now: Date()) == nil && (email?.isEmpty == false)
    }

    /// Returns the number of seconds remaining in the cooldown, or nil
    /// if the cooldown is inactive. View renders this as
    /// "Resend in Ns".
    func cooldownRemaining(now: Date) -> TimeInterval? {
        guard let until = resendCooldownUntil else { return nil }
        let delta = until.timeIntervalSince(now)
        return delta > 0 ? delta : nil
    }

    /// Fired from `.task` once per appearance. If a token was supplied
    /// (verification-email deep-link path), POST it to the backend.
    func verifyOnAppearIfNeeded(using auth: AuthManager) async {
        guard let token, !hasAutoVerified else { return }
        hasAutoVerified = true
        isVerifying = true
        defer { isVerifying = false }
        do {
            try await auth.verifyEmail(token: token)
            didVerify = true
            // Bounce back to login after a beat so the user reads the
            // success banner.
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            didComplete = true
        } catch let error as AuthError {
            errorMessage = error
            Observability.shared.capture(error)
        } catch {
            errorMessage = .unknown
            Observability.shared.capture(error)
        }
    }

    /// Re-sends the verification email. Honours the local cooldown so
    /// repeated taps don't pile on the backend rate limiter.
    func resend(using auth: AuthManager, now: Date = Date()) async {
        guard !isResending,
              cooldownRemaining(now: now) == nil,
              let email,
              !email.isEmpty
        else { return }
        clearError()
        isResending = true
        didResend = false
        defer { isResending = false }
        do {
            try await auth.resendVerification(email: email)
            didResend = true
            resendCooldownUntil = now.addingTimeInterval(Self.resendCooldown)
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

#Preview("Post-signup soft-gate") {
    NavigationStack {
        VerifyEmailView(email: "alice@example.com")
            .environment(AuthManager.previewSignedOut)
    }
}

#Preview("Deep-link landing") {
    NavigationStack {
        VerifyEmailView(email: "alice@example.com", token: "hashed-token-preview")
            .environment(AuthManager.previewSignedOut)
    }
}
