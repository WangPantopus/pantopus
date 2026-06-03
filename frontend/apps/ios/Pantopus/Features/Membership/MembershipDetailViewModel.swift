//
//  MembershipDetailViewModel.swift
//  Pantopus
//
//  A10.8 — Backs the fan-side membership manage screen. `load()` fetches
//  the fan's own membership from `GET /api/personas/:id/membership`
//  (`backend/routes/personaMembership.js:108`) and projects it onto the
//  existing `MembershipDetailContent`. The `MembershipSampleData` fixtures
//  remain the documented preview/test seam — they are injected only via the
//  `content:` / `slaMissed:` seeds (previews + snapshot baselines), never on
//  the live path.
//
//  Mutations: the single-tap Cancel posts to
//  `/api/personas/:id/membership/cancel` (no charge — free memberships
//  cancel immediately, paid memberships flip `cancel_at_period_end`).
//  Upgrade / downgrade / change-tier / refund stay host callbacks — those
//  are paid actions deferred to Phase 3. The `slaMissed` refund banner is a
//  preview-only frame (the backend membership read carries no SLA flag).
//

import Foundation
import Observation

@Observable
@MainActor
public final class MembershipDetailViewModel {
    public private(set) var state: MembershipDetailState = .loading

    /// Transient error surfaced inline when a Cancel round-trip fails.
    public var actionError: String?
    public private(set) var isCancelling = false

    private let api: APIClient
    private let personaId: String
    private let seededContent: MembershipDetailContent?
    private let startsSLAMissed: Bool

    /// - Parameters:
    ///   - personaId: Canonical route payload — the persona (UUID) whose
    ///     membership is being managed.
    ///   - api: Injected for tests; defaults to the shared client.
    ///   - content: Optional seed (previews / tests) — short-circuits the
    ///     fetch and renders the fixture.
    ///   - slaMissed: When `true` and no seed is supplied, renders the
    ///     refund-eligible sample frame (preview-only).
    public init(
        personaId: String,
        api: APIClient = .shared,
        content: MembershipDetailContent? = nil,
        slaMissed: Bool = false
    ) {
        self.personaId = personaId
        self.api = api
        seededContent = content
        startsSLAMissed = slaMissed
    }

    public func load() async {
        state = .loading
        actionError = nil

        // Preview/test seam: a seeded fixture (or the slaMissed flag) renders
        // the deterministic sample without touching the network.
        if let seededContent {
            state = seededContent.slaAlert != nil ? .slaMissed(seededContent) : .populated(seededContent)
            return
        }
        if startsSLAMissed {
            state = .slaMissed(MembershipSampleData.slaMissed)
            return
        }

        do {
            let response: PersonaMembershipResponse = try await api.request(
                MembershipEndpoints.membership(personaId: personaId)
            )
            guard let membership = response.membership, membership.persona != nil else {
                state = .error(message: "We couldn't find your membership.")
                return
            }
            state = .populated(Self.project(membership))
        } catch {
            let message = (error as? APIError)?.errorDescription ?? "Couldn't load membership."
            state = .error(message: message)
        }
    }

    /// "Give it a week" — drop the SLA banner and settle back to the happy
    /// path. The gentle alternative to a refund; never a guilt-trip.
    public func dismissSLAAlert() {
        guard case let .slaMissed(content) = state else { return }
        state = .populated(content.clearingSLAAlert())
    }

    /// Single-tap cancel. Returns `true` once the backend confirms so the
    /// host can advance to its cancellation screen. No charge (see header).
    /// On failure surfaces `actionError` inline and stays put.
    @discardableResult
    public func cancel() async -> Bool {
        guard !isCancelling else { return false }
        isCancelling = true
        actionError = nil
        defer { isCancelling = false }
        do {
            _ = try await api.request(
                MembershipEndpoints.cancelMembership(personaId: personaId),
                as: PersonaMembershipResponse.self
            )
            return true
        } catch {
            actionError = (error as? APIError)?.errorDescription
                ?? "Couldn't cancel right now. Please try again."
            return false
        }
    }

    // MARK: - Projection

