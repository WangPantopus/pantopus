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

/// Recurrence choices the form exposes. Mapped to iCal RRULE on submit.
public enum AddEventRecurrence: String, CaseIterable, Sendable, Hashable {
    case none
    case daily
    case weekly
    case monthly
    case yearly

    public var label: String {
        switch self {
        case .none: "Does not repeat"
        case .daily: "Repeats daily"
        case .weekly: "Repeats weekly"
        case .monthly: "Repeats monthly"
        case .yearly: "Repeats yearly"
        }
    }

    /// Serialize to an iCal RRULE. `.none` → nil.
    public var rrule: String? {
        switch self {
        case .none: nil
        case .daily: "FREQ=DAILY"
        case .weekly: "FREQ=WEEKLY"
        case .monthly: "FREQ=MONTHLY"
        case .yearly: "FREQ=YEARLY"
        }
    }

    /// Best-effort inverse for prefilling the picker on edit. Anything
    /// not recognised lands on `.none` (the row still renders "Repeats"
    /// in the agenda via the existing `recurrenceShortLabel`).
    public static func from(rrule: String?) -> AddEventRecurrence {
        guard let raw = rrule?.uppercased(), !raw.isEmpty else { return .none }
        if raw.contains("FREQ=DAILY") { return .daily }
        if raw.contains("FREQ=WEEKLY") { return .weekly }
        if raw.contains("FREQ=MONTHLY") { return .monthly }
        if raw.contains("FREQ=YEARLY") { return .yearly }
        return .none
    }
}

/// Reminder lead-time. The picker shape matches the design spec; the
/// granularity is UI-only today — the wire boolean (`alerts_enabled`)
/// records whether any reminder is set, not the minute count. On load
/// we default to `.fifteenMin` when the event has alerts enabled so the
/// picker shows something sensible.
public enum AddEventReminder: String, CaseIterable, Sendable, Hashable {
    case none
    case fiveMin
    case fifteenMin
    case oneHour
    case oneDay

    public var label: String {
        switch self {
        case .none: "None"
        case .fiveMin: "5 minutes before"
        case .fifteenMin: "15 minutes before"
        case .oneHour: "1 hour before"
        case .oneDay: "1 day before"
        }
    }

    public var alertsEnabled: Bool {
        self != .none
    }
}

/// Stable identifiers for free-text fields. Date / category / attendee
/// fields don't fit `FormFieldState`'s string shape so they live on the
/// view-model directly.
public enum AddEventField: String, CaseIterable, Sendable {
    case title
    case location
    case notes
}

/// Render state for the Add Event form.
public enum AddEventFormState: Sendable, Equatable {
    /// Initial — fetching members + (when editing) the source event.
    case loading
    case editing
    case error(String)
}

/// Outbound event after a successful commit. The host route reads
/// `pendingEvent` and pops / replaces / pushes as needed.
public enum AddEventFormEvent: Sendable, Equatable {
    case created(eventId: String)
    case updated(eventId: String)
}

/// Attendee row surfaced in the multi-pick. Built from `OccupantDTO`
/// but exposed as a tiny value type so view code doesn't depend on the
/// wire shape.
public struct AddEventAttendee: Sendable, Hashable, Identifiable {
    public let id: String
    public let displayName: String
    public let initials: String

    public init(id: String, displayName: String, initials: String) {
        self.id = id
        self.displayName = displayName
        self.initials = initials
    }
}

@Observable
@MainActor
public final class AddEventFormViewModel {
    // MARK: - Public state

    public private(set) var state: AddEventFormState = .loading
    public private(set) var pendingEvent: AddEventFormEvent?
    public private(set) var isSaving: Bool = false
    public var toast: ToastMessage?
    public private(set) var shakeTrigger: Int = 0

    /// Free-text fields backed by `FormFieldState` for dirty / valid
    /// bookkeeping consistent with other Form-archetype screens.
    public var fields: [AddEventField: FormFieldState] = [:]

    /// Category chip selection. Backed directly because `FormFieldState`
    /// is string-only and we want type-safe round-trip.
    public var category: CalendarEventCategory
    /// Original category for dirty tracking on edit.
    private let originalCategory: CalendarEventCategory

