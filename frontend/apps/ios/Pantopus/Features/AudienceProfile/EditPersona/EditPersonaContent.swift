//
//  EditPersonaContent.swift
//  Pantopus
//
//  A13.12 — Edit persona. Render models for the creator-facing persona
//  editor (the underlying editor for a Public Profile / persona, distinct
//  from the T3.3 Audience Profile management hub). Two frames:
//
//    • LIVE  — published & monetized: Stripe connected, paid tiers active,
//              sticky bar muted ("saved").
//    • SETUP — mid-setup draft: checklist hero replaces the live hero,
//              Stripe not connected (paid tiers locked), sticky bar
//              promises publish unlocks after Stripe + schedule.
//
//  Persona accent: sky / `primary600`, flat. The design source renders a
//  fuchsia gradient hero, but there is no fuchsia/persona token in the
//  design system (only Personal / Home / Business pillars), and every
//  shipped persona surface (Audience Profile, Broadcast detail, Membership)
//  uses the sky primary. We mirror that — no new tokens, no mobile-side
//  gradient.
//

import Foundation

/// Which frame the editor renders.
public enum EditPersonaVariant: Sendable, Hashable {
    case live
    case setup
}

/// Live availability of the chosen handle. Drives the inline status pill on
/// `HandleField` (reserved → lock, available → check).
public enum PersonaHandleStatus: Sendable, Hashable {
    /// Locked to this creator — the live persona, or a 24h setup hold.
    case reserved
    /// Free to claim.
    case available
    /// Already in use by someone else.
    case taken
}

/// One content-category chip in the policy block ("what this persona may /
/// may not post about").
public struct PersonaCategoryChip: Sendable, Hashable, Identifiable {
    public let id: String
    public let label: String
    public let icon: PantopusIcon

    public init(label: String, icon: PantopusIcon) {
        id = label
        self.label = label
        self.icon = icon
    }
}

/// One row in the SETUP checklist hero.
public struct PersonaChecklistStep: Sendable, Hashable, Identifiable {
    public let id: String
    public let label: String
    public let done: Bool
    /// The single "do this next" step — rendered with the accent ring + a
    /// trailing "Next" marker.
    public let isNext: Bool

    public init(id: String, label: String, done: Bool, isNext: Bool = false) {
        self.id = id
        self.label = label
        self.done = done
        self.isNext = isNext
    }
}

/// A creator-side tier card. Deliberately named to disambiguate from the
/// fan-side `MembershipTier` in A10.8 — this is the *configuration* card a
/// creator edits, not the membership a fan holds.
public struct PersonaTierCard: Sendable, Hashable, Identifiable {
    public enum Kind: Sendable, Hashable {
        case free
        case paid
        /// Paid tier dimmed + locked because Stripe isn't connected yet.
        case paidLocked
    }

    /// Per-card Stripe footer state. `none` = no footer (free tier).
    public enum StripeState: Sendable, Hashable {
        case none
        case ready
        case needsStripe
    }

    public let id: String
    public let name: String
    public let kind: Kind
    /// Display price without the leading `$` ("3", "8", or "—" when locked).
    public let priceLabel: String?
    /// Billing period ("mo").
    public let period: String?
    public let blurb: String
    public let perks: [String]
    public let stripeState: StripeState
    /// Highlight ring for a freshly-added tier awaiting setup.
    public let isFresh: Bool

    public init(
        id: String,
        name: String,
        kind: Kind,
        priceLabel: String? = nil,
        period: String? = nil,
        blurb: String,
        perks: [String] = [],
        stripeState: StripeState = .none,
        isFresh: Bool = false
    ) {
        self.id = id
        self.name = name
        self.kind = kind
        self.priceLabel = priceLabel
        self.period = period
        self.blurb = blurb
        self.perks = perks
        self.stripeState = stripeState
        self.isFresh = isFresh
    }
}

/// Stripe Connect onboarding state for the persona.
public enum PersonaStripeState: Sendable, Hashable {
    case connected(account: String)
    case notConnected
}

