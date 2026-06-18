//
//  WeeklyHoursComponents.swift
//  Pantopus
//
//  Stream I3 — B5 weekly-hours editor subviews: the per-weekday on/off +
//  time-range rows (real labelled time buttons, VoiceOver-ready), the
//  no-hours warning card, and the link-out rows. Tokens only.
//

import SwiftUI

/// A single weekday: leading on/off toggle, then (when on) its time ranges
/// (each a labeled time-range button), an "Add a block" affordance, and a
/// "Copy to…" menu.
struct WeekdayHoursRow: View {
    let day: DayHours
    var disabled: Bool = false
    let onToggle: (Bool) -> Void
    let onAddRange: () -> Void
    let onCopy: ([Int]) -> Void
    let onEditRange: (TimeRange) -> Void
    let onRemoveRange: (UUID) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            HStack(spacing: Spacing.s3) {
                Toggle(isOn: Binding(get: { day.isEnabled }, set: onToggle)) {
                    Text(Weekday.longName(day.weekday))
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(day.isEnabled ? Theme.Color.appText : Theme.Color.appTextSecondary)
                }
                .toggleStyle(.switch)
                .tint(Theme.Color.primary600)
                .disabled(disabled)
                .accessibilityIdentifier("scheduling.weeklyHours.toggle.\(day.weekday)")

                Spacer(minLength: Spacing.s2)

                if day.isEnabled {
                    copyMenu
                } else {
                    Text("Unavailable")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Theme.Color.appTextMuted)
                }
            }

            if day.isEnabled {
                VStack(alignment: .leading, spacing: Spacing.s2) {
                    ForEach(day.ranges) { range in
                        AvailabilityTimeRangeButton(
                            label: range.display,
                            isValid: range.isValid,
                            disabled: disabled,
                            onTap: { onEditRange(range) },
                            onRemove: day.ranges.count > 1 ? { onRemoveRange(range.id) } : nil
                        )
                        if !range.isValid {
                            Text("End must be after start")
                                .pantopusTextStyle(.caption)
                                .foregroundStyle(Theme.Color.error)
                        }
                    }
                    if !disabled {
                        addBlockButton
                    }
                }
                .padding(.leading, Spacing.s12)
            }
        }
        .padding(.vertical, Spacing.s1)
        .opacity(disabled ? 0.7 : 1)
    }

    /// Icon-only copy affordance (JSX renders a `copy` glyph that opens a
    /// "copy to other days" popover). The native menu is the popover here.
    private var copyMenu: some View {
        Menu {
            Button("Copy to all days") { onCopy(targets(in: Weekday.displayOrder)) }
            Button("Copy to weekdays") { onCopy(targets(in: [1, 2, 3, 4, 5])) }
            Button("Copy to weekend") { onCopy(targets(in: [6, 0])) }
        } label: {
            Icon(.copy, size: 15, color: Theme.Color.appTextMuted)
                .frame(width: 30, height: 30)
                .contentShape(Rectangle())
        }
        .accessibilityLabel("Copy \(Weekday.longName(day.weekday)) hours to other days")
    }

    private var addBlockButton: some View {
        Button(action: onAddRange) {
            HStack(spacing: Spacing.s1) {
                Icon(.plus, size: 13, strokeWidth: 2.4, color: Theme.Color.primary600)
                Text("Add a block")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.Color.primary600)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Add a time block to \(Weekday.longName(day.weekday))")
    }

    private func targets(in weekdays: [Int]) -> [Int] {
        weekdays.filter { $0 != day.weekday }
    }
}

/// Sky-tinted "Use 9–5, Mon–Fri" quick-default button shared by the warning
/// card and the empty hero (JSX `QuickDefaultButton`).
struct QuickDefaultButton: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: Spacing.s2) {
                Icon(.wandSparkles, size: 15, color: Theme.Color.primary700)
                Text("Use 9–5, Mon–Fri")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Theme.Color.primary700)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 42)
            .background(Theme.Color.primary50)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(Theme.Color.primary200, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Use 9 to 5, Monday to Friday")
    }
}

