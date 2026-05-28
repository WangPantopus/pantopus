//
//  WalletViewModel.swift
//  Pantopus
//
//  A10.10 — backs `WalletView`. There's no Stripe Connect backend
//  wired yet (P3.2 ships the visual surface; the live network swap
//  lands with the Connect integration), so the VM is seeded with
//  deterministic `WalletSampleData` and `content.isOnHold` selects
//  populated vs. hold. State machine matches the doc's four-state
//  rule: loading / populated / hold / error.
//

import Foundation
import Observation

@Observable
@MainActor
public final class WalletViewModel {
    public enum State: Equatable, Sendable {
        case loading
        case populated(WalletContent)
        case hold(WalletContent)
        case error(message: String)
    }

    public private(set) var state: State = .loading

    private let content: WalletContent
    /// When a caller seeds an explicit state (previews / tests for the
    /// loading + error chrome), `load()` is a no-op so the seed sticks.
    private let seeded: Bool

    public init(content: WalletContent = WalletSampleData.populated) {
        self.content = content
        seeded = false
    }

    /// Seed an explicit state — used by previews and tests to exercise
    /// the loading + error chrome without a network layer.
    public init(state: State, content: WalletContent = WalletSampleData.populated) {
        self.content = content
        self.state = state
        seeded = true
    }

    public func load() async {
        guard !seeded else { return }
        state = content.isOnHold ? .hold(content) : .populated(content)
    }

    public func refresh() async {
        await load()
    }
}
