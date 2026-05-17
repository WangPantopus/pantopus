//
//  HubState.swift
//  Pantopus
//
//  Projected state model that the Hub view consumes. The ViewModel
//  derives this from `/api/hub`, `/api/hub/today`, and
//  `/api/hub/discovery` responses.
//

import Foundation

/// Top-level Hub lifecycle state.
public enum HubState: Sendable {
    case skeleton
    case firstRun(FirstRunContent)
    case populated(PopulatedContent)
    case error(message: String)

    /// Content shown to new users while their hub is still "empty".
    public struct FirstRunContent: Sendable {
        public let greeting: String
        public let name: String
        public let avatarInitials: String
        public let identity: IdentityPillar
        public let ringProgress: Double
        public let profileCompleteness: Double
        public let stepsDone: Int
        public let stepsTotal: Int
        public let steps: [SetupStep]
        public let pillars: [PillarTile]
        public let discovery: [DiscoveryCardContent]
    }

    /// The fully-assembled hub bundle.
    public struct PopulatedContent: Sendable {
        public let topBar: TopBarContent
        public let actionChips: [ActionChipContent]
        public let setupBanner: SetupBannerContent?
        public let today: TodaySummary?
        public let pillars: [PillarTile]
        public let discovery: [DiscoveryCardContent]
        public let jumpBackIn: [JumpBackItem]
        public let activity: [ActivityEntry]
    }
}

/// Setup-step row projection.
public struct SetupStep: Identifiable, Sendable {
    public let id: String
    public let title: String
    public let done: Bool

    public init(id: String, title: String, done: Bool) {
        self.id = id
        self.title = title
        self.done = done
    }
}

/// Top-bar header content.
public struct TopBarContent: Sendable {
    public let greeting: String
    public let name: String
    public let avatarInitials: String
    /// Identity pillar that tints the avatar ring. The design uses the
    /// user's primary identity scope (personal / home / business).
    public let identity: IdentityPillar
    public let ringProgress: Double
    public let unreadCount: Int

    public init(
        greeting: String,
        name: String,
        avatarInitials: String,
        identity: IdentityPillar = .personal,
        ringProgress: Double,
        unreadCount: Int
    ) {
        self.greeting = greeting
        self.name = name
        self.avatarInitials = avatarInitials
        self.identity = identity
        self.ringProgress = ringProgress
        self.unreadCount = unreadCount
    }
}

/// Chip rendered in the action strip.
public struct ActionChipContent: Identifiable, Sendable {
    public enum Kind: String, Sendable {
        case postTask, snapAndSell, scanMail, addHome
    }

    public let id: String
    public let kind: Kind
    public let label: String
    public let icon: PantopusIcon
    public let active: Bool

    public init(kind: Kind, label: String, icon: PantopusIcon, active: Bool) {
        id = kind.rawValue
        self.kind = kind
        self.label = label
        self.icon = icon
        self.active = active
    }
}

/// Setup banner payload (amber card).
public struct SetupBannerContent: Sendable {
    public let title: String
    public let ctaTitle: String

    public init(title: String = "Verify your address", ctaTitle: String = "Start") {
        self.title = title
        self.ctaTitle = ctaTitle
    }
}

/// Today card — weather + AQI + commute.
public struct TodaySummary: Sendable {
    public let temperatureFahrenheit: Int?
    public let conditions: String?
    public let aqiLabel: String?
    public let commuteLabel: String?

    public init(
        temperatureFahrenheit: Int? = nil,
        conditions: String? = nil,
        aqiLabel: String? = nil,
        commuteLabel: String? = nil
    ) {
        self.temperatureFahrenheit = temperatureFahrenheit
        self.conditions = conditions
        self.aqiLabel = aqiLabel
        self.commuteLabel = commuteLabel
    }
}

/// One of the 4 pillar tiles in the 2×2 grid.
public struct PillarTile: Identifiable, Sendable {
    public enum Pillar: String, Sendable, CaseIterable {
        case pulse, marketplace, gigs, mail
    }

