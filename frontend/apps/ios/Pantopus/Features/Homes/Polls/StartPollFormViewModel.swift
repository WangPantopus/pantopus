//
//  StartPollFormViewModel.swift
//  Pantopus
//
//  ViewModel for the Start-a-Poll form (P2.5) — composes a `CreatePollRequest`
//  POSTed to `/api/homes/:id/polls` (route `backend/routes/home.js:7058`).
//
//  Five client kinds drive the options UX:
//    - single-choice → wire `single_choice`
//    - multi-choice  → wire `multiple_choice`
//    - ranked        → wire `ranking`
//    - yes-no        → wire `yes_no` (auto-fills `Yes` + `No`, locked)
//    - approval      → wire `multiple_choice` (approval voting is multi-pick;
//      the backend collapses both into the same `poll_type`. Client keeps
//      them distinct so the Voting screen can later render the approval-
//      specific UI without a wire break.)
//

import Foundation
import Observation
import SwiftUI

/// Client-side poll kinds the form lets the user pick. Each kind maps to
/// a backend `poll_type` via `wirePollType`; the form also reconfigures
/// the options list per-kind (yes-no auto-fills, choice kinds let the
/// user add/remove rows).
public enum StartPollKind: String, CaseIterable, Sendable, Hashable, Identifiable {
    case singleChoice
    case multiChoice
    case ranked
    case yesNo
    case approval

    public var id: String { rawValue }

    /// User-facing label rendered in the kind picker.
    public var label: String {
        switch self {
        case .singleChoice: "Single choice"
        case .multiChoice: "Multi-choice"
        case .ranked: "Ranked"
        case .yesNo: "Yes / No"
        case .approval: "Approval"
        }
    }

    /// Short helper rendered beneath the picker chip — explains the
    /// voting mechanic in one line.
    public var helper: String {
        switch self {
        case .singleChoice: "Voters pick one option."
        case .multiChoice: "Voters pick any number of options."
        case .ranked: "Voters rank the options in order."
        case .yesNo: "Yes or No — a quick binary read."
        case .approval: "Voters approve every option they're okay with."
        }
    }

    /// Lucide icon for the kind chip. We pick from the in-palette
    /// `PantopusIcon` cases — `.checkSquare` isn't in the set, so multi-
    /// choice borrows `.checkCheck` (the closest "tick-multiple" glyph).
    public var icon: PantopusIcon {
        switch self {
        case .singleChoice: .clipboardList
        case .multiChoice: .checkCheck
        case .ranked: .listChecks
        case .yesNo: .checkCircle
        case .approval: .thumbsUp
        }
    }

    /// Backend `poll_type` enum. Approval collapses to `multiple_choice`
    /// because the backend has no separate approval shape today.
    public var wirePollType: String {
        switch self {
        case .singleChoice: "single_choice"
        case .multiChoice, .approval: "multiple_choice"
        case .ranked: "ranking"
        case .yesNo: "yes_no"
        }
    }

    /// True when the options list is user-editable. Yes/No auto-fills.
    public var allowsCustomOptions: Bool { self != .yesNo }
}

/// Audience visibility model for a poll. Mirrors the backend `visibility`
/// field — `all_members` sends `nil` (server default), `selected` sends a
/// JSON-encoded `selected:<userId,…>` string that the backend treats as
/// an opaque scope token. The default for now is "all members" until a
/// dedicated audience routing endpoint lands; selected just records the
/// ids client-side.
public enum StartPollAudience: Sendable, Hashable {
    case allMembers
    case selectedMembers(Set<String>)

    public var isSelective: Bool {
        if case .selectedMembers = self { return true }
        return false
    }

    public var selectedIds: Set<String> {
        if case let .selectedMembers(ids) = self { return ids }
        return []
    }
}

/// Single editable option in the form. `id` is a stable UUID assigned at
/// creation so SwiftUI can diff rows across edits without losing focus.
public struct StartPollOption: Identifiable, Equatable, Sendable {
    public let id: String
    public var label: String
    /// True when the row is locked (yes-no auto-fills).
    public var isLocked: Bool

