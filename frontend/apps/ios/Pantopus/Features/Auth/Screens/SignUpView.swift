//
//  SignUpView.swift
//  Pantopus
//
//  T6.1b Create-account form. Renders against `auth-frames.jsx` frame 2,
//  expanded to the 14 backend-required + optional fields per the P4 spec.
//  Uses the FormShell archetype with its new `bottomActionLabel` slot.
//

import SwiftUI

struct SignUpView: View {
    @Environment(AuthManager.self) private var auth
    @State private var viewModel = SignUpViewModel()

    /// Caller-supplied dismiss hook — invoked by the top-bar X.
    let onClose: () -> Void
    /// Invoked when signUp returns 201, with the email that was registered
    /// so the host can push `AuthRoute.verifyEmail(email:)` onto the auth
    /// stack and the verify-email surface can render + resend correctly.
    let onSuccess: (String) -> Void

    var body: some View {
        FormShell(
            title: "Create account",
            rightActionLabel: nil,
            bottomActionLabel: "Create account",
            isValid: viewModel.isValid,
            isDirty: true,
            isSaving: viewModel.isSubmitting,
            onClose: onClose,
            onCommit: submit
        ) {
            if let error = viewModel.topLevelError {
                ErrorBanner(error: error) { viewModel.clearTopLevelError() }
                    .padding(.horizontal, Spacing.s4)
                    .accessibilityIdentifier("signUpErrorBanner")
            }

            FormFieldGroup("Account") {
                emailField
                passwordField
                confirmPasswordField
            }

            FormFieldGroup("Profile") {
                usernameField
                firstNameField
                middleNameField
                lastNameField
                dateOfBirthField
            }

            FormFieldGroup("Address") {
                addressField
                cityStateZipRow
            }

            FormFieldGroup("Account type") {
                AccountTypePicker(selection: $viewModel.accountType)
                    .accessibilityIdentifier("signUpAccountTypePicker")
            }

            FormFieldGroup("Optional") {
                phoneField
                inviteCodeField
            }

            TermsCheckbox(
                isOn: Binding(get: { viewModel.agreedToTerms }, set: { viewModel.agreedToTerms = $0 })
            )
            .accessibilityIdentifier("signUpTermsCheckbox")
            .padding(.horizontal, Spacing.s4)
        }
        .onChange(of: viewModel.didSucceed) { _, succeeded in
            guard succeeded else { return }
            let email = viewModel.email.trimmingCharacters(in: .whitespaces).lowercased()
            viewModel.acknowledgeSuccess()
            onSuccess(email)
        }
    }

    // MARK: - Field bindings

    private var emailField: some View {
        PantopusTextField(
            "Email",
            text: $viewModel.email,
            placeholder: "you@email.com",
            state: state(for: .email),
            keyboardType: .emailAddress,
            contentType: .emailAddress,
            identifier: "signUpEmailField"
        )
        .onChange(of: viewModel.email) { _, _ in viewModel.clearError(for: .email) }
    }

