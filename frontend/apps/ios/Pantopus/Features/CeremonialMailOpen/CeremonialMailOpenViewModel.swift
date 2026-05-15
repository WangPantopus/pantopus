//
//  CeremonialMailOpenViewModel.swift
//  Pantopus
//
//  Fetches the mail item via `GET /api/mailbox/v2/item/:id`, projects
//  it into a `CeremonialMailLetter`, owns the four-phase progression
//  (sealed → breaking → open → replying), and exposes a play/pause
//  hook for the optional voice postscript.
//

import Foundation
import Observation

@Observable
@MainActor
public final class CeremonialMailOpenViewModel {
    public private(set) var state: CeremonialMailOpenState = .loading
    public private(set) var isVoicePlaying: Bool = false

    private let mailId: String
    private let api: APIClient

    public init(mailId: String, api: APIClient = .shared) {
        self.mailId = mailId
        self.api = api
    }

    public func load() async {
        state = .loading
        do {
            let response: MailboxV2ItemResponse =
                try await api.request(MailboxV2Endpoints.item(mailId: mailId))
            let letter = Self.project(response: response, mailId: mailId)
            state = .loaded(letter, phase: .sealed)
        } catch {
            let message = (error as? APIError)?.errorDescription ?? "Couldn't load this letter."
            state = .error(message: message)
        }
    }

    public var phase: CeremonialMailPhase {
        if case let .loaded(_, phase) = state { return phase }
        return .sealed
    }

    public var letter: CeremonialMailLetter? {
        if case let .loaded(letter, _) = state { return letter }
        return nil
    }

    /// Step the seal-break ceremony forward. View calls this once
    /// the user taps the envelope; the breaking phase animates and
    /// then transitions to `.open` after `~600ms`.
    public func startBreakingSeal() async {
        guard case let .loaded(letter, phase) = state, phase == .sealed else { return }
        state = .loaded(letter, phase: .breaking)
        try? await Task.sleep(nanoseconds: 600_000_000)
        if case .loaded = state {
            state = .loaded(letter, phase: .open)
        }
    }

    public func openImmediately() {
        guard case let .loaded(letter, _) = state else { return }
        state = .loaded(letter, phase: .open)
    }

    public func enterReplying() {
        guard case let .loaded(letter, _) = state else { return }
        state = .loaded(letter, phase: .replying)
    }

    public func resetToOpen() {
        guard case let .loaded(letter, _) = state else { return }
        state = .loaded(letter, phase: .open)
    }

    public func toggleVoicePlayback() {
        isVoicePlaying.toggle()
    }

    public func stopVoicePlayback() {
        isVoicePlaying = false
    }

    // MARK: - Projection

    static func project(
        response: MailboxV2ItemResponse,
        mailId: String
    ) -> CeremonialMailLetter {
        let item = response.mail
        let payload = item.objectPayload?.objectValue ?? [:]
        let stationery = CeremonialMailStationeryTone(
            wire: payload["stationeryTheme"]?.stringValue
        )
        let ink = CeremonialMailInkTone(wire: payload["inkSelection"]?.stringValue)
        let seal = CeremonialMailSealTone(wire: payload["sealChoice"]?.stringValue)
        let voiceUri = payload["voicePostscriptUri"]?.stringValue
        let body = item.base.content ?? ""
        let paragraphs = body
            .components(separatedBy: "\n\n")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        let sender = CeremonialSenderCard(
            displayName: item.senderDisplay,
            handle: item.sender?.username,
            trustLabel: trustLabel(for: item.senderTrust),
            avatarUrl: nil
        )
        let category = "letter"
        return CeremonialMailLetter(
            mailId: mailId,
            sender: sender,
            category: category,
            subject: item.base.subject ?? "A letter",
            bodyParagraphs: paragraphs.isEmpty ? [body] : paragraphs,
            stationery: stationery,
            ink: ink,
            seal: seal,
            voicePostscriptUri: voiceUri,
            receivedAt: item.base.createdAt,
            outcomeCtas: CeremonialMailLetter.defaultOutcomeCtas()
        )
    }

    static func trustLabel(for raw: String?) -> String? {
        switch raw {
        case "verified_gov", "verified_utility", "verified_business": "Verified"
        case "pantopus_user": "Pantopus friend"
        case "partial": "Partial trust"
        case "none", nil: nil
        default: nil
        }
    }
}

// MARK: - JSONValue accessors

private extension JSONValue {
    var objectValue: [String: JSONValue]? {
        if case let .object(value) = self { return value }
        return nil
    }

    var stringValue: String? {
        if case let .string(value) = self { return value }
        return nil
    }
}
