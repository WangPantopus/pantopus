//
//  AddHouseholdTaskFormViewModel.swift
//  Pantopus
//
//  P2.4 — Backs `AddHouseholdTaskFormView`. Drives both Add
//  (`POST /api/homes/:id/tasks`, route `backend/routes/home.js:4238`)
//  and Edit (`PUT /api/homes/:id/tasks/:taskId`, route
//  `backend/routes/home.js:4308`) of one household chore. The two
//  modes share the same fields, layout, and validators — only the
//  load behavior (Edit hydrates from the existing task) and the
//  submit verb (Add → POST, Edit → PUT) differ.
//
//  Backend constraints worth knowing:
//   * The PUT allowlist (`home.js:4316`) does **not** include
//     `recurrence_rule`. The wire body carries it (so when the
//     backend catches up nothing changes here) but the server
//     silently drops it today. Mirrored on Android.
//   * `assigned_to` is a single user uuid column. The prompt asks
//     for multi-select; the wire forces single-select. Surfaced
//     in the UI as a single-selection picker labeled "Assigned to"
//     with "Unassigned (any member)" as the default. When schema
//     grows a multi-assignee column the picker can widen.
//

import Foundation
import Observation

/// Form-level category — distinct from the display-only
/// `HouseholdTaskCategory` palette (which is inferred from a free-form
/// title). These seven values are the user-pickable bucket from the
/// prompt. The wire payload sets `task_type` based on the mapping in
/// `taskType` below — the backend's `task_type` enum is `chore /
/// shopping / project / reminder / repair` and the seven design
/// buckets collapse into that vocabulary.
public enum AddHouseholdTaskFormCategory: String, CaseIterable, Sendable {
    case cleaning
    case cooking
    case shopping
    case yardwork
    case pets
    case repairs
    case other

    public var label: String {
        switch self {
        case .cleaning: "Cleaning"
        case .cooking: "Cooking"
        case .shopping: "Shopping"
        case .yardwork: "Yardwork"
        case .pets: "Pets"
        case .repairs: "Repairs"
        case .other: "Other"
        }
    }

    public var icon: PantopusIcon {
        switch self {
        case .cleaning: .sparkles
        case .cooking: .utensils
        case .shopping: .shoppingBag
        case .yardwork: .leaf
        case .pets: .pawPrint
        case .repairs: .hammer
        case .other: .checkCircle
        }
    }

    /// Wire `task_type` value (one of the 5 backend buckets).
    public var taskType: String {
        switch self {
        case .shopping: "shopping"
        case .repairs: "repair"
        default: "chore"
        }
    }

    /// Best-guess inference from an existing task's `task_type` +
    /// title — used in Edit mode to preselect the picker. Falls back
    /// to `.other` when nothing matches.
    public static func from(taskType: String?, title: String?) -> AddHouseholdTaskFormCategory {
        // Hard-coded title keywords mirror the display palette but
        // map onto the form's vocabulary. First match wins.
        let lower = (title ?? "").lowercased()
        if !lower.isEmpty {
            if matchAny(lower, ["cook", "meal", "dinner", "lunch", "breakfast"]) { return .cooking }
            if matchAny(lower, ["dish", "clean", "vacuum", "dust", "mop", "wipe", "scrub",
                                "sweep", "tidy", "bathroom", "bedroom"]) { return .cleaning }
            if matchAny(lower, ["trash", "garbage", "recycle", "recycling", "compost"]) { return .cleaning }
            if matchAny(lower, ["water plants", "plants", "garden", "mow", "lawn", "rake",
                                "leaves", "yard", "weed"]) { return .yardwork }
            if matchAny(lower, ["dog", "cat", "puppy", " pet ", "litter box", "vet "]) { return .pets }
            if matchAny(lower, ["fix", "repair", "replace", "patch", "screw", "leak"]) { return .repairs }
            if matchAny(lower, ["costco", "grocery", "groceries", "shopping", "shop ", "pickup",
                                "pick up", "store run", "errand", "buy "]) { return .shopping }
        }
        switch taskType?.lowercased() {
        case "shopping": return .shopping
        case "repair":   return .repairs
        default: return .other
        }
    }

