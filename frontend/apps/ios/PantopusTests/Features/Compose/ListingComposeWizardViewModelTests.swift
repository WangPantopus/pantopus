//
//  ListingComposeWizardViewModelTests.swift
//  PantopusTests
//
//  Covers the Snap & Sell state machine: photo grid operations, validation
//  gating across the six steps, submit happy path + error rollback, and
//  the success-step CTA wiring.
//

import XCTest
@testable import Pantopus

@MainActor
final class ListingComposeWizardViewModelTests: XCTestCase {
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

    private func makeVM(initialState: ListingComposeFormState = .empty) -> ListingComposeWizardViewModel {
        ListingComposeWizardViewModel(api: makeAPI(), initialState: initialState) { true }
    }

    private static let createListingJSON = """
    {"message":"Listing created successfully","listing":{
      "id":"listing_42",
      "title":"Moving boxes — bundle of 18",
      "is_free":false,
      "price":25,
      "category":"goods",
      "layer":"goods",
      "listing_type":"sell_item",
      "status":"active"
    }}
    """

    private func readyToSubmit() -> ListingComposeFormState {
        ListingComposeFormState(
            step: ListingComposeStep.review.rawValue,
            photos: [
                ListingComposePhoto(token: "photo_1"),
                ListingComposePhoto(token: "photo_2")
            ],
            title: "Moving boxes — bundle of 18",
            category: .goods,
            condition: .likeNew,
            bodyText: "Lightly used, perfect for a one-bedroom move across town.",
            priceKind: .fixed,
            priceAmount: "25",
            fulfillment: .pickup,
            locationKind: .savedAddress,
            locationLabel: ""
        )
    }

    // MARK: - Chrome shape

    func testInitialChromeIsPhotosStep() {
        let vm = makeVM()
        let chrome = vm.chrome
        XCTAssertEqual(chrome.title, "List an item")
        XCTAssertFalse(chrome.primaryCTAEnabled, "Continue is disabled until ≥1 photo is added.")
        XCTAssertEqual(chrome.primaryCTALabel, "Continue")
        XCTAssertEqual(chrome.leading, .close)
        XCTAssertEqual(chrome.progressLabel, .stepOf(current: 1, total: 6))
    }

    func testProgressLabelOnEachStep() {
        for step in ListingComposeStep.allCases where step != .success {
            var seed = ListingComposeFormState.empty
            seed.step = step.rawValue
            let vm = makeVM(initialState: seed)
            if let number = step.stepNumber {
                XCTAssertEqual(vm.chrome.progressLabel, .stepOf(current: number, total: 6))
                XCTAssertTrue(vm.chrome.showsProgressBar)
            }
        }
    }

    // MARK: - Photo step

