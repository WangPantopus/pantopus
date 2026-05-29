//
//  MailDayViewModel.swift
//  Pantopus
//
//  A13.16 — Backs the My Mail Day editor. Two variants seeded by the
//  caller:
//
//    `.populated` — mid-afternoon triage. The latest reviewed row pulses
//      a 5-second undo countdown; `tickUndo()` (driven by `MailDayView`)
//      decrements the seconds and clears the chip when it hits 0.
//    `.empty` — no scans yet today; the hero + recap + setup-nudges
//      render off the same `MailDayContent`.
//
//  Backend has been removed from the repo, so `load()` projects a
//  deterministic fixture (`MailDaySampleData`). Action handlers
//  (`route` / `junk` / `undo`) flip in-memory state — they don't write
//  to the wire — and exist so the screen feels real in previews.
//

import Foundation
import Observation

public enum MailDayVariant: String, Sendable, Hashable {
    case populated
    case empty
}

@Observable
@MainActor
public final class MailDayViewModel {
    public private(set) var state: MailDayState = .loading

    public var variant: MailDayVariant
    private let seededContent: MailDayContent?
    private let onScanRequested: @MainActor () -> Void

    /// - Parameters:
    ///   - variant: Which fixture to project. Defaults to `.populated`.
    ///   - content: Optional seed (tests / previews) overriding the
    ///     sample fixture for this variant.
    ///   - onScanRequested: Invoked when the user taps any Scan CTA
    ///     (top scan-more card or empty-hero primary). Out of scope to
    ///     wire to the real scanner here — the host hands a closure.
    public init(
        variant: MailDayVariant = .populated,
        content: MailDayContent? = nil,
        onScanRequested: @escaping @MainActor () -> Void = {}
    ) {
        self.variant = variant
        seededContent = content
        self.onScanRequested = onScanRequested
    }

    // MARK: - Lifecycle

    public func load() async {
        state = projectedState()
    }

    public func refresh() async {
        state = projectedState()
    }

    private func projectedState() -> MailDayState {
        switch variant {
        case .populated:
            .populated(seededContent ?? MailDaySampleData.populated)
        case .empty:
            .empty(seededContent ?? MailDaySampleData.empty)
        }
    }

    // MARK: - Derived

    /// Total pieces (reviewed + unreviewed) — drives the ProgressRing
    /// denominator + the "Finish day · N pieces" CTA label.
    public var total: Int {
        guard case let .populated(content) = state else { return 0 }
        return content.unreviewed.count + content.reviewed.count
    }

    /// Pieces with a decision (any action) — ProgressRing numerator.
    public var done: Int {
        guard case let .populated(content) = state else { return 0 }
        return content.reviewed.count
    }

    /// Pieces still needing a call. Drives the "Finish day" disabled
    /// state.
    public var remaining: Int {
        guard case let .populated(content) = state else { return 0 }
        return content.unreviewed.count
    }

    /// Routed-only subset for the FinishDay summary line.
    public var routedCount: Int {
        guard case let .populated(content) = state else { return 0 }
        return content.reviewed.filter { $0.action == .routed }.count
    }

    public var junkedCount: Int {
        guard case let .populated(content) = state else { return 0 }
        return content.reviewed.filter { $0.action == .junked }.count
    }

    public var returnedCount: Int {
        guard case let .populated(content) = state else { return 0 }
        return content.reviewed.filter { $0.action == .returned }.count
    }

    /// `true` once every piece has a decision and FinishDay can fire.
    public var canFinishDay: Bool {
        guard case .populated = state else { return false }
        return remaining == 0 && total > 0
    }

    // MARK: - Actions

    /// Out-of-scope per the task — open the scanner flow.
    public func requestScan() {
        onScanRequested()
    }

    /// Decrement the undo countdown on the latest reviewed row. When it
    /// hits 0 the chip clears and the row reads as a normal reviewed
    /// entry. Idempotent on zero / nil.
    public func tickUndo() {
        guard case let .populated(content) = state else { return }
        var updated = content.reviewed
        guard let firstIndex = updated.firstIndex(where: { ($0.undoCountdown ?? 0) > 0 }) else {
            return
        }
        let current = updated[firstIndex]
        let next = (current.undoCountdown ?? 0) - 1
        updated[firstIndex] = ReviewedMailDayItem(
            id: current.id,
            kind: current.kind,
            label: current.label,
            action: current.action,
            routedTo: current.routedTo,
            routedTint: current.routedTint,
            whenLabel: current.whenLabel,
            undoCountdown: next > 0 ? next : nil
        )
        state = .populated(
            MailDayContent(
                dateLabel: content.dateLabel,
                streakDays: content.streakDays,
                lastScanLabel: content.lastScanLabel,
                unreviewed: content.unreviewed,
                reviewed: updated,
                yesterdayRecap: content.yesterdayRecap,
                setupNudges: content.setupNudges
            )
        )
    }

    /// Move an unreviewed piece into the reviewed list as `.routed` —
    /// the AI suggestion is treated as accepted.
    public func acceptSuggestion(for itemId: String) {
        guard case let .populated(content) = state else { return }
        guard let index = content.unreviewed.firstIndex(where: { $0.id == itemId }) else { return }
        let item = content.unreviewed[index]
        var unreviewed = content.unreviewed
        unreviewed.remove(at: index)
        let firstName = item.suggestedName.split(separator: " ").first.map(String.init) ?? item.suggestedName
        let reviewed = [
            ReviewedMailDayItem(
                id: item.id,
                kind: item.kind,
                label: item.label,
                action: .routed,
                routedTo: firstName,
                routedTint: item.suggestedAvatar == .personalSky ? .personPrimary : .householdHome,
                whenLabel: "just now",
                undoCountdown: 5
            )
        ] + clearedReviewed(content.reviewed)
        state = .populated(
            MailDayContent(
                dateLabel: content.dateLabel,
                streakDays: content.streakDays,
                lastScanLabel: content.lastScanLabel,
                unreviewed: unreviewed,
                reviewed: reviewed,
                yesterdayRecap: content.yesterdayRecap,
                setupNudges: content.setupNudges
            )
        )
    }

    /// Clear `undoCountdown` from every prior row so only the latest
    /// action shows the pulse — matches the JSX's single-row treatment.
    private func clearedReviewed(_ rows: [ReviewedMailDayItem]) -> [ReviewedMailDayItem] {
        rows.map { row in
            ReviewedMailDayItem(
                id: row.id,
                kind: row.kind,
                label: row.label,
                action: row.action,
                routedTo: row.routedTo,
                routedTint: row.routedTint,
                whenLabel: row.whenLabel,
                undoCountdown: nil
            )
        }
    }
}
