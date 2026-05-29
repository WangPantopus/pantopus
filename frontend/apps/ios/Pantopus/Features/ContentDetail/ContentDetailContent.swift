//
//  ContentDetailContent.swift
//  Pantopus
//
//  Render-only models for the T2.6 Transactional Detail shell.
//  `ContentDetailContent` is a flat description the shell consumes —
//  the per-entity view-models (gig / listing / invoice) project their
//  backend payload into one of these. Modules are an open enum so
//  the backend's jsonb_modules[] can extend the middle section
//  without touching the shell.
//

import Foundation

/// Which entity is being shown. Drives a few subtle treatments — the
/// listing variant gets a full-bleed cover image, the invoice variant
/// gets a full-width pay dock without a secondary button.
public enum ContentDetailKind: String, Sendable, Hashable {
    case gig
    case listing
    case invoice
}

/// Cover image for the listing variant (gradient placeholder + glyph
/// until the first photo loads). Optional — gigs and invoices skip
/// the cover slot.
public struct ContentDetailCover: Sendable, Hashable {
    public let imageUrl: URL?
    public let gradient: ListingGradient
    public let placeholderIcon: PantopusIcon
    public let pageCount: Int
    public let activePage: Int
    /// Sold treatment: desaturates the hero + stamps a tilted SOLD badge.
    public let sold: Bool
    /// Decorative glass action chips overlaid top-right (share / bookmark).
    /// Render-only for now — live wiring lands with the share + save flows.
    public let glassActions: [PantopusIcon]

    public init(
        imageUrl: URL?,
        gradient: ListingGradient,
        placeholderIcon: PantopusIcon,
        pageCount: Int = 1,
        activePage: Int = 0,
        sold: Bool = false,
        glassActions: [PantopusIcon] = []
    ) {
        self.imageUrl = imageUrl
        self.gradient = gradient
        self.placeholderIcon = placeholderIcon
        self.pageCount = pageCount
        self.activePage = activePage
        self.sold = sold
        self.glassActions = glassActions
    }
}

/// Top-of-content status pill — "Open · 4 bids", "Due in 3 days", etc.
public struct ContentDetailPill: Sendable, Hashable, Identifiable {
    public enum Tone: Sendable, Hashable { case info, success, warning, business, neutral, error }

    public let id: String
    public let label: String
    public let icon: PantopusIcon?
    public let tone: Tone

    public init(id: String = UUID().uuidString, label: String, icon: PantopusIcon? = nil, tone: Tone = .info) {
        self.id = id
        self.label = label
        self.icon = icon
        self.tone = tone
    }
}

/// Hero block under the status pill. `subtitle` is the category chip +
/// meta line; `priceLine` is the big number; `priceCaption` is the
/// faded suffix ("budget", "due", etc).
public struct ContentDetailHero: Sendable, Hashable {
    /// Drives the colour of the big price number. `auto` resolves to
    /// `primary600` for listings and `appText` elsewhere; `success`
    /// recolours it green for the invoice paid state.
    public enum PriceTone: Sendable, Hashable { case auto, success }

    public let title: String
    public let categoryChip: ContentDetailCategoryChip?
    public let meta: String?
    public let monoId: String?
    public let priceLine: String?
    public let priceCaption: String?
    public let priceTone: PriceTone
    /// Strikes through the price (listing sold — original asking price).
    public let priceStrikethrough: Bool
    /// Green sale tag rendered next to a struck-through price ("Sold for $385").
    public let saleTag: String?
    /// Right-aligned faded label trailing the price ("paid in full").
    public let priceTrailingLabel: String?
    /// Green check disc rendered after the price (invoice paid).
    public let priceCheckDisc: Bool
    /// Pill row rendered directly under the price (listing condition / pickup / distance).
    public let inlinePills: [ContentDetailPill]

    public init(
        title: String,
        categoryChip: ContentDetailCategoryChip? = nil,
        meta: String? = nil,
        monoId: String? = nil,
        priceLine: String? = nil,
        priceCaption: String? = nil,
        priceTone: PriceTone = .auto,
        priceStrikethrough: Bool = false,
        saleTag: String? = nil,
        priceTrailingLabel: String? = nil,
        priceCheckDisc: Bool = false,
        inlinePills: [ContentDetailPill] = []
    ) {
        self.title = title
        self.categoryChip = categoryChip
        self.meta = meta
        self.monoId = monoId
        self.priceLine = priceLine
        self.priceCaption = priceCaption
        self.priceTone = priceTone
        self.priceStrikethrough = priceStrikethrough
        self.saleTag = saleTag
        self.priceTrailingLabel = priceTrailingLabel
        self.priceCheckDisc = priceCheckDisc
        self.inlinePills = inlinePills
    }
}

