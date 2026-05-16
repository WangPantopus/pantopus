//
//  RowModel.swift
//  Pantopus
//
//  Template-agnostic row payload. Every list-of-rows screen maps its
//  backend DTOs into one of these.
//
//  T5.0 — extended additively for the My posts / My bids / My tasks /
//  Connections / Notifications / Discover hub / Bills / Pets / Offers /
//  Listing offers / Review claims designs. Every existing call site
//  (Notifications v1, MyHomes, MyClaims, Mailbox list/drawers) compiles
//  unchanged because:
//    - existing enum cases are untouched (`.icon`, `.avatar`, `.none` on
//      `RowLeading`; `.statusChip`, `.chevron`, `.kebab`, `.none` on
//      `RowTrailing`; `.statusChip`, `.fileChevron`, `.avatarKebab` on
//      `RowTemplate`),
//    - new `RowModel` fields are optional with `nil` defaults,
//    - `RowSection` retains its original initializer signature and adds
//      new optional `count` / `onSeeAll` / `style` parameters with
//      defaults.
//

import SwiftUI

// swiftlint:disable enum_case_associated_values_count

/// Visual template for a list row.
public enum RowTemplate: Sendable {
    /// Title + optional subtitle + trailing status chip.
    case statusChip
    /// Leading icon + title + trailing chevron — for drill-down lists.
    case fileChevron
    /// Leading avatar + title + trailing kebab (more-horizontal) menu.
    case avatarKebab
}

// MARK: - Leading

/// Two-stop gradient for category / avatar / thumbnail backgrounds.
public struct GradientPair: Sendable, Hashable {
    public let start: Color
    public let end: Color
    public init(start: Color, end: Color) {
        self.start = start
        self.end = end
    }
}

/// Size variants for the avatar-with-verified-badge leading.
public enum AvatarBadgeSize: Sendable, Hashable {
    /// 36pt — used inside grouped sections (Discover hub).
    case small
    /// 40pt — review-claims rows.
    case medium
    /// 44pt — Connections row.
    case large

    public var size: CGFloat {
        switch self {
        case .small: 36
        case .medium: 40
        case .large: 44
        }
    }
}

/// Background fill for `RowLeading.avatarWithBadge`. Either a solid colour
/// or a two-stop gradient — both come from category / identity tokens; no
/// hex literals at call sites.
public enum AvatarBackground: Sendable, Hashable {
    case solid(Color)
    case gradient(GradientPair)
}

/// Thumbnail payload for `RowLeading.thumbnail` — Offers (56pt) and Pets
/// (64pt) both use this shape.
public enum ThumbnailImage: Sendable, Hashable {
    /// Icon over a gradient fill — fallback when no photo is available.
    case icon(PantopusIcon, gradient: GradientPair)
    /// Remote photo; icon-over-gradient renders while it loads or on
    /// failure.
    case url(URL, fallback: PantopusIcon, gradient: GradientPair)
}

/// Size variants for `RowLeading.thumbnail`.
public enum ThumbnailSize: Sendable, Hashable {
    /// 56pt — Offers row.
    case medium
    /// 64pt — Pets row.
    case large

    public var size: CGFloat {
        switch self {
        case .medium: 56
        case .large: 64
        }
    }
}

/// Mini-avatar used inside the bidder stack on My tasks rows. Tone drives
/// background + foreground colour from the design-token palette.
public struct Bidder: Sendable, Hashable, Identifiable {
    public let id: String
    public let initials: String
    public let tone: BidderTone

    public init(id: String, initials: String, tone: BidderTone) {
        self.id = id
        self.initials = initials
        self.tone = tone
    }
}

/// Categorical tones for `Bidder` avatars. The shell maps each tone to a
/// (background, foreground) colour pair — see `BidderStack.swift`.
public enum BidderTone: Sendable, Hashable, CaseIterable {
    case sky, teal, amber, rose, violet, slate
}

/// Optional leading visual.
public enum RowLeading: Sendable {
    /// Existing — icon-only.
    case icon(PantopusIcon, tint: Color = Theme.Color.primary600)
    /// Existing — avatar with identity-pillar ring.
    case avatar(name: String, imageURL: URL?, identity: IdentityPillar, ringProgress: Double)
    /// Existing — render nothing.
    case none

    // MARK: T5 additions

    /// 40pt rounded-square tile with a tinted background + foreground.
    /// Used by Notifications (per type) and Bills (receipt icon).
    case typeIcon(PantopusIcon, background: Color, foreground: Color)

    /// 40pt rounded-square tile with a two-stop gradient background +
    /// white foreground icon. Used by My bids / My tasks category icons.
    case categoryGradientIcon(PantopusIcon, gradient: GradientPair)

