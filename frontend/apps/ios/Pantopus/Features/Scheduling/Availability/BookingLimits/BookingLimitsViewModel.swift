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
//  Saving is PER-FIELD-DIRTY: only the fields the user actually changed are
//  put in the request body, so opening the sheet to tweak one control can
//  never silently rewrite an untouched value the UI can't faithfully
//  represent (e.g. a sub-hour `min_notice_min` or a non-60/30/15
//  `slot_interval_min`).
//
//  Clearing a cap (toggling `daily_cap`/`per_booker_cap` off) can't be
//  expressed: the Foundation `UpdateEventTypeRequest` encodes with
//  `encodeIfPresent`, so a nil field is OMITTED, not sent as JSON `null`,
//  and the backend leaves omitted fields untouched. Rather than report a
//  false success, we surface a clear message and resync. (A proper fix needs
//  a tri-state encoder in Foundation — flagged.)
//

import Observation
import SwiftUI

/// Slot-granularity options for the "Start times" control.
enum SlotInterval: Int, CaseIterable, Identifiable {
    case everyHour = 60
    case halfHour = 30
    case every15 = 15

    var id: Int {
        rawValue
    }

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

    // Raw loaded values, so per-field-dirty save never clobbers a value the
    // UI can't faithfully represent.
    private var loadedMinNoticeMin: Int?
    private var loadedHorizonDays: Int?
    private var loadedSlotIntervalMin: Int?
    private var loadedDailyCap: Int?
    private var loadedPerBookerCap: Int?

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

    var isValid: Bool {
        !windowConflict
    }

    var isDirty: Bool {
        signature() != baselineSignature
    }

    var canSave: Bool {
        isValid && isDirty && !isSaving
    }

    // MARK: Load

    func load() async {
        if case .ready = phase { return }
        await fetch()
    }

    func reload() async {
        await fetch()
    }

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
        loadedMinNoticeMin = eventType.minNoticeMin
        loadedHorizonDays = eventType.maxHorizonDays
        loadedSlotIntervalMin = eventType.slotIntervalMin
        loadedDailyCap = eventType.dailyCap
        loadedPerBookerCap = eventType.perBookerCap

        // Round (not truncate) so a 90-min notice reads as ~2h rather than 1h.
        if let notice = eventType.minNoticeMin {
            minNoticeHours = max(0, Int((Double(notice) / 60).rounded()))
        }
        if let horizon = eventType.maxHorizonDays { horizonDays = max(1, horizon) }
        if let cap = eventType.dailyCap { limitPerDay = true
            dailyCap = max(1, cap)
        }
        if let cap = eventType.perBookerCap { limitPerPerson = true
            perBookerCap = max(1, cap)
        }
        slotInterval = SlotInterval.from(minutes: eventType.slotIntervalMin)
    }

    // MARK: Save

    /// Returns true on a successful save so the View can dismiss. Builds the
    /// request from only the fields the user changed.
    func save() async -> Bool {
        guard canSave else { return false }
        isSaving = true
        defer { isSaving = false }

        var request = UpdateEventTypeRequest()
        var hasChanges = false

        let noticeMin = minNoticeHours * 60
        if noticeMin != loadedMinNoticeMin {
            request.minNoticeMin = noticeMin
            hasChanges = true
        }
        if horizonDays != loadedHorizonDays {
            request.maxHorizonDays = horizonDays
            hasChanges = true
        }
        if slotInterval.rawValue != loadedSlotIntervalMin {
            request.slotIntervalMin = slotInterval.rawValue
            hasChanges = true
        }

        // Caps: a numeric value can be set/raised/lowered, but a removal
        // (set → off) can't be expressed via the partial PUT.
        var clearRequested = false
        let desiredDaily: Int? = limitPerDay ? dailyCap : nil
        if desiredDaily != loadedDailyCap {
            if let value = desiredDaily { request.dailyCap = value
                hasChanges = true
            } else { clearRequested = true }
        }
        let desiredPerBooker: Int? = limitPerPerson ? perBookerCap : nil
        if desiredPerBooker != loadedPerBookerCap {
            if let value = desiredPerBooker { request.perBookerCap = value
                hasChanges = true
            } else { clearRequested = true }
        }

        do {
            if hasChanges {
                _ = try await client.request(
                    SchedulingEndpoints.updateEventType(owner: owner, id: eventTypeId, request),
                    as: EventTypeResponse.self
                )
            }
            if clearRequested {
                // The omitted-field PUT can't clear a cap; be honest and resync.
                saveError = "Removing a limit isn't supported yet — any other changes were saved."
                await fetch()
                return false
            }
            loadedMinNoticeMin = noticeMin
            loadedHorizonDays = horizonDays
            loadedSlotIntervalMin = slotInterval.rawValue
            loadedDailyCap = desiredDaily
            loadedPerBookerCap = desiredPerBooker
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
