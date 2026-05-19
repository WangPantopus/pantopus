//
//  GigComposeViewModelTests.swift
//  PantopusTests
//
//  Covers the P2.2 Post-a-Task wizard state machine: per-step validation
//  gates, forward/back navigation, preselect category, submit happy path,
//  submit error rollback, close-confirm dirty flag, and the per-step
//  chrome shape used by the snapshot baselines.
//

import XCTest
@testable import Pantopus

@MainActor
final class GigComposeViewModelTests: XCTestCase {
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

    /// Centralised constructor that injects an "always online" stub.
    private func makeVM(initialState: GigComposeFormState = .empty) -> GigComposeViewModel {
        GigComposeViewModel(
            api: makeAPI(),
            location: FixedLocationProvider(
                UserCoordinate(latitude: 40.7484, longitude: -73.9857, accuracyMeters: 100)
            ),
            initialState: initialState
        ) { true }
    }

    private static let createGigJSON = """
    {"message":"ok","gig":{
      "id":"gig_42","title":"Hang 3 shelves","description":"Need 3 IKEA shelves mounted","price":60.0,
      "category":"handyman","status":"open","created_at":"2025-01-01T00:00:00Z"
    }}
    """

    private func filledAtBasics() -> GigComposeFormState {
        GigComposeFormState(
            step: GigComposeStep.basics.rawValue,
            category: .handyman,
            title: "Hang 3 shelves in the living room",
            description: "Need three IKEA Lack shelves mounted on drywall. I have studs marked.",
            photoIds: []
        )
    }

    private func filledAtReview() -> GigComposeFormState {
        let future = ISO8601DateFormatter().string(from: Date().addingTimeInterval(86_400))
        return GigComposeFormState(
            step: GigComposeStep.review.rawValue,
            category: .handyman,
            title: "Hang 3 shelves in the living room",
            description: "Need three IKEA Lack shelves mounted on drywall. I have studs marked.",
            photoIds: [],
            budgetType: .fixed,
            budgetMin: "60",
            budgetMax: "",
            scheduleType: .oneTime,
            scheduledStartISO: future,
            locationMode: .yourAddress
        )
    }

    // MARK: - Initial chrome (Step 1 — Category)

    func testInitialChromeReflectsCategoryStep() {
        let vm = makeVM()
        let chrome = vm.chrome
        XCTAssertEqual(chrome.title, "Post a task")
        XCTAssertEqual(chrome.primaryCTALabel, "Continue")
        XCTAssertFalse(chrome.primaryCTAEnabled, "Continue must be disabled until a category is selected.")
        XCTAssertEqual(chrome.leading, .close)
        XCTAssertEqual(chrome.progressLabel, .stepOf(current: 1, total: 6))
        XCTAssertTrue(chrome.showsProgressBar)
    }

