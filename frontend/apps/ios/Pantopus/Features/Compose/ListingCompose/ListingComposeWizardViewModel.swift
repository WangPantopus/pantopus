//
//  ListingComposeWizardViewModel.swift
//  Pantopus
//
//  Wizard view model for the Snap & Sell listing-compose flow. Drives
//  the six-step + success state machine and submits to
//  `POST /api/listings` (`backend/routes/listings.js:426`).
//

import Foundation
import Observation

/// Outbound navigation events the host view consumes.
public enum ListingComposeOutboundEvent: Sendable, Equatable {
    /// Pop the wizard without further navigation.
    case dismiss
    /// Pop the wizard and route to the newly-created listing's detail.
    case openListingDetail(listingId: String)
}

@Observable
@MainActor
final class ListingComposeWizardViewModel: WizardModel {
    // MARK: - Public state

    /// Live form snapshot — mirrored into `@SceneStorage` so the wizard
    /// can be restored after process death.
    private(set) var form: ListingComposeFormState

    /// True while the final `POST /api/listings` is in flight.
    private(set) var isSubmitting: Bool = false

    /// User-facing error message attached to the active step. Cleared
    /// on any successful step transition.
    private(set) var errorMessage: String?

    /// Set once the user reaches the success step, holds the new
    /// listing's id so the "View listing" CTA can route to its detail.
    private(set) var createdListingId: String?

    /// One-shot navigation events the host view consumes.
    var pendingEvent: ListingComposeOutboundEvent?

    // MARK: - Private dependencies

    private let api: APIClient
    private let isOnlineProvider: @MainActor () -> Bool

    // MARK: - Init

    init(
        api: APIClient = .shared,
        initialState: ListingComposeFormState = .empty,
        isOnlineProvider: @escaping @MainActor () -> Bool = { NetworkMonitor.shared.isOnline }
    ) {
        self.api = api
        self.isOnlineProvider = isOnlineProvider
        form = initialState
    }

    /// Replace the in-memory form state from scene storage on first
    /// appear. No-op once the wizard has progressed past the restore.
    func restore(from snapshot: ListingComposeFormState) {
        guard form == .empty else { return }
        form = snapshot
    }

    // MARK: - WizardModel

