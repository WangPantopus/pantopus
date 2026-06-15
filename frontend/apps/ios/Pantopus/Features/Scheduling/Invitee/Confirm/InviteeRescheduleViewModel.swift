//
//  InviteeRescheduleViewModel.swift
//  Pantopus
//
//  Stream I6 — the invitee reschedule sub-flow presented locally from D4 Manage.
//  Drives the Foundation `SlotPicker` against `GET /booking/:token/available-
//  slots` (which excludes the current booking) and commits via
//  `POST /booking/:token/reschedule`. A 409 surfaces the Foundation
//  `SlotTakenSheet`. The picker is presentational; this view-model owns all state.
//

import SwiftUI

@Observable
@MainActor
final class InviteeRescheduleViewModel {
    enum Phase: Equatable {
        case loading
        case ready
        case error(message: String)
    }

    let token: String
    let accent: Color
    private let client: SchedulingClient
    private let currentStart: String?

    private(set) var phase: Phase = .loading
    private(set) var monthSlots: [SlotDTO] = []
    private(set) var monthAnchor: Date
    private(set) var selectedDate: Date
    var selectedSlotStart: String?
    private(set) var timezoneId: String
    private(set) var submitting = false

    // 409 recovery
    var showSlotTakenSheet = false
    private(set) var slotTakenAlternatives: [SchedulingSlotAlternative] = []
    private(set) var inlineBanner: String?

    private var didLoad = false

    init(token: String, tz: String, accent: Color, currentStart: String?, client: SchedulingClient) {
        self.token = token
        timezoneId = tz
        self.accent = accent
        self.currentStart = currentStart
        self.client = client
        let cal = DiscoveryCalendar.calendar(tz: tz)
        let now = Date()
        monthAnchor = cal.startOfDay(for: now)
        selectedDate = cal.startOfDay(for: now)
    }

    // MARK: - Derived

    var timezoneLabel: String { DiscoveryTimeZone.label(for: timezoneId) }
    var availableDays: Set<Date> { DiscoveryCalendar.availableDays(monthSlots, tz: timezoneId) }
    var daySlots: [SlotDTO] { DiscoveryCalendar.slots(monthSlots, on: selectedDate, tz: timezoneId) }
    var dstHint: String? { DiscoveryCalendar.dstHint(monthAnchor: monthAnchor, tz: timezoneId) }

    var slotPickerState: SlotPicker.LoadState {
        if phase == .loading { return .loading }
        if monthSlots.isEmpty { return .noAvailability }
        if daySlots.isEmpty { return .dayFull }
        return .loaded
    }

    var canSubmit: Bool { selectedSlotStart != nil && !submitting }

    // MARK: - Loading

    func load() async {
        guard !didLoad else { return }
        didLoad = true
        await fetchMonth()
    }

    func refresh() async { await fetchMonth() }

    private func fetchMonth() async {
        phase = .loading
        let range = DiscoveryCalendar.monthRange(monthAnchor: monthAnchor, tz: timezoneId)
        do {
            let response: SlotsResponse = try await client.request(
                SchedulingPublicEndpoints.manageAvailableSlots(token: token, from: range.from, to: range.to, tz: timezoneId)
            )
            monthSlots = response.slots
            // Default the selected day to the first day that has slots.
            if daySlots.isEmpty, let firstDay = availableDays.sorted().first {
                selectedDate = firstDay
            }
            phase = .ready
        } catch let error as SchedulingError {
            phase = .error(message: error.userMessage ?? "We couldn't load times. Try again.")
        } catch {
            phase = .error(message: "We couldn't load times. Try again.")
        }
    }

    // MARK: - Picker callbacks

    func selectDate(_ date: Date) {
        selectedDate = date
        selectedSlotStart = nil
    }

    func selectSlot(_ slot: SlotDTO) { selectedSlotStart = slot.start }

    func changeMonth(_ delta: Int) async {
        let cal = DiscoveryCalendar.calendar(tz: timezoneId)
        guard let newAnchor = cal.date(byAdding: .month, value: delta, to: monthAnchor) else { return }
        monthAnchor = newAnchor
        selectedSlotStart = nil
        await fetchMonth()
    }

    func changeTimezone(_ identifier: String) async {
        timezoneId = identifier
        selectedSlotStart = nil
        await fetchMonth()
    }

    func jumpNextAvailable() async {
        if let firstDay = availableDays.sorted().first {
            selectedDate = firstDay
        } else {
            await changeMonth(1)
        }
    }

    // MARK: - Commit

    /// Commit the reschedule. Returns true on success (caller dismisses + refreshes).
    func reschedule() async -> Bool {
        guard let start = selectedSlotStart else { return false }
        submitting = true
        inlineBanner = nil
        defer { submitting = false }
        do {
            let _: PublicBookingResponse = try await client.request(
                SchedulingPublicEndpoints.reschedule(token: token, PublicRescheduleRequest(startAt: start))
            )
            return true
        } catch let error as SchedulingError {
            if case let .slotConflict(_, _, alternatives) = error {
                slotTakenAlternatives = alternatives
                showSlotTakenSheet = true
            } else {
                inlineBanner = error.userMessage ?? "We couldn't reschedule. Try another time."
            }
            return false
        } catch {
            inlineBanner = "We couldn't reschedule. Try another time."
            return false
        }
    }

    func selectAlternative(_ alternative: SchedulingSlotAlternative) {
        selectedSlotStart = alternative.start
        showSlotTakenSheet = false
    }

    var slotTakenLabel: String? {
        selectedSlotStart.map { ConfirmFormat.dayAndTime(startUTC: $0, endUTC: nil, tz: timezoneId) }
    }
}
