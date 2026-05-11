//
//  MailItemCategory.swift
//  Pantopus
//
//  14-category enum for mailbox items. Each case carries its accent
//  color for the 4pt top strip.
//

import SwiftUI

/// A mailbox item's category. Maps 1:1 onto the backend `mail.mail_type`
/// field; an unknown value falls through to `.general`.
public enum MailItemCategory: String, Sendable, CaseIterable {
    case package
    case coupon
    case booklet
    case certified
    case notice
    case bill
    case statement
    case insurance
    case tax
    case subscription
    case legal
    case healthcare
    case membership
    case delivery
    case social
    case general

    /// 4pt accent strip color sitting at the top of the detail shell.
    public var accent: Color {
        switch self {
        case .package: Theme.Color.delivery
        case .coupon: Theme.Color.childCare
        case .booklet: Theme.Color.moving // violet token per P18 FrameBooklet
        case .certified: Theme.Color.primary600 // primary token per P18 FrameCertified
        case .notice: Theme.Color.warning
        case .bill: Theme.Color.error
        case .statement: Theme.Color.tutoring
        case .insurance: Theme.Color.info
        case .tax: Theme.Color.vehicles
        case .subscription: Theme.Color.goods
        case .legal: Theme.Color.moving
        case .healthcare: Theme.Color.petCare
        case .membership: Theme.Color.personal
        case .delivery: Theme.Color.handyman
        case .social: Theme.Color.cleaning
        case .general: Theme.Color.appTextSecondary
        }
    }

    /// Matches a backend `mail_type` / `type` string onto a typed case.
    public static func fromRaw(_ raw: String?) -> MailItemCategory {
        guard let raw = raw?.lowercased() else { return .general }
        return MailItemCategory(rawValue: raw) ?? .general
    }
}

/// Trust level for the sender pill.
public enum MailTrust: String, Sendable, CaseIterable {
    /// `verified_gov`, `verified_utility`, `verified_business` on the wire.
    case verified
    case partial
    case unverified
    /// Sender is a known Pantopus user.
    case chain
    /// Certified mail / chain-of-custody — primary tint, longer label.
    /// Set by the VM when category is `.certified`; not derived from the
    /// wire `sender_trust` field today.
    case certifiedChain

    /// Icon rendered in the pill.
    public var icon: PantopusIcon {
        switch self {
        case .verified: .shieldCheck
        case .partial: .shield
        case .unverified: .shield
        case .chain: .shieldCheck
        case .certifiedChain: .shieldCheck
        }
    }

    /// Pill background / foreground pair.
    public var background: Color {
        switch self {
        case .verified: Theme.Color.successBg
        case .partial: Theme.Color.warningBg
        case .unverified: Theme.Color.appSurfaceSunken
        case .chain: Theme.Color.infoBg
        case .certifiedChain: Theme.Color.infoBg
        }
    }

    /// Foreground tint.
    public var foreground: Color {
        switch self {
        case .verified: Theme.Color.success
        case .partial: Theme.Color.warning
        case .unverified: Theme.Color.appTextSecondary
        case .chain: Theme.Color.primary600
        case .certifiedChain: Theme.Color.primary600
        }
    }

    /// Human-readable label.
    public var label: String {
        switch self {
        case .verified: "Verified"
        case .partial: "Partial"
        case .unverified: "Unverified"
        case .chain: "Pantopus user"
        case .certifiedChain: "Certified · Chain of custody"
        }
    }

    /// Maps the backend `sender_trust` string onto a pill state.
    public static func fromRaw(_ raw: String?) -> MailTrust {
        switch raw {
        case "verified_gov", "verified_utility", "verified_business": .verified
        case "pantopus_user": .chain
        case "partial": .partial
        default: .unverified
        }
    }
}
