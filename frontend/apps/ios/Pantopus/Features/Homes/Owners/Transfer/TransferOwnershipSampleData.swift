//
//  TransferOwnershipSampleData.swift
//  Pantopus
//
//  A13.4 — Deterministic seed data for the Transfer Ownership form. The
//  backend has been removed from the repo, so the recipient roster, the
//  current owner roster (you + 2 co-owners), the home-context strip
//  copy, and the irreversibility warning template all live here so
//  previews + snapshot baselines render the same shape every time.
//

import Foundation
import SwiftUI

/// Static seed data backing `TransferOwnershipViewModel`. Every screen-
/// observable value is keyed by stable id so snapshot baselines stay
/// reproducible across runs.
public enum TransferOwnershipSampleData {
    // MARK: - Home strip

    public struct HomeContext: Sendable, Equatable {
        public let title: String
        public let address: String
        public let since: String
        public let yourStake: Int
        public let coOwnerNames: String
    }

    public static func homeContext(for _: String) -> HomeContext {
        HomeContext(
            title: "412 Elm Street",
            address: "412 Elm Street",
            since: "since 2019",
            yourStake: 60,
            coOwnerNames: "Mateo & Jin"
        )
    }

    // MARK: - You + co-owners

    public struct OwnerSeed: Identifiable, Sendable, Equatable {
        public let id: String
        public let displayName: String
        public let initials: String
        public let percent: Int
        public let palette: TransferOwnerPalette
    }

    public static let currentUser = OwnerSeed(
        id: "you",
        displayName: "You",
        initials: "DK",
        percent: 60,
        palette: .personal
    )

    public static let coOwners: [OwnerSeed] = [
        OwnerSeed(id: "mateo", displayName: "Mateo", initials: "MR", percent: 25, palette: .handyman),
        OwnerSeed(id: "jin", displayName: "Jin", initials: "JL", percent: 15, palette: .home)
    ]

    /// Sender display name used inside the Face ID confirm sheet.
    public static let senderFullName = "Daniel Kovács"

    // MARK: - Recipient roster

    public struct RecipientSeed: Identifiable, Sendable, Equatable {
        public let id: String
        public let name: String
        public let initials: String
        public let handle: String
        public let email: String
        public let owns: String
        public let onPantopus: String
        public let mutual: String
        public let verified: Bool
    }

    public static let mayaFortune = RecipientSeed(
        id: "maya_fortune",
        name: "Maya Fortune",
        initials: "MF",
        handle: "mayaf",
        email: "maya.fortune@pantopus.app",
        owns: "2 homes",
        onPantopus: "4 yrs",
        mutual: "5",
        verified: true
    )

    // MARK: - Slider presets

    public static let sliderRange: ClosedRange<Int> = 1...60
    public static let presets: [Int] = [10, 25, 33, 50]
    public static let defaultAmount = 25

    // MARK: - Confirmation phrase

    /// The literal the user must type into the confirmation field to enable
    /// the sticky CTA.
    public static let confirmationPhrase = "TRANSFER"

    // MARK: - Recipient palette → SwiftUI Color

    public static let recipientPaletteStart = Theme.Color.business
    public static let recipientPaletteEnd = Theme.Color.businessDark
}

/// Avatar gradient palettes for each owner row. Distinct from the
/// `OwnerProofPalette` rotation used by the Owners list so the diff bar's
/// colours stay legible and visually distinct without colliding with the
/// pillar tokens used elsewhere on the screen.
public enum TransferOwnerPalette: Sendable, Equatable {
    case personal
    case handyman
    case home
    case business

    public var color: Color {
        switch self {
        case .personal: Theme.Color.primary600
        case .handyman: Theme.Color.handyman
        case .home: Theme.Color.success
        case .business: Theme.Color.business
        }
    }

    public var gradientStart: Color {
        switch self {
        case .personal: Theme.Color.primary500
        case .handyman: Theme.Color.handyman
        case .home: Theme.Color.success
        case .business: Theme.Color.business
        }
    }

    public var gradientEnd: Color {
        switch self {
        case .personal: Theme.Color.primary700
        case .handyman: Theme.Color.warning
        case .home: Theme.Color.homeDark
        case .business: Theme.Color.businessDark
        }
    }
}
