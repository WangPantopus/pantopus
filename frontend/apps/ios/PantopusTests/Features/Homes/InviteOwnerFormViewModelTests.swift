//
//  InviteOwnerFormViewModelTests.swift
//  PantopusTests
//
//  Validation rules, dirty/valid bookkeeping, the 201 happy path, and
//  the 400/409 inline-error mapping for the Invite Owner form.
//

import XCTest
@testable import Pantopus

@MainActor
final class InviteOwnerFormViewModelTests: XCTestCase {
    override func setUp() {
        super.setUp()
        SequencedURLProtocol.reset()
    }

    private func makeAPI(
        routeResponses: [String: [SequencedURLProtocol.Response]] = [:]
    ) -> APIClient {
        APIClient(
            environment: .current,
            session: SequencedURLProtocol.makeSession(routeResponses: routeResponses),
            retryPolicy: .none
        )
    }

    private func makeVM(
        currentEmail: String = "me@example.com",
        routeResponses: [String: [SequencedURLProtocol.Response]] = [:]
    ) -> InviteOwnerFormViewModel {
        InviteOwnerFormViewModel(
            homeId: "home-1",
            currentUserEmail: currentEmail,
            api: makeAPI(routeResponses: routeResponses)
        )
    }

    // MARK: - Validation

    func testInitialStateIsCleanAndInvalid() {
        let vm = makeVM()
        XCTAssertFalse(vm.isDirty)
        XCTAssertFalse(vm.isValid, "Empty form must not be submittable.")
    }

    func testEmailValidationRejectsGarbage() {
        let vm = makeVM()
        vm.update(.email, to: "not-an-email")
        XCTAssertNotNil(vm.fields[.email]?.error)
        XCTAssertFalse(vm.isValid)
    }

    func testEmailValidationAcceptsRfcShape() {
        let vm = makeVM()
        vm.update(.email, to: "alex@pantopus.app")
        XCTAssertNil(vm.fields[.email]?.error)
        XCTAssertTrue(vm.isValid)
        XCTAssertTrue(vm.isDirty)
    }

    func testEmailRejectsSelfInvite() {
        let vm = makeVM(currentEmail: "Alex@example.com")
        vm.update(.email, to: "alex@example.com") // case-insensitive
        XCTAssertEqual(vm.fields[.email]?.error, "You can't invite yourself.")
        XCTAssertFalse(vm.isValid)
    }

    func testPhoneValidatorAllowsEmptyAndChecksE164() {
        let vm = makeVM()
        vm.update(.email, to: "x@y.com")
        vm.update(.phone, to: "")
        XCTAssertNil(vm.fields[.phone]?.error)
        XCTAssertTrue(vm.isValid)

        vm.update(.phone, to: "555-1212")
        XCTAssertNotNil(vm.fields[.phone]?.error)
        XCTAssertFalse(vm.isValid)

        vm.update(.phone, to: "+15555550123")
        XCTAssertNil(vm.fields[.phone]?.error)
        XCTAssertTrue(vm.isValid)
    }

    // MARK: - Submit

    func testSubmitHappyPathSetsToastAndDismiss() async {
        let vm = makeVM(routeResponses: [
            "/api/homes/home-1/owners/invite": [
                .status(201, body: "{\"message\":\"Co-owner invitation sent.\",\"claim_id\":\"c-1\"}")
            ]
        ])
        vm.update(.email, to: "alex@pantopus.app")
        let ok = await vm.submit()
        XCTAssertTrue(ok)
        XCTAssertEqual(vm.toast?.text, "Invite sent.")
        XCTAssertEqual(vm.toast?.kind, .success)
    }

    func testSubmitConflictMapsToInlineEmailError() async {
        let vm = makeVM(routeResponses: [
            "/api/homes/home-1/owners/invite": [
                .status(
                    409,
                    body: "{\"error\":\"An ownership claim is already active for this home.\",\"code\":\"DUPLICATE_CLAIM\"}"
                )
            ]
        ])
        vm.update(.email, to: "alex@pantopus.app")
        let ok = await vm.submit()
        XCTAssertFalse(ok)
        XCTAssertEqual(
            vm.fields[.email]?.error,
            "An ownership claim is already active for this home."
        )
        XCTAssertEqual(vm.toast?.kind, .error)
    }

    func testSubmitNotFoundMapsToFriendlyMessage() async {
        let vm = makeVM(routeResponses: [
            "/api/homes/home-1/owners/invite": [
                .status(
                    400,
                    body: "{\"error\":\"Could not find user. They may need to create an account first.\"}"
                )
            ]
        ])
        vm.update(.email, to: "alex@pantopus.app")
        _ = await vm.submit()
        XCTAssertEqual(
            vm.fields[.email]?.error,
            "We couldn't find a Pantopus account with that email."
        )
    }

    func testSubmitWithInvalidEmailDoesNotHitNetwork() async {
        let vm = makeVM()
        vm.update(.email, to: "garbage")
        let ok = await vm.submit()
        XCTAssertFalse(ok)
        XCTAssertTrue(SequencedURLProtocol.capturedRequests.isEmpty)
    }
}
