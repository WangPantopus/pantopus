//
//  PantopusTextField.swift
//  Pantopus
//
//  44pt text field with the four design-system states: default, focus,
//  valid, error. Reads label / error to VoiceOver.
//

import SwiftUI

/// Validation state for `PantopusTextField`.
public enum PantopusFieldState: Sendable, Equatable {
    case `default`
    case valid
    /// Carries a helper string rendered beneath the field.
    case error(String)
}

/// Token-styled text field with validation visuals.
@MainActor
public struct PantopusTextField: View {
    @Binding private var text: String
    private let label: String
    private let placeholder: String
    private let state: PantopusFieldState
    private let isSecure: Bool
    private let keyboardType: UIKeyboardType
    private let contentType: UITextContentType?

    @FocusState private var isFocused: Bool

    public init(
        _ label: String,
        text: Binding<String>,
        placeholder: String = "",
        state: PantopusFieldState = .default,
        isSecure: Bool = false,
        keyboardType: UIKeyboardType = .default,
        contentType: UITextContentType? = nil
    ) {
        self.label = label
        self._text = text
        self.placeholder = placeholder
        self.state = state
        self.isSecure = isSecure
        self.keyboardType = keyboardType
        self.contentType = contentType
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            Text(label)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)

            HStack(spacing: Spacing.s2) {
                input
                trailingIcon
            }
            .padding(.horizontal, Spacing.s3)
            .frame(minHeight: 44)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(borderColor, lineWidth: isFocused ? 2 : 1)
            )

            if case .error(let message) = state {
                Text(message)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.error)
                    .accessibilityLabel("Error: \(message)")
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(a11yLabel)
    }

    @ViewBuilder private var input: some View {
        if isSecure {
            SecureField(placeholder, text: $text)
                .textContentType(contentType)
                .focused($isFocused)
        } else {
            TextField(placeholder, text: $text)
                .textContentType(contentType)
                .keyboardType(keyboardType)
                .focused($isFocused)
        }
    }

    @ViewBuilder private var trailingIcon: some View {
        switch state {
        case .valid: Icon(.check, size: 18, color: Theme.Color.success)
        case .error: Icon(.alertCircle, size: 18, color: Theme.Color.error)
        case .default: EmptyView()
        }
    }

    private var borderColor: Color {
        switch state {
        case .error: Theme.Color.error
        case .valid: Theme.Color.success
        case .default: isFocused ? Theme.Color.primary600 : Theme.Color.appBorder
        }
    }

    private var a11yLabel: String {
        switch state {
        case .error(let msg): "\(label), error: \(msg)"
        case .valid: "\(label), valid"
        case .default: label
        }
    }
}

#Preview("All states") {
    @Previewable @State var plain = ""
    @Previewable @State var valid = "alice@pantopus.app"
    @Previewable @State var errored = "not-an-email"
    return VStack(spacing: Spacing.s3) {
        PantopusTextField("Email", text: $plain, placeholder: "you@pantopus.app")
        PantopusTextField("Email", text: $valid, state: .valid)
        PantopusTextField(
            "Email",
            text: $errored,
            state: .error("Please enter a valid email address")
        )
    }
    .padding()
    .background(Theme.Color.appBg)
}