    /// Plain circular avatar with optional 16pt verified-check overlay at
    /// the bottom-right. Used by Connections (large) and Review claims
    /// (medium) and Discover hub people rows (small).
    case avatarWithBadge(
        name: String,
        imageURL: URL?,
        background: AvatarBackground,
        size: AvatarBadgeSize,
        verified: Bool = false
    )

    /// Rounded thumbnail (56pt or 64pt) used by Offers and Pets.
    case thumbnail(image: ThumbnailImage, size: ThumbnailSize)

    /// Overlapping mini-avatars + `+N` overflow tile, used by My tasks
    /// bidder summary.
    case bidderStack(bidders: [Bidder], overflow: Int)
}

// MARK: - Trailing

/// Compact-button variant — used by `RowTrailing.verticalActions` and
/// `RowFooterAction`. Same palette as `CompactButton`.
public enum CompactButtonVariant: Sendable, Hashable {
    case primary
    case ghost
    case destructive
}

/// Single action description for `RowTrailing.verticalActions` (Connections
/// pending-request Accept / Ignore stack).
public struct VerticalAction: Sendable {
    public let label: String
    public let variant: CompactButtonVariant
    public let handler: @Sendable () -> Void

    public init(
        label: String,
        variant: CompactButtonVariant,
        handler: @escaping @Sendable () -> Void
    ) {
        self.label = label
        self.variant = variant
        self.handler = handler
    }
}

/// Trailing payload — rendered according to the chosen `RowTemplate`.
public enum RowTrailing: Sendable {
    /// Existing — status chip.
    case statusChip(text: String, variant: StatusChipVariant)
    /// Existing — drill-down chevron.
    case chevron
    /// Existing — more-horizontal kebab menu.
    case kebab
    /// Existing — render nothing.
    case none

    // MARK: T5 additions

    /// Amount on top + status chip stacked below — Bills.
    case amountWithChip(
        amount: String,
        chipText: String,
        chipVariant: StatusChipVariant,
        chipIcon: PantopusIcon? = nil
    )

    /// Single circular icon-button — Connections "message" CTA.
    case circularAction(
        icon: PantopusIcon,
        accessibilityLabel: String,
        background: Color = Theme.Color.primary50,
        foreground: Color = Theme.Color.primary600,
        handler: @Sendable () -> Void
    )

    /// Stacked Accept / Ignore pair (28-30pt compact buttons) —
    /// Connections pending requests.
    case verticalActions(primary: VerticalAction, secondary: VerticalAction)

    /// Price stack — amount on top, optional sublabel below. Used by My
    /// bids (bid amount + budget). The bid-card title sits to the left.
    case priceStack(amount: String, sublabel: String? = nil)
}

// MARK: - Chip

/// A chip rendered inline with the title (e.g. Pets "Dog" pill) or in the
/// chip row beneath the body (e.g. My posts intent chip, Offers
/// counter-pill).
public struct RowChip: Sendable, Hashable {
    public enum Tint: Sendable, Hashable {
        case status(StatusChipVariant)
        case custom(background: Color, foreground: Color)
    }

    public let text: String
    public let icon: PantopusIcon?
    public let tint: Tint

    public init(text: String, icon: PantopusIcon? = nil, tint: Tint) {
        self.text = text
        self.icon = icon
        self.tint = tint
    }
}

// MARK: - Footer

/// One action inside `RowFooter`. The shell renders all actions as
/// `CompactButton.footer` (34pt) in a horizontal stack, distributed by
/// `flex` weight.
public struct RowFooterAction: Sendable {
    public let title: String
    public let icon: PantopusIcon?
    public let variant: CompactButtonVariant
    /// Flex weight inside the footer row. Default 1.
    public let flex: Int
    public let handler: @Sendable () -> Void

    public init(
        title: String,
        icon: PantopusIcon? = nil,
        variant: CompactButtonVariant = .primary,
        flex: Int = 1,
        handler: @escaping @Sendable () -> Void
    ) {
        self.title = title
        self.icon = icon
        self.variant = variant
        self.flex = flex
        self.handler = handler
    }
}

/// Optional in-card footer for a row. Renders 1–3 inline compact buttons
/// separated by a hairline above. Used by My bids, My tasks, Offers,
/// Listing offers, Review claims.
public struct RowFooter: Sendable {
    public let actions: [RowFooterAction]

    public init(actions: [RowFooterAction]) {
        self.actions = actions
    }
}

// MARK: - Highlight

/// Optional visual highlight wrapping the whole card. Layered with the
/// normal row chrome — does not change geometry.
public enum RowHighlight: Sendable, Hashable {
    /// Notification unread row: `primary25` background, `personalBg` border,
    /// 8pt primary dot beside the title.
    case unread
    /// Listing-offer "LEADING" row: amber border + `LEADING` badge pinned
    /// to the top of the card.
    case leading
    /// My-posts archived row: 0.78 opacity.
    case archived
}

