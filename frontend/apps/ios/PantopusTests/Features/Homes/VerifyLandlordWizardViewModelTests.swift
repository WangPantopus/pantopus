//
//  VerifyLandlordWizardViewModelTests.swift
//  PantopusTests
//
//  Covers the verify-landlord wizard state machine: step transitions,
//  form validation (email format · lease unit mismatch · PM-required-
//  when-toggled-on), the error-summary count, and the submit ->
//  openPostcardVerification outbound event.
//

import Foundation
import XCTest
@testable import Pantopus

@MainActor
final class VerifyLandlordWizardViewModelTests: XCTestCase {
    // MARK: - Helpers

    private func makeVM(
        homeId: String = "home-1",
        form: VerifyLandlordForm? = nil,
        startContent: VerifyLandlordStartContent? = nil,
        postcardRequester: VerifyLandlordWizardViewModel.PostcardRequester? = nil
    ) -> VerifyLandlordWizardViewModel {
        VerifyLandlordWizardViewModel(
            homeId: homeId,
            startContent: startContent,
            form: form,
            submitDelayNanos: 0,
            postcardRequester: postcardRequester
        )
    }

    private func waitFor(
        _ description: String = "predicate",
        timeout: TimeInterval = 5.0,
        _ predicate: @MainActor () -> Bool
    ) async {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if predicate() { return }
            try? await Task.sleep(nanoseconds: 25_000_000)
        }
        XCTFail("Timed out waiting for \(description)")
    }

    // MARK: - Step machine

    func testInitialStateIsStart() {
        let vm = makeVM()
        XCTAssertEqual(vm.currentStep, .start)
        XCTAssertEqual(vm.chrome.primaryCTALabel, "Start verification")
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
        XCTAssertEqual(vm.chrome.leading, .close)
    }

    func testPrimaryFromStartAdvancesToDetails() {
        let vm = makeVM()
        vm.primaryTapped()
        XCTAssertEqual(vm.currentStep, .details)
        XCTAssertEqual(vm.chrome.primaryCTALabel, "Submit")
        XCTAssertEqual(vm.chrome.leading, .back)
    }

    func testBackOnDetailsReturnsToStart() {
        let vm = makeVM()
        vm.primaryTapped() // start -> details
        vm.leadingTapped() // details -> start (back)
        XCTAssertEqual(vm.currentStep, .start)
        XCTAssertNil(vm.errors, "Returning to Start should clear any pending validation chips")
    }

    func testLeadingOnStartDismisses() {
        let vm = makeVM()
        vm.leadingTapped()
        XCTAssertEqual(vm.pendingEvent, .dismiss)
    }

    // MARK: - Variants

    func testFastTrackVariantSurfacesExistingLandlord() {
        let vm = makeVM(homeId: "home-fast-track")
        XCTAssertTrue(vm.startContent.isFastTrack)
        XCTAssertNotNil(vm.startContent.existingLandlord)
    }

    func testCanonicalVariantHasNoExistingLandlord() {
        let vm = makeVM()
        XCTAssertFalse(vm.startContent.isFastTrack)
        XCTAssertNil(vm.startContent.existingLandlord)
    }

    func testSetVariantSwapsContent() {
        let vm = makeVM()
        vm.setVariant(.fastTrack)
        XCTAssertEqual(vm.startContent.variant, .fastTrack)
        vm.setVariant(.canonical)
        XCTAssertEqual(vm.startContent.variant, .canonical)
    }

    // MARK: - Validation

    func testValidationCatchesMissingTLD() {
        var form = VerifyLandlordSampleData.populatedForm
        form.email = "mira@elmstholdings"
        let errors = form.validate()
        XCTAssertEqual(errors.email, "Missing top-level domain")
    }

    func testValidationCatchesLeaseUnitMismatch() {
        let form = VerifyLandlordSampleData.errorForm
        let errors = form.validate()
        XCTAssertNotNil(errors.lease, "Sample errored form should flag a unit mismatch")
        XCTAssertEqual(errors.email, "Missing top-level domain")
        XCTAssertEqual(errors.count, 2)
    }

    func testValidationCountSummary() {
        let errors = VerifyLandlordValidationErrors(
            email: "Missing top-level domain",
            lease: "Unit mismatch"
        )
        XCTAssertEqual(errors.count, 2)
        XCTAssertEqual(errors.compactSummary, "Email format · Lease unit mismatch")
    }

    func testPMRequiredWhenToggleOn() {
        var form = VerifyLandlordSampleData.populatedForm
        form.pmEnabled = true
        form.pmName = ""
        form.pmEmail = ""
        let errors = form.validate()
        XCTAssertEqual(errors.pmName, "Required")
        XCTAssertEqual(errors.pmEmail, "Required")
    }

    func testPMNotRequiredWhenToggleOff() {
        var form = VerifyLandlordSampleData.populatedForm
        form.pmEnabled = false
        form.pmName = ""
        form.pmEmail = ""
        let errors = form.validate()
        XCTAssertNil(errors.pmName)
        XCTAssertNil(errors.pmEmail)
    }

    // MARK: - Submit state machine

    func testSubmitBlockedWhenErrorsExist() async {
        let vm = makeVM(form: VerifyLandlordSampleData.errorForm)
        vm.primaryTapped() // -> details
        await vm.submit()
        XCTAssertEqual(vm.currentStep, .details, "Submit should not advance with errors")
        if case let .error(message) = vm.submitState {
            XCTAssertTrue(message.contains("Fix"))
        } else {
            XCTFail("Expected .error submit state when validation fails, got \(vm.submitState)")
        }
        XCTAssertEqual(vm.errors?.count, 2)
        XCTAssertNil(vm.pendingEvent)
        XCTAssertFalse(vm.chrome.primaryCTAEnabled, "CTA should disable while errors remain")
    }

    func testSubmitHappyPathFiresOpenPostcardEvent() async {
        let vm = makeVM(
            homeId: "home-1",
            form: VerifyLandlordSampleData.populatedForm,
            postcardRequester: { .success(()) }
        )
        vm.primaryTapped() // start -> details
        await vm.submit()
        await waitFor("pendingEvent == .openPostcardVerification") {
            vm.pendingEvent == .openPostcardVerification(homeId: "home-1")
        }
        XCTAssertEqual(vm.submitState, .submitted)
        XCTAssertNotNil(vm.errors)
        XCTAssertTrue(vm.errors?.isEmpty == true)
    }

    func testCTADisabledWhenErrorsPresent() async {
        let vm = makeVM(form: VerifyLandlordSampleData.errorForm)
        vm.primaryTapped()
        XCTAssertTrue(vm.chrome.primaryCTAEnabled, "CTA should start enabled before validation runs")
        await vm.submit()
        XCTAssertFalse(vm.chrome.primaryCTAEnabled, "CTA should disable after validation flags errors")
    }

    // MARK: - Field mutations

    func testSetPMEnabledClearsPMFieldsWhenToggledOff() {
        let vm = makeVM(form: VerifyLandlordSampleData.populatedForm)
        XCTAssertEqual(vm.form.pmName, "Daniel Ortega")
        vm.setPMEnabled(false)
        XCTAssertTrue(vm.form.pmName.isEmpty)
        XCTAssertTrue(vm.form.pmEmail.isEmpty)
        XCTAssertTrue(vm.form.pmPhone.isEmpty)
    }

    func testFieldUpdatesReRunValidationWhenAlreadyShown() async {
        let vm = makeVM(form: VerifyLandlordSampleData.errorForm)
        vm.primaryTapped()
        await vm.submit()
        let originalCount = vm.errors?.count ?? 0
        XCTAssertEqual(originalCount, 2)
        vm.setEmail("mira@elmstholdings.com")
        XCTAssertEqual(vm.errors?.count, 1, "Fixing the email should drop the error count by 1")
    }

    func testFieldUpdatesDoNotShowErrorsUntilSubmitAttempt() {
        let vm = makeVM(form: VerifyLandlordSampleData.errorForm)
        vm.primaryTapped()
        XCTAssertNil(vm.errors)
        vm.setEmail("typing@")
        XCTAssertNil(vm.errors, "Errors must not materialise until the user attempts submit")
    }
}
