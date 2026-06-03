//
//  VerifyLandlordWizardViewModel.swift
//  Pantopus
//
//  Drives the A12.5 / A12.6 wizard state machine:
//
//    .start → .details → submit → openPostcardVerification(homeId)
//
//  On submit it requests the verification postcard via
//  `POST /api/homes/:id/request-postcard` (route
//  `backend/routes/homeOwnership.js:2452`) then dispatches the outbound
//  `openPostcardVerification` event. The landlord/PM details collected
//  in the form have no backend representation (request-postcard takes no
//  body), so they stay client-side.
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
    private let api: APIClient

    /// Test/offline seam for the postcard request. When non-nil,
    /// `submit()` calls this instead of
    /// `POST /api/homes/:id/request-postcard`.
    public typealias PostcardRequester = @MainActor () async -> Result<Void, any Error>
    private let postcardRequester: PostcardRequester?

    init(
        homeId: String,
        startContent: VerifyLandlordStartContent? = nil,
        form: VerifyLandlordForm? = nil,
        api: APIClient = .shared,
        submitDelayNanos: UInt64 = 800_000_000,
        postcardRequester: PostcardRequester? = nil
    ) {
        self.homeId = homeId
        self.startContent = startContent
            ?? VerifyLandlordSampleData.startContent(for: homeId)
        self.form = form ?? VerifyLandlordSampleData.formSeed(for: homeId)
        self.api = api
        self.submitDelayNanos = submitDelayNanos
        self.postcardRequester = postcardRequester
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
        // The landlord / property-manager details collected above have no
        // backend representation today — `request-postcard` takes no body
        // — so finishing the wizard simply mails the verification postcard
        // and routes to the code-entry screen. The gathered details stay
        // client-side (documented follow-up).
        switch await requestPostcard() {
        case .success:
            submitState = .submitted
            pendingEvent = .openPostcardVerification(homeId: homeId)
        case let .failure(error):
            // A pending/duplicate code (400) or address cap (429) means a
            // postcard is already on its way — proceed to enter it. Other
            // failures surface inline so the user can retry.
            if case let .clientError(status, _) = (error as? APIError), status == 400 || status == 429 {
                submitState = .submitted
                pendingEvent = .openPostcardVerification(homeId: homeId)
            } else {
                submitState = .error(
                    message: (error as? APIError)?.errorDescription
                        ?? "Couldn't request the verification postcard. Try again."
                )
            }
        }
    }

    /// Mails the verification postcard. Uses the injected
    /// `postcardRequester` seam when present (previews/tests); otherwise
    /// calls `POST /api/homes/:id/request-postcard`.
    private func requestPostcard() async -> Result<Void, any Error> {
        if let postcardRequester {
            try? await Task.sleep(nanoseconds: submitDelayNanos)
            return await postcardRequester()
        }
        do {
            _ = try await api.request(
                HomesEndpoints.requestPostcard(homeId: homeId),
                as: RequestPostcardResponse.self
            )
            return .success(())
        } catch {
            return .failure(error)
        }
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
