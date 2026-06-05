//
//  CreateBusinessWizardViewModel.swift
//  Pantopus
//
//  Drives the A12.10 Create Business wizard. Step 1 (pick category) is
//  the only step the new design ships frames for; steps 2-4 are stubs
//  the VM still routes through so the progress rail reads as
//  "N of 4" all the way through. A follow-on prompt replaces the
//  stub-step plumbing once design hands off the remaining frames.
//
//  The custom-category submit (search frame's "Add as custom category"
//  fallback) stays on step 1 with an explicit backend-unavailable error
//  until a real custom-category endpoint exists.
//

import Foundation
import Logging
import Observation

/// View model backing `CreateBusinessWizardView`. Owns the step machine,
/// the category selection, the search query, and the custom-category
/// backend-blocked custom-category submit.
@Observable
@MainActor
final class CreateBusinessWizardViewModel: WizardModel {
    // MARK: - Published state

    private(set) var currentStep: CreateBusinessStep = .pickCategory
    private(set) var selectedCategoryId: BusinessCategory? = .home
    var searchText: String = ""
    private(set) var isSubmittingCustom: Bool = false
    private(set) var submitError: String?
    var pendingEvent: CreateBusinessOutboundEvent?

    // MARK: - Init

    private let api: APIClient
    private let logger = Logger(label: "app.pantopus.ios.CreateBusinessWizard")

    init(api: APIClient = .shared) {
        self.api = api
    }

    // MARK: - WizardModel

    var chrome: WizardChrome {
        let label = WizardProgressLabel.stepOf(
            current: currentStep.stepNumber,
            total: CreateBusinessStep.totalSteps
        )
        let fraction = Double(currentStep.stepNumber) / Double(CreateBusinessStep.totalSteps)
        return WizardChrome(
            title: "Create business",
            progressLabel: label,
            progressFraction: fraction,
            leading: currentStep == .pickCategory ? .close : .back,
            primaryCTALabel: primaryLabel,
            primaryCTAEnabled: primaryEnabled,
            secondaryCTA: nil,
            isSubmitting: isSubmittingCustom,
            // Once the user has picked a non-default category, or typed a
            // search query, an X tap must surface the discard-confirm so
            // the partial selection isn't dropped silently.
            dirty: isDirty,
            showsProgressBar: true
        )
    }

    func leadingTapped() {
        switch currentStep {
        case .pickCategory:
            pendingEvent = .dismiss
        case .legalInfo:
            currentStep = .pickCategory
        case .profile:
            currentStep = .legalInfo
        case .confirm:
            currentStep = .profile
        }
    }

    func discardConfirmed() {
        pendingEvent = .dismiss
    }

    func primaryTapped() {
        switch currentStep {
        case .pickCategory:
            // Step 1's primary CTA advances to the legal-info stub. If
            // the user is on the search frame with no selected category
            // (only a query typed), the CTA stays disabled — search hits
            // are picked via row tap.
            guard selectedCategoryId != nil else { return }
            currentStep = .legalInfo
        case .legalInfo:
            currentStep = .profile
        case .profile:
            currentStep = .confirm
        case .confirm:
            submitError = "Business name, username, and email are required before this can be submitted."
        }
    }

    func secondaryTapped() {
        // Step 1 has no secondary CTA; stubs may add one later.
    }

    // MARK: - Selection

    func selectCategory(_ id: BusinessCategory) {
        selectedCategoryId = id
        submitError = nil
    }

    /// Pick a search hit — sets the selected category and clears the
    /// search query so the user falls back onto the populated grid view
    /// for the chosen category.
    func selectSearchHit(_ hit: CategorySearchHit) {
        selectedCategoryId = hit.category
        searchText = ""
    }

    /// Submit the typed search string as a custom category candidate.
    /// Per audit open question #3 the backend doesn't yet accept the
    /// payload. Keep the user on the search step with an explicit error
    /// until a real `POST /api/businesses/custom-categories` route ships.
    func submitCustomCategory() {
        let trimmed = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !isSubmittingCustom else { return }
        isSubmittingCustom = true
        submitError = nil
        // TODO(audit-q3): wire to `POST /api/businesses/custom-categories`
        // once backend accepts the payload.
        logger.info("custom-category submit", metadata: [
            "label": .string(trimmed)
        ])
        Analytics.track(.ctaCreateBusinessCustomCategorySubmit(label: trimmed))
        isSubmittingCustom = false
        submitError = "Custom categories are not accepted by the backend yet."
    }

    func acknowledgePendingEvent() {
        pendingEvent = nil
    }

    // MARK: - Derived state

    var isSearchActive: Bool {
        !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var searchHits: [CategorySearchHit] {
        CreateBusinessSampleData.searchHits(query: searchText)
    }

    var whatYouGetItems: [WhatYouGetItem] {
        // Only Home Services has a designed strip today — every other
        // tile renders without the preview block until design extends
        // the inventory.
        guard let selected = selectedCategoryId, selected == .home else { return [] }
        return CreateBusinessSampleData.homeServicesWhatYouGet
    }

    private var primaryLabel: String {
        switch currentStep {
        case .pickCategory: "Continue"
        case .legalInfo: "Next"
        case .profile: "Next"
        case .confirm: "Confirm"
        }
    }

    private var primaryEnabled: Bool {
        switch currentStep {
        case .pickCategory:
            // Disabled if the user is mid-search with no selection.
            selectedCategoryId != nil && !isSubmittingCustom
        case .legalInfo, .profile:
            // Stubs gate the CTA open so the flow can be walked end-to-end.
            true
        case .confirm:
            false
        }
    }

    private var isDirty: Bool {
        // The pickCategory step seeds `selectedCategoryId = .home` so the
        // user opens the wizard onto a sensible default. Treat "still on
        // that default with no typed query and step 1" as clean — every
        // other state earns the discard confirm.
        if currentStep == .pickCategory, selectedCategoryId == .home, !isSearchActive {
            return false
        }
        return true
    }
}
