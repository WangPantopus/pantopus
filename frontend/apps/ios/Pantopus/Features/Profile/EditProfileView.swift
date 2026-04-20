//
//  EditProfileView.swift
//  Pantopus
//
//  Edit Profile screen. Fields mirror `updateProfileSchema` at
//  `backend/routes/users.js:324-352`.
//

import SwiftUI

/// Edit Profile screen — presents as a sheet from the You tab.
public struct EditProfileView: View {
    @State private var viewModel = EditProfileViewModel()
    @State private var showsDiscardConfirm = false
    @Environment(\.dismiss) private var dismiss

    public init() {}

    public var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                ProgressView("Loading profile…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Theme.Color.appBg)
            case .loaded:
                loaded
            case .error(let message):
                EmptyState(
                    icon: .alertCircle,
                    headline: "Couldn't load profile",
                    subcopy: message,
                    cta: EmptyState.CTA(title: "Try again") {
                        await viewModel.refresh()
                    }
                )
            }
        }
        .background(Theme.Color.appBg)
        .task { await viewModel.load() }
        .overlay(alignment: .bottom) {
            if let toast = viewModel.toast {
                ToastView(message: toast)
                    .padding(.bottom, Spacing.s10)
                    .task {
                        try? await Task.sleep(nanoseconds: 2_000_000_000)
                        viewModel.toast = nil
                    }
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: viewModel.toast)
        .confirmationDialog(
            "Discard changes?",
            isPresented: $showsDiscardConfirm,
            titleVisibility: .visible
        ) {
            Button("Discard", role: .destructive) { dismiss() }
            Button("Keep editing", role: .cancel) {}
        } message: {
            Text("You'll lose any unsaved edits.")
        }
        .onChange(of: viewModel.shouldDismiss) { _, newValue in
            if newValue {
                viewModel.acknowledgeDismiss()
                dismiss()
            }
        }
    }

    @ViewBuilder private var loaded: some View {
        FormShell(
            title: "Edit profile",
            aggregate: viewModel.aggregate,
            isSaving: viewModel.isSaving,
            onClose: { intent in
                switch intent {
                case .dismiss: dismiss()
                case .confirmDiscard: showsDiscardConfirm = true
                }
            },
            onSave: { Task { await viewModel.save() } }
        ) {
            FormFieldGroup("About") {
                fieldView(for: .firstName, label: "First name")
                fieldView(for: .lastName, label: "Last name")
                bioField
            }
            FormFieldGroup("Contact") {
                readOnlyEmail
                fieldView(
                    for: .phoneNumber,
                    label: "Phone",
                    placeholder: "+15555550123",
                    keyboardType: .phonePad,
                    contentType: .telephoneNumber
                )
            }
            FormFieldGroup("Visibility") {
                visibilityPicker
            }
        }
        .formShakeOnChange(of: viewModel.shakeTrigger)
        .accessibilityIdentifier("editProfileShell")
    }

    @ViewBuilder
    private func fieldView(
        for key: EditProfileField,
        label: String,
        placeholder: String = "",
        keyboardType: UIKeyboardType = .default,
        contentType: UITextContentType? = nil
    ) -> some View {
        let snapshot = viewModel.fields[key] ?? FormFieldState(id: key.rawValue, originalValue: "")
        let binding = Binding<String>(
            get: { snapshot.value },
            set: { viewModel.update(key, to: $0) }
        )
        PantopusTextField(
            label,
            text: binding,
            placeholder: placeholder,
            state: fieldState(for: snapshot),
            keyboardType: keyboardType,
            contentType: contentType
        )
        .accessibilityIdentifier("field_\(key.rawValue)")
    }

    @ViewBuilder private var bioField: some View {
        let snapshot = viewModel.fields[.bio] ?? FormFieldState(id: "bio", originalValue: "")
        VStack(alignment: .leading, spacing: Spacing.s1) {
            Text("Bio")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            TextEditor(text: Binding(
                get: { snapshot.value },
                set: { viewModel.update(.bio, to: $0) }
            ))
            .frame(minHeight: 96)
            .padding(Spacing.s2)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(
                        snapshot.error != nil ? Theme.Color.error : Theme.Color.appBorder,
                        lineWidth: 1
                    )
            )
            if let error = snapshot.error {
                Text(error)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.error)
            }
        }
        .accessibilityIdentifier("field_bio")
    }

    @ViewBuilder private var readOnlyEmail: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            Text("Email")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            HStack {
                Text(viewModel.email)
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Spacer()
                if viewModel.emailVerified {
                    Icon(.check, size: 16, color: Theme.Color.success)
                }
            }
            .padding(Spacing.s3)
            .frame(minHeight: 44)
            .background(Theme.Color.appSurfaceSunken)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .accessibilityLabel("Email \(viewModel.email), read only")
        }
    }

    @ViewBuilder private var visibilityPicker: some View {
        let snapshot = viewModel.fields[.profileVisibility] ?? FormFieldState(
            id: "profileVisibility", originalValue: "public"
        )
        VStack(alignment: .leading, spacing: Spacing.s1) {
            Text("Profile visibility")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            Picker(
                "Profile visibility",
                selection: Binding(
                    get: { snapshot.value },
                    set: { viewModel.update(.profileVisibility, to: $0) }
                )
            ) {
                Text("Public").tag("public")
                Text("Registered").tag("registered")
                Text("Private").tag("private")
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("field_profileVisibility")
        }
    }

    private func fieldState(for snapshot: FormFieldState) -> PantopusFieldState {
        if let error = snapshot.error { return .error(error) }
        if snapshot.touched, snapshot.isDirty { return .valid }
        return .default
    }
}

#Preview {
    EditProfileView()
}
