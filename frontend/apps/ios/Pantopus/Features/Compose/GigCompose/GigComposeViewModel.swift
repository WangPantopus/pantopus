//
//  GigComposeViewModel.swift
//  Pantopus
//
//  Drives the 6-step Post-a-Task wizard (P2.2). State machine + chrome
//  derivation mirror `AddHomeWizardViewModel`; submission posts to
//  `POST /api/gigs` via `GigsEndpoints.create(...)`.
//

import Foundation
import Observation

// swiftlint:disable file_length

/// One-shot navigation events the host view consumes.
public enum GigComposeOutboundEvent: Sendable, Equatable {
    /// Pop the wizard with no further navigation.
    case dismiss
    /// Pop the wizard and navigate to the newly-created gig's detail.
    case openGigDetail(gigId: String)
}

@Observable
@MainActor
final class GigComposeViewModel: WizardModel {
    // MARK: - Public state

    /// Live form snapshot — mirrored into `@SceneStorage` so the wizard
    /// can be restored after process death.
    private(set) var form: GigComposeFormState

    /// True while the final `POST /api/gigs` is in flight.
    private(set) var isSubmitting: Bool = false

    /// User-facing error message attached to the active step. Cleared on
    /// any successful step transition.
    private(set) var errorMessage: String?

    /// Holds the new gig's id once `submit()` succeeds so the success
    /// step's primary CTA can route to the detail.
    private(set) var createdGigId: String?

    /// One-shot navigation events the host view consumes.
    var pendingEvent: GigComposeOutboundEvent?

    /// E.1 — the composer picker sheet currently presented over the wizard,
    /// or nil. Transient UI state — deliberately not mirrored into
    /// `@SceneStorage` (a half-open sheet shouldn't survive process death).
    var activePickerSheet: GigPickerSheet?

    // MARK: - Private dependencies

    private let api: APIClient
    private let location: any LocationProviding
    private let isOnlineProvider: @MainActor () -> Bool

    /// B.3 — in-flight debounce for the Magic Task archetype parse.
    private var detectionTask: Task<Void, Never>?

    // MARK: - Init

    init(
        api: APIClient = .shared,
        location: any LocationProviding = FallbackLocationProvider.shared,
        initialState: GigComposeFormState = .empty,
        // Defaults to the live NetworkMonitor in production. Tests inject
        // a closure returning a fixed value so the simulator's
        // NWPathMonitor doesn't gate `submit()` on CI runners.
        isOnlineProvider: @escaping @MainActor () -> Bool = { NetworkMonitor.shared.isOnline }
    ) {
        self.api = api
        self.location = location
        self.isOnlineProvider = isOnlineProvider
        form = initialState
    }

    /// Replace the in-memory form state from scene storage on first
    /// appear. No-op once the wizard has progressed past the restore.
    func restore(from snapshot: GigComposeFormState) {
        guard form == .empty else { return }
        form = snapshot
    }
}

extension GigComposeViewModel {
    // MARK: - WizardModel

    var chrome: WizardChrome {
        let step = currentStep
        return WizardChrome(
            title: "Post a task",
            progressLabel: progressLabel(for: step),
            progressFraction: progressFraction(for: step),
            leading: leadingControl(for: step),
            primaryCTALabel: primaryCTALabel(for: step),
            primaryCTAEnabled: primaryEnabled(for: step) && !isSubmitting,
            secondaryCTA: secondaryCTA(for: step),
            isSubmitting: isSubmitting,
            dirty: dirtyForCloseConfirm,
            showsProgressBar: step != .success
        )
    }

    func leadingTapped() {
        switch leadingControl(for: currentStep) {
        case .back: goBack()
        case .close: pendingEvent = .dismiss
        }
    }

    func discardConfirmed() {
        pendingEvent = .dismiss
    }

    func primaryTapped() {
        Task { await advance() }
    }

    #if DEBUG
    func advanceForTesting() async {
        await advance()
    }
    #endif

    func secondaryTapped() {
        switch currentStep {
        case .success:
            // Success step's "Done" — return to the feed.
            pendingEvent = .dismiss
        case .category where form.composeMode == .magic:
            // "Pick category" — drop into the manual archetype picker.
            setComposeMode(.manual)
        default:
            break
        }
    }

    // MARK: - B.3 Magic Task