    private static func matchAny(_ haystack: String, _ needles: [String]) -> Bool {
        needles.contains(where: { haystack.contains($0) })
    }
}

/// The five recurrence options exposed by the form. `custom` reveals
/// the "every N days / weeks / months" sub-form.
public enum AddHouseholdTaskRecurrence: String, CaseIterable, Sendable {
    case oneTime = "one_time"
    case daily
    case weekly
    case monthly
    case custom

    public var label: String {
        switch self {
        case .oneTime: "One-time"
        case .daily:   "Daily"
        case .weekly:  "Weekly"
        case .monthly: "Monthly"
        case .custom:  "Custom"
        }
    }

    public var isRecurring: Bool {
        self != .oneTime
    }
}

/// Unit for the custom recurrence sub-form.
public enum AddHouseholdTaskCustomUnit: String, CaseIterable, Sendable {
    case days
    case weeks
    case months

    public var label: String {
        switch self {
        case .days:   "Days"
        case .weeks:  "Weeks"
        case .months: "Months"
        }
    }

    /// RRULE FREQ token paired with the unit.
    public var rruleFreq: String {
        switch self {
        case .days:   "DAILY"
        case .weeks:  "WEEKLY"
        case .months: "MONTHLY"
        }
    }
}

/// Stable identifiers for every editable field in the Add/Edit
/// Household Task form. All non-enum payload uses `FormFieldState`
/// for dirty + validation tracking; the typed enums above ride
/// alongside via the stable raw-value mapping.
public enum AddHouseholdTaskField: String, CaseIterable, Sendable {
    case title
    case category
    case assignedTo
    case recurrence
    case customInterval
    case customUnit
    case dueAt
    case notes
}

/// One assignable member surfaced by the picker. Built from
/// `OccupantDTO` so the form doesn't depend on the wire shape.
public struct HouseholdTaskAssignableMember: Sendable, Hashable, Identifiable {
    public let id: String
    public let displayName: String
    public let initials: String

    public init(id: String, displayName: String, initials: String) {
        self.id = id
        self.displayName = displayName
        self.initials = initials
    }

    public static func from(_ occupant: OccupantDTO) -> HouseholdTaskAssignableMember? {
        guard occupant.isActive else { return nil }
        let name = occupant.displayName?.trimmingCharacters(in: .whitespaces)
            ?? occupant.username?.trimmingCharacters(in: .whitespaces)
            ?? ""
        let display = name.isEmpty ? "Member \(occupant.userId.prefix(4).uppercased())" : name
        let initials = display
            .split(separator: " ")
            .compactMap { $0.first.map(String.init) }
            .prefix(2)
            .joined()
            .uppercased()
        return HouseholdTaskAssignableMember(
            id: occupant.userId,
            displayName: display,
            initials: initials.isEmpty ? "··" : initials
        )
    }
}

/// Render state for the Add/Edit Household Task form.
public enum AddHouseholdTaskFormState: Sendable, Equatable {
    case loading
    case editing
    case error(String)
}

/// ViewModel for `AddHouseholdTaskFormView`. Holds the four
/// scaffold-relevant signals (`state`, `fields`, `isSaving`,
/// `shouldDismiss`) plus typed picker selections (`category`,
/// `recurrence`, `customUnit`).
@Observable
@MainActor
public final class AddHouseholdTaskFormViewModel {
    public private(set) var state: AddHouseholdTaskFormState = .editing
    public var fields: [AddHouseholdTaskField: FormFieldState] = [:]
    public private(set) var isSaving: Bool = false
    public var toast: ToastMessage?
    public private(set) var shakeTrigger: Int = 0
    public private(set) var shouldDismiss: Bool = false
    /// Newly-created task id surfaced after a successful Add — the
    /// caller can pop the form and push the detail in one step. Nil
    /// in Edit mode.
    public private(set) var createdTaskId: String?

    /// Loaded roster for the assignee picker. Lives independently of
    /// the form load so a slow members fetch can't block the title /
    /// recurrence editor.
    public private(set) var assignableMembers: [HouseholdTaskAssignableMember] = []

    public let homeId: String
    public let taskId: String?
    private let api: APIClient

