//
//  CeremonialMailOpenContent.swift
//  Pantopus
//
//  Render-only model for the T3.8 Ceremonial Mail Open screen.
//  Four phases the screen progresses through:
//    .sealed    → letter has arrived, envelope hero
//    .breaking  → seal-break animation in flight
//    .open      → letter unfurled, body visible
//    .replying  → write-back compose form launching
//

import SwiftUI

public enum CeremonialMailPhase: String, Sendable, Hashable {
    case sealed, breaking, open, replying
}

public enum CeremonialMailStationeryTone: String, Sendable, Hashable {
    case classicCream = "classic_cream"
    case midnightBlue = "midnight_blue"
    case linen
    case botanical

    public init(wire: String?) {
        switch wire {
        case "midnight_blue": self = .midnightBlue
        case "linen": self = .linen
        case "botanical": self = .botanical
        default: self = .classicCream
        }
    }

    public var paperColor: Color {
        switch self {
        case .classicCream: Color(red: 248 / 255, green: 240 / 255, blue: 222 / 255)
        case .midnightBlue: Color(red: 224 / 255, green: 228 / 255, blue: 240 / 255)
        case .linen: Color(red: 250 / 255, green: 247 / 255, blue: 240 / 255)
        case .botanical: Color(red: 235 / 255, green: 244 / 255, blue: 232 / 255)
        }
    }

    public var paperShadow: Color {
        Color.black.opacity(0.12)
    }
}

public enum CeremonialMailInkTone: String, Sendable, Hashable {
    case walnut, navy, sepia, forest

    public init(wire: String?) {
        switch wire {
        case "navy": self = .navy
        case "sepia": self = .sepia
        case "forest": self = .forest
        default: self = .walnut
        }
    }

    public var color: Color {
        switch self {
        case .walnut: Color(red: 92 / 255, green: 56 / 255, blue: 32 / 255)
        case .navy: Color(red: 30 / 255, green: 56 / 255, blue: 96 / 255)
        case .sepia: Color(red: 110 / 255, green: 75 / 255, blue: 40 / 255)
        case .forest: Color(red: 38 / 255, green: 70 / 255, blue: 44 / 255)
        }
    }
}

public enum CeremonialMailSealTone: String, Sendable, Hashable {
    case waxRed = "wax_red"
    case waxBlue = "wax_blue"
    case waxBlack = "wax_black"
    case none

    public init(wire: String?) {
        switch wire {
        case "wax_blue": self = .waxBlue
        case "wax_black": self = .waxBlack
        case "none": self = .none
        default: self = .waxRed
        }
    }

    public var color: Color {
        switch self {
        case .waxRed: Color(red: 168 / 255, green: 32 / 255, blue: 38 / 255)
        case .waxBlue: Color(red: 32 / 255, green: 64 / 255, blue: 130 / 255)
        case .waxBlack: Color(red: 30 / 255, green: 30 / 255, blue: 30 / 255)
        case .none: Color.clear
        }
    }
}

public struct CeremonialSenderCard: Sendable, Hashable {
    public let displayName: String
    public let handle: String?
    public let trustLabel: String?
    public let avatarUrl: String?

    public init(displayName: String, handle: String?, trustLabel: String?, avatarUrl: String?) {
        self.displayName = displayName
        self.handle = handle
        self.trustLabel = trustLabel
        self.avatarUrl = avatarUrl
    }
}

public struct CeremonialOutcomeCTA: Sendable, Hashable, Identifiable {
    public let id: String
    public let label: String
    public let icon: PantopusIcon
    public let style: Style

    public enum Style: String, Sendable { case primary, ghost }

    public init(id: String, label: String, icon: PantopusIcon, style: Style) {
        self.id = id
        self.label = label
        self.icon = icon
        self.style = style
    }
}

public struct CeremonialMailLetter: Sendable, Hashable {
    public let mailId: String
    public let sender: CeremonialSenderCard
    public let category: String
    public let subject: String
    public let bodyParagraphs: [String]
    public let stationery: CeremonialMailStationeryTone
    public let ink: CeremonialMailInkTone
    public let seal: CeremonialMailSealTone
    public let voicePostscriptUri: String?
    public let receivedAt: String?
    public let outcomeCtas: [CeremonialOutcomeCTA]

    public init(
        mailId: String,
        sender: CeremonialSenderCard,
        category: String,
        subject: String,
        bodyParagraphs: [String],
        stationery: CeremonialMailStationeryTone,
        ink: CeremonialMailInkTone,
        seal: CeremonialMailSealTone,
        voicePostscriptUri: String?,
        receivedAt: String?,
        outcomeCtas: [CeremonialOutcomeCTA]
    ) {
        self.mailId = mailId
        self.sender = sender
        self.category = category
        self.subject = subject
        self.bodyParagraphs = bodyParagraphs
        self.stationery = stationery
        self.ink = ink
        self.seal = seal
        self.voicePostscriptUri = voicePostscriptUri
        self.receivedAt = receivedAt
        self.outcomeCtas = outcomeCtas
    }
}

public enum CeremonialMailOpenState: Sendable {
    case loading
    case loaded(CeremonialMailLetter, phase: CeremonialMailPhase)
    case error(message: String)
}

public extension CeremonialMailLetter {
    /// Stock CTAs every open letter ships with: Write back (primary)
    /// + Save (ghost) + Just read (ghost).
    static func defaultOutcomeCtas() -> [CeremonialOutcomeCTA] {
        [
            CeremonialOutcomeCTA(id: "write_back", label: "Write back", icon: .send, style: .primary),
            CeremonialOutcomeCTA(id: "save", label: "Save to records", icon: .check, style: .ghost),
            CeremonialOutcomeCTA(id: "just_read", label: "Just read", icon: .check, style: .ghost)
        ]
    }
}
