//
//  BillsListViewModel.swift
//  Pantopus
//
//  Backs `BillsListView` (T5.2.2 / P13). Fetches
//  `GET /api/homes/:id/bills` (route `backend/routes/home.js:4506`) and
//  maps each row to the `RowTrailing.amountWithChip` template defined
//  in the T5.0 shell. Three tabs (Upcoming / Paid / All) filter by a
//  client-derived `ChipStatus`:
//
//    paid       — `status == "paid"`
//    overdue    — non-paid and `due_date` is in the past
//    scheduled  — `status == "scheduled"`
//    due        — everything else still upcoming
//
//  Row taps emit `onOpenBill(billId)`; the FAB / empty-state CTA emit
//  `onAddBill()`. The host (HubTabRoot) routes both.
//

import Foundation
import Observation
import SwiftUI

/// Canonical bill-chip status, derived from `BillDTO.status` + `due_date`.
public enum BillChipStatus: String, Sendable, Hashable {
    case due
    case overdue
    case paid
    case scheduled
}

/// Tab identifiers for the Bills shell — kept as raw strings so they
/// survive the `ListOfRowsDataSource.selectedTab: String` contract.
enum BillsTab: String, CaseIterable {
    case upcoming
    case paid
    case all
}

private struct BillsTabCounts {
    let upcoming: Int?
    let paid: Int?
    let all: Int?
}

/// ViewModel for the Bills list. Builds `RowModel`s from `BillDTO`s and
/// re-renders the tab filter client-side — backend supports a
/// `?status=` query but the design wants three buckets the server
/// doesn't speak, so the VM owns the projection.
@Observable
@MainActor
final class BillsListViewModel: ListOfRowsDataSource {
    let title = "Bills"
    var topBarAction: TopBarAction? {
        TopBarAction(
            icon: .plusCircle,
            accessibilityLabel: "Add a bill"
        ) { [onAddBill] in onAddBill() }
    }

    /// Tabs with live counts. Rebuilt whenever `bills` changes.
    var tabs: [ListOfRowsTab] {
        let summary = bills.map(counts) ?? BillsTabCounts(upcoming: nil, paid: nil, all: nil)
        return [
            ListOfRowsTab(id: BillsTab.upcoming.rawValue, label: "Upcoming", count: summary.upcoming),
            ListOfRowsTab(id: BillsTab.paid.rawValue, label: "Paid", count: summary.paid),
            ListOfRowsTab(id: BillsTab.all.rawValue, label: "All", count: summary.all)
        ]
    }

    var selectedTab: String = BillsTab.upcoming.rawValue {
        didSet { rebuildState() }
    }

    var fab: FABAction? {
        FABAction(
            icon: .plusCircle,
            accessibilityLabel: "Add a bill",
            variant: .secondaryCreate
        ) { [onAddBill] in onAddBill() }
    }

    private(set) var state: ListOfRowsState = .loading

    /// Last successful payload — held so a tab change can re-filter
    /// without re-fetching.
    private var bills: [BillDTO]?

    private let homeId: String
    private let api: APIClient
    private let onOpenBill: @Sendable (String) -> Void
    private let onAddBill: @Sendable () -> Void
    /// Inject a stable "now" for tests; production uses `Date.init`.
    private let now: @Sendable () -> Date

    init(
        homeId: String,
        api: APIClient = .shared,
        onOpenBill: @escaping @Sendable (String) -> Void = { _ in },
        onAddBill: @escaping @Sendable () -> Void = {},
        now: @escaping @Sendable () -> Date = Date.init
    ) {
        self.homeId = homeId
        self.api = api
        self.onOpenBill = onOpenBill
        self.onAddBill = onAddBill
        self.now = now
    }

    func load() async {
        if case .loading = state {} else { state = .loading }
        await fetch()
    }

    func refresh() async {
        await fetch()
    }

    /// Backend has no pagination on bills today.
    func loadMoreIfNeeded() async {}

    /// Re-issue the load after a successful create / update — the host
    /// view calls this on `pendingEvent == .created`. Falls back to the
    /// same `fetch()` path so optimistic UI isn't required.
    func reloadAfterMutation() async {
        await fetch()
    }

    private func fetch() async {
        do {
            let response: GetHomeBillsResponse = try await api.request(
                HomesEndpoints.bills(homeId: homeId)
            )
            bills = response.bills
            rebuildState()
        } catch {
            bills = nil
            state = .error(
                message: (error as? APIError)?.errorDescription
                    ?? "Couldn't load your bills."
            )
        }
    }

    private func rebuildState() {
        guard let bills else { return }
        let nowDate = now()
        let tab = BillsTab(rawValue: selectedTab) ?? .upcoming
        let filtered = bills.filter { passes($0, tab: tab, now: nowDate) }
        if filtered.isEmpty {
            state = .empty(
                ListOfRowsState.EmptyContent(
                    icon: .receipt,
                    headline: "No bills yet",
                    subcopy: "Add a bill to track due dates, schedule payments, and split with household members.",
                    ctaTitle: "Add a bill"
                ) { [onAddBill] in onAddBill() }
            )
            return
        }
        let rows = filtered.map { row(for: $0, now: nowDate) }
        state = .loaded(sections: [RowSection(rows: rows)], hasMore: false)
    }

