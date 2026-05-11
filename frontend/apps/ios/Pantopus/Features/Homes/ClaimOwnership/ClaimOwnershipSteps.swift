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
///
/// Chrome (top-bar readout + progress fraction) is computed in the VM's
/// `chrome` accessor; per-step numeric metadata isn't needed because the
/// wizard doesn't survive process death (no save/restore key).
public enum ClaimOwnershipStep: String, CaseIterable, Sendable {
    case start
    case upload
    case success
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
        "JPG or PNG up to 10 MB"
    }
}

/// Maximum file size accepted by the wizard's client-side picker.
/// Mirrors the backend's `/api/files/upload` cap so the user sees an
/// inline error instead of a 413 round-trip.
public let CLAIM_FILE_MAX_BYTES: Int = 10 * 1_024 * 1_024

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
