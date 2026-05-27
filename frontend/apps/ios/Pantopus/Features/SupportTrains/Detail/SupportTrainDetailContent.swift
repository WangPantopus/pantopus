//
//  SupportTrainDetailContent.swift
//  Pantopus
//
//  A10.9 — Render payloads for the participant-facing Support Train
//  detail screen. Pure value types so the view-model can be fed
//  deterministic stub data (`SupportTrainDetailSampleData`) and every
//  state snapshots reproducibly. Colour is expressed as a semantic
//  `SupportTrainKind`; the view layer maps it onto `Theme.Color` so
//  the model stays free of SwiftUI types.
//
//  Two designed variants share this model:
//   - `populated`     12 / 21 slots covered · 9 open · `PrimaryCTA` dock.
//   - `fullyCovered`  21 / 21 covered · `CelebrationBanner` at top · split
//                     `Send a card` + `Join as backup` dock.
//

import Foundation

/// Per-archetype palette. Currently drives the `TypeDatesCard` icon
/// tile + the `RecipientCard` avatar gradient. Mirrors the
/// `SupportTrainType` enum that the list feeds project so a future
/// backend round-trip lights up the same accent without re-mapping.
public enum SupportTrainKind: String, Sendable, Hashable {
    case meals
    case rides
    case childcare
    case petcare
    case errands
    case visits
    case generic
}

/// Sticky bottom dock variant. `signUp` is the populated default;
/// `sendCardAndBackup` is the fully-covered split dock.
public enum SupportTrainDock: Sendable, Hashable {
    case signUp(label: String)
    case sendCardAndBackup
}

/// The recipient block at the top of the screen. The household line is
/// foregrounded with a quote from the recipient so the request reads
/// human, not as a request for charity.
public struct RecipientCardContent: Equatable, Sendable {
    public let initials: String
    public let householdName: String
    /// `home` / `personal` / `business` — drives the identity chip tint
    /// + verified disc colour.
    public let identityTag: IdentityTag
    public let verified: Bool
    public let address: String
    /// "2 blocks from you" — locality hint, optional.
    public let proximity: String?
    public let quote: String
    public let quoteAttribution: String?

    public enum IdentityTag: String, Sendable, Hashable {
        case home
        case personal
        case business
    }

    public init(
        initials: String,
        householdName: String,
        identityTag: IdentityTag,
        verified: Bool,
        address: String,
        proximity: String? = nil,
        quote: String,
        quoteAttribution: String? = nil
    ) {
        self.initials = initials
        self.householdName = householdName
        self.identityTag = identityTag
        self.verified = verified
        self.address = address
        self.proximity = proximity
        self.quote = quote
        self.quoteAttribution = quoteAttribution
    }
}

/// One participant slot in the contributor strip (4 avatars + +N
/// overflow). Tracked separately from the slot rows because the strip
/// shows *unique helpers*, while a row is *per slot*.
public struct ContributorBubble: Equatable, Sendable, Identifiable {
    public let id: String
    public let initials: String
    /// Semantic palette swatch. Mirrors the dish-author tint in
    /// `SlotRowContent.author`. The view layer maps it onto a
    /// `Theme.Color`.
    public let tone: ContributorTone

    public enum ContributorTone: String, Sendable, Hashable {
        case warning
        case primary
        case business
        case success
        case error
        case personal
    }

    public init(id: String, initials: String, tone: ContributorTone) {
        self.id = id
        self.initials = initials
        self.tone = tone
    }
}

/// The big "type + dates + progress" card. Carries everything needed
/// to render the icon tile, title + dates strip, status pill, the
/// progress bar with sky gradient, and the contributor strip.
public struct TypeDatesCardContent: Equatable, Sendable {
    public let kind: SupportTrainKind
    public let title: String
    public let dateRange: String
    public let daysLeft: Int
    public let slotsFilled: Int
    public let slotsTotal: Int
    /// Up to four bubble previews + an `extraCount` for the trailing
    /// "+N" disc. The view truncates / pads as needed.
    public let contributors: [ContributorBubble]
    public let extraCount: Int

    public var isFullyCovered: Bool { slotsFilled >= slotsTotal && slotsTotal > 0 }

    /// 0…100, rounded. `0` when total is zero (defensive).
    public var percentCovered: Int {
        guard slotsTotal > 0 else { return 0 }
        return Int((Double(slotsFilled) / Double(slotsTotal) * 100).rounded())
    }

    public init(
        kind: SupportTrainKind,
        title: String,
        dateRange: String,
        daysLeft: Int,
        slotsFilled: Int,
        slotsTotal: Int,
        contributors: [ContributorBubble],
        extraCount: Int
    ) {
        self.kind = kind
        self.title = title
        self.dateRange = dateRange
        self.daysLeft = daysLeft
        self.slotsFilled = slotsFilled
        self.slotsTotal = slotsTotal
        self.contributors = contributors
        self.extraCount = extraCount
    }
}

