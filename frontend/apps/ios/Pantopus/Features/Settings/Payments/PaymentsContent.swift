//
//  PaymentsContent.swift
//  Pantopus
//
//  Render models for A14.6 Payments (Settings → Payments). This is
//  the payments-OUT surface (cards on file · Stripe Connect setup ·
//  payout routing) — distinct from A10.10 Wallet which surfaces
//  earnings-IN. Mirrors the JSX archetype in
//  `docs/designs/A14/payments-frames.jsx`: balance hero + three
//  grouped cards (Payment methods · Payouts · Activity) + an
//  optional destructive Close-account card.
//

import Foundation

/// Top-level state for the Payments screen.
public enum PaymentsState: Sendable {
    case loading
    case loaded(PaymentsLoaded)
    case error(message: String)
}

/// Loaded projection of the Payments screen.
public struct PaymentsLoaded: Sendable, Hashable {
    /// Hero card — `nil` on the empty-account frame (nothing to surface).
    public let balance: PaymentsBalance?
    /// Saved payment methods (cards / wallets / bank). Empty list →
    /// the methods card renders an inline empty hero above the Add row.
    public let methods: [PaymentMethod]
    /// Payouts card — Stripe Connect row + payout method row + tax row.
    public let payouts: PaymentsPayouts
    /// Activity card — populated has 3 stat rows (lifetime · YTD ·
    /// last payout); empty collapses to one muted "No transactions" row.
    public let activity: PaymentsActivity
    /// Surfaces the "Close payment account" destructive card.
    /// `false` on the empty frame (no account to close yet).
    public let canCloseAccount: Bool
    /// Monospaced footer caption rendered below the destructive card.
    public let footerCaption: String

    public init(
        balance: PaymentsBalance?,
        methods: [PaymentMethod],
        payouts: PaymentsPayouts,
        activity: PaymentsActivity,
        canCloseAccount: Bool,
        footerCaption: String
    ) {
        self.balance = balance
        self.methods = methods
        self.payouts = payouts
        self.activity = activity
        self.canCloseAccount = canCloseAccount
        self.footerCaption = footerCaption
    }
}

/// Balance hero — A14.6's compact `BalanceHero` payout variant.
public struct PaymentsBalance: Sendable, Hashable {
    /// "Available to pay out"
    public let overline: String
    /// Pre-formatted amount without leading "$" (the hero renders the
    /// glyph at half scale).
    public let amount: String
    /// "Next payout · Mon, May 27"
    public let nextPayoutLabel: String
    /// "Weekly" / "Daily" / "Monthly" — the trailing glass pill.
    public let frequencyPill: String

    public init(
        overline: String,
        amount: String,
        nextPayoutLabel: String,
        frequencyPill: String
    ) {
        self.overline = overline
        self.amount = amount
        self.nextPayoutLabel = nextPayoutLabel
        self.frequencyPill = frequencyPill
    }
}

/// One saved payment method row.
public struct PaymentMethod: Identifiable, Sendable, Hashable {
    public let id: String
    /// Brand badge (leading) — drives `PaymentMethodRow`'s rendering.
    public let brand: PaymentMethodBrand
    /// Primary label, e.g. "Visa •• 4523" or "Apple Pay".
    public let label: String
    /// Secondary sub-label, e.g. "Expires 03/24" or "iPhone 15 Pro".
    public let subtext: String?
    /// Optional chip rendered before the trailing chevron — used for
    /// the "Default" badge on the active method.
    public let chip: PaymentMethodChip?

    public init(
        id: String,
        brand: PaymentMethodBrand,
        label: String,
        subtext: String? = nil,
        chip: PaymentMethodChip? = nil
    ) {
        self.id = id
        self.brand = brand
        self.label = label
        self.subtext = subtext
        self.chip = chip
    }
}

/// Brand badge variants for `PaymentMethodRow`. Each case names the
/// 38×26 rounded leading mark to render (color, wordmark, optional
/// inline icon).
public enum PaymentMethodBrand: String, Sendable, Hashable {
    case visa
    case mastercard
    case amex
    case applePay
    case bank
    case stripe
    /// Generic card mark for brands without a bespoke badge (Discover,
    /// JCB, Diners, UnionPay, …) so real saved cards always render.
    case card
}

