//
//  BookingLimitsViewModel.swift
//  Pantopus
//
//  Stream I3 — B7 Booking Limits & Notice Rules (sheet). Booking limits live
//  on the EVENT TYPE (not the schedule), so this reads `GET /event-types/:id`
//  and saves the limit/notice subset via the partial `PUT /event-types/:id`.
//  Owner-scoped (personal sky by default, or the event type's pillar when
//  opened as a per-type override from I2).
//
//  NOTE (Foundation contract limitation): `UpdateEventTypeRequest` encodes
//  with `encodeIfPresent`, so a nil numeric field is OMITTED, not sent as
//  null. Turning a cap OFF therefore cannot CLEAR an existing server-side
//  cap through this PUT — it just stops sending it. Flagged for Foundation.
//

import Observation
import SwiftUI

/// Slot-granularity options for the "Start times" control.
enum SlotInterval: Int, CaseIterable, Identifiable {
    case everyHour = 60
    case halfHour = 30
    case every15 = 15

    var id: Int { rawValue }

    var label: String {
        switch self {
        case .everyHour: ":00 only"
        case .halfHour: ":00 & :30"
        case .every15: "every 15 min"
        }
    }

    static func from(minutes: Int?) -> SlotInterval {
        switch minutes {
        case 60: .everyHour
        case 30: .halfHour
        default: .every15
        }
    }
}

@Observable
@MainActor
final class BookingLimitsViewModel {
    enum Phase {
        case loading
        case ready
        case error(message: String)
    }

    private(set) var phase: Phase = .loading

    let owner: SchedulingOwner
    let eventTypeId: String

    // Editable fields
    var minNoticeHours = 4
    var horizonDays = 60
    var limitPerDay = false
    var dailyCap = 8
    var limitPerPerson = false
    var perBookerCap = 2
    var slotInterval: SlotInterval = .every15

    var eventTypeName = ""
    private(set) var isSaving = false
    var saveError: String?

    private let client: SchedulingClient
    private var baselineSignature: String?

    init(owner: SchedulingOwner, eventTypeId: String, client: SchedulingClient = .shared) {
        self.owner = owner
        self.eventTypeId = eventTypeId
        self.client = client
    }

    // MARK: Derived

    /// The booking window can't be shorter than the minimum notice, or no
    /// times would ever show.
    var windowConflict: Bool {
        minNoticeHours > horizonDays * 24
    }

    var isValid: Bool { !windowConflict }
    var isDirty: Bool { signature() != baselineSignature }
    var canSave: Bool { isValid && isDirty && !isSaving }

    // MARK: Load

    func load() async {
        if case .ready = phase { return }
        await fetch()
    }

    func reload() async { await fetch() }

    private func fetch() async {
        phase = .loading
        do {
            let detail: EventTypeDetailResponse = try await client.request(
                SchedulingEndpoints.getEventType(owner: owner, id: eventTypeId)
            )
            apply(detail.eventType)
            baselineSignature = signature()
            phase = .ready
        } catch let error as SchedulingError {
            phase = .error(message: error.userMessage ?? "Couldn't load these limits.")
        } catch {
            phase = .error(message: "Couldn't load these limits.")
        }
    }

    private func apply(_ eventType: EventTypeDTO) {
        eventTypeName = eventType.name
        if let notice = eventType.minNoticeMin { minNoticeHours = max(0, notice / 60) }
        if let horizon = eventType.maxHorizonDays { horizonDays = max(1, horizon) }
        if let cap = eventType.dailyCap { limitPerDay = true; dailyCap = max(1, cap) }
        if let cap = eventType.perBookerCap { limitPerPerson = true; perBookerCap = max(1, cap) }
        slotInterval = SlotInterval.from(minutes: eventType.slotIntervalMin)
    }

    // MARK: Save

    /// Returns true on a successful save so the View can dismiss.
    func save() async -> Bool {
        guard canSave else { return false }
        isSaving = true
        defer { isSaving = false }
        let request = UpdateEventTypeRequest(
            minNoticeMin: minNoticeHours * 60,
            maxHorizonDays: horizonDays,
            slotIntervalMin: slotInterval.rawValue,
            dailyCap: limitPerDay ? dailyCap : nil,
            perBookerCap: limitPerPerson ? perBookerCap : nil
        )
        do {
            _ = try await client.request(
                SchedulingEndpoints.updateEventType(owner: owner, id: eventTypeId, request),
                as: EventTypeResponse.self
            )
            baselineSignature = signature()
            return true
        } catch let error as SchedulingError {
            saveError = error.userMessage ?? "Couldn't save these limits."
            return false
        } catch {
            saveError = "Couldn't save these limits."
            return false
        }
    }

    private func signature() -> String {
        [
            String(minNoticeHours),
            String(horizonDays),
            limitPerDay ? String(dailyCap) : "off",
            limitPerPerson ? String(perBookerCap) : "off",
            String(slotInterval.rawValue)
        ].joined(separator: "|")
    }
}
