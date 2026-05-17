//
//  PasswordChangeViewModelTests.swift
//  PantopusTests
//
//  Covers field validation (length, match, current-required gating),
//  the auth-methods discovery call (hasPassword vs OAuth-only), the
//  success path (toast + shouldDismiss), and the 401 error path
//  (current-password field gets the inline error).
//

import XCTest
@testable import Pantopus

@MainActor
final class PasswordChangeViewModelTests: XCTestCase {
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

    func testLoadDiscoversHasPasswordFromAuthMethods() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: "{\"providers\":[\"email\"],\"has_password\":true}")
        ]
        let vm = PasswordChangeViewModel(api: makeAPI())
        await vm.load()
        XCTAssertTrue(vm.requiresCurrent)
        XCTAssertEqual(vm.formState, .ready)
    }

    func testLoadOAuthOnlyDoesNotRequireCurrent() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: "{\"providers\":[\"google\"],\"has_password\":false}")
        ]
        let vm = PasswordChangeViewModel(api: makeAPI())
        await vm.load()
        XCTAssertFalse(vm.requiresCurrent)
    }

    func testIsValidRejectsShortPassword() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: "{\"has_password\":true}")
        ]
        let vm = PasswordChangeViewModel(api: makeAPI())
        await vm.load()
        vm.update(.current, to: "old-password-123")
        vm.update(.new, to: "short")
        vm.update(.confirm, to: "short")
        XCTAssertFalse(vm.isValid)
    }

    func testIsValidRejectsMismatchedConfirm() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: "{\"has_password\":true}")
        ]
        let vm = PasswordChangeViewModel(api: makeAPI())
        await vm.load()
        vm.update(.current, to: "old-password-123")
        vm.update(.new, to: "new-password-456")
        vm.update(.confirm, to: "different")
        XCTAssertFalse(vm.isValid)
        XCTAssertEqual(vm.fields[.confirm]?.error, "Doesn't match")
    }

    func testSaveSuccessSetsToastAndShouldDismiss() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: "{\"has_password\":true}"),
            .status(200, body: "{\"message\":\"Password updated\"}")
        ]
        let vm = PasswordChangeViewModel(api: makeAPI())
        await vm.load()
        vm.update(.current, to: "old-password-123")
        vm.update(.new, to: "new-password-456")
        vm.update(.confirm, to: "new-password-456")
        await vm.save()
        XCTAssertEqual(vm.toast, "Password updated")
        XCTAssertTrue(vm.shouldDismiss)
    }

    func testSave401MarksCurrentPasswordFieldWithError() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: "{\"has_password\":true}"),
            .status(401, body: "{\"error\":\"Current password is incorrect\"}")
        ]
        let vm = PasswordChangeViewModel(api: makeAPI())
        await vm.load()
        vm.update(.current, to: "wrong-password-1")
        vm.update(.new, to: "new-password-456")
        vm.update(.confirm, to: "new-password-456")
        await vm.save()
        XCTAssertEqual(vm.fields[.current]?.error, "Current password is incorrect")
        XCTAssertFalse(vm.shouldDismiss)
    }
}