    static func project(_ dto: PersonaMembershipDTO) -> MembershipDetailContent {
        MembershipDetailContent(
            persona: projectPersona(dto.persona),
            tier: MembershipTier(rank: dto.tier?.rank),
            priceLabel: priceLabel(cents: dto.tier?.priceCents, currency: dto.tier?.currency),
            periodLabel: periodLabel(interval: dto.tier?.billingInterval),
            renewalLabel: renewalLabel(end: dto.currentPeriodEnd, cancelAtPeriodEnd: dto.cancelAtPeriodEnd ?? false),
            // Payment-method detail isn't on the membership read (Phase 3,
            // Stripe). Surface an honest, non-fabricated descriptor.
            paymentLabel: "Managed by Stripe",
            benefits: benefits(from: dto.tier),
            policyFootnote: MembershipSampleData.policyFootnote,
            slaAlert: nil
        )
    }

    private static func projectPersona(_ dto: MembershipPersonaDTO?) -> MembershipPersona {
        let name = dto?.displayName ?? dto?.handle ?? "Creator"
        return MembershipPersona(
            id: dto?.id ?? "",
            name: name,
            initials: initials(from: name),
            subtitle: subtitle(
                category: dto?.category,
                audienceLabel: dto?.audienceLabel,
                followerCount: dto?.followerCount
            ),
            pillar: .business,
            pillarLabel: "Creator",
            verified: dto?.credential?.status == "verified"
        )
    }

    private static func initials(from name: String) -> String {
        let chars = name.split(separator: " ").prefix(2).compactMap(\.first).map(String.init)
        return chars.joined().uppercased()
    }

    private static func subtitle(category: String?, audienceLabel: String?, followerCount: Int?) -> String {
        var parts: [String] = []
        if let category, !category.isEmpty { parts.append(category.capitalized) }
        if let followerCount {
            parts.append("\(followerCount.formatted()) \(audienceLabel ?? "members")")
        }
        return parts.joined(separator: " · ")
    }

    private static func priceLabel(cents: Int?, currency: String?) -> String {
        guard let cents, cents > 0 else { return "Free" }
        let symbol = if let currency, currency.lowercased() != "usd" {
            "\(currency.uppercased()) "
        } else {
            "$"
        }
        if cents % 100 == 0 { return "\(symbol)\(cents / 100)" }
        return String(format: "\(symbol)%.2f", Double(cents) / 100.0)
    }

    private static func periodLabel(interval: String?) -> String {
        switch interval {
        case "year", "yearly", "annual": "year"
        case "week", "weekly": "week"
        default: "month"
        }
    }

    private static func renewalLabel(end iso: String?, cancelAtPeriodEnd: Bool) -> String {
        guard let iso, let date = parseDate(iso) else {
            return cancelAtPeriodEnd ? "Cancels at the end of this period" : "Renews automatically"
        }
        let dateStr = date.formatted(.dateTime.month(.abbreviated).day())
        if cancelAtPeriodEnd { return "Cancels on \(dateStr)" }
        let days = max(0, Calendar.current.dateComponents([.day], from: Date(), to: date).day ?? 0)
        return "Renews on \(dateStr) · \(days) days from now"
    }

    private static func parseDate(_ iso: String) -> Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: iso) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: iso)
    }

    /// Benefit rows derived from the tier's perk fields — real data from the
    /// membership read, not fabricated. Empty when the tier carries no perks.
    private static func benefits(from tier: MembershipTierDTO?) -> [MembershipBenefit] {
        guard let tier else { return [] }
        var rows: [MembershipBenefit] = []
        if let threads = tier.msgThreadsPerPeriod, threads != 0 {
            rows.append(
                MembershipBenefit(
                    id: "threads",
                    icon: .messageCircle,
                    label: "Direct message threads",
                    meta: threads < 0 ? "Unlimited" : "\(threads) per period"
                )
            )
        }
        if tier.creatorCanInitiateDm == true {
            rows.append(
                MembershipBenefit(
                    id: "creatorDm",
                    icon: .mail,
                    label: "Creator can message you",
                    meta: "Replies land in your inbox"
                )
            )
        }
        if let policy = tier.replyPolicy, !policy.isEmpty {
            rows.append(
                MembershipBenefit(
                    id: "replyPolicy",
                    icon: .messageCircle,
                    label: "Reply policy",
                    meta: policy.replacingOccurrences(of: "_", with: " ").capitalized
                )
            )
        }
        return rows
    }
}

private extension MembershipTier {
    /// Map the backend tier rank (1–4) onto the fan-facing 3-rung ladder.
    /// Rank 4 (Direct) folds into Gold — the membership card models only
    /// Bronze / Silver / Gold paper-card treatments.
    init(rank: Int?) {
        switch rank ?? 1 {
        case ...1: self = .bronze
        case 2: self = .silver
        default: self = .gold
        }
    }
}
