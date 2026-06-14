//
//  WeeklyHoursComponents.swift
//  Pantopus
//
//  Stream I3 — B5 weekly-hours editor subviews: the per-weekday on/off +
//  time-range rows (real labelled time buttons, VoiceOver-ready), the
//  no-hours warning card, and the link-out rows. Tokens only.
//

import SwiftUI

/// One start–end window with two tappable time buttons and an optional remove.
struct TimeRangeRow: View {
    let range: TimeRange
    let onStart: (TimeOfDay) -> Void
    let onEnd: (TimeOfDay) -> Void
    let onRemove: (() -> Void)?

    var body: some View {
        HStack(spacing: Spacing.s2) {
            timePicker(range.start, label: "Start time", onChange: onStart)
            Text("–")
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appTextMuted)
            timePicker(range.end, label: "End time", onChange: onEnd)
            Spacer(minLength: Spacing.s2)
            if let onRemove {
                Button(action: onRemove) {
                    Icon(.x, size: 16, color: Theme.Color.appTextMuted)
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Remove time range")
            }
        }
        .overlay(alignment: .bottom) {
            if !range.isValid {
                Text("End must be after start")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.error)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .offset(y: 14)
            }
        }
    }

    private func timePicker(
        _ time: TimeOfDay,
        label: String,
        onChange: @escaping (TimeOfDay) -> Void
    ) -> some View {
        DatePicker(
            label,
            selection: Binding(
                get: { time.referenceDate() },
                set: { onChange(TimeOfDay(from: $0)) }
            ),
            displayedComponents: .hourAndMinute
        )
        .labelsHidden()
        .accessibilityLabel(label)
    }
}

/// A single weekday: leading on/off toggle, then (when on) its time ranges,
/// an "Add a block" affordance, and a "Copy to…" menu.
struct WeekdayHoursRow: View {
    let day: DayHours
    let onToggle: (Bool) -> Void
    let onAddRange: () -> Void
    let onCopy: ([Int]) -> Void
    let onStart: (UUID, TimeOfDay) -> Void
    let onEnd: (UUID, TimeOfDay) -> Void
    let onRemoveRange: (UUID) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            Toggle(isOn: Binding(get: { day.isEnabled }, set: onToggle)) {
                Text(Weekday.longName(day.weekday))
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
            }
            .tint(Theme.Color.primary600)
            .accessibilityIdentifier("scheduling.weeklyHours.toggle.\(day.weekday)")

            if day.isEnabled {
                ForEach(day.ranges) { range in
                    TimeRangeRow(
                        range: range,
                        onStart: { onStart(range.id, $0) },
                        onEnd: { onEnd(range.id, $0) },
                        onRemove: day.ranges.count > 1 ? { onRemoveRange(range.id) } : nil
                    )
                }
                actionRow
            } else {
                Text("Unavailable")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
        }
        .padding(.vertical, Spacing.s1)
    }

    private var actionRow: some View {
        HStack(spacing: Spacing.s3) {
            Button(action: onAddRange) {
                HStack(spacing: Spacing.s1) {
                    Icon(.plus, size: 14, color: Theme.Color.primary600)
                    Text("Add a block")
                        .pantopusTextStyle(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.Color.primary600)
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Add a time block to \(Weekday.longName(day.weekday))")

            Spacer()

            Menu {
                Button("Copy to all days") { onCopy(targets(in: Weekday.displayOrder)) }
                Button("Copy to weekdays") { onCopy(targets(in: [1, 2, 3, 4, 5])) }
                Button("Copy to weekend") { onCopy(targets(in: [6, 0])) }
            } label: {
                HStack(spacing: Spacing.s1) {
                    Icon(.copy, size: 13, color: Theme.Color.appTextSecondary)
                    Text("Copy to…")
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            .accessibilityLabel("Copy \(Weekday.longName(day.weekday)) hours to other days")
        }
    }

    private func targets(in weekdays: [Int]) -> [Int] {
        weekdays.filter { $0 != day.weekday }
    }
}

/// Inline amber warning shown when every weekday is off.
struct NoHoursWarningCard: View {
    let onUseDefault: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s2) {
                Icon(.alertTriangle, size: 18, color: Theme.Color.warning)
                Text("No hours set")
                    .pantopusTextStyle(.body)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.appText)
            }
            Text("People can't book you until you add at least one.")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            Button(action: onUseDefault) {
                Text("Use 9–5, Mon–Fri")
                    .pantopusTextStyle(.caption)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.warning)
                    .padding(.horizontal, Spacing.s3)
                    .padding(.vertical, Spacing.s2)
                    .overlay(
                        Capsule().stroke(Theme.Color.warning, lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
            .padding(.top, Spacing.s1)
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.warningBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .padding(.horizontal, Spacing.s4)
        .accessibilityIdentifier("scheduling.weeklyHours.noHoursWarning")
    }
}

/// A chevron link-out row inside the editor (date overrides / booking limits /
/// block off time).
struct SchedulingLinkRow: View {
    let icon: PantopusIcon
    let title: String
    let subtitle: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.s3) {
                Icon(icon, size: 18, color: Theme.Color.primary600)
                    .frame(width: 28)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appText)
                    if let subtitle {
                        Text(subtitle)
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                }
                Spacer(minLength: Spacing.s2)
                Icon(.chevronRight, size: 16, color: Theme.Color.appTextMuted)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