    /// Switch the step-1 entry mode (Magic describe ⇄ manual picker).
    func setComposeMode(_ mode: ComposeMode) {
        form.composeMode = mode
    }

    /// Update the plain-English describe text and (re)schedule a debounced
    /// archetype parse. Real backend NLP is deferred; `detectArchetype`
    /// is a deterministic keyword match standing in for it.
    func setDescribeText(_ text: String) {
        form.describeText = String(text.prefix(GigComposeLimits.describeMax))
        detectionTask?.cancel()
        let snapshot = form.describeText
        detectionTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 350_000_000)
            guard let self, !Task.isCancelled else { return }
            applyDetection(for: snapshot)
        }
    }

    /// Apply the parsed archetype if the text hasn't changed since the
    /// debounce fired. Mirrors the detected category into `form.category`
    /// so the rest of the wizard + submission consume it.
    func applyDetection(for text: String) {
        guard form.describeText == text else { return }
        let detected = Self.detectArchetype(from: text)
        form.detectedArchetype = detected
        if let detected { form.category = detected }
    }

    /// Deterministic keyword → archetype map (stand-in for backend NLP).
    static func detectArchetype(from text: String) -> GigComposeCategory? {
        let lower = text.lowercased()
        guard lower.count >= 3 else { return nil }
        func has(_ words: [String]) -> Bool {
            words.contains { lower.contains($0) }
        }
        if has(["move", "moving", "haul", "u-haul", "load boxes"]) { return .moving }
        if has(["clean", "tidy", "scrub", "vacuum", "mop"]) { return .cleaning }
        if has([
            "assemble",
            "ikea",
            "furniture",
            "shelf",
            "shelves",
            "mount",
            "drill",
            "fix",
            "repair",
            "install",
            "handy",
            "patch",
            "drywall"
        ]) { return .handyman }
        if has(["dog", "cat", " pet", "puppy", "litter", "groom", "walk"]) { return .petcare }
        if has(["babysit", "nanny", "kids", "child", "daycare"]) { return .childcare }
        if has(["tutor", "lesson", "math", "homework", "test prep", "teach"]) { return .tutoring }
        if has(["deliver", "pickup", "pick up", "drop off", "errand", "courier"]) { return .delivery }
        if has(["wifi", "wi-fi", "computer", "laptop", "printer", "router", "troubleshoot", "setup"]) { return .tech }
        return nil
    }

    // MARK: - Field updates

    func selectCategory(_ category: GigComposeCategory) {
        form.category = category
    }

    func selectEngagementMode(_ mode: GigComposeEngagementMode) {
        switch mode {
        case .oneTime:
            form.scheduleType = .oneTime
            if form.budgetType == .offers { form.budgetType = nil }
        case .recurring:
            form.scheduleType = .recurring
            if form.budgetType == .offers { form.budgetType = nil }
        case .openBidding:
            form.budgetType = .offers
        }
    }

    func setTitle(_ title: String) {
        // Hard-stop typing past the max so the user can't enter
        // server-rejecting values. The validation guard on advance still
        // catches the case where state restored from older builds.
        form.title = String(title.prefix(GigComposeLimits.titleMax))
    }

    func setDescription(_ description: String) {
        form.description = String(description.prefix(GigComposeLimits.descriptionMax))
    }

    /// Append a photo identifier. Caps the list at
    /// `GigComposeLimits.maxPhotos` — extra calls are ignored. Today the
    /// id is a free-form string (a placeholder until a real photo-upload
    /// pipeline lands in P15.5); the wizard treats it as opaque so the
    /// underlying mechanism can swap later.
    func addPhoto(_ id: String) {
        guard form.photoIds.count < GigComposeLimits.maxPhotos else { return }
        form.photoIds.append(id)
    }

    func removePhoto(at index: Int) {
        guard form.photoIds.indices.contains(index) else { return }
        form.photoIds.remove(at: index)
    }

    func selectBudgetType(_ type: GigComposeBudgetType) {
        form.budgetType = type
    }

    func setBudgetMin(_ value: String) {
        form.budgetMin = sanitizeBudget(value)
    }

    func setBudgetMax(_ value: String) {
        form.budgetMax = sanitizeBudget(value)
    }

    func selectScheduleType(_ type: GigComposeScheduleType) {
        form.scheduleType = type
        // Clear the date when leaving "one-time" so it can't bleed past.
        if type != .oneTime { form.scheduledStartISO = nil }
    }

    func setScheduledStart(_ date: Date?) {
        if let date {
            form.scheduledStartISO = ISO8601DateFormatter().string(from: date)
        } else {
            form.scheduledStartISO = nil
        }
    }

    func selectLocationMode(_ mode: GigComposeLocationMode) {
        form.locationMode = mode
    }

    func updatePlaceAddress(line1: String? = nil, city: String? = nil, state: String? = nil, zip: String? = nil) {
        if let line1 { form.placeAddress.line1 = line1 }
        if let city { form.placeAddress.city = city }
        if let state { form.placeAddress.state = state }
        if let zip { form.placeAddress.zip = zip }
    }

    // MARK: - E.1 Composer picker sheets

    /// Present one of the composer's bottom-sheet pickers.
    func presentPicker(_ sheet: GigPickerSheet) {
        activePickerSheet = sheet
    }

    /// Dismiss whichever picker sheet is open.
    func dismissPicker() {
        activePickerSheet = nil
    }

    /// E.1 — set (or clear) the optional deadline. `iso == nil` ⇒ flexible.
    func setDeadline(_ iso: String?) {
        form.deadlineISO = iso
    }

    /// E.1 — choose the cancellation-policy tier.
    func setCancellationPolicy(_ policy: GigCancellationPolicy) {
        form.cancellationPolicy = policy
    }

    /// E.1 — toggle the urgent boost flag.
    func setUrgent(_ isUrgent: Bool) {
        form.isUrgent = isUrgent
    }

    /// E.1 — add a tag if there's room (`maxTags`). No-op on duplicates or
    /// empty input.
    func addTag(_ raw: String) {
        guard let tag = Self.normalizeTag(raw),
              form.tags.count < GigComposeLimits.maxTags,
              !form.tags.contains(tag)
        else { return }
        form.tags.append(tag)
    }

    /// E.1 — remove a tag by its normalised value.
    func removeTag(_ tag: String) {
        form.tags.removeAll { $0 == tag }
    }

    /// E.1 — add a suggested tag if absent, remove it if already chosen.
    func toggleTag(_ raw: String) {
        guard let tag = Self.normalizeTag(raw) else { return }
        if form.tags.contains(tag) {
            removeTag(tag)
        } else {
            addTag(tag)
        }
    }

    /// Normalise a freeform tag into the stored form: trimmed, lowercased,
    /// without a leading `#`, whitespace collapsed to single hyphens, capped
    /// at 50 chars. Returns nil for empty input.
    static func normalizeTag(_ raw: String) -> String? {
        let lowered = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let withoutHash = lowered.hasPrefix("#") ? String(lowered.dropFirst()) : lowered
        let hyphenated = withoutHash
            .split { $0 == " " || $0 == "\t" }
            .joined(separator: "-")
        let capped = String(hyphenated.prefix(50))
        return capped.isEmpty ? nil : capped
    }

    // MARK: - State transitions

    var currentStep: GigComposeStep {
        GigComposeStep(rawValue: form.step) ?? .category
    }

    /// True when the active step's inputs are valid enough to advance.
    /// Exposed for tests and for the chrome's `primaryCTAEnabled` flag.
    var canAdvance: Bool {
        primaryEnabled(for: currentStep)
    }

    private func advance() async {
        switch currentStep {
        case .category, .basics, .budget, .schedule, .location:
            if let next = GigComposeStep(rawValue: form.step + 1) {
                transition(to: next)
            }
        case .review:
            await submit()
        case .success:
            if let gigId = createdGigId {
                pendingEvent = .openGigDetail(gigId: gigId)
            }
        }
    }

    private func goBack() {
        guard let previous = GigComposeStep(rawValue: form.step - 1) else { return }
        transition(to: previous)
    }

    private func transition(to step: GigComposeStep) {
        form.step = step.rawValue
        errorMessage = nil
        if let stepNumber = step.stepNumber {
            Analytics.track(
                .screenComposeGigWizardStepViewed(
                    stepNumber: stepNumber,
                    stepName: String(describing: step)
                )
            )
        }
    }
}

