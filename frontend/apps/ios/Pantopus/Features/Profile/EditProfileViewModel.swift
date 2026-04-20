//
//  EditProfileViewModel.swift
//  Pantopus
//
//  Fetches `GET /api/users/profile` (`backend/routes/users.js:1427`) and
//  submits `PATCH /api/users/profile` (`backend/routes/users.js:1503`).
//  Every editable field is defined in `updateProfileSchema`
//  (`backend/routes/users.js:324-352`).
//

import Foundation
import Observation

/// Stable identifiers for the Edit Profile fields.
public enum EditProfileField: String, CaseIterable, Sendable {
    case firstName
    case lastName
    case bio
    case phoneNumber
    case profileVisibility
}

/// Observed state for the Edit Profile screen.
public enum EditProfileState: Sendable {
    case loading
    case loaded
    case error(String)
}

/// ViewModel backing `EditProfileView`.
@Observable
@MainActor
final class EditProfileViewModel {
    private(set) var state: EditProfileState = .loading
    /// Email is read-only; captured so the view can render it.
    private(set) var email: String = ""
    /// True while the verified flag on the fetched profile is set.
    private(set) var emailVerified: Bool = false

    /// Field states keyed by `EditProfileField`.
    var fields: [EditProfileField: FormFieldState] = [:]
    /// Busy flag for the Save CTA.
    private(set) var isSaving: Bool = false
    /// Toast surfaced by the view after a successful PATCH or failure.
    var toast: ToastMessage?
    /// Increments to trigger the first-invalid shake on submit.
    private(set) var shakeTrigger: Int = 0
    /// Set when a successful save should pop the screen.
    private(set) var shouldDismiss: Bool = false

    private let api: APIClient

    init(api: APIClient = .shared) {
        self.api = api
        for field in EditProfileField.allCases {
            fields[field] = FormFieldState(id: field.rawValue, originalValue: "")
        }
    }

    /// Initial load; no-op when already loaded.
    func load() async {
        if case .loaded = state { return }
        state = .loading
        do {
            let response: ProfileResponse = try await api.request(UsersEndpoints.profile())
            hydrate(from: response.user)
            state = .loaded
        } catch {
            state = .error((error as? APIError)?.errorDescription ?? "Couldn't load profile.")
        }
    }

    /// Retry after an error.
    func refresh() async { await load() }

    /// Update a field's value and re-run its validator.
    func update(_ field: EditProfileField, to value: String) {
        guard var snapshot = fields[field] else { return }
        snapshot.value = value
        snapshot.touched = true
        snapshot.error = validator(for: field).validate(value)
        fields[field] = snapshot
    }

    /// Current aggregate dirty + validity.
    var aggregate: FormAggregate {
        FormAggregate(fields: EditProfileField.allCases.compactMap { fields[$0] })
    }

    /// Runs all validators and returns the first failing field id, if any.
    @discardableResult
    func validateAll() -> EditProfileField? {
        var firstInvalid: EditProfileField?
        for field in EditProfileField.allCases {
            guard var snapshot = fields[field] else { continue }
            let message = validator(for: field).validate(snapshot.value)
            snapshot.error = message
            snapshot.touched = true
            fields[field] = snapshot
            if firstInvalid == nil, message != nil { firstInvalid = field }
        }
        return firstInvalid
    }

    /// Submit the PATCH. Returns true on success.
    @discardableResult
    func save() async -> Bool {
        if let invalid = validateAll() {
            shakeTrigger &+= 1
            toast = ToastMessage(text: "Fix the highlighted field.", kind: .error)
            _ = invalid
            return false
        }
        guard aggregate.isDirty else { return false }
        isSaving = true
        defer { isSaving = false }
        do {
            let response: ProfileUpdateResponse = try await api.request(
                UsersEndpoints.updateProfile(buildRequest())
            )
            hydrate(from: response.user)
            toast = ToastMessage(text: "Profile updated.", kind: .success)
            shouldDismiss = true
            return true
        } catch {
            toast = ToastMessage(
                text: (error as? APIError)?.errorDescription ?? "Couldn't save profile.",
                kind: .error
            )
            return false
        }
    }

    /// Invoked by the view when the dismiss should take effect.
    func acknowledgeDismiss() { shouldDismiss = false }

    // MARK: - Private

    private func hydrate(from profile: UserProfile) {
        email = profile.email
        emailVerified = profile.verified
        seed(.firstName, profile.firstName)
        seed(.lastName, profile.lastName)
        seed(.bio, profile.bio ?? "")
        seed(.phoneNumber, profile.phoneNumber ?? "")
        seed(.profileVisibility, profile.profileVisibility ?? "public")
    }

    private func seed(_ field: EditProfileField, _ value: String) {
        var snapshot = FormFieldState(id: field.rawValue, originalValue: value)
        snapshot.error = validator(for: field).validate(value)
        fields[field] = snapshot
    }

    private func validator(for field: EditProfileField) -> FormValidator {
        switch field {
        case .firstName:
            return FormValidator.all([.required("First name"), .maxLength(255)])
        case .lastName:
            return FormValidator.all([.required("Last name"), .maxLength(255)])
        case .bio:
            return FormValidator.maxLength(2000)
        case .phoneNumber:
            return FormValidator.e164Phone()
        case .profileVisibility:
            return FormValidator { value in
                ["public", "registered", "private"].contains(value)
                    ? nil
                    : "Pick a visibility option."
            }
        }
    }

    private func buildRequest() -> ProfileUpdateRequest {
        var update = ProfileUpdateRequest()
        for field in EditProfileField.allCases {
            guard let snapshot = fields[field], snapshot.isDirty else { continue }
            let trimmed = snapshot.value.trimmingCharacters(in: .whitespacesAndNewlines)
            switch field {
            case .firstName: update.firstName = trimmed
            case .lastName: update.lastName = trimmed
            case .bio: update.bio = trimmed
            case .phoneNumber: update.phoneNumber = trimmed.isEmpty ? nil : trimmed
            case .profileVisibility: update.profileVisibility = trimmed
            }
        }
        return update
    }
}
