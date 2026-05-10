//
//  WizardStep.swift
//  Pantopus
//
//  Step descriptor + the small view-model protocol `WizardShell` consumes.
//  Each concrete wizard (Add Home, etc.) implements `WizardModel` and
//  stays in charge of its own state machine — the shell only drives chrome.
//

import SwiftUI

/// Contract a wizard view model must satisfy to plug into `WizardShell`.
@MainActor
public protocol WizardModel: AnyObject {
    /// Snapshot of chrome state for the active step.
    var chrome: WizardChrome { get }

    /// Tap on the leading control (X or back chevron). The model decides
    /// whether to dismiss, go back, or show the discard confirm.
    func leadingTapped()

    /// User confirmed the discard sheet. Caller should pop the wizard.
    func discardConfirmed()

    /// Tap on the primary CTA in the sticky row.
    func primaryTapped()

    /// Tap on the optional secondary CTA. Default: no-op.
    func secondaryTapped()
}

public extension WizardModel {
    func secondaryTapped() {}
}
