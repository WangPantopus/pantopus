//
//  UnboxingViewModel.swift
//  Pantopus
//
//  A17.14 — Backs the Unboxing scan-capture flow. Two phases seeded by the
//  caller:
//
//    `.capture` — live classified frame. The shutter (`capture()`) appends
//      a labeled shot to the filmstrip; `confirm()` files the item and
//      advances to `.filed`.
//    `.filed`   — confirmed summary. `undo()` returns to `.capture`;
//      `scanNext()` re-arms the capture sequence and hands off to the host.
//
//  Real OCR / classification / vault upload are out of scope (B2.4) — the
//  VM projects the deterministic `UnboxingSampleData` fixture and the
//  action handlers flip in-memory state so the screen feels real in
//  previews. Nothing writes to the wire. Mirrors `UnboxingViewModel` on
//  Android.
//

import Foundation
import Observation

@Observable
@MainActor
public final class UnboxingViewModel {
    public private(set) var state: UnboxingScreenState
    public private(set) var phase: UnboxingPhase

    private var content: UnboxingContent
    private let onScanNext: @MainActor () -> Void
    private let onOpenDrawer: @MainActor () -> Void

    /// - Parameters:
    ///   - phase: Which frame to project. Defaults to `.capture`.
    ///   - content: Optional seed (tests / previews / snapshots) overriding
    ///     the sample fixture.
    ///   - onScanNext: Invoked when the user taps "Scan the next item" in the
    ///     filed frame. The host re-enters the capture flow.
    ///   - onOpenDrawer: Invoked when the user taps "View in Home drawer".
    ///     Out of scope to wire to the real vault here — the host hands a
    ///     closure.
    public init(
        phase: UnboxingPhase = .capture,
        content: UnboxingContent = UnboxingSampleData.content,
        onScanNext: @escaping @MainActor () -> Void = {},
        onOpenDrawer: @escaping @MainActor () -> Void = {}
    ) {
        self.content = content
        self.phase = phase
        self.onScanNext = onScanNext
        self.onOpenDrawer = onOpenDrawer
        state = Self.project(phase: phase, content: content)
    }

    private static func project(phase: UnboxingPhase, content: UnboxingContent) -> UnboxingScreenState {
        switch phase {
        case .capture: .capture(content)
        case .filed: .filed(content)
        }
    }

    private func restate() {
        state = Self.project(phase: phase, content: content)
    }

    // MARK: - Lifecycle

    /// Kept for parity with the fetchable-surface pattern; the fixture is
    /// already projected in `init`, so this is a no-op re-projection.
    public func load() async {
        restate()
    }

    // MARK: - Capture

    /// Append the next labeled shot from the canonical capture sequence
    /// (cycling once all four are present) — the shutter handler. Keeps the
    /// filmstrip "appending labeled shots" without a real camera.
    public func capture() {
        let sequence = UnboxingSampleData.captureSequence
        guard !sequence.isEmpty else { return }
        let template = sequence[content.shots.count % sequence.count]
        let appended = UnboxingShot(
            id: "\(template.id)-\(content.shots.count)",
            tag: template.tag,
            label: template.label,
            isMain: false
        )
        content = content.withShots(content.shots + [appended])
        restate()
    }

    // MARK: - Filing

    /// Confirm the AI suggestion and file the item — advances to `.filed`.
    public func confirm() {
        guard phase == .capture else { return }
        phase = .filed
        restate()
    }

    /// Undo the filing (the filed-banner "Undo" chip) — back to `.capture`.
    public func undo() {
        guard phase == .filed else { return }
        phase = .capture
        restate()
    }

    /// Re-arm the capture sequence for the next item and hand off to the
    /// host ("Scan the next item").
    public func scanNext() {
        content = content.withShots(UnboxingSampleData.captureSequence)
        phase = .capture
        restate()
        onScanNext()
    }

    /// "View in Home drawer" — hands off to the host.
    public func openDrawer() {
        onOpenDrawer()
    }

    // MARK: - Derived

    public var shots: [UnboxingShot] {
        content.shots
    }
}
