//
//  WaitlistManagementViewModel.swift
//  Pantopus
//
//  Stream I9 — E13 Waitlist (host management surface). Lists the event type's
//  waitlist (`GET /event-types/:id/waitlist`) over a capacity header (seats from
//  the event type's `seat_cap`) and promotes entries
//  (`POST /waitlist/:id/promote` — which notifies the invitee a spot opened).
//

import Observation
import SwiftUI

@Observable
@MainActor
final class WaitlistManagementViewModel {
    enum Phase: Equatable {
        case loading
        case ready
        case empty
        case error(message: String)
    }

    let owner: SchedulingOwner
    let eventTypeId: String
    let push: @MainActor (SchedulingRoute) -> Void

    private(set) var phase: Phase = .loading
    private(set) var seatTotal = 0
    private(set) var entries: [RosterPerson] = []
    var actionError: String?

    private let client: SchedulingClient

    init(
        owner: SchedulingOwner,
        eventTypeId: String,
        push: @escaping @MainActor (SchedulingRoute) -> Void,
        client: SchedulingClient
    ) {
        self.owner = owner
        self.eventTypeId = eventTypeId
        self.push = push
        self.client = client
    }

    var waitingCount: Int { entries.count }

    func load() async {
        if phase != .ready { phase = .loading }
        await fetch(showLoading: phase != .ready)
    }

    func refresh() async { await fetch(showLoading: false) }

    private func fetch(showLoading: Bool) async {
        if showLoading { phase = .loading }
        do {
            async let eventTypeResponse: EventTypeDetailResponse = client.request(
                SchedulingEndpoints.getEventType(owner: owner, id: eventTypeId)
            )
            async let waitlistResponse: WaitlistResponse = client.request(
                SchedulingEndpoints.getWaitlist(owner: owner, eventTypeId: eventTypeId)
            )
            let (eventType, waitlist) = try await (eventTypeResponse, waitlistResponse)

            seatTotal = max(eventType.eventType.seatCap ?? 1, 1)
            let waiting = waitlist.waitlist.filter { ($0.status ?? "waiting") == "waiting" }
            entries = Self.build(waiting)
            phase = entries.isEmpty ? .empty : .ready
        } catch let error as SchedulingError {
            phase = .error(message: error.userMessage ?? "Couldn't load the waitlist")
        } catch {
            phase = .error(message: "Couldn't load the waitlist")
        }
    }

    private static func build(_ entries: [WaitlistEntryDTO]) -> [RosterPerson] {
        let tz = SchedulingTime.deviceTimeZoneIdentifier
        return entries.enumerated().map { index, entry in
            var meta = "#\(index + 1)"
            if let joined = BookingsExtrasFormatting.shortDay(utcISO: entry.createdAt, tz: tz) {
                meta += " · joined \(joined)"
            }
            return RosterPerson(
                id: entry.id,
                name: entry.inviteeName ?? entry.inviteeEmail ?? "Guest",
                meta: meta,
                statusRaw: nil,
                promoteEntryId: entry.id
            )
        }
    }

    func promote(entryId: String) async {
        actionError = nil
        do {
            let _: SchedulingOkResponse = try await client.request(
                SchedulingEndpoints.promoteWaitlist(owner: owner, entryId: entryId)
            )
            await refresh()
        } catch let error as SchedulingError {
            actionError = error.userMessage ?? "Couldn't promote — try again"
        } catch {
            actionError = "Couldn't promote — try again"
        }
    }
}
