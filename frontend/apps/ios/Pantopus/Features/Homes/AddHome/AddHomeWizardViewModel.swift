//
//  AddHomeWizardViewModel.swift
//  Pantopus
//
//  Wizard view model. Drives the 4-step + success state machine, keeps
//  step 1 search-first with deterministic address fixtures, and exposes
//  the small `WizardChrome` shape the shared `WizardShell` consumes.
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
    case viewHome
    case backToHub
}

/// Outbound navigation events the view should react to.
public enum AddHomeOutboundEvent: Sendable, Equatable {
    /// Pop the wizard with no further navigation.
    case dismiss
    /// Pop the wizard and navigate to the newly-created home dashboard.
    case openHomeDashboard(homeId: String)
    /// `check-address` matched an already-claimed home and the user
    /// picked the owner role — hand off to the ownership-claim wizard
    /// for that existing home instead of creating a duplicate row.
    /// Mirrors RN `useHomeForm.ts:461`.
    case openClaimOwnership(homeId: String)
    /// Residency claim submitted against an existing home — RN routes
    /// to the waiting room (`useHomeForm.ts:466`).
    case openWaitingRoom(homeId: String)
}

struct AddHomeGeocodedAddress: Equatable {
    let street: String
    let unit: String
    let city: String
    let state: String
    let zipCode: String
    let latitude: Double?
    let longitude: Double?
    let isMultiUnit: Bool
}

struct AddHomeZipMismatch: Equatable {
    let enteredZip: String
    let correctedZip: String
    let street: String
    let city: String
    let state: String
}

@Observable
@MainActor
final class AddHomeWizardViewModel: WizardModel {
    // MARK: - Public state

    /// Live form snapshot — mirrored into `@SceneStorage` so the wizard
    /// can be restored after process death.
    private(set) var form: AddHomeFormState

    /// Single search query used by the A12.1 step-1 typeahead.
    private(set) var homeSearchQuery: String = ""
    /// Candidate id selected from nearby results or autocomplete.
    private(set) var selectedHomeID: String?

    /// Result of `POST /api/homes/check-address`, populated when entering
    /// step 2.
    private(set) var addressCheck: CheckAddressResponse?
    /// Canonical address returned by check-address, used for the
    /// confirmation map and one-tap ZIP correction.
    private(set) var geocodedAddress: AddHomeGeocodedAddress?
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

    // MARK: - Existing-home (address already claimed) branch

    /// `check-address` returned `HOME_FOUND_CLAIMED` — show the
    /// two-step confirm modal instead of advancing. Mirrors RN
    /// `useHomeForm.ts:611`.
    private(set) var showsClaimedModal: Bool = false
    /// Second page of that modal ("Confirm this is your address").
    var showsConfirmAddressSheet: Bool = false
    /// Once the user confirms, submit resolves against the existing
    /// home instead of `POST /api/homes`.
    private(set) var isClaimingExistingHome: Bool = false
    /// `home_id` returned by `check-address` for the matched home.
    private(set) var existingHomeId: String?

    /// Address label rendered in the confirm sheet — the server's
    /// `formatted_address` when present, else the typed fields.
    var claimedAddressLabel: String {
        if let formatted = addressCheck?.formattedAddress?
            .trimmingCharacters(in: .whitespacesAndNewlines),
            !formatted.isEmpty {
            return formatted
        }
        return [
            form.address.street,
            form.address.unit,
            form.address.city,
            form.address.state,
            form.address.zipCode
        ]
        .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        .filter { !$0.isEmpty }
        .joined(separator: ", ")
    }

    /// One-shot navigation events the host view consumes.
    var pendingEvent: AddHomeOutboundEvent?

    // MARK: - Private dependencies