    public var isEditing: Bool { taskId != nil }

    public init(
        homeId: String,
        taskId: String? = nil,
        api: APIClient = .shared
    ) {
        self.homeId = homeId
        self.taskId = taskId
        self.api = api
        for field in AddHouseholdTaskField.allCases {
            fields[field] = FormFieldState(id: field.rawValue, originalValue: defaultValue(for: field))
        }
        // Re-run validators against seed values so the initial
        // aggregate matches the schema's view of "valid".
        primeErrors()
        // Seed the editing state directly when we have no `taskId` —
        // Add mode has nothing to fetch. Edit mode flips to .loading
        // until `load()` finishes hydrating.
        state = taskId == nil ? .editing : .loading
    }

    // MARK: - Lifecycle

    /// Initial load. In Add mode this only fetches the assignee
    /// roster; in Edit mode it also fetches the task itself and
    /// hydrates the field map.
    ///
    /// Note: `backend/routes/home.js:4170` only exposes a list
    /// endpoint — no GET-by-id today. Edit mode fetches the full
    /// list and matches client-side.
    public func load() async {
        if taskId == nil {
            await loadMembers()
            return
        }
        state = .loading
        do {
            let response: GetHomeTasksResponse = try await api.request(
                HomesEndpoints.tasks(homeId: homeId)
            )
            guard let match = response.tasks.first(where: { $0.id == taskId }) else {
                state = .error("Couldn't find that task.")
                return
            }
            hydrate(from: match)
            state = .editing
            Task { await self.loadMembers() }
        } catch {
            state = .error((error as? APIError)?.errorDescription ?? "Couldn't load the task.")
        }
    }

    public func refresh() async {
        await load()
    }

    /// Update a single field's raw value and re-run its validator.
    public func update(_ field: AddHouseholdTaskField, to value: String) {
        guard var snapshot = fields[field] else { return }
        snapshot.value = value
        snapshot.touched = true
        snapshot.error = validator(for: field).validate(value)
        fields[field] = snapshot
    }

    /// Typed setter for the category chip.
    public func selectCategory(_ value: AddHouseholdTaskFormCategory) {
        update(.category, to: value.rawValue)
    }

    /// Typed setter for the recurrence picker. Clears custom-only
    /// fields when switching away from `.custom` so a stale interval
    /// doesn't survive into the wire body, and re-runs the custom
    /// interval validator on the way in so a previously-typed bad
    /// value surfaces an error immediately.
    public func selectRecurrence(_ value: AddHouseholdTaskRecurrence) {
        update(.recurrence, to: value.rawValue)
        if value != .custom {
            update(.customInterval, to: "1")
            update(.customUnit, to: AddHouseholdTaskCustomUnit.weeks.rawValue)
        } else {
            // Re-validate the current customInterval value against
            // the now-active strict rule without flipping the
            // touched flag — the user hasn't typed anything new.
            if var snapshot = fields[.customInterval] {
                snapshot.error = validator(for: .customInterval).validate(snapshot.value)
                fields[.customInterval] = snapshot
            }
        }
    }

    /// Typed setter for the custom recurrence unit.
    public func selectCustomUnit(_ value: AddHouseholdTaskCustomUnit) {
        update(.customUnit, to: value.rawValue)
    }

    /// Single-select assignee. Pass `nil` for the "Unassigned (any
    /// member)" default. See top-of-file note on multi-assignee.
    public func selectAssignee(_ memberId: String?) {
        update(.assignedTo, to: memberId ?? "")
    }

    // MARK: - Typed reads

    public var selectedCategory: AddHouseholdTaskFormCategory {
        AddHouseholdTaskFormCategory(rawValue: fields[.category]?.value ?? "")
            ?? .other
    }

    public var selectedRecurrence: AddHouseholdTaskRecurrence {
        AddHouseholdTaskRecurrence(rawValue: fields[.recurrence]?.value ?? "")
            ?? .oneTime
    }

    public var selectedCustomUnit: AddHouseholdTaskCustomUnit {
        AddHouseholdTaskCustomUnit(rawValue: fields[.customUnit]?.value ?? "")
            ?? .weeks
    }

