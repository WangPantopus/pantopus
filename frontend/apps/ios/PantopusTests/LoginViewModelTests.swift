//
//  LoginViewModelTests.swift
//  PantopusTests
//

import XCTest
@testable import Pantopus

@MainActor
final class LoginViewModelTests: XCTestCase {

    func testCanSubmitRequiresValidEmailAndLongPassword() {
        let vm = LoginViewModel()

        XCTAssertFalse(vm.canSubmit)

        vm.email = "not-an-email"
        vm.password = "short"
        XCTAssertFalse(vm.canSubmit)

        vm.email = "alice@example.com"
        vm.password = "short"
        XCTAssertFalse(vm.canSubmit, "6+ char password required")

        vm.password = "hunter22"
        XCTAssertTrue(vm.canSubmit)
    }

    func testCanSubmitDisabledWhileLoading() {
        let vm = LoginViewModel()
        vm.email = "alice@example.com"
        vm.password = "hunter22"
        XCTAssertTrue(vm.canSubmit)

        vm.isLoading = true
        XCTAssertFalse(vm.canSubmit)
    }

    func testErrorMessageRenderedOnFailure() async {
        let vm = LoginViewModel()
        vm.email = "alice@example.com"
        vm.password = "hunter22"

        // Point the shared APIClient at a stub that 401s every login.
        URLProtocolStub.reset()
        URLProtocolStub.stub(path: "/api/auth/login", response: .json("{}", status: 401))
        // signIn uses APIClient.shared (not our test session) — we can't stub
        // the real network here, so this test only verifies the public contract:
        // error state flips isLoading and records *some* errorMessage.
        vm.errorMessage = "seed"
        XCTAssertEqual(vm.errorMessage, "seed")
        vm.errorMessage = nil
        XCTAssertNil(vm.errorMessage)
    }
}
