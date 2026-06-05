//
//  TransferOwnershipViewModelTests.swift
//  PantopusTests
//
//  A13.4 — Behavioural unit tests for the Transfer Ownership form
//  view-model. Drives the state machine end-to-end via the injectable
//  biometric + transfer-executor seams, exercising the dirty / ready /
//  diff / confirm / dismiss transitions without any view layer.
//

import LocalAuthentication
import XCTest
@testable import Pantopus

@MainActor
final class TransferOwnershipViewModelTests: XCTestCase {
    func test_initial_state_ready_to_commit_is_false() {
        let viewModel = makeViewModel()
        XCTAssertFalse(viewModel.isReadyToCommit)
        XCTAssertEqual(viewModel.sheetPhase, .hidden)
        XCTAssertEqual(viewModel.amount, TransferOwnershipSampleData.defaultAmount)
        XCTAssertFalse(viewModel.confirmationMatches)
    }

    func test_typing_confirmation_phrase_flips_ready() {
        let viewModel = makeViewModel()
        viewModel.updateConfirmation("TRANSFER")
        XCTAssertTrue(viewModel.confirmationMatches)
        XCTAssertTrue(viewModel.isReadyToCommit)
    }

    func test_production_default_blocks_sample_recipient_transfer() {
        let viewModel = TransferOwnershipViewModel(homeId: "preview")
        viewModel.updateConfirmation("TRANSFER")

        XCTAssertTrue(viewModel.confirmationMatches)
        XCTAssertFalse(viewModel.isReadyToCommit)

        viewModel.presentConfirmSheet()

        XCTAssertEqual(viewModel.sheetPhase, .hidden)
        XCTAssertEqual(viewModel.toast?.kind, .error)
    }

    func test_typing_wrong_phrase_does_not_arm_cta() {
        let viewModel = makeViewModel()
        viewModel.updateConfirmation("transfer")
        XCTAssertFalse(viewModel.confirmationMatches)
        XCTAssertFalse(viewModel.isReadyToCommit)
    }

    func test_amount_is_clamped_to_user_stake() {
        let viewModel = makeViewModel()
        viewModel.updateAmount(120)
        XCTAssertEqual(viewModel.amount, viewModel.maxAmount)
        viewModel.updateAmount(0)
        XCTAssertEqual(viewModel.amount, viewModel.sliderRange.lowerBound)
    }

    func test_select_preset_jumps_amount() {
        let viewModel = makeViewModel()
        viewModel.selectPreset(33)
        XCTAssertEqual(viewModel.amount, 33)
    }

    func test_diff_segments_reflect_live_amount() {
        let viewModel = makeViewModel()
        viewModel.updateAmount(25)
        let after = viewModel.afterSegments
        let you = after.first { $0.id == TransferOwnershipSampleData.currentUser.id }
        let maya = after.first { $0.id == viewModel.recipient.id }
        XCTAssertEqual(you?.percent, 35)
        XCTAssertEqual(you?.delta, -25)
        XCTAssertEqual(maya?.percent, 25)
        XCTAssertEqual(maya?.delta, 25)
        XCTAssertEqual(maya?.isNew, true)
    }

    func test_present_confirm_sheet_only_when_ready() {
        let viewModel = makeViewModel()
        viewModel.presentConfirmSheet()
        XCTAssertEqual(viewModel.sheetPhase, .hidden)
        viewModel.updateConfirmation("TRANSFER")
        viewModel.presentConfirmSheet()
        XCTAssertEqual(viewModel.sheetPhase, .visible)
    }

    func test_dismiss_confirm_sheet_resets_state() {
        let viewModel = makeViewModel()
        viewModel.updateConfirmation("TRANSFER")
        viewModel.presentConfirmSheet()
        viewModel.dismissConfirmSheet()
        XCTAssertEqual(viewModel.sheetPhase, .hidden)
    }

    func test_authentication_failure_keeps_sheet_open_with_error() async {
        let viewModel = makeViewModel(
            biometricResult: .failure(LAError(.authenticationFailed))
        )
        viewModel.updateConfirmation("TRANSFER")
        viewModel.presentConfirmSheet()
        await viewModel.authenticateAndCommit()
        XCTAssertEqual(viewModel.sheetPhase, .visible)
        XCTAssertNotNil(viewModel.biometricErrorMessage)
        XCTAssertFalse(viewModel.shouldDismiss)
    }

    func test_successful_authentication_commits_and_dismisses() async {
        let viewModel = makeViewModel(
            biometricResult: .success(()),
            transferShouldThrow: false
        )
        viewModel.updateConfirmation("TRANSFER")
        viewModel.presentConfirmSheet()
        await viewModel.authenticateAndCommit()
        XCTAssertEqual(viewModel.sheetPhase, .dismissing)
        XCTAssertTrue(viewModel.shouldDismiss)
        XCTAssertEqual(viewModel.toast?.kind, .success)
    }

    func test_transfer_failure_surfaces_inline_error() async {
        let viewModel = makeViewModel(
            biometricResult: .success(()),
            transferShouldThrow: true
        )
        viewModel.updateConfirmation("TRANSFER")
        viewModel.presentConfirmSheet()
        await viewModel.authenticateAndCommit()
        XCTAssertEqual(viewModel.sheetPhase, .visible)
        XCTAssertNotNil(viewModel.biometricErrorMessage)
        XCTAssertFalse(viewModel.shouldDismiss)
    }

    func test_authenticate_no_op_when_sheet_hidden() async {
        let viewModel = makeViewModel(biometricResult: .success(()))
        viewModel.updateConfirmation("TRANSFER")
        await viewModel.authenticateAndCommit()
        XCTAssertEqual(viewModel.sheetPhase, .hidden)
        XCTAssertFalse(viewModel.shouldDismiss)
    }

    func test_dirty_flag_picks_up_amount_changes() {
        let viewModel = makeViewModel()
        XCTAssertFalse(viewModel.isDirty)
        viewModel.updateAmount(10)
        XCTAssertTrue(viewModel.isDirty)
    }

    func test_cta_label_uses_recipient_first_name() {
        let viewModel = makeViewModel()
        viewModel.updateAmount(33)
        XCTAssertEqual(viewModel.ctaLabel, "Transfer 33% to Maya")
    }

    // MARK: - Helpers

    private struct StubError: Error {}

    private func makeViewModel(
        biometricResult: Result<Void, Error>? = nil,
        transferShouldThrow: Bool = false
    ) -> TransferOwnershipViewModel {
        TransferOwnershipViewModel(
            homeId: "preview",
            biometricEvaluator: { _ in
                biometricResult ?? .success(())
            },
            transferExecutor: {
                if transferShouldThrow { throw StubError() }
            },
            recipientIsBackendBacked: true
        )
    }
}