    public var selectedAssigneeId: String? {
        let id = fields[.assignedTo]?.value ?? ""
        return id.isEmpty ? nil : id
    }

    public var dueDate: Date? {
        Self.parseISODay(fields[.dueAt]?.value ?? "")
    }

    public func setDueDate(_ date: Date?) {
        update(.dueAt, to: date.map(Self.formatISODay) ?? "")
    }

    // MARK: - Aggregate

    /// Aggregate dirty + validity across every field. Drives the
    /// FormShell save action.
    public var aggregate: FormAggregate {
        FormAggregate(fields: AddHouseholdTaskField.allCases.compactMap { fields[$0] })
    }

    public var isValid: Bool {
        aggregate.isValid
    }

    /// Add mode treats every field as "new" so the Save button is
    /// active the moment a valid title lands. Edit mode keeps the
    /// dirty gate so an untouched task can't accidentally re-save.
    public var isDirty: Bool {
        isEditing ? aggregate.isDirty : true
    }

    /// Whether the custom recurrence sub-form should render.
    public var showsCustomRecurrenceSubForm: Bool {
        selectedRecurrence == .custom
    }

    // MARK: - Submit

    /// Run every validator. Returns the first invalid field id, if any.
    @discardableResult
    public func validateAll() -> AddHouseholdTaskField? {
        var firstInvalid: AddHouseholdTaskField?
        for field in AddHouseholdTaskField.allCases {
            guard var snapshot = fields[field] else { continue }
            let message = validator(for: field).validate(snapshot.value)
            snapshot.error = message
            snapshot.touched = true
            fields[field] = snapshot
            if firstInvalid == nil, message != nil { firstInvalid = field }
        }
        return firstInvalid
    }

