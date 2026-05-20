//
//  GigFilterSheet.swift
//  Pantopus
//
//  P5.3 — Gig filter bottom sheet. A thin projection over the shared
//  `FilterSheetShell`: `GigFilterCriteria` builds the `[FilterSection]`
//  the shell renders and parses the applied sections back into a typed
//  value the feed view-model filters its already-fetched gigs against.
//  Filtering is client-side (the `/api/gigs` list endpoint only models
//  category + sort) so Apply narrows the loaded rows immediately.
//

import Foundation
import SwiftUI

// MARK: - Dimension enums

/// Schedule chip filter. Backend `schedule_type` is loosely typed
/// ("scheduled" / "flexible" / seed values), so matching is tolerant —
/// see `from(backendKey:)`.
public enum GigScheduleFilter: String, CaseIterable, Sendable, Hashable, Identifiable {
    case oneTime
    case recurring
    case flexible

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .oneTime: "One-time"
        case .recurring: "Recurring"
        case .flexible: "Flexible"
        }
    }

    /// Map a backend `schedule_type` to a filter bucket. Returns `nil`
    /// when the value is missing or unrecognised.
    public static func from(backendKey raw: String?) -> GigScheduleFilter? {
        let key = (raw ?? "")
            .lowercased()
            .replacingOccurrences(of: "_", with: "")
            .replacingOccurrences(of: "-", with: "")
        switch key {
        case "onetime", "scheduled", "once": return .oneTime
        case "recurring", "repeat", "repeating": return .recurring
        case "flexible", "flex", "anytime": return .flexible
        default: return nil
        }
    }
}

/// "Posted within" radio filter.
public enum GigPostedWithin: String, CaseIterable, Sendable, Hashable, Identifiable {
    case anytime
    case today
    case week
    case month

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .anytime: "Anytime"
        case .today: "Today"
        case .week: "This week"
        case .month: "This month"
        }
    }

    /// Earliest acceptable post date relative to `now`. `nil` means no
    /// lower bound (`anytime`).
    public func cutoff(from now: Date) -> Date? {
        switch self {
        case .anytime: nil
        case .today: now.addingTimeInterval(-86_400)
        case .week: now.addingTimeInterval(-604_800)
        case .month: now.addingTimeInterval(-2_592_000)
        }
    }
}

// MARK: - Criteria

/// The applied Gig filter selection. The default value is the
/// "everything passes" position, so a freshly-constructed criteria
/// has an `activeCount` of zero and `matches` returns `true` for every
/// gig.
public struct GigFilterCriteria: Sendable, Hashable {
    /// Empty == "all categories".
    public var categories: Set<GigsCategory>
    /// Lower budget handle (dollars). `budgetMin` == no lower bound.
    public var budgetLower: Double
    /// Upper budget handle (dollars). `budgetMax` == "no ceiling" ($500+).
    public var budgetUpper: Double
    /// Empty == "any schedule".
    public var schedules: Set<GigScheduleFilter>
    /// When `true`, keep only gigs still accepting bids (unassigned).
    public var openToBids: Bool
    public var postedWithin: GigPostedWithin

    /// Budget slider domain. `budgetMax` doubles as the "$500+" ceiling.
    public static let budgetMin: Double = 0
    public static let budgetMax: Double = 500
    public static let budgetStep: Double = 25

    /// Stable id for the single "open to bids" chip.
    static let openToBidsOptionID = "openToBids"

    public init(
        categories: Set<GigsCategory> = [],
        budgetLower: Double = GigFilterCriteria.budgetMin,
        budgetUpper: Double = GigFilterCriteria.budgetMax,
        schedules: Set<GigScheduleFilter> = [],
        openToBids: Bool = false,
        postedWithin: GigPostedWithin = .anytime
    ) {
        self.categories = categories
        self.budgetLower = budgetLower
        self.budgetUpper = budgetUpper
        self.schedules = schedules
        self.openToBids = openToBids
        self.postedWithin = postedWithin
    }

    /// Concrete categories the chip group offers (`all` is a sentinel
    /// and never shown in the sheet).
    static let categoryOptions: [GigsCategory] = GigsCategory.allCases.filter { $0 != .all }

    var isBudgetActive: Bool {
        budgetLower > Self.budgetMin || budgetUpper < Self.budgetMax
    }

    /// Number of active filter dimensions — drives the "N filters" pill.
    public var activeCount: Int {
        var count = 0
        if !categories.isEmpty { count += 1 }
        if isBudgetActive { count += 1 }
        if !schedules.isEmpty { count += 1 }
        if openToBids { count += 1 }
        if postedWithin != .anytime { count += 1 }
        return count
    }

    // MARK: Sections (criteria → shell)

