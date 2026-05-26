//
//  ComposeBroadcastViewModel.swift
//  Pantopus
//
//  A.7 (A22.2) — Backs the full-screen Compose Broadcast surface. The
//  editor fields (body / audience / media / schedule) are stored and
//  bound directly; `state` is derived from them plus the send `phase`
//  so it always matches the prompt's
//  `.empty / .composing / .scheduled / .sending / .error` contract.
//
//  No backend: `performSend` defaults to a no-op success and can be
//  injected (latency / failure) by the host or tests. Recent broadcasts
//  + persona + per-audience reach are seeded from
//  `ComposeBroadcastSampleData`.
//

import Foundation
import Observation

@Observable
@MainActor
public final class ComposeBroadcastViewModel {
    /// Send lifecycle, kept separate from the editable draft so the
    /// draft survives a `.sending` / `.error` round-trip.
    enum Phase: Equatable {
        case idle
        case sending
        case error(String)
    }

    public let personaId: String
    public let persona: BroadcastPersona
    public private(set) var recentBroadcasts: [RecentBroadcastContent]
    public let maxCharacterCount: Int

    public private(set) var draft: ComposeBroadcastDraft
    public private(set) var scheduledAt: Date?
    private var phase: Phase = .idle
    private var lastSavedDraft: ComposeBroadcastDraft

    private let audienceReach: [BroadcastAudience: Int]
    private let onSent: @MainActor () -> Void

    /// Stubbed network call. Default is an immediate no-op success; the
    /// host injects latency and the test suite injects a thrower /
    /// state-capture closure. `var` so it can be swapped after init.
    var performSend: @MainActor (ComposeBroadcastDraft, Date?) async throws -> Void

    public init(
        personaId: String,
        persona: BroadcastPersona,
        recentBroadcasts: [RecentBroadcastContent],
        audienceReach: [BroadcastAudience: Int] = [:],
        draft: ComposeBroadcastDraft = ComposeBroadcastDraft(),
        scheduledAt: Date? = nil,
        maxCharacterCount: Int = 1000,
        onSent: @escaping @MainActor () -> Void = {},
        performSend: @escaping @MainActor (ComposeBroadcastDraft, Date?) async throws -> Void = { _, _ in }
    ) {
        self.personaId = personaId
        self.persona = persona
        self.recentBroadcasts = recentBroadcasts
        self.audienceReach = audienceReach
        self.draft = draft
        self.scheduledAt = scheduledAt
        self.maxCharacterCount = maxCharacterCount
        self.onSent = onSent
        self.performSend = performSend
        lastSavedDraft = draft
    }

    // MARK: - Derived state

    public var state: ComposeBroadcastState {
        switch phase {
        case .sending:
            return .sending
        case let .error(message):
            return .error(message: message)
        case .idle:
            if let sendAt = scheduledAt {
                return .scheduled(draft, sendAt: sendAt)
            }
            return draft.isEmpty ? .empty : .composing(draft)
        }
    }

    public var characterCount: Int {
        draft.body.count
    }

    public var isOverLimit: Bool {
        characterCount > maxCharacterCount
    }

    public var hasRecentBroadcasts: Bool {
        !recentBroadcasts.isEmpty
    }

    public var isSending: Bool {
        phase == .sending
    }

    /// Send is allowed once there's content, we're under the character
    /// limit, and a send isn't already in flight.
    public var canSend: Bool {
        guard phase != .sending else { return false }
        guard !draft.isEmpty else { return false }
        return !isOverLimit
    }

    /// Drives the top-bar unsaved-draft chip — content exists and it
    /// diverges from the last saved snapshot.
    public var isDirty: Bool {
        !draft.isEmpty && draft != lastSavedDraft
    }

    /// CTA copy flips to first-run when the persona has never broadcast.
    public var primaryActionTitle: String {
        hasRecentBroadcasts ? "Send broadcast" : "Send your first broadcast"
    }

    public func reach(for audience: BroadcastAudience) -> Int? {
        audienceReach[audience]
    }

    // MARK: - Editing intents

    public func updateBody(_ text: String) {
        draft.body = text
        recoverFromError()
    }

    public func setAudience(_ audience: BroadcastAudience) {
        draft.audience = audience
        recoverFromError()
    }

    public func attachMedia(_ media: ComposeMediaPreview) {
        draft.media = media
        recoverFromError()
    }

    public func removeMedia() {
        draft.media = nil
        recoverFromError()
    }

    public func schedule(at date: Date) {
        scheduledAt = date
        recoverFromError()
    }

    public func sendNow() {
        scheduledAt = nil
        recoverFromError()
    }

    /// Persist the current draft. No backend — we just snapshot it so the
    /// unsaved-draft chip clears.
    public func saveDraft() {
        lastSavedDraft = draft
    }

    /// Leave the error phase and return to the live composer.
    public func retry() {
        if case .error = phase { phase = .idle }
    }

    public func send() async {
        guard canSend else { return }
        let snapshot = draft
        let sendAt = scheduledAt
        phase = .sending
        do {
            try await performSend(snapshot, sendAt)
            // Reset the composer but keep the chosen audience as the
            // default for the next broadcast.
            draft = ComposeBroadcastDraft(audience: snapshot.audience)
            scheduledAt = nil
            lastSavedDraft = draft
            phase = .idle
            onSent()
        } catch {
            phase = .error(Self.message(for: error))
        }
    }

    // MARK: - Helpers

    /// Any edit clears a prior send error so the composer is live again.
    private func recoverFromError() {
        if case .error = phase { phase = .idle }
    }

    private static func message(for error: any Error) -> String {
        if let localized = error as? any LocalizedError, let description = localized.errorDescription {
            return description
        }
        return "Couldn't send broadcast. Try again."
    }
}