    /// Submit. Returns true on success.
    @discardableResult
    public func save() async -> Bool {
        if validateAll() != nil {
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
        isSaving = true
        defer { isSaving = false }
        do {
            if let taskId {
                let response: HomeTaskResponse = try await api.request(
                    HomesEndpoints.updateTask(
                        homeId: homeId,
                        taskId: taskId,
                        request: buildUpdateRequest()
                    )
                )
                hydrate(from: response.task)
                toast = ToastMessage(text: "Task updated.", kind: .success)
            } else {
                let response: HomeTaskResponse = try await api.request(
                    HomesEndpoints.createTask(
                        homeId: homeId,
                        request: buildCreateRequest()
                    )
                )
                createdTaskId = response.task.id
                toast = ToastMessage(text: "Task added.", kind: .success)
            }
            shouldDismiss = true
            return true
        } catch {
            toast = ToastMessage(
                text: (error as? APIError)?.errorDescription
                    ?? (isEditing ? "Couldn't update the task." : "Couldn't add the task."),
                kind: .error
            )
            return false
        }
    }

    public func acknowledgeDismiss() {
        shouldDismiss = false
    }

    // MARK: - Members

    private func loadMembers() async {
        do {
            let response: OccupantsResponse = try await api.request(
                HomesEndpoints.listOccupants(homeId: homeId)
            )
            assignableMembers = response.occupants.compactMap(HouseholdTaskAssignableMember.from)
        } catch {
            // Picker keeps the assignee snapshot value but only shows
            // "Unassigned (any member)" as an option — the editor
            // doesn't gate on members loading, so swallow the error.
            assignableMembers = []
        }
    }

    // MARK: - Hydration

    private func hydrate(from task: HomeTaskDTO) {
        seed(.title, task.title)
        seed(.notes, task.description ?? "")
        seed(.assignedTo, task.assignedTo ?? "")
        seed(.dueAt, Self.isoDayOnly(from: task.dueAt) ?? "")
        let category = AddHouseholdTaskFormCategory.from(
            taskType: task.taskType,
            title: task.title
        )
        seed(.category, category.rawValue)
        let parsed = Self.parseRecurrence(task.recurrenceRule)
        seed(.recurrence, parsed.recurrence.rawValue)
        seed(.customInterval, "\(parsed.interval)")
        seed(.customUnit, parsed.unit.rawValue)
        primeErrors()
    }

    private func defaultValue(for field: AddHouseholdTaskField) -> String {
        switch field {
        case .category:       AddHouseholdTaskFormCategory.other.rawValue
        case .recurrence:     AddHouseholdTaskRecurrence.oneTime.rawValue
        case .customInterval: "1"
        case .customUnit:     AddHouseholdTaskCustomUnit.weeks.rawValue
        default: ""
        }
    }

    private func seed(_ field: AddHouseholdTaskField, _ value: String) {
        var snapshot = FormFieldState(id: field.rawValue, originalValue: value)
        snapshot.error = validator(for: field).validate(value)
        fields[field] = snapshot
    }

    /// Re-run every validator against the current values without
    /// flipping touched flags — used after a seed so the aggregate
    /// reflects the initial pose.
    private func primeErrors() {
        for field in AddHouseholdTaskField.allCases {
            guard var snapshot = fields[field] else { continue }
            snapshot.error = validator(for: field).validate(snapshot.value)
            fields[field] = snapshot
        }
    }

    // MARK: - Validators

    private func validator(for field: AddHouseholdTaskField) -> FormValidator {
        switch field {
        case .title:
            return .all([.required("Title"), .maxLength(80)])
        case .recurrence:
            return FormValidator { value in
                AddHouseholdTaskRecurrence(rawValue: value) == nil
                    ? "Pick a recurrence."
                    : nil
            }
        case .category:
            return FormValidator { value in
                AddHouseholdTaskFormCategory(rawValue: value) == nil
                    ? "Pick a category."
                    : nil
            }
        case .customInterval:
            // Only enforced when the parent picker is .custom — we
            // pass the live recurrence + unit as `Sendable` value
            // snapshots so the validator closure stays
            // `@Sendable`-safe under strict concurrency.
            return Self.customIntervalValidator(
                recurrence: selectedRecurrence,
                unit: selectedCustomUnit
            )
        case .customUnit:
            return FormValidator { value in
                AddHouseholdTaskCustomUnit(rawValue: value) == nil
                    ? "Pick a unit."
                    : nil
            }
        default:
            return FormValidator { _ in nil }
        }
    }

    // MARK: - Wire payload

    private func buildCreateRequest() -> CreateHomeTaskRequest {
        let snapshot = wireSnapshot()
        return CreateHomeTaskRequest(
            taskType: snapshot.taskType,
            title: snapshot.title,
            description: snapshot.description,
            assignedTo: snapshot.assignedTo,
            dueAt: snapshot.dueAt,
            recurrenceRule: snapshot.recurrenceRule,
            priority: nil
        )
    }

    private func buildUpdateRequest() -> UpdateHomeTaskRequest {
        let snapshot = wireSnapshot()
        // Edit mode sends every field on the wire — the form's job
        // is to keep the row's authoritative pose in lockstep with
        // the user's edits. `nil` is reserved for fields the schema
        // doesn't support yet.
        return UpdateHomeTaskRequest(
            status: nil,
            title: snapshot.title,
            description: snapshot.description,
            assignedTo: snapshot.assignedTo,
            dueAt: snapshot.dueAt,
            // See top-of-file note: backend allowlist drops
            // recurrence_rule today. We still send it so the wire
            // tracks user intent.
            recurrenceRule: snapshot.recurrenceRule,
            priority: nil,
            completedAt: nil
        )
    }

    private struct WireSnapshot {
        let taskType: String
        let title: String
        let description: String?
        let assignedTo: String?
        let dueAt: String?
        let recurrenceRule: String?
    }

    private func wireSnapshot() -> WireSnapshot {
        let title = (fields[.title]?.value ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let notes = (fields[.notes]?.value ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let assignee = (fields[.assignedTo]?.value ?? "")
            .trimmingCharacters(in: .whitespaces)
        let due = (fields[.dueAt]?.value ?? "")
            .trimmingCharacters(in: .whitespaces)
        return WireSnapshot(
            taskType: selectedCategory.taskType,
            title: title,
            description: notes.isEmpty ? nil : notes,
            assignedTo: assignee.isEmpty ? nil : assignee,
            dueAt: due.isEmpty ? nil : due,
            recurrenceRule: buildRecurrenceRule()
        )
    }

    private func buildRecurrenceRule() -> String? {
        switch selectedRecurrence {
        case .oneTime: return nil
        case .daily:   return "FREQ=DAILY"
        case .weekly:  return "FREQ=WEEKLY"
        case .monthly: return "FREQ=MONTHLY"
        case .custom:
            let interval = max(1, Int(fields[.customInterval]?.value ?? "") ?? 1)
            return "FREQ=\(selectedCustomUnit.rruleFreq);INTERVAL=\(interval)"
        }
    }

    // MARK: - Parsing

    /// Build a custom-interval validator parameterised on a snapshot
    /// of the current recurrence + unit. Keeping the closure `Sendable`
    /// — i.e. free of `self` capture — requires we pass these as value
    /// arguments rather than reading them inside the closure body.
    static func customIntervalValidator(
        recurrence: AddHouseholdTaskRecurrence,
        unit: AddHouseholdTaskCustomUnit
    ) -> FormValidator {
        let unitLabel = unit.label.lowercased()
        return FormValidator { value in
            guard recurrence == .custom else { return nil }
            guard let n = Int(value.trimmingCharacters(in: .whitespaces)) else {
                return "Enter a whole number of \(unitLabel)."
            }
            if n < 1 { return "Must be at least 1." }
            if n > 365 { return "Must be 365 or fewer." }
            return nil
        }
    }

    struct ParsedRecurrence {
        let recurrence: AddHouseholdTaskRecurrence
        let interval: Int
        let unit: AddHouseholdTaskCustomUnit
    }

    /// Map a server `recurrence_rule` to the form's picker selections.
    /// Anything with an `INTERVAL=` larger than 1 lands on `.custom`
    /// so the round-trip is editable; vanilla `FREQ=…` rules land on
    /// the matching simple option.
    static func parseRecurrence(_ rule: String?) -> ParsedRecurrence {
        let raw = rule?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? ""
        guard !raw.isEmpty else {
            return ParsedRecurrence(recurrence: .oneTime, interval: 1, unit: .weeks)
        }
        let interval = parseInterval(raw)
        let unit: AddHouseholdTaskCustomUnit
        let baseRecurrence: AddHouseholdTaskRecurrence
        switch true {
        case raw.contains("freq=daily"):
            baseRecurrence = .daily
            unit = .days
        case raw.contains("freq=weekly"):
            baseRecurrence = .weekly
            unit = .weeks
        case raw.contains("freq=monthly"):
            baseRecurrence = .monthly
            unit = .months
        default:
            // Unknown rule shapes (e.g. literal "Weekly · Tue") land
            // on .custom with a 1-unit interval so the user can
            // re-pick from a clean slate.
            return ParsedRecurrence(recurrence: .custom, interval: 1, unit: .weeks)
        }
        if interval <= 1 {
            return ParsedRecurrence(recurrence: baseRecurrence, interval: 1, unit: unit)
        }
        return ParsedRecurrence(recurrence: .custom, interval: interval, unit: unit)
    }

    private static func parseInterval(_ lowered: String) -> Int {
        guard let range = lowered.range(of: "interval=") else { return 1 }
        let tail = lowered[range.upperBound...]
        let token = tail.split(separator: ";", maxSplits: 1).first.map(String.init)
            ?? String(tail)
        return Int(token.trimmingCharacters(in: .whitespaces)) ?? 1
    }

    private static func isoDayOnly(from iso: String?) -> String? {
        guard let iso, !iso.isEmpty else { return nil }
        // Backend can return either `yyyy-MM-dd` or a full ISO
        // timestamp. The form only edits the day; strip the time.
        if let dash = iso.firstIndex(of: "T") {
            return String(iso[..<dash])
        }
        return iso.prefix(10).isEmpty ? nil : String(iso.prefix(10))
    }

    static func parseISODay(_ value: String) -> Date? {
        let trimmed = value.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return nil }
        return makeDayFormatter().date(from: trimmed)
    }

    static func formatISODay(_ date: Date) -> String {
        makeDayFormatter().string(from: date)
    }

    private static func makeDayFormatter() -> DateFormatter {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .iso8601)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(secondsFromGMT: 0)
        f.dateFormat = "yyyy-MM-dd"
        return f
    }
}
