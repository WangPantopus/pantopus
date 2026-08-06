//
//  WaitingRoomViewModel.swift
//  Pantopus
//
//  Backs the A18.4 persistent waiting room. Loads the caller's pending
//  ownership claim for `homeId` from `GET /api/homes/my-ownership-claims`
//  and projects it into `WaitingRoomContent`. Actions surface navigation
//  intents via `pendingNav` for the host to handle.
//

import Foundation
import Logging
import Observation

/// Which canonical frame the room opens on (seed / query param).
public enum WaitingRoomState: String, Sendable, Hashable {
    case active
    case moreInfoRequested
}

/// Navigation intents surfaced to the host.
public enum WaitingRoomNav: Sendable, Hashable {
    case notifications
    case backToHome(homeId: String)
    case viewClaim(claimId: String)
    case updateEvidence(homeId: String, claimId: String)
    case cancelClaim(homeId: String)
}

@Observable
@MainActor
public final class WaitingRoomViewModel {
    public let homeId: String
    public private(set) var content: WaitingRoomContent
    public private(set) var phase: WaitingRoomPhase = .loading
    public private(set) var pendingNav: WaitingRoomNav?

    /// The only masked status `GET /api/homes/my-ownership-claims` returns
    /// while a claim is still in flight — every other value it can return
    /// (`approved` / `rejected` / `revoked`) is terminal.
    private static let underReviewStatus = "under_review"
    /// Claim reference shown in the waiting room = first 8 chars of the id.
    private static let claimRefLength = 8

    private let seedState: WaitingRoomState
    private var claimId: String?
    private let api: APIClient
    private let logger = Logger(label: "app.pantopus.ios.WaitingRoom")

    init(
        homeId: String,
        state: WaitingRoomState = .active,
        content: WaitingRoomContent? = nil,
        api: APIClient = .shared
    ) {
        self.homeId = homeId
        seedState = state
        self.api = api
        self.content = content ?? Self.content(for: state)
    }

    static func content(for state: WaitingRoomState) -> WaitingRoomContent {
        switch state {
        case .active: .active()
        case .moreInfoRequested: .moreInfoRequested()
        }
    }

    public func consumeNav() {
        pendingNav = nil
    }

    public func refresh() async {
        do {
            let claimsResponse: MyOwnershipClaimsResponse = try await api.request(
                HomesEndpoints.myOwnershipClaims()
            )
            guard let claim = claimsResponse.claims.first(where: { $0.homeId == homeId }) else {
                claimId = nil
                phase = .notice(.noClaim)
                return
            }
            claimId = claim.id
            guard claim.status == Self.underReviewStatus else {
                phase = .notice(.claimDecided)
                return
            }
            let ref = String(claim.id.prefix(Self.claimRefLength)).uppercased()

            var address = "Your home"
            if let detail: HomeDetailResponse = try? await api.request(HomesEndpoints.detail(homeId: homeId)) {
                let home = detail.home.base
                let parts = [home.address, home.city, home.state].compactMap { $0 }
                let joined = parts.joined(separator: " · ")
                if !joined.isEmpty { address = joined }
            }

            content =
                seedState == .moreInfoRequested
                    ? .moreInfoRequested(address: address, claimRef: ref)
                    : .active(address: address, claimRef: ref)
            phase = .loaded
        } catch {
            logger.warning("waitingRoom.load failed: \(error.localizedDescription)")
            phase = .notice(.loadFailed)
        }
    }

    public func openNotifications() {
        pendingNav = .notifications
    }

    public func handleInlineAction(_ action: WaitingRoomInlineAction) {
        switch action.actionKey {
        case "update_evidence":
            if let claimId {
                pendingNav = .updateEvidence(homeId: homeId, claimId: claimId)
            }
        case "cancel_claim":
            pendingNav = .cancelClaim(homeId: homeId)
        default:
            log("inline.\(action.actionKey)")
        }
    }

    public func handlePrimary(_ cta: StatusCTA) {
        switch cta.actionKey {
        case "view_claim":
            if let claimId {
                pendingNav = .viewClaim(claimId: claimId)
            }
        default:
            log("dock.\(cta.actionKey)")
        }
    }

    public func handleSecondary(_ cta: StatusCTA) {
        switch cta.actionKey {
        case "back_to_home":
            pendingNav = .backToHome(homeId: homeId)
        default:
            log("dock.\(cta.actionKey)")
        }
    }

    private func log(_ action: String) {
        logger.info("waitingRoom.action", metadata: [
            "homeId": .string(homeId),
            "action": .string(action)
        ])
    }
}
