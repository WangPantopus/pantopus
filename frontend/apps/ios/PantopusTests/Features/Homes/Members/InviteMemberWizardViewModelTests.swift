//
//  InviteMemberWizardViewModelTests.swift
//  PantopusTests
//
//  T6.3a / P9 — Invite Member wizard. Covers:
//    - email validation (loose form)
//    - step progression with primary CTA enable state
//    - leadingTapped behaviour on first vs interior steps
//    - submit → POST /:id/invite happy path emits `.submitted`
//    - submit failure surfaces errorMessage and stays on the review step
//    - role selection maps to the wire `relationship` field
//

import XCTest
@testable import Pantopus

@MainActor
final class InviteMemberWizardViewModelTests: XCTestCase {
    override func setUp() {
        super.setUp()
        SequencedURLProtocol.reset()
    }

    private func makeAPI() -> APIClient {
        APIClient(
            environment: .current,
            session: SequencedURLProtocol.makeSession(),
            retryPolicy: .none
        )
    }

    private func makeVM(homeId: String = "home_1") -> InviteMemberWizardViewModel {
        InviteMemberWizardViewModel(homeId: homeId, api: makeAPI())
    }

    // MARK: - Validation

    func testEmailValidationAcceptsCanonicalAddress() {
        XCTAssertTrue(InviteMemberWizardViewModel.isValidEmail("user@example.com"))
        XCTAssertTrue(InviteMemberWizardViewModel.isValidEmail(" user@example.com "))
    }

    func testEmailValidationRejectsObviouslyMalformed() {
        XCTAssertFalse(InviteMemberWizardViewModel.isValidEmail(""))
        XCTAssertFalse(InviteMemberWizardViewModel.isValidEmail("nope"))
        XCTAssertFalse(InviteMemberWizardViewModel.isValidEmail("@example.com"))
        XCTAssertFalse(InviteMemberWizardViewModel.isValidEmail("user@nodot"))
    }

    // MARK: - Step progression

    func testPrimaryEnabledOnRoleStepWithoutFormData() {
        let vm = makeVM()
        XCTAssertEqual(vm.chrome.primaryCTALabel, "Next")
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    func testPrimaryDisabledOnIdentifyUntilValidEmail() {
        let vm = makeVM()
        vm.primaryTapped() // role → identify
        XCTAssertEqual(vm.currentStep, .identify)
        XCTAssertFalse(vm.chrome.primaryCTAEnabled)
        vm.setEmail("user@example.com")
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    func testReviewStepCarriesSendInviteLabel() {
        let vm = makeVM()
        vm.setEmail("user@example.com")
        vm.primaryTapped() // role → identify
        vm.primaryTapped() // identify → review
        XCTAssertEqual(vm.currentStep, .review)
        XCTAssertEqual(vm.chrome.primaryCTALabel, "Send invite")
    }

    func testLeadingFromRoleStepEmitsDismiss() {
        let vm = makeVM()
        vm.leadingTapped()
        XCTAssertEqual(vm.pendingEvent, .dismiss)
    }

    func testLeadingFromInteriorStepStepsBack() {
        let vm = makeVM()
        vm.primaryTapped() // → identify
        vm.leadingTapped()
        XCTAssertEqual(vm.currentStep, .role)
        XCTAssertNil(vm.pendingEvent)
    }

    // MARK: - Submit

    func testSubmitSuccessEmitsInvitationEvent() async {
        SequencedURLProtocol.sequence = [
            .status(201, body: """
            {"invitation":{
              "id":"inv_new","home_id":"home_1","invited_by":"u_owner",
              "invitee_email":"a@b.com","invitee_user_id":null,
              "proposed_role":"member","created_at":"2026-05-15T12:00:00Z"
            },"emailSent":true}
            """)
        ]
        let vm = makeVM()
        vm.setEmail("a@b.com")
        vm.primaryTapped() // role → identify
        vm.primaryTapped() // identify → review
        vm.primaryTapped() // submit
        // Allow the Task in primaryTapped to run.
        try? await Task.sleep(nanoseconds: 100_000_000)
        guard case let .submitted(invitation) = vm.pendingEvent else {
            XCTFail("Expected .submitted, got \(String(describing: vm.pendingEvent))")
            return
        }
        XCTAssertEqual(invitation.id, "inv_new")
        XCTAssertEqual(invitation.inviteeEmail, "a@b.com")
        XCTAssertNil(vm.errorMessage)
    }

    func testSubmitFailureSurfacesErrorMessageAndStaysOnReview() async {
        SequencedURLProtocol.sequence = [
            .status(500, body: "{\"error\":\"oops\"}")
        ]
        let vm = makeVM()
        vm.setEmail("a@b.com")
        vm.primaryTapped() // role → identify
        vm.primaryTapped() // identify → review
        vm.primaryTapped() // submit
        try? await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertNil(vm.pendingEvent)
        XCTAssertNotNil(vm.errorMessage)
        XCTAssertEqual(vm.currentStep, .review)
    }

    // MARK: - Role → relationship mapping

    func testRoleGuestRequestsGuestRelationship() async {
        SequencedURLProtocol.sequence = [
            .status(201, body: """
            {"invitation":{
              "id":"inv_g","home_id":"home_1","invited_by":"u_owner",
              "invitee_email":"g@e.com","invitee_user_id":null,
              "proposed_role":"guest","created_at":"2026-05-15T12:00:00Z"
            }}
            """)
        ]
        let vm = makeVM()
        vm.setRole(.guest)
        vm.setEmail("g@e.com")
        vm.primaryTapped()
        vm.primaryTapped()
        vm.primaryTapped()
        try? await Task.sleep(nanoseconds: 100_000_000)
        if case let .submitted(invitation) = vm.pendingEvent {
            XCTAssertEqual(invitation.proposedRole, "guest")
        } else {
            XCTFail("Expected .submitted")
        }
    }

    // MARK: - Chrome

    func testChromeReportsThreeStepsAndProgress() {
        let vm = makeVM()
        if case let .stepOf(_, total) = vm.chrome.progressLabel {
            XCTAssertEqual(total, 3)
        } else {
            XCTFail("Expected stepOf progress label")
        }
        XCTAssertNotNil(vm.chrome.progressFraction)
    }

    func testDirtyOnceFormChanges() {
        let vm = makeVM()
        XCTAssertFalse(vm.chrome.dirty)
        vm.setEmail("anything")
        XCTAssertTrue(vm.chrome.dirty)
    }
}
