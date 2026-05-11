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
    /// Category-specific sub-payload resolved from `mail.object_payload`.
    /// `.other` for categories without a dedicated body decoder.
    public let payload: MailboxCategoryPayload

    public init(
        category: MailItemCategory,
        trust: MailTrust,
        sender: SenderBlockContent,
        aiElf: AIElfContent?,
        keyFacts: [KeyFactRow],
        timeline: [TimelineStep],
        packageInfo: PackageBodyContent?,
        ctaEnabled: Bool,
        payload: MailboxCategoryPayload = .other
    ) {
        self.category = category
        self.trust = trust
        self.sender = sender
        self.aiElf = aiElf
        self.keyFacts = keyFacts
        self.timeline = timeline
        self.packageInfo = packageInfo
        self.ctaEnabled = ctaEnabled
        self.payload = payload
    }
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

    /// Whether the user has checked the "I acknowledge receipt" gate on
    /// the certified body. The view binds to this; the primary CTA is
    /// only enabled once it flips true.
    var certifiedAckChecked: Bool = false

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
    func refresh() async {
        await fetch()
    }

    // MARK: - Actions

    /// Primary CTA for Package: `PATCH .../status { status: "received" }`
    /// Marks the step as optimistically-done; rolls back on failure.
    func logAsReceived() async {
        guard case var .loaded(content) = state, !ctaFlags.primaryLoading else { return }
        Analytics.track(.ctaMailboxItemLogReceived)
        if !NetworkMonitor.shared.isOnline {
            ctaFlags.errorToast = "You're offline. Try again when you're back online."
            return
        }
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
            if case var .loaded(rollback) = state {
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
        guard case let .loaded(snapshot) = state, !ctaFlags.ghostLoading else { return }
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

    // MARK: - P18 unified CTA dispatch

    /// Primary CTA tap. Routes by category:
    ///   .package    → `logAsReceived()` (existing P9 flow)
    ///   .coupon     → optimistic "added to wallet" client-side flip
    ///   .booklet    → `file` action via the V2 item-action endpoint
    ///   .certified  → `acknowledge` action; gated on
    ///                 `certifiedAckChecked == true`
    /// Returns silently for any other category.
    func performPrimaryAction() async {
        guard case let .loaded(content) = state, !ctaFlags.primaryLoading else { return }
        switch content.category {
        case .package:
            await logAsReceived()
        case .coupon:
            await addToWallet()
        case .booklet:
            await saveBookletToLibrary()
        case .certified:
            guard certifiedAckChecked else { return }
            await acknowledgeReceipt()
        default:
            break
        }
    }

    /// Ghost CTA tap. Mirrors `performPrimaryAction`'s dispatch.
    func performGhostAction() async {
        guard case let .loaded(content) = state, !ctaFlags.ghostLoading else { return }
        switch content.category {
        case .package:
            await markNotMine()
        case .coupon:
            await saveCouponForLater()
        case .certified:
            // "View terms" is a UI-only modal handled by the screen.
            break
        default:
            break
        }
    }

    // MARK: - Coupon actions

    /// "Add to wallet" — backend has no first-class wallet endpoint
    /// today, so we flip to the "added" state client-side and surface
    /// an analytic event. Wire to a real `add_to_wallet` action when
    /// it lands in `validActions` on the V2 item-action route.
    private func addToWallet() async {
        Analytics.track(.ctaMailboxItemLogReceived) // TODO(analytics): rename when wallet event lands
        ctaFlags.primaryCompleted = true
    }

    /// "Save for later" → POST .../action { action: "file" }. `file`
    /// is the closest valid action in the V2 whitelist; a dedicated
    /// `save_for_later` action would be cleaner.
    private func saveCouponForLater() async {
        await callItemAction(action: "file", primary: false)
    }

    // MARK: - Booklet actions

    /// "Save to library" → POST .../action { action: "file" }. Same
    /// rationale as `saveCouponForLater`.
    private func saveBookletToLibrary() async {
        await callItemAction(action: "file", primary: true)
    }

    // MARK: - Certified actions

    /// "Acknowledge receipt" → POST .../action { action: "acknowledge" }.
    /// `acknowledge` is in the V2 action whitelist
    /// (`backend/routes/mailboxV2.js:465`).
    private func acknowledgeReceipt() async {
        await callItemAction(action: "acknowledge", primary: true) {
            // On success: lock the CTA and visually mark as completed.
            self.ctaFlags.primaryCompleted = true
        }
    }

    /// Generic V2 item-action dispatcher with optimistic loading flag
    /// and a friendly error toast on failure.
    private func callItemAction(
        action: String,
        primary: Bool,
        onSuccess: (@MainActor () -> Void)? = nil
    ) async {
        if primary {
            ctaFlags.primaryLoading = true
        } else {
            ctaFlags.ghostLoading = true
        }
        do {
            _ = try await api.request(
                MailboxV2Endpoints.itemAction(mailId: mailId, action: action)
            ) as MailboxItemActionResponse
            onSuccess?()
        } catch {
            ctaFlags.errorToast =
                (error as? APIError)?.errorDescription ?? "Couldn't complete that action."
        }
        if primary {
            ctaFlags.primaryLoading = false
        } else {
            ctaFlags.ghostLoading = false
        }
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
            switch category {
            case .package:
                await fetchPackageDetails(for: response.mail, category: category)
            case .coupon, .booklet, .certified:
                applyCategoryBody(response.mail, category: category)
            default:
                applyItem(response.mail, category: category)
            }
        } catch {
            state = .error((error as? APIError)?.errorDescription ?? "Couldn't load this item.")
        }
    }

    /// Coupon / Booklet / Certified — decode `object_payload` into a
    /// typed payload and project facts + AI elf + (for Certified) chain
    /// timeline + stamp.
    private func applyCategoryBody(
        _ item: MailboxV2ItemResponse.Item,
        category: MailItemCategory
    ) {
        let payload = MailboxCategoryPayload.resolve(
            category: category,
            objectPayload: item.objectPayload
        )
        let baseTrust = MailTrust.fromRaw(item.senderTrust)
        Analytics.track(
            .screenMailboxItemDetailViewed(
                category: category.rawValue,
                trustLevel: baseTrust.rawValue
            )
        )

        switch payload {
        case let .coupon(coupon):
            applyCoupon(item: item, category: category, coupon: coupon, baseTrust: baseTrust)
        case let .booklet(booklet):
            applyBooklet(item: item, category: category, booklet: booklet, baseTrust: baseTrust)
        case let .certified(certified):
            applyCertified(item: item, category: category, certified: certified)
        case .other:
            // Payload didn't decode — fall back to the placeholder body.
            applyItem(item, category: category)
        }
    }

    private func applyCoupon(
        item: MailboxV2ItemResponse.Item,
        category: MailItemCategory,
        coupon: CouponDetailDTO,
        baseTrust: MailTrust
    ) {
        let aiElf: AIElfContent? = coupon.expiresAt.flatMap { expiry in
            guard let days = Self.daysUntil(expiry), days >= 0 && days <= 30 else { return nil }
            return AIElfContent(
                suggestion: "Expires in \(days) day\(days == 1 ? "" : "s") — add to wallet before then?",
                primaryChip: "Add to wallet",
                secondaryChip: "Remind me later"
            )
        }
        var facts: [KeyFactRow] = []
        if let merchant = coupon.merchant { facts.append(KeyFactRow(label: "Merchant", value: merchant)) }
        if let code = coupon.code { facts.append(KeyFactRow(label: "Code", value: code, isCode: true)) }
        if let terms = coupon.terms { facts.append(KeyFactRow(label: "Terms", value: terms)) }
        if let minSpend = coupon.minimumSpend { facts.append(KeyFactRow(label: "Minimum spend", value: minSpend)) }

        state = .loaded(
            MailboxItemDetailContent(
                category: category,
                trust: baseTrust,
                sender: SenderBlockContent(
                    displayName: item.senderDisplay,
                    meta: item.base.createdAt,
                    initials: Self.initials(from: item.senderDisplay),
                    senderUserId: item.base.senderUserId
                ),
                aiElf: aiElf,
                keyFacts: facts,
                timeline: [],
                packageInfo: nil,
                ctaEnabled: true,
                payload: .coupon(coupon)
            )
        )
    }

    private func applyBooklet(
        item: MailboxV2ItemResponse.Item,
        category: MailItemCategory,
        booklet: BookletDetailDTO,
        baseTrust: MailTrust
    ) {
        var facts: [KeyFactRow] = [
            KeyFactRow(label: "Sender", value: item.senderDisplay),
            KeyFactRow(label: "Pages", value: "\(booklet.pageCount)"),
            KeyFactRow(label: "Received at", value: item.base.createdAt)
        ]
        if facts.isEmpty { facts = [] } // keep linter happy for future edits
        state = .loaded(
            MailboxItemDetailContent(
                category: category,
                trust: baseTrust,
                sender: SenderBlockContent(
                    displayName: item.senderDisplay,
                    meta: item.base.createdAt,
                    initials: Self.initials(from: item.senderDisplay),
                    senderUserId: item.base.senderUserId
                ),
                aiElf: nil,
                keyFacts: facts,
                timeline: [],
                packageInfo: nil,
                ctaEnabled: true,
                payload: .booklet(booklet)
            )
        )
    }

    private func applyCertified(
        item: MailboxV2ItemResponse.Item,
        category: MailItemCategory,
        certified: CertifiedDetailDTO
    ) {
        let timeline: [TimelineStep] = certified.chain.enumerated().map { _, step in
            TimelineStep(
                id: step.id,
                title: step.label,
                state: step.isComplete ? .done : .upcoming
            )
        }
        let aiElf: AIElfContent? = certified.acknowledgeBy.map { deadline in
            let days = Self.daysUntil(deadline) ?? 0
            return AIElfContent(
                suggestion: "Acknowledge by \(deadline) — \(days) day\(days == 1 ? "" : "s") remaining",
                primaryChip: "Acknowledge now",
                secondaryChip: "View terms"
            )
        }
        var facts: [KeyFactRow] = [
            KeyFactRow(label: "Reference #", value: certified.referenceNumber, isCode: true),
            KeyFactRow(label: "Sender", value: item.senderDisplay)
        ]
        if let ackBy = certified.acknowledgeBy {
            facts.append(KeyFactRow(label: "Acknowledge by", value: ackBy))
        }
        if let docType = certified.documentType {
            facts.append(KeyFactRow(label: "Document type", value: docType))
        }

        state = .loaded(
            MailboxItemDetailContent(
                category: category,
                trust: .certifiedChain,
                sender: SenderBlockContent(
                    displayName: item.senderDisplay,
                    meta: item.base.createdAt,
                    initials: Self.initials(from: item.senderDisplay),
                    senderUserId: item.base.senderUserId,
                    showStamp: true
                ),
                aiElf: aiElf,
                keyFacts: facts,
                timeline: timeline,
                packageInfo: nil,
                ctaEnabled: !certified.isAcknowledged,
                payload: .certified(certified)
            )
        )
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
        let trust = MailTrust.fromRaw(item.senderTrust)
        Analytics.track(
            .screenMailboxItemDetailViewed(
                category: category.rawValue,
                trustLevel: trust.rawValue
            )
        )
        state = .loaded(
            MailboxItemDetailContent(
                category: category,
                trust: trust,
                sender: SenderBlockContent(
                    displayName: item.senderDisplay,
                    meta: item.base.createdAt,
                    initials: Self.initials(from: item.senderDisplay),
                    senderUserId: item.base.senderUserId
                ),
                aiElf: nil,
                keyFacts: [
                    KeyFactRow(label: "Subject", value: item.base.displayTitle ?? item.base.subject ?? "—"),
                    KeyFactRow(label: "Received", value: item.base.createdAt)
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
        let trust = MailTrust.fromRaw(item.senderTrust)
        Analytics.track(
            .screenMailboxItemDetailViewed(
                category: MailItemCategory.package.rawValue,
                trustLevel: trust.rawValue
            )
        )

        state = .loaded(
            MailboxItemDetailContent(
                category: .package,
                trust: trust,
                sender: SenderBlockContent(
                    displayName: item.senderDisplay,
                    meta: pkg.sender?.display ?? carrier,
                    initials: Self.initials(from: item.senderDisplay),
                    senderUserId: item.base.senderUserId
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

    /// Number of whole days from now until the supplied ISO-8601 string,
    /// rounded down. Negative if the date is in the past, nil if it
    /// can't be parsed.
    static func daysUntil(_ iso: String) -> Int? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let parsed = formatter.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
        guard let date = parsed else { return nil }
        let interval = date.timeIntervalSinceNow
        return Int((interval / 86_400).rounded(.down))
    }

    private static func timeline(for status: String) -> [TimelineStep] {
        let order = ["pre_receipt", "in_transit", "out_for_delivery", "delivered"]
        let labels = ["Shipped", "In transit", "Out for delivery", "Delivered"]
        let currentIndex = order.firstIndex(of: status) ?? 1
        return zip(order, labels).enumerated().map { index, pair in
            let state: TimelineStepState = if index < currentIndex {
                .done
            } else if index == currentIndex {
                .current
            } else {
                .upcoming
            }
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