    func testSelectCategoryEnablesContinue() {
        let vm = makeVM()
        vm.selectCategory(.handyman)
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    func testPreselectFromRouteArgument() {
        let preselected = GigComposeFormState(category: .cleaning)
        let vm = makeVM(initialState: preselected)
        XCTAssertEqual(vm.form.category, .cleaning)
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    func testCategoryFromRawKeyParsesKnownAndUnknownValues() {
        XCTAssertEqual(GigComposeCategory.from(rawKey: "handyman"), .handyman)
        XCTAssertEqual(GigComposeCategory.from(rawKey: "petcare"), .petcare)
        XCTAssertNil(GigComposeCategory.from(rawKey: nil))
        XCTAssertNil(GigComposeCategory.from(rawKey: ""), "Empty key must return nil so the user picks fresh.")
        XCTAssertNil(GigComposeCategory.from(rawKey: "all"), "The feed filter sentinel must not preselect a tile.")
        XCTAssertNil(GigComposeCategory.from(rawKey: "nope"))
    }

    // MARK: - Step 2 — Basics validation

    func testBasicsStepRequiresTitleAndDescriptionLengths() {
        let seed = GigComposeFormState(step: GigComposeStep.basics.rawValue, category: .handyman)
        let vm = makeVM(initialState: seed)
        XCTAssertFalse(vm.chrome.primaryCTAEnabled, "Empty fields must block Continue.")
        vm.setTitle("1234")
        XCTAssertFalse(vm.chrome.primaryCTAEnabled, "Title shorter than the 5-char minimum must block.")
        vm.setTitle("Hang shelves")
        XCTAssertFalse(vm.chrome.primaryCTAEnabled, "Description shorter than the 20-char minimum must block.")
        vm.setDescription("Long enough description to clear the 20 char floor.")
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    func testTitleCannotExceedMax() {
        let vm = makeVM(initialState: GigComposeFormState(step: GigComposeStep.basics.rawValue, category: .handyman))
        let oversize = String(repeating: "a", count: GigComposeLimits.titleMax + 50)
        vm.setTitle(oversize)
        XCTAssertEqual(vm.form.title.count, GigComposeLimits.titleMax)
    }

    func testPhotoCapEnforcedOnAdd() {
        let vm = makeVM(initialState: GigComposeFormState(step: GigComposeStep.basics.rawValue, category: .handyman))
        for _ in 0 ..< GigComposeLimits.maxPhotos + 3 { vm.addPhoto("placeholder://photo") }
        XCTAssertEqual(vm.form.photoIds.count, GigComposeLimits.maxPhotos)
    }

    // MARK: - Step 3 — Budget validation

    func testBudgetOffersEnablesContinueWithoutNumbers() {
        var seed = filledAtBasics()
        seed.step = GigComposeStep.budget.rawValue
        let vm = makeVM(initialState: seed)
        XCTAssertFalse(vm.chrome.primaryCTAEnabled)
        vm.selectBudgetType(.offers)
        XCTAssertTrue(vm.chrome.primaryCTAEnabled, "Open-to-bids must not require a numeric min.")
    }

    func testBudgetFixedRequiresPositiveMin() {
        var seed = filledAtBasics()
        seed.step = GigComposeStep.budget.rawValue
        let vm = makeVM(initialState: seed)
        vm.selectBudgetType(.fixed)
        XCTAssertFalse(vm.chrome.primaryCTAEnabled)
        vm.setBudgetMin("0")
        XCTAssertFalse(vm.chrome.primaryCTAEnabled)
        vm.setBudgetMin("25")
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    func testBudgetSanitizerStripsNonDigits() {
        let vm = makeVM()
        vm.setBudgetMin("$1,234.50abc")
        XCTAssertEqual(vm.form.budgetMin, "1234.50")
        vm.setBudgetMin("12.3.4")
        XCTAssertEqual(vm.form.budgetMin, "12.34", "Only one decimal point survives sanitisation.")
    }

    // MARK: - Step 4 — Schedule validation

    func testScheduleOneTimeRequiresFutureDate() {
        var seed = filledAtBasics()
        seed.step = GigComposeStep.schedule.rawValue
        seed.budgetType = .offers
        let vm = makeVM(initialState: seed)
        vm.selectScheduleType(.oneTime)
        XCTAssertFalse(vm.chrome.primaryCTAEnabled, "One-time without a date must block.")
        vm.setScheduledStart(Date().addingTimeInterval(-3600))
        XCTAssertFalse(vm.chrome.primaryCTAEnabled, "Past dates must not satisfy the future-only rule.")
        vm.setScheduledStart(Date().addingTimeInterval(3600))
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    func testScheduleFlexibleNeedsNoDate() {
        var seed = filledAtBasics()
        seed.step = GigComposeStep.schedule.rawValue
        let vm = makeVM(initialState: seed)
        vm.selectScheduleType(.flexible)
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    func testSelectingNonOneTimeClearsDate() {
        var seed = filledAtBasics()
        seed.step = GigComposeStep.schedule.rawValue
        seed.scheduledStartISO = ISO8601DateFormatter().string(from: Date().addingTimeInterval(3600))
        let vm = makeVM(initialState: seed)
        vm.selectScheduleType(.flexible)
        XCTAssertNil(vm.form.scheduledStartISO, "Switching off one-time must clear the leftover date.")
    }

    // MARK: - Step 5 — Location validation

    func testLocationYourAddressOrVirtualNeedsNoFields() {
        var seed = filledAtBasics()
        seed.step = GigComposeStep.location.rawValue
        let vm = makeVM(initialState: seed)
        vm.selectLocationMode(.yourAddress)
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
        vm.selectLocationMode(.virtual)
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    func testLocationAPlaceRequiresCompleteAddress() {
        var seed = filledAtBasics()
        seed.step = GigComposeStep.location.rawValue
        let vm = makeVM(initialState: seed)
        vm.selectLocationMode(.aPlace)
        XCTAssertFalse(vm.chrome.primaryCTAEnabled)
        vm.updatePlaceAddress(line1: "123 Main St", city: "Portland", state: "OR", zip: "97214")
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    // MARK: - Step 6 — Review chrome

    func testReviewChromeAndBuildBody() {
        let vm = makeVM(initialState: filledAtReview())
        let chrome = vm.chrome
        XCTAssertEqual(chrome.primaryCTALabel, "Post task")
        XCTAssertEqual(chrome.leading, .back)
        let body = vm.buildCreateBody()
        XCTAssertNotNil(body)
        XCTAssertEqual(body?.title, "Hang 3 shelves in the living room")
        XCTAssertEqual(body?.payType, "fixed")
        XCTAssertEqual(body?.scheduleType, "scheduled")
        XCTAssertEqual(body?.location.mode, "home")
    }

    func testVirtualMapsToRemoteTaskFormat() {
        var seed = filledAtReview()
        seed.locationMode = .virtual
        let vm = makeVM(initialState: seed)
        let body = vm.buildCreateBody()
        XCTAssertEqual(body?.taskFormat, "remote")
        XCTAssertEqual(body?.location.mode, "custom")
    }

    func testRecurringMapsToFlexibleWireValue() {
        var seed = filledAtReview()
        seed.scheduleType = .recurring
        seed.scheduledStartISO = nil
        let vm = makeVM(initialState: seed)
        XCTAssertEqual(vm.buildCreateBody()?.scheduleType, "flexible")
    }

    // MARK: - Forward / back navigation

    func testForwardAdvancesThroughSteps() async {
        let vm = makeVM()
        vm.selectCategory(.handyman)
        await vm.advanceForTesting()
        XCTAssertEqual(vm.currentStep, .basics)
        vm.setTitle("Hang 3 shelves")
        vm.setDescription("Need three IKEA Lack shelves mounted on drywall.")
        await vm.advanceForTesting()
        XCTAssertEqual(vm.currentStep, .budget)
    }

    func testBackPreservesData() async {
        let vm = makeVM()
        vm.selectCategory(.handyman)
        await vm.advanceForTesting()
        vm.leadingTapped()
        XCTAssertEqual(vm.currentStep, .category)
        XCTAssertEqual(vm.form.category, .handyman, "Going back must not stomp the user's category pick.")
    }

    // MARK: - Submit happy path / error rollback

    func testSubmitAdvancesToSuccessAndRecordsGigId() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.createGigJSON)]
        let vm = makeVM(initialState: filledAtReview())
        await vm.advanceForTesting()
        XCTAssertEqual(vm.currentStep, .success)
        XCTAssertEqual(vm.createdGigId, "gig_42")
        XCTAssertEqual(vm.chrome.primaryCTALabel, "View task")
        XCTAssertEqual(vm.chrome.secondaryCTA?.identifier, "composeGigDone")
        XCTAssertFalse(vm.chrome.showsProgressBar)
    }

    func testSubmitErrorKeepsUserOnReview() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{\"error\":\"down\"}")]
        let vm = makeVM(initialState: filledAtReview())
        await vm.advanceForTesting()
        XCTAssertEqual(vm.currentStep, .review)
        XCTAssertNotNil(vm.errorMessage)
    }

    func testSuccessPrimaryFiresOpenGigDetailEvent() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.createGigJSON)]
        let vm = makeVM(initialState: filledAtReview())
        await vm.advanceForTesting()
        await vm.advanceForTesting()
        XCTAssertEqual(vm.pendingEvent, .openGigDetail(gigId: "gig_42"))
    }