// MARK: - RowModel

/// A single row. Call sites construct these from DTOs in their ViewModel.
///
/// **Backwards compat:** the original 8-parameter init is the
/// `convenience` shape. All new T5 fields default to `nil` and the
/// memberwise init is replaced with a labeled one whose parameter order
/// preserves the original prefix.
public struct RowModel: Identifiable, Sendable {
    public let id: String
    public let title: String
    public let subtitle: String?
    public let template: RowTemplate
    public let leading: RowLeading
    public let trailing: RowTrailing
    /// Invoked when the row is tapped.
    public let onTap: @Sendable () -> Void
    /// Invoked when the kebab menu is tapped (if any).
    public let onSecondary: (@Sendable () -> Void)?

    // MARK: T5 additions

    /// Multiline body rendered below the subtitle. Used by Notifications
    /// (2-line clipped) and My posts (2-line clipped).
    public let body: String?

    /// Optional small icon prefix for the subtitle line. Used by
    /// Connections (map-pin in front of locality).
    public let subtitleIcon: PantopusIcon?

    /// Optional small icon prefix for the body line. Used by Connections
    /// (per-row interaction-type icon: message-circle / wrench / megaphone /
    /// user-plus / sparkles).
    public let bodyIcon: PantopusIcon?

    /// Chip rendered inline with the title (Pets "Dog" pill).
    public let inlineChip: RowChip?

    /// Chip row below the body (My posts intent chip; My bids / My tasks
    /// status chip; Offers counter pill). Renders left-to-right.
    public let chips: [RowChip]?

    /// Small dim text on the far-right of the chip row (My posts "2h",
    /// Notifications "12m").
    public let timeMeta: String?

    /// Text appended after the chip row, separated from chips with a "·"
    /// (My bids "3 others bid · 1d left").
    public let metaTail: String?

    /// Italic block quote rendered below the row body (Listing offers
    /// buyer note).
    public let note: String?

    /// Optional visual highlight wrapping the row (unread / leading /
    /// archived).
    public let highlight: RowHighlight?

    /// Optional in-card footer with 1–3 compact buttons.
    public let footer: RowFooter?

    public init(
        id: String,
        title: String,
        subtitle: String? = nil,
        template: RowTemplate,
        leading: RowLeading = .none,
        trailing: RowTrailing = .none,
        onTap: @escaping @Sendable () -> Void = {},
        onSecondary: (@Sendable () -> Void)? = nil,
        body: String? = nil,
        subtitleIcon: PantopusIcon? = nil,
        bodyIcon: PantopusIcon? = nil,
        inlineChip: RowChip? = nil,
        chips: [RowChip]? = nil,
        timeMeta: String? = nil,
        metaTail: String? = nil,
        note: String? = nil,
        highlight: RowHighlight? = nil,
        footer: RowFooter? = nil
    ) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.template = template
        self.leading = leading
        self.trailing = trailing
        self.onTap = onTap
        self.onSecondary = onSecondary
        self.body = body
        self.subtitleIcon = subtitleIcon
        self.bodyIcon = bodyIcon
        self.inlineChip = inlineChip
        self.chips = chips
        self.timeMeta = timeMeta
        self.metaTail = metaTail
        self.note = note
        self.highlight = highlight
        self.footer = footer
    }
}

// MARK: - Section

/// Style for a `RowSection`. `flat` is the default and matches T1–T4.1
/// behaviour (rows with surface backgrounds, spaced by `s2`); `card`
/// groups all rows inside a single rounded card with hairline separators
/// between them — used by Discover hub's typed sections (People /
/// Businesses / Gigs / Listings).
public enum SectionStyle: Sendable {
    case flat
    case card
}

/// Optional grouping for the list body.
public struct RowSection: Identifiable, Sendable {
    public let id: String
    public let header: String?
    public let rows: [RowModel]

    // MARK: T5 additions

    /// Counter displayed after the section header (Discover hub
    /// "People (24)").
    public let count: Int?
    /// Trailing CTA on the section header (Discover hub "See all →").
    public let onSeeAll: (@Sendable () -> Void)?
    /// Rendering style — default `.flat` preserves T1–T4.1 behaviour.
    public let style: SectionStyle

    public init(
        id: String = UUID().uuidString,
        header: String? = nil,
        rows: [RowModel],
        count: Int? = nil,
        onSeeAll: (@Sendable () -> Void)? = nil,
        style: SectionStyle = .flat
    ) {
        self.id = id
        self.header = header
        self.rows = rows
        self.count = count
        self.onSeeAll = onSeeAll
        self.style = style
    }
}
