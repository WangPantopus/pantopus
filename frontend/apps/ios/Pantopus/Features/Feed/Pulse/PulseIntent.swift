//
//  PulseIntent.swift
//  Pantopus
//
//  Six-way classification for Pulse posts. Drives the chip-row filter,
//  the per-card colored chip, the reaction-verb set, and the compose
//  FAB's pre-fill. The `all` case is a chip-row-only sentinel; real
//  posts always resolve to one of the other five.
//

import Foundation

/// One of the six chip-row intents.
public enum PulseIntent: String, CaseIterable, Sendable, Hashable {
    case all
    case ask
    case recommend
    case event
    case lost
    case announce

    /// Chip-row display label.
    public var label: String {
        switch self {
        case .all: return "All"
        case .ask: return "Ask"
        case .recommend: return "Recommend"
        case .event: return "Event"
        case .lost: return "Lost & Found"
        case .announce: return "Announce"
        }
    }

    /// Right-aligned per-card chip label (shorter than the chip-row label).
    public var cardChipLabel: String {
        switch self {
        case .all: return ""
        case .ask: return "Ask"
        case .recommend: return "Rec"
        case .event: return "Event"
        case .lost: return "Lost"
        case .announce: return "Announce"
        }
    }

    /// Backend `post_type` filter value sent on `/api/posts/feed`. `all`
    /// returns `nil` so the backend skips the filter.
    public var postType: String? {
        switch self {
        case .all: return nil
        case .ask: return "ask_local"
        case .recommend: return "recommendation"
        case .event: return "event"
        case .lost: return "lost_found"
        case .announce: return "local_update"
        }
    }

    /// Resolve a backend `post_type` string back to a UI intent. Unknown
    /// types fall through to `.announce` — the most generic chip — so
    /// the card still renders a meaningful intent indicator.
    public static func from(postType: String?) -> PulseIntent {
        switch postType ?? "" {
        case "ask_local", "ask": return .ask
        case "recommendation", "recommend": return .recommend
        case "event": return .event
        case "lost_found": return .lost
        case "local_update", "announcement", "heads_up", "neighborhood_win": return .announce
        default: return .announce
        }
    }
}

public extension PulseIntent {
    /// Pantopus icon used inside the per-card intent chip.
    var icon: PantopusIcon {
        switch self {
        case .all: return .info
        case .ask: return .helpCircle
        case .recommend: return .thumbsUp
        case .event: return .calendar
        case .lost: return .search
        case .announce: return .megaphone
        }
    }
}

/// One reaction kind shown in the bottom strip of a post card. The
/// backend only persists `like` (helpful); the other counts are
/// display-only and intent-shaped to match the design.
public struct PulseReaction: Sendable, Hashable, Identifiable {
    public enum Kind: String, Sendable, Hashable {
        case helpful, heart, going, seen, shared
    }

    public let id: Kind
    public let kind: Kind
    public let icon: PantopusIcon
    public let label: String
    public let count: Int
    /// True iff this reaction maps to the `like` toggle endpoint.
    public let isInteractive: Bool

    public init(kind: Kind, icon: PantopusIcon, label: String, count: Int, isInteractive: Bool) {
        id = kind
        self.kind = kind
        self.icon = icon
        self.label = label
        self.count = count
        self.isInteractive = isInteractive
    }
}

public extension PulseIntent {
    /// Returns the reaction strip the design specifies for this intent.
    /// The first kind is always the one wired to `POST /:id/like`; the
    /// rest are display-only counts.
    func reactionTemplate(helpfulCount: Int, secondaryCount: Int = 0) -> [PulseReaction] {
        switch self {
        case .ask:
            return [
                PulseReaction(kind: .helpful, icon: .lightbulb, label: "helpful", count: helpfulCount, isInteractive: true),
                PulseReaction(kind: .heart, icon: .heart, label: "", count: secondaryCount, isInteractive: false)
            ]
        case .recommend:
            return [
                PulseReaction(kind: .helpful, icon: .heart, label: "", count: helpfulCount, isInteractive: true),
                PulseReaction(kind: .heart, icon: .lightbulb, label: "helpful", count: secondaryCount, isInteractive: false)
            ]
        case .event:
            return [
                PulseReaction(kind: .going, icon: .check, label: "going", count: helpfulCount, isInteractive: true),
                PulseReaction(kind: .heart, icon: .heart, label: "", count: secondaryCount, isInteractive: false)
            ]
        case .lost:
            return [
                PulseReaction(kind: .seen, icon: .eye, label: "seen", count: helpfulCount, isInteractive: true),
                PulseReaction(kind: .shared, icon: .share, label: "shared", count: secondaryCount, isInteractive: false)
            ]
        case .announce, .all:
            return [
                PulseReaction(kind: .helpful, icon: .lightbulb, label: "helpful", count: helpfulCount, isInteractive: true),
                PulseReaction(kind: .heart, icon: .heart, label: "", count: secondaryCount, isInteractive: false)
            ]
        }
    }
}