/// A small status chip rendered before the trailing chevron in a
/// payment-method row. Mirrors the Lucide-frames vocabulary
/// (`Default`, `Connected`, `On file`).
public struct PaymentMethodChip: Sendable, Hashable {
    public let label: String
    public let tone: PaymentsChipTone

    public init(label: String, tone: PaymentsChipTone) {
        self.label = label
        self.tone = tone
    }
}

/// Chip color tones — matches the `RowControl.ChipTone` palette in
/// `GroupedListContent.swift` so the visual reads identically.
public enum PaymentsChipTone: String, Sendable, Hashable {
    case primary
    case success
    case neutral
}

/// Payouts card content (Stripe row + 2–3 sibling rows).
public struct PaymentsPayouts: Sendable, Hashable {
    /// Stripe Connect row — populated frame shows the green
    /// "Connected" chip; empty frame shows a primary "Connect" CTA chip.
    public let stripe: PaymentsPayoutRow
    /// Payout method row (e.g. "Payout to Chase •• 1023"). On the
    /// empty frame this renders gated with a lock glyph + "Available
    /// after Stripe connect" sub.
    public let payoutMethod: PaymentsPayoutRow
    /// Payout schedule row (only rendered on the populated frame —
    /// nil hides the row).
    public let payoutSchedule: PaymentsPayoutRow?
    /// Tax info row (W-9 on file / em-dash gated).
    public let taxInfo: PaymentsPayoutRow
    /// 11.5pt caption rendered under the card.
    public let helper: String?

    public init(
        stripe: PaymentsPayoutRow,
        payoutMethod: PaymentsPayoutRow,
        payoutSchedule: PaymentsPayoutRow? = nil,
        taxInfo: PaymentsPayoutRow,
        helper: String? = nil
    ) {
        self.stripe = stripe
        self.payoutMethod = payoutMethod
        self.payoutSchedule = payoutSchedule
        self.taxInfo = taxInfo
        self.helper = helper
    }
}

/// One row inside the Payouts card. Trailing affordance is one of
/// chevron / chip+chevron / lock-gated em-dash.
public struct PaymentsPayoutRow: Identifiable, Sendable, Hashable {
    public let id: String
    /// Optional leading brand badge (Stripe purple / bank sky / nil).
    public let leadingBrand: PaymentMethodBrand?
    public let label: String
    public let subtext: String?
    public let trailing: PaymentsRowTrailing

    public init(
        id: String,
        leadingBrand: PaymentMethodBrand? = nil,
        label: String,
        subtext: String? = nil,
        trailing: PaymentsRowTrailing
    ) {
        self.id = id
        self.leadingBrand = leadingBrand
        self.label = label
        self.subtext = subtext
        self.trailing = trailing
    }
}

/// Trailing affordance vocabulary for `PaymentsPayoutRow`.
public enum PaymentsRowTrailing: Sendable, Hashable {
    /// Plain navigation chevron.
    case chevron
    /// Chip + chevron (e.g. green "Connected" chip + chevron).
    case chipChevron(label: String, tone: PaymentsChipTone)
    /// Primary CTA chip without a chevron (e.g. blue "Connect" chip
    /// on Stripe Connect in the empty frame).
    case ctaChip(label: String, tone: PaymentsChipTone)
    /// Em-dash glyph used to mark a gated row (empty frame's payout
    /// method / tax info — locked behind Stripe Connect).
    case gatedDash
}

/// Activity card content.
public enum PaymentsActivity: Sendable, Hashable {
    /// Three stat rows: lifetime · YTD · last payout.
    case stats([PaymentsActivityStat])
    /// Single muted "No transactions yet" row. Used on the empty
    /// frame when there's no Stripe history.
    case empty(title: String, body: String)
}

/// One row inside the activity card.
public struct PaymentsActivityStat: Identifiable, Sendable, Hashable {
    public let id: String
    public let label: String
    public let subtext: String?

    public init(id: String, label: String, subtext: String? = nil) {
        self.id = id
        self.label = label
        self.subtext = subtext
    }
}
