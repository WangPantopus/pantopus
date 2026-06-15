//
//  EventTypeEditorComponents.swift
//  Pantopus
//
//  Stream I2 — B2 editor subviews: colour swatch picker, duration chips,
//  labelled stepper + caption toggle rows, and the inline info / connect card.
//  Tokens only.
//

import SwiftUI

/// Row of fixed colour swatches; the selected one wears a ring + check.
struct EventTypeColorPicker: View {
    @Binding var selection: EventTypeSwatch

    var body: some View {
        HStack(spacing: Spacing.s2) {
            ForEach(EventTypeSwatch.allCases) { swatch in
                Button { selection = swatch } label: {
                    ZStack {
                        if selection == swatch {
                            Circle().stroke(swatch.color, lineWidth: 2).frame(width: 30, height: 30)
                        }
                        Circle().fill(swatch.color).frame(width: 22, height: 22)
                        if selection == swatch {
                            Icon(.check, size: 12, color: Theme.Color.appTextInverse)
                        }
                    }
                    .frame(width: 30, height: 30)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(swatch.rawValue.capitalized) colour")
                .accessibilityAddTraits(selection == swatch ? [.isSelected] : [])
            }
            Spacer(minLength: Spacing.s0)
        }
        .accessibilityIdentifier("scheduling.eventType.colorPicker")
    }
}

/// A selectable duration chip used in multiple-duration mode. A small "Default"
/// marker rides on the one the booker gets by default.
struct DurationChip: View {
    let minutes: Int
    let isSelected: Bool
    let isDefault: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.s1) {
                if isDefault { Icon(.star, size: 11, color: Theme.Color.appTextInverse) }
                Text(EventTypeFormat.duration(minutes))
                    .pantopusTextStyle(.caption)
                    .fontWeight(.semibold)
            }
            .foregroundStyle(isSelected ? Theme.Color.appTextInverse : Theme.Color.appTextStrong)
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s2)
            .background(isSelected ? Theme.Color.primary600 : Theme.Color.appSurfaceSunken)
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(EventTypeFormat.duration(minutes))\(isDefault ? ", default" : "")")
    }
}

/// `title  …  value [−/+]` with an optional one-line caption.
struct LabeledStepper: View {
    let title: String
    var caption: String?
    @Binding var value: Int
    let range: ClosedRange<Int>
    var step = 1
    let format: (Int) -> String

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            Stepper(value: $value, in: range, step: step) {
                HStack {
                    Text(title)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appText)
                    Spacer(minLength: Spacing.s2)
                    Text(format(value))
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            if let caption {
                Text(caption)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
    }
}

/// A platform toggle with a title + optional caption underneath.
struct CaptionToggle: View {
    let title: String
    var caption: String?
    @Binding var isOn: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            Toggle(isOn: $isOn) {
                Text(title)
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
            }
            .tint(Theme.Color.primary600)
            if let caption {
                Text(caption)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
    }
}

/// Inline info / call-to-action card (Stripe connect, coming-soon notice).
struct EventInfoCard: View {
    enum Tone { case info, warning }

    let icon: PantopusIcon
    let title: String
    let message: String
    var tone: Tone = .info
    var actionTitle: String?
    var action: (() -> Void)?

    private var fg: Color { tone == .warning ? Theme.Color.warning : Theme.Color.info }
    private var bg: Color { tone == .warning ? Theme.Color.warningBg : Theme.Color.infoBg }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s2) {
                Icon(icon, size: 18, color: fg)
                Text(title)
                    .pantopusTextStyle(.body)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.appText)
            }
            Text(message)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            if let actionTitle, let action {
                Button(action: action) {
                    Text(actionTitle)
                        .pantopusTextStyle(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(fg)
                        .padding(.horizontal, Spacing.s3)
                        .padding(.vertical, Spacing.s2)
                        .overlay(Capsule().stroke(fg, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .padding(.top, Spacing.s1)
            }
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(bg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
    }
}
