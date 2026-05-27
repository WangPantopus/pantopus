//
//  VerifyLandlordSteps.swift
//  Pantopus
//
//  Step descriptor + form state for the A12.5 / A12.6 verify-landlord
//  wizard. The wizard renders a "1 of 3" / "2 of 3" counter on its first
//  two steps and then fires `.openPostcardVerification` so the host can
//  push the standalone A12.7 sibling screen (which carries its own
//  chrome — not wizard chrome).
//

import Foundation

/// Steps the verify-landlord wizard owns. The third leg of the flow
/// (A12.7 Postcard verification) lives outside this state machine; the
/// wizard advertises "1 of 3" / "2 of 3" purely so the user understands
/// where they are in the broader flow.
public enum VerifyLandlordStep: String, CaseIterable, Sendable {
    case start
    case details
}

/// Which Start variant the wizard renders. The fast-track path is
/// surfaced when 2+ other tenants in the building have already verified
/// the same landlord; we skip the email confirmation in that case.
public enum VerifyLandlordVariant: String, Sendable {
    case canonical
    case fastTrack = "fast_track"
}

/// Submit-time state machine — shared shape between iOS + Android.
public enum VerifyLandlordSubmitState: Sendable, Equatable {
    case idle
    case submitting
    case submitted
    case error(message: String)
}

/// Detected attributes from a lease upload, used to drive the
/// done / warn DLeaseUpload variants and the unit-mismatch validation.
public struct VerifyLandlordLeaseFile: Sendable, Equatable {
    public let filename: String
    public let sizeLabel: String
    public let pageCount: Int
    public let detectedOwner: String?
    public let detectedUnit: String?

    public init(
        filename: String,
        sizeLabel: String,
        pageCount: Int,
        detectedOwner: String?,
        detectedUnit: String?
    ) {
        self.filename = filename
        self.sizeLabel = sizeLabel
        self.pageCount = pageCount
        self.detectedOwner = detectedOwner
        self.detectedUnit = detectedUnit
    }
}

/// Per-slot validation messages surfaced in the A12.6 error frame
/// (per-field chips) and aggregated into the top error-summary banner.
public struct VerifyLandlordValidationErrors: Sendable, Equatable {
    public var ownerName: String?
    public var contactName: String?
    public var email: String?
    public var lease: String?
    public var pmName: String?
    public var pmEmail: String?

    public init(
        ownerName: String? = nil,
        contactName: String? = nil,
        email: String? = nil,
        lease: String? = nil,
        pmName: String? = nil,
        pmEmail: String? = nil
    ) {
        self.ownerName = ownerName
        self.contactName = contactName
        self.email = email
        self.lease = lease
        self.pmName = pmName
        self.pmEmail = pmEmail
    }

    /// Used by the error-summary banner ("Fix N things to submit").
    public var count: Int {
        [ownerName, contactName, email, lease, pmName, pmEmail].compactMap { $0 }.count
    }

    /// Compact dot-separated list rendered as the banner sub-label
    /// ("Email format · Lease unit mismatch").
    public var compactSummary: String {
        var parts: [String] = []
        if email != nil { parts.append("Email format") }
        if lease != nil { parts.append("Lease unit mismatch") }
        if ownerName != nil { parts.append("Owner name") }
        if contactName != nil { parts.append("Contact name") }
        if pmName != nil { parts.append("PM name") }
        if pmEmail != nil { parts.append("PM email") }
        return parts.joined(separator: " · ")
    }

    public var isEmpty: Bool { count == 0 }
}

/// The full A12.6 form state. Held inside the wizard VM and projected
/// into per-field views on the Details step.
public struct VerifyLandlordForm: Sendable, Equatable {
    public var ownerName: String
    public var contactName: String
    public var email: String
    public var phone: String
    public var lease: VerifyLandlordLeaseFile?
    public var pmEnabled: Bool
    public var pmName: String
    public var pmEmail: String
    public var pmPhone: String

    /// The registered unit on the home record — drives the lease unit
    /// mismatch validation when the OCR'd unit doesn't agree.
    public var registeredUnit: String

    public init(
        ownerName: String = "",
        contactName: String = "",
        email: String = "",
        phone: String = "",
        lease: VerifyLandlordLeaseFile? = nil,
        pmEnabled: Bool = false,
        pmName: String = "",
        pmEmail: String = "",
        pmPhone: String = "",
        registeredUnit: String = ""
    ) {
        self.ownerName = ownerName
        self.contactName = contactName
        self.email = email
        self.phone = phone
        self.lease = lease
        self.pmEnabled = pmEnabled
        self.pmName = pmName
        self.pmEmail = pmEmail
        self.pmPhone = pmPhone
        self.registeredUnit = registeredUnit
    }

    /// Pure validation projection — same logic on iOS + Android. Surfaces
    /// the three contracts from the audit:
    ///   1. Email must be RFC-shaped (`x@y.z`).
    ///   2. The lease's detected unit must match `registeredUnit` when
    ///      OCR was able to read one.
    ///   3. When the PM toggle is on, PM name + PM email are both
    ///      required (PM phone stays optional).
    public func validate() -> VerifyLandlordValidationErrors {
        var errors = VerifyLandlordValidationErrors()
        if ownerName.trimmingCharacters(in: .whitespaces).isEmpty {
            errors.ownerName = "Required"
        }
        if contactName.trimmingCharacters(in: .whitespaces).isEmpty {
            errors.contactName = "Required"
        }
        let trimmedEmail = email.trimmingCharacters(in: .whitespaces)
        if trimmedEmail.isEmpty {
            errors.email = "Required"
        } else if !Self.looksLikeEmail(trimmedEmail) {
            errors.email = "Missing top-level domain"
        }
        if let lease {
            if let detected = lease.detectedUnit,
               !registeredUnit.isEmpty,
               detected.caseInsensitiveCompare(registeredUnit) != .orderedSame {
                errors.lease = "Unit mismatch"
            }
        } else {
            errors.lease = "Required"
        }
        if pmEnabled {
            if pmName.trimmingCharacters(in: .whitespaces).isEmpty {
                errors.pmName = "Required"
            }
            let trimmedPmEmail = pmEmail.trimmingCharacters(in: .whitespaces)
            if trimmedPmEmail.isEmpty {
                errors.pmEmail = "Required"
            } else if !Self.looksLikeEmail(trimmedPmEmail) {
                errors.pmEmail = "Missing top-level domain"
            }
        }
        return errors
    }

    /// Lightweight client-side check — catches the missing-TLD case
    /// from the design ("mira@elmstholdings"). Server-side still runs
    /// the authoritative validation.
    static func looksLikeEmail(_ candidate: String) -> Bool {
        guard let at = candidate.firstIndex(of: "@") else { return false }
        let local = candidate[..<at]
        let domain = candidate[candidate.index(after: at)...]
        guard !local.isEmpty, domain.contains(".") else { return false }
        // Rule out a trailing dot or leading dot in the domain
        // ("mira@elmstholdings.") and require at least one char after
        // the final dot so the TLD case from the audit fails closed.
        let parts = domain.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count >= 2, let tld = parts.last, !tld.isEmpty else { return false }
        return true
    }
}

/// Outbound events the wizard view needs the host nav stack to act on.
public enum VerifyLandlordOutboundEvent: Sendable, Equatable {
    case dismiss
    /// Submit succeeded — pop the wizard and push the standalone A12.7
    /// Postcard verification screen so the user can track delivery.
    case openPostcardVerification(homeId: String)
}
