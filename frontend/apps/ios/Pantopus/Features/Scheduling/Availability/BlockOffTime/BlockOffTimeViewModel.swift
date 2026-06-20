//
//  BlockOffTimeViewModel.swift
//  Pantopus
//
//  Stream I3 — B9 Block Off Time / Personal busy override (sheet). The host's
//  quick "+ Busy time" action — drops an ad-hoc busy hold onto personal
//  availability so the engine stops offering that window. Creates a block via
//  `POST /api/scheduling/availability/blocks` (absolute ISO start/end +
//  optional RRULE). It does NOT create a bookable event. Personal only.
//

import Observation
import SwiftUI

/// Recurrence options for a busy hold.
enum BlockRepeat: String, CaseIterable, Identifiable {
    case never
    case daily
    case weekly
    case monthly

    var id: String {
        rawValue
    }

    var label: String {
        switch self {
        case .never: "Does not repeat"
        case .daily: "Daily"
        case .weekly: "Weekly"
        case .monthly: "Monthly"
        }
    }

    /// The RRULE the backend stores (nil = one-off).
    var rrule: String? {
        switch self {
        case .never: nil
        case .daily: "FREQ=DAILY"
        case .weekly: "FREQ=WEEKLY"
        case .monthly: "FREQ=MONTHLY"
        }
    }

    /// Sub-caption shown under the repeat control once a recurring rule is
    /// chosen. Mirrors the design's "Repeats … indefinitely" helper line.
    /// `nil` for a one-off (no caption).
    var caption: String? {
        switch self {
        case .never: nil
        case .daily: "Repeats every day · Ends never. Tap to add an end date."
        case .weekly: "Repeats every week · Ends never. Tap to add an end date."
        case .monthly: "Repeats every month · Ends never. Tap to add an end date."
        }
    }
}

/// View-only presentation of a booking-overlap warning. The actual overlap
/// detection (checking the chosen window against confirmed bookings) is a
/// backend concern — see `deferredBackend`. This struct lets the view render
/// the design's conflict frame once that signal exists.
struct BlockConflictWarning: Equatable {
    /// Human label for the conflicting booking, e.g. "confirmed 2:30 PM booking".
    let bookingLabel: String
}

@Observable
@MainActor
final class BlockOffTimeViewModel {
    // Editable fields
    var reason = ""
    var date = Date()
    var allDay = false
    var startTime = TimeOfDay(hour: 14, minute: 0)
    var endTime = TimeOfDay(hour: 15, minute: 0)
    var repeats: BlockRepeat = .never

    /// Set when the chosen window overlaps a confirmed booking. Drives the
    /// design's conflict-warning card. Detection is backend-driven and not
    /// yet wired (see deferredBackend), so this stays `nil` for now.
    var conflict: BlockConflictWarning?

    private(set) var isSaving = false
    var saveError: String?

    private let client: SchedulingClient

    init(client: SchedulingClient = .shared) {
        self.client = client
    }

    /// Timed blocks need a positive window; all-day blocks are always valid.
    var isValid: Bool {
        allDay || TimeRange(start: startTime, end: endTime).isValid
    }

    /// Tapped from the conflict-warning card's "View booking" link. Routing to
    /// the overlapping booking's detail screen requires the booking id and a
    /// navigation hook that aren't wired yet (see deferredBackend); this is the
    /// view-only seam so the design's affordance renders today.
    func viewConflictingBooking() {
        // No-op until the conflict signal carries a booking id + route.
    }

    /// Returns true on success so the View can dismiss.
    func save() async -> Bool {
        guard isValid, !isSaving else { return false }
        isSaving = true
        defer { isSaving = false }
        let trimmed = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        let request = CreateBlockRequest(
            startAt: Self.iso(startInstant()),
            endAt: Self.iso(endInstant()),
            title: trimmed.isEmpty ? nil : trimmed,
            recurrenceRule: repeats.rrule
        )
        do {
            _ = try await client.request(SchedulingEndpoints.createBlock(request), as: AvailabilityBlockResponse.self)
            return true
        } catch let error as SchedulingError {
            saveError = error.userMessage ?? "Couldn't block off this time."
            return false
        } catch {
            saveError = "Couldn't block off this time."
            return false
        }
    }

    // MARK: Instant building

    private func startInstant(calendar: Calendar = .current) -> Date {
        if allDay { return calendar.startOfDay(for: date) }
        return combine(date, startTime, calendar: calendar)
    }

    private func endInstant(calendar: Calendar = .current) -> Date {
        if allDay {
            return calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: date))
                ?? calendar.startOfDay(for: date)
        }
        return combine(date, endTime, calendar: calendar)
    }

    private func combine(_ day: Date, _ time: TimeOfDay, calendar: Calendar) -> Date {
        calendar.date(
            bySettingHour: time.hour,
            minute: time.minute,
            second: 0,
            of: calendar.startOfDay(for: day)
        ) ?? day
    }

    private static func iso(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.string(from: date)
    }
}
