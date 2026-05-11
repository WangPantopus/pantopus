//
//  InviteOwnerFormView.swift
//  Pantopus
//
//  P19 FrameInvite — Email + Phone field groups inside the FormShell.
//  Backend doesn't accept the Role / Personal Note fields the design
//  draws, so they're omitted (see PR description).
//
// swiftlint:disable multiple_closures_with_trailing_closure

import SwiftUI

/// Invite-an-owner form for a given home.
@MainActor
struct InviteOwnerFormView: View {
    @State private var viewModel: InviteOwnerFormViewModel
    private let onClose: @MainActor () -> Void

    init(
        homeId: String,
        currentUserEmail: String,
        api: APIClient = .shared,
        onClose: @escaping @MainActor () -> Void
    ) {
        _viewModel = State(initialValue: InviteOwnerFormViewModel(
            homeId: homeId,
            currentUserEmail: currentUserEmail,
            api: api
        ))
        self.onClose = onClose
    }

    var body: some View {
        FormShell(
            title: "Invite owner",
            rightActionLabel: "Send",
            isValid: viewModel.isValid,
            isDirty: viewModel.isDirty,
            isSaving: viewModel.isSaving,
            onClose: onClose,
            onCommit: { Task { await viewModel.submit() } }
        ) {
            FormFieldGroup("Owner details") {
                PantopusTextField(
                    "Email",
                    text: bind(.email),
                    placeholder: "name@example.com",
                    state: fieldState(.email),
                    keyboardType: .emailAddress,
                    contentType: .emailAddress,
                    identifier: "inviteOwnerEmailField"
                )
                PantopusTextField(
                    "Phone (optional)",
                    text: bind(.phone),
                    placeholder: "+1 555 555 0123",
                    state: fieldState(.phone),
                    keyboardType: .phonePad,
                    contentType: .telephoneNumber,
                    identifier: "inviteOwnerPhoneField"
                )
            }
        }
        .formShakeOnChange(of: viewModel.shakeTrigger)
        .overlay(alignment: .bottom) { toastOverlay }
        .onChange(of: viewModel.shouldDismiss) { _, dismiss in
            if dismiss {
                viewModel.acknowledgeDismiss()
                onClose()
            }
        }
    }

    @ViewBuilder private var toastOverlay: some View {
        if let toast = viewModel.toast {
            ToastView(message: toast)
                .padding(.bottom, Spacing.s8)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .task(id: toast) {
                    try? await Task.sleep(nanoseconds: 2_500_000_000)
                    viewModel.toast = nil
                }
        }
    }

    private func bind(_ field: InviteOwnerField) -> Binding<String> {
        Binding(
            get: { viewModel.fields[field]?.value ?? "" },
            set: { viewModel.update(field, to: $0) }
        )
    }

    private func fieldState(_ field: InviteOwnerField) -> PantopusFieldState {
        guard let snapshot = viewModel.fields[field], snapshot.touched else { return .default }
        if let error = snapshot.error { return .error(error) }
        return snapshot.value.trimmingCharacters(in: .whitespaces).isEmpty ? .default : .valid
    }
}

#Preview {
    InviteOwnerFormView(
        homeId: "home-preview",
        currentUserEmail: "me@example.com"
    ) {}
}