/// Small category chip rendered next to the hero subtitle. Re-uses the
/// Gigs category palette (T2.3) so the vocabulary stays consistent.
public struct ContentDetailCategoryChip: Sendable, Hashable {
    public let label: String
    public let category: GigsCategory

    public init(label: String, category: GigsCategory) {
        self.label = label
        self.category = category
    }
}

/// One cell in the 3-up stat strip (gigs). Skipping the strip is fine
/// — empty array hides it.
public struct ContentDetailStat: Sendable, Hashable, Identifiable {
    public let id: String
    public let top: String
    public let bottom: String

    public init(id: String = UUID().uuidString, top: String, bottom: String) {
        self.id = id
        self.top = top
        self.bottom = bottom
    }
}

/// Counterparty card (avatar + name + identity + rating + message
/// button). Used by the listing variant for the seller; gigs surface
/// the bidder list as a module rather than a single counterparty.
public struct ContentDetailCounterparty: Sendable, Hashable {
    public let displayName: String
    public let initials: String
    public let identityKind: String? // "personal" | "business" | nil
    public let verified: Bool
    public let rating: Double?
    public let trailing: String?
    public let showsMessageButton: Bool

    public init(
        displayName: String,
        initials: String,
        identityKind: String?,
        verified: Bool,
        rating: Double?,
        trailing: String?,
        showsMessageButton: Bool = true
    ) {
        self.displayName = displayName
        self.initials = initials
        self.identityKind = identityKind
        self.verified = verified
        self.rating = rating
        self.trailing = trailing
        self.showsMessageButton = showsMessageButton
    }
}

/// Trust capsule rendered in a wrap below the hero. Tone matches the
/// status-pill enum.
public typealias ContentDetailTrustCapsule = ContentDetailPill

/// Sticky dock button. `enabled == false` renders the disabled treatment
/// (sunken fill, muted text, no shadow) — used for the awarded gig's
/// "Bidding closed" lock CTA.
public struct ContentDetailDockButton: Sendable, Hashable {
    public let label: String
    public let icon: PantopusIcon?
    public let enabled: Bool

    public init(label: String, icon: PantopusIcon? = nil, enabled: Bool = true) {
        self.label = label
        self.icon = icon
        self.enabled = enabled
    }
}

/// Sticky dock CTA. `secondary == nil` → primary spans full width.
public struct ContentDetailDock: Sendable, Hashable {
    public let secondary: ContentDetailDockButton?
    public let primary: ContentDetailDockButton

    public init(secondary: ContentDetailDockButton? = nil, primary: ContentDetailDockButton) {
        self.secondary = secondary
        self.primary = primary
    }
}

// MARK: - Module variants

/// One row in the variable middle section. Open enum so the
/// backend's `jsonb_modules[]` can extend without touching the shell.
public enum ContentDetailModule: Sendable, Hashable, Identifiable {
    case description(ContentDetailDescription)
    case detailRow(ContentDetailDetailRow)
    case captionedText(ContentDetailCaptionedText)
    case twoStop(ContentDetailTwoStop)
    case photoStrip(ContentDetailPhotoStrip)
    case capsuleRow(ContentDetailCapsuleRow)
    case detailsGrid(ContentDetailDetailsGrid)
    case similarItems(ContentDetailSimilarStrip)
    case bids(ContentDetailBidsModule)
    case callout(ContentDetailCallout)
    case fromTo(ContentDetailFromTo)
    case lineItems(ContentDetailLineItems)
    case summary(ContentDetailSummary)

