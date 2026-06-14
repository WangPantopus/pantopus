//
//  SlotTakenSheet.swift
//  Pantopus
//
//  Foundation (I0b) — the 409 conflict recovery sheet. The most important
//  scheduling error: it must NEVER dead-end. Presented locally on a
//  SLOT_TAKEN / SLOT_UNAVAILABLE / SLOT_FULL response, it leads with a calm
//  amber halo, re-renders the nearest open times (`SchedulingSlotAlternative`)
//  as live slot rows, and always offers "Pick another time". Entered details
//  are preserved by the caller. Tokens only.
//

import SwiftUI

/// Renders the 409 `alternatives` array as a recovery bottom sheet. Feature
/// streams present this via `.sheet` and apply `.presentationDetents`.
public struct SlotTakenSheet: View {
    /// Which recovery state to show.
    public enum Mode: Equatable, Sendable {
        /// Nearest open times available (use `alternatives`).
        case alternatives
        /// No alternatives — offer the waitlist.
        case fullyBooked
        /// Re-fetching live availability (shimmer rows).
        case refreshing
    }

    private let mode: Mode
    private let alternatives: [SchedulingSlotAlternative]
    private let takenTimeLabel: String?
    private let timeZoneIdentifier: String
    private let accent: Color
    private let onSelect: (SchedulingSlotAlternative) -> Void
    private let onPickAnotherTime: () -> Void
    private let onJoinWaitlist: (() -> Void)?
    private let onSeeAnotherDay: (() -> Void)?

    public init(
        mode: Mode = .alternatives,
        alternatives: [SchedulingSlotAlternative],
        takenTimeLabel: String? = nil,
        timeZoneIdentifier: String,
        accent: Color = Theme.Color.primary600,
        onSelect: @escaping (SchedulingSlotAlternative) -> Void,
        onPickAnotherTime: @escaping () -> Void,
        onJoinWaitlist: (() -> Void)? = nil,
        onSeeAnotherDay: (() -> Void)? = nil
    ) {
        self.mode = mode
        self.alternatives = alternatives
        self.takenTimeLabel = takenTimeLabel
        self.timeZoneIdentifier = timeZoneIdentifier
        self.accent = accent
        self.onSelect = onSelect
        self.onPickAnotherTime = onPickAnotherTime
        self.onJoinWaitlist = onJoinWaitlist
        self.onSeeAnotherDay = onSeeAnotherDay
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            errorBlock
            content
            footer
        }
        .padding(Spacing.s5)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .accessibilityIdentifier("scheduling.slotTakenSheet")
    }

    // MARK: - Header

    private var errorBlock: some View {
        HStack(alignment: .top, spacing: Spacing.s3) {
            ZStack {
                Circle().fill(Theme.Color.warningBg).frame(width: 44, height: 44)
                Icon(.clock, size: 22, color: Theme.Color.warning)
            }
            VStack(alignment: .leading, spacing: Spacing.s1) {
                Text("That time was just taken")
                    .pantopusTextStyle(.h3)
                    .foregroundStyle(Theme.Color.appText)
                Text(subtitle)
                    .pantopusTextStyle(.small)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var subtitle: String {
        switch mode {
        case .alternatives:
            if let takenTimeLabel {
                return "Someone grabbed \(takenTimeLabel) first. Here are the closest open times."
            }
            return "Here are the closest open times."
        case .fullyBooked:
            return "This day is fully booked. Join the waitlist and we'll let you know the moment something opens."
        case .refreshing:
            return "Checking live availability…"
        }
    }

    // MARK: - Content

    @ViewBuilder
    private var content: some View {
        switch mode {
        case .alternatives:
            VStack(spacing: Spacing.s2) {
                ForEach(alternatives, id: \.self) { alt in
                    SchedulingSlotRow(
                        time: timeLabel(for: alt),
                        detail: dayLabel(for: alt),
                        accent: accent
                    ) {
                        onSelect(alt)
                    }
                }
            }
        case .fullyBooked:
            emptyFullyBooked
        case .refreshing:
            VStack(spacing: Spacing.s2) {
                ForEach(0..<3, id: \.self) { _ in SchedulingSlotRowSkeleton() }
            }
            .accessibilityLabel("Checking live availability")
        }
    }

    private var emptyFullyBooked: some View {
        VStack(spacing: Spacing.s2) {
            Icon(.calendar, size: 26, color: Theme.Color.appTextMuted)
            Text("This day is fully booked")
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.s6)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
                .foregroundStyle(Theme.Color.appBorder)
        )
    }

    // MARK: - Footer

    private var footer: some View {
        VStack(spacing: Spacing.s3) {
            switch mode {
            case .alternatives, .refreshing:
                GhostButton(title: "Pick another time", action: onPickAnotherTime)
                Text("Your details are saved.")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextMuted)
            case .fullyBooked:
                if let onJoinWaitlist {
                    PrimaryButton(title: "Join the waitlist", action: onJoinWaitlist)
                }
                GhostButton(title: "See another day", action: onSeeAnotherDay ?? onPickAnotherTime)
            }
        }
    }

    // MARK: - Formatting

    private func timeLabel(for alt: SchedulingSlotAlternative) -> String {
        SchedulingTime.localString(
            utcISO: alt.start, tz: timeZoneIdentifier, dateStyle: .none, timeStyle: .short
        ) ?? alt.startLocal ?? alt.start
    }

    private func dayLabel(for alt: SchedulingSlotAlternative) -> String? {
        SchedulingTime.localString(
            utcISO: alt.start, tz: timeZoneIdentifier, dateStyle: .medium, timeStyle: .none
        )
    }
}

#if DEBUG
#Preview("Alternatives") {
    SlotTakenSheet(
        mode: .alternatives,
        alternatives: [
            SchedulingSlotAlternative(start: "2026-07-01T16:00:00Z", end: "2026-07-01T16:30:00Z", startLocal: nil),
            SchedulingSlotAlternative(start: "2026-07-01T17:00:00Z", end: "2026-07-01T17:30:00Z", startLocal: nil)
        ],
        takenTimeLabel: "2:00 PM",
        timeZoneIdentifier: "America/New_York",
        onSelect: { _ in },
        onPickAnotherTime: {}
    )
}

#Preview("Fully booked") {
    SlotTakenSheet(
        mode: .fullyBooked,
        alternatives: [],
        timeZoneIdentifier: "America/New_York",
        onSelect: { _ in },
        onPickAnotherTime: {},
        onJoinWaitlist: {}
    )
}
#endif
