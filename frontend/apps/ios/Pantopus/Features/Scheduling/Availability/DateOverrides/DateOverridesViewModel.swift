//
//  DateOverridesViewModel.swift
//  Pantopus
//
//  Stream I3 — B6 Date Overrides & Holidays (sheet). Manages a schedule's
//  date-level overrides: full-day "unavailable" holidays and partial-day
//  custom-hours windows, plus a bulk US-public-holiday import. Reads the
//  composite (`GET /api/scheduling/availability`) and saves the WHOLE set
//  (`PUT /availability/:id/overrides`) on each add/remove. Personal only.
//

import Observation
import SwiftUI

/// One date override the user is managing.
struct OverrideEntry: Identifiable, Hashable {
    let date: String // YYYY-MM-DD
    var isUnavailable: Bool
    var start: TimeOfDay?
    var end: TimeOfDay?
    var id: String {
        date
    }
}

enum OverrideMode: String, CaseIterable, Identifiable {
    case unavailable
    case customHours
    var id: String {
        rawValue
    }

    var label: String {
        self == .unavailable ? "Unavailable" : "Custom hours"
    }
}

@Observable
@MainActor
final class DateOverridesViewModel {
    enum Phase {
        case loading
        case ready
        case error(message: String)
    }

    private(set) var phase: Phase = .loading
    let scheduleId: String

    private(set) var overrides: [OverrideEntry] = []

    // Composer state
    var selectedDate = Date()
    var mode: OverrideMode = .unavailable
    var customStart: TimeOfDay = .nineAM
    var customEnd: TimeOfDay = .fivePM
    var isRange = false
    var rangeEndDate = Date()

    private(set) var isSaving = false
    var errorMessage: String?

    private let client: SchedulingClient

    init(scheduleId: String, client: SchedulingClient = .shared) {
        self.scheduleId = scheduleId
        self.client = client
    }

    // MARK: Derived

    /// US holidays for the current calendar year.
    private var currentYearHolidays: [USHolidays.Holiday] {
        USHolidays.forYear(Calendar.current.component(.year, from: Date()))
    }

    var holidayCount: Int {
        currentYearHolidays.count
    }

    /// The current-year US holiday set, exposed for the read-only
    /// imported-holiday list shown when the holiday set is on. View-only —
    /// the source of truth for "is the set on" is `holidaysEnabled`.
    var currentYearHolidayList: [USHolidays.Holiday] {
        currentYearHolidays
    }

    /// True when every current-year holiday is already present as an
    /// unavailable override.
    var holidaysEnabled: Bool {
        let present = Set(overrides.filter(\.isUnavailable).map(\.date))
        return currentYearHolidays.allSatisfy { present.contains($0.date) }
    }

    var canAddCustom: Bool {
        guard !isRange, mode == .customHours else { return true }
        return TimeRange(start: customStart, end: customEnd).isValid
    }

    /// Upper bound for the date-range picker so a blocked range can't exceed
    /// the per-write cap (and silently drop the tail past `dates(from:to:)`'s
    /// guard). 60 days comfortably covers a long vacation hold.
    var maxRangeEnd: Date {
        Calendar.current.date(byAdding: .day, value: 60, to: selectedDate) ?? selectedDate
    }

    // MARK: Load

    func load() async {
        if case .ready = phase { return }
        await fetch()
    }

    func reload() async {
        await fetch()
    }

    private func fetch() async {
        phase = .loading
        do {
            let response: AvailabilityResponse = try await client.request(SchedulingEndpoints.getAvailability())
            overrides = Self.buildEntries(from: response.overrides.filter { $0.scheduleId == scheduleId })
            phase = .ready
        } catch let error as SchedulingError {
            phase = .error(message: error.userMessage ?? "Couldn't load your overrides.")
        } catch {
            phase = .error(message: "Couldn't load your overrides.")
        }
    }

    private static func buildEntries(from dtos: [AvailabilityOverrideDTO]) -> [OverrideEntry] {
        dtos
            .map { dto in
                OverrideEntry(
                    date: dto.date,
                    isUnavailable: dto.isUnavailable ?? false,
                    start: dto.startTime.flatMap(TimeOfDay.init),
                    end: dto.endTime.flatMap(TimeOfDay.init)
                )
            }
            .sorted { $0.date < $1.date }
    }

    // MARK: Mutations

    func addOverride() async {
        var list = overrides
        let newEntries = composedEntries()
        guard !newEntries.isEmpty else { return }
        let newDates = Set(newEntries.map(\.date))
        list.removeAll { newDates.contains($0.date) }
        list.append(contentsOf: newEntries)
        await persist(list)
    }

    func removeOverride(_ date: String) async {
        await persist(overrides.filter { $0.date != date })
    }

    func toggleHolidays(_ enable: Bool) async {
        let holidayDates = Set(currentYearHolidays.map(\.date))
        var list = overrides.filter { !holidayDates.contains($0.date) }
        if enable {
            list.append(contentsOf: currentYearHolidays.map {
                OverrideEntry(date: $0.date, isUnavailable: true, start: nil, end: nil)
            })
        }
        await persist(list)
    }

    private func composedEntries() -> [OverrideEntry] {
        if isRange {
            return Self.dates(from: selectedDate, to: rangeEndDate).map {
                OverrideEntry(date: $0, isUnavailable: true, start: nil, end: nil)
            }
        }
        let key = OverrideFormatting.ymdKey(selectedDate)
        if mode == .unavailable {
            return [OverrideEntry(date: key, isUnavailable: true, start: nil, end: nil)]
        }
        let range = TimeRange(start: customStart, end: customEnd)
        guard range.isValid else { return [] }
        return [OverrideEntry(date: key, isUnavailable: false, start: customStart, end: customEnd)]
    }

    private func persist(_ newList: [OverrideEntry]) async {
        guard !isSaving else { return }
        isSaving = true
        defer { isSaving = false }
        let sorted = newList.sorted { $0.date < $1.date }
        let payload = sorted.map { entry in
            OverridesRequest.Override(
                date: entry.date,
                isUnavailable: entry.isUnavailable,
                startTime: entry.isUnavailable ? nil : entry.start?.hhmm,
                endTime: entry.isUnavailable ? nil : entry.end?.hhmm
            )
        }
        do {
            let response: OverridesResponse = try await client.request(
                SchedulingEndpoints.setOverrides(scheduleId: scheduleId, OverridesRequest(overrides: payload))
            )
            overrides = Self.buildEntries(from: response.overrides)
        } catch let error as SchedulingError {
            // Keep the in-memory list and composer state intact on failure —
            // re-fetching here would flash the loading skeleton and discard
            // what the user just entered. They can simply retry.
            errorMessage = error.userMessage ?? "Couldn't save your overrides."
        } catch {
            errorMessage = "Couldn't save your overrides."
        }
    }

    private static func dates(from start: Date, to end: Date, calendar: Calendar = .current) -> [String] {
        let lower = calendar.startOfDay(for: min(start, end))
        let upper = calendar.startOfDay(for: max(start, end))
        var keys: [String] = []
        var cursor = lower
        var guardCount = 0
        while cursor <= upper, guardCount < 90 {
            keys.append(OverrideFormatting.ymdKey(cursor, calendar: calendar))
            guard let next = calendar.date(byAdding: .day, value: 1, to: cursor) else { break }
            cursor = next
            guardCount += 1
        }
        return keys
    }
}
