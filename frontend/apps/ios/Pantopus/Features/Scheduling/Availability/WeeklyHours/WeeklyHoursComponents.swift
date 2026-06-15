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
            HStack(spacing: Spacing.s2) {
                Icon(.clock, size: 14, color: Theme.Color.primary600)
                timePicker(range.start, label: "Start time", onChange: onStart)
                Text("–")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextMuted)
                timePicker(range.end, label: "End time", onChange: onEnd)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s2)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(range.isValid ? Theme.Color.appBorder : Theme.Color.error, lineWidth: 1.5)
            )

            if let onRemove {
                Button(action: onRemove) {
                    Icon(.x, size: 15, color: Theme.Color.appTextMuted)
                        .frame(width: 30, height: 30)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Remove time range")
            }
        }
        .overlay(alignment: .bottomLeading) {
            if !range.isValid {
                Text("End must be after start")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.error)
                    .offset(y: 16)
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
            HStack(spacing: Spacing.s3) {
                Toggle(isOn: Binding(get: { day.isEnabled }, set: onToggle)) {
                    Text(Weekday.longName(day.weekday))
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(day.isEnabled ? Theme.Color.appText : Theme.Color.appTextSecondary)
                }
                .toggleStyle(.switch)
                .tint(Theme.Color.primary600)
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
                        TimeRangeRow(
                            range: range,
                            onStart: { onStart(range.id, $0) },
                            onEnd: { onEnd(range.id, $0) },
                            onRemove: day.ranges.count > 1 ? { onRemoveRange(range.id) } : nil
                        )
                    }
                    addBlockButton
                }
                .padding(.leading, Spacing.s12)
            }
        }
        .padding(.vertical, Spacing.s1)
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
