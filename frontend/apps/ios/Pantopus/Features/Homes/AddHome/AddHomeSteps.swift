//
//  AddHomeSteps.swift
//  Pantopus
//
//  Step identifiers + persistable form state for the Add-Home wizard.
//  Used both by `AddHomeWizardViewModel` and the SceneStorage-backed
//  restoration glue in `AddHomeWizardView`.
//

import Foundation

/// The four pre-success steps of the Add-Home wizard, in order.
public enum AddHomeStep: Int, CaseIterable, Sendable {
    case address = 0
    case confirm
    case role
    case review
    case success

    /// Total number of "step N of M" steps shown in the readout. Excludes
    /// the success terminal.
    public static let progressTotal: Int = 4

    /// One-indexed position used in the "N of M" top-bar readout.
    public var stepNumber: Int? {
        switch self {
        case .address: 1
        case .confirm: 2
        case .role: 3
        case .review: 4
        case .success: nil
        }
    }
}

/// User-supplied address fields in step 1. We deviate from the design's
/// "single typeahead" because the backend's
/// `propertySuggestions` Joi schema requires `{address, city, state,
/// zipCode}` rather than a free-form `{query}` (`backend/routes/home.js:540`).
/// A real typeahead will need a server-side autocomplete endpoint.
// TODO(backend): expose a query-only autocomplete to enable the design's
// single-input typeahead UX in step 1.
public struct AddHomeAddressFields: Codable, Sendable, Equatable {
    public var street: String
    public var unit: String
    public var city: String
    public var state: String
    public var zipCode: String

    public init(
        street: String = "",
        unit: String = "",
        city: String = "",
        state: String = "",
        zipCode: String = ""
    ) {
        self.street = street
        self.unit = unit
        self.city = city
        self.state = state
        self.zipCode = zipCode
    }

    /// True when every required component (street/city/state/zip) has at
    /// least one non-whitespace character.
    public var isComplete: Bool {
        !trimmed(street).isEmpty
            && !trimmed(city).isEmpty
            && !trimmed(state).isEmpty
            && !trimmed(zipCode).isEmpty
    }

    private func trimmed(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

/// User's role on the home being added — picked in step 3.
public enum AddHomeRole: String, CaseIterable, Codable, Sendable {
    case owner
    case tenant
    case householdMember

    /// Wire value sent in `CreateHomeRequest` (a `name` hint, not the
    /// canonical role field — the role is implied by the verification
    /// flow on the server).
    public var label: String {
        switch self {
        case .owner: "Owner"
        case .tenant: "Tenant"
        case .householdMember: "Household member"
        }
    }
}

/// Snapshot of all wizard form state. Encoded into `@SceneStorage` so the
/// in-progress wizard survives process death and config changes per
/// acceptance criterion #5.
public struct AddHomeFormState: Codable, Sendable, Equatable {
    public var step: Int
    public var address: AddHomeAddressFields
    public var isPrimary: Bool
    public var role: AddHomeRole?

    public init(
        step: Int = AddHomeStep.address.rawValue,
        address: AddHomeAddressFields = .init(),
        isPrimary: Bool = true,
        role: AddHomeRole? = nil
    ) {
        self.step = step
        self.address = address
        self.isPrimary = isPrimary
        self.role = role
    }

    public static let empty = AddHomeFormState()
}
