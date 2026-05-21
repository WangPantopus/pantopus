//
//  EditPersonaViewModel.swift
//  Pantopus
//
//  A13.12 — Backs the creator-facing Edit persona editor. The backend has
//  been removed from the repo, so `load()` projects a deterministic fixture
//  (`EditPersonaSampleData`) instead of fetching. The `variant` selects the
//  LIVE (published) vs. SETUP (mid-setup draft) frame; tests / previews can
//  also seed `content` directly.
//

import Foundation
import Observation

@Observable
@MainActor
public final class EditPersonaViewModel {
    public private(set) var state: EditPersonaState = .loading

    private let personaId: String
    private let variant: EditPersonaVariant
    private let seededContent: EditPersonaContent?

    /// - Parameters:
    ///   - personaId: Canonical route payload — the persona being edited.
    ///   - variant: Which frame to project (defaults to `.live`).
    ///   - content: Optional seed (tests / previews) overriding the sample.
    public init(
        personaId: String,
        variant: EditPersonaVariant = .live,
        content: EditPersonaContent? = nil
    ) {
        self.personaId = personaId
        self.variant = variant
        seededContent = content
    }

    public func load() async {
        state = .loading
        switch variant {
        case .live:
            state = .live(seededContent ?? EditPersonaSampleData.live)
        case .setup:
            state = .setup(
                seededContent ?? EditPersonaSampleData.setup,
                stepsDone: EditPersonaSampleData.setupStepsDone,
                stepsTotal: EditPersonaSampleData.setupStepsTotal
            )
        }
    }
}