/// Inline amber warning shown when every weekday is off (JSX `WarningCard`).
struct NoHoursWarningCard: View {
    let onUseDefault: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            HStack(alignment: .top, spacing: Spacing.s2) {
                Icon(.alertTriangle, size: 17, color: Theme.Color.warning)
                VStack(alignment: .leading, spacing: 2) {
                    Text("No hours set")
                        .pantopusTextStyle(.body)
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.Color.appText)
                    Text("People can't book you until you add at least one block.")
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            QuickDefaultButton(onTap: onUseDefault)
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.warningBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.warningLight, lineWidth: 1)
        )
        .padding(.horizontal, Spacing.s4)
        .accessibilityIdentifier("scheduling.weeklyHours.noHoursWarning")
    }
}

/// Sky-tinted explainer card shown when the schedule is unset — these hours
/// are the source the home & business pages compose on (JSX `CompositionGapCard`).
struct CompositionGapCard: View {
    var body: some View {
        HStack(alignment: .top, spacing: Spacing.s3) {
            Icon(.layers, size: 16, color: Theme.Color.primary700)
                .frame(width: 32, height: 32)
                .background(Theme.Color.primary100)
                .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text("Start with your hours")
                    .pantopusTextStyle(.body)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.appText)
                Text("Your family and business pages build on these hours, so set them first.")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.primary50)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.primary200, lineWidth: 1)
        )
        .padding(.horizontal, Spacing.s4)
        .accessibilityIdentifier("scheduling.weeklyHours.compositionGap")
    }
}

/// Empty / unset hero shown when the schedule has no hours set and the user
/// has not yet seeded a default. Mirrors the design's `EmptyHero` inside a
/// bordered `Card` (54pt icon tile + headline + body + quick-default button).
struct WeeklyHoursEmptyHero: View {
    let onUseDefault: () -> Void

    var body: some View {
        AvailabilityCard {
            VStack(spacing: Spacing.s3) {
                Icon(.calendarClock, size: 26, strokeWidth: 1.9, color: Theme.Color.primary600)
                    .frame(width: 54, height: 54)
                    .background(Theme.Color.primary50)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
                Text("Set your hours")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                Text("Tell people the days and times you're open to bookings. You can fine-tune any day after.")
                    .font(.system(size: 12.5))
                    .lineSpacing(3)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .frame(maxWidth: 226)
                QuickDefaultButton(onTap: onUseDefault)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.s1)
        }
        .accessibilityIdentifier("scheduling.weeklyHours.emptyHero")
    }
}

/// A time-range editor sheet with two wheel pickers (start / end). Presented
/// when a `AvailabilityTimeRangeButton` is tapped, mirroring the design's
/// "tap a labeled button to open a picker" idiom.
struct TimeRangePickerSheet: View {
    let title: String
    @State private var start: Date
    @State private var end: Date
    let onCommit: (TimeOfDay, TimeOfDay) -> Void
    @Environment(\.dismiss) private var dismiss

    init(
        range: TimeRange,
        onCommit: @escaping (TimeOfDay, TimeOfDay) -> Void
    ) {
        title = "Edit time range"
        _start = State(initialValue: range.start.referenceDate())
        _end = State(initialValue: range.end.referenceDate())
        self.onCommit = onCommit
    }

    var body: some View {
        VStack(spacing: Spacing.s4) {
            Text(title)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .padding(.top, Spacing.s4)
            HStack(spacing: Spacing.s2) {
                VStack(spacing: Spacing.s1) {
                    AvailabilityFieldLabel(text: "Starts")
                    DatePicker("Start time", selection: $start, displayedComponents: .hourAndMinute)
                        .labelsHidden()
                        .datePickerStyle(.wheel)
                        .accessibilityLabel("Start time")
                }
                VStack(spacing: Spacing.s1) {
                    AvailabilityFieldLabel(text: "Ends")
                    DatePicker("End time", selection: $end, displayedComponents: .hourAndMinute)
                        .labelsHidden()
                        .datePickerStyle(.wheel)
                        .accessibilityLabel("End time")
                }
            }
            PrimaryButton(title: "Done") {
                onCommit(TimeOfDay(from: start), TimeOfDay(from: end))
                await MainActor.run { dismiss() }
            }
            .padding(.horizontal, Spacing.s4)
            Spacer(minLength: 0)
        }
        .background(Theme.Color.appBg)
        .presentationDetents([.height(360)])
        .presentationDragIndicator(.visible)
        .accessibilityIdentifier("scheduling.weeklyHours.timeRangeSheet")
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
                Icon(icon, size: 15, color: Theme.Color.appTextSecondary)
                    .frame(width: 30, height: 30)
                    .background(Theme.Color.appSurfaceSunken)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 13, weight: .semibold))
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
