//
//  WizardShell.swift
//  Pantopus
//
//  Generic wizard chrome: top bar (X/back + title + N/M readout), segmented
//  progress, scrolling content, and a sticky bottom CTA row with primary +
//  optional ghost. Owns the discard-confirm sheet.
//

import SwiftUI

/// Reusable wizard scaffold. Feature screens supply a `WizardModel` and a
/// content view; everything else (chrome, progress, CTA, close-confirm)
/// is handled here.
///
/// Pass `identity` to repaint the progress rail and the primary CTA in
/// a non-default pillar (`.home`, `.business`, `.warm`). Default is
/// `.personal` so legacy call sites render identically.
@MainActor
public struct WizardShell<Content: View>: View {
    private let model: any WizardModel
    private let identity: WizardIdentity
    private let content: Content

    @State private var showsDiscard = false

    public init(
        model: any WizardModel,
        identity: WizardIdentity = .personal,
        @ViewBuilder content: () -> Content
    ) {
        self.model = model
        self.identity = identity
        self.content = content()
    }

    public var body: some View {
        let chrome = model.chrome
        return VStack(spacing: Spacing.s0) {
            WizardTopBar(
                title: chrome.title,
                leading: chrome.leading,
                progressLabel: chrome.progressLabel,
                onLeading: handleLeading
            )
            if chrome.showsProgressBar, let fraction = chrome.progressFraction {
                progressBar(fraction: fraction)
            }
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.s5) {
                    content
                }
                .padding(.horizontal, Spacing.s4)
                .padding(.top, Spacing.s4)
                .padding(.bottom, Spacing.s8)
            }
            .background(Theme.Color.appBg)
            stickyCTA(chrome: chrome)
        }
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("wizardShell")
        .wizardCloseConfirm(isPresented: $showsDiscard) { model.discardConfirmed() }
    }

    @ViewBuilder
    private func progressBar(fraction: Double) -> some View {
        let segmentCount: Int = if case let .stepOf(_, total) = model.chrome.progressLabel {
            max(1, total)
        } else {
            1
        }
        let filled = Int((Double(segmentCount) * fraction).rounded())
        SegmentedProgressBar(
            currentStep: filled,
            totalSteps: segmentCount,
            fillColor: identity.accent
        )
            .padding(.horizontal, Spacing.s4)
            .padding(.vertical, Spacing.s2)
            .background(Theme.Color.appSurface)
    }

    private func stickyCTA(chrome: WizardChrome) -> some View {
        VStack(spacing: Spacing.s0) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
            HStack(spacing: Spacing.s3) {
                if let secondary = chrome.secondaryCTA {
                    GhostButton(title: secondary.label) {
                        await MainActor.run { model.secondaryTapped() }
                    }
                    .accessibilityIdentifier(secondary.identifier)
                }
                WizardPrimaryCTA(
                    title: chrome.isSubmitting ? "Working…" : chrome.primaryCTALabel,
                    isLoading: chrome.isSubmitting,
                    isEnabled: chrome.primaryCTAEnabled,
                    tint: identity.accent,
                    shadow: identity.ctaShadow
                ) {
                    await MainActor.run { model.primaryTapped() }
                }
                .accessibilityIdentifier("wizardPrimaryCTA")
            }
            .padding(Spacing.s4)
            .background(Theme.Color.appSurface)
        }
    }

    private func handleLeading() {
        switch model.chrome.leading {
        case .back:
            model.leadingTapped()
        case .close:
            if model.chrome.dirty {
                showsDiscard = true
            } else {
                model.leadingTapped()
            }
        }
    }
}

/// 44pt top bar: X/back leading, centered title, "N of M" trailing.
private struct WizardTopBar: View {
    let title: String
    let leading: WizardLeadingControl
    let progressLabel: WizardProgressLabel
    let onLeading: () -> Void

    var body: some View {
        ZStack {
            Text(title)
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
            HStack {
                Button(action: onLeading) {
                    Icon(leadingIcon, size: 22, color: Theme.Color.appText)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel(leadingA11y)
                .accessibilityIdentifier("wizardLeadingButton")
                Spacer()
                Group {
                    if case let .stepOf(current, total) = progressLabel {
                        Text("\(current) of \(total)")
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                            .padding(.horizontal, Spacing.s3)
                            .accessibilityIdentifier("wizardStepReadout")
                    } else {
                        Color.clear.frame(width: 44, height: 44)
                    }
                }
            }
            .padding(.horizontal, Spacing.s2)
        }
        .frame(height: 44)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
        }
    }

    private var leadingIcon: PantopusIcon {
        switch leading {
        case .close: .x
        case .back: .chevronLeft
        }
    }

    private var leadingA11y: String {
        switch leading {
        case .close: "Close"
        case .back: "Back"
        }
    }
}

/// Primary CTA used by the wizard sticky row. Mirrors the geometry and
/// loading/disabled behaviour of `PrimaryButton` but takes an explicit
/// `tint` + `shadow` so the wizard can paint the button in the current
/// identity pillar (sky / home / business / warm-amber).
@MainActor
private struct WizardPrimaryCTA: View {
    let title: String
    let isLoading: Bool
    let isEnabled: Bool
    let tint: Color
    let shadow: PantopusShadow
    let action: () async -> Void

    var body: some View {
        Button {
            Task { await action() }
        } label: {
            ZStack {
                Text(title)
                    .pantopusTextStyle(.body)
                    .opacity(isLoading ? 0 : 1)
                if isLoading {
                    ProgressView().tint(Theme.Color.appTextInverse)
                }
            }
            .foregroundStyle(Theme.Color.appTextInverse)
            .frame(maxWidth: .infinity, minHeight: 44)
            .padding(.horizontal, Spacing.s4)
            .background(tint)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .pantopusShadow(shadow)
            .opacity(isEnabled ? 1 : 0.5)
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled || isLoading)
        .accessibilityLabel(title)
        .accessibilityAddTraits(.isButton)
    }
}
