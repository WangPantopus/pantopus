//
//  AddHomeWizardViewModel.swift
//  Pantopus
//
//  Wizard view model. Drives the 4-step + success state machine, calls
//  `POST /api/homes/property-suggestions`, `POST /api/homes/check-address`,
//  and `POST /api/homes` (`backend/routes/home.js:540`, `:555`, `:677`),
//  and exposes the small `WizardChrome` shape the shared `WizardShell`
//  consumes.
//

import Foundation
import Observation

/// Tap intents the view raises on the wizard. Kept narrow so the model's
/// API surface is easy to reason about and unit-test.
public enum AddHomeIntent: Sendable {
    case primaryCTA
    case leading
    case selectRole(AddHomeRole)
    case togglePrimaryHome(Bool)
    case retrySuggestions
    case viewHome
    case backToHub
}

/// Outbound navigation events the view should react to.
public enum AddHomeOutboundEvent: Sendable, Equatable {
    /// Pop the wizard with no further navigation.
    case dismiss
    /// Pop the wizard and navigate to the newly-created home dashboard.
    case openHomeDashboard(homeId: String)
}

@Observable
@MainActor
final class AddHomeWizardViewModel: WizardModel {
    // MARK: - Public state

    /// Live form snapshot — mirrored into `@SceneStorage` so the wizard
    /// can be restored after process death.
    private(set) var form: AddHomeFormState

    /// Current address-suggestion list (empty until the user pauses
    /// typing in step 1).
    private(set) var suggestions: [String] = []
    /// True while the property-suggestions call is in flight.
    private(set) var isLoadingSuggestions: Bool = false

    /// Result of `POST /api/homes/check-address`, populated when entering
    /// step 2.
    private(set) var addressCheck: CheckAddressResponse?
    /// True while the check-address call is in flight.
    private(set) var isCheckingAddress: Bool = false

    /// True while the final `POST /api/homes` is in flight.
    private(set) var isSubmitting: Bool = false

    /// User-facing error message attached to the active step. Cleared on
    /// any successful step transition.
    private(set) var errorMessage: String?

    /// Set once the user reaches the success step, holds the new home's
    /// id so the "View home" CTA can route to the dashboard.
    private(set) var createdHomeId: String?

    /// One-shot navigation events the host view consumes.
    var pendingEvent: AddHomeOutboundEvent?

    // MARK: - Private dependencies

    private let api: APIClient
    private let isOnlineProvider: @MainActor () -> Bool
    private var debounceTask: Task<Void, Never>?

    /// Visible-for-testing default that fires `propertySuggestions` after
    /// the user pauses typing.
    static let suggestionDebounceNanoseconds: UInt64 = 300_000_000

    // MARK: - Init

    init(
        api: APIClient = .shared,
        initialState: AddHomeFormState = .empty,
        // Defaults to the live NetworkMonitor in production. Tests inject
        // a closure returning a fixed value so the simulator's
        // NWPathMonitor (which can transiently report `.unsatisfied` on
        // CI runners with limited network) doesn't gate `submit()`.
        isOnlineProvider: @escaping @MainActor () -> Bool = { NetworkMonitor.shared.isOnline }
    ) {
        self.api = api
        self.isOnlineProvider = isOnlineProvider
        form = initialState
    }

    /// Replace the in-memory form state from scene storage on first
    /// appear. No-op once the wizard has progressed past the restore.
    func restore(from snapshot: AddHomeFormState) {
        guard form == .empty else { return }
        form = snapshot
    }

    // MARK: - WizardModel

    var chrome: WizardChrome {
        let step = currentStep
        return WizardChrome(
            title: "Add a home",
            progressLabel: progressLabel(for: step),
            progressFraction: progressFraction(for: step),
            leading: leadingControl(for: step),
            primaryCTALabel: primaryCTALabel(for: step),
            primaryCTAEnabled: primaryEnabled(for: step) && !isSubmitting && !isCheckingAddress,
            secondaryCTA: secondaryCTA(for: step),
            isSubmitting: isSubmitting || isCheckingAddress,
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

    func secondaryTapped() {
        // Success step's "Back to Hub" — no other step uses the secondary.
        if currentStep == .success { pendingEvent = .dismiss }
    }

    // MARK: - Field updates (step 1)

    func update(_ field: AddressField, to value: String) {
        switch field {
        case .street: form.address.street = value
        case .unit: form.address.unit = value
        case .city: form.address.city = value
        case .state: form.address.state = value
        case .zip: form.address.zipCode = value
        }
        scheduleSuggestions()
    }

    /// Pick a suggestion from the list — copies its values into the form.
    /// Today the response shape is provider-defined so we surface the
    /// raw display string only; a richer parser lands when the backend
    /// returns structured suggestions.
    func selectSuggestion(_ suggestion: String) {
        form.address.street = suggestion
        suggestions = []
    }

    // MARK: - Field updates (step 2/3)

    func setPrimaryHome(_ isPrimary: Bool) {
        form.isPrimary = isPrimary
    }

    func selectRole(_ role: AddHomeRole) {
        form.role = role
    }

    /// User-tapped on the "Try again" CTA after a check-address error.
    func retryCheckAddress() {
        Task { await runCheckAddress() }
    }

    // MARK: - State transitions

    var currentStep: AddHomeStep {
        AddHomeStep(rawValue: form.step) ?? .address
    }

    private func advance() async {
        switch currentStep {
        case .address:
            // Move to confirm and kick off check-address.
            transition(to: .confirm)
            await runCheckAddress()
        case .confirm:
            guard !isCheckingAddress else { return }
            transition(to: .role)
        case .role:
            transition(to: .review)
        case .review:
            await submit()
        case .success:
            // "View home" — route to dashboard.
            if let homeId = createdHomeId {
                pendingEvent = .openHomeDashboard(homeId: homeId)
            }
        }
    }

    private func goBack() {
        guard let previous = AddHomeStep(rawValue: form.step - 1) else { return }
        transition(to: previous)
    }

    private func transition(to step: AddHomeStep) {
        form.step = step.rawValue
        errorMessage = nil
        if let stepNumber = step.stepNumber {
            Analytics.track(
                .screenAddHomeWizardStepViewed(
                    stepNumber: stepNumber,
                    stepName: String(describing: step)
                )
            )
        }
    }

    // MARK: - API calls

    private func scheduleSuggestions() {
        debounceTask?.cancel()
        guard form.address.isComplete else {
            suggestions = []
            isLoadingSuggestions = false
            return
        }
        let snapshot = form.address
        debounceTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: Self.suggestionDebounceNanoseconds)
            guard let self, !Task.isCancelled else { return }
            await fetchSuggestions(for: snapshot)
        }
    }

