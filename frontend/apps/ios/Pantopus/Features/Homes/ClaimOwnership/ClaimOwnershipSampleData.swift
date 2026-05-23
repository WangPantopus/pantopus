//
//  ClaimOwnershipSampleData.swift
//  Pantopus
//
//  Deterministic fixture data for the Claim Ownership wizard start step.
//

import Foundation

struct ClaimOwnershipStartContent: Equatable {
    let homeLabel: String
    let contestedClaim: ClaimOwnershipContestedClaim?

    var isContested: Bool {
        contestedClaim != nil
    }
}

struct ClaimOwnershipContestedClaim: Equatable {
    let title: String
    let body: String
    let claimantInitials: String
    let claimantName: String
    let filedLabel: String
    let statusLabel: String
}

enum ClaimOwnershipSampleData {
    static func startContent(for homeId: String) -> ClaimOwnershipStartContent {
        if homeId.localizedCaseInsensitiveContains("contested") {
            return contestedStart
        }
        return canonicalStart
    }

    static let canonicalStart = ClaimOwnershipStartContent(
        homeLabel: "412 Elm St",
        contestedClaim: nil
    )

    static let contestedStart = ClaimOwnershipStartContent(
        homeLabel: "412 Elm St",
        contestedClaim: ClaimOwnershipContestedClaim(
            title: "Another claim is already in review",
            body: "A verified resident at this address filed an ownership claim 3 days ago. " +
                "You can still claim; both claims will be reviewed together.",
            claimantInitials: "JR",
            claimantName: "J. R.",
            filedLabel: "Filed Oct 9",
            statusLabel: "Under review"
        )
    )
}
