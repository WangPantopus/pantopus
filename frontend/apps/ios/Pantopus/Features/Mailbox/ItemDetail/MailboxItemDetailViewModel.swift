//
//  MailboxItemDetailViewModel.swift
//  Pantopus
//
//  Fetches `GET /api/mailbox/v2/item/:id` + `GET /api/mailbox/v2/package/:mailId`
//  (when category = package) and handles optimistic updates for
//  `POST /api/mailbox/v2/item/:id/action` and
//  `PATCH /api/mailbox/v2/package/:mailId/status`.
//

import Foundation
import Observation

/// Projection of a single mailbox item for the detail screen.
public struct MailboxItemDetailContent: Sendable {
    public let category: MailItemCategory
    public let trust: MailTrust
    public let sender: SenderBlockContent
    public let aiElf: AIElfContent?
    public let keyFacts: [KeyFactRow]
    public let timeline: [TimelineStep]
    public let packageInfo: PackageBodyContent?
    public let ctaEnabled: Bool
}

/// Data for the Package body sub-card.
public struct PackageBodyContent: Sendable {
    public let carrier: String
    public let etaLine: String?
}

/// Observed detail-screen state.
public enum MailboxItemDetailState: Sendable {
    case loading
    case loaded(MailboxItemDetailContent)
    case error(String)
}

/// Per-CTA busy / error flags surfaced to the view for optimistic UI.
public struct MailboxCTAFlags: Sendable {
    public var primaryLoading: Bool = false
    public var ghostLoading: Bool = false
    public var errorToast: String?
    public var primaryCompleted: Bool = false
}

/// ViewModel backing `MailboxItemDetailView`.
@Observable
@MainActor
final class MailboxItemDetailViewModel {
    /// Currently displayed state.
    private(set) var state: MailboxItemDetailState = .loading
    /// Ephemeral CTA busy / toast flags.
    var ctaFlags = MailboxCTAFlags()

    private let mailId: String
    private let api: APIClient

    init(mailId: String, api: APIClient = .shared) {
        self.mailId = mailId
        self.api = api
    }

    /// Initial load; no-op when already loaded.
    func load() async {
        if case .loaded = state { return }
        state = .loading
        await fetch()
    }

    /// Pull-to-refresh / retry.
    func refresh() async { await fetch() }

    // MARK: - Actions

    /// Primary CTA for Package: `PATCH .../status { status: "received" }`
    /// Marks the step as optimistically-done; rolls back on failure.
    func logAsReceived() async {
        guard case .loaded(var content) = state, !ctaFlags.primaryLoading else { return }
        let originalTimeline = content.timeline
        let originalCtaEnabled = content.ctaEnabled

        // Optimistic: flip the last .current step to .done (or append one).
        let updatedTimeline = flipCurrentToDone(originalTimeline)
        content = MailboxItemDetailContent(
            category: content.category,
            trust: content.trust,
            sender: content.sender,
            aiElf: content.aiElf,
            keyFacts: content.keyFacts,
            timeline: updatedTimeline,
            packageInfo: content.packageInfo,
            ctaEnabled: false
        )
        state = .loaded(content)
        ctaFlags.primaryLoading = true

        do {
            _ = try await api.request(
                MailboxV2Endpoints.packageStatusUpdate(
                    mailId: mailId,
                    request: PackageStatusUpdateRequest(status: "delivered")
                )
            ) as PackageStatusUpdateResponse
            ctaFlags.primaryCompleted = true
        } catch {
            if case .loaded(var rollback) = state {
                rollback = MailboxItemDetailContent(
                    category: rollback.category,
                    trust: rollback.trust,
                    sender: rollback.sender,
                    aiElf: rollback.aiElf,
                    keyFacts: rollback.keyFacts,
                    timeline: originalTimeline,
                    packageInfo: rollback.packageInfo,
                    ctaEnabled: originalCtaEnabled
                )
                state = .loaded(rollback)
            }
            ctaFlags.errorToast = (error as? APIError)?.errorDescription ?? "Couldn't update status."
        }
        ctaFlags.primaryLoading = false
    }

    /// Ghost CTA: `POST .../action { action: "not_mine" }`.
    func markNotMine() async {
        guard case .loaded(let snapshot) = state, !ctaFlags.ghostLoading else { return }
        ctaFlags.ghostLoading = true
        do {
            _ = try await api.request(
                MailboxV2Endpoints.itemAction(mailId: mailId, action: "not_mine")
            ) as MailboxItemActionResponse
            // Disable the CTAs — the item is no longer actionable by this user.
            let disabled = MailboxItemDetailContent(
                category: snapshot.category,
                trust: snapshot.trust,
                sender: snapshot.sender,
                aiElf: snapshot.aiElf,
                keyFacts: snapshot.keyFacts,
                timeline: snapshot.timeline,
                packageInfo: snapshot.packageInfo,
                ctaEnabled: false
            )
            state = .loaded(disabled)
        } catch {
            ctaFlags.errorToast = (error as? APIError)?.errorDescription ?? "Couldn't flag this item."
        }
        ctaFlags.ghostLoading = false
    }

