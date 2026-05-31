//
//  EarnViewModel.swift
//  Pantopus
//
//  A10.11 — backs `EarnView`. There's no payout / earnings backend wired
//  yet (this batch ships the visual surface; the live network swap lands
//  with the Stripe Connect integration), so the VM is seeded with
//  deterministic `EarnSampleData`. A non-nil `content` selects the
//  active-earner frame; a nil `content` selects the empty new-earner
//  frame (no hero, gated rows, add-payout nudge). State machine matches
//  the doc's four-state rule: loading / populated / empty / error.
//

import Foundation
import Observation

@Observable
@MainActor
public final class EarnViewModel {
    public enum State: Equatable, Sendable {
        case loading
        case populated(EarnContent)
        /// New earner — no earnings yet. Carries only the shared
        /// `Ways to earn` rows; every other slot is a fixed gated /
        /// nudge treatment owned by the view.
        case empty(waysToEarn: [EarnWayToEarn])
        case error(message: String)
    }

    public private(set) var state: State = .loading

    /// Non-nil → active earner (populated); nil → new earner (empty).
    private let content: EarnContent?
    private let waysToEarn: [EarnWayToEarn]
    /// When a caller seeds an explicit state (previews / tests for the
    /// loading + error chrome), `load()` is a no-op so the seed sticks.
    private let seeded: Bool

    /// Default — active earner. Pass `content: nil` for the empty
    /// new-earner frame.
    public init(
        content: EarnContent? = EarnSampleData.populated,
        waysToEarn: [EarnWayToEarn] = EarnSampleData.waysToEarn
    ) {
        self.content = content
        self.waysToEarn = waysToEarn
        seeded = false
    }

    /// Seed an explicit state — used by previews and tests to exercise
    /// the loading + error chrome without a network layer.
    public init(state: State) {
        content = nil
        waysToEarn = EarnSampleData.waysToEarn
        self.state = state
        seeded = true
    }

    public func load() async {
        guard !seeded else { return }
        state = content.map { .populated($0) } ?? .empty(waysToEarn: waysToEarn)
    }

    public func refresh() async {
        await load()
    }
}