    // MARK: - Row + chip mapping

    func row(for bill: BillDTO, now: Date) -> RowModel {
        let projection = BillsListViewModel.project(bill: bill, now: now)
        let billId = bill.id
        return RowModel(
            id: bill.id,
            title: projection.payee,
            subtitle: projection.subtitle,
            template: .statusChip,
            leading: .typeIcon(.receipt, background: Theme.Color.primary50, foreground: Theme.Color.primary600),
            trailing: .amountWithChip(
                amount: projection.amount,
                chipText: projection.chipText,
                chipVariant: projection.chipVariant,
                chipIcon: projection.chipIcon
            )
        ) { [onOpenBill] in onOpenBill(billId) }
    }

    /// Pure mapping from a bill + clock to display strings. Exposed
    /// `static` so unit tests can exercise the chip / subtitle
    /// derivation without standing the VM up.
    static func project(bill: BillDTO, now: Date) -> BillRowProjection {
        let chip = chipStatus(for: bill, now: now)
        let payee = bill.providerName ?? bill.billType.capitalized
        let amount = formatCurrency(bill.displayAmount)
        let dueShort = formatDateShort(iso: bill.dueDate)
        let paidShort = formatDateShort(iso: bill.paidAt)

        switch chip {
        case .paid:
            return BillRowProjection(
                payee: payee,
                subtitle: paidShort.map { "Paid \($0)" } ?? "Paid",
                amount: amount,
                chipText: "Paid",
                chipVariant: .success,
                chipIcon: .check,
                status: chip
            )
        case .overdue:
            return BillRowProjection(
                payee: payee,
                subtitle: dueShort.map { "Due \($0)" } ?? "Overdue",
                amount: amount,
                chipText: "Overdue",
                chipVariant: .error,
                chipIcon: .alertCircle,
                status: chip
            )
        case .scheduled:
            return BillRowProjection(
                payee: payee,
                subtitle: dueShort.map { "Auto-pay \($0)" } ?? "Auto-pay",
                amount: amount,
                chipText: "Scheduled",
                chipVariant: .personal,
                chipIcon: .calendar,
                status: chip
            )
        case .due:
            return BillRowProjection(
                payee: payee,
                subtitle: dueShort ?? "No due date",
                amount: amount,
                chipText: dueShort.map { "Due \($0)" } ?? "Due",
                chipVariant: .warning,
                chipIcon: .clock,
                status: chip
            )
        }
    }

    static func chipStatus(for bill: BillDTO, now: Date) -> BillChipStatus {
        if bill.status == "paid" { return .paid }
        if bill.status == "scheduled" { return .scheduled }
        if let iso = bill.dueDate, let due = parseDate(iso), due < now {
            return .overdue
        }
        return .due
    }

    private func passes(_ bill: BillDTO, tab: BillsTab, now: Date) -> Bool {
        if bill.status == "cancelled" { return false }
        let chip = BillsListViewModel.chipStatus(for: bill, now: now)
        switch tab {
        case .upcoming:
            return chip == .due || chip == .overdue || chip == .scheduled
        case .paid:
            return chip == .paid
        case .all:
            return true
        }
    }

    private func counts(_ bills: [BillDTO]) -> BillsTabCounts {
        let nowDate = now()
        var upcoming = 0
        var paid = 0
        var all = 0
        for b in bills {
            if b.status == "cancelled" { continue }
            all += 1
            let chip = BillsListViewModel.chipStatus(for: b, now: nowDate)
            switch chip {
            case .paid: paid += 1
            case .due, .overdue, .scheduled: upcoming += 1
            }
        }
        return BillsTabCounts(upcoming: upcoming, paid: paid, all: all)
    }

    // MARK: - Formatting

    static func formatCurrency(_ amount: Decimal) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencyCode = "USD"
        formatter.maximumFractionDigits = 2
        formatter.minimumFractionDigits = 2
        return formatter.string(from: amount as NSDecimalNumber) ?? "$\(amount)"
    }

    static func formatDateShort(iso: String?) -> String? {
        guard let iso, let date = parseDate(iso) else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "MMM d"
        return formatter.string(from: date)
    }

    private static func parseDate(_ iso: String) -> Date? {
        // Accept full ISO timestamps and bare yyyy-MM-dd strings.
        let isoFull = ISO8601DateFormatter()
        isoFull.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = isoFull.date(from: iso) { return d }
        let isoShort = ISO8601DateFormatter()
        isoShort.formatOptions = [.withInternetDateTime]
        if let d = isoShort.date(from: iso) { return d }
        let day = DateFormatter()
        day.locale = Locale(identifier: "en_US_POSIX")
        day.timeZone = TimeZone(secondsFromGMT: 0)
        day.dateFormat = "yyyy-MM-dd"
        return day.date(from: iso)
    }
}

/// Pure projection of one bill into a row's display fields. Used both
/// by the VM and by tests.
public struct BillRowProjection: Sendable, Equatable {
    public let payee: String
    public let subtitle: String
    public let amount: String
    public let chipText: String
    public let chipVariant: StatusChipVariant
    public let chipIcon: PantopusIcon?
    public let status: BillChipStatus
}
