//
//  EditProfileViewModel.swift
//  Pantopus
//
//  Fetches `GET /api/users/profile` (`backend/routes/users.js:1427`) and
//  submits `PATCH /api/users/profile` (`backend/routes/users.js:1503`).
//  Every editable field is defined in `updateProfileSchema`
//  (`backend/routes/users.js:324-351`) and is mirrored 1:1 below.
//
//  TODO(backend): the Edit Profile design also calls for an avatar
//  upload, an editable email when unverified, and boolean toggles
//  `profile_visibility_public` + `show_in_neighbor_discovery`. None of
//  those exist in `updateProfileSchema`, so per the P10 rules we omit
//  them here and leave this comment for future backend work.
//

import Foundation
import Observation

/// Stable identifiers for every editable field in the Edit Profile form.
/// Order mirrors `updateProfileSchema` declaration order so the form layout
/// reads top-down in the same order as the backend contract.
public enum EditProfileField: String, CaseIterable, Sendable {
    // About
    case firstName
    case middleName
    case lastName
    case bio
    case tagline

    // Contact
    case phoneNumber
    case dateOfBirth

    // Address
    case address
    case city
    case state
    case zipcode

    // Social links — backend stores these in the `social_links` jsonb
    // column but accepts them as flat keys on PATCH.
    case website
    case linkedin
    case twitter
    case instagram
    case facebook

    /// Visibility
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
    func refresh() async {
        await load()
    }

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

    /// Convenience exposes for `FormShell` so the view doesn't have to
    /// thread `aggregate.isValid` / `aggregate.isDirty` through the call
    /// site.
    var isValid: Bool {
        aggregate.isValid
    }

    var isDirty: Bool {
        aggregate.isDirty
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
        if let invalidField = validateAll() {
            shakeTrigger &+= 1
            toast = ToastMessage(text: "Fix the highlighted field.", kind: .error)
            Analytics.track(.formEditProfileValidationError(field: invalidField.rawValue))
            return false
        }
        guard aggregate.isDirty else { return false }
        if !NetworkMonitor.shared.isOnline {
            // P15: don't silently queue. Surface the error inline.
            toast = ToastMessage(
                text: "You're offline. Try again when you're back online.",
                kind: .error
            )
            Analytics.track(.formEditProfileSubmit(result: .error))
            return false
        }
        isSaving = true
        defer { isSaving = false }
        do {
            let response: ProfileUpdateResponse = try await api.request(
                UsersEndpoints.updateProfile(buildRequest())
            )
            hydrate(from: response.user)
            toast = ToastMessage(text: "Profile updated.", kind: .success)
            shouldDismiss = true
            Analytics.track(.formEditProfileSubmit(result: .success))
            return true
        } catch {
            toast = ToastMessage(
                text: (error as? APIError)?.errorDescription ?? "Couldn't save profile.",
                kind: .error
            )
            Analytics.track(.formEditProfileSubmit(result: .error))
            return false
        }
    }

    /// Invoked by the view when the dismiss should take effect.
    func acknowledgeDismiss() {
        shouldDismiss = false
    }

    // MARK: - Private

    private func hydrate(from profile: UserProfile) {
        email = profile.email
        emailVerified = profile.verified
        seed(.firstName, profile.firstName)
        seed(.middleName, profile.middleName ?? "")
        seed(.lastName, profile.lastName)
        seed(.bio, profile.bio ?? "")
        seed(.tagline, profile.tagline ?? "")
        seed(.phoneNumber, profile.phoneNumber ?? "")
        seed(.dateOfBirth, profile.dateOfBirth ?? "")
        seed(.address, profile.address ?? "")
        seed(.city, profile.city ?? "")
        seed(.state, profile.state ?? "")
        seed(.zipcode, profile.zipcode ?? "")
        seed(.website, profile.socialLinks?.website ?? "")
        seed(.linkedin, profile.socialLinks?.linkedin ?? "")
        seed(.twitter, profile.socialLinks?.twitter ?? "")
        seed(.instagram, profile.socialLinks?.instagram ?? "")
        seed(.facebook, profile.socialLinks?.facebook ?? "")
        seed(.profileVisibility, profile.profileVisibility ?? "public")
    }