    private let api: APIClient
    private let isOnlineProvider: @MainActor () -> Bool

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
        selectedHomeID = AddHomeSampleData.candidate(for: initialState.address)?.id
        homeSearchQuery = AddHomeSampleData
            .candidate(for: initialState.address)?
            .line1 ?? ""
    }

    /// Replace the in-memory form state from scene storage on first
    /// appear. No-op once the wizard has progressed past the restore.
    func restore(from snapshot: AddHomeFormState) {
        guard form == .empty else { return }
        form = snapshot
        let candidate = AddHomeSampleData.candidate(for: snapshot.address)
        selectedHomeID = candidate?.id
        homeSearchQuery = candidate?.line1 ?? ""
    }

    // MARK: - WizardModel

    var chrome: WizardChrome {
        let step = currentStep
        return WizardChrome(
            title: title(for: step),
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

    #if DEBUG
    func advanceForTesting() async {
        await advance()
    }
    #endif

    func secondaryTapped() {
        // Success step's "Back to Hub" — no other step uses the secondary.
        if currentStep == .success { pendingEvent = .dismiss }
    }

    // MARK: - Search updates (step 1)

    var nearbyHomes: [AddHomeAddressCandidate] {
        AddHomeSampleData.nearbyHomes
    }

    var autocompleteResults: [AddHomeAddressCandidate] {
        guard selectedHomeID == nil else { return [] }
        return AddHomeSampleData.autocompleteResults(matching: homeSearchQuery)
    }

    var showsAutocomplete: Bool {
        selectedHomeID == nil
            && !homeSearchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    func updateSearchQuery(_ query: String) {
        homeSearchQuery = query
        selectedHomeID = nil
        form.address = .init()
        addressCheck = nil
        geocodedAddress = nil
    }

    func clearSearchQuery() {
        homeSearchQuery = ""
        selectedHomeID = nil
        form.address = .init()
        addressCheck = nil
        geocodedAddress = nil
    }

    func useCurrentLocation() {
        homeSearchQuery = ""
        selectedHomeID = nil
        form.address = .init()
        addressCheck = nil
        geocodedAddress = nil
    }

    func selectAddressCandidate(_ candidate: AddHomeAddressCandidate) {
        guard !candidate.isClaimed else { return }
        selectedHomeID = candidate.id
        homeSearchQuery = candidate.line1
        form.address = candidate.addressFields
        addressCheck = nil
        geocodedAddress = nil
    }

    func addManuallyTapped() {
        selectedHomeID = nil
        form.address = .init()
        addressCheck = nil
        geocodedAddress = nil
    }

    // MARK: - Legacy field updates (step 1)

    func update(_ field: AddressField, to value: String) {
        switch field {
        case .street: form.address.street = value
        case .unit: form.address.unit = value
        case .city: form.address.city = value
        case .state: form.address.state = value
        case .zip: form.address.zipCode = value
        }
        selectedHomeID = AddHomeSampleData.candidate(for: form.address)?.id
        homeSearchQuery = selectedHomeID == nil
            ? form.address.street
            : AddHomeSampleData.candidate(for: form.address)?.line1 ?? form.address.street
        addressCheck = nil
        geocodedAddress = nil
    }

    var zipMismatch: AddHomeZipMismatch? {
        guard let geocodedAddress else { return nil }
        let entered = normalizedAddHomeZip(form.address.zipCode)
        let corrected = normalizedAddHomeZip(geocodedAddress.zipCode)
        guard !entered.isEmpty, !corrected.isEmpty, entered != corrected else { return nil }
        return AddHomeZipMismatch(
            enteredZip: form.address.zipCode,
            correctedZip: geocodedAddress.zipCode,
            street: geocodedAddress.street,
            city: geocodedAddress.city,
            state: geocodedAddress.state
        )
    }

    var isGeocodeResolved: Bool {
        geocodedAddress != nil && zipMismatch == nil
    }

    func applyGeocodedZip() {
        guard let correctedZip = zipMismatch?.correctedZip else { return }
        form.address.zipCode = correctedZip
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
            guard !isCheckingAddress, zipMismatch == nil, !showsClaimedModal else { return }
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

    private func runCheckAddress() async {
        isCheckingAddress = true
        defer { isCheckingAddress = false }
        addressCheck = nil
        geocodedAddress = nil
        showsClaimedModal = false
        showsConfirmAddressSheet = false
        isClaimingExistingHome = false
        existingHomeId = nil
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
            geocodedAddress = makeAddHomeGeocodedAddress(from: response, fallback: form.address)
            existingHomeId = response.homeId
            if response.isAlreadyClaimed {
                // RN `useHomeForm.ts:611` — never advance; the modal
                // owns the next action.
                showsClaimedModal = true
            } else if response.isFoundUnclaimed {
                // A home row exists with no active occupants — RN
                // (`useHomeForm.ts:616`) claims it instead of creating
                // a duplicate.
                isClaimingExistingHome = response.homeId != nil
            }
        } catch {
            errorMessage = (error as? APIError)?.errorDescription
                ?? "Couldn't verify that address. Try again."
        }
    }

    // MARK: - Address-already-claimed modal

    /// "Change address" / "Edit" — close the modal and return to the
    /// address step so the user can correct their input.
    func dismissClaimedModal() {
        showsClaimedModal = false
        showsConfirmAddressSheet = false
        isClaimingExistingHome = false
        existingHomeId = nil
        transition(to: .address)
    }

    /// "This address is correct" → show the confirm page of the modal.
    func showConfirmAddressStep() {
        showsConfirmAddressSheet = true
    }

    /// "Confirm address" — commit to joining the existing home. RN skips
    /// the details step and lands on role selection
    /// (`useHomeForm.ts:700-705`).
    func confirmClaimedAddress() {
        showsClaimedModal = false
        showsConfirmAddressSheet = false
        isClaimingExistingHome = true
        transition(to: .role)
    }

    private func submitExistingHomeClaim(role: AddHomeRole) async {
        guard let homeId = existingHomeId else {
            errorMessage = "We could not find the existing home record. Please try that address again."
            transition(to: .address)
            return
        }
        if role == .owner {
            // Owner path: verification, not a residency claim.
            pendingEvent = .openClaimOwnership(homeId: homeId)
            return
        }
        isSubmitting = true
        defer { isSubmitting = false }
        do {
            _ = try await api.request(
                HomeDiscoveryEndpoints.submitResidencyClaim(
                    homeId: homeId,
                    request: SubmitResidencyClaimRequest(claimedRole: role.claimedRole)
                )
            ) as SubmitResidencyClaimResponse
            pendingEvent = .openWaitingRoom(homeId: homeId)
        } catch {
            errorMessage = (error as? APIError)?.errorDescription
                ?? "Failed to submit claim"
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
        // Existing-home flow: claim it rather than creating a duplicate
        // Home row (RN `useHomeForm.ts:456-473`).
        if isClaimingExistingHome {
            await submitExistingHomeClaim(role: role)
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

    private func title(for step: AddHomeStep) -> String {
        switch step {
        case .address: "Find your home"
        default: "Add home"
        }
    }

    private func primaryCTALabel(for step: AddHomeStep) -> String {
        switch step {
        case .address, .confirm, .role: "Continue"
        case .review: isClaimingExistingHome ? "Submit claim" : "Submit"
        case .success: "View home"
        }
    }

    private func secondaryCTA(for step: AddHomeStep) -> WizardSecondaryCTA? {
        guard step == .success else { return nil }
        return WizardSecondaryCTA(label: "Back to Hub", identifier: "addHomeBackToHub")
    }

    private func primaryEnabled(for step: AddHomeStep) -> Bool {
        switch step {
        case .address: selectedHomeID != nil
        case .confirm:
            !isCheckingAddress && errorMessage == nil && zipMismatch == nil && !showsClaimedModal
        case .role: form.role != nil
        case .review: form.role != nil
        case .success: createdHomeId != nil
        }
    }

    /// Whether the wizard is "dirty" enough to warrant a discard confirm
    /// when the user taps X on step 1 / success step.
    private var dirtyForCloseConfirm: Bool {
        currentStep != .success
            && (
                selectedHomeID != nil
                    || !homeSearchQuery.isEmpty
                    || !form.address.street.isEmpty
            )
    }
}

private func makeAddHomeGeocodedAddress(
    from response: CheckAddressResponse,
    fallback: AddHomeAddressFields
) -> AddHomeGeocodedAddress? {
    guard let normalized = response.normalizedAddress else { return nil }
    return AddHomeGeocodedAddress(
        street: cleanAddHomeGeocodeValue(normalized.street) ?? fallback.street,
        unit: cleanAddHomeGeocodeValue(normalized.unit) ?? fallback.unit,
        city: cleanAddHomeGeocodeValue(normalized.city) ?? fallback.city,
        state: cleanAddHomeGeocodeValue(normalized.state) ?? fallback.state,
        zipCode: cleanAddHomeGeocodeValue(normalized.zipCode) ?? fallback.zipCode,
        latitude: normalized.latitude,
        longitude: normalized.longitude,
        isMultiUnit: normalized.isMultiUnit ?? !fallback.unit.isEmpty
    )
}

private func cleanAddHomeGeocodeValue(_ value: String?) -> String? {
    guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines),
          !trimmed.isEmpty
    else { return nil }
    return trimmed
}

private func normalizedAddHomeZip(_ value: String) -> String {
    value.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
}

/// The five user-facing input fields in step 1.
public enum AddressField: String, Sendable, CaseIterable {
    case street
    case unit
    case city
    case state
    case zip
}