/// Posting-cap segmented options. Order + labels mirror the design's
/// `CapSelector` (1/wk · 3/wk · Daily · Unlimited).
public enum PersonaCapOption: String, Sendable, Hashable, CaseIterable, Identifiable {
    case weekly1 = "1/wk"
    case weekly3 = "3/wk"
    case daily = "Daily"
    case unlimited = "Unlimited"

    public var id: String {
        rawValue
    }

    public var label: String {
        rawValue
    }
}

/// Composed editor content shared by both frames. Frame-specific emphasis
/// (live hero stats vs. setup checklist) is selected by the enclosing
/// `EditPersonaState` case; the unused fields simply aren't rendered.
public struct EditPersonaContent: Sendable, Hashable {
    public let personaId: String
    public let handle: String
    public let displayName: String
    public let bio: String
    public let bioCharCount: String
    public let handleStatus: PersonaHandleStatus
    /// Optional note under the handle field (setup: "Reserved for 24h…").
    public let handleNote: String?

    // Live hero stat strip.
    public let followers: String
    public let posts: String
    public let rating: String
    public let liveBadge: String

    // Setup checklist hero.
    public let checklist: [PersonaChecklistStep]
    public let checklistSummary: String

    // Category policy.
    public let categoriesAllow: [PersonaCategoryChip]
    public let categoriesAllowSub: String
    public let categoriesOff: [PersonaCategoryChip]
    public let categoriesOffSub: String
    public let policyNote: String?

    // Tiers.
    public let stripe: PersonaStripeState
    public let tiers: [PersonaTierCard]
    public let canAddTier: Bool

    // Broadcast.
    public let cap: PersonaCapOption
    public let quietHoursOn: Bool
    public let quietHoursRange: String

    // Share.
    public let shareUrl: String
    public let shareIsPublic: Bool

    // Analytics.
    public let analyticsOn: Bool
    public let analyticsScope: [String]

    public init(
        personaId: String,
        handle: String,
        displayName: String,
        bio: String,
        bioCharCount: String,
        handleStatus: PersonaHandleStatus,
        handleNote: String? = nil,
        followers: String = "",
        posts: String = "",
        rating: String = "",
        liveBadge: String = "Live",
        checklist: [PersonaChecklistStep] = [],
        checklistSummary: String = "",
        categoriesAllow: [PersonaCategoryChip],
        categoriesAllowSub: String,
        categoriesOff: [PersonaCategoryChip],
        categoriesOffSub: String,
        policyNote: String? = nil,
        stripe: PersonaStripeState,
        tiers: [PersonaTierCard],
        canAddTier: Bool,
        cap: PersonaCapOption,
        quietHoursOn: Bool,
        quietHoursRange: String,
        shareUrl: String,
        shareIsPublic: Bool,
        analyticsOn: Bool,
        analyticsScope: [String]
    ) {
        self.personaId = personaId
        self.handle = handle
        self.displayName = displayName
        self.bio = bio
        self.bioCharCount = bioCharCount
        self.handleStatus = handleStatus
        self.handleNote = handleNote
        self.followers = followers
        self.posts = posts
        self.rating = rating
        self.liveBadge = liveBadge
        self.checklist = checklist
        self.checklistSummary = checklistSummary
        self.categoriesAllow = categoriesAllow
        self.categoriesAllowSub = categoriesAllowSub
        self.categoriesOff = categoriesOff
        self.categoriesOffSub = categoriesOffSub
        self.policyNote = policyNote
        self.stripe = stripe
        self.tiers = tiers
        self.canAddTier = canAddTier
        self.cap = cap
        self.quietHoursOn = quietHoursOn
        self.quietHoursRange = quietHoursRange
        self.shareUrl = shareUrl
        self.shareIsPublic = shareIsPublic
        self.analyticsOn = analyticsOn
        self.analyticsScope = analyticsScope
    }

    /// `@handle` for the top-bar subtitle + share row.
    public var atHandle: String {
        "@\(handle)"
    }
}

/// Top-level editor state. `.live` and `.setup` carry the same content
/// shape; `.setup` adds the checklist progress counters consumed by the
/// hero's progress bar.
public enum EditPersonaState: Sendable {
    case loading
    case live(EditPersonaContent)
    case setup(EditPersonaContent, stepsDone: Int, stepsTotal: Int)
    case error(message: String)
}
