//
//  FormValidation.swift
//  Pantopus
//
//  Reusable validators for the Form archetype. Each rule returns a
//  localized error string or nil when the value is acceptable.
//

import Foundation

/// A single validation rule applied to a string field.
public struct FormValidator: Sendable {
    public let validate: @Sendable (String) -> String?

    public init(_ validate: @escaping @Sendable (String) -> String?) {
        self.validate = validate
    }
}

public extension FormValidator {
    /// Trims whitespace; returns an error when the result is empty.
    static func required(_ label: String) -> FormValidator {
        FormValidator { value in
            value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "\(label) is required." : nil
        }
    }

    /// Ensures length does not exceed `max` characters.
    static func maxLength(_ max: Int) -> FormValidator {
        FormValidator { value in
            value.count > max ? "Must be \(max) characters or fewer." : nil
        }
    }

    /// E.164 phone-number format (`^\+[1-9]\d{1,14}$`). Empty is allowed.
    /// Mirrors `updateProfileSchema.phoneNumber` at `backend/routes/users.js:330`.
    static func e164Phone() -> FormValidator {
        FormValidator { value in
            let trimmed = value.trimmingCharacters(in: .whitespaces)
            guard !trimmed.isEmpty else { return nil }
            let pattern = #"^\+[1-9]\d{1,14}$"#
            let matches = trimmed.range(of: pattern, options: .regularExpression) != nil
            return matches ? nil : "Phone must be in E.164 format, e.g. +15555550123."
        }
    }

    /// Applies each rule in order; stops at the first failure.
    static func all(_ rules: [FormValidator]) -> FormValidator {
        FormValidator { value in
            for rule in rules {
                if let message = rule.validate(value) { return message }
            }
            return nil
        }
    }
}