    public var allDay: Bool {
        didSet { rebuildEndIfAllDayChanged(from: oldValue) }
    }
    private let originalAllDay: Bool

    public var startDate: Date {
        didSet { ensureEndAfterStart() }
    }
    private let originalStart: Date

    /// When nil the event is point-in-time (no end).
    public var endDate: Date?
    private let originalEnd: Date?

    public var recurrence: AddEventRecurrence
    private let originalRecurrence: AddEventRecurrence

    public var reminder: AddEventReminder
    private let originalReminder: AddEventReminder

    /// Multi-select attendee user-ids. Order matches the original list.
    public var selectedAttendeeIds: Set<String>
    private let originalAttendeeIds: Set<String>

    /// Available household members loaded from `/api/homes/:id/occupants`.
    public private(set) var attendees: [AddEventAttendee] = []

    // MARK: - Dependencies

    private let homeId: String
    private let editingEventId: String?
    private let api: APIClient
    /// Loaded once for edit mode so we can build the PATCH-style request
    /// without a re-GET.
    private var sourceEvent: CalendarEventDTO?

    // MARK: - Init

    /// Create form. Pass `editingEvent: nil` for create. When editing,
    /// pass the existing DTO so the form prefills synchronously.
    public init(
        homeId: String,
        editingEvent: CalendarEventDTO? = nil,
        prefilledCategory: CalendarEventCategory? = nil,
        prefilledStart: Date? = nil,
        api: APIClient = .shared
    ) {
        self.homeId = homeId
        editingEventId = editingEvent?.id
        sourceEvent = editingEvent
        self.api = api

        for field in AddEventField.allCases {
            fields[field] = FormFieldState(id: field.rawValue, originalValue: "")
        }

        if let editingEvent {
            // Hydrate from existing event.
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

            var locationField = FormFieldState(
                id: AddEventField.location.rawValue,
                originalValue: editingEvent.locationNotes ?? ""
            )
            fields[.location] = locationField

            var notesField = FormFieldState(
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
            originalCategory = .generic // Treat prefilled as dirty so save enables once title's typed.
            recurrence = .none
            originalRecurrence = .none
            reminder = .none
            originalReminder = .none
            selectedAttendeeIds = []
            originalAttendeeIds = []
        }
    }

    // MARK: - Lifecycle

    public var isEditing: Bool {
        editingEventId != nil
    }

    public var screenTitle: String {
        isEditing ? "Edit event" : "Add event"
    }

    public var commitLabel: String {
        isEditing ? "Save" : "Add"
    }

    /// Load the household roster + (when editing) re-fetch the source
    /// event so a stale prefill doesn't get committed.
    public func load() async {
        state = .loading
        do {
            let response: OccupantsResponse = try await api.request(
                HomesEndpoints.listOccupants(homeId: homeId)
            )
            attendees = response.occupants
                .filter { $0.isActive }
                .map { Self.buildAttendee(from: $0) }
            state = .editing
        } catch {
            // Members fetch failed — surface inline but still let the
            // user edit. Attendees just won't be pickable.
            attendees = []
            state = .editing
            toast = ToastMessage(
                text: (error as? APIError)?.errorDescription ?? "Couldn't load household members.",
                kind: .error
            )
        }
    }

    // MARK: - Mutators

    public func updateField(_ field: AddEventField, to value: String) {
        guard var snapshot = fields[field] else { return }
        snapshot.value = value
        snapshot.touched = true
        snapshot.error = validator(for: field).validate(value)
        fields[field] = snapshot
    }

    public func toggleAttendee(_ userId: String) {
        if selectedAttendeeIds.contains(userId) {
            selectedAttendeeIds.remove(userId)
        } else {
            selectedAttendeeIds.insert(userId)
        }
    }

    /// User toggled the All-day switch — clamp times and reset the
    /// end-date relationship so the picker doesn't render a stale time.
    private func rebuildEndIfAllDayChanged(from oldValue: Bool) {
        guard oldValue != allDay else { return }
        if allDay {
            // Snap start to midnight of its day; drop the end.
            startDate = Calendar.current.startOfDay(for: startDate)
            endDate = nil
        } else {
            // Coming off all-day → preserve the day but pick a sensible
            // 9 AM start so the time picker isn't a midnight surprise.
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

    public var isDirty: Bool {
        let textDirty = fields.values.contains { $0.isDirty }
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

    public var isValid: Bool {
        let title = (fields[.title]?.value ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else { return false }
        if fields.values.contains(where: { $0.error != nil }) { return false }
        if let end = endDate, end < startDate { return false }
        return true
    }

    /// Returns an inline message when end-date is before start.
    public var endError: String? {
        guard let end = endDate, end < startDate else { return nil }
        return "End must be on or after start."
    }

    // MARK: - Submit

    @discardableResult
    public func submit() async -> Bool {
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
        } else {
            return await commitCreate()
        }
    }

    private func commitCreate() async -> Bool {
        isSaving = true
        defer { isSaving = false }
        let request = buildCreateRequest()
        do {
            let response: HomeEventResponse = try await api.request(
                HomesEndpoints.createHomeEvent(homeId: homeId, request: request)
            )
            toast = ToastMessage(text: "Event added.", kind: .success)
            pendingEvent = .created(eventId: response.event.id)
            return true
        } catch {
            toast = ToastMessage(
                text: (error as? APIError)?.errorDescription ?? "Couldn't add this event.",
                kind: .error
            )
            return false
        }
    }

    private func commitUpdate(eventId: String) async -> Bool {
        isSaving = true
        defer { isSaving = false }
        let request = buildUpdateRequest()
        do {
            _ = try await api.request(
                HomesEndpoints.updateHomeEvent(
                    homeId: homeId,
                    eventId: eventId,
                    request: request
                )
            ) as HomeEventResponse
            toast = ToastMessage(text: "Event updated.", kind: .success)
            pendingEvent = .updated(eventId: eventId)
            return true
        } catch {
            toast = ToastMessage(
                text: (error as? APIError)?.errorDescription ?? "Couldn't update this event.",
                kind: .error
            )
            return false
        }
    }

    public func acknowledgePendingEvent() {
        pendingEvent = nil
    }

    // MARK: - Private helpers

    private func validator(for field: AddEventField) -> FormValidator {
        switch field {
        case .title:
            return Self.titleValidator
        case .location:
            return FormValidator { _ in nil }
        case .notes:
            return .maxLength(2000)
        }
    }

    /// Force-validate all fields; returns true when **all** field-level
    /// validators + the end-date relationship pass.
    private func validateAll() -> Bool {
        var allValid = true
        for field in AddEventField.allCases {
            guard var snapshot = fields[field] else { continue }
            let message = validator(for: field).validate(snapshot.value)
            snapshot.error = message
            snapshot.touched = true
            fields[field] = snapshot
            if message != nil { allValid = false }
        }
        // End-date relationship is enforced via the inline `endError`
        // surface; the view shows it next to the picker.
        if let end = endDate, end < startDate { allValid = false }
        return allValid
    }

    private func buildCreateRequest() -> CreateHomeEventRequest {
        let title = (fields[.title]?.value ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let location = nilIfEmpty((fields[.location]?.value ?? "").trimmingCharacters(in: .whitespacesAndNewlines))
        let notes = nilIfEmpty((fields[.notes]?.value ?? "").trimmingCharacters(in: .whitespacesAndNewlines))
        let assignedIds = sortedAttendeeIds()
        return CreateHomeEventRequest(
            eventType: category.rawValue,
            title: title,
            startAt: Self.iso8601(from: startDate, allDay: allDay),
            description: notes,
            endAt: allDay ? nil : endDate.map { Self.iso8601(from: $0, allDay: false) },
            locationNotes: location,
            recurrenceRule: recurrence.rrule,
            assignedTo: assignedIds.isEmpty ? nil : assignedIds,
            alertsEnabled: reminder.alertsEnabled
        )
    }

    private func buildUpdateRequest() -> UpdateHomeEventRequest {
        let title = (fields[.title]?.value ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let location = nilIfEmpty((fields[.location]?.value ?? "").trimmingCharacters(in: .whitespacesAndNewlines))
        let notes = nilIfEmpty((fields[.notes]?.value ?? "").trimmingCharacters(in: .whitespacesAndNewlines))
        let assignedIds = sortedAttendeeIds()
        return UpdateHomeEventRequest(
            eventType: category.rawValue,
            title: title,
            description: notes ?? "",
            startAt: Self.iso8601(from: startDate, allDay: allDay),
            endAt: allDay ? "" : (endDate.map { Self.iso8601(from: $0, allDay: false) } ?? ""),
            locationNotes: location ?? "",
            recurrenceRule: recurrence.rrule ?? "",
            assignedTo: assignedIds,
            alertsEnabled: reminder.alertsEnabled
        )
    }

    private func sortedAttendeeIds() -> [String] {
        // Preserve the order of the loaded roster for stable wire output.
        attendees.map(\.id).filter(selectedAttendeeIds.contains)
    }

    private func nilIfEmpty(_ value: String) -> String? {
        value.isEmpty ? nil : value
    }

    // MARK: - Static helpers

    /// Bucket validator used internally + for hydration round-tripping.
    static let titleValidator: FormValidator = .all([
        .required("Title"),
        .maxLength(120)
    ])

    /// Default start time when creating an event from scratch — the next
    /// quarter-hour, on today's day.
    public static func defaultStart(from now: Date) -> Date {
        let cal = Calendar.current
        var comps = cal.dateComponents([.year, .month, .day, .hour, .minute], from: now)
        let minute = comps.minute ?? 0
        let rounded = ((minute / 15) + 1) * 15
        if rounded >= 60 {
            comps.hour = (comps.hour ?? 0) + 1
            comps.minute = 0
        } else {
            comps.minute = rounded
        }
        return cal.date(from: comps) ?? now
    }

    /// Same heuristic as the agenda row: midnight + nil end → all-day.
    static func isAllDayHeuristic(start: Date, end: Date?, endIso: String?) -> Bool {
        let cal = Calendar.current
        let parts = cal.dateComponents([.hour, .minute, .second], from: start)
        let startIsMidnight = (parts.hour ?? 0) == 0 && (parts.minute ?? 0) == 0 && (parts.second ?? 0) == 0
        return startIsMidnight && end == nil && endIso == nil
    }

    /// ISO-8601 instant in UTC. For all-day events the time portion is
    /// 00:00:00 so the agenda's all-day heuristic recognises it.
    public static func iso8601(from date: Date, allDay: Bool) -> String {
        let fmt = ISO8601DateFormatter()
        fmt.formatOptions = [.withInternetDateTime]
        let stamp: Date = if allDay {
            Calendar.current.startOfDay(for: date)
        } else {
            date
        }
        return fmt.string(from: stamp)
    }

    public static func parseIsoInstant(_ iso: String) -> Date? {
        let withFrac = ISO8601DateFormatter()
        withFrac.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = withFrac.date(from: iso) { return d }
        let plain = ISO8601DateFormatter()
        plain.formatOptions = [.withInternetDateTime]
        if let d = plain.date(from: iso) { return d }
        let dayFmt = DateFormatter()
        dayFmt.locale = Locale(identifier: "en_US_POSIX")
        dayFmt.timeZone = TimeZone(identifier: "UTC")
        dayFmt.dateFormat = "yyyy-MM-dd"
        return dayFmt.date(from: iso)
    }

    /// Project a household occupant into the attendee picker row shape.
    private static func buildAttendee(from occupant: OccupantDTO) -> AddEventAttendee {
        let name = occupant.displayName?.trimmingCharacters(in: .whitespaces).isEmpty == false
            ? occupant.displayName!
            : (occupant.username ?? "Member")
        return AddEventAttendee(
            id: occupant.userId,
            displayName: name,
            initials: initials(from: name)
        )
    }

    private static func initials(from name: String) -> String {
        let parts = name.split(separator: " ").prefix(2)
        let chars = parts.compactMap { $0.first.map(String.init) }
        return chars.joined().uppercased()
    }
}
