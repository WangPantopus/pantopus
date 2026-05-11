//
//  ClaimOwnershipSteps.swift
//  Pantopus
//
//  Step descriptors + form state for the claim-ownership wizard.
//

import Foundation

/// Steps the claim-ownership wizard can be on. Order is meaningful —
/// the wizard advances `start → upload → success` and back-navigates
/// `upload → start`. Success has no back chevron and ends the flow.
public enum ClaimOwnershipStep: String, CaseIterable, Sendable {
    case start
    case upload
    case success

    /// 1-based step number, nil for terminal step.
    public var stepNumber: Int? {
        switch self {
        case .start: 1
        case .upload: 2
        case .success: nil
        }
    }
}

/// Identifier for one of the two upload tiles.
public enum ClaimEvidenceSlot: String, CaseIterable, Sendable {
    case identity
    case ownership

    /// Backend `evidence_type` enum value sent on upload —
    /// `uploadEvidenceSchema` (`backend/routes/homeOwnership.js:43`).
    public var backendType: String {
        switch self {
        case .identity: "idv"
        case .ownership: "deed"
        }
    }

    public var title: String {
        switch self {
        case .identity: "Government ID"
        case .ownership: "Proof of ownership"
        }
    }

    public var acceptHint: String {
        "JPG, PNG, or PDF up to 10 MB"
    }
}

/// One picked file held in the VM until submit time.
public struct ClaimPickedFile: Sendable, Equatable {
    public let filename: String
    public let mimeType: String
    public let data: Data

    public init(filename: String, mimeType: String, data: Data) {
        self.filename = filename
        self.mimeType = mimeType
        self.data = data
    }

    public var sizeBytes: Int { data.count }
}

/// Per-slot upload state surfaced to the UI.
public enum ClaimSlotUiState: Sendable, Equatable {
    case empty
    case picked(file: ClaimPickedFile)
    case uploading(file: ClaimPickedFile, fraction: Double)
    case uploaded(file: ClaimPickedFile, fileURL: String)
    case failed(file: ClaimPickedFile, message: String)

    public var hasFile: Bool {
        switch self {
        case .empty: false
        default: true
        }
    }

    public var pickedFile: ClaimPickedFile? {
        switch self {
        case let .picked(file), let .uploading(file, _),
             let .uploaded(file, _), let .failed(file, _):
            file
        case .empty:
            nil
        }
    }
}
