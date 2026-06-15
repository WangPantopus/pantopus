//
//  WaitlistJoinViewModel.swift
//  Pantopus
//
//  Stream I9 — E13 Waitlist (invitee join surface). An invitee joins a full
//  event type's waitlist via the public `POST /book/:slug/:eventTypeSlug/
//  waitlist`. The backend keys the entry on EMAIL and notifies by email; it
//  returns no queue position and offers no "leave" endpoint (backend gaps — the
//  UI reflects what the API supports).
//

import Observation
import SwiftUI

@Observable
@MainActor
final class WaitlistJoinViewModel {
    let slug: String
    let eventTypeSlug: String
    let windowLabel: String
    let timeZoneLabel: String

    var name = ""
    var email = ""
    var preferredTime = ""

    private(set) var isJoining = false
    private(set) var didJoin = false
    var errorMessage: String?

    private let client: SchedulingClient

    init(
        slug: String,
        eventTypeSlug: String,
        windowLabel: String,
        timeZoneLabel: String,
        client: SchedulingClient
    ) {
        self.slug = slug
        self.eventTypeSlug = eventTypeSlug
        self.windowLabel = windowLabel
        self.timeZoneLabel = timeZoneLabel
        self.client = client
    }

    var canJoin: Bool {
        let trimmed = email.trimmingCharacters(in: .whitespaces)
        return trimmed.contains("@") && trimmed.contains(".") && !isJoining
    }

    func join() async {
        guard canJoin else { return }
        isJoining = true
        errorMessage = nil
        defer { isJoining = false }
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            let _: WaitlistJoinResponse = try await client.request(
                SchedulingPublicEndpoints.joinWaitlist(
                    slug: slug,
                    eventTypeSlug: eventTypeSlug,
                    WaitlistJoinRequest(
                        email: email.trimmingCharacters(in: .whitespaces),
                        name: trimmedName.isEmpty ? nil : trimmedName
                    )
                )
            )
            didJoin = true
        } catch let error as SchedulingError {
            errorMessage = error.userMessage ?? "Couldn't join the waitlist — try again"
        } catch {
            errorMessage = "Couldn't join the waitlist — try again"
        }
    }
}