    public let id: String
    public let pillar: Pillar
    public let label: String
    public let icon: PantopusIcon
    public let tint: IdentityPillar
    /// Either a numeric count ("3 new") or a string cue ("Set up").
    public let chip: String?
    public let chipSetupState: Bool
    /// 10.5pt fg3 caption beneath the label — design's per-tile context
    /// line (e.g. "Jorge left a rec" / "3 saved · 9 nearby"). Optional;
    /// when nil the label sits alone.
    public let caption: String?

    public init(
        pillar: Pillar,
        label: String,
        icon: PantopusIcon,
        tint: IdentityPillar,
        chip: String?,
        chipSetupState: Bool,
        caption: String? = nil
    ) {
        id = pillar.rawValue
        self.pillar = pillar
        self.label = label
        self.icon = icon
        self.tint = tint
        self.chip = chip
        self.chipSetupState = chipSetupState
        self.caption = caption
    }
}

/// Kind of entity surfaced by a Hub discovery card. Used by the
/// navigation host to dispatch a tap to the matching detail screen.
public enum DiscoveryKind: String, Sendable {
    case gig, person, business, post, unknown

    public init(rawType: String) {
        self = DiscoveryKind(rawValue: rawType) ?? .unknown
    }
}

/// Discovery rail card.
public struct DiscoveryCardContent: Identifiable, Sendable {
    public let id: String
    public let title: String
    public let meta: String
    public let category: String
    public let avatarInitials: String
    public let kind: DiscoveryKind
    /// Pillar tint that drives the top-half gradient + the trailing chip
    /// color (per the design's per-card tint).
    public let tint: IdentityPillar

    public init(
        id: String,
        title: String,
        meta: String,
        category: String,
        avatarInitials: String,
        kind: DiscoveryKind,
        tint: IdentityPillar = .personal
    ) {
        self.id = id
        self.title = title
        self.meta = meta
        self.category = category
        self.avatarInitials = avatarInitials
        self.kind = kind
        self.tint = tint
    }
}

/// "Jump back in" rail card. `route` is the canonical web path
/// returned by `GET /api/hub` (e.g. `/app/mailbox?scope=home&homeId=…`);
/// the navigation host parses it to pick the native destination.
public struct JumpBackItem: Identifiable, Sendable {
    public let id: String
    public let title: String
    public let icon: PantopusIcon
    public let route: String
    /// Pillar tint that drives the icon disk + progress bar fill. Backend
    /// doesn't carry this today; the VM derives it from the resolved
    /// route's pillar.
    public let tint: IdentityPillar
    /// Small uppercase overline above the title — design uses "In progress"
    /// or "Draft".
    public let kicker: String
    /// Progress-text line below the bar (e.g. "Step 2 of 3 · Budget").
    /// Optional; when nil the bar is hidden entirely.
    public let progressLabel: String?
    /// 0..1 fraction for the progress bar. Optional; when nil the bar
    /// is hidden.
    public let progressFraction: Double?

    public init(
        id: String,
        title: String,
        icon: PantopusIcon,
        route: String,
        tint: IdentityPillar = .personal,
        kicker: String = "In progress",
        progressLabel: String? = nil,
        progressFraction: Double? = nil
    ) {
        self.id = id
        self.title = title
        self.icon = icon
        self.route = route
        self.tint = tint
        self.kicker = kicker
        self.progressLabel = progressLabel
        self.progressFraction = progressFraction
    }
}

/// Recent-activity row.
public struct ActivityEntry: Identifiable, Sendable {
    public let id: String
    public let title: String
    public let timeAgo: String
    public let icon: PantopusIcon
    public let tint: IdentityPillar

    public init(id: String, title: String, timeAgo: String, icon: PantopusIcon, tint: IdentityPillar) {
        self.id = id
        self.title = title
        self.timeAgo = timeAgo
        self.icon = icon
        self.tint = tint
    }
}
