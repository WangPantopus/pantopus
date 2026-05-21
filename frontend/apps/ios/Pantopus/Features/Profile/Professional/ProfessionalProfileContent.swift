//
//  ProfessionalProfileContent.swift
//  Pantopus
//
//  A.5 (A13.11) — render models for the Professional Profile editor: the
//  Business-pillar identity surface (company, certifications, portfolio,
//  skills, visibility). Distinct from the Personal `EditProfile` (A13.9):
//  every high-trust claim (company affiliation, certifications) carries a
//  verification status, and the sticky-save bar is verification-aware.
//
//  Backend was removed from the repo, so these models are hydrated from
//  `ProfessionalProfileSampleData`; the shapes still mirror the eventual
//  API contract so the wiring is a drop-in once a route exists.
//

import SwiftUI

// MARK: - Verification status

/// Verification outcome for a high-trust claim (company affiliation or a
/// certification). Drives the inline pill color: verified → success,
/// pending → warning, expiring → error.
public enum ProVerificationStatus: String, Sendable, Hashable {
    /// Confirmed by Pantopus — green success pill.
    case verified
    /// Awaiting confirmation (usually 1–2 business days) — amber warning pill.
    case pending
    /// Claim was rejected or has not been backed by proof — red error pill.
    case unverified
    /// Credential nearing its expiry date — red error pill.
    case expiring

    /// Whether this status counts toward the "claims pending verification"
    /// total surfaced on the strength meter + sticky bar.
    public var isAwaitingReview: Bool {
        self == .pending
    }

    var label: String {
        switch self {
        case .verified: "Verified"
        case .pending: "Pending"
        case .unverified: "Unverified"
        case .expiring: "Expiring"
        }
    }

    var icon: PantopusIcon {
        switch self {
        case .verified: .badgeCheck
        case .pending: .clock
        case .unverified: .alertCircle
        case .expiring: .alertTriangle
        }
    }

    var foreground: Color {
        switch self {
        case .verified: Theme.Color.success
        case .pending: Theme.Color.warning
        case .unverified: Theme.Color.error
        case .expiring: Theme.Color.error
        }
    }

    var background: Color {
        switch self {
        case .verified: Theme.Color.successBg
        case .pending: Theme.Color.warningBg
        case .unverified: Theme.Color.errorBg
        case .expiring: Theme.Color.errorBg
        }
    }
}

// MARK: - Section models

/// The user's company affiliation. A claimed company can be confirmed,
/// awaiting confirmation, or nearing lapse.
public struct CompanyClaim: Sendable, Hashable {
    public var name: String
    public var locality: String
    public var status: ProVerificationStatus
    /// True when the company was changed in this editing session.
    public var isDirty: Bool
    /// Inline note shown beneath the field (e.g. the co-op confirmation copy).
    public var hint: String?

    public init(
        name: String,
        locality: String,
        status: ProVerificationStatus,
        isDirty: Bool = false,
        hint: String? = nil
    ) {
        self.name = name
        self.locality = locality
        self.status = status
        self.isDirty = isDirty
        self.hint = hint
    }
}

/// A trade / specialty chip. Skills carry no verification, but a chip added
/// this session shows a "fresh" amber dot.
public struct ProSkill: Sendable, Hashable, Identifiable {
    public let id: String
    public var label: String
    public var icon: PantopusIcon
    public var isFresh: Bool

    public init(id: String, label: String, icon: PantopusIcon, isFresh: Bool = false) {
        self.id = id
        self.label = label
        self.icon = icon
        self.isFresh = isFresh
    }
}

/// A certification card: name + issuer + dates + verification status.
public struct Certification: Sendable, Hashable, Identifiable {
    public let id: String
    public var name: String
    public var issuer: String
    public var issued: String
    public var expires: String
    public var status: ProVerificationStatus
    /// True when uploaded this session (amber ring + dot).
    public var isFresh: Bool

    public init(
        id: String,
        name: String,
        issuer: String,
        issued: String,
        expires: String,
        status: ProVerificationStatus,
        isFresh: Bool = false
    ) {
        self.id = id
        self.name = name
        self.issuer = issuer
        self.issued = issued
        self.expires = expires
        self.status = status
        self.isFresh = isFresh
    }
}

/// Resolution state for an auto-fetched portfolio link preview.
public enum PortfolioLinkState: Sendable, Hashable {
    /// Preview resolved and is showing.
    case resolved
    /// Preview is being fetched (spinner).
    case loading
    /// Preview fetch failed (error tint + retry copy).
    case error
}