    public var id: String {
        switch self {
        case let .description(m): "description_\(m.title)"
        case let .detailRow(m): "detail_\(m.title)"
        case let .captionedText(m): "caption_\(m.title)"
        case let .twoStop(m): "twostop_\(m.title)"
        case let .photoStrip(m): "photos_\(m.title)"
        case let .capsuleRow(m): "capsules_\(m.id)"
        case let .detailsGrid(m): "details_\(m.title)"
        case let .similarItems(m): "similar_\(m.title)"
        case let .bids(m): "bids_\(m.title)"
        case let .callout(m): "callout_\(m.identifier)"
        case let .fromTo(m): "fromto_\(m.from.name)"
        case let .lineItems(m): "lineitems_\(m.title)"
        case .summary: "summary"
        }
    }
}

/// Long paragraph with section title. Used for gigs "What needs doing"
/// and listings "Description".
public struct ContentDetailDescription: Sendable, Hashable {
    public let title: String
    public let icon: PantopusIcon?
    public let body: String

    public init(title: String, icon: PantopusIcon?, body: String) {
        self.title = title
        self.icon = icon
        self.body = body
    }
}

/// One-line row with map-pin / icon and trailing label (e.g. "Where").
public struct ContentDetailDetailRow: Sendable, Hashable {
    public let title: String
    public let sectionIcon: PantopusIcon?
    public let rowIcon: PantopusIcon
    public let label: String
    public let trailing: String?

    public init(title: String, sectionIcon: PantopusIcon?, rowIcon: PantopusIcon, label: String, trailing: String?) {
        self.title = title
        self.sectionIcon = sectionIcon
        self.rowIcon = rowIcon
        self.label = label
        self.trailing = trailing
    }
}

/// Free-text caption ("Sat Nov 9 — Sun Nov 10 · flexible morning").
public struct ContentDetailCaptionedText: Sendable, Hashable {
    public let title: String
    public let icon: PantopusIcon?
    public let label: String

    public init(title: String, icon: PantopusIcon?, label: String) {
        self.title = title
        self.icon = icon
        self.label = label
    }
}

/// Two-stop pickup → drop-off card (Magic Task V2). A/B discs over a
/// muted card with a connector line. Used by the gig V2 "Pickup → drop-off"
/// module.
public struct ContentDetailTwoStop: Sendable, Hashable {
    public enum StopTone: Sendable, Hashable { case primary, success }

    public struct Stop: Sendable, Hashable, Identifiable {
        public let id: String
        public let letter: String
        public let tone: StopTone
        public let address: String
        public let distance: String?

        public init(id: String = UUID().uuidString, letter: String, tone: StopTone, address: String, distance: String?) {
            self.id = id
            self.letter = letter
            self.tone = tone
            self.address = address
            self.distance = distance
        }
    }

    public let title: String
    public let icon: PantopusIcon?
    public let stops: [Stop]

    public init(title: String, icon: PantopusIcon? = .mapPin, stops: [Stop]) {
        self.title = title
        self.icon = icon
        self.stops = stops
    }
}

/// Inline wrap of trust/status capsules placed mid-flow (gig V2 trust
/// row sits between the photo strip and the bid list).
public struct ContentDetailCapsuleRow: Sendable, Hashable {
    public let id: String
    public let capsules: [ContentDetailPill]

    public init(id: String = "trust", capsules: [ContentDetailPill]) {
        self.id = id
        self.capsules = capsules
    }
}

/// Key/value detail grid (listing "Details" — Brand / Frame size / …).
public struct ContentDetailDetailsGrid: Sendable, Hashable {
    public struct Row: Sendable, Hashable, Identifiable {
        public let id: String
        public let key: String
        public let value: String

        public init(id: String = UUID().uuidString, key: String, value: String) {
            self.id = id
            self.key = key
            self.value = value
        }
    }

    public let title: String
    public let icon: PantopusIcon?
    public let rows: [Row]

    public init(title: String, icon: PantopusIcon? = .info, rows: [Row]) {
        self.title = title
        self.icon = icon
        self.rows = rows
    }
}

/// Flexible callout card. Covers the awarded banner, the Pantopus Pay
/// receipt capsule, the "Alert me when similar appears" row, and the
/// no-bids "Be the first to bid" empty capsule.
public struct ContentDetailCallout: Sendable, Hashable {
    /// `banner` is a leading-icon row; `empty` is a centred dashed capsule.
    public enum Style: Sendable, Hashable { case banner, empty }
    /// Card background / border family.
    public enum Tone: Sendable, Hashable { case success, neutral, dashed }
    /// Leading icon disc treatment.
    public enum IconTone: Sendable, Hashable { case success, successOutline, primary }

