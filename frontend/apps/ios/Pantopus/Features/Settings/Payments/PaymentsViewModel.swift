//
//  PaymentsViewModel.swift
//  Pantopus
//
//  Projects the A14.6 Payments screen into render state. P5.2 ships
//  the screen against sample fixtures — live Stripe Connect onboarding
//  and the card-add bottom sheet are out of scope (deep-link out to
//  Stripe-hosted flows when wired). The seed selector lets callers
//  preview both frames without piggy-backing on an env flag.
//

import Foundation
import Observation

/// Which sample frame to load when the screen mounts. The screen is
/// currently fixture-driven (no live Stripe Connect yet); flipping
/// the seed swaps in the empty-account fixture so the disabled-state
/// chrome remains exercised at preview/snapshot time.
public enum PaymentsSeed: Sendable, Hashable {
    case populated
    case empty
}

@Observable
@MainActor
public final class PaymentsViewModel {
    public private(set) var state: PaymentsState = .loading

    private let seed: PaymentsSeed

    public init(seed: PaymentsSeed = .populated) {
        self.seed = seed
    }

    public func load() async {
        state = .loading
        let loaded: PaymentsLoaded = switch seed {
        case .populated: PaymentsSampleData.populated
        case .empty: PaymentsSampleData.empty
        }
        state = .loaded(loaded)
    }

    public func refresh() async {
        await load()
    }

    /// Row taps are no-ops today — Stripe Connect deep-link and the
    /// card-add bottom sheet are flagged out of scope (P5.2 OUT OF
    /// SCOPE). The hook stays so the view can stay typed.
    public func tapRow(_: String) async {}
    public func tapAddMethod() async {}
    public func tapCloseAccount() async {}
}
