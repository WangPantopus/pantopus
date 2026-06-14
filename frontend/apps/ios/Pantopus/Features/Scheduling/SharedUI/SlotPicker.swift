//
//  SlotPicker.swift
//  Pantopus
//
//  Foundation (I0b) — the tz-aware date + time slot grid. A month calendar
//  strip and the selected day's slots stack in one scroll (not a wizard split),
//  mirroring the Support Train grid + Home calendar month strip. Driven by
//  `[SlotDTO]`; the host pillar accent paints today / selected / slot-select.
//  Loading shimmers, day-full, and no-availability are first-class calm states.
//  Presentational only — the parent loads slots and owns the state.
//

import SwiftUI

/// The booker's date + time picker. Stateless: the parent supplies the month,
/// selected day, the day's `slots`, the load `state`, and reacts to callbacks.
public struct SlotPicker: View {
    /// What the slot column renders. The calendar stays visible across all.
    public enum LoadState: Equatable, Sendable {
        /// Slots for the selected day are loading — shimmer rows.
        case loading
        /// Slots are loaded (use `slots`).
        case loaded
        /// The selected day has no open times.
        case dayFull
        /// Nothing is open in the visible month/range.
        case noAvailability
    }

    private let state: LoadState
    private let slots: [SlotDTO]
    private let timeZoneIdentifier: String
    private let timeZoneLabel: String
    private let accent: Color
    private let monthAnchor: Date
    private let selectedDate: Date
    private let availableDays: Set<Date>?
    private let selectedSlotStart: String?
    private let dstHint: String?
    private let onSelectDate: (Date) -> Void
    private let onSelectSlot: (SlotDTO) -> Void
    private let onChangeMonth: (Int) -> Void
    private let onTapTimeZone: () -> Void
    private let onJumpNextAvailable: (() -> Void)?
    private let onNotifyMe: (() -> Void)?

    public init(
        state: LoadState,
        slots: [SlotDTO],
        timeZoneIdentifier: String,
        timeZoneLabel: String,
        accent: Color,
        monthAnchor: Date,
        selectedDate: Date,
        availableDays: Set<Date>? = nil,
        selectedSlotStart: String? = nil,
        dstHint: String? = nil,
        onSelectDate: @escaping (Date) -> Void,
        onSelectSlot: @escaping (SlotDTO) -> Void,
        onChangeMonth: @escaping (Int) -> Void,
        onTapTimeZone: @escaping () -> Void,
        onJumpNextAvailable: (() -> Void)? = nil,
        onNotifyMe: (() -> Void)? = nil
    ) {
        self.state = state
        self.slots = slots
        self.timeZoneIdentifier = timeZoneIdentifier
        self.timeZoneLabel = timeZoneLabel
        self.accent = accent
        self.monthAnchor = monthAnchor
        self.selectedDate = selectedDate
        self.availableDays = availableDays
        self.selectedSlotStart = selectedSlotStart
        self.dstHint = dstHint
        self.onSelectDate = onSelectDate
        self.onSelectSlot = onSelectSlot
        self.onChangeMonth = onChangeMonth
        self.onTapTimeZone = onTapTimeZone
        self.onJumpNextAvailable = onJumpNextAvailable
        self.onNotifyMe = onNotifyMe
    }

