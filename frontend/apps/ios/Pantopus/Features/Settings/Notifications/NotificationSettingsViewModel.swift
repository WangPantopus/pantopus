//
//  NotificationSettingsViewModel.swift
//  Pantopus
//
//  P7.5 / A14.5 — Notification preferences. Reshaped from the old
//  channel-keyed toggle list into the design's three-channel matrix:
//  five category cards (Tasks · Pulse · Marketplace · Home & Mailbox ·
//  Account & security), each row carrying a `ChannelTriad` (Push /
//  Email / SMS). A Master card on top hosts the Pause-all toggle + a
//  Quiet-hours row; flipping Pause swaps the Master card for the amber
//  `PauseBanner` and dims the category cards to 0.5.
//
//  Backend persistence is out of scope for P7.5 (mirrors A14.2 Home
//  security) — every chip / toggle flips local state only. The helper
//  lines and channel patterns are the parity contract, mirrored
//  word-for-word in the Android `NotificationSettingsViewModel`.
//
//  Two variant frames cover the design parity audit:
//    `.populated` — the real mix (push for replies, email for receipts,
//                   Pulse quiet, Home gets everything, security locked).
//    `.paused`    — Pause-all on: amber banner + dimmed category cards.
//

import Foundation
import Observation

@Observable
@MainActor
public final class NotificationSettingsViewModel: GroupedListDataSource {
    public var title: String {
        "Notifications"
    }

    /// Mono legend pinned at the bottom of the scroll, so the P/E/S
    /// abbreviations never have to be guessed.
    public var footerCaption: String? {
        "P · Push   E · Email   S · SMS"
    }

    public private(set) var state: GroupedListState = .loading

    /// Master "Pause all" state. When on, the Master card is replaced by
    /// the amber banner and the category cards dim.
    public private(set) var isPaused: Bool
    /// Per-row Push/Email/SMS pattern, keyed by row id. Locked channels
    /// (Emergency push) live in `Self.locked(for:)`, not here.
    private var patterns: [String: ChannelPattern]

    public enum Variant: Sendable, Hashable { case populated, paused }

    public init(variant: Variant = .populated) {
        isPaused = (variant == .paused)
        patterns = Self.seedPatterns()
    }

    // MARK: - GroupedListDataSource

    public var banner: GroupedListBanner? {
        guard isPaused else { return nil }
        return GroupedListBanner(
            icon: .bellOff,
            title: "Paused for 2 hours",
            subtitle: "Resumes 11:42 AM · Emergency alerts still come through",
            actionLabel: "Resume"
        )
    }

    public var contentDimmed: Bool {
        isPaused
    }

    public func load() async {
        state = .loaded(groups())
    }

    public func tapRow(_: String) async {
        // Quiet-hours opens a duration sheet in a later prompt; no-op
        // while sheet wiring is out of scope.
    }

    public func selectRadio(_: String) async {}
    public func setSlider(_: String, index _: Int) async {}

    public func toggleRow(_ rowId: String, isOn: Bool) async {
        guard rowId == RowID.pauseAll else { return }
        isPaused = isOn
        state = .loaded(groups())
    }

    public func toggleChannel(_ rowId: String, channel: ChannelGlyph, isOn: Bool) async {
        guard var pattern = patterns[rowId] else { return }
        // Locked channels can't be toggled — the view guards this too.
        guard !Self.locked(for: rowId).contains(channel) else { return }
        switch channel {
        case .p: pattern.p = isOn
        case .e: pattern.e = isOn
        case .s: pattern.s = isOn
        }
        patterns[rowId] = pattern
        state = .loaded(groups())
    }

    public func tapBanner() async {
        // Resume — clears the pause and brings the configured pattern
        // back on.
        isPaused = false
        state = .loaded(groups())
    }

    // MARK: - Group projection

    private func groups() -> [GroupedListGroup] {
        var result: [GroupedListGroup] = []
        if !isPaused { result.append(masterGroup()) }
        result.append(contentsOf: Self.categories.map(categoryGroup))
        return result
    }

    private func masterGroup() -> GroupedListGroup {
        GroupedListGroup(
            id: "master",
            overline: "Master",
            helper: "Pause all silences every channel except emergency alerts. Quiet hours just delays them.",
            rows: [
                GroupedListRow(
                    id: RowID.pauseAll,
                    label: "Pause all notifications",
                    subtext: "Snooze everything but emergencies",
                    control: .toggle(isOn: isPaused)
                ),
                GroupedListRow(
                    id: RowID.quietHours,
                    label: "Quiet hours",
                    subtext: "10:00 PM – 7:00 AM · Weekdays",
                    control: .chipStatus(label: "On", tone: .neutral, includesChevron: true)
                )
            ]
        )
    }

    private func categoryGroup(_ category: Category) -> GroupedListGroup {
        GroupedListGroup(
            id: category.id,
            overline: category.title,
            helper: category.helper,
            showsChannelHeader: true,
            rows: category.rows.map { row in
                let pattern = patterns[row.id] ?? row.seed
                return GroupedListRow(
                    id: row.id,
                    label: row.label,
                    subtext: row.sub,
                    control: .channelTriad(
                        p: pattern.p,
                        e: pattern.e,
                        s: pattern.s,
                        locked: Self.locked(for: row.id)
                    )
                )
            }
        )
    }

