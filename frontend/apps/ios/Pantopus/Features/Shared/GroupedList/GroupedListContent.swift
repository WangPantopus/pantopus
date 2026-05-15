//
//  GroupedListContent.swift
//  Pantopus
//
//  Render models for the shared GroupedList archetype — every
//  settings-style surface in the app uses this shape. Two levels:
//  `groups[]` with optional overline + helper caption + `rows[]`. Each
//  row carries a single `RowControl` that drives the right-side
//  affordance (chevron, toggle, radio, chip+chevron, slider).
//

import Foundation

/// The right-side control on one row. Drives both layout and the
/// callback the data source hears back from.
public enum RowControl: Sendable, Hashable {
    /// Plain navigation row — just a chevron on the right.
    case chevron
    /// Binary preference toggle.
    case toggle(isOn: Bool)
    /// Radio selection within the group; one row per option, the
    /// `isSelected` row carries `true`.
    case radio(isSelected: Bool)
    /// Status / value chip (e.g. "Verified", "Stripe connected").
    /// `includesChevron` adds the chevron after the chip when the row
    /// is also navigable.
    case chipStatus(label: String, tone: ChipTone, includesChevron: Bool)
    /// Stops-based slider. `stops` is the ordered label list, `index`
    /// is the active index.
    case slider(stops: [String], index: Int)

    public enum ChipTone: Sendable, Hashable { case success, info, neutral, warning }
}

/// One row in a group.
public struct GroupedListRow: Identifiable, Sendable, Hashable {
    public let id: String
    public let label: String
    /// Optional secondary line under the label (e.g. "3 people",
    /// "Push, email, SMS").
    public let subtext: String?
    public let control: RowControl
    /// Red destructive text + force this row into its own card. The
    /// shell pulls it out of the group and renders a dedicated card.
    public let destructive: Bool

    public init(
        id: String,
        label: String,
        subtext: String? = nil,
        control: RowControl,
        destructive: Bool = false
    ) {
        self.id = id
        self.label = label
        self.subtext = subtext
        self.control = control
        self.destructive = destructive
    }
}

/// One group — a card of rows with optional overline + helper.
public struct GroupedListGroup: Identifiable, Sendable, Hashable {
    public let id: String
    /// 11pt uppercase tracked label rendered above the card. `nil`
    /// hides the overline (used for the standalone destructive card).
    public let overline: String?
    /// 11.5pt fg3 caption below the card. Used for context like
    /// "Sent to maria@…" or "Carrier rates may apply".
    public let helper: String?
    public let rows: [GroupedListRow]

    public init(
        id: String,
        overline: String? = nil,
        helper: String? = nil,
        rows: [GroupedListRow]
    ) {
        self.id = id
        self.overline = overline
        self.helper = helper
        self.rows = rows
    }
}

/// Render state for the shell.
public enum GroupedListState: Sendable {
    case loading
    case loaded([GroupedListGroup])
    case error(message: String)
}

/// Contract every grouped-list feature implements. View-models
/// conform; views are pure.
@MainActor
public protocol GroupedListDataSource: AnyObject, Observable {
    /// Top-bar title.
    var title: String { get }
    /// Footer caption (e.g. "maria@pantopus · ID 8174"). `nil` hides
    /// the footer.
    var footerCaption: String? { get }
    /// Observed state.
    var state: GroupedListState { get }

    /// Triggered on first appear + retry-after-error.
    func load() async
    /// Tap on a `.chevron` or `.chipStatus(includesChevron: true)` row.
    func tapRow(_ rowId: String) async
    /// Tap on a `.toggle` row. The shell flips the value
    /// optimistically; the data source is responsible for the persist
    /// + rollback.
    func toggleRow(_ rowId: String, isOn: Bool) async
    /// Tap on a `.radio` row. The shell highlights it optimistically
    /// before this returns.
    func selectRadio(_ rowId: String) async
    /// Drag on a `.slider` row. `index` is the stop index after the
    /// user's release.
    func setSlider(_ rowId: String, index: Int) async
}

/// Convenience helpers feature view-models reach for in their
/// optimistic mutation paths.
public extension GroupedListGroup {
    /// Find a row by id within this group.
    func row(id: String) -> GroupedListRow? {
        rows.first { $0.id == id }
    }
}
