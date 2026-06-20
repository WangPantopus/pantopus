//
//  MarkNoShowViewModel.swift
//  Pantopus
//
//  Stream I9 — E6 Mark No-Show (confirmation dialog). Flips a booking to the
//  `no_show` terminal state via `POST /bookings/:id/no-show`. Only surfaces
//  after the booking's start time; the backend returns `409 NOT_APPLICABLE_YET`
//  (older builds: `BAD_STATE`) before then — handled defensively.
//
//  Group events are modeled as one booking row per attendee, so "who didn't
//  show" maps each selected attendee to its own booking id and marks each.
//

import Observation
import SwiftUI

/// One no-show target — a single booking (1:1) or one attendee's booking row
/// within a group event.
struct NoShowTarget: Identifiable, Hashable {
    let bookingId: String
    let name: String

    var id: String {
        bookingId
    }

    var initials: String {
        BookingsExtrasFormatting.initials(from: name)
    }
}

@Observable
@MainActor
final class MarkNoShowViewModel {
    let owner: SchedulingOwner
    let targets: [NoShowTarget]

    var selectedIds: Set<String>
    var note: String = ""
    private(set) var isSubmitting = false
    var errorMessage: String?

    private let client: SchedulingClient

    init(owner: SchedulingOwner, targets: [NoShowTarget], client: SchedulingClient) {
        self.owner = owner
        self.targets = targets
        // Default to every target selected; the host deselects anyone who showed.
        selectedIds = Set(targets.map(\.bookingId))
        self.client = client
    }

    var isGroup: Bool {
        targets.count > 1
    }

    var canConfirm: Bool {
        !selectedIds.isEmpty
    }

    var confirmTitle: String {
        isGroup ? "Mark \(selectedIds.count) as no-show" : "Mark no-show"
    }

    func toggle(_ id: String) {
        if selectedIds.contains(id) {
            selectedIds.remove(id)
        } else {
            selectedIds.insert(id)
        }
    }

    /// Marks each selected target a no-show. Returns `true` when every selected
    /// booking flipped successfully.
    func confirm() async -> Bool {
        guard !isSubmitting, canConfirm else { return false }
        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }
        do {
            for target in targets where selectedIds.contains(target.bookingId) {
                let _: BookingResponse = try await client.request(
                    SchedulingEndpoints.markNoShow(owner: owner, id: target.bookingId)
                )
            }
            return true
        } catch let error as SchedulingError {
            errorMessage = Self.message(for: error)
            return false
        } catch {
            errorMessage = "Couldn't update — try again"
            return false
        }
    }

    static func message(for error: SchedulingError) -> String {
        switch error.code {
        case "NOT_APPLICABLE_YET", "BAD_STATE":
            "You can mark a no-show only after the booking's start time."
        default:
            error.userMessage ?? "Couldn't update — try again"
        }
    }
}
