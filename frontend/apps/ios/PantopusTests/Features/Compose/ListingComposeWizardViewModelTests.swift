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

    private func makeEditVM(
        listingId: String = "listing_42",
        jumpToStep: ListingComposeStep? = nil,
        initialState: ListingComposeFormState = .empty
    ) -> ListingComposeWizardViewModel {
        ListingComposeWizardViewModel(
            mode: .edit(listingId: listingId, jumpToStep: jumpToStep),
            api: makeAPI(),
            initialState: initialState
        ) { true }
    }

    /// Detail-fetch payload used by the edit-prefill tests. Mirrors the
    /// `GET /api/listings/:id` envelope returned by
    /// `backend/routes/listings.js:1375`.
    private static let editListingDetailJSON = """
    {"listing":{
      "id":"listing_42",
      "user_id":"user_seller",
      "title":"Mid-century walnut credenza",
      "description":"Solid walnut, four sliding doors, dovetail joinery.",
      "price":420,
      "is_free":false,
      "category":"furniture",
      "condition":"like_new",
      "media_urls":["https://example.com/a.jpg","https://example.com/b.jpg"],
      "first_image":"https://example.com/a.jpg",
      "layer":"goods",
      "listing_type":"sell_item",
      "location_name":"Lincoln Park bandshell",
      "status":"active"
    }}
    """

    private static let updateListingJSON = """
    {"message":"Listing updated successfully","listing":{
      "id":"listing_42",
      "title":"Mid-century walnut credenza",
      "is_free":false,
      "price":399,
      "category":"furniture",
      "layer":"goods",
      "listing_type":"sell_item",
      "status":"active"
    }}
    """

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

    // MARK: - Edit mode

    func testEditModeChromeShowsEditTitle() {
        let vm = makeEditVM()
        XCTAssertTrue(vm.isEditMode)
        XCTAssertEqual(vm.chrome.title, "Edit listing")
        XCTAssertEqual(vm.editingListingId, "listing_42")
    }

    func testEditModeReviewStepCTAReads_SaveChanges() {
        var seed = readyToSubmit()
        seed.title = "Mid-century walnut credenza"
        seed.category = .goods
        seed.condition = .likeNew
        let vm = makeEditVM(initialState: seed)
        XCTAssertEqual(vm.chrome.primaryCTALabel, "Save changes")
    }

    func testEditPrefillProjectsListingDTOIntoForm() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.editListingDetailJSON)
        ]
        let vm = makeEditVM()
        await vm.loadExistingIfNeeded()
        XCTAssertEqual(vm.form.title, "Mid-century walnut credenza")
        XCTAssertEqual(vm.form.category, .goods)
        XCTAssertEqual(vm.form.condition, .likeNew)
        XCTAssertEqual(vm.form.priceKind, .fixed)
        XCTAssertEqual(vm.form.priceAmount, "420")
        XCTAssertEqual(vm.form.bodyText, "Solid walnut, four sliding doors, dovetail joinery.")
        XCTAssertEqual(vm.form.photos.count, 2)
        XCTAssertEqual(vm.form.locationKind, .meetPoint)
        XCTAssertEqual(vm.form.locationLabel, "Lincoln Park bandshell")
        // jumpToStep default → land on review so the user can scan +
        // tap Save changes immediately.
        XCTAssertEqual(vm.currentStep, .review)
    }

    func testEditPrefillJumpsToPriceStepWhenRequested() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.editListingDetailJSON)
        ]
        let vm = makeEditVM(jumpToStep: .price)
        await vm.loadExistingIfNeeded()
        XCTAssertEqual(vm.currentStep, .price, "Entry point requested price step.")
        XCTAssertEqual(vm.form.priceAmount, "420")
    }

    func testEditPrefillIsNoOpWhenFormAlreadyDirty() async {
        var seed = ListingComposeFormState.empty
        seed.title = "User-edited title"
        seed.photos = [ListingComposePhoto(token: "user_photo")]
        let vm = makeEditVM(initialState: seed)
        // No sequence stubbed — the fetch must not be attempted.
        await vm.loadExistingIfNeeded()
        XCTAssertEqual(vm.form.title, "User-edited title")
    }

    func testEditPrefillFreeListingMapsToFreeCategory() async {
        let json = """
        {"listing":{
          "id":"listing_free",
          "title":"Free moving boxes",
          "is_free":true,
          "category":"free_stuff",
          "layer":"goods",
          "listing_type":"free_item",
          "status":"active"
        }}
        """
        SequencedURLProtocol.sequence = [.status(200, body: json)]
        let vm = makeEditVM(listingId: "listing_free")
        await vm.loadExistingIfNeeded()
        XCTAssertEqual(vm.form.category, .free)
        XCTAssertEqual(vm.form.priceKind, .free)
        XCTAssertEqual(vm.form.priceAmount, "")
    }

    func testEditPrefillWantedListingMapsToWantedCategory() async {
        let json = """
        {"listing":{
          "id":"listing_w",
          "title":"Looking for a sewing machine",
          "is_free":false,
          "category":"furniture",
          "layer":"goods",
          "listing_type":"wanted_request",
          "status":"active"
        }}
        """
        SequencedURLProtocol.sequence = [.status(200, body: json)]
        let vm = makeEditVM(listingId: "listing_w")
        await vm.loadExistingIfNeeded()
        XCTAssertEqual(vm.form.category, .wanted)
    }

    func testEditPrefillSurfacesErrorOnFetchFailure() async {
        SequencedURLProtocol.sequence = [
            .status(500, body: "{\"error\":\"boom\"}")
        ]
        let vm = makeEditVM()
        await vm.loadExistingIfNeeded()
        XCTAssertNotNil(vm.errorMessage)
        XCTAssertEqual(vm.form, .empty, "Form stays empty so retry can re-fetch.")
    }

    func testEditSubmitFiresPATCHAndEmitsListingUpdated() async {
        // Two-call sequence: prefill GET then update PATCH.
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.editListingDetailJSON),
            .status(200, body: Self.updateListingJSON)
        ]
        let vm = makeEditVM()
        await vm.loadExistingIfNeeded()
        XCTAssertEqual(vm.currentStep, .review)
        await vm.advanceForTesting() // .review → submit
        XCTAssertEqual(vm.currentStep, .success)
        XCTAssertEqual(vm.createdListingId, "listing_42")
        // Success step CTAs adapt to edit mode.
        XCTAssertEqual(vm.chrome.primaryCTALabel, "Back to listing")
        XCTAssertEqual(vm.chrome.secondaryCTA?.identifier, "listingComposeEditDone")
        // Primary tap on success emits `.listingUpdated`, not `.openListingDetail`.
        await vm.advanceForTesting()
        XCTAssertEqual(vm.pendingEvent, .listingUpdated(listingId: "listing_42"))
    }

    func testEditSubmitErrorKeepsUserOnReviewWithErrorBanner() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.editListingDetailJSON),
            .status(500, body: "{\"error\":\"boom\"}")
        ]
        let vm = makeEditVM()
        await vm.loadExistingIfNeeded()
        await vm.advanceForTesting()
        XCTAssertEqual(vm.currentStep, .review)
        XCTAssertNotNil(vm.errorMessage)
    }

    func testEditModeIsAlwaysDirtyPreSuccess() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.editListingDetailJSON)
        ]
        let vm = makeEditVM()
        await vm.loadExistingIfNeeded()
        XCTAssertTrue(vm.chrome.dirty, "Edit mode warns on close so the user doesn't lose intent.")
    }

    func testEditModeSecondaryTapEmitsListingUpdated() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.editListingDetailJSON),
            .status(200, body: Self.updateListingJSON)
        ]
        let vm = makeEditVM()
        await vm.loadExistingIfNeeded()
        await vm.advanceForTesting() // submit
        XCTAssertEqual(vm.currentStep, .success)
        vm.secondaryTapped()
        XCTAssertEqual(vm.pendingEvent, .listingUpdated(listingId: "listing_42"))
    }

    func testEditModeRestoreFromSceneStorageIsNoOp() {
        let vm = makeEditVM()
        var snap = ListingComposeFormState.empty
        snap.title = "From scene storage"
        vm.restore(from: snap)
        XCTAssertEqual(vm.form.title, "", "Edit mode never consumes the create-draft snapshot.")
    }
}
