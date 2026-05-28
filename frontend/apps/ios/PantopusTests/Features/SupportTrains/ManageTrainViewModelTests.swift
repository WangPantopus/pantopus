//
//  ManageTrainViewModelTests.swift
//  PantopusTests
//
//  A13.13 — covers the Manage train VM projection. No backend: `load()`
//  emits the deterministic ACTIVE fixture, draft mutations stay
//  in-memory, and the Close-train sheet flips the train to CLOSED on
//  confirm. Mirrors the Android `ManageTrainViewModelTest` shape so
//  the state machines stay in lock-step across platforms.
//

import XCTest
@testable import Pantopus

@MainActor
final class ManageTrainViewModelTests: XCTestCase {
    func testLoadProjectsActiveFixture() async {
        let vm = ManageTrainViewModel(trainId: ManageTrainSampleData.trainId)
        await vm.load()
        guard case let .loaded(content) = vm.state else {
            return XCTFail("Expected .loaded, got \(vm.state)")
        }
        XCTAssertEqual(content.title, "Meals for the Murphy family")
        XCTAssertTrue(content.isActive)
        XCTAssertEqual(content.slotFillValue, "18/21")
        XCTAssertEqual(content.helpersValue, "12")
        XCTAssertEqual(content.daysLeftValue, "9d")
        XCTAssertEqual(content.dropoutValue, "1")
        XCTAssertEqual(content.organizeRows.map(\.id), ["edit-dates", "invite", "analytics"])
        XCTAssertEqual(content.closeRow.id, "close")
        XCTAssertTrue(content.closeRow.isDestructive)
    }

    func testInitialDraftMirrorsContent() async {
        let vm = ManageTrainViewModel(trainId: ManageTrainSampleData.trainId)
        await vm.load()
        XCTAssertFalse(vm.draftMessage.isEmpty, "Active fixture seeds a typed draft")
        XCTAssertEqual(vm.selectedAudienceId, "all")
        XCTAssertTrue(vm.pushToPhones)
        XCTAssertTrue(vm.canSendUpdate)
    }

    func testCharacterCapClampsOversizeInput() async {
        let vm = ManageTrainViewModel(trainId: ManageTrainSampleData.trainId)
        await vm.load()
        let oversized = String(repeating: "x", count: manageTrainMessageMaxChars + 50)
        vm.updateDraftMessage(oversized)
        XCTAssertEqual(vm.draftMessage.count, manageTrainMessageMaxChars)
        XCTAssertEqual(vm.characterCount, manageTrainMessageMaxChars)
    }

    func testEmptyDraftDisablesSend() async {
        let vm = ManageTrainViewModel(trainId: ManageTrainSampleData.trainId)
        await vm.load()
        vm.updateDraftMessage("   \n  ")
        XCTAssertFalse(vm.canSendUpdate, "Whitespace-only draft is not sendable")
    }

    func testSelectAudienceUpdatesSelection() async {
        let vm = ManageTrainViewModel(trainId: ManageTrainSampleData.trainId)
        await vm.load()
        vm.selectAudience("upcoming")
        XCTAssertEqual(vm.selectedAudienceId, "upcoming")
    }

    func testSelectAudienceRejectsUnknownId() async {
        let vm = ManageTrainViewModel(trainId: ManageTrainSampleData.trainId)
        await vm.load()
        vm.selectAudience("does-not-exist")
        XCTAssertEqual(vm.selectedAudienceId, "all", "Unknown ids are dropped")
    }

    func testSendUpdateClearsDraftAndFlashesToast() async {
        let vm = ManageTrainViewModel(trainId: ManageTrainSampleData.trainId)
        await vm.load()
        XCTAssertNil(vm.toast)
        vm.sendUpdate()
        XCTAssertEqual(vm.draftMessage, "")
        XCTAssertNotNil(vm.toast, "Send fires the helpers toast")
        XCTAssertTrue(vm.toast?.contains("12") == true)
    }

    func testShowAndHideCloseSheet() async {
        let vm = ManageTrainViewModel(trainId: ManageTrainSampleData.trainId)
        await vm.load()
        XCTAssertEqual(vm.sheetMode, .hidden)
        vm.showCloseSheet()
        XCTAssertEqual(vm.sheetMode, .closing)
        vm.hideCloseSheet()
        XCTAssertEqual(vm.sheetMode, .hidden)
    }

    func testConfirmCloseFlipsTrainAndFiresToast() async {
        let vm = ManageTrainViewModel(trainId: ManageTrainSampleData.trainId)
        await vm.load()
        vm.showCloseSheet()
        vm.confirmClose()
        XCTAssertEqual(vm.sheetMode, .closed)
        if case let .loaded(content) = vm.state {
            XCTAssertFalse(content.isActive, "Confirm close flips the train chip to Closed")
        } else {
            XCTFail("Expected .loaded after confirmClose")
        }
        XCTAssertNotNil(vm.toast)
    }

    func testSampleDataActiveFixtureMatchesDesignCopy() {
        let content = ManageTrainSampleData.active
        XCTAssertEqual(content.close.mealsDelivered, "18")
        XCTAssertEqual(content.close.neighborsHelped, "12")
        XCTAssertEqual(content.close.coverageDays, "12d")
        XCTAssertTrue(content.close.recipientQuote.contains("Theo"))
        XCTAssertEqual(content.audienceChips.first?.label, "All helpers")
        XCTAssertEqual(content.audienceChips.first?.count, "12")
    }
}
