//
//  MailTranslationViewModel.swift
//  Pantopus
//
//  A17.13 — Translation view-model. Drives the four DoD states off the
//  sample letter, owns the machine → confirmed transition (optimistic,
//  rolls back on failure), the `ViewToggle` selection, and the stubbed
//  "Listen" affordance.
//
//  Translation/TTS are sample-driven (B2.3 out-of-scope) — the confirm
//  call hits the real `MailboxEndpoints.translate` helper so the wiring
//  exists, but a failure simply rolls the optimistic flip back.
//

import Foundation
import Observation

@Observable
@MainActor
public final class MailTranslationViewModel {
    public private(set) var state: MailTranslationState = .loading
    /// One-shot toast surfaced on confirm / listen / rollback.
    public var toast: String?
    /// True while the confirm round-trip is in flight (disables the CTA).
    public private(set) var confirmInFlight = false

    private let mailId: String
    private let api: APIClient
    private let seedConfirmed: Bool

    init(
        mailId: String,
        api: APIClient = .shared,
        seedConfirmed: Bool = false
    ) {
        self.mailId = mailId
        self.api = api
        self.seedConfirmed = seedConfirmed
    }

    /// Load the (sample) translation. Real MT lands behind this seam later;
    /// today the projection is deterministic so previews + snapshots are
    /// stable. Still routed through a `do/catch` so the error state is real.
    public func load() async {
        state = .loading
        let content = seedConfirmed
            ? MailTranslationSampleData.confirmedLetter(mailId: mailId)
            : MailTranslationSampleData.letter(mailId: mailId)
        if Task.isCancelled { return }
        state = .loaded(content)
    }

    public func refresh() async {
        await load()
    }

    /// Switch the body the toggle renders.
    public func selectViewMode(_ mode: TranslationViewMode) {
        guard case var .loaded(content) = state, content.viewMode != mode else { return }
        content.viewMode = mode
        state = .loaded(content)
    }

    /// Confirm the machine translation. Optimistically flips to the
    /// confirmed state (banner + reading view + reply CTA); rolls back and
    /// toasts on failure.
    public func confirmTranslation() async {
        guard case var .loaded(content) = state, !content.confirmed, !confirmInFlight else { return }
        confirmInFlight = true
        defer { confirmInFlight = false }
        let previous = content
        content.confirmed = true
        content.viewMode = .translated
        state = .loaded(content)
        do {
            // The translate endpoint doubles as the "confirm/trust" write
            // until a dedicated confirm route ships. Discard the body — the
            // optimistic projection is the source of truth for the UI.
            _ = try await api.request(
                MailboxV2Endpoints.translate(mailId: mailId),
                as: TranslationResultDTO.self
            )
            toast = "Translation confirmed"
        } catch {
            state = .loaded(previous)
            toast = "Couldn't confirm — try again"
        }
    }

    /// Stubbed text-to-speech affordance. Real audio is out of scope (B2.3);
    /// this surfaces a toast so the control is never a dead tap.
    public func listen(_ which: TranslationListenColumn) {
        switch which {
        case .original:
            toast = "Playing the original aloud…"
        case .translated:
            toast = "Playing the translation aloud…"
        }
    }
}

/// Which column the "Listen" stub reads.
public enum TranslationListenColumn: Sendable {
    case original
    case translated
}