    public init(id: String = UUID().uuidString, label: String = "", isLocked: Bool = false) {
        self.id = id
        self.label = label
        self.isLocked = isLocked
    }
}

/// Render state for the Start-a-Poll form.
public enum StartPollFormState: Sendable, Equatable {
    case editing
    case submitting
    case success(pollId: String)
    case error(String)
}

/// Bounds the choice-kind options list. Yes/No bypasses these via
/// `allowsCustomOptions == false`.
public enum StartPollBounds {
    public static let minOptions = 2
    public static let maxOptions = 10
    public static let questionMin = 5
    public static let questionMax = 200
    /// `closesAt` must be at least this many seconds in the future at
    /// submit time. 1 hour matches the prompt.
    public static let closeMinSecondsAhead: TimeInterval = 60 * 60
}

/// ViewModel backing `StartPollFormView`. Strict-concurrency, MainActor-
/// isolated, `@Observable`. Owns dirty + valid + saving + submission.
@Observable
@MainActor
final class StartPollFormViewModel {
    public private(set) var state: StartPollFormState = .editing

    /// Question text — bound to a `FormFieldState` for dirty + error
    /// tracking, mirroring the InviteOwner pattern.
    public var question: FormFieldState

    public private(set) var kind: StartPollKind {
        didSet { reconfigureOptionsForKind() }
    }

    public private(set) var options: [StartPollOption]

    /// Audience selection — defaults to all household members.
    public var audience: StartPollAudience = .allMembers

    /// Closing date. Optional during edits — required at submit time.
    public var closesAt: Date?

    /// Anonymity toggle.
    public var isAnonymous: Bool = false

    /// Banner toast for the submit happy/error path.
    public var toast: ToastMessage?

    /// Bumped each time `submit()` runs on an invalid form so the view
    /// can shake the first invalid group.
    public private(set) var shakeTrigger: Int = 0

    /// Set true after a successful create so the navigation host can
    /// pop the form.
    public private(set) var shouldDismiss: Bool = false

    /// Available household members for the audience picker — hydrated by
    /// the view in `.task`. While loading the picker shows a shimmer
    /// row and the form treats audience as `.allMembers`.
    public private(set) var members: [StartPollMember] = []
    public private(set) var isLoadingMembers: Bool = false

    private let homeId: String
    private let api: APIClient
    /// Initial kind — used to decide whether kind changes should mark
    /// the form dirty (a user who lands on yes-no from the quickstart
    /// tile hasn't "changed" anything yet).
    private let initialKind: StartPollKind
    /// Inject a stable "now" for tests.
    private let now: @Sendable () -> Date
    /// Inject a stable id generator for tests.
    private let idGenerator: @Sendable () -> String

    public init(
        homeId: String,
        api: APIClient = .shared,
        kind: StartPollKind = .singleChoice,
        now: @escaping @Sendable () -> Date = Date.init,
        idGenerator: @escaping @Sendable () -> String = { UUID().uuidString }
    ) {
        self.homeId = homeId
        self.api = api
        self.kind = kind
        initialKind = kind
        self.now = now
        self.idGenerator = idGenerator
        question = FormFieldState(id: "question", originalValue: "")
        options = StartPollFormViewModel.makeInitialOptions(
            for: kind,
            generate: idGenerator
        )
    }

    // MARK: - Mutators

    /// Update the question text and re-run its validator.
    public func updateQuestion(_ value: String) {
        question.value = value
        question.touched = true
        question.error = validateQuestion(value)
    }

    /// Switch the active poll kind. Reconfigures the options list per
    /// the kind contract (yes/no auto-fills; choice kinds keep at
    /// least 2 rows; switching to ranked just renames the prompt).
    public func setKind(_ next: StartPollKind) {
        guard next != kind else { return }
        kind = next
    }

    /// Append an empty option row. Capped at `maxOptions`.
    public func addOption() {
        guard kind.allowsCustomOptions else { return }
        guard options.count < StartPollBounds.maxOptions else { return }
        options.append(StartPollOption(id: idGenerator(), label: ""))
    }

