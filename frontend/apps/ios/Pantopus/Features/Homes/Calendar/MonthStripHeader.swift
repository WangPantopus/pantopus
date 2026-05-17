//
//  MonthStripHeader.swift
//  Pantopus
//
//  T6.4c — Small "month label + 7-day week strip" calendar component
//  rendered between the top bar and the agenda list on the Home calendar.
//  Lifted from the design at `calendar-frames.jsx:118-195`.
//
//  Per the design contract, this component lives in the feature folder
//  (NOT the shared shell) — it's specific to the calendar surface, and
//  the shell's `customHeader` slot (added in T6.4c) hosts it.
//
//  Geometry:
//    - Container: surface bg + 1px bottom border, padding 10/16/12.
//    - Row 1: month label + prev/next chevrons (26pt rounded buttons).
//    - Row 2: 7 day columns in a HStack, each rendering DOW abbrev +
//      date number + up to 3 event dots below.
//    - Today / selected day: home-green pill background; 4pt white dots.
//    - Other days: transparent background; 4pt home-green dots.
//

import SwiftUI

/// State payload the host VM hands the strip. One row per day in the
/// current 7-day window plus the month label and the host's
/// `selectedIsoDate` (the date the user has tapped to filter the
/// agenda — `nil` when nothing is selected and the strip should
/// highlight today only).
public struct MonthStripState: Sendable, Equatable {
    /// "October 2025" — already-formatted month + year.
    public let monthLabel: String
    /// 7 entries — one per day in the visible week.
    public let days: [Day]
    /// ISO yyyy-MM-dd of the currently-selected day. `nil` means
    /// "no filter applied".
    public let selectedIsoDate: String?
    /// ISO yyyy-MM-dd of today (anchored to the host's `now`). The
    /// strip pills the matching day even when nothing's selected.
    public let todayIsoDate: String

    public init(
        monthLabel: String,
        days: [Day],
        selectedIsoDate: String?,
        todayIsoDate: String
    ) {
        self.monthLabel = monthLabel
        self.days = days
        self.selectedIsoDate = selectedIsoDate
        self.todayIsoDate = todayIsoDate
    }

    /// One day in the week strip.
    public struct Day: Sendable, Equatable, Identifiable {
        /// ISO yyyy-MM-dd. Used as `id` + comparison key.
        public let id: String
        /// Abbreviated day of week — "Sun" / "Mon" / …
        public let dayOfWeek: String
        /// 1-based date number rendered below the DOW abbreviation.
        public let date: Int
        /// Number of events scheduled on this day. The strip renders
        /// `min(count, 3)` small dots below the date.
        public let eventCount: Int

        public init(id: String, dayOfWeek: String, date: Int, eventCount: Int) {
            self.id = id
            self.dayOfWeek = dayOfWeek
            self.date = date
            self.eventCount = eventCount
        }
    }
}

/// The reusable strip. Pure on its inputs — the caller owns navigation
/// (prev / next month) and selection state.
public struct MonthStripHeader: View {
    let state: MonthStripState
    let onSelectDay: @MainActor (String) -> Void
    let onPrevMonth: @MainActor () -> Void
    let onNextMonth: @MainActor () -> Void