    public func sections() -> [FilterSection] {
        [
            FilterSection(
                id: "category",
                title: "Category",
                control: .chipGroup(
                    options: Self.categoryOptions.map { FilterOption(id: $0.rawValue, label: $0.label) },
                    selectedIds: Set(categories.map(\.rawValue))
                )
            ),
            FilterSection(
                id: "budget",
                title: "Budget ($0–$500+)",
                control: .rangeSlider(
                    FilterRange(
                        min: Self.budgetMin,
                        max: Self.budgetMax,
                        lower: budgetLower,
                        upper: budgetUpper,
                        step: Self.budgetStep
                    )
                )
            ),
            FilterSection(
                id: "schedule",
                title: "Schedule",
                control: .chipGroup(
                    options: GigScheduleFilter.allCases.map { FilterOption(id: $0.rawValue, label: $0.label) },
                    selectedIds: Set(schedules.map(\.rawValue))
                )
            ),
            FilterSection(
                id: "openToBids",
                title: "Bids",
                control: .chipGroup(
                    options: [FilterOption(id: Self.openToBidsOptionID, label: "Open to bids only")],
                    selectedIds: openToBids ? [Self.openToBidsOptionID] : []
                )
            ),
            FilterSection(
                id: "postedWithin",
                title: "Posted within",
                control: .radio(
                    options: GigPostedWithin.allCases.map { FilterOption(id: $0.rawValue, label: $0.label) },
                    selectedId: postedWithin.rawValue
                )
            )
        ]
    }

    // MARK: Parse (shell → criteria)

    public init(sections: [FilterSection]) {
        self.init()
        for section in sections {
            switch (section.id, section.control) {
            case let ("category", .chipGroup(_, ids)):
                categories = Set(ids.compactMap(GigsCategory.init(rawValue:)))
            case let ("budget", .rangeSlider(range)):
                budgetLower = range.lower
                budgetUpper = range.upper
            case let ("schedule", .chipGroup(_, ids)):
                schedules = Set(ids.compactMap(GigScheduleFilter.init(rawValue:)))
            case let ("openToBids", .chipGroup(_, ids)):
                openToBids = ids.contains(Self.openToBidsOptionID)
            case let ("postedWithin", .radio(_, selectedId)):
                postedWithin = selectedId.flatMap(GigPostedWithin.init(rawValue:)) ?? .anytime
            default:
                break
            }
        }
    }

    // MARK: Predicates

    /// `true` when `category` survives the category dimension.
    func matchesCategory(_ category: GigsCategory) -> Bool {
        categories.isEmpty || categories.contains(category)
    }

    /// `true` when `price` survives the budget dimension. A `nil` price
    /// only passes when the budget filter is inactive.
    func matchesBudget(_ price: Double?) -> Bool {
        guard isBudgetActive else { return true }
        guard let price else { return false }
        if price < budgetLower { return false }
        if budgetUpper < Self.budgetMax, price > budgetUpper { return false }
        return true
    }

    /// Full gig predicate across every dimension.
    public func matches(_ gig: GigDTO, now: Date = Date()) -> Bool {
        guard matchesCategory(GigsCategory.from(backendKey: gig.category)) else { return false }
        guard matchesBudget(gig.price) else { return false }
        if !schedules.isEmpty {
            guard let bucket = GigScheduleFilter.from(backendKey: gig.scheduleType),
                  schedules.contains(bucket) else { return false }
        }
        if openToBids, !(gig.acceptedBy ?? "").isEmpty { return false }
        if let cutoff = postedWithin.cutoff(from: now) {
            guard let posted = Self.parseDate(gig.createdAt), posted >= cutoff else { return false }
        }
        return true
    }

    static func parseDate(_ iso: String?) -> Date? {
        guard let iso else { return nil }
        let withFraction = ISO8601DateFormatter()
        withFraction.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return withFraction.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
    }
}

// MARK: - Sheet

/// Gig filter bottom sheet. Host presents it via `.sheet`; `onApply`
/// fires with the parsed criteria, then the shell calls `onClose`.
@MainActor
public struct GigFilterSheet: View {
    private let criteria: GigFilterCriteria
    private let onApply: @MainActor (GigFilterCriteria) -> Void
    private let onClose: @MainActor () -> Void

    public init(
        criteria: GigFilterCriteria,
        onApply: @escaping @MainActor (GigFilterCriteria) -> Void,
        onClose: @escaping @MainActor () -> Void
    ) {
        self.criteria = criteria
        self.onApply = onApply
        self.onClose = onClose
    }

    public var body: some View {
        FilterSheetShell(
            title: "Filters",
            sections: criteria.sections(),
            onApply: { sections in onApply(GigFilterCriteria(sections: sections)) },
            onClose: onClose
        )
        .accessibilityIdentifier("gigFilterSheet")
    }
}

#Preview("Default") {
    GigFilterSheet(criteria: GigFilterCriteria(), onApply: { _ in }, onClose: {})
}

#Preview("Active") {
    GigFilterSheet(
        criteria: GigFilterCriteria(
            categories: [.handyman, .cleaning],
            budgetLower: 50,
            budgetUpper: 300,
            schedules: [.oneTime],
            openToBids: true,
            postedWithin: .week
        ),
        onApply: { _ in },
        onClose: {}
    )
}
