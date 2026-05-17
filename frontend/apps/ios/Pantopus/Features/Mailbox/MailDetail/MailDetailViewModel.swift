//
//  MailDetailViewModel.swift
//  Pantopus
//
//  T6.5b (P20) — Drives the **generic A17.1 mail item detail** screen.
//  Sits on the shared `MailItemDetailShell` (P19) and projects the
//  `GET /api/mailbox/:id` payload into the shell's typed slots:
//
//    - top bar: back / eyebrow trust dot / overflow (Forward, Archive,
//      Mark unread, Delete, Report)
//    - hero: category accent + sender overline + title + excerpt
//    - aiElf: when the DTO carries an `ai_summary` (V2 surface — falls
//      back to nil for V1 items so the shell skips the strip)
//    - keyFacts: derived from `created_at`, `expires_at`, mail type,
//      and the sender's business name when present
//    - body: rich-text `mail.content` rendered as paragraphs
//    - attachments: when `mail.attachments` is non-empty
//    - sender card: always
//    - actions: Acknowledge (when `ack_required` and not yet acked) +
//      Forward / Archive / Reply secondary tiles
//
//  P21-P23 will replace the body / actions for the package / coupon /
//  booklet / certified variants by composing the same shell with their
//  variant-specific slot views. The generic VM here owns the
//  "everything else" rendering.
//

import Foundation
import Observation

// swiftlint:disable type_body_length

/// Lifecycle state for the generic mail detail screen.
@MainActor
public enum MailDetailState: Sendable {
    case loading
    case loaded(MailDetailContent)
    case error(message: String)
}

/// Pure projection of the backend mail item into the A17 shell slots.
public struct MailDetailContent: Sendable {
    public let mailId: String
    public let category: MailItemCategory
    public let trust: MailTrust
    public let detailTrust: MailDetailTrust
    public let senderDisplayName: String
    public let senderMeta: String?
    public let senderInitials: String
    public let senderUserId: String?
    public let title: String
    public let excerpt: String?
    public let createdAtLabel: String?
    public let expiresAtLabel: String?
    public let bodyParagraphs: [String]
    public let attachments: [String]
    public let aiSummary: String?
    public let ackRequired: Bool
    public let isAcknowledged: Bool

    /// Build a `KeyFactRow` list from the projected fields. Variants
    /// extend this in P21-P23; the generic surface keeps it minimal.
    public func keyFacts() -> [MailDetailKeyFact] {
        var rows: [MailDetailKeyFact] = []
        if let createdAtLabel {
            rows.append(MailDetailKeyFact(icon: .calendar, label: "Received", value: createdAtLabel))
        }
        if let expiresAtLabel {
            rows.append(MailDetailKeyFact(icon: .clock, label: "Expires", value: expiresAtLabel))
        }
        if let senderMeta {
            rows.append(MailDetailKeyFact(icon: .briefcase, label: "From", value: senderMeta))
        }
        rows.append(
            MailDetailKeyFact(icon: category.icon, label: "Category", value: category.label)
        )
        return rows
    }
}

/// Lightweight key/value/icon triple for the generic detail's key
/// facts panel. Variants can opt-in to richer row types later.
public struct MailDetailKeyFact: Identifiable, Sendable, Hashable {
    public let id: String
    public let icon: PantopusIcon
    public let label: String
    public let value: String

    public init(id: String = UUID().uuidString, icon: PantopusIcon, label: String, value: String) {
        self.id = id
        self.icon = icon
        self.label = label
        self.value = value
    }
}

@Observable
@MainActor
public final class MailDetailViewModel {
    public private(set) var state: MailDetailState = .loading
    /// Transient banner; the view clears it after display.
    public var toast: String?
    public private(set) var ackInFlight: Bool = false

    private let mailId: String
    private let api: APIClient
    private let now: @Sendable () -> Date

    public init(
        mailId: String,
        api: APIClient = .shared,
        now: @escaping @Sendable () -> Date = Date.init
    ) {
        self.mailId = mailId
        self.api = api
        self.now = now
    }

    // MARK: - Lifecycle

    public func load() async {
        if case .loaded = state {} else { state = .loading }
        await fetch()
    }

    public func refresh() async {
        await fetch()
    }

    private func fetch() async {
        do {
            let response: MailDetailResponse = try await api.request(
                MailboxEndpoints.detail(mailId: mailId)
            )
            state = .loaded(Self.project(detail: response.mail, now: now()))
        } catch {
            state = .error(
                message: (error as? APIError)?.errorDescription ?? "Couldn't load this item."
            )
        }
    }