    private func fetchSuggestions(for fields: AddHomeAddressFields) async {
        isLoadingSuggestions = true
        defer { isLoadingSuggestions = false }
        let request = PropertySuggestionsRequest(
            address: fields.street,
            unitNumber: fields.unit.isEmpty ? nil : fields.unit,
            city: fields.city,
            state: fields.state,
            zipCode: fields.zipCode
        )
        do {
            let response: PropertySuggestionsResponse = try await api.request(
                HomesEndpoints.propertySuggestions(request)
            )
            suggestions = Self.flattenSuggestions(response)
        } catch {
            // Suggestions are advisory — don't block the user on failure.
            suggestions = []
        }
    }

    private func runCheckAddress() async {
        isCheckingAddress = true
        defer { isCheckingAddress = false }
        addressCheck = nil
        let request = CheckAddressRequest(
            address: form.address.street,
            unitNumber: form.address.unit.isEmpty ? nil : form.address.unit,
            city: form.address.city,
            state: form.address.state,
            zipCode: form.address.zipCode
        )
        do {
            let response: CheckAddressResponse = try await api.request(
                HomesEndpoints.checkAddress(request)
            )
            addressCheck = response
        } catch {
            errorMessage = (error as? APIError)?.errorDescription
                ?? "Couldn't verify that address. Try again."
        }
    }

    private func submit() async {
        guard let role = form.role else { return }
        Analytics.track(.ctaAddHomeSubmit)
        if !isOnlineProvider() {
            // P15: surface offline state inline; never silent-queue.
            errorMessage = "You're offline. Try again when you're back online."
            return
        }
        isSubmitting = true
        defer { isSubmitting = false }
        let request = CreateHomeRequest(
            address: form.address.street,
            unitNumber: form.address.unit.isEmpty ? nil : form.address.unit,
            city: form.address.city,
            state: form.address.state,
            zipCode: form.address.zipCode,
            name: role.label
        )
        do {
            let response: CreateHomeResponse = try await api.request(
                HomesEndpoints.create(request)
            )
            createdHomeId = response.home.id
            transition(to: .success)
        } catch {
            errorMessage = (error as? APIError)?.errorDescription
                ?? "Couldn't add your home. Please try again."
        }
    }

    // MARK: - Chrome derivation

    private func progressLabel(for step: AddHomeStep) -> WizardProgressLabel {
        if let stepNumber = step.stepNumber {
            return .stepOf(current: stepNumber, total: AddHomeStep.progressTotal)
        }
        return .hidden
    }

    private func progressFraction(for step: AddHomeStep) -> Double? {
        guard let stepNumber = step.stepNumber else { return nil }
        return Double(stepNumber) / Double(AddHomeStep.progressTotal)
    }

    private func leadingControl(for step: AddHomeStep) -> WizardLeadingControl {
        switch step {
        case .address, .success: .close
        case .confirm, .role, .review: .back
        }
    }

    private func primaryCTALabel(for step: AddHomeStep) -> String {
        switch step {
        case .address, .confirm, .role: "Continue"
        case .review: "Submit"
        case .success: "View home"
        }
    }

    private func secondaryCTA(for step: AddHomeStep) -> WizardSecondaryCTA? {
        guard step == .success else { return nil }
        return WizardSecondaryCTA(label: "Back to Hub", identifier: "addHomeBackToHub")
    }

    private func primaryEnabled(for step: AddHomeStep) -> Bool {
        switch step {
        case .address: form.address.isComplete
        case .confirm: !isCheckingAddress && errorMessage == nil
        case .role: form.role != nil
        case .review: form.role != nil
        case .success: createdHomeId != nil
        }
    }

    /// Whether the wizard is "dirty" enough to warrant a discard confirm
    /// when the user taps X on step 1 / success step.
    private var dirtyForCloseConfirm: Bool {
        currentStep != .success && !form.address.street.isEmpty
    }

    // MARK: - Suggestion parsing

    /// Flatten the provider-defined ATTOM JSON into a list of human-
    /// readable display strings. Returns at most 5.
    static func flattenSuggestions(_ value: JSONValue) -> [String] {
        var collected: [String] = []
        Self.collect(value, into: &collected)
        return Array(collected.prefix(5))
    }

    private static func collect(_ value: JSONValue, into out: inout [String]) {
        switch value {
        case let .object(dict):
            if let address = dict["address"]?.stringValue,
               !address.isEmpty {
                out.append(address)
            }
            for (_, child) in dict {
                Self.collect(child, into: &out)
            }
        case let .array(array):
            for item in array {
                Self.collect(item, into: &out)
            }
        default: break
        }
    }
}

/// The five user-facing input fields in step 1.
public enum AddressField: String, Sendable, CaseIterable {
    case street
    case unit
    case city
    case state
    case zip
}
