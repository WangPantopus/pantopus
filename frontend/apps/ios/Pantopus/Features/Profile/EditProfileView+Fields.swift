//
//  EditProfileView+Fields.swift
//  Pantopus
//
//  Field groups and input builders for `EditProfileView`.
//

import SwiftUI

extension EditProfileView {
    var aboutSection: some View {
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

    var contactSection: some View {
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

    var addressSection: some View {
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

    var socialSection: some View {
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

    var visibilitySection: some View {
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

    @ViewBuilder
    func textField(
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
            isRequired: isRequiredField(key),
            isDirty: snapshot.isDirty,
            keyboardType: keyboardType,
            contentType: contentType,
            identifier: "field_\(key.rawValue)"
        )
    }

    var taglineField: some View {
        textField(
            .tagline,
            label: "Tagline (optional)",
            placeholder: "A short headline"
        )
    }

    @ViewBuilder var bioField: some View {
        let snapshot = viewModel.fields[.bio] ?? FormFieldState(id: "bio", originalValue: "")
        VStack(alignment: .leading, spacing: Spacing.s1) {
            EditProfileFieldLabel("Bio", dirty: snapshot.isDirty)
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

    @ViewBuilder var dateOfBirthField: some View {
        let snapshot = viewModel.fields[.dateOfBirth]
            ?? FormFieldState(id: "dateOfBirth", originalValue: "")
        let dateBinding = Binding<Date>(
            get: { Self.parseISO(snapshot.value) ?? Date() },
            set: { viewModel.update(.dateOfBirth, to: Self.formatISO($0)) }
        )
        VStack(alignment: .leading, spacing: Spacing.s1) {
            HStack {
                EditProfileFieldLabel("Date of birth (optional)", dirty: snapshot.isDirty)
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

    var readOnlyEmail: some View {
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

    @ViewBuilder var visibilityPicker: some View {
        let snapshot = viewModel.fields[.profileVisibility] ?? FormFieldState(
            id: "profileVisibility", originalValue: "public"
        )
        VStack(alignment: .leading, spacing: Spacing.s1) {
            EditProfileFieldLabel("Profile visibility", dirty: snapshot.isDirty)
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

    func fieldState(for snapshot: FormFieldState) -> PantopusFieldState {
        if let error = snapshot.error { return .error(error) }
        if snapshot.touched, snapshot.isDirty { return .valid }
        return .default
    }

    func isRequiredField(_ field: EditProfileField) -> Bool {
        field == .firstName || field == .lastName
    }

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
