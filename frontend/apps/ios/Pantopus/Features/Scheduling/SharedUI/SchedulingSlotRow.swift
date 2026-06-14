//
//  SchedulingSlotRow.swift
//  Pantopus
//
//  Foundation (I0b) — the single slot-row primitive reused 1:1 across the slot
//  picker, the 409 slot-taken sheet, host reschedule, and find-a-time. Mirrors
//  the Support Trains weekday/time-range row: full-width button, time leading,
//  optional detail, trailing chevron, pillar accent on selection. Tokens only.
//

import SwiftUI

/// A single tappable open-time row. Callers format `time` / `detail` from their
/// own slot model (`SlotDTO`, `SchedulingSlotAlternative`, …).
public struct SchedulingSlotRow: View {
    private let time: String
    private let detail: String?
    private let accent: Color
    private let isSelected: Bool
    private let isDisabled: Bool
    private let action: () -> Void

    public init(
        time: String,
        detail: String? = nil,
        accent: Color = Theme.Color.primary600,
        isSelected: Bool = false,
        isDisabled: Bool = false,
        action: @escaping () -> Void
    ) {
        self.time = time
        self.detail = detail
        self.accent = accent
        self.isSelected = isSelected
        self.isDisabled = isDisabled
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.s3) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(time)
                        .pantopusTextStyle(.body)
                        .fontWeight(.semibold)
                        .foregroundStyle(isSelected ? accent : Theme.Color.appText)
                    if let detail {
                        Text(detail)
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextMuted)
                    }
                }
                Spacer(minLength: Spacing.s2)
                Icon(.chevronRight, size: 16, color: isSelected ? accent : Theme.Color.appTextMuted)
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.vertical, Spacing.s3)
            .frame(minHeight: 44)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(isSelected ? accent.opacity(0.08) : Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(isSelected ? accent : Theme.Color.appBorder, lineWidth: isSelected ? 1.5 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .opacity(isDisabled ? 0.5 : 1)
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
        .accessibilityLabel(detail.map { "\(time), \($0)" } ?? time)
        .accessibilityAddTraits(.isButton)
    }
}

/// A shimmer placeholder the exact width of a real slot row, for the loading and
/// "checking live availability" states.
public struct SchedulingSlotRowSkeleton: View {
    public init() {}

    public var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 6) {
                Shimmer(width: 96, height: 16)
                Shimmer(width: 64, height: 12)
            }
            Spacer()
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s3)
        .frame(minHeight: 44, alignment: .leading)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
    }
}

#if DEBUG
#Preview {
    VStack(spacing: Spacing.s2) {
        SchedulingSlotRow(time: "9:30 AM", detail: "30 min · 12:30 PM for Maria") {}
        SchedulingSlotRow(time: "10:00 AM", detail: "30 min", isSelected: true) {}
        SchedulingSlotRowSkeleton()
    }
    .padding()
    .background(Theme.Color.appBg)
}
#endif
