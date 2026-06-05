//
//  CreateBusinessWizardViewModelTests.swift
//  PantopusTests
//
//  Covers the A12.10 Create Business wizard state machine: step
//  transitions, category selection, search filtering, custom-category
//  backend blocking, and the chrome's dirty-tracking.
//

import Foundation
import XCTest
@testable import Pantopus

@MainActor
final class CreateBusinessWizardViewModelTests: XCTestCase {
    private func makeVM() -> CreateBusinessWizardViewModel {
        CreateBusinessWizardViewModel(
            api: APIClient(session: .shared, retryPolicy: .none)
        )
    }

    func testInitialStateIsPickCategoryWithHomeDefault() {
        let vm = makeVM()
        XCTAssertEqual(vm.currentStep, .pickCategory)
        XCTAssertEqual(vm.selectedCategoryId, .home)
        XCTAssertEqual(vm.chrome.primaryCTALabel, "Continue")
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
        XCTAssertFalse(vm.chrome.dirty, "Default home selection on step 1 should not be dirty.")
        XCTAssertEqual(vm.chrome.progressLabel, .stepOf(current: 1, total: 4))
    }

    func testSelectingNonDefaultCategoryMarksDirty() {
        let vm = makeVM()
        vm.selectCategory(.tech)
        XCTAssertEqual(vm.selectedCategoryId, .tech)
        XCTAssertTrue(vm.chrome.dirty, "Picking a non-default tile must mark the wizard dirty.")
    }

    func testTypingSearchQueryMarksDirty() {
        let vm = makeVM()
        vm.searchText = "tutor"
        XCTAssertTrue(vm.isSearchActive)
        XCTAssertTrue(vm.chrome.dirty)
    }

    func testSearchHitsAreFilteredAndCapped() {
        let vm = makeVM()
        vm.searchText = "tutor"
        XCTAssertEqual(vm.searchHits.count, 3)
        XCTAssertEqual(vm.searchHits.first?.id, "tutoring-core")
        XCTAssertEqual(vm.searchHits.first?.category, .personal)
        XCTAssertTrue(vm.searchHits.allSatisfy { $0.label.lowercased().contains("tutor") })
    }

    func testEmptySearchYieldsNoHits() {
        let vm = makeVM()
        vm.searchText = "   "
        XCTAssertTrue(vm.searchHits.isEmpty)
    }

    func testSelectingSearchHitSelectsCategoryAndClearsQuery() {
        let vm = makeVM()
        vm.searchText = "tutor"
        guard let hit = vm.searchHits.first else {
            XCTFail("Expected at least one tutor hit")
            return
        }
        vm.selectSearchHit(hit)
        XCTAssertEqual(vm.selectedCategoryId, hit.category)
        XCTAssertEqual(vm.searchText, "")
        XCTAssertFalse(vm.isSearchActive)
    }

    func testPrimaryFromPickCategoryAdvancesToLegalInfo() {
        let vm = makeVM()
        vm.primaryTapped()
        XCTAssertEqual(vm.currentStep, .legalInfo)
        XCTAssertEqual(vm.chrome.progressLabel, .stepOf(current: 2, total: 4))
        XCTAssertEqual(vm.chrome.primaryCTALabel, "Next")
    }

    func testPrimaryFromLegalInfoAdvancesToProfile() {
        let vm = makeVM()
        vm.primaryTapped() // → legalInfo
        vm.primaryTapped() // → profile
        XCTAssertEqual(vm.currentStep, .profile)
        XCTAssertEqual(vm.chrome.progressLabel, .stepOf(current: 3, total: 4))
    }

    func testPrimaryFromProfileAdvancesToConfirm() {
        let vm = makeVM()
        vm.primaryTapped() // legal
        vm.primaryTapped() // profile
        vm.primaryTapped() // confirm
        XCTAssertEqual(vm.currentStep, .confirm)
        XCTAssertEqual(vm.chrome.primaryCTALabel, "Confirm")
    }

    func testBackFromLegalInfoReturnsToPickCategory() {
        let vm = makeVM()
        vm.primaryTapped()
        vm.leadingTapped()
        XCTAssertEqual(vm.currentStep, .pickCategory)
    }

    func testCloseOnPickCategoryDispatchesDismiss() {
        let vm = makeVM()
        vm.leadingTapped()
        XCTAssertEqual(vm.pendingEvent, .dismiss)
    }

    func testCustomCategorySubmitStaysOnPickCategoryWithBackendError() {
        let vm = makeVM()
        vm.searchText = "alpaca grooming"
        vm.submitCustomCategory()
        XCTAssertEqual(vm.selectedCategoryId, .home)
        XCTAssertEqual(vm.currentStep, .pickCategory)
        XCTAssertEqual(vm.searchText, "alpaca grooming")
        XCTAssertEqual(vm.submitError, "Custom categories are not accepted by the backend yet.")
        XCTAssertFalse(vm.isSubmittingCustom)
    }

    func testCustomCategorySubmitNoopOnEmptyQuery() {
        let vm = makeVM()
        vm.searchText = "   "
        vm.submitCustomCategory()
        XCTAssertEqual(vm.currentStep, .pickCategory)
    }

    func testWhatYouGetOnlyVisibleForHomeServices() {
        let vm = makeVM()
        XCTAssertFalse(vm.whatYouGetItems.isEmpty, "Default .home should show the strip.")
        vm.selectCategory(.tech)
        XCTAssertTrue(vm.whatYouGetItems.isEmpty, "Other categories don't have a payload yet.")
    }

    func testChromeIdentityAccentIsBusinessViolet() {
        // Smoke-check the WizardIdentity threading by verifying the
        // chrome wires up the violet identity at the call site. We can't
        // assert the accent color directly on the chrome (it lives on
        // the identity, not the chrome), but the wizard view passes
        // `.business` into `WizardShell` — covered by the snapshot test.
        XCTAssertEqual(WizardIdentity.business.accent, Theme.Color.business)
        XCTAssertEqual(WizardIdentity.business.accentBg, Theme.Color.businessBg)
    }
}
