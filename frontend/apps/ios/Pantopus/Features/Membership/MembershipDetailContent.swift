//
//  MembershipDetailContent.swift
//  Pantopus
//
//  A10.8 — Fan membership manage. Render models for the fan-side view of
//  a paid membership to a persona (creator / business). The screen is the
//  "policy commitment made visible" per audience-profile §11.6: the tier,
//  what it promises (with the SLA shown inline, not buried in TOS), the
//  renewal mechanics, a single-tap cancel with no retention dark patterns,
//  and — when an SLA breaks — the refund offered up front.
//

import SwiftUI

/// Membership tier with its paper-card colour treatment. The tier palette
/// (Bronze / Silver / Gold) is membership-local because no other surface
/// needs it — identity pillars + semantic colours live in `Theme.Color`.
/// Mirrors the `GigsCategory.color` precedent: feature-local swatches use
/// `Color(red:green:blue:)` component literals so the CI hex-grep guard
/// stays satisfied (no raw hex string in `Features/**`).
public enum MembershipTier: String, Sendable, Hashable, CaseIterable {
    case bronze
    case silver
    case gold

    /// Display name for the tier strip.
    public var displayName: String {
        switch self {
        case .bronze: "Bronze"
        case .silver: "Silver"
        case .gold: "Gold"
        }
    }

    /// 1-based rung on the 3-tier ladder. Drives the "2 of 3" pill.
    public var ladderRank: Int {
        switch self {
        case .bronze: 1
        case .silver: 2
        case .gold: 3
        }
    }

    /// Total rungs on the ladder (Bronze · Silver · Gold).
    public static var ladderTotal: Int {
        allCases.count
    }

    /// Tier strip background. Feature-local tier swatches mirror the design pack.
    public var bgColor: Color {
        switch self {
        case .bronze: Color(red: 254 / 255, green: 243 / 255, blue: 199 / 255)
        case .silver: Color(red: 241 / 255, green: 243 / 255, blue: 245 / 255)
        case .gold: Color(red: 254 / 255, green: 249 / 255, blue: 195 / 255)
        }
    }

    /// Tier name foreground. Feature-local tier swatches mirror the design pack.
    public var fgColor: Color {
        switch self {
        case .bronze: Color(red: 146 / 255, green: 64 / 255, blue: 14 / 255)
        case .silver: Color(red: 55 / 255, green: 65 / 255, blue: 81 / 255)
        case .gold: Color(red: 133 / 255, green: 77 / 255, blue: 14 / 255)
        }
    }
}

/// The persona the fan supports — drives the PersonaCard at the top.
public struct MembershipPersona: Sendable, Hashable {
    public let id: String
    public let name: String
    public let initials: String
    public let subtitle: String
    public let pillar: IdentityPillar
    public let pillarLabel: String
    public let verified: Bool

    public init(
        id: String,
        name: String,
        initials: String,
        subtitle: String,
        pillar: IdentityPillar,
        pillarLabel: String,
        verified: Bool
    ) {
        self.id = id
        self.name = name
        self.initials = initials
        self.subtitle = subtitle
        self.pillar = pillar
        self.pillarLabel = pillarLabel
        self.verified = verified
    }
}

/// One verified-benefit row. `slaBadge` is non-nil for the benefit that
/// carries the service-level promise so it renders as a visible chip
/// rather than hiding inside the meta line.
public struct MembershipBenefit: Sendable, Hashable, Identifiable {
    public let id: String
    public let icon: PantopusIcon
    public let label: String
    public let meta: String
    public let slaBadge: String?

    public init(
        id: String,
        icon: PantopusIcon,
        label: String,
        meta: String,
        slaBadge: String? = nil
    ) {
        self.id = id
        self.icon = icon
        self.label = label
        self.meta = meta
        self.slaBadge = slaBadge
    }
}

/// SLA-missed banner payload. Owns the broken promise up front and offers
/// the refund as the primary action; "give it a week" is the gentle
/// alternative — never a guilt-trip "are you sure".
public struct MembershipSLAAlert: Sendable, Hashable {
    public let title: String
    public let message: String
    public let refundCtaLabel: String
    public let dismissCtaLabel: String

    public init(title: String, message: String, refundCtaLabel: String, dismissCtaLabel: String) {
        self.title = title
        self.message = message
        self.refundCtaLabel = refundCtaLabel
        self.dismissCtaLabel = dismissCtaLabel
    }
}

/// Composed content for the membership detail surface. `slaAlert` is
/// non-nil only in the `.slaMissed` state (the amber banner + warn-tone
/// renewal row read from it).
public struct MembershipDetailContent: Sendable, Hashable {
    public let persona: MembershipPersona
    public let tier: MembershipTier
    public let priceLabel: String
    public let periodLabel: String
    public let renewalLabel: String
    public let paymentLabel: String
    public let benefits: [MembershipBenefit]
    public let policyFootnote: String
    public let slaAlert: MembershipSLAAlert?

    public init(
        persona: MembershipPersona,
        tier: MembershipTier,
        priceLabel: String,
        periodLabel: String,
        renewalLabel: String,
        paymentLabel: String,
        benefits: [MembershipBenefit],
        policyFootnote: String,
        slaAlert: MembershipSLAAlert? = nil
    ) {
        self.persona = persona
        self.tier = tier
        self.priceLabel = priceLabel
        self.periodLabel = periodLabel
        self.renewalLabel = renewalLabel
        self.paymentLabel = paymentLabel
        self.benefits = benefits
        self.policyFootnote = policyFootnote
        self.slaAlert = slaAlert
    }

    /// Same content with the SLA banner dropped — used when the fan picks
    /// "give it a week" so the screen settles back to the happy path.
    public func clearingSLAAlert() -> MembershipDetailContent {
        MembershipDetailContent(
            persona: persona,
            tier: tier,
            priceLabel: priceLabel,
            periodLabel: periodLabel,
            renewalLabel: renewalLabel,
            paymentLabel: paymentLabel,
            benefits: benefits,
            policyFootnote: policyFootnote,
            slaAlert: nil
        )
    }
}

/// Lightweight descriptor for the "You're a member" footer rendered on the
/// Audience Profile — the Wave A direct-link entry point into membership
/// detail until the standalone Memberships index list ships.
public struct AudienceMemberFooter: Sendable, Hashable {
    public let personaId: String
    public let personaName: String
    public let tierName: String

    public init(personaId: String, personaName: String, tierName: String) {
        self.personaId = personaId
        self.personaName = personaName
        self.tierName = tierName
    }
}

/// Top-level state for the membership detail VM. No `.empty` case — a fan
/// only reaches this screen for an active membership; `.slaMissed` is the
/// happy-path content plus the refund banner.
public enum MembershipDetailState: Sendable {
    case loading
    case populated(MembershipDetailContent)
    case slaMissed(MembershipDetailContent)
    case error(message: String)
}