    func testPhotoStepRequiresAtLeastOnePhoto() {
        let vm = makeVM()
        XCTAssertFalse(vm.chrome.primaryCTAEnabled)
        vm.addPhoto(token: "photo_a")
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    func testAddingPhotosUntilCapIsReached() {
        let vm = makeVM()
        for index in 0..<10 {
            vm.addPhoto(token: "photo_\(index)")
        }
        XCTAssertEqual(vm.form.photos.count, ListingComposeFormState.maxPhotos)
    }

    func testRemovePhotoById() {
        let vm = makeVM()
        vm.addPhoto(token: "a")
        vm.addPhoto(token: "b")
        guard let firstId = vm.form.photos.first?.id else { return XCTFail("no photo") }
        vm.removePhoto(id: firstId)
        XCTAssertEqual(vm.form.photos.count, 1)
        XCTAssertEqual(vm.form.photos.first?.token, "b")
    }

    func testMovePhotoChangesHero() {
        let vm = makeVM()
        vm.addPhoto(token: "a")
        vm.addPhoto(token: "b")
        vm.addPhoto(token: "c")
        XCTAssertEqual(vm.heroPhoto?.token, "a")
        vm.movePhoto(from: 2, to: 0)
        XCTAssertEqual(vm.heroPhoto?.token, "c")
    }

    func testMakeHeroPromotesToZero() {
        let vm = makeVM()
        vm.addPhoto(token: "a")
        vm.addPhoto(token: "b")
        vm.addPhoto(token: "c")
        guard let secondId = vm.form.photos.dropFirst().first?.id else { return XCTFail("no photo") }
        vm.makeHero(id: secondId)
        XCTAssertEqual(vm.heroPhoto?.token, "b")
    }

    func testMakeHeroIsNoOpForFirstSlot() {
        let vm = makeVM()
        vm.addPhoto(token: "a")
        vm.addPhoto(token: "b")
        guard let firstId = vm.form.photos.first?.id else { return XCTFail("no photo") }
        vm.makeHero(id: firstId)
        XCTAssertEqual(vm.heroPhoto?.token, "a")
    }

    // MARK: - Title + category

    func testTitleCategoryGate() {
        var seed = ListingComposeFormState.empty
        seed.step = ListingComposeStep.titleCategory.rawValue
        seed.photos = [ListingComposePhoto(token: "p")]
        let vm = makeVM(initialState: seed)
        XCTAssertFalse(vm.chrome.primaryCTAEnabled, "Title and category both required.")
        vm.setTitle("Hi")
        XCTAssertFalse(vm.chrome.primaryCTAEnabled, "Title under 5 chars must fail.")
        vm.setTitle("Moving boxes — bundle of 18")
        XCTAssertFalse(vm.chrome.primaryCTAEnabled, "Title alone is not enough — category must be set.")
        vm.setCategory(.goods)
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    func testTitleMaxLength() {
        var seed = ListingComposeFormState.empty
        seed.step = ListingComposeStep.titleCategory.rawValue
        seed.photos = [ListingComposePhoto(token: "p")]
        let vm = makeVM(initialState: seed)
        vm.setCategory(.goods)
        vm.setTitle(String(repeating: "a", count: 81))
        XCTAssertFalse(vm.chrome.primaryCTAEnabled, "Title over 80 chars must fail.")
        vm.setTitle(String(repeating: "a", count: 80))
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    func testFreeCategoryPicksPriceKindFree() {
        let vm = makeVM()
        vm.setCategory(.free)
        XCTAssertEqual(vm.form.priceKind, .free)
    }

    func testWantedCategoryClearsConditionAndSkipsConditionGate() {
        var seed = ListingComposeFormState.empty
        seed.step = ListingComposeStep.conditionDescription.rawValue
        seed.photos = [ListingComposePhoto(token: "p")]
        seed.title = "Looking for a sewing machine"
        seed.category = .wanted
        seed.bodyText = "Anything in working order would be great — thank you, neighbors!"
        let vm = makeVM(initialState: seed)
        XCTAssertTrue(vm.chrome.primaryCTAEnabled, "Wanted skips the condition requirement.")
    }

    // MARK: - Condition + description

    func testDescriptionMinLength() {
        var seed = ListingComposeFormState.empty
        seed.step = ListingComposeStep.conditionDescription.rawValue
        seed.photos = [ListingComposePhoto(token: "p")]
        seed.title = "Moving boxes — bundle of 18"
        seed.category = .goods
        seed.condition = .likeNew
        let vm = makeVM(initialState: seed)
        XCTAssertFalse(vm.chrome.primaryCTAEnabled, "Empty description must fail.")
        vm.setBody("Short body.")
        XCTAssertFalse(vm.chrome.primaryCTAEnabled, "Description under 20 chars must fail.")
        vm.setBody("Lightly used, perfect for a one-bedroom move across town.")
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    func testConditionRequiredForGoods() {
        var seed = ListingComposeFormState.empty
        seed.step = ListingComposeStep.conditionDescription.rawValue
        seed.photos = [ListingComposePhoto(token: "p")]
        seed.title = "Moving boxes — bundle of 18"
        seed.category = .goods
        seed.bodyText = "Lightly used, perfect for a one-bedroom move across town."
        let vm = makeVM(initialState: seed)
        XCTAssertFalse(vm.chrome.primaryCTAEnabled, "Condition required for goods.")
        vm.setCondition(.likeNew)
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    // MARK: - Price step

    func testFixedPriceRequiresPositiveAmount() {
        var seed = ListingComposeFormState.empty
        seed.step = ListingComposeStep.price.rawValue
        let vm = makeVM(initialState: seed)
        vm.setPriceKind(.fixed)
        XCTAssertFalse(vm.chrome.primaryCTAEnabled, "Empty amount must fail.")
        vm.setPriceAmount("0")
        XCTAssertFalse(vm.chrome.primaryCTAEnabled, "Zero must fail.")
        vm.setPriceAmount("25")
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    func testFreePriceKindIsAlwaysValid() {
        var seed = ListingComposeFormState.empty
        seed.step = ListingComposeStep.price.rawValue
        let vm = makeVM(initialState: seed)
        vm.setPriceKind(.free)
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    func testPriceAmountFiltersToDecimal() {
        let vm = makeVM()
        vm.setPriceAmount("abc12.5")
        XCTAssertEqual(vm.form.priceAmount, "12.5")
        vm.setPriceAmount("12.3.4")
        // Two-decimal-separator input is rejected — last valid value sticks.
        XCTAssertEqual(vm.form.priceAmount, "12.5")
    }

    // MARK: - Location step

    func testLocationRequiresSelection() {
        var seed = ListingComposeFormState.empty
        seed.step = ListingComposeStep.location.rawValue
        let vm = makeVM(initialState: seed)
        XCTAssertFalse(vm.chrome.primaryCTAEnabled)
        vm.setLocationKind(.savedAddress)
        XCTAssertTrue(vm.chrome.primaryCTAEnabled)
    }

    // MARK: - Submit happy path

    func testSubmitAdvancesToSuccessAndRecordsListingId() async {
        SequencedURLProtocol.sequence = [.status(201, body: Self.createListingJSON)]
        let vm = makeVM(initialState: readyToSubmit())
        await vm.advanceForTesting()
        XCTAssertEqual(vm.currentStep, .success)
        XCTAssertEqual(vm.createdListingId, "listing_42")
        XCTAssertEqual(vm.chrome.primaryCTALabel, "View listing")
        XCTAssertEqual(vm.chrome.secondaryCTA?.identifier, "listingComposeBackToMarketplace")
        XCTAssertFalse(vm.chrome.showsProgressBar, "Success step hides the progress bar.")
    }

    func testSubmitErrorKeepsUserOnReview() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{\"error\":\"server\"}")]
        let vm = makeVM(initialState: readyToSubmit())
        await vm.advanceForTesting()
        XCTAssertEqual(vm.currentStep, .review)
        XCTAssertNotNil(vm.errorMessage)
    }

    func testSubmitOfflineSurfacesInlineError() async {
        let vm = ListingComposeWizardViewModel(
            api: makeAPI(),
            initialState: readyToSubmit()
        ) { false }
        await vm.advanceForTesting()
        XCTAssertEqual(vm.currentStep, .review, "Offline submit stays on review.")
        XCTAssertEqual(
            vm.errorMessage,
            "You're offline. Try again when you're back online."
        )
    }

    // MARK: - Success step CTAs

    func testSuccessPrimaryEmitsOpenListingEvent() async {
        SequencedURLProtocol.sequence = [.status(201, body: Self.createListingJSON)]
        let vm = makeVM(initialState: readyToSubmit())
        await vm.advanceForTesting()
        await vm.advanceForTesting()
        XCTAssertEqual(vm.pendingEvent, .openListingDetail(listingId: "listing_42"))
    }

    func testSuccessSecondaryEmitsDismiss() async {
        SequencedURLProtocol.sequence = [.status(201, body: Self.createListingJSON)]
        let vm = makeVM(initialState: readyToSubmit())
        await vm.advanceForTesting()
        vm.secondaryTapped()
        XCTAssertEqual(vm.pendingEvent, .dismiss)
    }

    // MARK: - Close-confirm

    func testCloseOnEmptyStep1IsClean() {
        let vm = makeVM()
        XCTAssertFalse(vm.chrome.dirty)
    }

    func testCloseOnDirtyStep1RequiresConfirm() {
        let vm = makeVM()
        vm.addPhoto(token: "a")
        XCTAssertTrue(vm.chrome.dirty)
    }

    func testCloseOnSuccessIsClean() async {
        SequencedURLProtocol.sequence = [.status(201, body: Self.createListingJSON)]
        let vm = makeVM(initialState: readyToSubmit())
        await vm.advanceForTesting()
        XCTAssertFalse(vm.chrome.dirty)
    }

    // MARK: - Restore

    func testRestoreCopiesSnapshotIntoEmptyForm() {
        let vm = makeVM()
        vm.restore(from: readyToSubmit())
        XCTAssertEqual(vm.currentStep, .review)
        XCTAssertEqual(vm.form.title, "Moving boxes — bundle of 18")
    }

    func testRestoreNoOpsOnceFormIsDirty() {
        let vm = makeVM()
        vm.addPhoto(token: "existing")
        vm.restore(from: readyToSubmit())
        XCTAssertEqual(vm.form.photos.count, 1)
        XCTAssertEqual(vm.form.photos.first?.token, "existing")
    }

    // MARK: - Back navigation

    func testBackOnTitleStepGoesToPhotos() {
        var seed = ListingComposeFormState.empty
        seed.step = ListingComposeStep.titleCategory.rawValue
        seed.photos = [ListingComposePhoto(token: "p")]
        let vm = makeVM(initialState: seed)
        vm.leadingTapped()
        XCTAssertEqual(vm.currentStep, .photos)
    }
}
