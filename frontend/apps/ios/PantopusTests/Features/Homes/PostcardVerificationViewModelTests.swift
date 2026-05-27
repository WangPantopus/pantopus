//
//  PostcardVerificationViewModelTests.swift
//  PantopusTests
//
//  Covers the A12.7 sibling status surface: stage transitions, code
//  unlock gating, verify-tapped happy path and wrong-code error path,
//  and the .verified outbound event payload.
//

import Foundation
import XCTest
@testable import Pantopus

@MainActor
final class PostcardVerificationViewModelTests: XCTestCase {
    private func makeVM(
        homeId: String = "home-1",
        stage: PostcardDeliveryStage = .inTransit,
        expectedCode: String = "4Q2K7B"
    ) -> PostcardVerificationViewModel {
        PostcardVerificationViewModel(
            homeId: homeId,
            stage: stage,
            expectedCode: expectedCode,
            submitDelayNanos: 0
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

    // MARK: - Stage gating

    func testInTransitStageLocksCodeInput() {
        let vm = makeVM(stage: .inTransit)
        XCTAssertFalse(vm.isCodeInputUnlocked)
        XCTAssertFalse(vm.primaryCTAEnabled)
    }

    func testDeliveredStageUnlocksInput() {
        let vm = makeVM(stage: .delivered)
        XCTAssertTrue(vm.isCodeInputUnlocked)
        XCTAssertFalse(vm.primaryCTAEnabled, "Empty code should keep the CTA disabled")
    }

    func testFilledCodeOnDeliveredEnablesCTA() {
        let vm = makeVM(stage: .delivered)
        vm.updateCode("4Q2K7B")
        XCTAssertTrue(vm.primaryCTAEnabled)
        XCTAssertEqual(vm.codeInput, "4Q2K7B")
    }

    func testSetStageTransitions() {
        let vm = makeVM(stage: .inTransit)
        vm.setStage(.delivered)
        XCTAssertEqual(vm.stage, .delivered)
        XCTAssertNotNil(vm.content.deliveredOn)
    }

    // MARK: - Code typing

    func testUpdateCodeUppercasesAndClamps() {
        let vm = makeVM(stage: .delivered)
        vm.updateCode("abc123extra")
        XCTAssertEqual(vm.codeInput, "ABC123")
    }

    // MARK: - Verify

    func testVerifyCorrectCodeFiresVerifiedEvent() async {
        let vm = makeVM(homeId: "home-42", stage: .delivered)
        vm.updateCode("4Q2K7B")
        vm.verifyTapped()
        await waitFor("verified event fired") {
            vm.pendingEvent == .verified(homeId: "home-42")
        }
        XCTAssertEqual(vm.submitState, .submitted)
    }

    func testVerifyWrongCodeSurfacesErrorAndClears() async {
        let vm = makeVM(stage: .delivered, expectedCode: "ABCDEF")
        vm.updateCode("4Q2K7B")
        vm.verifyTapped()
        await waitFor("submit state is .error") {
            if case .error = vm.submitState { return true }
            return false
        }
        XCTAssertEqual(vm.codeInput, "", "Wrong code should clear the input so the user can retype")
        XCTAssertNil(vm.pendingEvent)
    }

    func testInTransitVerifyTappedIsNoOp() {
        let vm = makeVM(stage: .inTransit)
        vm.updateCode("4Q2K7B") // Even if a faulty caller pushes a code
        vm.verifyTapped()
        XCTAssertEqual(vm.submitState, .idle)
        XCTAssertNil(vm.pendingEvent)
    }

    // MARK: - Resend

    func testResendClearsCodeInput() {
        let vm = makeVM(stage: .delivered)
        vm.updateCode("4Q2K7B")
        vm.resendPostcard()
        XCTAssertTrue(vm.codeInput.isEmpty)
    }
}