    /// Remove the option with `id`. No-op when this would drop below
    /// the minimum or when the option is locked.
    public func removeOption(id: String) {
        guard kind.allowsCustomOptions else { return }
        guard options.count > StartPollBounds.minOptions else { return }
        guard let index = options.firstIndex(where: { $0.id == id }) else { return }
        if options[index].isLocked { return }
        options.remove(at: index)
    }

    /// Update an option's label in place.
    public func updateOption(id: String, to value: String) {
        guard let index = options.firstIndex(where: { $0.id == id }) else { return }
        if options[index].isLocked { return }
        options[index].label = value
    }

    /// Toggle a single member in the audience selection. If the form
    /// was previously `.allMembers` the first toggle seeds the set.
    public func toggleMember(_ userId: String) {
        var current = audience.selectedIds
        if current.contains(userId) {
            current.remove(userId)
        } else {
            current.insert(userId)
        }
        audience = current.isEmpty ? .allMembers : .selectedMembers(current)
    }

    /// Select the "All members" branch.
    public func selectAllMembers() {
        audience = .allMembers
    }

    // MARK: - Members hydration

    /// Hydrate the household members list for the audience picker.
    /// Idempotent — safe to call from `.task` and refresh.
    public func loadMembers() async {
        isLoadingMembers = true
        defer { isLoadingMembers = false }
        do {
            let response: OccupantsResponse = try await api.request(
                HomesEndpoints.listOccupants(homeId: homeId)
            )
            members = response.occupants
                .filter { $0.isActive }
                .map { occupant in
                    StartPollMember(
                        id: occupant.userId,
                        name: occupant.displayName
                            ?? occupant.username
                            ?? "Member"
                    )
                }
                .sorted { lhs, rhs in lhs.name.localizedStandardCompare(rhs.name) == .orderedAscending }
        } catch {
            // Members are an optional convenience — failing to fetch
            // just leaves the picker in "all members" mode. The poll
            // submit path doesn't depend on this list.
            members = []
        }
    }

    // MARK: - Aggregate

    public var isDirty: Bool {
        let trimmed = question.value.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { return true }
        if closesAt != nil { return true }
        if isAnonymous { return true }
        if audience.isSelective { return true }
        if kind != initialKind { return true }
        if options.contains(where: { !$0.label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !$0.isLocked }) {
            return true
        }
        return false
    }

    public var isValid: Bool {
        firstValidationError() == nil
    }

    public var isSubmitting: Bool {
        if case .submitting = state { return true }
        return false
    }

    // MARK: - Submit

    /// Validate + POST. Returns true on success.
    @discardableResult
    public func submit() async -> Bool {
        if let error = firstValidationError() {
            shakeTrigger &+= 1
            toast = ToastMessage(text: error, kind: .error)
            // Touch + tag every visible error so inline messages appear.
            applyValidationTouches()
            return false
        }
        if !NetworkMonitor.shared.isOnline {
            toast = ToastMessage(
                text: "You're offline. Try again when you're back online.",
                kind: .error
            )
            return false
        }
        state = .submitting
        let request = buildRequest()
        do {
            let response: HomePollResponse = try await api.request(
                HomesEndpoints.createPoll(homeId: homeId, request: request)
            )
            state = .success(pollId: response.poll.id)
            toast = ToastMessage(text: "Poll started.", kind: .success)
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            shouldDismiss = true
            return true
        } catch {
            let message = (error as? APIError)?.errorDescription
                ?? "Couldn't start the poll."
            state = .error(message)
            toast = ToastMessage(text: message, kind: .error)
            return false
        }
    }

    /// Invoked by the view once dismissal has taken effect.
    public func acknowledgeDismiss() {
        shouldDismiss = false
    }

    // MARK: - Validation

