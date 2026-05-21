//
//  MembershipDetailViewModel.swift
//  Pantopus
//
//  A10.8 — Backs the fan-side membership manage screen. The backend has
//  been removed from the repo, so `load()` projects a deterministic
//  fixture (`MembershipSampleData`) instead of fetching. The `slaMissed`
//  flag (or a seeded `content` carrying a non-nil `slaAlert`) selects the
//  refund-eligible secondary state. "Give it a week" snoozes the banner
//  in place via `dismissSLAAlert()`.
//

import Foundation
import Observation

@Observable
@MainActor
public final class MembershipDetailViewModel {
    public private(set) var state: MembershipDetailState = .loading

    private let personaId: String
    private let seededContent: MembershipDetailContent?
    private let startsSLAMissed: Bool

    /// - Parameters:
    ///   - personaId: Canonical route payload — the persona being managed.
    ///   - content: Optional seed (tests / previews) overriding the sample.
    ///   - slaMissed: When `true` and no seed is supplied, loads the
    ///     refund-eligible fixture.
    public init(
        personaId: String,
        content: MembershipDetailContent? = nil,
        slaMissed: Bool = false
    ) {
        self.personaId = personaId
        seededContent = content
        startsSLAMissed = slaMissed
    }

    public func load() async {
        state = .loading
        let content = seededContent
            ?? (startsSLAMissed ? MembershipSampleData.slaMissed : MembershipSampleData.populated)
        if content.slaAlert != nil {
            state = .slaMissed(content)
        } else {
            state = .populated(content)
        }
    }

    /// "Give it a week" — drop the SLA banner and settle back to the happy
    /// path. The gentle alternative to a refund; never a guilt-trip.
    public func dismissSLAAlert() {
        guard case let .slaMissed(content) = state else { return }
        state = .populated(content.clearingSLAAlert())
    }
}