    public let identifier: String
    public let style: Style
    public let tone: Tone
    public let icon: PantopusIcon
    public let iconTone: IconTone
    public let title: String
    public let subtitle: String?
    public let subtitleMono: Bool
    public let trailingActionLabel: String?
    public let footerPill: String?

    public init(
        identifier: String,
        style: Style = .banner,
        tone: Tone = .success,
        icon: PantopusIcon,
        iconTone: IconTone = .success,
        title: String,
        subtitle: String? = nil,
        subtitleMono: Bool = false,
        trailingActionLabel: String? = nil,
        footerPill: String? = nil
    ) {
        self.identifier = identifier
        self.style = style
        self.tone = tone
        self.icon = icon
        self.iconTone = iconTone
        self.title = title
        self.subtitle = subtitle
        self.subtitleMono = subtitleMono
        self.trailingActionLabel = trailingActionLabel
        self.footerPill = footerPill
    }
}

/// Horizontal strip of square gradient tiles (no real images yet).
public struct ContentDetailPhotoStrip: Sendable, Hashable {
    public let title: String
    public let icon: PantopusIcon?
    public let countLabel: String?
    public let tiles: [ContentDetailPhotoTile]

    public init(title: String, icon: PantopusIcon? = .camera, countLabel: String? = nil, tiles: [ContentDetailPhotoTile]) {
        self.title = title
        self.icon = icon
        self.countLabel = countLabel
        self.tiles = tiles
    }
}

public struct ContentDetailPhotoTile: Sendable, Hashable, Identifiable {
    public let id: String
    public let gradient: ListingGradient
    public let icon: PantopusIcon

    public init(id: String = UUID().uuidString, gradient: ListingGradient, icon: PantopusIcon) {
        self.id = id
        self.gradient = gradient
        self.icon = icon
    }
}

/// Horizontal carousel of similar items (listing detail).
public struct ContentDetailSimilarStrip: Sendable, Hashable {
    public let title: String
    public let sub: String?
    public let items: [ContentDetailSimilarItem]

    public init(title: String, sub: String? = nil, items: [ContentDetailSimilarItem]) {
        self.title = title
        self.sub = sub
        self.items = items
    }
}

public struct ContentDetailSimilarItem: Sendable, Hashable, Identifiable {
    public let id: String
    public let title: String
    public let price: String
    public let gradient: ListingGradient

    public init(id: String, title: String, price: String, gradient: ListingGradient) {
        self.id = id
        self.title = title
        self.price = price
        self.gradient = gradient
    }
}

/// Bid list (gig detail, owner-only — empty array hides the section).
/// `sub` carries the low/high range (open) or "closed" (awarded).
public struct ContentDetailBidsModule: Sendable, Hashable {
    public let title: String
    public let sub: String?
    public let bids: [ContentDetailBidRow]

    public init(title: String, sub: String? = nil, bids: [ContentDetailBidRow]) {
        self.title = title
        self.sub = sub
        self.bids = bids
    }
}

public struct ContentDetailBidRow: Sendable, Hashable, Identifiable {
    public let id: String
    public let initials: String
    public let displayName: String
    public let avatarColor: String
    public let ratingLine: String
    public let amount: String
    public let verified: Bool
    /// Optional tag pill ("fastest reply" / "has van" — V2; "Winner" is
    /// derived from `won`).
    public let tag: String?
    /// Winning bid in the awarded state — green row tint + Winner pill +
    /// green amount.
    public let won: Bool
    /// Losing bid in the awarded state — 55% opacity + struck-through amount.
    public let dimmed: Bool

    public init(
        id: String,
        initials: String,
        displayName: String,
        avatarColor: String,
        ratingLine: String,
        amount: String,
        verified: Bool,
        tag: String? = nil,
        won: Bool = false,
        dimmed: Bool = false
    ) {
        self.id = id
        self.initials = initials
        self.displayName = displayName
        self.avatarColor = avatarColor
        self.ratingLine = ratingLine
        self.amount = amount
        self.verified = verified
        self.tag = tag
        self.won = won
        self.dimmed = dimmed
    }
}

/// Invoice-only — two side-by-side party cards (From / To).
public struct ContentDetailFromTo: Sendable, Hashable {
    public let from: ContentDetailParty
    public let to: ContentDetailParty

