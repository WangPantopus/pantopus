//
//  WalletViewModel.swift
//  Pantopus
//
//  A10.10 / P1-F — backs `WalletView`.
//
//  READ-PATH wiring (P1-F): the production initializer hydrates the balance
//  hero + Recent-activity feed from live data —
//    GET /api/wallet               (available balance)
//    GET /api/wallet/transactions  (activity feed)
//    GET /api/wallet/pending-release (escrow breakdown → pending total)
//  The withdraw / payout surface (payout method card, tax docs, Withdraw CTA)
//  is intentionally left on its P3.2 visual placeholder — `POST
//  /api/wallet/withdraw` and the Stripe Connect payout method land in Phase 3.
//
//  Previews / snapshots / unit tests still seed deterministic
//  `WalletContent` via `init(content:)` / `init(state:)`, which bypass the
//  network; `content.isOnHold` selects populated vs. hold. State machine
//  matches the doc's four-state rule: loading / populated / hold / error.
//

import Foundation
import Observation

@Observable
@MainActor
public final class WalletViewModel {
    public enum State: Equatable, Sendable {
        case loading
        case populated(WalletContent)
        case hold(WalletContent)
        case error(message: String)
    }

    public private(set) var state: State = .loading

    private let api: APIClient
    private let calendar: Calendar
    private let now: @Sendable () -> Date
    /// Non-nil for the sample/preview path — `load()` resolves locally and
    /// never touches the network.
    private let sampleContent: WalletContent?
    /// When a caller seeds an explicit state (loading / error chrome),
    /// `load()` is a no-op so the seed sticks.
    private let seeded: Bool

    /// Production initializer — uses the shared API client. Public-safe: it
    /// takes no `APIClient` parameter (the client type + `.shared` are
    /// module-internal). `load()` fetches the read endpoints and maps them
    /// into `WalletContent`.
    public convenience init(
        calendar: Calendar = .current,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.init(api: .shared, calendar: calendar, now: now)
    }

    /// Designated live initializer. `api` is injectable for tests; internal
    /// because `APIClient` is internal.
    init(
        api: APIClient,
        calendar: Calendar = .current,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.api = api
        self.calendar = calendar
        self.now = now
        sampleContent = nil
        seeded = false
    }

    /// Sample/preview path — resolve straight from deterministic content.
    public init(content: WalletContent) {
        api = .shared
        calendar = .current
        now = { Date() }
        sampleContent = content
        seeded = false
    }

    /// Seed an explicit state — used by previews and tests to exercise the
    /// loading + error chrome without a network layer.
    public init(state: State, content: WalletContent = WalletSampleData.populated) {
        api = .shared
        calendar = .current
        now = { Date() }
        sampleContent = content
        self.state = state
        seeded = true
    }

    public func load() async {
        guard !seeded else { return }
        if let sampleContent {
            state = sampleContent.isOnHold ? .hold(sampleContent) : .populated(sampleContent)
            return
        }
        await fetchLive()
    }

    public func refresh() async {
        await load()
    }

    // MARK: - Live fetch

    private func fetchLive() async {
        state = .loading
        do {
            let balance: WalletBalanceResponse = try await api.request(WalletEndpoints.balance())
            let history: WalletTransactionsResponse = try await api.request(WalletEndpoints.transactions())
            // Pending-release is supplementary — a failure there shouldn't sink
            // the whole screen, so it degrades to nil (zero pending).
            let pending: WalletPendingReleaseResponse? = try? await api.request(
                WalletEndpoints.pendingRelease()
            )
            let content = Self.makeContent(
                balance: balance,
                transactions: history.transactions,
                pending: pending,
                calendar: calendar,
                now: now()
            )
            state = .populated(content)
        } catch {
            state = .error(
                message: (error as? APIError)?.errorDescription ?? "Couldn't load your wallet."
            )
        }
    }

    // MARK: - Mapping (pure — unit-test surface)

    /// Project the read-path DTOs into a `WalletContent`. The withdraw/payout
    /// slots (payout method, tax docs) reuse the P3.2 visual placeholder —
    /// they are wired in Phase 3 with Stripe Connect — and `holdState` stays
    /// nil because the hold banner copy is Stripe-specific.
    public static func makeContent(
        balance: WalletBalanceResponse,
        transactions: [WalletTransactionDTO],
        pending: WalletPendingReleaseResponse?,
        calendar: Calendar = .current,
        now: Date = Date()
    ) -> WalletContent {
        let pendingCents = pending?.totalPendingCents ?? 0
        let pendingCount = (pending?.inReviewCount ?? 0) + (pending?.releasingSoonCount ?? 0)
        let activity = transactions.map { activityItem(from: $0, calendar: calendar, now: now) }
        let placeholder = WalletSampleData.populated
        return WalletContent(
            available: centsToPlain(balance.wallet.balance),
            pending: centsToCurrency(pendingCents),
            pendingMeta: pendingMeta(count: pendingCount, cents: pendingCents),
            monthValue: centsToCurrency(monthIncomeCents(transactions, calendar: calendar, now: now)),
            monthMeta: monthMeta(count: monthIncomeCount(transactions, calendar: calendar, now: now)),
            activity: activity,
            payoutMethod: placeholder.payoutMethod,
            taxDocs: placeholder.taxDocs,
            holdState: nil
        )
    }