    private var passwordField: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            PantopusTextField(
                "Password",
                text: $viewModel.password,
                placeholder: "8+ characters",
                state: state(for: .password),
                isSecure: true,
                contentType: .newPassword,
                identifier: "signUpPasswordField"
            )
            .onChange(of: viewModel.password) { _, _ in viewModel.clearError(for: .password) }
            PasswordStrengthMeter(score: viewModel.passwordStrength, label: viewModel.passwordStrengthLabel)
                .accessibilityIdentifier("signUpPasswordStrengthMeter")
        }
    }

    private var confirmPasswordField: some View {
        PantopusTextField(
            "Confirm password",
            text: $viewModel.confirmPassword,
            placeholder: "Repeat your password",
            state: state(for: .confirmPassword),
            isSecure: true,
            contentType: .newPassword,
            identifier: "signUpConfirmPasswordField"
        )
        .onChange(of: viewModel.confirmPassword) { _, _ in viewModel.clearError(for: .confirmPassword) }
    }

    private var usernameField: some View {
        PantopusTextField(
            "Username",
            text: $viewModel.username,
            placeholder: "your_handle",
            state: state(for: .username),
            contentType: .username,
            identifier: "signUpUsernameField"
        )
        .textInputAutocapitalization(.never)
        .onChange(of: viewModel.username) { _, _ in viewModel.clearError(for: .username) }
    }

    private var firstNameField: some View {
        PantopusTextField(
            "First name",
            text: $viewModel.firstName,
            placeholder: "Maria",
            state: state(for: .firstName),
            contentType: .givenName,
            identifier: "signUpFirstNameField"
        )
        .onChange(of: viewModel.firstName) { _, _ in viewModel.clearError(for: .firstName) }
    }

    private var middleNameField: some View {
        PantopusTextField(
            "Middle name (optional)",
            text: $viewModel.middleName,
            placeholder: "Optional",
            state: state(for: .middleName),
            contentType: .middleName,
            identifier: "signUpMiddleNameField"
        )
    }

    private var lastNameField: some View {
        PantopusTextField(
            "Last name",
            text: $viewModel.lastName,
            placeholder: "Kowalski",
            state: state(for: .lastName),
            contentType: .familyName,
            identifier: "signUpLastNameField"
        )
        .onChange(of: viewModel.lastName) { _, _ in viewModel.clearError(for: .lastName) }
    }

    private var dateOfBirthField: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            Text("Date of birth")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            DatePicker(
                "",
                selection: Binding(
                    get: { viewModel.dateOfBirth ?? Self.defaultDate },
                    set: { viewModel.dateOfBirth = $0 }
                ),
                in: ...Self.maxDate,
                displayedComponents: .date
            )
            .labelsHidden()
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityIdentifier("signUpDateOfBirthField")
            if let message = visibleError(for: .dateOfBirth) {
                Text(message)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.error)
            }
        }
    }

    private var phoneField: some View {
        PantopusTextField(
            "Phone (optional)",
            text: $viewModel.phoneNumber,
            placeholder: "+15555550123",
            state: state(for: .phoneNumber),
            keyboardType: .phonePad,
            contentType: .telephoneNumber,
            identifier: "signUpPhoneField"
        )
        .onChange(of: viewModel.phoneNumber) { _, _ in viewModel.clearError(for: .phoneNumber) }
    }

    private var addressField: some View {
        PantopusTextField(
            "Street address",
            text: $viewModel.address,
            placeholder: "123 Main St",
            state: state(for: .address),
            contentType: .fullStreetAddress,
            identifier: "signUpAddressField"
        )
        .onChange(of: viewModel.address) { _, _ in viewModel.clearError(for: .address) }
    }

    private var cityStateZipRow: some View {
        HStack(alignment: .top, spacing: Spacing.s2) {
            PantopusTextField(
                "City",
                text: $viewModel.city,
                placeholder: "Cambridge",
                state: state(for: .city),
                contentType: .addressCity,
                identifier: "signUpCityField"
            )
            .onChange(of: viewModel.city) { _, _ in viewModel.clearError(for: .city) }
            PantopusTextField(
                "State",
                text: $viewModel.state,
                placeholder: "MA",
                state: state(for: .state),
                contentType: .addressState,
                identifier: "signUpStateField"
            )
            .frame(maxWidth: 80)
            .onChange(of: viewModel.state) { _, _ in viewModel.clearError(for: .state) }
            PantopusTextField(
                "ZIP",
                text: $viewModel.zipcode,
                placeholder: "02139",
                state: state(for: .zipcode),
                keyboardType: .numberPad,
                contentType: .postalCode,
                identifier: "signUpZipField"
            )
            .frame(maxWidth: 100)
            .onChange(of: viewModel.zipcode) { _, _ in viewModel.clearError(for: .zipcode) }
        }
    }

    private var inviteCodeField: some View {
        PantopusTextField(
            "Invite code (optional)",
            text: $viewModel.inviteCode,
            placeholder: "abc123",
            state: state(for: .inviteCode),
            identifier: "signUpInviteCodeField"
        )
        .textInputAutocapitalization(.never)
    }

    // MARK: - Helpers

    private func submit() {
        Task { await viewModel.submit(using: auth) }
    }

    private func state(for field: SignUpField) -> PantopusFieldState {
        if let message = visibleError(for: field) { return .error(message) }
        return .default
    }

    private func visibleError(for field: SignUpField) -> String? {
        if let active = viewModel.fieldErrors[field] { return active }
        if viewModel.hasAttemptedSubmit, let live = viewModel.validate(field) { return live }
        return nil
    }

    private static let maxDate: Date = // Today minus 18 years.
        Calendar.current.date(byAdding: .year, value: -18, to: Date()) ?? Date()

    private static let defaultDate: Date = Calendar.current.date(byAdding: .year, value: -25, to: Date()) ?? Date()
}