/// A single row in the `Open slots near you` / `Already on the train`
/// / `Your commitment` sections. The same row recipe carries every
/// state — the view layer flips the trailing affordance (`Sign up`
/// pill / check disc / `Edit` ghost) off `state`.
public struct SlotRowContent: Equatable, Sendable, Identifiable {
    public let id: String
    public let dayLabel: String
    public let dateLabel: String
    public let state: SlotRowState
    /// Helper attribution — populated for covered slots only.
    public let author: SlotAuthor?
    /// Title text. For open slots: "Open · dinner for 4". For covered:
    /// the dish / contribution line ("Lentil soup + cornbread").
    public let title: String
    /// Sub-meta — drop window, viewer hint, etc. Optional.
    public let subtitle: String?
    /// `true` when the viewer's own commitment. Renders the sky outline
    /// + "Your slot" chip + Edit affordance.
    public let mine: Bool

    public enum SlotRowState: String, Sendable, Hashable {
        case open
        case covered
    }

    public struct SlotAuthor: Equatable, Sendable {
        public let initials: String
        public let displayName: String
        public let tone: ContributorBubble.ContributorTone

        public init(
            initials: String,
            displayName: String,
            tone: ContributorBubble.ContributorTone
        ) {
            self.initials = initials
            self.displayName = displayName
            self.tone = tone
        }
    }

    public init(
        id: String,
        dayLabel: String,
        dateLabel: String,
        state: SlotRowState,
        author: SlotAuthor? = nil,
        title: String,
        subtitle: String? = nil,
        mine: Bool = false
    ) {
        self.id = id
        self.dayLabel = dayLabel
        self.dateLabel = dateLabel
        self.state = state
        self.author = author
        self.title = title
        self.subtitle = subtitle
        self.mine = mine
    }
}

/// The organizer footer pinned at the bottom of the body.
public struct HostedByFooter: Equatable, Sendable {
    public let organizerInitials: String
    public let organizerDisplayName: String
    public let neighborHint: String?

    public init(organizerInitials: String, organizerDisplayName: String, neighborHint: String?) {
        self.organizerInitials = organizerInitials
        self.organizerDisplayName = organizerDisplayName
        self.neighborHint = neighborHint
    }
}

/// One stack of slot rows ("Open slots near you" · "Already on the
/// train" · "Your commitment" · "Next up"). Carries an optional action
/// label that surfaces as a trailing `See all N` button.
public struct SlotSection: Equatable, Sendable, Identifiable {
    public let id: String
    public let overline: String
    public let actionLabel: String?
    public let rows: [SlotRowContent]

    public init(id: String, overline: String, actionLabel: String? = nil, rows: [SlotRowContent]) {
        self.id = id
        self.overline = overline
        self.actionLabel = actionLabel
        self.rows = rows
    }
}

/// Full render payload for the participant-facing Support Train detail
/// screen. The two designed variants are both expressible as this
/// payload; the VM picks `populated` vs `fullyCovered` off
/// `typeDates.isFullyCovered`.
public struct SupportTrainDetailContent: Equatable, Sendable {
    public let trainId: String
    public let recipient: RecipientCardContent
    public let typeDates: TypeDatesCardContent
    /// 28 days in row-major order (week 0 Mon…Sun … week 3 Mon…Sun).
    /// The view passes these straight into `SlotCalendar`.
    public let calendarDays: [SlotCalendarDay]
    /// One or more row stacks. The first is conventionally the open
    /// slots (in the populated variant) or the viewer's own commitment
    /// (in the fully-covered variant); the second is "Already on the
    /// train" / "Next up".
    public let sections: [SlotSection]
    public let hostedBy: HostedByFooter
    public let dock: SupportTrainDock
    /// Optional celebration banner — shown at the top of the body in
    /// the fully-covered variant.
    public let celebrationBanner: CelebrationBanner?

    public struct CelebrationBanner: Equatable, Sendable {
        public let title: String
        public let body: String

        public init(title: String, body: String) {
            self.title = title
            self.body = body
        }
    }

    public var isFullyCovered: Bool { typeDates.isFullyCovered }

    public init(
        trainId: String,
        recipient: RecipientCardContent,
        typeDates: TypeDatesCardContent,
        calendarDays: [SlotCalendarDay],
        sections: [SlotSection],
        hostedBy: HostedByFooter,
        dock: SupportTrainDock,
        celebrationBanner: CelebrationBanner? = nil
    ) {
        self.trainId = trainId
        self.recipient = recipient
        self.typeDates = typeDates
        self.calendarDays = calendarDays
        self.sections = sections
        self.hostedBy = hostedBy
        self.dock = dock
        self.celebrationBanner = celebrationBanner
    }
}