    // MARK: - Fetch

    private func fetch() async {
        do {
            let response: MailboxV2ItemResponse = try await api.request(
                MailboxV2Endpoints.item(mailId: mailId)
            )
            let category = MailItemCategory.fromRaw(
                response.mail.base.mailType ?? response.mail.base.type
            )
            if category == .package {
                await fetchPackageDetails(for: response.mail, category: category)
            } else {
                applyItem(response.mail, category: category)
            }
        } catch {
            state = .error((error as? APIError)?.errorDescription ?? "Couldn't load this item.")
        }
    }

    private func fetchPackageDetails(
        for item: MailboxV2ItemResponse.Item,
        category: MailItemCategory
    ) async {
        do {
            let pkg: PackageDetailResponse = try await api.request(
                MailboxV2Endpoints.package(mailId: mailId)
            )
            applyPackage(item: item, pkg: pkg)
        } catch {
            // Fall back to the base mail detail if package lookup fails.
            applyItem(item, category: category)
        }
    }

    private func applyItem(_ item: MailboxV2ItemResponse.Item, category: MailItemCategory) {
        state = .loaded(
            MailboxItemDetailContent(
                category: category,
                trust: MailTrust.fromRaw(item.senderTrust),
                sender: SenderBlockContent(
                    displayName: item.senderDisplay,
                    meta: item.base.createdAt,
                    initials: Self.initials(from: item.senderDisplay)
                ),
                aiElf: nil,
                keyFacts: [
                    KeyFactRow(label: "Subject", value: item.base.displayTitle ?? item.base.subject ?? "—"),
                    KeyFactRow(label: "Received", value: item.base.createdAt),
                ],
                timeline: [],
                packageInfo: nil,
                ctaEnabled: true
            )
        )
    }

    private func applyPackage(
        item: MailboxV2ItemResponse.Item,
        pkg: PackageDetailResponse
    ) {
        let pkgDict = pkg.package.dictValue
        let trackingNumber = pkgDict?["tracking_number"]?.stringValue
        let carrier = pkgDict?["carrier"]?.stringValue ?? "Carrier"
        let currentStatus = pkgDict?["status"]?.stringValue ?? "in_transit"
        let suggested = pkgDict?["suggested_order_match"]?.stringValue

        let aiElf: AIElfContent? = suggested.map { match in
            AIElfContent(
                suggestion: "Looks like your \(match) order",
                primaryChip: "Link",
                secondaryChip: "Not mine"
            )
        }

        var facts: [KeyFactRow] = []
        if let trackingNumber {
            facts.append(KeyFactRow(label: "Tracking #", value: trackingNumber, isCode: true))
        }
        facts.append(KeyFactRow(label: "Sender", value: item.senderDisplay))
        facts.append(KeyFactRow(label: "Carrier", value: carrier))
        facts.append(KeyFactRow(label: "Received at", value: item.base.createdAt))

        let steps = Self.timeline(for: currentStatus)

        state = .loaded(
            MailboxItemDetailContent(
                category: .package,
                trust: MailTrust.fromRaw(item.senderTrust),
                sender: SenderBlockContent(
                    displayName: item.senderDisplay,
                    meta: pkg.sender?.display ?? carrier,
                    initials: Self.initials(from: item.senderDisplay)
                ),
                aiElf: aiElf,
                keyFacts: facts,
                timeline: steps,
                packageInfo: PackageBodyContent(carrier: carrier, etaLine: nil),
                ctaEnabled: currentStatus != "delivered"
            )
        )
    }

    // MARK: - Helpers

    private static func initials(from display: String) -> String {
        let parts = display.split(separator: " ").prefix(2)
        return parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
    }

    private static func timeline(for status: String) -> [TimelineStep] {
        let order = ["pre_receipt", "in_transit", "out_for_delivery", "delivered"]
        let labels = ["Shipped", "In transit", "Out for delivery", "Delivered"]
        let currentIndex = order.firstIndex(of: status) ?? 1
        return zip(order, labels).enumerated().map { index, pair in
            let state: TimelineStepState
            if index < currentIndex { state = .done }
            else if index == currentIndex { state = .current }
            else { state = .upcoming }
            return TimelineStep(id: pair.0, title: pair.1, state: state)
        }
    }

    private func flipCurrentToDone(_ steps: [TimelineStep]) -> [TimelineStep] {
        guard let index = steps.firstIndex(where: { $0.state == .current }) else { return steps }
        var updated = steps
        updated[index] = TimelineStep(id: steps[index].id, title: steps[index].title, state: .done)
        if index + 1 < updated.count {
            updated[index + 1] = TimelineStep(
                id: steps[index + 1].id,
                title: steps[index + 1].title,
                state: .current
            )
        }
        return updated
    }
}

private extension JSONValue {
    /// Dictionary projection if this case is `.object`.
    var dictValue: [String: JSONValue]? {
        if case .object(let dict) = self { return dict } else { return nil }
    }
}