    // MARK: - Mutations

    /// Acknowledge the mail item. Optimistic — flips the local
    /// `isAcknowledged` state then rolls back on transport failure.
    public func acknowledge() async {
        guard case let .loaded(content) = state, !ackInFlight else { return }
        ackInFlight = true
        defer { ackInFlight = false }
        let previous = content
        let optimistic = MailDetailContent.replacingAck(content, with: true)
        state = .loaded(optimistic)
        do {
            let _: AckResponse = try await api.request(
                MailboxEndpoints.acknowledge(mailId: mailId)
            )
            toast = "Acknowledged"
        } catch {
            state = .loaded(previous)
            toast = (error as? APIError)?.errorDescription ?? "Couldn't acknowledge"
        }
    }

    // MARK: - Pure projection (exposed for tests)

    /// Map the backend `MailDetailResponse.MailDetail` envelope to the
    /// generic A17.1 content. Static so the test suite can exercise it
    /// without standing the VM up.
    public static func project(
        detail: MailDetailResponse.MailDetail,
        now: Date = Date()
    ) -> MailDetailContent {
        let item = detail.item
        let category = MailItemCategory.fromRaw(item.mailType ?? item.type)
        let trust = MailTrust.fromRaw(nil)
        let senderDisplayName = detail.sender?.name
            ?? item.senderBusinessName
            ?? item.senderAddress
            ?? "Unknown sender"
        let senderMeta = detail.sender?.username.flatMap { "@\($0)" } ?? item.senderAddress
        let title = item.displayTitle ?? item.subject ?? "Mail"
        let excerpt = item.previewText
        let createdAtLabel = formatLongDate(item.createdAt)
        let expiresAtLabel = formatLongDate(item.expiresAt)
        let body: [String] = {
            guard let content = item.content, !content.isEmpty else { return [] }
            // Split on blank-line boundaries for simple rich-text rendering.
            return content
                .components(separatedBy: "\n\n")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
        }()
        let attachments = item.attachments ?? []
        let aiSummary: String? = nil // V1 list/detail endpoints don't expose ai_summary yet.
        let ackRequired = item.ackRequired ?? false
        let isAcknowledged = (item.ackStatus ?? "").lowercased() == "acknowledged"
        let initials = makeInitials(from: senderDisplayName)
        return MailDetailContent(
            mailId: item.id,
            category: category,
            trust: trust,
            detailTrust: trust.detailTrust,
            senderDisplayName: senderDisplayName,
            senderMeta: senderMeta,
            senderInitials: initials,
            senderUserId: detail.sender?.id,
            title: title,
            excerpt: excerpt,
            createdAtLabel: createdAtLabel,
            expiresAtLabel: expiresAtLabel,
            bodyParagraphs: body,
            attachments: attachments,
            aiSummary: aiSummary,
            ackRequired: ackRequired,
            isAcknowledged: isAcknowledged
        )
    }

    // MARK: - Helpers

    static func makeInitials(from name: String) -> String {
        let parts = name.split(separator: " ").prefix(2)
        let result = parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
        return result.isEmpty ? "M" : result
    }

    static func formatLongDate(_ iso: String?) -> String? {
        guard let iso, !iso.isEmpty else { return nil }
        let isoFull = ISO8601DateFormatter()
        isoFull.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let plain = ISO8601DateFormatter()
        plain.formatOptions = [.withInternetDateTime]
        guard let date = isoFull.date(from: iso) ?? plain.date(from: iso) else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "EEE MMM d, yyyy"
        return formatter.string(from: date)
    }
}

private extension MailDetailContent {
    /// Return a copy of `content` with `isAcknowledged` flipped to the
    /// supplied value. Used by the optimistic acknowledge mutation.
    static func replacingAck(_ content: MailDetailContent, with value: Bool) -> MailDetailContent {
        MailDetailContent(
            mailId: content.mailId,
            category: content.category,
            trust: content.trust,
            detailTrust: content.detailTrust,
            senderDisplayName: content.senderDisplayName,
            senderMeta: content.senderMeta,
            senderInitials: content.senderInitials,
            senderUserId: content.senderUserId,
            title: content.title,
            excerpt: content.excerpt,
            createdAtLabel: content.createdAtLabel,
            expiresAtLabel: content.expiresAtLabel,
            bodyParagraphs: content.bodyParagraphs,
            attachments: content.attachments,
            aiSummary: content.aiSummary,
            ackRequired: content.ackRequired,
            isAcknowledged: value
        )
    }
}
