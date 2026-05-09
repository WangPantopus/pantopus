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
@MainActor
public struct WizardShell<Content: View>: View {
    private let model: any WizardModel
    private let content: Content

    @State private var showsDiscard = false

    public init(
        model: any WizardModel,
        @ViewBuilder content: () -> Content
    ) {
        self.model = model
        self.content = content()
    }

    public var body: some View {
        let chrome = model.chrome
        return VStack(spacing: 0) {
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
        let segmentCount: Int
        if case .stepOf(_, let total) = model.chrome.progressLabel {
            segmentCount = max(1, total)
        } else {
            segmentCount = 1
        }
        let filled = Int((Double(segmentCount) * fraction).rounded())
        SegmentedProgressBar(filled: filled, total: segmentCount)
            .padding(.horizontal, Spacing.s4)
            .padding(.vertical, Spacing.s2)
            .background(Theme.Color.appSurface)
    }

    @ViewBuilder
    private func stickyCTA(chrome: WizardChrome) -> some View {
        VStack(spacing: 0) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
            HStack(spacing: Spacing.s3) {
                if let secondary = chrome.secondaryCTA {
                    GhostButton(title: secondary.label) {
                        await MainActor.run { model.secondaryTapped() }
                    }
                    .accessibilityIdentifier(secondary.identifier)
                }
                PrimaryButton(
                    title: chrome.isSubmitting ? "Working…" : chrome.primaryCTALabel,
                    isLoading: chrome.isSubmitting,
                    isEnabled: chrome.primaryCTAEnabled
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
                    if case .stepOf(let current, let total) = progressLabel {
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
        case .close: return .x
        case .back: return .chevronLeft
        }
    }

    private var leadingA11y: String {
        switch leading {
        case .close: return "Close"
        case .back: return "Back"
        }
    }
}