extension GigComposeViewModel {
    // MARK: - API

    private func submit() async {
        Analytics.track(.ctaComposeGigSubmit)
        if !isOnlineProvider() {
            errorMessage = "You're offline. Try again when you're back online."
            return
        }
        guard let body = buildCreateBody() else {
            errorMessage = "Please complete each step before posting."
            return
        }
        isSubmitting = true
        defer { isSubmitting = false }
        do {
            let response: CreateGigResponse = try await api.request(GigsEndpoints.create(body))
            createdGigId = response.gig.id
            transition(to: .success)
        } catch {
            errorMessage = (error as? APIError)?.errorDescription
                ?? "Couldn't post your task. Please try again."
        }
    }

    /// Assemble a `CreateGigBody` from the form. Returns nil if any
    /// required field is missing — `primaryEnabled(...)` should have
    /// caught it but we double-check before sending.
    func buildCreateBody() -> CreateGigBody? {
        guard let budgetType = form.budgetType,
              let scheduleType = form.scheduleType,
              let locationMode = form.locationMode
        else { return nil }
        let title = form.title.trimmingCharacters(in: .whitespacesAndNewlines)
        let description = form.description.trimmingCharacters(in: .whitespacesAndNewlines)
        guard title.count >= GigComposeLimits.titleMin,
              title.count <= GigComposeLimits.titleMax,
              description.count >= GigComposeLimits.descriptionMin
        else { return nil }
        let price = priceFromBudget(type: budgetType)
        guard price > 0 || budgetType == .offers else { return nil }
        let scheduledStart = scheduleType == .oneTime ? form.scheduledStartISO : nil
        if scheduleType == .oneTime, scheduledStart == nil { return nil }
        let taskFormat: String? = locationMode == .virtual ? "remote" : nil
        let location = composedLocation(for: locationMode) ?? fallbackLocation()
        return CreateGigBody(
            title: title,
            description: description,
            category: form.category?.rawValue,
            // Backend requires positive number; we send `1` for
            // open-to-bids so the schema accepts it and treat the
            // `pay_type` as the source of truth.
            price: price > 0 ? price : 1,
            payType: budgetType.wireValue,
            scheduleType: scheduleType.wireValue,
            scheduledStart: scheduledStart,
            taskFormat: taskFormat,
            attachments: form.photoIds.isEmpty ? nil : form.photoIds,
            // E.1 — composer picker-sheet fields. Each is omitted from the
            // JSON when unset (optional + `encodeIfPresent`); `is_urgent`
            // only rides along when the boost is on.
            deadline: form.deadlineISO,
            cancellationPolicy: form.cancellationPolicy?.wireValue,
            isUrgent: form.isUrgent ? true : nil,
            tags: form.tags.isEmpty ? nil : form.tags,
            location: location
        )
    }