    public init(from: ContentDetailParty, to: ContentDetailParty) {
        self.from = from
        self.to = to
    }
}

public struct ContentDetailParty: Sendable, Hashable {
    public enum Accent: Sendable, Hashable { case business, personal, neutral }

    public let label: String
    public let name: String
    public let sub: String
    public let accent: Accent

    public init(label: String, name: String, sub: String, accent: Accent) {
        self.label = label
        self.name = name
        self.sub = sub
        self.accent = accent
    }
}

/// Invoice-only — line items table. Per the A09.4 design the fees/tax
/// block and the grand-total row live in a muted footer *inside* this
/// same card, so they're modelled here rather than as a separate summary.
public struct ContentDetailLineItems: Sendable, Hashable {
    public enum TotalTone: Sendable, Hashable { case primary, success }

    public let title: String
    public let icon: PantopusIcon?
    public let rows: [ContentDetailLineItem]
    public let fees: [ContentDetailSummaryRow]
    public let totalLabel: String?
    public let totalValue: String?
    public let totalTone: TotalTone

    public init(
        title: String,
        icon: PantopusIcon? = .file,
        rows: [ContentDetailLineItem],
        fees: [ContentDetailSummaryRow] = [],
        totalLabel: String? = nil,
        totalValue: String? = nil,
        totalTone: TotalTone = .primary
    ) {
        self.title = title
        self.icon = icon
        self.rows = rows
        self.fees = fees
        self.totalLabel = totalLabel
        self.totalValue = totalValue
        self.totalTone = totalTone
    }
}

public struct ContentDetailLineItem: Sendable, Hashable, Identifiable {
    public let id: String
    public let item: String
    public let qty: String
    public let unit: String
    public let total: String

    public init(id: String = UUID().uuidString, item: String, qty: String, unit: String, total: String) {
        self.id = id
        self.item = item
        self.qty = qty
        self.unit = unit
        self.total = total
    }
}

/// Invoice-only — subtotal / tax / total summary card. `totalTone`
/// recolours the total (success green when paid).
public struct ContentDetailSummary: Sendable, Hashable {
    public enum TotalTone: Sendable, Hashable { case primary, success }

    public let rows: [ContentDetailSummaryRow]
    public let totalLabel: String
    public let totalValue: String
    public let totalTone: TotalTone

    public init(rows: [ContentDetailSummaryRow], totalLabel: String, totalValue: String, totalTone: TotalTone = .primary) {
        self.rows = rows
        self.totalLabel = totalLabel
        self.totalValue = totalValue
        self.totalTone = totalTone
    }
}

public struct ContentDetailSummaryRow: Sendable, Hashable, Identifiable {
    public let id: String
    public let label: String
    public let value: String

    public init(id: String = UUID().uuidString, label: String, value: String) {
        self.id = id
        self.label = label
        self.value = value
    }
}

// MARK: - Top-level content

/// Render-only content the shell consumes. The per-entity view-models
/// produce one of these from their backend payload.
public struct ContentDetailContent: Sendable, Hashable {
    public let kind: ContentDetailKind
    public let cover: ContentDetailCover?
    public let statusPill: ContentDetailPill?
    public let hero: ContentDetailHero
    public let statStrip: [ContentDetailStat]
    public let counterparty: ContentDetailCounterparty?
    public let modules: [ContentDetailModule]
    public let trustCapsules: [ContentDetailTrustCapsule]
    public let dock: ContentDetailDock

    public init(
        kind: ContentDetailKind,
        cover: ContentDetailCover? = nil,
        statusPill: ContentDetailPill? = nil,
        hero: ContentDetailHero,
        statStrip: [ContentDetailStat] = [],
        counterparty: ContentDetailCounterparty? = nil,
        modules: [ContentDetailModule] = [],
        trustCapsules: [ContentDetailTrustCapsule] = [],
        dock: ContentDetailDock
    ) {
        self.kind = kind
        self.cover = cover
        self.statusPill = statusPill
        self.hero = hero
        self.statStrip = statStrip
        self.counterparty = counterparty
        self.modules = modules
        self.trustCapsules = trustCapsules
        self.dock = dock
    }
}

/// Render state for the shell. View-models populate one of these.
public enum ContentDetailState: Sendable {
    case loading
    case loaded(ContentDetailContent)
    case error(message: String)
}
