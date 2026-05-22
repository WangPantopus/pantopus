//
//  GigComposeSteps.swift
//  Pantopus
//
//  Step identifiers + form-state value types for the Post-a-Task wizard
//  (P2.2). Six pre-success steps + a terminal success step, mirroring
//  `Wizard.html` / `wizard-frames.jsx`. The form state is a
//  `Codable`/`Equatable` snapshot so the wizard can survive process
//  death via `@SceneStorage` (same pattern as `AddHomeFormState`).
//

import Foundation

/// One-of-nine category the user can pick in step 1. Mirrors the chip
/// palette in `gigs-frames.jsx` CATS plus an `other` bucket the
/// composer surfaces but the feed filter does not (we route `other` to
/// the backend's free-form `category` field).
public enum GigComposeCategory: String, CaseIterable, Sendable, Codable, Hashable {
    case handyman
    case cleaning
    case moving
    case petcare
    case childcare
    case tutoring
    case delivery
    case tech
    case other

    public var label: String {
        switch self {
        case .handyman: "Handyman"
        case .cleaning: "Cleaning"
        case .moving: "Moving"
        case .petcare: "Pet care"
        case .childcare: "Child care"
        case .tutoring: "Tutoring"
        case .delivery: "Delivery"
        case .tech: "Tech"
        case .other: "Other"
        }
    }

    /// Maps a `GigsCategory.rawValue` (or any unrecognised string) into
    /// the compose enum. Used so the Hub's category-specific entry
    /// preselects the right tile.
    public static func from(rawKey: String?) -> GigComposeCategory? {
        guard let raw = rawKey?.lowercased(), !raw.isEmpty, raw != "all" else { return nil }
        return GigComposeCategory.allCases.first { $0.rawValue == raw }
    }
}

/// B.3 (A12.8) — entry mode for step 1. `.magic` is the default
/// AI-assisted describe path; `.manual` is the category-grid fallback
/// reachable via the "Pick a category instead" link.
public enum ComposeMode: String, CaseIterable, Sendable, Codable, Hashable {
    case magic
    case manual
}

/// Budget-type radio in step 3.
public enum GigComposeBudgetType: String, CaseIterable, Sendable, Codable, Hashable {
    case fixed
    case hourly
    case offers

    /// User-facing label rendered in the radio row.
    public var label: String {
        switch self {
        case .fixed: "Fixed price"
        case .hourly: "Hourly"
        case .offers: "Open to bids"
        }
    }

    /// Wire value forwarded as `pay_type` to `POST /api/gigs`.
    public var wireValue: String {
        rawValue
    }
}

/// Schedule-type radio in step 4.
public enum GigComposeScheduleType: String, CaseIterable, Sendable, Codable, Hashable {
    case oneTime
    case recurring
    case flexible

    public var label: String {
        switch self {
        case .oneTime: "One-time"
        case .recurring: "Recurring"
        case .flexible: "Flexible"
        }
    }

    /// Wire value forwarded as `schedule_type` to `POST /api/gigs`.
    /// "Recurring" maps to `flexible` until the backend gains a true
    /// recurring schedule_type — the spec surfaces it in the UI but the
    /// API doesn't model it yet.
    public var wireValue: String {
        switch self {
        case .oneTime: "scheduled"
        case .recurring: "flexible"
        case .flexible: "flexible"
        }
    }
}

/// Location-mode radio in step 5.
public enum GigComposeLocationMode: String, CaseIterable, Sendable, Codable, Hashable {
    case yourAddress
    case aPlace
    case virtual

    public var label: String {
        switch self {
        case .yourAddress: "Your address"
        case .aPlace: "A place"
        case .virtual: "Virtual"
        }
    }

    /// Subcopy under the radio label.
    public var subcopy: String {
        switch self {
        case .yourAddress: "Helpers come to the address on your account."
        case .aPlace: "Helpers come to a different address you'll enter."
        case .virtual: "Done over phone, video, or messages — no on-site visit."
        }
    }

    /// Wire value forwarded as `location.mode` to `POST /api/gigs`.
    public var wireMode: String {
        switch self {
        case .yourAddress: "home"
        case .aPlace: "address"
        case .virtual: "custom"
        }
    }
}

/// Plain-old-data address fields collected in step 5 when the user
/// picks `aPlace`. Mirrors `AddHomeAddressFields`.
public struct GigComposePlaceAddress: Codable, Sendable, Equatable {
    public var line1: String
    public var city: String
    public var state: String
    public var zip: String

