//
//  FirstRunWizardScreen.swift
//  Pantopus
//
//  A2 "Set up booking" first-run wizard screen. Wraps `WizardShell` with the
//  owner's identity and switches on `model.step` to render the StepRail + step
//  body (claim handle · pick type · weekly hours · success hero). Success share
//  presents a `UIActivityViewController` via `.sheet(item:)`; dismissing it
//  finishes the wizard. Matches `scheduling-setup-frames.jsx`.
//

import SwiftUI
import UIKit

struct FirstRunWizardScreen: View {
    @State private var model: FirstRunWizardModel
    @Environment(\.dismiss) private var dismiss

    init(owner: SchedulingOwner) {
        _model = State(wrappedValue: FirstRunWizardModel(owner: owner))
    }

    var body: some View {
        WizardShell(model: model, identity: model.theme.identity) {
            content
        }
        .navigationBarBackButtonHidden(true)
        .onChange(of: model.isFinished) { _, finished in
            if finished { dismiss() }
        }
        .sheet(item: shareBinding) { item in
            WizardShareSheet(items: [item.url])
        }
    }

    private var shareBinding: Binding<ShareItem?> {
        Binding(
            get: { model.pendingShareURL.map(ShareItem.init) },
            set: { newValue in
                if newValue == nil {
                    model.pendingShareURL = nil
                    model.finishAfterShare()
                }
            }
        )
    }

    private var steps: [(Int, String)] {
        [(1, "Link"), (2, "Type"), (3, "Hours"), (4, "Share")]
    }

    @ViewBuilder
    private var content: some View {
        WizardStepRail(steps: steps, current: model.step.rawValue, accent: model.theme.accent, accentBg: model.theme.accentBg)
        switch model.step {
        case .link:
            WizardHeadline(
                title: "Claim your booking link",
                sub: "This is the link you'll share. People book you at it — pick something short and memorable."
            )
            WizardHandleField(
                slug: $model.slug,
                state: model.slugState,
                accent: model.theme.accent,
                accentBg: model.theme.accentBg,
                onPick: { model.pickSuggestion($0) }
            )
        case .type:
            WizardHeadline(
                title: "Pick a meeting type",
                sub: "Start with one — how you meet and how long it runs. You can add more from settings."
            )
            WizardTypePicker(
                locationMode: $model.locationMode,
                duration: $model.duration,
                accent: model.theme.accent,
                accentBg: model.theme.accentBg
            )
        case .hours:
            WizardHeadline(
                title: "Set your weekly hours",
                sub: "People can only book inside these windows. You can fine-tune any day, or just use the defaults."
            )
            WizardTimezoneChip(identifier: model.timezoneIdentifier, accent: model.theme.accent, accentBg: model.theme.accentBg)
            WizardHoursGrid(enabled: $model.hoursEnabled, accent: model.theme.accent, rangeLabel: model.rangeLabel)
        case .success:
            WizardSuccessHero(
                accent: model.theme.accent,
                accentBg: model.theme.accentBg,
                shadow: model.theme.ctaShadow,
                title: "You're all set",
                sub: "Your booking link is live. Share it and people can book a \(model.duration)-minute meeting during your weekly hours.",
                link: model.shareLink,
                onCopy: copyLink
            )
        }
    }

    private func copyLink() {
        UIPasteboard.general.string = "https://\(model.shareLink)"
    }
}

// MARK: - Share sheet plumbing

private struct ShareItem: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

private struct WizardShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context _: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_: UIActivityViewController, context _: Context) {}
}
