//
//  VacationHoldViewModel.swift
//  Pantopus
//
//  A14.8 — Vacation Hold view-model. Drives both the `scheduling`
//  (compose a hold) and `active` (in-flight hold) variants from a
//  single mode enum. Persistence is stubbed — the backend endpoint
//  lands in a later phase; today the VM just flips `mode` locally
//  so the design parity tests + previews exercise both frames.
//

import Foundation
import Observation
import SwiftUI

/// Initial seed for the screen. The route currently lands in
/// `.scheduling` with the canonical sample draft; `.active` is reached
/// programmatically (e.g. from a "view current hold" deep link) until
/// the persistence layer wires real data.
public enum VacationHoldSeed: Sendable, Hashable {
    case scheduling
    case active
}

@Observable
@MainActor
public final class VacationHoldViewModel {
    public private(set) var mode: VacationHoldMode

    private let onBack: @MainActor () -> Void
    private let onEditForwarding: @MainActor () -> Void
    private let onEditEmergency: @MainActor () -> Void
    private let onPickFromDate: @MainActor () -> Void
    private let onPickToDate: @MainActor () -> Void

    public init(
        seed: VacationHoldSeed = .scheduling,
        onBack: @escaping @MainActor () -> Void = {},
        onEditForwarding: @escaping @MainActor () -> Void = {},
        onEditEmergency: @escaping @MainActor () -> Void = {},
        onPickFromDate: @escaping @MainActor () -> Void = {},
        onPickToDate: @escaping @MainActor () -> Void = {}
    ) {
        switch seed {
        case .scheduling:
            mode = .scheduling(VacationHoldSampleData.schedulingDraft)
        case .active:
            mode = .active(VacationHoldSampleData.activeHold)
        }
        self.onBack = onBack
        self.onEditForwarding = onEditForwarding
        self.onEditEmergency = onEditEmergency
        self.onPickFromDate = onPickFromDate
        self.onPickToDate = onPickToDate
    }

    // MARK: - Trailing-action labels

    /// Top-bar trailing label. `Save` in scheduling, `End hold` in active.
    public var trailingActionLabel: String {
        switch mode {
        case .scheduling: "Save"
        case .active: "End hold"
        }
    }

    /// Scheduling mode disables Save when the draft is invalid. Active
    /// mode always renders the End-hold button enabled (in neutral tone)
    /// so the user can end the hold at any time.
    public var trailingActionEnabled: Bool {
        switch mode {
        case let .scheduling(draft): draft.isValid
        case .active: true
        }
    }

    /// `primary600` in scheduling for the Save CTA; neutral `appText`
    /// in the active variant for End hold.
    public var trailingActionTint: Color {
        switch mode {
        case .scheduling: trailingActionEnabled ? Theme.Color.primary600 : Theme.Color.appTextMuted
        case .active: Theme.Color.appText
        }
    }

    // MARK: - View intents

    public func tapBack() {
        onBack()
    }

    public func tapTrailingAction() {
        switch mode {
        case .scheduling:
            // Persistence stub — backend endpoint lands later. For now,
            // mutate the local mode so QA + previews can verify the
            // "Save flips to active" handoff.
            mode = .active(VacationHoldSampleData.activeHold)
        case .active:
            mode = .scheduling(VacationHoldSampleData.schedulingDraft)
        }
    }

    public func tapFromDate() {
        onPickFromDate()
    }

    public func tapToDate() {
        onPickToDate()
    }

    public func tapForwarding() {
        onEditForwarding()
    }

    public func tapEmergency() {
        onEditEmergency()
    }

    /// Toggle a scope row in the scheduling variant. Locked rows are
    /// ignored (civic notices stay locked on `.always-on`).
    public func toggleScope(_ kind: VacationHoldScope.Kind, isOn: Bool) {
        guard case var .scheduling(draft) = mode else { return }
        draft.scopes = draft.scopes.map { scope in
            guard scope.kind == kind, !scope.isLocked else { return scope }
            return VacationHoldScope(
                kind: scope.kind,
                label: scope.label,
                sub: scope.sub,
                isOn: isOn,
                isLocked: scope.isLocked
            )
        }
        mode = .scheduling(draft)
    }

    /// Toggle forwarding on/off.
    public func toggleForwarding(_ isOn: Bool) {
        guard case var .scheduling(draft) = mode else { return }
        draft.forwardingEnabled = isOn
        mode = .scheduling(draft)
    }

    /// Replace the `fromDate` (callable from a date-picker sheet host).
    /// Clamps `toDate` so the span stays at least 1 day.
    public func setFromDate(_ newValue: Date) {
        guard case var .scheduling(draft) = mode else { return }
        draft.fromDate = newValue
        if draft.toDate < newValue {
            draft.toDate = newValue
        }
        mode = .scheduling(draft)
    }

    /// Replace the `toDate`. Clamps to `fromDate` if a user picks a date
    /// earlier than the start.
    public func setToDate(_ newValue: Date) {
        guard case var .scheduling(draft) = mode else { return }
        draft.toDate = max(newValue, draft.fromDate)
        mode = .scheduling(draft)
    }
}
