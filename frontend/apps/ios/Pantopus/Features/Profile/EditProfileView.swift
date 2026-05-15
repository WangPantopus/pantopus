//
//  EditProfileView.swift
//  Pantopus
//
//  Edit Profile screen. Fields mirror `updateProfileSchema` at
//  `backend/routes/users.js:324-351`. Section ordering matches the P10
//  design: About, Contact, Address, Social, Visibility.
//

import SwiftUI

/// Edit Profile screen — presents as a sheet from the You tab.
public struct EditProfileView: View {
    @State private var viewModel = EditProfileViewModel()
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
            case let .error(message):
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
        .onAppear { Analytics.track(.screenEditProfileViewed) }
        .overlay(alignment: .bottom) {
            if let toast = viewModel.toast {
                ToastView(message: toast)
                    .padding(.bottom, Spacing.s10)
                    .task {
                        try? await Task.sleep(nanoseconds: 2_000_000_000)
                        viewModel.toast = nil
                    }
                    .transition(.opacity)
                    .accessibilityIdentifier("editProfileToast")
            }
        }
        .animation(.easeInOut(duration: 0.2), value: viewModel.toast)
        .onChange(of: viewModel.shouldDismiss) { _, newValue in
            // Hold the success toast visible briefly before popping so the
            // user actually sees the confirmation.
            guard newValue else { return }
            viewModel.acknowledgeDismiss()
            Task {
                try? await Task.sleep(nanoseconds: 700_000_000)
                dismiss()
            }
        }
    }

    private var loaded: some View {
        FormShell(
            title: "Edit profile",
            isValid: viewModel.isValid,
            isDirty: viewModel.isDirty,
            isSaving: viewModel.isSaving,
            onClose: { dismiss() },
            onCommit: { Task { await viewModel.save() } },
            content: {
                aboutSection
                contactSection
                addressSection
                socialSection
                visibilitySection
            }
        )
        .formShakeOnChange(of: viewModel.shakeTrigger)
        .accessibilityIdentifier("editProfileShell")
    }

    // MARK: - Sections

    private var aboutSection: some View {
        // Note: the design also calls for an avatar upload (tap to
        // replace). `updateProfileSchema` exposes no avatar field, so
        // the affordance is intentionally omitted until the backend
        // accepts an avatar key on PATCH /api/users/profile.
        FormFieldGroup("About") {
            textField(.firstName, label: "First name")
            textField(.middleName, label: "Middle name (optional)")
            textField(.lastName, label: "Last name")
            taglineField
            bioField
        }
    }

    private var contactSection: some View {
        FormFieldGroup("Contact") {
            // Note: the design allows editing email when `verified ==
            // false`. `updateProfileSchema` exposes no `email` key, so
            // the field is read-only until the backend adds it.
            readOnlyEmail
            textField(
                .phoneNumber,
                label: "Phone (optional)",
                placeholder: "+15555550123",
                keyboardType: .phonePad,
                contentType: .telephoneNumber
            )
            dateOfBirthField
        }
    }

    private var addressSection: some View {
        FormFieldGroup("Address") {
            textField(
                .address,
                label: "Street",
                placeholder: "123 Main St",
                contentType: .streetAddressLine1
            )
            textField(
                .city,
                label: "City",
                contentType: .addressCity
            )
            HStack(alignment: .top, spacing: Spacing.s2) {
                textField(.state, label: "State", contentType: .addressState)
                textField(.zipcode, label: "Zip", contentType: .postalCode)
            }
        }
    }

    private var socialSection: some View {
        FormFieldGroup("Social") {
            textField(
                .website,
                label: "Website",
                placeholder: "https://example.com",
                keyboardType: .URL,
                contentType: .URL
            )
            textField(.linkedin, label: "LinkedIn", placeholder: "https://linkedin.com/in/…", keyboardType: .URL)
            textField(.twitter, label: "Twitter / X", placeholder: "https://x.com/…", keyboardType: .URL)
            textField(.instagram, label: "Instagram", placeholder: "https://instagram.com/…", keyboardType: .URL)
            textField(.facebook, label: "Facebook", placeholder: "https://facebook.com/…", keyboardType: .URL)
        }
    }

    private var visibilitySection: some View {
        // Note: the design splits visibility into a
        // `profile_visibility_public` boolean and a
        // `show_in_neighbor_discovery` toggle. The schema only has the
        // 3-way `profileVisibility` enum today (public / registered /
        // private) and no neighbor-discovery key, so we render the
        // enum picker and omit the toggle until the backend adds it.
        FormFieldGroup("Visibility") {
            visibilityPicker
        }
    }

    // MARK: - Field builders

    @ViewBuilder
    private func textField(
        _ key: EditProfileField,
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
            contentType: contentType,
            identifier: "field_\(key.rawValue)"
        )
    }

    private var taglineField: some View {
        textField(
            .tagline,
            label: "Tagline (optional)",
            placeholder: "A short headline"
        )
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

    @ViewBuilder private var dateOfBirthField: some View {
        let snapshot = viewModel.fields[.dateOfBirth]
            ?? FormFieldState(id: "dateOfBirth", originalValue: "")
        let dateBinding = Binding<Date>(
            get: { Self.parseISO(snapshot.value) ?? Date() },
            set: { viewModel.update(.dateOfBirth, to: Self.formatISO($0)) }
        )
        VStack(alignment: .leading, spacing: Spacing.s1) {
            HStack {
                Text("Date of birth (optional)")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Spacer()
                if !snapshot.value.isEmpty {
                    Button("Clear") { viewModel.update(.dateOfBirth, to: "") }
                        .font(Theme.Font.role(.caption))
                        .foregroundStyle(Theme.Color.primary600)
                        .accessibilityIdentifier("field_dateOfBirth_clear")
                }
            }
            DatePicker(
                "Date of birth",
                selection: dateBinding,
                in: ...Date(),
                displayedComponents: .date
            )
            .labelsHidden()
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Spacing.s3)
            .frame(minHeight: 44)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(
                        snapshot.error != nil ? Theme.Color.error : Theme.Color.appBorder,
                        lineWidth: 1
                    )
            )
            .accessibilityIdentifier("field_dateOfBirth")
            if let error = snapshot.error {
                Text(error)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.error)
            }
        }
    }

    private var readOnlyEmail: some View {
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
            .accessibilityIdentifier("field_email")
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

    // MARK: - Date helpers

    /// Build a fresh ISO `yyyy-MM-dd` formatter. Constructed per-call so
    /// no shared mutable state crosses actor boundaries — DateFormatter
    /// would otherwise need `nonisolated(unsafe)` under strict
    /// concurrency.
    private static func makeISOFormatter() -> DateFormatter {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .iso8601)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }

    private static func parseISO(_ value: String) -> Date? {
        let trimmed = value.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return nil }
        return makeISOFormatter().date(from: trimmed)
    }

    private static func formatISO(_ date: Date) -> String {
        makeISOFormatter().string(from: date)
    }
}

#Preview {
    EditProfileView()
}