    // MARK: - Locked channels

    /// Channels that can't be muted. Emergency alerts keep push locked
    /// on — surfaced as a sky chip with a lock badge.
    static func locked(for rowId: String) -> Set<ChannelGlyph> {
        rowId == RowID.emergency ? [.p] : []
    }

    // MARK: - Stable identifiers

    public enum RowID {
        public static let pauseAll = "master.pauseAll"
        public static let quietHours = "master.quietHours"
        public static let emergency = "home.emergency"
    }

    // MARK: - Seed data (parity contract — mirrored in Android)

    struct ChannelPattern: Sendable, Hashable {
        var p: Bool
        var e: Bool
        var s: Bool
    }

    struct CategoryRowSpec: Sendable {
        let id: String
        let label: String
        let sub: String?
        let seed: ChannelPattern
    }

    struct Category: Sendable {
        let id: String
        let title: String
        let helper: String?
        let rows: [CategoryRowSpec]
    }

    static let categories: [Category] = [
        Category(
            id: "tasks",
            title: "Tasks",
            helper: "Push only for things that need a fast reply. Receipts go to email so they're searchable.",
            rows: [
                CategoryRowSpec(id: "tasks.bids", label: "Bids on my tasks", sub: "Within 5 minutes of posting", seed: ChannelPattern(p: true, e: false, s: false)),
                CategoryRowSpec(id: "tasks.messages", label: "New messages", sub: "From clients & taskers", seed: ChannelPattern(p: true, e: true, s: false)),
                CategoryRowSpec(id: "tasks.status", label: "Status updates", sub: "Accepted, on the way, done", seed: ChannelPattern(p: true, e: false, s: false)),
                CategoryRowSpec(id: "tasks.receipts", label: "Payment receipts", sub: nil, seed: ChannelPattern(p: false, e: true, s: false))
            ]
        ),
        Category(
            id: "pulse",
            title: "Pulse",
            helper: "Pulse is quiet by default. Mentions break through, browsing doesn't.",
            rows: [
                CategoryRowSpec(id: "pulse.replies", label: "Replies to my posts", sub: nil, seed: ChannelPattern(p: true, e: false, s: false)),
                CategoryRowSpec(id: "pulse.mentions", label: "Mentions", sub: "When a neighbor @s you", seed: ChannelPattern(p: true, e: false, s: false)),
                CategoryRowSpec(id: "pulse.lostFound", label: "Nearby Lost & Found", sub: "Within 0.5 mi of your address", seed: ChannelPattern(p: false, e: false, s: false)),
                CategoryRowSpec(id: "pulse.digest", label: "Weekly digest", sub: "Sundays, 8am", seed: ChannelPattern(p: false, e: true, s: false))
            ]
        ),
        Category(
            id: "marketplace",
            title: "Marketplace",
            helper: nil,
            rows: [
                CategoryRowSpec(id: "marketplace.offers", label: "Offers on my listings", sub: nil, seed: ChannelPattern(p: true, e: true, s: false)),
                CategoryRowSpec(id: "marketplace.buyerMessages", label: "Buyer messages", sub: nil, seed: ChannelPattern(p: true, e: false, s: false)),
                CategoryRowSpec(id: "marketplace.priceDrops", label: "Price drops on saved items", sub: nil, seed: ChannelPattern(p: false, e: true, s: false)),
                CategoryRowSpec(id: "marketplace.expiring", label: "Listing expiring soon", sub: "48h before auto-pause", seed: ChannelPattern(p: false, e: true, s: false))
            ]
        ),
        Category(
            id: "homeMailbox",
            title: "Home & Mailbox",
            helper: "Emergency alerts can't be muted on push.",
            rows: [
                CategoryRowSpec(id: "home.package", label: "Package arrived", sub: "When carrier scans \"delivered\"", seed: ChannelPattern(p: true, e: true, s: true)),
                CategoryRowSpec(id: "home.member", label: "Member activity", sub: "Check-ins, new passes, edits", seed: ChannelPattern(p: true, e: false, s: false)),
                CategoryRowSpec(id: "home.civic", label: "Civic notices", sub: "Permits, service alerts", seed: ChannelPattern(p: true, e: true, s: false)),
                CategoryRowSpec(id: RowID.emergency, label: "Emergency alerts", sub: nil, seed: ChannelPattern(p: true, e: true, s: true))
            ]
        ),
        Category(
            id: "accountSecurity",
            title: "Account & security",
            helper: "Security alerts always come through. You can choose how.",
            rows: [
                CategoryRowSpec(id: "account.signIn", label: "New sign-in", sub: nil, seed: ChannelPattern(p: true, e: true, s: true)),
                CategoryRowSpec(id: "account.verification", label: "Verification status", sub: nil, seed: ChannelPattern(p: true, e: true, s: false)),
                CategoryRowSpec(id: "account.billing", label: "Billing & receipts", sub: nil, seed: ChannelPattern(p: false, e: true, s: false))
            ]
        )
    ]

    static func seedPatterns() -> [String: ChannelPattern] {
        var map: [String: ChannelPattern] = [:]
        for category in categories {
            for row in category.rows { map[row.id] = row.seed }
        }
        return map
    }
}
