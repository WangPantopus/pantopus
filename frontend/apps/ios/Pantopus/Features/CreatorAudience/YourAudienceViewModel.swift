//
//  YourAudienceViewModel.swift
//  Pantopus
//
//  A22.2 "Your audience". Drives the creator's member-management screen:
//  fetches `/me/audience`, projects pending requests + tier-grouped active
//  members, and runs approve / decline / remove actions against
//  `PATCH /me/audience/:membershipId`. Same VM/service pattern as My Bids —
//  a single `state` enum plus fine-grained published fields.
//

import SwiftUI

@Observable
@MainActor
public final class YourAudienceViewModel {
    /// Single source of truth for the screen body.
    public private(set) var state: YourAudienceState = .loading
    /// Backend counts (computed before filtering) — drive the nav count
    /// line and the chip badges regardless of the active filter.
    public private(set) var counts: AudienceCounts = .zero
    /// Creator-named tier labels, accumulated across fetches so a chip can
    /// be labelled even when the current (filtered) page omits that tier.
    public private(set) var tierNames: [Int: String] = [:]

    /// Active filter chip. Mutated only through `select(filter:)`.
    public private(set) var filter: AudienceFilter = .all
    /// Member whose overflow (•••) sheet is open.
    public var overflowTarget: AudienceMember?
    /// Transient confirmation / error message.
    public var toast: String?

    private let api: APIClient
    private var loadedAtLeastOnce = false

    public init(api: APIClient = .shared) {
        self.api = api
    }

    // MARK: - Loading

    public func load() async { await fetch() }
    public func refresh() async { await fetch() }

    /// Switch scope chips. Re-fetches with the matching query params.
    public func select(filter newFilter: AudienceFilter) async {
        guard filter != newFilter else { return }
        filter = newFilter
        await fetch()
    }

    private func fetch() async {
        if !loadedAtLeastOnce {
            state = .loading
        }
        do {
            let response: AudienceListResponse = try await api.request(
                AudienceProfileEndpoints.audience(
                    status: filter.statusParam,
                    tierRank: filter.tierRankParam
                )
            )
            loadedAtLeastOnce = true
            apply(response)
        } catch {
            let message = (error as? APIError)?.errorDescription ?? "Couldn't load your audience."
            if loadedAtLeastOnce {
                toast = message
            } else {
                state = .error(message: message)
            }
        }
    }

    private func apply(_ response: AudienceListResponse) {
        let parsed = AudienceCounts(
            totalActive: response.counts.totalActive ?? 0,
            pending: response.counts.pending ?? 0,
            byTier: Self.byTier(response.counts.byTier)
        )
        counts = parsed

        let members = response.items.compactMap(AudienceMember.init(dto:))
        for member in members where !member.tierName.isEmpty {
            tierNames[member.tierRank] = member.tierName
        }

        // Full-empty is a property of the whole audience, not the current
        // filter, so it keys off the unfiltered counts.
        if parsed.totalActive == 0, parsed.pending == 0 {
            state = .empty
            return
        }

        let pending = members.filter(\.isPending)
        let groups = Self.groupByTier(members.filter { !$0.isPending }, names: tierNames)
        state = .loaded(AudienceLoaded(counts: parsed, pending: pending, tierGroups: groups))
    }

    // MARK: - Actions

    public func approve(_ member: AudienceMember) async {
        await perform(.approve, on: member)
    }

    public func decline(_ member: AudienceMember) async {
        await perform(.decline, on: member)
    }

    public func remove(_ member: AudienceMember) async {
        overflowTarget = nil
        await perform(.remove, on: member)
    }

    /// Overflow → Message. No PII (user id) is exposed by the creator
    /// serializer, so a direct thread can't be opened from here yet.
    public func message(_ member: AudienceMember) {
        overflowTarget = nil
        toast = "Messaging \(member.displayName) is coming soon."
    }

    /// Overflow → Change tier. Tier moves aren't wired on mobile yet.
    public func changeTier(_ member: AudienceMember) {
        overflowTarget = nil
        toast = "Changing tiers is coming soon."
    }

    private func perform(_ action: AudienceMemberAction, on member: AudienceMember) async {
        do {
            _ = try await api.request(
                AudienceProfileEndpoints.memberAction(
                    membershipId: member.membershipId,
                    action: action.rawValue
                ),
                as: AudienceMemberActionResponse.self
            )
            // Re-fetch for authoritative counts + grouping (approve moves a
            // row from pending into its tier group; decline/remove drop it).
            await fetch()
            toast = Self.confirmation(for: action, member: member)
        } catch {
            toast = (error as? APIError)?.errorDescription
                ?? "Couldn't update \(member.displayName)."
        }
    }

    // MARK: - Derived view data

    /// Nav-bar count line — "5 members · 2 pending" / "0 members".
    public var countLine: String {
        if counts.totalActive == 0, counts.pending == 0 {
            return "0 members"
        }
        let memberWord = counts.totalActive == 1 ? "member" : "members"
        return "\(counts.totalActive) \(memberWord) · \(counts.pending) pending"
    }

    /// One chip per tier with a non-zero count, premium first (matches the
    /// design's VIP-before-Insiders order).
    public var tierChips: [AudienceTierChip] {
        counts.byTier
            .filter { $0.value > 0 }
            .keys
            .sorted(by: >)
            .map { rank in
                AudienceTierChip(
                    rank: rank,
                    name: tierNames[rank] ?? AudienceTierStyle.defaultName(rank: rank),
                    count: counts.byTier[rank] ?? 0
                )
            }
    }

    // MARK: - Helpers

    static func byTier(_ raw: [String: Int]?) -> [Int: Int] {
        var result: [Int: Int] = [:]
        for (key, value) in raw ?? [:] {
            if let rank = Int(key) { result[rank] = value }
        }
        return result
    }

    static func groupByTier(_ members: [AudienceMember], names: [Int: String]) -> [AudienceTierGroup] {
        Dictionary(grouping: members, by: \.tierRank)
            .map { rank, members in
                AudienceTierGroup(
                    rank: rank,
                    name: names[rank] ?? members.first?.tierName ?? AudienceTierStyle.defaultName(rank: rank),
                    members: members
                )
            }
            .sorted { $0.rank > $1.rank }
    }

    private static func confirmation(for action: AudienceMemberAction, member: AudienceMember) -> String {
        switch action {
        case .approve: "Approved \(member.displayName)."
        case .decline: "Declined \(member.displayName)."
        case .remove: "Removed \(member.displayName)."
        case .mute: "Muted \(member.displayName)."
        case .unmute: "Unmuted \(member.displayName)."
        }
    }
}

#if DEBUG
extension YourAudienceViewModel {
    /// Preview/snapshot factory — seeds the published state directly so
    /// previews don't hit the network. Setters are file-private, so this
    /// lives alongside the view-model.
    static func preview(
        _ state: YourAudienceState,
        counts: AudienceCounts,
        tierNames: [Int: String] = [:]
    ) -> YourAudienceViewModel {
        let viewModel = YourAudienceViewModel()
        viewModel.state = state
        viewModel.counts = counts
        viewModel.tierNames = tierNames
        return viewModel
    }
}
#endif
