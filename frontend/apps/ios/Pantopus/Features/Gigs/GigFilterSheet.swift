//
//  GigFilterSheet.swift
//  Pantopus
//
//  P5.3 — Gig filter bottom sheet. A thin projection over the shared
//  `FilterSheetShell`: `GigFilterCriteria` builds the `[FilterSection]`
//  the shell renders and parses the applied sections back into a typed
//  value. Budget bounds (`minPrice`/`maxPrice`), open-to-bids
//  (`pay_type=offers`), and a single schedule (`schedule_type`) are
//  forwarded to `GET /api/gigs` as query params — Apply refetches. The
//  dimensions the API can't express (multi-category, multi-schedule,
//  posted-within) stay client-side via `matchesClientSide`.
//  P6a adds the saved-search mapping + the footer save/manage row.
//

// swiftlint:disable file_length

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

    public var id: String {
        rawValue
    }

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

    public var id: String {
        rawValue
    }

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
        case .today: now.addingTimeInterval(-86400)
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

    // MARK: Server-side query mapping (GET /api/gigs)

    /// `minPrice` query param — only when the lower handle moved.
    public var serverMinPrice: Double? {
        budgetLower > Self.budgetMin ? budgetLower : nil
    }

    /// `maxPrice` query param — `budgetMax` is the open-ended "$500+"
    /// ceiling, so it imposes no upper bound.
    public var serverMaxPrice: Double? {
        budgetUpper < Self.budgetMax ? budgetUpper : nil
    }

    /// `pay_type=offers` — the backend models "open to bids" as a pay type.
    public var serverPayType: String? {
        openToBids ? "offers" : nil
    }

    /// `schedule_type` query param. The backend takes a single value, so
    /// it's only forwarded when exactly one schedule is selected *and*
    /// that selection has a backend equivalent — `recurring` has none
    /// (gigs store it as `flexible`-ish seed values), so it stays
    /// client-side, as does any multi-select.
    public var serverScheduleType: String? {
        guard schedules.count == 1, let only = schedules.first else { return nil }
        switch only {
        case .oneTime: return "scheduled"
        case .flexible: return "flexible"
        case .recurring: return nil
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

    /// Full gig predicate across every dimension. Used by surfaces that
    /// filter purely client-side (e.g. the Nearby map pins).
    public func matches(_ gig: GigDTO, now: Date = Date()) -> Bool {
        matches(
            category: GigsCategory.from(backendKey: gig.category),
            price: gig.price,
            scheduleType: gig.scheduleType,
            acceptedBy: gig.acceptedBy,
            createdAt: gig.createdAt,
            now: now
        )
    }

    /// Primitive-field overload for surfaces that project away the DTO
    /// (the Tasks map's `TaskMapItem` — seed/preview mode has no `GigDTO`).
    public func matches(
        category: GigsCategory,
        price: Double?,
        scheduleType: String?,
        acceptedBy: String?,
        createdAt: String?,
        now: Date = Date()
    ) -> Bool {
        guard matchesCategory(category) else { return false }
        guard matchesBudget(price) else { return false }
        if !schedules.isEmpty {
            guard let bucket = GigScheduleFilter.from(backendKey: scheduleType),
                  schedules.contains(bucket) else { return false }
        }
        if openToBids, !(acceptedBy ?? "").isEmpty { return false }
        if let cutoff = postedWithin.cutoff(from: now) {
            guard let posted = Self.parseDate(createdAt), posted >= cutoff else { return false }
        }
        return true
    }

    /// Residual predicate for the Gigs feed — only the dimensions
    /// `GET /api/gigs` can't express. Budget + open-to-bids (+ a single
    /// mappable schedule) ride the request as query params, so they're
    /// deliberately absent here. Posted-within stays client-side: the
    /// backend has no posted-within / created-after param.
    public func matchesClientSide(_ gig: GigDTO, now: Date = Date()) -> Bool {
        guard matchesCategory(GigsCategory.from(backendKey: gig.category)) else { return false }
        if !schedules.isEmpty, serverScheduleType == nil {
            guard let bucket = GigScheduleFilter.from(backendKey: gig.scheduleType),
                  schedules.contains(bucket) else { return false }
        }
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

// MARK: - Saved-search mapping (P6a)

/// `POST /api/gigs/saved-searches` projection — pure functions so the
/// derived name + body are testable without a view. Route
/// `backend/routes/gigSavedSearches.js:64`.
public extension GigFilterCriteria {
    /// The single category a saved search stores (the backend keeps one
    /// value). Exactly one sheet chip wins; with no sheet chips the
    /// feed's active chip applies (omitting "All"); a multi-select
    /// saves category-less so alerts span every selected category.
    func savedSearchCategory(feedCategory: GigsCategory) -> GigsCategory? {
        if categories.count == 1 { return categories.first }
        if categories.isEmpty, feedCategory != .all { return feedCategory }
        return nil
    }

    /// Client-derived display name, e.g. "Cleaning · under $100 · 5 mi".
    /// Mirrors exactly the criteria that ride the POST body.
    func savedSearchName(
        feedCategory: GigsCategory,
        searchText: String,
        radiusMiles: Double
    ) -> String {
        var pieces = [savedSearchCategory(feedCategory: feedCategory)?.label ?? "All tasks"]
        let trimmed = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { pieces.append("\u{201C}\(trimmed)\u{201D}") }
        switch (serverMinPrice, serverMaxPrice) {
        case let (min?, max?): pieces.append("$\(Int(min))–$\(Int(max))")
        case let (min?, nil): pieces.append("over $\(Int(min))")
        case let (nil, max?): pieces.append("under $\(Int(max))")
        case (nil, nil): break
        }
        if serverScheduleType != nil, let only = schedules.first { pieces.append(only.label) }
        if openToBids { pieces.append("open to bids") }
        pieces.append(
            radiusMiles.truncatingRemainder(dividingBy: 1) == 0
                ? "\(Int(radiusMiles)) mi"
                : String(format: "%.1f mi", radiusMiles)
        )
        return pieces.joined(separator: " · ")
    }

    /// Build the `POST /api/gigs/saved-searches` body from the live
    /// state: this criteria + the feed's active chip, search text, and
    /// resolved location/radius. Server-expressible dimensions reuse
    /// the existing `GET /api/gigs` mappings (`serverMinPrice` /
    /// `serverMaxPrice` / `serverScheduleType` / `serverPayType`);
    /// slider extremes and unmappable selections are omitted.
    func savedSearchBody(
        feedCategory: GigsCategory,
        searchText: String,
        latitude: Double,
        longitude: Double,
        radiusMiles: Double
    ) -> CreateGigSavedSearchBody {
        let trimmed = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        return CreateGigSavedSearchBody(
            name: savedSearchName(
                feedCategory: feedCategory,
                searchText: searchText,
                radiusMiles: radiusMiles
            ),
            category: savedSearchCategory(feedCategory: feedCategory)?.rawValue,
            search: trimmed.isEmpty ? nil : trimmed,
            minPrice: serverMinPrice,
            maxPrice: serverMaxPrice,
            scheduleType: serverScheduleType,
            payType: serverPayType,
            latitude: latitude,
            longitude: longitude,
            radiusMiles: radiusMiles,
            notify: true
        )
    }

    /// "Save this search" enablement: any active criteria dimension or
    /// a non-empty feed search text.
    func canSaveSearch(searchText: String) -> Bool {
        activeCount > 0 || !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

// MARK: - Sheet

/// Gig filter bottom sheet. Host presents it via `.sheet`; `onApply`
/// fires with the parsed criteria, then the shell calls `onClose`.
/// When `onSaveSearch` is supplied (the Gigs feed), the footer grows a
/// "Save this search" button — enabled by the **live working**
/// criteria or feed search text — plus a "Saved searches" link that
/// presents the manage sheet (P6a).
@MainActor
public struct GigFilterSheet: View {
    private let criteria: GigFilterCriteria
    private let searchText: String
    private let onApply: @MainActor (GigFilterCriteria) -> Void
    private let onClose: @MainActor () -> Void
    private let onSaveSearch: (@MainActor (GigFilterCriteria) -> Void)?

    @State private var showManageSheet = false

    public init(
        criteria: GigFilterCriteria,
        searchText: String = "",
        onApply: @escaping @MainActor (GigFilterCriteria) -> Void,
        onClose: @escaping @MainActor () -> Void,
        onSaveSearch: (@MainActor (GigFilterCriteria) -> Void)? = nil
    ) {
        self.criteria = criteria
        self.searchText = searchText
        self.onApply = onApply
        self.onClose = onClose
        self.onSaveSearch = onSaveSearch
    }

    public var body: some View {
        FilterSheetShell(
            title: "Filters",
            sections: criteria.sections(),
            footerAccessory: footerAccessory,
            onApply: { sections in onApply(GigFilterCriteria(sections: sections)) },
            onClose: onClose
        )
        .sheet(isPresented: $showManageSheet) {
            GigSavedSearchesSheet()
        }
        .accessibilityIdentifier("gigFilterSheet")
    }

    /// Save/manage footer row, present only when the host wires
    /// `onSaveSearch` (the Gigs feed). Receives the shell's live
    /// working sections so enablement tracks unapplied edits.
    private var footerAccessory: (@MainActor ([FilterSection]) -> AnyView)? {
        guard let onSaveSearch else { return nil }
        return { sections in
            AnyView(
                self.savedSearchRow(
                    working: GigFilterCriteria(sections: sections),
                    save: onSaveSearch
                )
            )
        }
    }

    /// Footer accessory: save the live working criteria + manage link.
    private func savedSearchRow(
        working: GigFilterCriteria,
        save: @escaping @MainActor (GigFilterCriteria) -> Void
    ) -> some View {
        let canSave = working.canSaveSearch(searchText: searchText)
        return HStack(spacing: Spacing.s3) {
            Button {
                save(working)
            } label: {
                HStack(spacing: 6) {
                    Icon(
                        .bell,
                        size: 13,
                        strokeWidth: 2.2,
                        color: canSave ? Theme.Color.primary600 : Theme.Color.appTextMuted
                    )
                    Text("Save this search")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(canSave ? Theme.Color.primary600 : Theme.Color.appTextMuted)
                }
                .frame(minHeight: 36)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!canSave)
            .accessibilityLabel("Save this search")
            .accessibilityIdentifier("gigFilters.saveSearch")
            Spacer()
            Button {
                showManageSheet = true
            } label: {
                Text("Saved searches")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .underline()
                    .frame(minHeight: 36)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Saved searches")
            .accessibilityIdentifier("gigFilters.manageSearches")
        }
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