    var chrome: WizardChrome {
        let step = currentStep
        return WizardChrome(
            title: "List an item",
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

    func secondaryTapped() {
        // Success step's "Back to Marketplace" — no other step uses the secondary.
        if currentStep == .success { pendingEvent = .dismiss }
    }

    #if DEBUG
    func advanceForTesting() async {
        await advance()
    }
    #endif

    // MARK: - Step 1 (photos) mutations

    /// Append a new photo to the grid. Captures up to `maxPhotos`.
    func addPhoto(token: String = "photo_\(UUID().uuidString.prefix(6))") {
        guard form.photos.count < ListingComposeFormState.maxPhotos else { return }
        form.photos.append(ListingComposePhoto(token: token))
    }

    /// Remove the photo with the given id.
    func removePhoto(id: UUID) {
        form.photos.removeAll { $0.id == id }
    }

    /// Move the photo at `from` to `to`. First slot is the hero so the
    /// view can re-render the HERO chip without a flicker.
    func movePhoto(from: Int, to: Int) {
        guard from != to,
              form.photos.indices.contains(from),
              to >= 0, to <= form.photos.count
        else { return }
        let photo = form.photos.remove(at: from)
        let insertIndex = to > form.photos.count ? form.photos.count : to
        form.photos.insert(photo, at: insertIndex)
    }

    /// Promote a photo to the hero slot (index 0).
    func makeHero(id: UUID) {
        guard let index = form.photos.firstIndex(where: { $0.id == id }), index != 0 else { return }
        let photo = form.photos.remove(at: index)
        form.photos.insert(photo, at: 0)
    }

    // MARK: - Other step mutations

    func setTitle(_ value: String) {
        form.title = value
    }

    func setCategory(_ category: ListingComposeCategory) {
        form.category = category
        // Category implies price kind for Free and clears stale condition
        // when switching to Wanted (which asks, not offers).
        if category == .free {
            form.priceKind = .free
            form.priceAmount = ""
        } else if form.priceKind == .free, category != .free {
            form.priceKind = nil
        }
        if !category.requiresCondition {
            form.condition = nil
        }
    }

    func setCondition(_ condition: ListingComposeCondition) {
        form.condition = condition
    }

    func setBody(_ value: String) {
        form.bodyText = value
    }

    func setPriceKind(_ kind: ListingComposePriceKind) {
        form.priceKind = kind
        if kind == .free { form.priceAmount = "" }
    }

    func setPriceAmount(_ value: String) {
        // Allow only digits and one decimal separator. View-side state
        // would otherwise let a user type "12.3.4".
        let filtered = value.filter { $0.isNumber || $0 == "." }
        let parts = filtered.split(separator: ".", omittingEmptySubsequences: false)
        if parts.count <= 2 {
            form.priceAmount = filtered
        }
    }

    func setFulfillment(_ value: ListingComposeFulfillment) {
        form.fulfillment = value
    }

    func setLocationKind(_ kind: ListingComposeLocationKind) {
        form.locationKind = kind
    }

    func setLocationLabel(_ value: String) {
        form.locationLabel = value
    }

    // MARK: - Derived state

    var currentStep: ListingComposeStep {
        ListingComposeStep(rawValue: form.step) ?? .photos
    }

    /// Hero photo for the review summary + step-1 chip rendering.
    var heroPhoto: ListingComposePhoto? { form.photos.first }

    /// Numeric value parsed from `priceAmount`, or nil when unparseable.
    var parsedPrice: Double? {
        guard !form.priceAmount.isEmpty else { return nil }
        return Double(form.priceAmount)
    }

    // MARK: - State transitions

    private func advance() async {
        switch currentStep {
        case .photos:
            transition(to: .titleCategory)
        case .titleCategory:
            transition(to: .conditionDescription)
        case .conditionDescription:
            transition(to: .price)
        case .price:
            transition(to: .location)
        case .location:
            transition(to: .review)
        case .review:
            await submit()
        case .success:
            if let listingId = createdListingId {
                pendingEvent = .openListingDetail(listingId: listingId)
            }
        }
    }

    private func goBack() {
        guard let previous = ListingComposeStep(rawValue: form.step - 1) else { return }
        transition(to: previous)
    }

    private func transition(to step: ListingComposeStep) {
        form.step = step.rawValue
        errorMessage = nil
        if let stepNumber = step.stepNumber {
            Analytics.track(
                .screenListingComposeWizardStepViewed(
                    stepNumber: stepNumber,
                    stepName: String(describing: step)
                )
            )
        }
    }

    // MARK: - Submit

    private func submit() async {
        guard let category = form.category, let priceKind = form.priceKind else { return }
        Analytics.track(.ctaListingComposeSubmit)
        if !isOnlineProvider() {
            errorMessage = "You're offline. Try again when you're back online."
            return
        }
        isSubmitting = true
        defer { isSubmitting = false }

        let isFree = priceKind == .free || category == .free
        let listingType = category.listingType
        let condition = form.condition?.rawValue
        let price: Double? = isFree ? nil : parsedPrice

        let request = CreateListingRequest(
            title: trimmedTitle,
            description: trimmedDescription,
            price: price,
            isFree: isFree,
            category: category.rawValue,
            condition: condition,
            mediaUrls: form.photos.map(\.token),
            layer: category.layer,
            listingType: listingType,
            latitude: nil,
            longitude: nil,
            locationName: form.locationLabel.isEmpty ? nil : form.locationLabel,
            locationAddress: nil,
            meetupPreference: form.fulfillment.meetupPreference,
            deliveryAvailable: form.fulfillment == .delivery,
            isWanted: category.isWanted
        )

        do {
            let response: CreateListingResponse = try await api.request(
                ListingsEndpoints.create(request)
            )
            createdListingId = response.listing.id
            transition(to: .success)
        } catch {
            errorMessage = (error as? APIError)?.errorDescription
                ?? "Couldn't list your item. Please try again."
        }
    }

    // MARK: - Chrome derivation

    private func progressLabel(for step: ListingComposeStep) -> WizardProgressLabel {
        if let stepNumber = step.stepNumber {
            return .stepOf(current: stepNumber, total: ListingComposeStep.progressTotal)
        }
        return .hidden
    }

    private func progressFraction(for step: ListingComposeStep) -> Double? {
        guard let stepNumber = step.stepNumber else { return nil }
        return Double(stepNumber) / Double(ListingComposeStep.progressTotal)
    }

    private func leadingControl(for step: ListingComposeStep) -> WizardLeadingControl {
        switch step {
        case .photos, .success: .close
        default: .back
        }
    }

    private func primaryCTALabel(for step: ListingComposeStep) -> String {
        switch step {
        case .photos, .titleCategory, .conditionDescription, .price, .location: "Continue"
        case .review: "List it"
        case .success: "View listing"
        }
    }

    private func secondaryCTA(for step: ListingComposeStep) -> WizardSecondaryCTA? {
        guard step == .success else { return nil }
        return WizardSecondaryCTA(label: "Back to Marketplace", identifier: "listingComposeBackToMarketplace")
    }

    private func primaryEnabled(for step: ListingComposeStep) -> Bool {
        switch step {
        case .photos:
            !form.photos.isEmpty
        case .titleCategory:
            isTitleValid && form.category != nil
        case .conditionDescription:
            isDescriptionValid && conditionSatisfied
        case .price:
            isPriceValid
        case .location:
            form.locationKind != nil
        case .review:
            true
        case .success:
            createdListingId != nil
        }
    }

    private var dirtyForCloseConfirm: Bool {
        currentStep != .success
            && (!form.photos.isEmpty
                || !form.title.isEmpty
                || !form.bodyText.isEmpty)
    }

    // MARK: - Validation helpers

    var trimmedTitle: String {
        form.title.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var trimmedDescription: String {
        form.bodyText.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var isTitleValid: Bool {
        let length = trimmedTitle.count
        return length >= ListingComposeFormState.titleMinLength
            && length <= ListingComposeFormState.titleMaxLength
    }

    var isDescriptionValid: Bool {
        let length = trimmedDescription.count
        return length >= ListingComposeFormState.descriptionMinLength
            && length <= ListingComposeFormState.descriptionMaxLength
    }

    /// Condition is mandatory unless the category is Wanted, which is a
    /// request and so has no condition to report.
    var conditionSatisfied: Bool {
        guard let category = form.category else { return false }
        return !category.requiresCondition || form.condition != nil
    }

    var isPriceValid: Bool {
        guard let kind = form.priceKind else { return false }
        switch kind {
        case .free: return true
        case .fixed, .negotiable:
            guard let value = parsedPrice else { return false }
            return value > 0
        }
    }
}
