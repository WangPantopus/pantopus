//
//  PasswordChangeViewModel.swift
//  Pantopus
//
//  P8 / T6.2c — Settings → Password sub-route.
//  Three-field form: current password (only when the account already
//  has a password — discovered via `GET /api/users/auth-methods`),
//  new password, confirm new password. Submit calls
//  `POST /api/users/password` (users.js:1771).
//
//  Validation:
//  - current required when `hasPassword == true`
//  - new ≥ 8 chars (matches `PASSWORD_MIN_LENGTH` constants in users.js)
//  - new ≠ current (server also enforces; we catch early for clarity)
//  - confirm == new
//

import Foundation
import Observation

@Observable
@MainActor
public final class PasswordChangeViewModel {
    public enum FormState: Sendable, Equatable {
        case loading
        case ready
        case error(message: String)
    }

    public enum FieldKey: String, CaseIterable, Sendable {
        case current, new, confirm
    }

    public private(set) var formState: FormState = .loading
    public private(set) var hasPassword: Bool = true
    public private(set) var isSaving: Bool = false
    public var fields: [FieldKey: FormFieldState] = [
        .current: FormFieldState(id: "current", originalValue: ""),
        .new: FormFieldState(id: "new", originalValue: ""),
        .confirm: FormFieldState(id: "confirm", originalValue: "")
    ]
    public private(set) var toast: String?
    public var shouldDismiss: Bool = false

    private let api: APIClient

    init(api: APIClient = .shared) {
        self.api = api
    }

    public static let minPasswordLength = 8

    public var requiresCurrent: Bool {
        hasPassword
    }

    public var isDirty: Bool {
        let newValue = fields[.new]?.value ?? ""
        let confirmValue = fields[.confirm]?.value ?? ""
        let currentValue = fields[.current]?.value ?? ""
        return !newValue.isEmpty || !confirmValue.isEmpty || (requiresCurrent && !currentValue.isEmpty)
    }

    public var isValid: Bool {
        let newValue = fields[.new]?.value ?? ""
        let confirmValue = fields[.confirm]?.value ?? ""
        let currentValue = fields[.current]?.value ?? ""
        guard newValue.count >= Self.minPasswordLength else { return false }
        guard confirmValue == newValue else { return false }
        guard !requiresCurrent || !currentValue.isEmpty else { return false }
        if requiresCurrent, currentValue == newValue { return false }
        return true
    }

    public func load() async {
        formState = .loading
        do {
            let methods: AuthMethodsResponse = try await api.request(AuthMethodsEndpoints.methods)
            hasPassword = methods.hasPassword ?? true
            formState = .ready
        } catch {
            // Default to requiring the current password — safer than the
            // alternative if the discovery call fails.
            hasPassword = true
            formState = .ready
        }
    }

    public func update(_ key: FieldKey, to value: String) {
        var snapshot = fields[key] ?? FormFieldState(id: key.rawValue, originalValue: "")
        snapshot.value = value
        snapshot.touched = true
        snapshot.error = inlineError(for: key, value: value)
        fields[key] = snapshot
        // Confirm cross-field check when new changes.
        if key == .new, let confirm = fields[.confirm], confirm.touched {
            var c = confirm
            c.error = inlineError(for: .confirm, value: c.value)
            fields[.confirm] = c
        }
    }

    public func save() async {
        guard isValid, !isSaving else { return }
        let currentValue = fields[.current]?.value ?? ""
        let newValue = fields[.new]?.value ?? ""
        isSaving = true
        defer { isSaving = false }
        let body = PasswordUpdateBody(
            currentPassword: requiresCurrent ? currentValue : nil,
            newPassword: newValue
        )
        do {
            _ = try await api.request(AuthMethodsEndpoints.updatePassword(body))
            toast = "Password updated"
            shouldDismiss = true
        } catch APIError.unauthorized {
            mark(key: .current, error: "Current password is incorrect")
        } catch let APIError.clientError(status, message) {
            mapServerError(status: status, message: message)
        } catch APIError.notFound {
            mark(key: .new, error: "We couldn't find your account. Try signing back in.")
        } catch {
            mark(key: .new, error: "Couldn't update your password. Try again.")
        }
    }

    public func acknowledgeDismiss() {
        shouldDismiss = false
    }

    private func inlineError(for key: FieldKey, value: String) -> String? {
        switch key {
        case .current:
            if requiresCurrent, value.isEmpty {
                return "Required"
            }
            return nil
        case .new:
            if value.count < Self.minPasswordLength {
                return "At least \(Self.minPasswordLength) characters"
            }
            let currentValue = fields[.current]?.value ?? ""
            if requiresCurrent, !currentValue.isEmpty, value == currentValue {
                return "Choose something different from your current password"
            }
            return nil
        case .confirm:
            let newValue = fields[.new]?.value ?? ""
            if value != newValue { return "Doesn't match" }
            return nil
        }
    }

    private func mark(key: FieldKey, error: String) {
        var snapshot = fields[key] ?? FormFieldState(id: key.rawValue, originalValue: "")
        snapshot.error = error
        fields[key] = snapshot
    }

    private func mapServerError(status: Int, message: String?) {
        switch status {
        case 401:
            mark(key: .current, error: message ?? "Current password is incorrect")
        case 400:
            mark(key: .new, error: message ?? "Couldn't update your password")
        case 429:
            toast = "Too many attempts. Wait a minute and try again."
        default:
            mark(key: .new, error: message ?? "Couldn't update your password")
        }
    }
}