    /// Map one `WalletTransaction` row into an activity row.
    public static func activityItem(
        from tx: WalletTransactionDTO,
        calendar: Calendar = .current,
        now: Date = Date()
    ) -> WalletActivityItem {
        let date = parseDate(tx.createdAt)
        let direction = direction(for: tx.type)
        return WalletActivityItem(
            id: tx.id,
            day: dayLabel(date, calendar: calendar, now: now),
            dateLabel: timeLabel(date),
            description: tx.description ?? typeLabel(for: tx.type),
            counterparty: counterpartyLabel(for: tx.type),
            category: category(for: tx.type),
            direction: direction,
            status: status(for: tx, direction: direction),
            amount: centsToPlain(tx.amount),
            isFee: tx.type == "cancellation_fee"
        )
    }

    // MARK: - Field mappers

    /// Outbound for withdrawals, sent tips/payments, and fees; inbound for
    /// everything else (deposits, gig/tip income, refunds, adjustments).
    static func direction(for type: String) -> ActivityDirection {
        switch type {
        case "withdrawal", "gig_payment", "tip_sent", "transfer_out", "cancellation_fee":
            .out
        default:
            .in
        }
    }

    /// Map the transaction type onto one of the six designed activity tiles.
    /// (The feed has no service-category metadata, so types are bucketed:
    /// bank movements → `.bank`, fees/refunds → `.fee`, earnings → `.handyman`.)
    static func category(for type: String) -> WalletActivityCategory {
        switch type {
        case "withdrawal", "deposit", "transfer_in", "transfer_out":
            .bank
        case "cancellation_fee", "refund", "adjustment":
            .fee
        default:
            .handyman
        }
    }

    static func status(for tx: WalletTransactionDTO, direction: ActivityDirection) -> ActivityStatus {
        switch tx.status {
        case "pending":
            .pending(clearsLabel: "soon")
        default:
            direction == .out ? .complete : .available
        }
    }

    static func counterpartyLabel(for type: String) -> String {
        switch type {
        case "withdrawal", "deposit", "transfer_in", "transfer_out": "Bank"
        case "gig_income", "gig_payment": "Gig"
        case "tip_income", "tip_sent": "Tip"
        case "refund": "Refund"
        case "cancellation_fee": "Pantopus"
        default: "Adjustment"
        }
    }

    static func typeLabel(for type: String) -> String {
        switch type {
        case "withdrawal": "Withdrawal"
        case "deposit": "Deposit"
        case "gig_income", "gig_payment": "Gig payment"
        case "tip_income": "Tip received"
        case "tip_sent": "Tip sent"
        case "refund": "Refund"
        case "cancellation_fee": "Cancellation fee"
        default: "Adjustment"
        }
    }

    // MARK: - Aggregates

    private static func monthIncomeCents(
        _ transactions: [WalletTransactionDTO],
        calendar: Calendar,
        now: Date
    ) -> Int {
        monthIncomeRows(transactions, calendar: calendar, now: now).reduce(0) { $0 + $1.amount }
    }

    private static func monthIncomeCount(
        _ transactions: [WalletTransactionDTO],
        calendar: Calendar,
        now: Date
    ) -> Int {
        monthIncomeRows(transactions, calendar: calendar, now: now).count
    }

    /// Inbound rows whose `created_at` falls in the same calendar month as
    /// `now` — drives the "this month" summary.
    private static func monthIncomeRows(
        _ transactions: [WalletTransactionDTO],
        calendar: Calendar,
        now: Date
    ) -> [WalletTransactionDTO] {
        transactions.filter { tx in
            guard direction(for: tx.type) == .in, let date = parseDate(tx.createdAt) else { return false }
            return calendar.isDate(date, equalTo: now, toGranularity: .month)
        }
    }

    private static func pendingMeta(count: Int, cents: Int) -> String {
        guard cents > 0 else { return "Nothing in escrow" }
        let noun = count == 1 ? "payment" : "payments"
        return "\(count) \(noun) · releases after review"
    }

    private static func monthMeta(count: Int) -> String {
        let noun = count == 1 ? "task" : "tasks"
        return "\(count) \(noun) this month"
    }

    // MARK: - Formatting

    private static let centsFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        formatter.locale = Locale(identifier: "en_US")
        return formatter
    }()

    /// Integer cents → grouped 2-dp string with no symbol, e.g. `"1,284.50"`.
    static func centsToPlain(_ cents: Int) -> String {
        let dollars = Double(cents) / 100.0
        return centsFormatter.string(from: NSNumber(value: dollars)) ?? String(format: "%.2f", dollars)
    }

    /// Integer cents → `"$1,284.50"`.
    static func centsToCurrency(_ cents: Int) -> String {
        "$\(centsToPlain(cents))"
    }

    private static let iso8601Fraction: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let iso8601Plain: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    static func parseDate(_ raw: String?) -> Date? {
        guard let raw, !raw.isEmpty else { return nil }
        return iso8601Fraction.date(from: raw) ?? iso8601Plain.date(from: raw)
    }

    /// Day-group header: "Today" / "Yesterday" / "Nov 28".
    static func dayLabel(_ date: Date?, calendar: Calendar, now: Date) -> String {
        guard let date else { return "—" }
        let startToday = calendar.startOfDay(for: now)
        let startDate = calendar.startOfDay(for: date)
        let delta = calendar.dateComponents([.day], from: startDate, to: startToday).day ?? 0
        if delta == 0 { return "Today" }
        if delta == 1 { return "Yesterday" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = calendar
        formatter.dateFormat = "MMM d"
        return formatter.string(from: date)
    }

    /// Time-of-day sub-label: "2:14 pm".
    static func timeLabel(_ date: Date?) -> String {
        guard let date else { return "" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "h:mm a"
        formatter.amSymbol = "am"
        formatter.pmSymbol = "pm"
        return formatter.string(from: date)
    }
}