// MARK: - Subcomponents

/// Two-segment Personal / Business picker rendered as a token-styled
/// segmented control. Kept private to the SignUp feature for now; promote
/// if a second screen needs the same control.
private struct AccountTypePicker: View {
    @Binding var selection: SignUpAccountTypeChoice

    var body: some View {
        HStack(spacing: Spacing.s0) {
            ForEach(SignUpAccountTypeChoice.allCases) { choice in
                Button {
                    selection = choice
                } label: {
                    Text(choice.label)
                        .pantopusTextStyle(.small)
                        .fontWeight(.semibold)
                        .foregroundStyle(selection == choice
                            ? Theme.Color.appTextInverse
                            : Theme.Color.appText)
                        .frame(maxWidth: .infinity, minHeight: 36)
                        .background(selection == choice
                            ? Theme.Color.primary600
                            : Theme.Color.appSurface)
                        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                }
                .accessibilityIdentifier("signUpAccountType\(choice.label)")
            }
        }
        .padding(Spacing.s1)
        .background(Theme.Color.appSurfaceSunken)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
    }
}

/// 3-band password strength meter — matches the design's `StrengthMeter`
/// from `auth-frames.jsx:489-517`.
private struct PasswordStrengthMeter: View {
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
        VStack(alignment: .leading, spacing: Spacing.s1) {
            HStack(spacing: 6) {
                ForEach(0..<3, id: \.self) { index in
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
            Text("Min 8 chars · letters + numbers. Symbols make it stronger.")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
    }
}

/// Terms agreement checkbox with linked Terms / Privacy text.
private struct TermsCheckbox: View {
    @Binding var isOn: Bool

    var body: some View {
        Button {
            isOn.toggle()
        } label: {
            HStack(alignment: .top, spacing: Spacing.s2) {
                RoundedRectangle(cornerRadius: Radii.xs, style: .continuous)
                    .fill(isOn ? Theme.Color.primary600 : SwiftUI.Color.clear)
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.xs, style: .continuous)
                            .stroke(
                                isOn ? Theme.Color.primary600 : Theme.Color.appBorderStrong,
                                lineWidth: 1.5
                            )
                    )
                    .overlay {
                        if isOn {
                            Icon(.check, size: 11, color: Theme.Color.appTextInverse)
                        }
                    }
                    .frame(width: 20, height: 20)
                Text(termsText)
                    .pantopusTextStyle(.small)
                    .foregroundStyle(Theme.Color.appText)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: Spacing.s0)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isOn
            ? "Agreed to terms and privacy"
            : "Not agreed to terms and privacy")
    }

    private var termsText: AttributedString {
        var result = AttributedString("I agree to the Terms and Privacy Policy.")
        if let range = result.range(of: "Terms") {
            result[range].foregroundColor = Theme.Color.primary600
            result[range].underlineStyle = .single
        }
        if let range = result.range(of: "Privacy Policy") {
            result[range].foregroundColor = Theme.Color.primary600
            result[range].underlineStyle = .single
        }
        return result
    }
}

/// Inline top-of-form error banner. Mirrors `auth-frames.jsx` frame 6 —
/// red rounded card with alert-circle icon, headline, body.
struct ErrorBanner: View {
    let error: AuthError
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.s2) {
            Icon(.alertCircle, size: 18, color: Theme.Color.error)
            VStack(alignment: .leading, spacing: 2) {
                Text("Couldn't complete that")
                    .pantopusTextStyle(.small)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.error)
                Text(error.errorDescription ?? "Try again in a moment.")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.error)
            }
            Spacer(minLength: Spacing.s0)
            Button(action: onDismiss) {
                Icon(.x, size: 16, color: Theme.Color.error)
            }
            .accessibilityLabel("Dismiss error")
        }
        .padding(Spacing.s3)
        .background(Theme.Color.errorBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
    }
}

#Preview {
    SignUpView(onClose: {}, onSuccess: { _ in })
        .environment(AuthManager.previewSignedOut)
}