    private func seed(_ field: EditProfileField, _ value: String) {
        var snapshot = FormFieldState(id: field.rawValue, originalValue: value)
        snapshot.error = validator(for: field).validate(value)
        fields[field] = snapshot
    }

    /// Per-field validator, looked up via `Self.validators` so this stays
    /// well below the SwiftLint cyclomatic-complexity ceiling.
    private func validator(for field: EditProfileField) -> FormValidator {
        Self.validators[field] ?? FormValidator { _ in nil }
    }

    /// Static validator table. Each entry mirrors the corresponding Joi
    /// rule in `updateProfileSchema` (`backend/routes/users.js:324-351`).
    private static let validators: [EditProfileField: FormValidator] = [
        // Required name fields — Joi `.string().min(1).max(255)`.
        .firstName: .all([.required("First name"), .maxLength(255)]),
        .lastName: .all([.required("Last name"), .maxLength(255)]),
        // Optional name fields with length bounds.
        .middleName: .optionalLength("Middle name", min: 1, max: 255),
        // `.allow('', null)` text fields — only an upper bound applies.
        .bio: .maxLength(2000),
        .tagline: .maxLength(255),
        // E.164 phone (optional). Empty allowed at the validator layer
        // but skipped from the PATCH body (see fieldAllowsEmpty).
        .phoneNumber: .e164Phone(),
        // ISO-8601 date or empty.
        .dateOfBirth: .isoDateOrEmpty(),
        // Optional address fields — Joi enforces min/max only when set.
        .address: .optionalLength("Address", min: 5, max: 255),
        .city: .optionalLength("City", min: 2, max: 100),
        .state: .optionalLength("State", min: 2, max: 50),
        .zipcode: .optionalLength("Zipcode", min: 3, max: 20),
        // Social links — Joi `urlOrEmpty`.
        .website: .urlOrEmpty(),
        .linkedin: .urlOrEmpty(),
        .twitter: .urlOrEmpty(),
        .instagram: .urlOrEmpty(),
        .facebook: .urlOrEmpty(),
        // Visibility enum — restrict to the three schema values.
        .profileVisibility: FormValidator { value in
            ["public", "registered", "private"].contains(value)
                ? nil
                : "Pick a visibility option."
        }
    ]

    /// Whether the schema explicitly allows an empty / null payload for
    /// the given field — see the Joi declarations at
    /// `backend/routes/users.js:324-351`.
    private static let allowsEmpty: Set<EditProfileField> = [
        .middleName, .bio, .tagline, .dateOfBirth,
        .website, .linkedin, .twitter, .instagram, .facebook
    ]

    // swiftlint:disable cyclomatic_complexity

    /// Assemble a PATCH body with only the dirty fields. Empty strings are
    /// included for fields whose schema entry has `.allow('', null)` (so
    /// the user can clear them); fields without that allowance are
    /// skipped when empty so we don't send a value the server will reject.
    ///
    /// The 17-case switch below mirrors the schema 1:1; cyclomatic
    /// complexity is intentionally high and adding indirection would only
    /// hide the mapping.
    private func buildRequest() -> ProfileUpdateRequest {
        var update = ProfileUpdateRequest()
        for field in EditProfileField.allCases {
            guard let snapshot = fields[field], snapshot.isDirty else { continue }
            let trimmed = snapshot.value.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty && !Self.allowsEmpty.contains(field) { continue }
            switch field {
            case .firstName: update.firstName = trimmed
            case .middleName: update.middleName = trimmed
            case .lastName: update.lastName = trimmed
            case .bio: update.bio = trimmed
            case .tagline: update.tagline = trimmed
            case .phoneNumber: update.phoneNumber = trimmed
            case .dateOfBirth: update.dateOfBirth = trimmed
            case .address: update.address = trimmed
            case .city: update.city = trimmed
            case .state: update.state = trimmed
            case .zipcode: update.zipcode = trimmed
            case .website: update.website = trimmed
            case .linkedin: update.linkedin = trimmed
            case .twitter: update.twitter = trimmed
            case .instagram: update.instagram = trimmed
            case .facebook: update.facebook = trimmed
            case .profileVisibility: update.profileVisibility = trimmed
            }
        }
        return update
    }
    // swiftlint:enable cyclomatic_complexity
}
