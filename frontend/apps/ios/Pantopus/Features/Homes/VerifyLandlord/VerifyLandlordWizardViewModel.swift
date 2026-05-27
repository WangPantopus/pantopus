//
//  VerifyLandlordWizardViewModel.swift
//  Pantopus
//
//  Drives the A12.5 / A12.6 wizard state machine:
//
//    .start → .details → submit → openPostcardVerification(homeId)
//
//  Stubs the network round-trip — sleeps 800ms then dispatches the
//  outbound `openPostcardVerification` event. Wiring against the real
//  backend lands when the verify-landlord endpoints ship.
//

import Foundation
import Observation

/// View model backing `VerifyLandlordWizardView`. Holds the per-field
/// form state, the current step, and the submit state machine
/// (`.idle → .submitting → .submitted / .error(_)`).
@Observable
@MainActor
final class VerifyLandlordWizardViewModel: WizardModel {
    // MARK: - Published state

    private(set) var currentStep: VerifyLandlordStep = .start
    private(set) var startContent: VerifyLandlordStartContent
    var form: VerifyLandlordForm
    /// Validation errors materialised lazily — `nil` means "user hasn't
    /// tried to submit yet, don't render error chips". Becomes
    /// `.empty` or populated after the first submit attempt.
    private(set) var errors: VerifyLandlordValidationErrors?
    private(set) var submitState: VerifyLandlordSubmitState = .idle
    var pendingEvent: VerifyLandlordOutboundEvent?

    // MARK: - Init

    private let homeId: String
    private let submitDelayNanos: UInt64

    init(
        homeId: String,
        startContent: VerifyLandlordStartContent? = nil,
        form: VerifyLandlordForm? = nil,
        submitDelayNanos: UInt64 = 800_000_000
    ) {
        self.homeId = homeId
        self.startContent = startContent
            ?? VerifyLandlordSampleData.startContent(for: homeId)
        self.form = form ?? VerifyLandlordSampleData.formSeed(for: homeId)
        self.submitDelayNanos = submitDelayNanos
    }

    // MARK: - WizardModel

    var chrome: WizardChrome {
        let dirty = !form.ownerName.isEmpty
            || !form.contactName.isEmpty
            || !form.email.isEmpty
            || form.lease != nil
            || form.pmEnabled
        switch currentStep {
        case .start:
            return WizardChrome(
                title: "Verify landlord",
                progressLabel: .stepOf(current: 1, total: 3),
                progressFraction: 1.0 / 3.0,
                leading: .close,
                primaryCTALabel: "Start verification",
                primaryCTAEnabled: true,
                secondaryCTA: nil,
                isSubmitting: false,
                dirty: dirty,
                showsProgressBar: true
            )
        case .details:
            let live = form.validate()
            let blocked = (errors != nil && !live.isEmpty) || isSubmitting
            return WizardChrome(
                title: "Verify landlord",
                progressLabel: .stepOf(current: 2, total: 3),
                progressFraction: 2.0 / 3.0,
                leading: .back,
                primaryCTALabel: "Submit",
                primaryCTAEnabled: !blocked,
                secondaryCTA: nil,
                isSubmitting: isSubmitting,
                dirty: dirty,
                showsProgressBar: true
            )
        }
    }

    var isSubmitting: Bool {
        if case .submitting = submitState { return true }
        return false
    }

    func leadingTapped() {
        switch currentStep {
        case .start:
            pendingEvent = .dismiss
        case .details:
            currentStep = .start
            errors = nil
        }
    }

    func discardConfirmed() {
        pendingEvent = .dismiss
    }

    func primaryTapped() {
        switch currentStep {
        case .start:
            currentStep = .details
        case .details:
            Task { await submit() }
        }
    }

    func secondaryTapped() {}

    // MARK: - Form mutations

    func setOwnerName(_ value: String) {
        form.ownerName = value
        refreshErrorsIfShown()
    }

    func setContactName(_ value: String) {
        form.contactName = value
        refreshErrorsIfShown()
    }

    func setEmail(_ value: String) {
        form.email = value
        refreshErrorsIfShown()
    }

    func setPhone(_ value: String) {
        form.phone = value
    }

    func setLease(_ lease: VerifyLandlordLeaseFile?) {
        form.lease = lease
        refreshErrorsIfShown()
    }

    func setPMEnabled(_ enabled: Bool) {
        form.pmEnabled = enabled
        if !enabled {
            form.pmName = ""
            form.pmEmail = ""
            form.pmPhone = ""
        }
        refreshErrorsIfShown()
    }

    func setPMName(_ value: String) {
        form.pmName = value
        refreshErrorsIfShown()
    }

    func setPMEmail(_ value: String) {
        form.pmEmail = value
        refreshErrorsIfShown()
    }

    func setPMPhone(_ value: String) {
        form.pmPhone = value
    }

    // MARK: - Submit

    func submit() async {
        if isSubmitting { return }
        let live = form.validate()
        errors = live
        if !live.isEmpty {
            submitState = .error(message: "Fix \(live.count) thing\(live.count == 1 ? "" : "s") to submit")
            return
        }
        submitState = .submitting
        if !NetworkMonitor.shared.isOnline {
            submitState = .error(message: "You're offline. Try again when you're back online.")
            return
        }
        // Stubbed round-trip — replace with real endpoint when the
        // verify-landlord backend lands. The 800ms sleep mirrors the
        // task brief's "stub the network client to return success
        // after 800ms" contract so the Submit button animation reads
        // realistically in QA.
        try? await Task.sleep(nanoseconds: submitDelayNanos)
        submitState = .submitted
        pendingEvent = .openPostcardVerification(homeId: homeId)
    }

    func acknowledgePendingEvent() {
        pendingEvent = nil
    }

    // MARK: - Variant switching

    // Used by previews / sample data toggles + the dashboard fast-track decision tree.

    func setVariant(_ variant: VerifyLandlordVariant) {
        guard variant != startContent.variant else { return }
        switch variant {
        case .canonical: startContent = VerifyLandlordSampleData.canonical
        case .fastTrack: startContent = VerifyLandlordSampleData.fastTrack
        }
    }

    // MARK: - Helpers

    /// Re-run validation only after the user has already attempted
    /// submit once — so we don't spray error chips while they're still
    /// typing the email.
    private func refreshErrorsIfShown() {
        guard errors != nil else { return }
        errors = form.validate()
    }
}
