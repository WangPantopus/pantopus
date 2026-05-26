//
//  PasswordChangeView.swift
//  Pantopus
//
//  P8 / T6.2c — Settings → Password sub-route. Form-archetype screen
//  with current/new/confirm fields. Posts to `/api/users/password`
//  via `PasswordChangeViewModel`.
//

import SwiftUI

public struct PasswordChangeView: View {
    @State private var viewModel: PasswordChangeViewModel
    private let onBack: @MainActor () -> Void

    init(
        viewModel: PasswordChangeViewModel = PasswordChangeViewModel(),
        onBack: @escaping @MainActor () -> Void
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onBack = onBack
    }

    public var body: some View {
        FormShell(
            title: "Change password",
            rightActionLabel: "Save",
            isValid: viewModel.isValid,
            isDirty: viewModel.isDirty,
            isSaving: viewModel.isSaving,
            onClose: onBack,
            onCommit: { Task { await viewModel.save() } },
            content: {
                fieldsSection
                helperSection
            }
        )
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("passwordChange")
        .task { await viewModel.load() }
        .overlay(alignment: .bottom) {
            if let toast = viewModel.toast {
                ToastView(
                    message: ToastMessage(
                        text: toast,
                        kind: toast == "Password updated" ? .success : .error
                    )
                )
                .padding(.bottom, Spacing.s10)
                .accessibilityIdentifier("passwordChangeToast")
                .transition(.opacity)
            }
        }
        .pantopusAnimation(.componentState, value: viewModel.toast)
        .onChange(of: viewModel.shouldDismiss) { _, newValue in
            guard newValue else { return }
            viewModel.acknowledgeDismiss()
            Task {
                try? await Task.sleep(nanoseconds: 700_000_000)
                onBack()
            }
        }
    }

    private var fieldsSection: some View {
        FormFieldGroup(viewModel.requiresCurrent ? "Update password" : "Set a password") {
            if viewModel.requiresCurrent {
                secureField(.current, label: "Current password", contentType: .password)
            }
            secureField(.new, label: "New password", contentType: .newPassword)
            secureField(.confirm, label: "Confirm new password", contentType: .newPassword)
        }
    }

    private var helperSection: some View {
        Text("Use at least \(PasswordChangeViewModel.minPasswordLength) characters. A mix of letters, numbers, and symbols is strongest.")
            .pantopusTextStyle(.caption)
            .foregroundStyle(Theme.Color.appTextSecondary)
            .padding(.horizontal, Spacing.s4)
    }

    @ViewBuilder
    private func secureField(
        _ key: PasswordChangeViewModel.FieldKey,
        label: String,
        contentType: UITextContentType?
    ) -> some View {
        let snapshot = viewModel.fields[key] ?? FormFieldState(id: key.rawValue, originalValue: "")
        let binding = Binding<String>(
            get: { snapshot.value },
            set: { viewModel.update(key, to: $0) }
        )
        PantopusTextField(
            label,
            text: binding,
            placeholder: "",
            state: fieldState(snapshot),
            isSecure: true,
            contentType: contentType,
            identifier: "field_\(key.rawValue)"
        )
    }

    private func fieldState(_ snapshot: FormFieldState) -> PantopusFieldState {
        if let error = snapshot.error { return .error(error) }
        return .default
    }
}

#Preview {
    NavigationStack {
        PasswordChangeView {}
    }
}
