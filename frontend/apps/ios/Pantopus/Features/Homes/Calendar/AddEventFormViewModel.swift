//
//  AddEventFormViewModel.swift
//  Pantopus
//
//  P2.7 — Add / edit event form for the Home calendar.
//
//  POSTs `POST /api/homes/:id/events` (`backend/routes/home.js:5035`) on
//  create, `PUT /api/homes/:id/events/:eventId` (line 5082) on edit.
//  Schema columns: `event_type / title / description / start_at /
//  end_at / location_notes / recurrence_rule / assigned_to /
//  alerts_enabled`. Reminder granularity (5min / 15min / 1hr / 1day) is
//  rendered client-side but flattened to `alerts_enabled` on the wire —
//  the backend has no minute-count column today.
//

import Foundation
import Observation

@Observable
@MainActor
final class AddEventFormViewModel {
    // MARK: - Public state

    private(set) var state: AddEventFormState = .loading
    private(set) var pendingEvent: AddEventFormEvent?
    private(set) var isSaving: Bool = false
    var toast: ToastMessage?
    private(set) var shakeTrigger: Int = 0

    /// Free-text fields backed by `FormFieldState` for dirty / valid
    /// bookkeeping consistent with other Form-archetype screens.
    var fields: [AddEventField: FormFieldState] = [:]

    /// Category chip selection. Backed directly because `FormFieldState`
    /// is string-only and we want type-safe round-trip.
    var category: CalendarEventCategory
    /// Original category for dirty tracking on edit.
    let originalCategory: CalendarEventCategory

    var allDay: Bool {
        didSet { rebuildEndIfAllDayChanged(from: oldValue) }
    }

    let originalAllDay: Bool

    var startDate: Date {
        didSet { ensureEndAfterStart() }
    }

    let originalStart: Date

    /// When nil the event is point-in-time (no end).
    var endDate: Date?
    let originalEnd: Date?

    var recurrence: AddEventRecurrence
    let originalRecurrence: AddEventRecurrence

    var reminder: AddEventReminder
    let originalReminder: AddEventReminder

    /// Multi-select attendee user-ids. Order matches the original list.
    var selectedAttendeeIds: Set<String>
    let originalAttendeeIds: Set<String>

    /// Available household members loaded from `/api/homes/:id/occupants`.
    private(set) var attendees: [AddEventAttendee] = []

    // MARK: - Dependencies

    let homeId: String
    let editingEventId: String?
    let api: APIClient

    // MARK: - Init

    /// Create form. Pass `editingEvent: nil` for create. When editing,
    /// pass the existing DTO so the form prefills synchronously.
    init(
        homeId: String,
        editingEvent: CalendarEventDTO? = nil,
        prefilledCategory: CalendarEventCategory? = nil,
        prefilledStart: Date? = nil,
        api: APIClient = .shared
    ) {
        self.homeId = homeId
        editingEventId = editingEvent?.id
        self.api = api

        for field in AddEventField.allCases {
            fields[field] = FormFieldState(id: field.rawValue, originalValue: "")
        }

        if let editingEvent {
            let parsedStart = Self.parseIsoInstant(editingEvent.startAt) ?? Date()
            let parsedEnd = editingEvent.endAt.flatMap(Self.parseIsoInstant)
            startDate = parsedStart
            originalStart = parsedStart
            endDate = parsedEnd
            originalEnd = parsedEnd
            allDay = Self.isAllDayHeuristic(start: parsedStart, end: parsedEnd, endIso: editingEvent.endAt)
            originalAllDay = allDay
            category = CalendarEventCategory.from(eventType: editingEvent.eventType)
            originalCategory = category
            recurrence = AddEventRecurrence.from(rrule: editingEvent.recurrenceRule)
            originalRecurrence = recurrence
            reminder = (editingEvent.alertsEnabled ?? false) ? .fifteenMin : .none
            originalReminder = reminder
            let attendeeIds = Set(editingEvent.assignedTo ?? [])
            selectedAttendeeIds = attendeeIds
            originalAttendeeIds = attendeeIds

            var titleField = FormFieldState(id: AddEventField.title.rawValue, originalValue: editingEvent.title)
            titleField.error = Self.titleValidator.validate(editingEvent.title)
            fields[.title] = titleField

            let locationField = FormFieldState(
                id: AddEventField.location.rawValue,
                originalValue: editingEvent.locationNotes ?? ""
            )
            fields[.location] = locationField

            let notesField = FormFieldState(
                id: AddEventField.notes.rawValue,
                originalValue: editingEvent.description ?? ""
            )
            fields[.notes] = notesField
        } else {
            let now = prefilledStart ?? Self.defaultStart(from: Date())
            startDate = now
            originalStart = now
            endDate = nil
            originalEnd = nil
            allDay = false
            originalAllDay = false
            category = prefilledCategory ?? .generic
            originalCategory = .generic
            recurrence = .none
            originalRecurrence = .none
            reminder = .none
            originalReminder = .none
            selectedAttendeeIds = []
            originalAttendeeIds = []
        }
    }