    /// Returns the first user-facing validation error or nil when valid.
    /// Order: question → options → close date.
    public func firstValidationError() -> String? {
        if let error = validateQuestion(question.value) { return error }
        if kind.allowsCustomOptions {
            let labels = options.map { $0.label.trimmingCharacters(in: .whitespacesAndNewlines) }
            let nonEmpty = labels.filter { !$0.isEmpty }
            if nonEmpty.count < StartPollBounds.minOptions {
                return "Add at least \(StartPollBounds.minOptions) options."
            }
            let normalised = Set(nonEmpty.map { $0.lowercased() })
            if normalised.count < nonEmpty.count {
                return "Each option must be unique."
            }
        }
        guard let closesAt else {
            return "Pick a close date."
        }
        let cutoff = now().addingTimeInterval(StartPollBounds.closeMinSecondsAhead)
        if closesAt < cutoff {
            return "Close date must be at least 1 hour in the future."
        }
        return nil
    }

    private func validateQuestion(_ value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return "Question is required."
        }
        if trimmed.count < StartPollBounds.questionMin {
            return "Question must be at least \(StartPollBounds.questionMin) characters."
        }
        if trimmed.count > StartPollBounds.questionMax {
            return "Question must be \(StartPollBounds.questionMax) characters or fewer."
        }
        return nil
    }

    /// Apply touched-flags so the field-level error inlines on submit
    /// even when the user hasn't edited yet.
    private func applyValidationTouches() {
        question.touched = true
        question.error = validateQuestion(question.value)
    }

    // MARK: - Per-kind options reconfigure

    private func reconfigureOptionsForKind() {
        if !kind.allowsCustomOptions {
            options = StartPollFormViewModel.makeInitialOptions(for: kind, generate: idGenerator)
            return
        }
        // Switching between choice kinds preserves user-typed labels.
        // If we were on yes-no (locked Yes/No), clear and re-seed two
        // empty rows so the user can type their own.
        if options.contains(where: { $0.isLocked }) {
            options = StartPollFormViewModel.makeInitialOptions(for: kind, generate: idGenerator)
            return
        }
        // Ensure at least two rows exist after the switch.
        while options.count < StartPollBounds.minOptions {
            options.append(StartPollOption(id: idGenerator(), label: ""))
        }
    }

    private static func makeInitialOptions(
        for kind: StartPollKind,
        generate: @Sendable () -> String
    ) -> [StartPollOption] {
        if !kind.allowsCustomOptions {
            return [
                StartPollOption(id: generate(), label: "Yes", isLocked: true),
                StartPollOption(id: generate(), label: "No", isLocked: true)
            ]
        }
        return [
            StartPollOption(id: generate(), label: ""),
            StartPollOption(id: generate(), label: "")
        ]
    }

    // MARK: - Wire shape

    /// Build the POST body. Validation must have passed before calling.
    func buildRequest() -> CreatePollRequest {
        let title = question.value.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedLabels: [String]
        if kind.allowsCustomOptions {
            trimmedLabels = options
                .map { $0.label.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
        } else {
            trimmedLabels = options.map(\.label)
        }
        let closesIso = closesAt.map(StartPollFormViewModel.formatIso)
        let visibility = audienceWireValue()
        return CreatePollRequest(
            title: title,
            description: nil,
            pollType: kind.wirePollType,
            options: trimmedLabels.map { CreatePollRequest.Option(label: $0) },
            closesAt: closesIso,
            visibility: visibility
        )
    }

    /// Encode the audience selection into the backend's `visibility`
    /// field. `nil` → server default (all members). `selected:<ids>` →
    /// opaque scope token; once the backend ships a structured audience
    /// model we'll move this into a typed field. Anonymous polls layer
    /// `anonymous` onto the scope so the renderer can hide voter
    /// identities client-side until the server enforces it.
    private func audienceWireValue() -> String? {
        var components: [String] = []
        if case let .selectedMembers(ids) = audience, !ids.isEmpty {
            components.append("selected:" + ids.sorted().joined(separator: ","))
        }
        if isAnonymous {
            components.append("anonymous")
        }
        return components.isEmpty ? nil : components.joined(separator: ";")
    }

    static func formatIso(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.string(from: date)
    }
}

/// Minimal projection of a household member into the audience picker.
public struct StartPollMember: Identifiable, Equatable, Sendable {
    public let id: String
    public let name: String

    public init(id: String, name: String) {
        self.id = id
        self.name = name
    }
}