    public init(
        state: MonthStripState,
        onSelectDay: @escaping @MainActor (String) -> Void,
        onPrevMonth: @escaping @MainActor () -> Void,
        onNextMonth: @escaping @MainActor () -> Void
    ) {
        self.state = state
        self.onSelectDay = onSelectDay
        self.onPrevMonth = onPrevMonth
        self.onNextMonth = onNextMonth
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            monthRow
            weekRow
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.top, 10)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(Theme.Color.appBorder)
                .frame(height: 1)
        }
        .accessibilityIdentifier("homeCalendar_monthStrip")
    }

    private var monthRow: some View {
        HStack(spacing: Spacing.s1) {
            HStack(spacing: 4) {
                Text(state.monthLabel)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                Icon(.chevronDown, size: 12, color: Theme.Color.appTextSecondary)
            }
            .accessibilityIdentifier("homeCalendar_monthLabel")
            Spacer()
            chevronButton(.chevronLeft, label: "Previous month", handler: onPrevMonth)
                .accessibilityIdentifier("homeCalendar_prevMonth")
            chevronButton(.chevronRight, label: "Next month", handler: onNextMonth)
                .accessibilityIdentifier("homeCalendar_nextMonth")
        }
    }

    private func chevronButton(
        _ icon: PantopusIcon,
        label: String,
        handler: @escaping @MainActor () -> Void
    ) -> some View {
        Button {
            handler()
        } label: {
            Icon(icon, size: 14, color: Theme.Color.appTextStrong)
                .frame(width: 26, height: 26)
                .background(Theme.Color.appSurfaceSunken)
                .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private var weekRow: some View {
        HStack(spacing: 4) {
            ForEach(state.days) { day in
                dayCell(day)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private func dayCell(_ day: MonthStripState.Day) -> some View {
        let isHighlighted = isHighlighted(day)
        return Button {
            onSelectDay(day.id)
        } label: {
            VStack(spacing: 3) {
                Text(day.dayOfWeek.uppercased())
                    .font(.system(size: 10, weight: .semibold))
                    .tracking(0.4)
                    .foregroundStyle(
                        isHighlighted
                            ? Color.white.opacity(0.85)
                            : Theme.Color.appTextMuted
                    )
                Text("\(day.date)")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(
                        isHighlighted
                            ? Theme.Color.appTextInverse
                            : Theme.Color.appText
                    )
                dotsRow(eventCount: day.eventCount, isHighlighted: isHighlighted)
            }
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity)
            .background(
                isHighlighted ? Theme.Color.home : Color.clear
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("homeCalendar_day_\(day.id)")
        .accessibilityLabel(
            "\(day.dayOfWeek) \(day.date) · \(day.eventCount) events"
        )
        .accessibilityAddTraits(isHighlighted ? [.isButton, .isSelected] : .isButton)
    }

    private func dotsRow(eventCount: Int, isHighlighted: Bool) -> some View {
        HStack(spacing: 2) {
            ForEach(0..<min(eventCount, 3), id: \.self) { _ in
                Circle()
                    .fill(
                        isHighlighted
                            ? Color.white.opacity(0.9)
                            : Theme.Color.home
                    )
                    .frame(width: 4, height: 4)
            }
        }
        .frame(height: 4)
    }

    /// A day is highlighted (home-green pill) when either it is the
    /// user-selected day, or — if there is no selection — when it is
    /// today. Mirrors the design's "today is a pill" default plus the
    /// "tapping a day pins the pill" interaction.
    private func isHighlighted(_ day: MonthStripState.Day) -> Bool {
        if let selected = state.selectedIsoDate {
            return day.id == selected
        }
        return day.id == state.todayIsoDate
    }
}

#Preview {
    let today = "2025-10-12"
    let week: [MonthStripState.Day] = [
        .init(id: "2025-10-12", dayOfWeek: "Sun", date: 12, eventCount: 3),
        .init(id: "2025-10-13", dayOfWeek: "Mon", date: 13, eventCount: 1),
        .init(id: "2025-10-14", dayOfWeek: "Tue", date: 14, eventCount: 2),
        .init(id: "2025-10-15", dayOfWeek: "Wed", date: 15, eventCount: 1),
        .init(id: "2025-10-16", dayOfWeek: "Thu", date: 16, eventCount: 0),
        .init(id: "2025-10-17", dayOfWeek: "Fri", date: 17, eventCount: 1),
        .init(id: "2025-10-18", dayOfWeek: "Sat", date: 18, eventCount: 2)
    ]
    MonthStripHeader(
        state: MonthStripState(
            monthLabel: "October 2025",
            days: week,
            selectedIsoDate: nil,
            todayIsoDate: today
        ),
        onSelectDay: { _ in },
        onPrevMonth: {},
        onNextMonth: {}
    )
    .frame(width: 360)
    .background(Theme.Color.appBg)
}