    func testSuccessSecondaryFiresDismissEvent() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.createGigJSON)]
        let vm = makeVM(initialState: filledAtReview())
        await vm.advanceForTesting()
        vm.secondaryTapped()
        XCTAssertEqual(vm.pendingEvent, .dismiss)
    }

    // MARK: - Close-confirm

    func testCloseOnEmptyStep1IsClean() {
        let vm = makeVM()
        XCTAssertFalse(vm.chrome.dirty)
    }

    func testCloseAfterSelectingCategoryIsDirty() {
        let vm = makeVM()
        vm.selectCategory(.handyman)
        XCTAssertTrue(vm.chrome.dirty, "Any entered data must trigger the discard confirm.")
    }

    func testCloseOnSuccessStepIsClean() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.createGigJSON)]
        let vm = makeVM(initialState: filledAtReview())
        await vm.advanceForTesting()
        XCTAssertFalse(vm.chrome.dirty, "Success step must not gate dismiss with a discard confirm.")
    }

    // MARK: - Restore from SceneStorage

    func testRestoreCopiesSnapshotIntoEmptyForm() {
        let vm = makeVM()
        vm.restore(from: filledAtBasics())
        XCTAssertEqual(vm.currentStep, .basics)
        XCTAssertEqual(vm.form.title, "Hang 3 shelves in the living room")
    }

    func testRestoreNoOpsOnceFormIsDirty() {
        let vm = makeVM(initialState: filledAtBasics())
        let other = GigComposeFormState(step: GigComposeStep.budget.rawValue, category: .cleaning, title: "X")
        vm.restore(from: other)
        XCTAssertEqual(vm.form.title, "Hang 3 shelves in the living room", "Restore must not stomp existing data.")
    }
}