    public init(
        line1: String = "",
        city: String = "",
        state: String = "",
        zip: String = ""
    ) {
        self.line1 = line1
        self.city = city
        self.state = state
        self.zip = zip
    }

    /// True when every required component carries a non-whitespace value.
    public var isComplete: Bool {
        !line1.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !city.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !state.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !zip.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

/// The six pre-success steps of the Post-a-Task wizard, in order.
public enum GigComposeStep: Int, CaseIterable, Sendable {
    case category = 0
    case basics
    case budget
    case schedule
    case location
    case review
    case success

    /// Total number of "step N of M" steps shown in the readout. Excludes
    /// the success terminal.
    public static let progressTotal: Int = 6

    /// One-indexed position used in the "N of M" top-bar readout.
    public var stepNumber: Int? {
        switch self {
        case .category: 1
        case .basics: 2
        case .budget: 3
        case .schedule: 4
        case .location: 5
        case .review: 6
        case .success: nil
        }
    }
}

/// Validation constants enforced at the UI layer. The backend also
/// validates (`backend/routes/gigs.js:425`); these mirror the prompt's
/// stricter UI rules.
public enum GigComposeLimits {
    public static let titleMin: Int = 5
    public static let titleMax: Int = 100
    public static let descriptionMin: Int = 20
    public static let descriptionMax: Int = 2000
    public static let maxPhotos: Int = 6
    /// B.3 — Magic Task describe textarea cap (matches A12.8 "184 / 500").
    public static let describeMax: Int = 500
}

/// Snapshot of all wizard form state. Encoded into `@SceneStorage` so
/// the in-progress wizard survives process death and config changes.
public struct GigComposeFormState: Codable, Sendable, Equatable {
    public var step: Int
    /// B.3 — step-1 entry mode (Magic describe vs manual picker).
    public var composeMode: ComposeMode
    /// B.3 — plain-English Magic Task input.
    public var describeText: String
    /// B.3 — archetype parsed from `describeText` (debounced). Mirrored
    /// into `category` so downstream steps + submission use it.
    public var detectedArchetype: GigComposeCategory?
    public var category: GigComposeCategory?
    public var title: String
    public var description: String
    public var photoIds: [String]
    public var budgetType: GigComposeBudgetType?
    public var budgetMin: String
    public var budgetMax: String
    public var scheduleType: GigComposeScheduleType?
    /// ISO-8601 date string for the one-time `scheduleType` selection.
    /// Stored as a string so the form survives JSON round-trips.
    public var scheduledStartISO: String?
    public var locationMode: GigComposeLocationMode?
    public var placeAddress: GigComposePlaceAddress

    public init(
        step: Int = GigComposeStep.category.rawValue,
        composeMode: ComposeMode = .magic,
        describeText: String = "",
        detectedArchetype: GigComposeCategory? = nil,
        category: GigComposeCategory? = nil,
        title: String = "",
        description: String = "",
        photoIds: [String] = [],
        budgetType: GigComposeBudgetType? = nil,
        budgetMin: String = "",
        budgetMax: String = "",
        scheduleType: GigComposeScheduleType? = nil,
        scheduledStartISO: String? = nil,
        locationMode: GigComposeLocationMode? = nil,
        placeAddress: GigComposePlaceAddress = .init()
    ) {
        self.step = step
        self.composeMode = composeMode
        self.describeText = describeText
        self.detectedArchetype = detectedArchetype
        self.category = category
        self.title = title
        self.description = description
        self.photoIds = photoIds
        self.budgetType = budgetType
        self.budgetMin = budgetMin
        self.budgetMax = budgetMax
        self.scheduleType = scheduleType
        self.scheduledStartISO = scheduledStartISO
        self.locationMode = locationMode
        self.placeAddress = placeAddress
    }

    public static let empty = GigComposeFormState()

    /// True when any user-visible field carries data — drives the
    /// discard-confirm gate.
    public var hasAnyData: Bool {
        !describeText.isEmpty
            || category != nil
            || !title.isEmpty
            || !description.isEmpty
            || !photoIds.isEmpty
            || budgetType != nil
            || !budgetMin.isEmpty
            || !budgetMax.isEmpty
            || scheduleType != nil
            || scheduledStartISO != nil
            || locationMode != nil
            || placeAddress.isComplete
            || !placeAddress.line1.isEmpty
    }
}
