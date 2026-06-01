//
//  WaitingRoomViewModel.swift
//  Pantopus
//
//  Backs the A18.4 persistent waiting room. The room is re-entrant — it
//  survives navigating away and back — so the view-model just projects a
//  deterministic fixture for the requested state (`active` /
//  `moreInfoRequested`). Real review-status polling is out of scope (B5.1);
//  the backend was removed from the repo, so the two frames are seeded from
//  `WaitingRoomContent`'s factories.
//
//  Actions (bell, View claim / Back to home, Update evidence, Cancel claim)
//  are stubbed: they log and no-op, pending the review-status backend. The
//  back-chevron is the only live navigation and is owned by the caller.
//

import Foundation
import Logging
import Observation

/// Which canonical frame the room opens on.
public enum WaitingRoomState: String, Sendable, Hashable {
    /// Active wait — `Under review`, info-toned pulsing halo.
    case active
    /// More info requested · review paused — warning halo + reviewer note.
    case moreInfoRequested
}

@Observable
@MainActor
public final class WaitingRoomViewModel {
    /// The home this room belongs to (`pantopus://homes/:id/waiting-room`).
    public let homeId: String
    public private(set) var content: WaitingRoomContent

    private let logger = Logger(label: "app.pantopus.ios.WaitingRoom")

    /// - Parameters:
    ///   - homeId: The home whose claim is under review.
    ///   - state: Which frame to seed. Defaults to the active wait.
    ///   - content: Optional override (tests / previews).
    public init(
        homeId: String,
        state: WaitingRoomState = .active,
        content: WaitingRoomContent? = nil
    ) {
        self.homeId = homeId
        self.content = content ?? Self.content(for: state)
    }

    static func content(for state: WaitingRoomState) -> WaitingRoomContent {
        switch state {
        case .active: .active()
        case .moreInfoRequested: .moreInfoRequested()
        }
    }

    // MARK: - Stubbed actions (no backend in B5.1)

    /// Top-bar notifications bell. Stub until the review-status surface lands.
    public func openNotifications() {
        log("bell")
    }

    /// One of the 2-column "Manage this claim" actions fired.
    public func handleInlineAction(_ action: WaitingRoomInlineAction) {
        log("inline.\(action.actionKey)")
    }

    /// Sticky-dock primary ("View claim"). Stub.
    public func handlePrimary(_ cta: StatusCTA) {
        log("dock.\(cta.actionKey)")
    }

    /// Sticky-dock secondary ("Back to home"). Stub — the caller may also
    /// pop; this only records the tap.
    public func handleSecondary(_ cta: StatusCTA) {
        log("dock.\(cta.actionKey)")
    }

    private func log(_ action: String) {
        logger.info("waitingRoom.action", metadata: [
            "homeId": .string(homeId),
            "action": .string(action)
        ])
    }
}
