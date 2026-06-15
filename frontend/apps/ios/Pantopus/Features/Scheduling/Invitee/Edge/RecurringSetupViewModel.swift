//
//  RecurringSetupViewModel.swift
//  Pantopus
//
//  D12 Recurring / Multi-Session Setup (Stream I7). Lets the owner lay out a
//  weekly series — weekday + time + session count — then builds the `sessions[]`
//  (UTC ISO starts) and books them in one call (`POST /api/scheduling/bookings/
//  recurring`). A 409 surfaces the nearest open times through the Foundation
//  `SlotTakenSheet` (never a dead end), prompting the owner to adjust the pattern
//  and retry. Tokens only.
//

import SwiftUI

@Observable
@MainActor
final class RecurringSetupViewModel {
    enum State: Equatable {
        case loading
        case configuring
        case confirming
        case booked(count: Int)
        case error(message: String)
    }

    let owner: SchedulingOwner
    let eventTypeId: String
    private let push: @MainActor (SchedulingRoute) -> Void
    private let client: SchedulingClient

    private(set) var state: State = .loading
    private(set) var eventType: EventTypeDTO?

    // Pattern configuration (observed → drives the live preview).
    var weekdayIndex: Int = 2 // 0=Sun … 6=Sat (default Tuesday, matching the design)
    var timeOfDay: Date = RecurringSetupViewModel.defaultTime()
    var count: Int = 6
    let minCount = 1
    let maxCount = 52

    /// Drives the 409 recovery sheet; set when a confirm comes back conflicting.
    var slotConflict: SlotConflictItem?
    /// Shown as a banner after a conflict so the owner knows to adjust.
    private(set) var conflictHint = false

    private var didLoad = false
    private var isFetching = false

    init(
        owner: SchedulingOwner,
        eventTypeId: String,
        push: @escaping @MainActor (SchedulingRoute) -> Void,
        client: SchedulingClient
    ) {
        self.owner = owner
        self.eventTypeId = eventTypeId
        self.push = push
        self.client = client
    }

    func load() async {
        guard !didLoad else { return }
        didLoad = true
        await fetch()
    }

    func refresh() async {
        await fetch()
    }

    private func fetch() async {
        guard !isFetching else { return }
        isFetching = true
        defer { isFetching = false }
        state = .loading
        do {
            let response: EventTypeDetailResponse = try await client.request(
                SchedulingEndpoints.getEventType(owner: owner, id: eventTypeId)
            )
            eventType = response.eventType
            state = .configuring
        } catch let error as SchedulingError {
            state = .error(message: error.userMessage ?? "We couldn't load this event type.")
        } catch {
            state = .error(message: "We couldn't load this event type.")
        }
    }

    // MARK: - Count stepper

    func incrementCount() {
        guard count < maxCount else { return }
        count += 1
        clearConflictHint()
    }

    func decrementCount() {
        guard count > minCount else { return }
        count -= 1
        clearConflictHint()
    }

    func setCount(_ value: Int) {
        count = min(max(value, minCount), maxCount)
        clearConflictHint()
    }

    func setWeekday(_ index: Int) {
        weekdayIndex = index
        clearConflictHint()
    }

    private func clearConflictHint() {
        conflictHint = false
    }