    private var calendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: timeZoneIdentifier) ?? .current
        return cal
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            timeZoneChip
            if let dstHint {
                infoCaption(dstHint)
            }
            SlotPickerCalendar(
                monthAnchor: monthAnchor,
                selectedDate: selectedDate,
                availableDays: availableDays,
                accent: accent,
                calendar: calendar,
                onSelectDate: onSelectDate,
                onChangeMonth: onChangeMonth,
                onJumpNextAvailable: onJumpNextAvailable
            )
            Divider().background(Theme.Color.appBorderSubtle)
            slotColumn
        }
    }

    // MARK: - Timezone chip

    private var timeZoneChip: some View {
        Button(action: onTapTimeZone) {
            HStack(spacing: Spacing.s2) {
                Icon(.globe, size: 16, color: Theme.Color.primary600)
                Text("Times shown in \(timeZoneLabel)")
                    .pantopusTextStyle(.small)
                    .foregroundStyle(Theme.Color.appText)
                Spacer(minLength: Spacing.s1)
                Icon(.chevronRight, size: 14, color: Theme.Color.appTextMuted)
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s2)
            .background(Theme.Color.primary50)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Time zone, \(timeZoneLabel). Tap to change.")
        .accessibilityIdentifier("scheduling.slotPicker.timezone")
    }

    private func infoCaption(_ text: String) -> some View {
        HStack(spacing: Spacing.s2) {
            Icon(.info, size: 14, color: Theme.Color.info)
            Text(text)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .padding(Spacing.s2)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.infoBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
    }

    // MARK: - Slot column

    @ViewBuilder
    private var slotColumn: some View {
        switch state {
        case .loading:
            VStack(spacing: Spacing.s2) {
                ForEach(0..<6, id: \.self) { _ in SchedulingSlotRowSkeleton() }
            }
            .accessibilityLabel("Loading times")
        case .loaded:
            loadedSlots
        case .dayFull:
            dayFullCard
        case .noAvailability:
            noAvailabilityCard
        }
    }

    private var loadedSlots: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            ForEach(SlotGroup.allCases, id: \.self) { group in
                let groupSlots = slots.filter { slotGroup(for: $0) == group }
                if !groupSlots.isEmpty {
                    VStack(alignment: .leading, spacing: Spacing.s2) {
                        Text(group.title)
                            .pantopusTextStyle(.overline)
                            .foregroundStyle(Theme.Color.appTextMuted)
                        ForEach(groupSlots, id: \.self) { slot in
                            SchedulingSlotRow(
                                time: timeLabel(for: slot),
                                detail: durationLabel(for: slot),
                                accent: accent,
                                isSelected: slot.start == selectedSlotStart
                            ) {
                                onSelectSlot(slot)
                            }
                        }
                    }
                }
            }
        }
    }

    private var dayFullCard: some View {
        VStack(spacing: Spacing.s2) {
            Icon(.calendar, size: 24, color: Theme.Color.appTextMuted)
            Text("No times left this day")
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
            if let onJumpNextAvailable {
                Button("See next available", action: onJumpNextAvailable)
                    .font(Theme.Font.small)
                    .foregroundStyle(accent)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.s8)
    }

    private var noAvailabilityCard: some View {
        VStack(spacing: Spacing.s3) {
            ZStack {
                Circle().fill(Theme.Color.appSurfaceSunken).frame(width: 56, height: 56)
                Icon(.calendar, size: 26, color: Theme.Color.appTextMuted)
            }
            Text("No open times in \(monthName)")
                .pantopusTextStyle(.h3)
                .foregroundStyle(Theme.Color.appText)
                .multilineTextAlignment(.center)
            Text("Availability changes often. Try a later month.")
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            VStack(spacing: Spacing.s2) {
                if let onJumpNextAvailable {
                    Button("Jump to next available", action: onJumpNextAvailable)
                        .font(Theme.Font.small)
                        .fontWeight(.semibold)
                        .foregroundStyle(accent)
                }
                if let onNotifyMe {
                    Button("Get notified when times open", action: onNotifyMe)
                        .font(Theme.Font.small)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            .padding(.top, Spacing.s1)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.s8)
        .padding(.horizontal, Spacing.s4)
    }

    // MARK: - Formatting

    private var monthName: String {
        let fmt = DateFormatter()
        fmt.calendar = calendar
        fmt.timeZone = calendar.timeZone
        fmt.dateFormat = "LLLL"
        return fmt.string(from: monthAnchor)
    }

    private func timeLabel(for slot: SlotDTO) -> String {
        SchedulingTime.localString(
            utcISO: slot.start, tz: timeZoneIdentifier, dateStyle: .none, timeStyle: .short
        ) ?? slot.startLocal ?? slot.start
    }

    private func durationLabel(for slot: SlotDTO) -> String? {
        guard let start = SchedulingTime.parseUTC(slot.start),
              let end = SchedulingTime.parseUTC(slot.end) else { return nil }
        let minutes = Int(end.timeIntervalSince(start) / 60)
        guard minutes > 0 else { return nil }
        return "\(minutes) min"
    }

    private func slotGroup(for slot: SlotDTO) -> SlotGroup {
        guard let date = SchedulingTime.parseUTC(slot.start) else { return .afternoon }
        let hour = calendar.component(.hour, from: date)
        switch hour {
        case ..<12: return .morning
        case 12..<17: return .afternoon
        default: return .evening
        }
    }

    private enum SlotGroup: CaseIterable, Hashable {
        case morning, afternoon, evening
        var title: String {
            switch self {
            case .morning: "Morning"
            case .afternoon: "Afternoon"
            case .evening: "Evening"
            }
        }
    }
}

// MARK: - Month calendar strip

private struct SlotPickerCalendar: View {
    let monthAnchor: Date
    let selectedDate: Date
    let availableDays: Set<Date>?
    let accent: Color
    let calendar: Calendar
    let onSelectDate: (Date) -> Void
    let onChangeMonth: (Int) -> Void
    let onJumpNextAvailable: (() -> Void)?

    private let columns = Array(repeating: GridItem(.flexible(), spacing: Spacing.s1), count: 7)

    var body: some View {
        VStack(spacing: Spacing.s3) {
            header
            weekdayHeader
            LazyVGrid(columns: columns, spacing: Spacing.s1) {
                ForEach(Array(monthDays.enumerated()), id: \.offset) { _, day in
                    if let day {
                        dayCell(day)
                    } else {
                        Color.clear.frame(height: 40)
                    }
                }
            }
        }
    }

    private var header: some View {
        HStack {
            Text(monthTitle)
                .pantopusTextStyle(.body)
                .fontWeight(.semibold)
                .foregroundStyle(Theme.Color.appText)
            Spacer()
            if let onJumpNextAvailable {
                Button("Next available", action: onJumpNextAvailable)
                    .font(Theme.Font.caption)
                    .foregroundStyle(accent)
            }
            Button { onChangeMonth(-1) } label: {
                Icon(.chevronLeft, size: 18, color: Theme.Color.appTextSecondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Previous month")
            Button { onChangeMonth(1) } label: {
                Icon(.chevronRight, size: 18, color: Theme.Color.appTextSecondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Next month")
        }
    }

    private var weekdayHeader: some View {
        HStack(spacing: Spacing.s1) {
            ForEach(weekdaySymbols, id: \.self) { symbol in
                Text(symbol)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextMuted)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    @ViewBuilder
    private func dayCell(_ day: Date) -> some View {
        let isSelected = calendar.isDate(day, inSameDayAs: selectedDate)
        let isToday = calendar.isDateInToday(day)
        let enabled = isDayEnabled(day)
        Button {
            if enabled { onSelectDate(day) }
        } label: {
            Text("\(calendar.component(.day, from: day))")
                .pantopusTextStyle(.small)
                .fontWeight(isSelected ? .semibold : .regular)
                .foregroundStyle(dayForeground(isSelected: isSelected, enabled: enabled))
                .frame(maxWidth: .infinity)
                .frame(height: 40)
                .background(
                    Circle()
                        .fill(isSelected ? accent : Color.clear)
                        .frame(width: 36, height: 36)
                )
                .overlay(
                    Circle()
                        .stroke(isToday && !isSelected ? accent : Color.clear, lineWidth: 1.5)
                        .frame(width: 36, height: 36)
                )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(accessibilityLabel(for: day, enabled: enabled, isToday: isToday))
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }

    private func dayForeground(isSelected: Bool, enabled: Bool) -> Color {
        if isSelected { return Theme.Color.appTextInverse }
        return enabled ? Theme.Color.appText : Theme.Color.appTextMuted
    }

    private func isDayEnabled(_ day: Date) -> Bool {
        let startOfDay = calendar.startOfDay(for: day)
        let today = calendar.startOfDay(for: Date())
        guard startOfDay >= today else { return false }
        guard let availableDays else { return true }
        return availableDays.contains(startOfDay)
    }

    private var monthTitle: String {
        let fmt = DateFormatter()
        fmt.calendar = calendar
        fmt.timeZone = calendar.timeZone
        fmt.dateFormat = "LLLL yyyy"
        return fmt.string(from: monthAnchor)
    }

    private var weekdaySymbols: [String] {
        let fmt = DateFormatter()
        fmt.calendar = calendar
        let symbols = fmt.veryShortStandaloneWeekdaySymbols ?? ["S", "M", "T", "W", "T", "F", "S"]
        let first = calendar.firstWeekday - 1
        return Array(symbols[first...] + symbols[..<first])
    }

    /// Days of the visible month, padded with `nil` for the leading blanks.
    private var monthDays: [Date?] {
        guard let monthInterval = calendar.dateInterval(of: .month, for: monthAnchor),
              let range = calendar.range(of: .day, in: .month, for: monthAnchor) else { return [] }
        let firstWeekday = calendar.component(.weekday, from: monthInterval.start)
        let leadingBlanks = (firstWeekday - calendar.firstWeekday + 7) % 7
        var cells: [Date?] = Array(repeating: nil, count: leadingBlanks)
        for dayOffset in range {
            if let date = calendar.date(byAdding: .day, value: dayOffset - 1, to: monthInterval.start) {
                cells.append(date)
            }
        }
        return cells
    }

    private func accessibilityLabel(for day: Date, enabled: Bool, isToday: Bool) -> String {
        let fmt = DateFormatter()
        fmt.calendar = calendar
        fmt.timeZone = calendar.timeZone
        fmt.dateFormat = "EEEE, MMMM d"
        var label = fmt.string(from: day)
        if isToday { label += ", today" }
        label += enabled ? ", available" : ", unavailable"
        return label
    }
}

#if DEBUG
#Preview("Loaded") {
    ScrollView {
        SlotPicker(
            state: .loaded,
            slots: [
                SlotDTO(start: "2026-07-01T16:00:00Z", end: "2026-07-01T16:30:00Z", startLocal: nil),
                SlotDTO(start: "2026-07-01T20:30:00Z", end: "2026-07-01T21:00:00Z", startLocal: nil)
            ],
            timeZoneIdentifier: "America/Los_Angeles",
            timeZoneLabel: "Pacific Time",
            accent: Theme.Color.primary600,
            monthAnchor: Date(),
            selectedDate: Date(),
            onSelectDate: { _ in },
            onSelectSlot: { _ in },
            onChangeMonth: { _ in },
            onTapTimeZone: {},
            onJumpNextAvailable: {}
        )
        .padding()
    }
    .background(Theme.Color.appBg)
}

#Preview("No availability") {
    ScrollView {
        SlotPicker(
            state: .noAvailability,
            slots: [],
            timeZoneIdentifier: "America/Los_Angeles",
            timeZoneLabel: "Pacific Time",
            accent: Theme.Color.home,
            monthAnchor: Date(),
            selectedDate: Date(),
            onSelectDate: { _ in },
            onSelectSlot: { _ in },
            onChangeMonth: { _ in },
            onTapTimeZone: {},
            onJumpNextAvailable: {},
            onNotifyMe: {}
        )
        .padding()
    }
    .background(Theme.Color.appBg)
}
#endif
