//
//  WalletContent.swift
//  Pantopus
//
//  A10.10 — pure value types backing the Wallet screen. Mirrors the
//  shape of `wallet-frames.jsx` so the populated / hold variants
//  snapshot reproducibly. UI types stay out of the model; the view
//  maps `WalletActivityCategory` → `Theme.Color` and `PantopusIcon`.
//

import Foundation

/// Activity-row category — drives the per-row icon tile colour + glyph.
public enum WalletActivityCategory: String, Equatable, Sendable, CaseIterable {
    case cleaning
    case childCare = "child-care"
    case handyman
    case petCare = "pet-care"
    case bank
    case fee
}

/// Clearing status for a single activity row. The view renders a
/// trailing label ("Cleared" / "On hold" / "Payout" / "Fee") and
/// optionally an amber "Pending" chip beside the description.
public enum ActivityStatus: Equatable, Sendable {
    /// Earned and cleared — counts toward the available balance.
    case available
    /// Earned but still in escrow. `clearsLabel` is the user-facing
    /// "clears Dec 4" copy rendered after the counterparty line.
    case pending(clearsLabel: String)
    /// Already-settled outbound payout or fee.
    case complete
}

/// Direction of money flow for a row.
public enum ActivityDirection: Equatable, Sendable {
    case `in`
    case out
}

/// A single transaction row inside the Recent activity card.
public struct WalletActivityItem: Identifiable, Equatable, Sendable {
    public let id: String
    /// Day group label — "Today" / "Yesterday" / "Nov 28" / …
    public let day: String
    /// Time-of-day or sub-day timestamp ("2:14 pm").
    public let dateLabel: String
    /// Headline description ("Patio cleanup · 3 hr").
    public let description: String
    /// Counterparty ("Marcus P." / "Chase ••••7421" / "Pantopus").
    public let counterparty: String
    public let category: WalletActivityCategory
    public let direction: ActivityDirection
    public let status: ActivityStatus
    /// Pre-formatted amount string without the leading sign or "$".
    /// Example: `"140.00"`. The row renders "+$140.00" / "−$2.40"
    /// based on `direction`.
    public let amount: String
    /// `true` for the service-fee row — switches the trailing label to
    /// "Fee" and uses the neutral fee category tint.
    public let isFee: Bool

    public init(
        id: String,
        day: String,
        dateLabel: String,
        description: String,
        counterparty: String,
        category: WalletActivityCategory,
        direction: ActivityDirection,
        status: ActivityStatus,
        amount: String,
        isFee: Bool = false
    ) {
        self.id = id
        self.day = day
        self.dateLabel = dateLabel
        self.description = description
        self.counterparty = counterparty
        self.category = category
        self.direction = direction
        self.status = status
        self.amount = amount
        self.isFee = isFee
    }
}

/// Payout-method card payload. The view renders a debit-card-shaped
/// `CHASE` tile plus the meta line; `warn == true` flips the card to
/// the amber re-verify state.
public struct WalletPayoutMethod: Equatable, Sendable {
    public let bankLabel: String
    public let last4: String
    /// Body line rendered under the bank label. In the default state
    /// the view prepends the green `zap` flash icon; in the warn state
    /// it prepends the amber `alert-circle` icon.
    public let bodyText: String
    /// `true` swaps the card to amber gradient + `Re-verify` button.
    public let warn: Bool

    public init(bankLabel: String, last4: String, bodyText: String, warn: Bool) {
        self.bankLabel = bankLabel
        self.last4 = last4
        self.bodyText = bodyText
        self.warn = warn
    }
}

/// Tax-docs row payload. `ready` lights up the home-green icon tile +
/// `New` chip + "1099-NEC ready" body. Otherwise the row renders the
/// neutral grey YTD line.
public struct WalletTaxDocs: Equatable, Sendable {
    public let ready: Bool
    public let bodyText: String

    public init(ready: Bool, bodyText: String) {
        self.ready = ready
        self.bodyText = bodyText
    }
}

/// Hold-state payload — populated only in the `.hold` variant. Drives
/// the amber top banner above the BalanceHero and the locked Withdraw
/// CTA footnote at the bottom.
public struct WalletHoldState: Equatable, Sendable {
    public let bannerHeadline: String
    public let bannerBody: String
    /// Compact one-line summary surfaced inside the BalanceHero's
    /// inset amber strip ("Re-verify your bank to release funds.").
    public let heroBannerHeadline: String
    public let heroBannerBody: String
    /// Centred footnote under the locked Withdraw CTA.
    public let withdrawFootnote: String

    public init(
        bannerHeadline: String,
        bannerBody: String,
        heroBannerHeadline: String,
        heroBannerBody: String,
        withdrawFootnote: String
    ) {
        self.bannerHeadline = bannerHeadline
        self.bannerBody = bannerBody
        self.heroBannerHeadline = heroBannerHeadline
        self.heroBannerBody = heroBannerBody
        self.withdrawFootnote = withdrawFootnote
    }
}

/// Top-level Wallet render payload.
public struct WalletContent: Equatable, Sendable {
    /// Pre-formatted available balance — e.g. `"847.50"`.
    public let available: String
    public let pending: String
    public let pendingMeta: String
    public let monthValue: String
    public let monthMeta: String
    public let activity: [WalletActivityItem]
    public let payoutMethod: WalletPayoutMethod
    public let taxDocs: WalletTaxDocs
    /// Populated only in the `.hold` variant.
    public let holdState: WalletHoldState?

    public var isOnHold: Bool { holdState != nil }

    public init(
        available: String,
        pending: String,
        pendingMeta: String,
        monthValue: String,
        monthMeta: String,
        activity: [WalletActivityItem],
        payoutMethod: WalletPayoutMethod,
        taxDocs: WalletTaxDocs,
        holdState: WalletHoldState? = nil
    ) {
        self.available = available
        self.pending = pending
        self.pendingMeta = pendingMeta
        self.monthValue = monthValue
        self.monthMeta = monthMeta
        self.activity = activity
        self.payoutMethod = payoutMethod
        self.taxDocs = taxDocs
        self.holdState = holdState
    }
}
