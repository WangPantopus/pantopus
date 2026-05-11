//
//  DisambiguateMailFormViewModel.swift
//  Pantopus
//
//  POSTs `/api/mailbox/v2/resolve`
//  (`backend/routes/mailboxV2.js:555`). Schema:
//  `resolveRoutingSchema` (line 12) — mailId + drawer (`personal` |
//  `home` | `business`) + optional alias.
//
//  The Form design drew "Possible recipients" as a list of people with
//  avatars. The backend has no candidates endpoint and resolves into
//  one of three drawers. The form translates "drawer choice" into the
//  same radio-row UX with three rows; the optional notes field maps
//  to `aliasString` (when non-empty, sends `addAlias: true`).
//

import Foundation
import Observation

/// One of the three drawer options surfaced as a radio row.
public enum MailRecipientChoice: String, CaseIterable, Identifiable, Sendable {
    case personal
    case home
    case business

    public var id: String { rawValue }

    /// Human-readable row label.
    public var title: String {
        switch self {
        case .personal: "Just for me"
        case .home: "My home household"
        case .business: "My business inbox"
        }
    }

    /// Subtitle copy underneath the row title.
    public var subtitle: String {
        switch self {
        case .personal: "Routes to your personal drawer"
        case .home: "Routes to the shared home drawer"
        case .business: "Routes to the business team drawer"
        }
    }

    /// Identity-pillar tint used by the avatar / accent.
    public var identity: IdentityPillar {
        switch self {
        case .personal: .personal
        case .home: .home
        case .business: .business
        }
    }
}

/// Render state for the Disambiguate form.
public enum DisambiguateMailFormState: Sendable, Equatable {
    case editing
    case error(String)
}

/// ViewModel backing `DisambiguateMailFormView`.
@Observable
@MainActor
final class DisambiguateMailFormViewModel {
    private(set) var state: DisambiguateMailFormState = .editing

    /// Currently-selected recipient row.
    var selectedChoice: MailRecipientChoice?

    /// Optional alias / notes the user types under "Anything else?".
    /// When non-empty, sent as `addAlias: true, aliasString: <value>`.
    var aliasNotes: String = ""

    /// OCR'd recipient text rendered in the envelope card. Wired in by
    /// the screen from the upstream mail item.
    var ocrRecipient: String

    /// AI confidence in the OCR / routing — drives the confidence pill
    /// color and label.
    var confidence: Double

    /// Scanned-envelope image URL (nil → fall through to a placeholder).
    var envelopeImageURL: URL?

    /// Busy flag for the sticky CTA.
    private(set) var isSubmitting: Bool = false

    var toast: ToastMessage?
    /// Set true after a successful resolve so the screen can pop.
    private(set) var shouldDismiss: Bool = false

    /// True while the alias remains over the 255-char schema limit.
    var aliasError: String? {
        let trimmed = aliasNotes.trimmingCharacters(in: .whitespaces)
        if trimmed.count > 255 { return "Notes must be 255 characters or fewer." }
        return nil
    }

    /// Computed "primary action enabled" — drives the sticky CTA.
    var canSubmit: Bool {
        selectedChoice != nil && aliasError == nil && !isSubmitting
    }

    private let mailId: String
    private let api: APIClient

    init(
        mailId: String,
        ocrRecipient: String = "",
        confidence: Double = 0.0,
        envelopeImageURL: URL? = nil,
        api: APIClient = .shared
    ) {
        self.mailId = mailId
        self.ocrRecipient = ocrRecipient
        self.confidence = confidence
        self.envelopeImageURL = envelopeImageURL
        self.api = api
    }

    func select(_ choice: MailRecipientChoice) {
        selectedChoice = choice
    }

    @discardableResult
    func submit() async -> Bool {
        guard let choice = selectedChoice else {
            toast = ToastMessage(text: "Pick a destination first.", kind: .error)
            return false
        }
        if let aliasError {
            toast = ToastMessage(text: aliasError, kind: .error)
            return false
        }
        if !NetworkMonitor.shared.isOnline {
            toast = ToastMessage(
                text: "You're offline. Try again when you're back online.",
                kind: .error
            )
            return false
        }
        isSubmitting = true
        defer { isSubmitting = false }
        let trimmedAlias = aliasNotes.trimmingCharacters(in: .whitespacesAndNewlines)
        let request = ResolveRoutingRequest(
            mailId: mailId,
            drawer: choice.rawValue,
            addAlias: trimmedAlias.isEmpty ? nil : true,
            aliasString: trimmedAlias.isEmpty ? nil : trimmedAlias
        )
        do {
            let response: ResolveRoutingResponse = try await api.request(
                MailboxV2Endpoints.resolve(request)
            )
            toast = ToastMessage(
                text: "Mail routed to \(choice.title.lowercased()).",
                kind: .success
            )
            // Use the backend-confirmed drawer in case the response
            // diverges from what we sent.
            _ = response.drawer
            shouldDismiss = true
            return true
        } catch {
            toast = ToastMessage(
                text: (error as? APIError)?.errorDescription ?? "Couldn't route this mail.",
                kind: .error
            )
            return false
        }
    }

    func acknowledgeDismiss() {
        shouldDismiss = false
    }
}