    private func composedLocation(for mode: GigComposeLocationMode) -> CreateGigLocation? {
        let coord = location.cachedCoordinate()
        let lat = coord?.latitude ?? 0
        let lon = coord?.longitude ?? 0
        switch mode {
        case .yourAddress:
            return CreateGigLocation(
                mode: mode.wireMode,
                latitude: lat,
                longitude: lon,
                address: "Your saved address",
                city: nil,
                state: nil,
                zip: nil,
                homeId: nil
            )
        case .aPlace:
            let addr = form.placeAddress
            guard addr.isComplete else { return nil }
            return CreateGigLocation(
                mode: mode.wireMode,
                latitude: lat,
                longitude: lon,
                address: addr.line1.trimmingCharacters(in: .whitespacesAndNewlines),
                city: addr.city.trimmingCharacters(in: .whitespacesAndNewlines),
                state: addr.state.trimmingCharacters(in: .whitespacesAndNewlines),
                zip: addr.zip.trimmingCharacters(in: .whitespacesAndNewlines),
                homeId: nil
            )
        case .virtual:
            return CreateGigLocation(
                mode: mode.wireMode,
                latitude: lat,
                longitude: lon,
                address: "Remote / Online",
                city: nil,
                state: nil,
                zip: nil,
                homeId: nil
            )
        }
    }

    private func fallbackLocation() -> CreateGigLocation {
        CreateGigLocation(
            mode: "custom",
            latitude: 0,
            longitude: 0,
            address: "Remote / Online"
        )
    }
}

