//
//  ListingComposeWizardViewModel.swift
//  Pantopus
//
//  Wizard view model for the Snap & Sell listing-compose flow. Drives
//  the six-step + success state machine and submits to
//  `POST /api/listings` (`backend/routes/listings.js:426`) when creating
//  a new listing, or `PATCH /api/listings/:id`
//  (`backend/routes/listings.js:1479`) when editing an existing one.
//

import Foundation
import Observation

/// Outbound navigation events the host view consumes.
public enum ListingComposeOutboundEvent: Sendable, Equatable {
    /// Pop the wizard without further navigation.
    case dismiss
    /// Pop the wizard and route to the newly-created listing's detail.
    case openListingDetail(listingId: String)
    /// Pop the wizard after an edit save — the host usually pops to the
    /// listing detail (which then refreshes) rather than pushing a new
    /// screen.
    case listingUpdated(listingId: String)
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
    /// In edit mode this is set to the existing listing's id once the
    /// PATCH resolves.
    private(set) var createdListingId: String?

    /// True while the existing-listing fetch is in flight (edit mode
    /// only). View overlays a shimmer when this is set so the user
    /// can't tap through stale fields.
    private(set) var isLoadingExisting: Bool = false

    /// One-shot navigation events the host view consumes.
    var pendingEvent: ListingComposeOutboundEvent?

    // MARK: - Private dependencies

    private let api: APIClient
    private let isOnlineProvider: @MainActor () -> Bool
    private let mode: ListingComposeMode

    // MARK: - Init

    init(
        mode: ListingComposeMode = .create,
        api: APIClient = .shared,
        initialState: ListingComposeFormState = .empty,
        isOnlineProvider: @escaping @MainActor () -> Bool = { NetworkMonitor.shared.isOnline }
    ) {
        self.mode = mode
        self.api = api
        self.isOnlineProvider = isOnlineProvider
        form = initialState
    }

    /// True when the wizard is editing an existing listing.
    var isEditMode: Bool {
        mode.isEdit
    }

    /// The listing id being edited (nil when creating).
    var editingListingId: String? {
        mode.editingListingId
    }

    /// Replace the in-memory form state from scene storage on first
    /// appear. No-op once the wizard has progressed past the restore.
    /// Always a no-op in edit mode — edit fetches its own prefill from
    /// the backend.
    func restore(from snapshot: ListingComposeFormState) {
        guard !isEditMode else { return }
        guard form == .empty else { return }
        form = snapshot
    }

    /// Fetch the existing listing and project it into the form. No-op
    /// for create mode. Idempotent — safe to call from `.task { … }`.
    func loadExistingIfNeeded() async {
        guard case let .edit(listingId, jumpToStep) = mode else { return }
        // Only fetch on first land; if the user has already started
        // editing fields, don't blow them away.
        guard form == .empty else { return }
        isLoadingExisting = true
        defer { isLoadingExisting = false }
        do {
            let response: ListingDetailResponse = try await api.request(
                ListingsEndpoints.detail(id: listingId)
            )
            form = Self.project(from: response.listing, jumpToStep: jumpToStep)
        } catch {
            errorMessage = (error as? APIError)?.errorDescription
                ?? "Couldn't load the listing. Pull to retry."
        }
    }

    // MARK: - WizardModel

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
        // Success step's "Back to Marketplace" (create) / "Done" (edit) —
        // no other step uses the secondary. In edit mode "Done" still
        // signals the host to pop and refresh the listing detail, so we
        // emit the same event the primary fires.
        guard currentStep == .success else { return }
        if isEditMode, let listingId = createdListingId {
            pendingEvent = .listingUpdated(listingId: listingId)
        } else {
            pendingEvent = .dismiss
        }
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
                pendingEvent = isEditMode
                    ? .listingUpdated(listingId: listingId)
                    : .openListingDetail(listingId: listingId)
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
        let mediaUrls = form.photos.map(\.token)
        let locationName: String? = form.locationLabel.isEmpty ? nil : form.locationLabel
        let deliveryAvailable = form.fulfillment == .delivery

        switch mode {
        case .create:
            let request = CreateListingRequest(
                title: trimmedTitle,
                description: trimmedDescription,
                price: price,
                isFree: isFree,
                category: category.rawValue,
                condition: condition,
                mediaUrls: mediaUrls,
                layer: category.layer,
                listingType: listingType,
                latitude: nil,
                longitude: nil,
                locationName: locationName,
                locationAddress: nil,
                meetupPreference: form.fulfillment.meetupPreference,
                deliveryAvailable: deliveryAvailable,
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
        case let .edit(listingId, _):
            let request = UpdateListingRequest(
                title: trimmedTitle,
                description: trimmedDescription,
                price: price,
                isFree: isFree,
                category: category.rawValue,
                condition: condition,
                mediaUrls: mediaUrls,
                layer: category.layer,
                listingType: listingType,
                locationName: locationName,
                meetupPreference: form.fulfillment.meetupPreference,
                deliveryAvailable: deliveryAvailable,
                isWanted: category.isWanted
            )
            do {
                let response: UpdateListingResponse = try await api.request(
                    ListingsEndpoints.update(id: listingId, body: request)
                )
                createdListingId = response.listing.id
                transition(to: .success)
            } catch {
                errorMessage = (error as? APIError)?.errorDescription
                    ?? "Couldn't save your changes. Please try again."
            }
        }
    }
}