    // MARK: - Session computation

    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        return calendar
    }

    /// The local start instants for the configured series.
    var sessions: [Date] {
        let calendar = self.calendar
        let now = Date()
        let comps = calendar.dateComponents([.hour, .minute], from: timeOfDay)
        let today = calendar.startOfDay(for: now)
        guard var anchor = calendar.date(
            bySettingHour: comps.hour ?? 9,
            minute: comps.minute ?? 0,
            second: 0,
            of: today
        ) else { return [] }

        // Shift to the next matching weekday (1=Sun … 7=Sat in Calendar).
        let target = weekdayIndex + 1
        let current = calendar.component(.weekday, from: anchor)
        let delta = (target - current + 7) % 7
        anchor = calendar.date(byAdding: .day, value: delta, to: anchor) ?? anchor
        if anchor < now {
            anchor = calendar.date(byAdding: .day, value: 7, to: anchor) ?? anchor
        }

        return (0..<count).compactMap { calendar.date(byAdding: .day, value: $0 * 7, to: anchor) }
    }

    /// The series as UTC ISO start strings — the `sessions[]` request payload.
    var sessionISOs: [String] {
        sessions.map(Self.isoUTC)
    }

    // MARK: - Confirm

    func confirm() async {
        let isos = sessionISOs
        guard !isos.isEmpty else { return }
        state = .confirming
        let request = RecurringBookingRequest(
            eventTypeId: eventTypeId,
            sessions: isos,
            inviteeTimezone: TimeZone.current.identifier
        )
        do {
            let response: RecurringBookingsResponse = try await client.request(
                SchedulingEndpoints.createRecurringBookings(owner: owner, request)
            )
            state = .booked(count: response.bookings.count)
        } catch let error as SchedulingError {
            if let conflict = SlotConflictItem(error: error) {
                slotConflict = conflict
                conflictHint = true
                state = .configuring
            } else {
                state = .error(message: error.userMessage ?? "We couldn't book your series.")
            }
        } catch {
            state = .error(message: "We couldn't book your series.")
        }
    }

    /// Dismiss the recovery sheet — the owner adjusts the pattern and retries.
    func dismissConflict() {
        slotConflict = nil
    }

    // MARK: - Presentation helpers

    var durationMin: Int { eventType?.defaultDuration ?? eventType?.durations.first ?? 30 }

    var isPriced: Bool {
        SchedulingFeatureFlags.paidEnabled && (eventType?.priceCents ?? 0) > 0
    }

    var perSessionPriceLabel: String? {
        guard isPriced else { return nil }
        return EdgeFormat.money(cents: eventType?.priceCents, currency: eventType?.currency)
    }

    var totalPriceLabel: String? {
        guard isPriced, let price = eventType?.priceCents else { return nil }
        return EdgeFormat.money(cents: price * count, currency: eventType?.currency)
    }

    var weekdayName: String {
        let symbols = calendar.weekdaySymbols
        let index = min(max(weekdayIndex, 0), symbols.count - 1)
        return symbols[index]
    }

    var timeLabel: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US")
        formatter.timeZone = .current
        formatter.setLocalizedDateFormatFromTemplate("jmm")
        return formatter.string(from: timeOfDay)
    }

    /// `Jun 16 – Jul 21` — the span of the series.
    var rangeLabel: String? {
        guard let first = sessions.first else { return nil }
        let from = monthDay(first)
        guard let last = sessions.last, sessions.count > 1 else { return from }
        return "\(from) – \(monthDay(last))"
    }

    private func monthDay(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US")
        formatter.timeZone = .current
        formatter.setLocalizedDateFormatFromTemplate("MMMd")
        return formatter.string(from: date)
    }

    static func isoUTC(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.string(from: date)
    }

    private static func defaultTime() -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        return calendar.date(bySettingHour: 14, minute: 0, second: 0, of: Date()) ?? Date()
    }
}

#if DEBUG
extension RecurringSetupViewModel {
    static func previewConfiguring() -> RecurringSetupViewModel {
        let viewModel = RecurringSetupViewModel(owner: .personal, eventTypeId: "et1", push: { _ in }, client: .shared)
        let json = #"""
        {"eventType":{"id":"et1","name":"Intro call","slug":"intro","durations":[30],"default_duration":30,"price_cents":4000,"currency":"usd"}}
        """#
        if let data = json.data(using: .utf8), let response = try? JSONDecoder().decode(EventTypeDetailResponse.self, from: data) {
            viewModel.eventType = response.eventType
        }
        viewModel.state = .configuring
        viewModel.didLoad = true
        return viewModel
    }
}
#endif
