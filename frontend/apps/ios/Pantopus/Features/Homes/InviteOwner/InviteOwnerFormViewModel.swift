//
//  InviteOwnerFormViewModel.swift
//  Pantopus
//
//  POSTs `/api/homes/:id/owners/invite`
//  (`backend/routes/homeOwnership.js:1376`). Schema: `inviteOwnerSchema`
//  (line 66) — only `email` + `phone` + `user_id` + `fast_track` are
//  accepted. The Form design also drew Role + Personal Note fields;
//  neither has a backend counterpart today, so they're omitted here
//  with a TODO. See PR description for the discrepancy.
//

import Foundation
import Observation

/// Stable identifiers for every editable Invite Owner field.
public enum InviteOwnerField: String, CaseIterable, Sendable {
    case email
    case phone
}

/// Render state for the Invite Owner form.
public enum InviteOwnerFormState: Sendable, Equatable {
    case editing
    case error(String)
}

/// ViewModel backing `InviteOwnerFormView`.
@Observable
@MainActor
final class InviteOwnerFormViewModel {
    private(set) var state: InviteOwnerFormState = .editing
    var fields: [InviteOwnerField: FormFieldState] = [:]
    private(set) var isSaving: Bool = false
    var toast: ToastMessage?
    private(set) var shakeTrigger: Int = 0
    /// Set true when the form should pop after a successful invite.
    private(set) var shouldDismiss: Bool = false

    private let homeId: String
    private let currentUserEmail: String
    private let api: APIClient

    init(homeId: String, currentUserEmail: String, api: APIClient = .shared) {
        self.homeId = homeId
        self.currentUserEmail = currentUserEmail
        self.api = api
        for field in InviteOwnerField.allCases {
            fields[field] = FormFieldState(id: field.rawValue, originalValue: "")
        }
    }

    /// Update a field's value and re-run its validator.
    func update(_ field: InviteOwnerField, to value: String) {
        guard var snapshot = fields[field] else { return }
        snapshot.value = value
        snapshot.touched = true
        snapshot.error = validator(for: field).validate(value)
        fields[field] = snapshot
    }

    /// Aggregate dirty + validity snapshot.
    var aggregate: FormAggregate {
        FormAggregate(fields: InviteOwnerField.allCases.compactMap { fields[$0] })
    }

    var isValid: Bool { aggregate.isValid }
    var isDirty: Bool { aggregate.isDirty }

    /// Run all validators. Returns the first invalid field id.
    @discardableResult
    func validateAll() -> InviteOwnerField? {
        var firstInvalid: InviteOwnerField?
        for field in InviteOwnerField.allCases {
            guard var snapshot = fields[field] else { continue }
            let message = validator(for: field).validate(snapshot.value)
            snapshot.error = message
            snapshot.touched = true
            fields[field] = snapshot
            if firstInvalid == nil, message != nil { firstInvalid = field }
        }
        return firstInvalid
    }

    /// Submit the invite. Returns true on success.
    @discardableResult
    func submit() async -> Bool {
        if validateAll() != nil {
            shakeTrigger &+= 1
            toast = ToastMessage(text: "Fix the highlighted field.", kind: .error)
            return false
        }
        if !NetworkMonitor.shared.isOnline {
            toast = ToastMessage(
                text: "You're offline. Try again when you're back online.",
                kind: .error
            )
            return false
        }
        isSaving = true
        defer { isSaving = false }
        let request = buildRequest()
        do {
            _ = try await api.request(
                HomesEndpoints.inviteOwner(homeId: homeId, request: request)
            ) as InviteOwnerResponse
            toast = ToastMessage(text: "Invite sent.", kind: .success)
            // Hold the success toast on screen briefly before tearing
            // the form down — otherwise SwiftUI dismisses the view
            // before the overlay has a chance to render.
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            shouldDismiss = true
            return true
        } catch let APIError.clientError(status, message) where status == 400 || status == 409 {
            // Backend returns 400 when the email doesn't resolve to a
            // user, and 409 when an active claim exists for this home.
            // Both surface as inline errors on the email field.
            var snapshot = fields[.email]
                ?? FormFieldState(id: InviteOwnerField.email.rawValue, originalValue: "")
            snapshot.error = friendlyClientError(message: message, status: status)
            snapshot.touched = true
            fields[.email] = snapshot
            shakeTrigger &+= 1
            toast = ToastMessage(text: snapshot.error ?? "Couldn't send invite.", kind: .error)
            return false
        } catch {
            toast = ToastMessage(
                text: (error as? APIError)?.errorDescription ?? "Couldn't send invite.",
                kind: .error
            )
            return false
        }
    }

    /// Invoked by the view when dismiss has taken effect.
    func acknowledgeDismiss() {
        shouldDismiss = false
    }

    // MARK: - Private

    private func validator(for field: InviteOwnerField) -> FormValidator {
        switch field {
        case .email: .all([.email(), .emailNotMatching(currentUserEmail)])
        case .phone: .e164Phone()
        }
    }

    private func buildRequest() -> InviteOwnerRequest {
        let email = (fields[.email]?.value ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let phone = (fields[.phone]?.value ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        return InviteOwnerRequest(
            email: email.isEmpty ? nil : email,
            phone: phone.isEmpty ? nil : phone,
            userId: nil,
            fastTrack: false
        )
    }

    private func friendlyClientError(message: String?, status: Int) -> String {
        let raw = (message ?? "").lowercased()
        if status == 409 || raw.contains("already active") {
            return "An ownership claim is already active for this home."
        }
        if raw.contains("already an owner") {
            return "Already an owner of this home."
        }
        if raw.contains("could not find") || raw.contains("create an account") {
            return "We couldn't find a Pantopus account with that email."
        }
        return message ?? "Couldn't send invite."
    }
}