extension GigComposeViewModel {
    // MARK: - Chrome derivation

    private func progressLabel(for step: GigComposeStep) -> WizardProgressLabel {
        if let stepNumber = step.stepNumber {
            return .stepOf(current: stepNumber, total: GigComposeStep.progressTotal)
        }
        return .hidden
    }

    private func progressFraction(for step: GigComposeStep) -> Double? {
        guard let stepNumber = step.stepNumber else { return nil }
        return Double(stepNumber) / Double(GigComposeStep.progressTotal)
    }

    private func leadingControl(for step: GigComposeStep) -> WizardLeadingControl {
        switch step {
        case .category, .success: .close
        case .basics, .budget, .schedule, .location, .review: .back
        }
    }

    private func primaryCTALabel(for step: GigComposeStep) -> String {
        switch step {
        case .category, .basics, .budget, .schedule, .location: "Continue"
        case .review: "Post task"
        case .success: "View task"
        }
    }

    private func secondaryCTA(for step: GigComposeStep) -> WizardSecondaryCTA? {
        switch step {
        case .success:
            WizardSecondaryCTA(label: "Done", identifier: "composeGigDone")
        case .category where form.composeMode == .magic:
            // Ghost link beside the primary CTA → manual picker.
            WizardSecondaryCTA(label: "Pick category", identifier: "composeGigPickCategory")
        default:
            nil
        }
    }

    private func primaryEnabled(for step: GigComposeStep) -> Bool {
        switch step {
        case .category:
            // Magic: enabled once an archetype is detected. Manual:
            // enabled once a category tile is selected.
            form.composeMode == .magic ? form.detectedArchetype != nil : hasSelectedCategory
        case .basics:
            hasValidBasics
        case .budget:
            hasValidBudget
        case .schedule:
            hasValidSchedule
        case .location:
            hasValidLocation
        case .review:
            buildCreateBody() != nil
        case .success:
            createdGigId != nil
        }
    }

    private var dirtyForCloseConfirm: Bool {
        currentStep != .success && form.hasAnyData
    }

    // MARK: - Helpers

    /// Strip everything except digits + a single decimal point. Empty
    /// strings stay empty so the placeholder shows.
    private func sanitizeBudget(_ raw: String) -> String {
        var seenDot = false
        var out = ""
        for char in raw {
            if char.isNumber {
                out.append(char)
            } else if char == "." && !seenDot {
                out.append(char)
                seenDot = true
            }
        }
        return out
    }

    /// Resolve the wire `price` from the active budget type. For
    /// `fixed` it's the min; for `hourly` it's the min hourly rate; for
    /// `offers` it's 0 (we send `1` so the schema accepts it).
    private func priceFromBudget(type: GigComposeBudgetType) -> Double {
        switch type {
        case .offers: 0
        case .fixed, .hourly: Double(form.budgetMin) ?? 0
        }
    }
}

private extension GigComposeViewModel {
    var hasSelectedCategory: Bool {
        form.category != nil
    }

    var hasValidBasics: Bool {
        let title = form.title.trimmingCharacters(in: .whitespacesAndNewlines)
        let desc = form.description.trimmingCharacters(in: .whitespacesAndNewlines)
        return title.count >= GigComposeLimits.titleMin
            && title.count <= GigComposeLimits.titleMax
            && desc.count >= GigComposeLimits.descriptionMin
            && desc.count <= GigComposeLimits.descriptionMax
            && form.photoIds.count <= GigComposeLimits.maxPhotos
    }

    var hasValidBudget: Bool {
        guard let type = form.budgetType else { return false }
        switch type {
        case .offers:
            return true
        case .fixed, .hourly:
            let min = Double(form.budgetMin) ?? 0
            return min > 0
        }
    }

    var hasValidSchedule: Bool {
        guard let type = form.scheduleType else { return false }
        if type == .oneTime {
            guard let iso = form.scheduledStartISO,
                  let date = ISO8601DateFormatter().date(from: iso)
            else { return false }
            return date.timeIntervalSinceNow > 0
        }
        return true
    }

    var hasValidLocation: Bool {
        guard let mode = form.locationMode else { return false }
        switch mode {
        case .yourAddress, .virtual:
            return true
        case .aPlace:
            return form.placeAddress.isComplete
        }
    }
}
