//
//  ConnectedCalendarsViewModel.swift
//  Pantopus
//
//  Stream I2 — B8 Connected Calendars. Reads the (v1-empty) connected-calendar
//  list (`GET /connected-calendars`); `POST /connected-calendars/connect`
//  returns 501, so connecting surfaces an honest "coming soon" notice rather
//  than a dead end. If the backend ever returns linked accounts they render as
//  conflict-check / write-target rows. Personal surface.
//

import Observation
import SwiftUI

/// A calendar provider offered on the connect rows.
struct CalendarProvider: Identifiable, Hashable {
    let id: String
    let name: String
    let icon: PantopusIcon

    static let all: [CalendarProvider] = [
        CalendarProvider(id: "google", name: "Google Calendar", icon: .calendarDays),
        CalendarProvider(id: "apple", name: "Apple Calendar", icon: .calendar),
        CalendarProvider(id: "outlook", name: "Outlook", icon: .mail)
    ]
}

@Observable
@MainActor
final class ConnectedCalendarsViewModel {
    enum Phase: Equatable {
        case loading
        case ready
        case error(message: String)
    }

    private(set) var phase: Phase = .loading
    private(set) var calendars: [ConnectedCalendarDTO] = []
    /// A connect attempt that returned 501 — surfaced as a "coming soon" alert.
    var notice: String?
    /// Provider id mid-attempt (drives the row's shimmer status).
    var connectingId: String?

    let owner: SchedulingOwner
    private let client: SchedulingClient

    init(owner: SchedulingOwner, client: SchedulingClient = .shared) {
        self.owner = owner
        self.client = client
    }

    /// v1 always returns no linked accounts, so the screen leads with the
    /// calm coming-soon hero.
    var isComingSoon: Bool { calendars.isEmpty }

    let providers = CalendarProvider.all

    func load() async {
        if case .ready = phase { return }
        await fetch()
    }

    func reload() async { await fetch() }

    private func fetch() async {
        phase = .loading
        do {
            let response: ConnectedCalendarsResponse = try await client.request(
                SchedulingEndpoints.getConnectedCalendars()
            )
            calendars = response.calendars
            phase = .ready
        } catch let error as SchedulingError {
            phase = .error(message: error.userMessage ?? "Couldn't load your calendars.")
        } catch {
            phase = .error(message: "Couldn't load your calendars.")
        }
    }

    func connect(_ provider: CalendarProvider) async {
        guard connectingId == nil else { return }
        connectingId = provider.id
        defer { connectingId = nil }
        do {
            try await client.send(SchedulingEndpoints.connectCalendar())
            await fetch()
        } catch let error as SchedulingError {
            notice = comingSoonMessage(for: provider, error: error)
        } catch {
            notice = "Couldn't connect right now. Please try again."
        }
    }

    private func comingSoonMessage(for provider: CalendarProvider, error: SchedulingError) -> String {
        if case .notImplemented = error {
            return "Calendar sync is coming soon. We'll let you know when you can connect \(provider.name)."
        }
        return error.userMessage ?? "Couldn't connect \(provider.name) right now."
    }
}
