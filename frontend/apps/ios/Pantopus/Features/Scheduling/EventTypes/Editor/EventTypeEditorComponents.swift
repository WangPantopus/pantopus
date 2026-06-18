//
//  EventTypeEditorComponents.swift
//  Pantopus
//
//  Stream I2 — B2 editor subviews: colour swatch picker, duration chips,
//  labelled stepper + caption toggle rows, and the inline info / connect card.
//  Tokens only.
//

import SwiftUI

/// Row of fixed colour swatches. The selected one wears a double-ring halo —
/// a white gap then a thin ring in the swatch's own colour — per the design's
/// `0 0 0 2px #fff, 0 0 0 4px c` selection treatment (no inner check glyph).
struct EventTypeColorPicker: View {
    @Binding var selection: EventTypeSwatch

    var body: some View {
        HStack(spacing: Spacing.s2) {
            ForEach(EventTypeSwatch.allCases) { swatch in
                Button { selection = swatch } label: {
                    ZStack {
                        if selection == swatch {
                            Circle()
                                .stroke(Theme.Color.appSurface, lineWidth: 2)
                                .frame(width: 24, height: 24)
                            Circle()
                                .stroke(swatch.color, lineWidth: 2)
                                .frame(width: 28, height: 28)
                        }
                        Circle().fill(swatch.color).frame(width: 24, height: 24)
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

/// White card with a **pillar-colored** UPPERCASE overline — the design's
/// `Card.overline` (sky personal / violet business). Mirrors the shared
/// `FormFieldGroup` geometry (16px-radius surface, s4 padding) but paints the
/// overline in the active pillar accent instead of grey, and supports an
/// overline-less card (the Controls/Visibility card draws no overline) plus a
/// trailing chevron for the collapsible Advanced card.
struct PillarFieldGroup<Content: View>: View {
    let overline: String?
    let accent: Color
    var isExpanded: Bool?
    var onToggle: (() -> Void)?
    @ViewBuilder let content: () -> Content

    init(
        _ overline: String?,
        accent: Color,
        isExpanded: Bool? = nil,
        onToggle: (() -> Void)? = nil,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.overline = overline
        self.accent = accent
        self.isExpanded = isExpanded
        self.onToggle = onToggle
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            if let overline {
                overlineHeader(overline)
            }
            content()
        }
        .padding(Spacing.s4)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .pantopusShadow(.sm)
        .padding(.horizontal, Spacing.s4)
    }

    @ViewBuilder
    private func overlineHeader(_ text: String) -> some View {
        if let isExpanded, let onToggle {
            Button(action: onToggle) {
                HStack {
                    Text(text.uppercased())
                        .pantopusTextStyle(.overline)
                        .foregroundStyle(accent)
                    Spacer()
                    Icon(isExpanded ? .chevronUp : .chevronDown, size: 16, color: Theme.Color.appTextMuted)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(.isHeader)
        } else {
            Text(text.uppercased())
                .pantopusTextStyle(.overline)
                .foregroundStyle(accent)
                .accessibilityAddTraits(.isHeader)
        }
    }
}

/// Compact bordered stepper box — the design `DurationCard` `Stepper` that sits
/// inline beside the quick chips: a 1px-bordered pill showing "30 min" flanked
/// by − / + tap targets. Distinct from `LabeledStepper` (a full-width row).
struct CompactDurationStepper: View {
    @Binding var value: Int
    var unit = "min"
    var range: ClosedRange<Int> = 5...480
    var step = 5

    var body: some View {
        HStack(spacing: Spacing.s2) {
            button(.minus) { value = max(range.lowerBound, value - step) }
            Text("\(value) \(unit)")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .monospacedDigit()
                .frame(minWidth: 52)
            button(.plus) { value = min(range.upperBound, value + step) }
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, Spacing.s2)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Length")
        .accessibilityValue("\(value) \(unit)")
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: value = min(range.upperBound, value + step)
            case .decrement: value = max(range.lowerBound, value - step)
            @unknown default: break
            }
        }
    }

    private func button(_ icon: PantopusIcon, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Icon(icon, size: 14, strokeWidth: 2.2, color: Theme.Color.appTextSecondary)
                .frame(width: 22, height: 22)
        }
        .buttonStyle(.plain)
    }
}

/// A small outlined "+ N" pill that adds a preset length in single-duration
/// mode — the design's `QuickChip`.
struct QuickDurationChip: View {
    let minutes: Int
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.s1) {
                Icon(.plus, size: 11, color: Theme.Color.primary600)
                Text("\(minutes)")
                    .pantopusTextStyle(.caption)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.appTextStrong)
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s2)
            .overlay(Capsule().stroke(Theme.Color.appBorder, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Set length to \(minutes) minutes")
    }
}

/// Pinned, full-width primary save bar — the design's sticky `SaveBar`
/// ("Create event type" / "Save event type"), shimmering while a commit is in
/// flight. Functional chrome stays product sky.
struct EventTypeSaveBar: View {
    let label: String
    let isEnabled: Bool
    let isSaving: Bool
    let onCommit: () -> Void

    var body: some View {
        Group {
            if isSaving {
                HStack {
                    Text("Saving…")
                        .pantopusTextStyle(.body)
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.Color.appTextMuted)
                }
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(Theme.Color.appSurfaceSunken)
                .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            } else {
                Button(action: onCommit) {
                    Text(label)
                        .pantopusTextStyle(.body)
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.Color.appTextInverse)
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .background(isEnabled ? Theme.Color.primary600 : Theme.Color.appBorderStrong)
                .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
                .disabled(!isEnabled)
            }
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.top, Spacing.s3)
        .padding(.bottom, Spacing.s4)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .top) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
        }
        .accessibilityIdentifier("scheduling.eventType.saveBar")
    }
}

