//
//  StampsViewModel.swift
//  Pantopus
//
//  A17.11 — Stamps (postage wallet) view-model. Drives the four render
//  states (loading → loaded / empty / error) for the screen.
//
//  The web `api.mailboxV2P3.getStamps()` route models an achievement
//  *gallery*, not this postage *wallet*, and has no native client
//  equivalent — so `load()` projects the deterministic
//  `StampsSampleData` fixtures (the same no-backend pattern as
//  `VacationHold` / `MailDay`). When a wallet endpoint lands, only
//  `projectedState()` changes.
//
//  Buy actions are stubs per the brief (no Stripe): "Buy more" refills
//  the featured book in local state; "Buy stamps" / "Get book" on the
//  empty state acquires the starter book and flips to the populated
//  wallet.
//

import Foundation
import Observation

/// Initial seed for the screen — which frame the route lands on.
public enum StampsSeed: Sendable, Hashable {
    case populated
    case empty
}

@Observable
@MainActor
public final class StampsViewModel {
    public private(set) var state: StampsState = .loading

    private let seed: StampsSeed
    private let seededContent: StampsContent?
    private let onBack: @MainActor () -> Void

    /// - Parameters:
    ///   - seed: Which frame to project. `.populated` is the default
    ///     route landing; `.empty` is reached when the wallet is bare.
    ///   - content: Optional override (tests / previews) for the
    ///     populated projection.
    ///   - onBack: Pops the screen.
    public init(
        seed: StampsSeed = .populated,
        content: StampsContent? = nil,
        onBack: @escaping @MainActor () -> Void = {}
    ) {
        self.seed = seed
        seededContent = content
        self.onBack = onBack
    }

    // MARK: - Lifecycle

    public func load() async {
        state = projectedState()
    }

    public func refresh() async {
        state = projectedState()
    }

    private func projectedState() -> StampsState {
        switch seed {
        case .populated:
            .loaded(seededContent ?? StampsSampleData.populated)
        case .empty:
            .empty(StampsSampleData.empty)
        }
    }

    // MARK: - Intents

    public func tapBack() {
        onBack()
    }

    /// "Buy more stamps" (populated dock). Stub: refills the featured
    /// book to full in local state — no purchase flow (out of scope).
    public func buyMore() {
        guard case let .loaded(content) = state else { return }
        var refilled = content
        refilled.book = StampBook(
            series: content.book.series,
            total: content.book.total,
            used: 0,
            purchasedLabel: content.book.purchasedLabel,
            validityLabel: content.book.validityLabel
        )
        state = .loaded(refilled)
    }

    /// "Buy stamps" / "Get book" (empty state). Stub: acquires the
    /// starter book and flips to the populated wallet — no purchase flow.
    public func purchaseStarterBook() {
        state = .loaded(seededContent ?? StampsSampleData.populated)
    }
}
