//
//  DiscoverySupport.swift
//  Pantopus
//
//  Stream I5 (Invitee Discovery) — small shared helpers for the public booking
//  surface: host-pillar accent from the page `owner_type` string, friendly
//  IANA timezone labels for the SlotPicker chip, location-mode presentation,
//  and the tz-aware day grouping that drives the Foundation `SlotPicker`.
//
//  Public invitee screens have NO `SchedulingOwner` (they are unauthenticated),
//  so accent is derived from the page's `owner_type` wire string by mapping it
//  onto the Foundation `SchedulingIdentityTheme` — never a hardcoded hue.
//

import SwiftUI

/// Maps the public booking page's `owner_type` string to the host's pillar accent.
enum DiscoveryTheme {
    /// `"home"` → green, `"business"` → violet, everything else (`"user"`/nil) → personal sky.
    static func accent(forOwnerType ownerType: String?) -> Color {
        owner(forOwnerType: ownerType).theme.accent
    }

    /// The lightest pillar tint for the host accent.
    static func accentBg(forOwnerType ownerType: String?) -> Color {
        owner(forOwnerType: ownerType).theme.accentBg
    }

    private static func owner(forOwnerType ownerType: String?) -> SchedulingOwner {
        switch (ownerType ?? "").lowercased() {
        case "home": .home(homeId: "")
        case "business": .business(id: "")
        default: .personal
        }
    }
}

/// Friendly labels for an IANA timezone identifier (the SlotPicker chip text).
enum DiscoveryTimeZone {
    /// e.g. `America/Los_Angeles` → "Pacific Time"; falls back to a prettified city.
    static func label(for identifier: String) -> String {
        guard let zone = TimeZone(identifier: identifier) else {
            return prettyCity(from: identifier)
        }
        if let name = zone.localizedName(for: .generic, locale: .current), !name.isEmpty {
            return name
        }
        return prettyCity(from: identifier)
    }

    /// The current GMT offset for the zone, e.g. "GMT-7".
    static func gmtOffset(for identifier: String, at date: Date = Date()) -> String {
        guard let zone = TimeZone(identifier: identifier) else { return "" }
        let seconds = zone.secondsFromGMT(for: date)
        if seconds == 0 { return "GMT" }
        let hours = seconds / 3600
        let minutes = abs(seconds / 60) % 60
        let sign = seconds < 0 ? "-" : "+"
        return minutes == 0
            ? "GMT\(sign)\(abs(hours))"
            : String(format: "GMT%@%d:%02d", sign, abs(hours), minutes)
    }

    private static func prettyCity(from identifier: String) -> String {
        identifier.split(separator: "/").last.map {
            $0.replacingOccurrences(of: "_", with: " ")
        } ?? identifier
    }
}

/// Presentation for the event-type `location_mode` wire string.
enum DiscoveryLocation {
    static func label(mode: String?, detail: String?) -> String? {
        switch (mode ?? "").lowercased() {
        case "video", "google_meet", "zoom", "meet", "teams": "Video call"
        case "in_person", "in-person", "inperson": "In person"
        case "phone", "phone_call": "Phone"
        case "custom", "other": detail?.isEmpty == false ? detail : "Custom"
        case "": nil
        default: detail?.isEmpty == false ? detail : "Custom"
        }
    }

    static func icon(mode: String?) -> PantopusIcon {
        switch (mode ?? "").lowercased() {
        case "video", "google_meet", "zoom", "meet", "teams": .video
        case "in_person", "in-person", "inperson": .mapPin
        case "phone", "phone_call": .phone
        default: .calendar
        }
    }
}

/// tz-aware day math shared by the slot-picker and no-availability view-models.
/// Mirrors the `Calendar(identifier:.gregorian)` + zone the Foundation `SlotPicker`
/// uses internally, so `availableDays` and the selected-day filter line up exactly.
enum DiscoveryCalendar {
    static func calendar(tz: String) -> Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: tz) ?? .current
        return cal
    }

    /// The set of start-of-day dates (in `tz`) that have at least one slot.
    static func availableDays(_ slots: [SlotDTO], tz: String) -> Set<Date> {
        let cal = calendar(tz: tz)
        return Set(slots.compactMap { slot in
            SchedulingTime.parseUTC(slot.start).map { cal.startOfDay(for: $0) }
        })
    }

    /// Slots that fall on `day` (compared at start-of-day in `tz`), time-sorted.
    static func slots(_ slots: [SlotDTO], on day: Date, tz: String) -> [SlotDTO] {
        let cal = calendar(tz: tz)
        let target = cal.startOfDay(for: day)
        return slots
            .filter { slot in
                guard let date = SchedulingTime.parseUTC(slot.start) else { return false }
                return cal.startOfDay(for: date) == target
            }
            .sorted { $0.start < $1.start }
    }

    /// `from`/`to` ISO day strings (in `tz`) covering the visible month, clamped to today.
    static func monthRange(monthAnchor: Date, tz: String) -> (from: String, to: String) {
        let cal = calendar(tz: tz)
        let interval = cal.dateInterval(of: .month, for: monthAnchor)
        let start = interval?.start ?? monthAnchor
        let end = interval?.end ?? cal.date(byAdding: .month, value: 1, to: monthAnchor) ?? monthAnchor
        let today = cal.startOfDay(for: Date())
        let from = max(start, today)
        // Defense-in-depth: never emit an inverted (from > to) range.
        let to = max(end, from)
        return (isoDay(from, tz: tz), isoDay(to, tz: tz))
    }

    /// `yyyy-MM-dd` rendered in `tz` (the booking-page timezone), avoiding UTC skew.
    static func isoDay(_ date: Date, tz: String) -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .iso8601)
        formatter.timeZone = TimeZone(identifier: tz) ?? .current
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }

    /// A DST-transition hint for the visible month, or nil when none applies.
    static func dstHint(monthAnchor: Date, tz: String) -> String? {
        guard let zone = TimeZone(identifier: tz) else { return nil }
        let cal = calendar(tz: tz)
        guard let interval = cal.dateInterval(of: .month, for: monthAnchor) else { return nil }
        guard let next = zone.nextDaylightSavingTimeTransition(after: interval.start) else { return nil }
        guard next < interval.end else { return nil }
        return "Clocks change this month — times are adjusted."
    }
}