/// Stripe-not-connected inline card — the design's violet-tinted `StripeCard`
/// with a credit-card tile, the connect copy, and a full-width sky
/// "Connect Stripe" button carrying an external-link glyph.
struct StripeConnectCard: View {
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            HStack(alignment: .top, spacing: Spacing.s3) {
                ZStack {
                    RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                        .fill(Theme.Color.business)
                        .frame(width: 30, height: 30)
                    Icon(.creditCard, size: 15, strokeWidth: 2.2, color: Theme.Color.appTextInverse)
                }
                VStack(alignment: .leading, spacing: Spacing.s1) {
                    Text("Connect payments to charge for bookings")
                        .pantopusTextStyle(.body)
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.Color.appText)
                    Text("Pantopus uses Stripe to collect payments and deposits. It takes about a minute.")
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextStrong)
                }
            }
            Button(action: action) {
                HStack(spacing: Spacing.s2) {
                    Icon(.externalLink, size: 14, color: Theme.Color.appTextInverse)
                    Text("Connect Stripe")
                        .pantopusTextStyle(.body)
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .frame(maxWidth: .infinity, minHeight: 38)
                .background(Theme.Color.primary600)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("scheduling.eventType.connectStripe")
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.businessBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.business.opacity(0.2), lineWidth: 1)
        )
    }
}

/// Toggle row with a leading icon tile — the design's `ControlsCard` `ToggleRow`
/// idiom (icon tile tints sky when on, sunken when off; product-sky switch on
/// the trailing edge; title + sub).
struct IconToggleRow: View {
    let icon: PantopusIcon
    let title: String
    let subtitle: String
    @Binding var isOn: Bool

    var body: some View {
        HStack(spacing: Spacing.s3) {
            ZStack {
                RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                    .fill(isOn ? Theme.Color.primary50 : Theme.Color.appSurfaceSunken)
                    .frame(width: 30, height: 30)
                Icon(icon, size: 15, color: isOn ? Theme.Color.primary600 : Theme.Color.appTextSecondary)
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .pantopusTextStyle(.body)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.appText)
                Text(subtitle)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer(minLength: Spacing.s2)
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(Theme.Color.primary600)
        }
        .accessibilityElement(children: .combine)
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
