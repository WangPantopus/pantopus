//
//  AddHomeWizardViewModelTests.swift
//  PantopusTests
//
//  Covers the AddHome state machine: forward / back, validation gates,
//  check-address transition, submit happy path, submit error rollback,
//  scene-storage restore, and the 300 ms suggestion debounce.
//

import XCTest
@testable import Pantopus

@MainActor
final class AddHomeWizardViewModelTests: XCTestCase {
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

    /// Centralised constructor so every test injects an "always online"
    /// stub. NetworkMonitor.shared can transiently report `.unsatisfied`
    /// on CI simulators, which would gate `submit()` and hide the real
    /// behaviour we're testing.
    private func makeVM(initialState: AddHomeFormState = .empty) -> AddHomeWizardViewModel {
        AddHomeWizardViewModel(
            api: makeAPI(),
            initialState: initialState,
            isOnlineProvider: { true }
        )
    }

    /// Poll-and-yield helper. Replaces the brittle `Task.sleep(150ms)`
    /// pattern: waits up to `timeout` seconds, returning as soon as the
    /// predicate becomes true. CI runners are slow enough that fixed
    /// 150ms sleeps consistently race against the URL stub roundtrip
    /// + main-actor hop + recompose.
    private func waitFor(
        _ description: String = "predicate",
        timeout: TimeInterval = 3.0,
        _ predicate: @MainActor () -> Bool
    ) async {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if predicate() { return }
            try? await Task.sleep(nanoseconds: 25_000_000)
        }
        XCTFail("Timed out waiting for \(description)")
    }

    private func filled() -> AddHomeFormState {
        AddHomeFormState(
            step: AddHomeStep.address.rawValue,
            address: AddHomeAddressFields(
                street: "412 Elm St",
                unit: "",
                city: "Portland",
                state: "OR",
                zipCode: "97214"
            ),
            isPrimary: true,
            role: nil
        )
    }

    private static let suggestionsJSON = """
    {"results":[{"address":"412 Elm St"},{"address":"414 Elm St"}]}
    """

    private static let checkAddressJSON = """
    {"exists":false,"homeCount":0,"hasVerifiedMembers":false,"verdictStatus":null}
    """

    private static let createHomeJSON = """
    {"message":"ok","home":{
      "id":"home_42","name":"412 Elm St","address":"412 Elm St",
      "city":"Portland","state":"OR","zipcode":"97214",
      "home_type":null,"visibility":"public","description":null,
      "created_at":"2025-01-01T00:00:00Z","updated_at":"2025-01-01T00:00:00Z"
    },"requires_verification":false,"verification_type":null,"role":"owner"}
    """

    // MARK: - Initial state

    func testInitialChromeReflectsAddressStep() {
        let vm = AddHomeWizardViewModel(api: makeAPI(), initialState: .empty)
        let chrome = vm.chrome
        XCTAssertEqual(chrome.title, "Add a home")
        XCTAssertEqual(chrome.primaryCTALabel, "Continue")
        XCTAssertFalse(chrome.primaryCTAEnabled, "Continue must be disabled until the address fields are filled.")
        XCTAssertEqual(chrome.leading, .close)
        XCTAssertEqual(chrome.progressLabel, .stepOf(current: 1, total: 4))
    }

    func testFilledFormEnablesContinue() {
        let vm = AddHomeWizardViewModel(api: makeAPI(), initialState: filled())
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    // MARK: - Address → Confirm

    func testPrimaryAdvancesAndFiresCheckAddress() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.checkAddressJSON)]
        let vm = makeVM(initialState: filled())
        vm.primaryTapped()
        await waitFor("addressCheck populated") { vm.addressCheck != nil }
        XCTAssertEqual(vm.currentStep, .confirm)
        XCTAssertNotNil(vm.addressCheck)
        XCTAssertEqual(vm.chrome.leading, .back, "Back chevron replaces X on step 2.")
    }

    func testCheckAddressErrorSurfacesMessage() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{\"error\":\"down\"}")]
        let vm = makeVM(initialState: filled())
        vm.primaryTapped()
        await waitFor("errorMessage set") { vm.errorMessage != nil }
        XCTAssertEqual(vm.currentStep, .confirm)
        XCTAssertNil(vm.addressCheck)
        XCTAssertNotNil(vm.errorMessage)
    }

    // MARK: - Back navigation

    func testBackOnConfirmGoesToAddress() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.checkAddressJSON)]
        let vm = makeVM(initialState: filled())
        vm.primaryTapped()
        await waitFor("step is confirm") { vm.currentStep == .confirm && !vm.isCheckingAddress }
        vm.leadingTapped()
        XCTAssertEqual(vm.currentStep, .address)
    }

    // MARK: - Role gating

    func testRoleStepRequiresSelection() {
        var seed = filled()
        seed.step = AddHomeStep.role.rawValue
        let vm = AddHomeWizardViewModel(api: makeAPI(), initialState: seed)
        XCTAssertFalse(vm.chrome.primaryCTAEnabled)
        vm.selectRole(.owner)
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    // MARK: - Submit happy path

    func testSubmitAdvancesToSuccessAndRecordsHomeId() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.createHomeJSON)]
        var seed = filled()
        seed.step = AddHomeStep.review.rawValue
        seed.role = .owner
        let vm = makeVM(initialState: seed)
        vm.primaryTapped()
        await waitFor("currentStep == .success") { vm.currentStep == .success }
        XCTAssertEqual(vm.currentStep, .success)
        XCTAssertEqual(vm.createdHomeId, "home_42")
        XCTAssertEqual(vm.chrome.primaryCTALabel, "View home")
        XCTAssertEqual(vm.chrome.secondaryCTA?.identifier, "addHomeBackToHub")
        XCTAssertFalse(vm.chrome.showsProgressBar, "Success step hides the segmented progress bar.")
    }

    func testSubmitErrorKeepsUserOnReview() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{\"error\":\"server\"}")]
        var seed = filled()
        seed.step = AddHomeStep.review.rawValue
        seed.role = .owner
        let vm = makeVM(initialState: seed)
        vm.primaryTapped()
        await waitFor("errorMessage set") { vm.errorMessage != nil }
        XCTAssertEqual(vm.currentStep, .review)
        XCTAssertNotNil(vm.errorMessage)
    }

    // MARK: - Success step CTAs

    func testSuccessPrimaryFiresOpenDashboardEvent() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.createHomeJSON)]
        var seed = filled()
        seed.step = AddHomeStep.review.rawValue
        seed.role = .owner
        let vm = makeVM(initialState: seed)
        vm.primaryTapped()
        await waitFor("createdHomeId set") { vm.createdHomeId != nil }
        // Now on success step.
        vm.primaryTapped()
        await waitFor("openHomeDashboard event") {
            vm.pendingEvent == .openHomeDashboard(homeId: "home_42")
        }
        XCTAssertEqual(vm.pendingEvent, .openHomeDashboard(homeId: "home_42"))
    }

    func testSuccessSecondaryFiresDismissEvent() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.createHomeJSON)]
        var seed = filled()
        seed.step = AddHomeStep.review.rawValue
        seed.role = .owner
        let vm = makeVM(initialState: seed)
        vm.primaryTapped()
        await waitFor("createdHomeId set") { vm.createdHomeId != nil }
        vm.secondaryTapped()
        XCTAssertEqual(vm.pendingEvent, .dismiss)
    }

    // MARK: - Close-confirm

    func testCloseOnEmptyStep1IsClean() {
        let vm = AddHomeWizardViewModel(api: makeAPI(), initialState: .empty)
        XCTAssertFalse(vm.chrome.dirty)
    }

    func testCloseOnFilledStep1IsDirty() {
        let vm = AddHomeWizardViewModel(api: makeAPI(), initialState: filled())
        XCTAssertTrue(vm.chrome.dirty)
    }

    func testCloseOnSuccessIsClean() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.createHomeJSON)]
        var seed = filled()
        seed.step = AddHomeStep.review.rawValue
        seed.role = .owner
        let vm = makeVM(initialState: seed)
        vm.primaryTapped()
        await waitFor("currentStep == .success") { vm.currentStep == .success }
        XCTAssertFalse(vm.chrome.dirty)
    }

    // MARK: - Suggestions

    func testSuggestionsDebounce() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.suggestionsJSON)]
        let vm = makeVM(initialState: .empty)
        vm.update(.street, to: "412 Elm St")
        vm.update(.city, to: "Portland")
        vm.update(.state, to: "OR")
        vm.update(.zip, to: "97214")
        // Right away, no fetch yet.
        XCTAssertEqual(vm.suggestions.count, 0)
        // Poll for up to 5s — debounce is 300ms, plus the URL stub round
        // trip and main-actor hop. Tight 450ms sleeps raced on CI runners.
        await waitFor("suggestions populated", timeout: 5.0) { !vm.suggestions.isEmpty }
        XCTAssertGreaterThan(vm.suggestions.count, 0)
    }

    func testSelectSuggestionPopulatesStreet() {
        let vm = makeVM(initialState: .empty)
        vm.selectSuggestion("100 Test Ave")
        XCTAssertEqual(vm.form.address.street, "100 Test Ave")
    }

    // MARK: - Restore

    func testRestoreCopiesSnapshotIntoEmptyForm() {
        let vm = AddHomeWizardViewModel(api: makeAPI(), initialState: .empty)
        vm.restore(from: filled())
        XCTAssertEqual(vm.currentStep, .address)
        XCTAssertEqual(vm.form.address.street, "412 Elm St")
    }

    func testRestoreNoOpsOnceFormIsDirty() {
        let vm = AddHomeWizardViewModel(api: makeAPI(), initialState: filled())
        let other = AddHomeFormState(
            step: AddHomeStep.review.rawValue,
            address: AddHomeAddressFields(street: "X"),
            isPrimary: false,
            role: .tenant
        )
        vm.restore(from: other)
        XCTAssertEqual(vm.form.address.street, "412 Elm St", "Restore should not stomp existing form data.")
    }

    // MARK: - Suggestion parser

    func testFlattenSuggestionsHandlesNestedPayload() throws {
        let raw = #"{"results":[{"address":"100 Main St"},{"nested":{"address":"200 Oak St"}}]}"#
        let json = Data(raw.utf8)
        let value = try JSONDecoder().decode(JSONValue.self, from: json)
        let flat = AddHomeWizardViewModel.flattenSuggestions(value)
        XCTAssertEqual(flat, ["100 Main St", "200 Oak St"])
    }
}