    // MARK: - Lifecycle

    var isEditing: Bool {
        editingEventId != nil
    }

    var screenTitle: String {
        isEditing ? "Edit event" : "Add event"
    }

    var commitLabel: String {
        isEditing ? "Save" : "Add"
    }

    /// Load the household roster + (when editing) re-fetch the source
    /// event so a stale prefill doesn't get committed.
    func load() async {
        state = .loading
        do {
            let response: OccupantsResponse = try await api.request(
                HomesEndpoints.listOccupants(homeId: homeId)
            )
            attendees = response.occupants
                .filter(\.isActive)
                .map { Self.buildAttendee(from: $0) }
            state = .editing
        } catch {
            attendees = []
            state = .editing
            toast = ToastMessage(
                text: (error as? APIError)?.errorDescription ?? "Couldn't load household members.",
                kind: .error
            )
        }
    }

    // MARK: - Mutators

    func updateField(_ field: AddEventField, to value: String) {
        guard var snapshot = fields[field] else { return }
        snapshot.value = value
        snapshot.touched = true
        snapshot.error = validator(for: field).validate(value)
        fields[field] = snapshot
    }

    func toggleAttendee(_ userId: String) {
        if selectedAttendeeIds.contains(userId) {
            selectedAttendeeIds.remove(userId)
        } else {
            selectedAttendeeIds.insert(userId)
        }
    }

    private func rebuildEndIfAllDayChanged(from oldValue: Bool) {
        guard oldValue != allDay else { return }
        if allDay {
            startDate = Calendar.current.startOfDay(for: startDate)
            endDate = nil
        } else {
            let cal = Calendar.current
            var comps = cal.dateComponents([.year, .month, .day], from: startDate)
            comps.hour = 9
            comps.minute = 0
            if let snapped = cal.date(from: comps) {
                startDate = snapped
            }
        }
    }

    private func ensureEndAfterStart() {
        if let end = endDate, end < startDate {
            endDate = startDate.addingTimeInterval(60 * 60)
        }
    }

    // MARK: - Aggregate dirty / valid

    var isDirty: Bool {
        let textDirty = fields.values.contains(where: \.isDirty)
        if textDirty { return true }
        if category != originalCategory { return true }
        if allDay != originalAllDay { return true }
        if abs(startDate.timeIntervalSince(originalStart)) > 0.5 { return true }
        switch (endDate, originalEnd) {
        case (nil, nil): break
        case let (a?, b?) where abs(a.timeIntervalSince(b)) <= 0.5: break
        default: return true
        }
        if recurrence != originalRecurrence { return true }
        if reminder != originalReminder { return true }
        if selectedAttendeeIds != originalAttendeeIds { return true }
        return false
    }

    var isValid: Bool {
        let title = (fields[.title]?.value ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else { return false }
        if fields.values.contains(where: { $0.error != nil }) { return false }
        if let end = endDate, end < startDate { return false }
        return true
    }

    /// Returns an inline message when end-date is before start.
    var endError: String? {
        guard let end = endDate, end < startDate else { return nil }
        return "End must be on or after start."
    }

    // MARK: - Submit

    @discardableResult
    func submit() async -> Bool {
        if !validateAll() {
            shakeTrigger &+= 1
            toast = ToastMessage(text: "Fix the highlighted field.", kind: .error)
            return false
        }
        if !NetworkMonitor.shared.isOnline {
            toast = ToastMessage(
                text: "You're offline. Try again when you're back online.",
                kind: .error
            )
            return false
        }
        if let eventId = editingEventId {
            return await commitUpdate(eventId: eventId)
        }
        return await commitCreate()
    }

    func acknowledgePendingEvent() {
        pendingEvent = nil
    }

    func setSaving(_ value: Bool) {
        isSaving = value
    }

    func recordCreated(eventId: String) {
        toast = ToastMessage(text: "Event added.", kind: .success)
        pendingEvent = .created(eventId: eventId)
    }

    func recordUpdated(eventId: String) {
        toast = ToastMessage(text: "Event updated.", kind: .success)
        pendingEvent = .updated(eventId: eventId)
    }

    func recordCommitError(_ error: Error, isUpdate: Bool) {
        let fallback = isUpdate ? "Couldn't update this event." : "Couldn't add this event."
        toast = ToastMessage(
            text: (error as? APIError)?.errorDescription ?? fallback,
            kind: .error
        )
    }
}
