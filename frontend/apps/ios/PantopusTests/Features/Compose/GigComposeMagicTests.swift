//
//  GigComposeMagicTests.swift
//  PantopusTests
//
//  B.3 (A12.8) — Magic Task step-1 behaviour: deterministic archetype
//  detection, compose-mode toggling, the mode-aware Continue gate +
//  secondary CTA, module-prompt fixture, and a structural render of both
//  design frames (Magic populated · manual picker).
//

import SwiftUI
import XCTest
@testable import Pantopus

@MainActor
final class GigComposeMagicTests: XCTestCase {
    private func makeVM(_ state: GigComposeFormState = .empty) -> GigComposeViewModel {
        GigComposeViewModel(
            api: APIClient.shared,
            location: FixedLocationProvider(
                UserCoordinate(latitude: 40.7484, longitude: -73.9857, accuracyMeters: 100)
            ),
            initialState: state
        ) { true }
    }

    // MARK: - Default entry

    func testDefaultComposeModeIsMagic() {
        XCTAssertEqual(makeVM().form.composeMode, .magic)
    }

    // MARK: - Deterministic detection

    func testDetectArchetypeKeywordMap() {
        XCTAssertEqual(GigComposeViewModel.detectArchetype(from: "Help me move boxes Saturday"), .moving)
        XCTAssertEqual(GigComposeViewModel.detectArchetype(from: "Assemble an IKEA desk"), .handyman)
        XCTAssertEqual(GigComposeViewModel.detectArchetype(from: "Deep clean my apartment"), .cleaning)
        XCTAssertEqual(GigComposeViewModel.detectArchetype(from: "Walk my dog twice a day"), .petcare)
        XCTAssertEqual(GigComposeViewModel.detectArchetype(from: "Need a math tutor"), .tutoring)
        XCTAssertEqual(GigComposeViewModel.detectArchetype(from: "My wifi router needs setup"), .tech)
        XCTAssertNil(GigComposeViewModel.detectArchetype(from: "hi"))
        XCTAssertNil(GigComposeViewModel.detectArchetype(from: "something totally unrelated zzz"))
    }

    func testApplyDetectionMirrorsIntoCategory() {
        let vm = makeVM()
        vm.setDescribeText("Need someone to assemble an IKEA desk this Saturday")
        // Apply synchronously rather than waiting on the 350ms debounce.
        vm.applyDetection(for: vm.form.describeText)
        XCTAssertEqual(vm.form.detectedArchetype, .handyman)
        XCTAssertEqual(vm.form.category, .handyman)
    }

    func testApplyDetectionIgnoresStaleText() {
        let vm = makeVM()
        vm.setDescribeText("clean my place")
        vm.applyDetection(for: "an older snapshot") // stale → no-op
        XCTAssertNil(vm.form.detectedArchetype)
    }

    func testDescribeTextCappedAtMax() {
        let vm = makeVM()
        vm.setDescribeText(String(repeating: "a", count: GigComposeLimits.describeMax + 100))
        XCTAssertEqual(vm.form.describeText.count, GigComposeLimits.describeMax)
    }

    // MARK: - Continue gate

    func testMagicContinueGatedOnDetection() {
        let vm = makeVM()
        XCTAssertFalse(vm.chrome.primaryCTAEnabled, "Magic Continue is disabled before an archetype is detected.")
        vm.setDescribeText("Assemble an IKEA desk")
        vm.applyDetection(for: vm.form.describeText)
        XCTAssertTrue(vm.chrome.primaryCTAEnabled, "Detection enables Continue.")
    }

    func testManualContinueGatedOnCategory() {
        let vm = makeVM()
        vm.setComposeMode(.manual)
        XCTAssertFalse(vm.chrome.primaryCTAEnabled)
        vm.selectCategory(.cleaning)
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    func testManualPickerUsesEightConcreteArchetypes() {
        XCTAssertEqual(GigComposeCategory.manualPickerCases.count, 8)
        XCTAssertFalse(GigComposeCategory.manualPickerCases.contains(.other))
    }

    // MARK: - Mode toggle via secondary CTA

    func testMagicStepExposesPickCategorySecondary() {
        let vm = makeVM()
        XCTAssertEqual(vm.chrome.secondaryCTA?.identifier, "composeGigPickCategory")
    }

    func testSecondaryTapSwitchesToManual() {
        let vm = makeVM()
        vm.secondaryTapped()
        XCTAssertEqual(vm.form.composeMode, .manual)
    }

    func testManualStepHasNoSecondaryCTA() {
        let vm = makeVM()
        vm.setComposeMode(.manual)
        XCTAssertNil(vm.chrome.secondaryCTA, "Manual picker's back-to-magic affordance is an in-content banner.")
    }

    func testOpenBiddingEngagementPrefillsOffersBudget() {
        let vm = makeVM()
        XCTAssertEqual(vm.form.engagementMode, .oneTime)

        vm.selectEngagementMode(.openBidding)
        XCTAssertEqual(vm.form.engagementMode, .openBidding)
        XCTAssertEqual(vm.form.budgetType, .offers)

        vm.selectEngagementMode(.recurring)
        XCTAssertEqual(vm.form.engagementMode, .recurring)
        XCTAssertEqual(vm.form.scheduleType, .recurring)
        XCTAssertNil(vm.form.budgetType)
    }

    // MARK: - Module prompts fixture

    func testModulePromptsReflectParsedState() {
        let prompts = gigMagicModulePrompts(for: .handyman)
        XCTAssertEqual(prompts.count, 5)
        XCTAssertEqual(prompts.filter(\.isFilled).count, 4, "4 of 5 filled, one nudge (Photos).")
        XCTAssertEqual(prompts.first { !$0.isFilled }?.label, "Photos")
        XCTAssertTrue(gigMagicModulePrompts(for: nil).isEmpty, "No prompts until an archetype is parsed.")
    }

    // MARK: - Structural render of both frames

    func testMagicPopulatedFrameRenders() {
        let state = GigComposeFormState(
            composeMode: .magic,
            describeText: "Assemble an IKEA desk this Saturday morning",
            detectedArchetype: .handyman,
            category: .handyman,
            scheduleType: .oneTime
        )
        assertRenders(GigComposeWizardView(viewModel: makeVM(state)) { _ in })
    }

    func testManualPickerFrameRenders() {
        let state = GigComposeFormState(composeMode: .manual)
        assertRenders(GigComposeWizardView(viewModel: makeVM(state)) { _ in })
    }

    private func assertRenders(_ view: some View, file: StaticString = #filePath, line: UInt = #line) {
        let host = UIHostingController(rootView: view.frame(width: 390, height: 820))
        host.view.frame = CGRect(x: 0, y: 0, width: 390, height: 820)
        host.view.layoutIfNeeded()
        XCTAssertGreaterThan(host.view.frame.size.width, 0, file: file, line: line)
        XCTAssertGreaterThan(host.view.frame.size.height, 0, file: file, line: line)
    }
}
