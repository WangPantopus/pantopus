//
//  ComposeBroadcastContent.swift
//  Pantopus
//
//  A.7 (A22.2) Compose Broadcast — render-only models for the
//  full-screen broadcast composer pushed from the Audience Profile.
//  The inline Audience Profile composer remains a lightweight
//  quick-post; this surface is the canonical broadcast composer
//  (large editor + media + audience targeting + scheduling + recent
//  broadcast analytics). No backend: the VM is seeded with sample
//  data matching the eventual API shapes.
//

import Foundation

/// Targeting for a broadcast. Mirrors the audience-frames TierChip map
/// ("All beacons" public reach down to per-tier locks). `tierRank` maps
/// onto the persona tier ladder so chip colors stay consistent with
/// `AudienceProfileView.tierColor(rank:)` (nil = all beacons / public).
public enum BroadcastAudience: String, Sendable, Hashable, CaseIterable, Identifiable {
    case allBeacons
    case followersOnly
    case bronzePlus
    case silverPlus
    case goldOnly

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .allBeacons: "All beacons"
        case .followersOnly: "Followers only"
        case .bronzePlus: "Bronze+"
        case .silverPlus: "Silver+"
        case .goldOnly: "Gold only"
        }
    }

    /// Lucide-token icon for the chip. Public reach gets a globe; the
    /// tier-locked options get a lock; followers gets the people glyph.
    public var icon: PantopusIcon {
        switch self {
        case .allBeacons: .globe
        case .followersOnly: .users
        case .bronzePlus, .silverPlus, .goldOnly: .lock
        }
    }

    /// Persona tier rank used to color the chip via
    /// `AudienceProfileView.tierColor(rank:)`. `nil` for all-beacons.
    public var tierRank: Int? {
        switch self {
        case .allBeacons: nil
        case .followersOnly: 1
        case .bronzePlus: 2
        case .silverPlus: 3
        case .goldOnly: 4
        }
    }

    /// True for every targeting option that isn't the public broadcast.
    public var isRestricted: Bool { self != .allBeacons }
}

/// One attached media item in the composer. `imageData` carries the
/// bytes when the user picks a real photo (rendered inline); sample /
/// snapshot data leaves it `nil` so the preview falls back to a tinted
/// placeholder — deterministic for snapshot baselines.
public struct ComposeMediaPreview: Sendable, Hashable, Identifiable {
    public enum Kind: String, Sendable, Hashable {
        case image
        case video
    }

    public let id: String
    public let kind: Kind
    public let caption: String?
    public let imageData: Data?

    public init(id: String = UUID().uuidString, kind: Kind, caption: String?, imageData: Data? = nil) {
        self.id = id
        self.kind = kind
        self.caption = caption
        self.imageData = imageData
    }
}

/// The mutable composer payload — what would be POSTed on send. Kept a
/// value type so the VM can snapshot it for the send call and compare
/// against the last-saved copy for the unsaved-draft indicator.
public struct ComposeBroadcastDraft: Sendable, Hashable {
    public var body: String
    public var audience: BroadcastAudience
    public var media: ComposeMediaPreview?

    public init(
        body: String = "",
        audience: BroadcastAudience = .allBeacons,
        media: ComposeMediaPreview? = nil
    ) {
        self.body = body
        self.audience = audience
        self.media = media
    }

    /// Nothing worth sending — empty body (whitespace-trimmed) and no
    /// media. Audience choice alone doesn't count as content.
    public var isEmpty: Bool {
        body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && media == nil
    }
}

/// The persona a broadcast is sent as. Drives the composer's PersonaRow
/// (avatar initial + handle + "Sending as <pillar>" + identity tone).
public struct BroadcastPersona: Sendable, Hashable {
    public let id: String
    public let handle: String
    public let displayName: String
    public let kind: IdentityKind
    public let avatarInitial: String

    public init(
        id: String,
        handle: String,
        displayName: String,
        kind: IdentityKind,
        avatarInitial: String
    ) {
        self.id = id
        self.handle = handle
        self.displayName = displayName
        self.kind = kind
        self.avatarInitial = avatarInitial
    }
}

/// One recent broadcast with inline analytics — borrows the audience
/// analytics inline pattern. Counts are pre-formatted display strings
/// ("1.1K", "78%") so the row stays render-only.
public struct RecentBroadcastContent: Sendable, Hashable, Identifiable {
    public let id: String
    public let timeLabel: String
    public let audience: BroadcastAudience
    public let body: String
    public let reach: String
    public let read: String
    public let readPct: String
    public let reactions: String
    public let replies: String
    public let hasMedia: Bool

    public init(
        id: String,
        timeLabel: String,
        audience: BroadcastAudience,
        body: String,
        reach: String,
        read: String,
        readPct: String,
        reactions: String,
        replies: String,
        hasMedia: Bool
    ) {
        self.id = id
        self.timeLabel = timeLabel
        self.audience = audience
        self.body = body
        self.reach = reach
        self.read = read
        self.readPct = readPct
        self.reactions = reactions
        self.replies = replies
        self.hasMedia = hasMedia
    }
}

/// Top-level composer state. The screen always renders the editor; this
/// enum tracks the composer *phase* the design's two frames + the send
/// lifecycle imply. `.empty` is the first-broadcast prompt (just opened),
/// `.composing` once there's content, `.scheduled` once a send time is
/// pinned, `.sending` mid-submit, `.error` on a failed send (the draft is
/// preserved so the user can retry).
public enum ComposeBroadcastState: Equatable, Sendable {
    case empty
    case composing(ComposeBroadcastDraft)
    case scheduled(ComposeBroadcastDraft, sendAt: Date)
    case sending
    case error(message: String)
}