/// A portfolio link with an auto-fetched site preview.
public struct PortfolioLink: Sendable, Hashable, Identifiable {
    public let id: String
    public var host: String
    public var title: String
    public var url: String
    public var state: PortfolioLinkState
    /// True when added this session (amber ring + dot).
    public var isFresh: Bool

    public init(
        id: String,
        host: String,
        title: String,
        url: String,
        state: PortfolioLinkState,
        isFresh: Bool = false
    ) {
        self.id = id
        self.host = host
        self.title = title
        self.url = url
        self.state = state
        self.isFresh = isFresh
    }

    /// Host-derived leading glyph. Behance → palette, YouTube → play-circle,
    /// everything else → a generic link glyph (SF Symbols ships no brand
    /// logos, matching the design's `link-2` fallback).
    public var icon: PantopusIcon {
        let host = host.lowercased()
        if host.contains("behance") { return .palette }
        if host.contains("youtube") || host.contains("youtu.be") { return .playCircle }
        return .link
    }
}

/// A visibility toggle row, optionally with a scope chip shown when on.
public struct ProVisibilityRow: Sendable, Hashable, Identifiable {
    public let id: String
    public var label: String
    public var sub: String?
    public var isOn: Bool
    /// Baseline used to detect a toggle change this session.
    public var originalOn: Bool
    /// Scope chip text (e.g. "Elm Park · 0.6 mi radius"), shown when on.
    public var scope: String?

    public init(
        id: String,
        label: String,
        sub: String? = nil,
        isOn: Bool,
        scope: String? = nil
    ) {
        self.id = id
        self.label = label
        self.sub = sub
        self.isOn = isOn
        originalOn = isOn
        self.scope = scope
    }

    /// True when toggled away from its loaded baseline this session.
    public var isDirty: Bool {
        isOn != originalOn
    }
}

// MARK: - Aggregate content

/// The full editable Professional-profile payload.
public struct ProfessionalProfileContent: Sendable, Equatable {
    /// Display name shown in the pillar header (e.g. "Maria Kovács").
    public var proName: String
    /// Profile strength 0–100; gates the Pro+ tier.
    public var strength: Int
    public var title: FormFieldState
    public var yearsInRole: FormFieldState
    public var company: CompanyClaim
    public var skills: [ProSkill]
    public var certifications: [Certification]
    public var portfolio: [PortfolioLink]
    public var visibility: [ProVisibilityRow]

    public init(
        proName: String,
        strength: Int,
        title: FormFieldState,
        yearsInRole: FormFieldState,
        company: CompanyClaim,
        skills: [ProSkill],
        certifications: [Certification],
        portfolio: [PortfolioLink],
        visibility: [ProVisibilityRow]
    ) {
        self.proName = proName
        self.strength = strength
        self.title = title
        self.yearsInRole = yearsInRole
        self.company = company
        self.skills = skills
        self.certifications = certifications
        self.portfolio = portfolio
        self.visibility = visibility
    }

    /// Number of unsaved edits made this session — drives the "N edits"
    /// pill and whether the form is dirty.
    public var dirtyCount: Int {
        var count = 0
        if title.isDirty { count += 1 }
        if yearsInRole.isDirty { count += 1 }
        if company.isDirty { count += 1 }
        count += skills.filter(\.isFresh).count
        count += certifications.filter(\.isFresh).count
        count += portfolio.filter(\.isFresh).count
        count += visibility.filter(\.isDirty).count
        return count
    }

    /// Number of claims awaiting verification (company + certs). Drives the
    /// strength caption and the sticky bar's SLA note.
    public var pendingCount: Int {
        var count = 0
        if company.status.isAwaitingReview { count += 1 }
        count += certifications.filter(\.status.isAwaitingReview).count
        return count
    }

    public var isDirty: Bool {
        dirtyCount > 0
    }

    /// Strength-meter caption — accurate regardless of dirty state so the
    /// post-submit "verified but in review" case reads correctly.
    public var strengthCaption: String {
        pendingCount == 0
            ? "All claims verified · ready for high-trust clients."
            : "\(pendingCount) \(pendingCount == 1 ? "claim" : "claims") pending verification · finish to reach Pro+."
    }
}

// MARK: - Screen state

/// Top-level render state for the Professional Profile editor.
public enum ProfessionalProfileState: Sendable, Equatable {
    case loading
    /// Published & clean — no unsaved edits. Save is disabled.
    case verified(ProfessionalProfileContent)
    /// Unsaved edits present — `dirtyCount` edits, `pendingCount` of which
    /// are new claims needing 1–2 day verification.
    case pending(ProfessionalProfileContent, dirtyCount: Int, pendingCount: Int)
    case error(message: String)
}
