//
//  ComposeBroadcastViewModelTests.swift
//  PantopusTests
//
//  A.7 (A22.2) — Behavioral coverage for the Compose Broadcast VM:
//  state derivation (empty / composing / scheduled / sending / error),
//  the live character count + over-limit guard, audience + media + send
//  reset, the unsaved-draft indicator, and the first-run CTA copy.
//

import XCTest
@testable import Pantopus

@MainActor
final class ComposeBroadcastViewModelTests: XCTestCase {
    private func makeVM(
        recents: [RecentBroadcastContent] = [],
        audienceReach: [BroadcastAudience: Int] = [:],
        maxCharacterCount: Int = 1000,
        onSent: @escaping @MainActor () -> Void = {}
    ) -> ComposeBroadcastViewModel {
        ComposeBroadcastViewModel(
            personaId: "p1",
            persona: ComposeBroadcastSampleData.persona,
            recentBroadcasts: recents,
            audienceReach: audienceReach,
            maxCharacterCount: maxCharacterCount,
            onSent: onSent
        )
    }

    func testInitialStateIsEmpty() {
        let vm = makeVM()
        XCTAssertEqual(vm.state, .empty)
        XCTAssertFalse(vm.canSend)
        XCTAssertEqual(vm.characterCount, 0)
        XCTAssertFalse(vm.isDirty)
    }

    func testTypingTransitionsToComposingAndCountsLive() {
        let vm = makeVM()
        vm.updateBody("Hello beacons")
        XCTAssertEqual(vm.state, .composing(vm.draft))
        XCTAssertEqual(vm.characterCount, 13)
        XCTAssertTrue(vm.canSend)
        XCTAssertTrue(vm.isDirty)
    }

    func testOverCharacterLimitBlocksSend() {
        let vm = makeVM(maxCharacterCount: 5)
        vm.updateBody("123456")
        XCTAssertTrue(vm.isOverLimit)
        XCTAssertFalse(vm.canSend)
    }

    func testMediaWithoutBodyStillAllowsSend() {
        let vm = makeVM()
        vm.attachMedia(ComposeMediaPreview(kind: .image, caption: "boule.jpg"))
        XCTAssertFalse(vm.draft.isEmpty)
        XCTAssertTrue(vm.canSend)
        XCTAssertEqual(vm.state, .composing(vm.draft))
        vm.removeMedia()
        XCTAssertNil(vm.draft.media)
        XCTAssertEqual(vm.state, .empty)
    }

    func testSetAudienceUpdatesDraftAndReach() {
        let vm = makeVM(audienceReach: [.allBeacons: 1247, .bronzePlus: 518])
        XCTAssertEqual(vm.draft.audience, .allBeacons)
        vm.setAudience(.bronzePlus)
        XCTAssertEqual(vm.draft.audience, .bronzePlus)
        XCTAssertEqual(vm.reach(for: .bronzePlus), 518)
    }

    func testScheduleAndSendNowToggleState() {
        let vm = makeVM()
        vm.updateBody("Loaf drop at 4")
        let date = Date(timeIntervalSince1970: 1_760_641_200)
        vm.schedule(at: date)
        XCTAssertEqual(vm.scheduledAt, date)
        XCTAssertEqual(vm.state, .scheduled(vm.draft, sendAt: date))
        vm.sendNow()
        XCTAssertNil(vm.scheduledAt)
        XCTAssertEqual(vm.state, .composing(vm.draft))
    }

    func testSaveDraftClearsUnsavedIndicator() {
        let vm = makeVM()
        vm.updateBody("draft")
        XCTAssertTrue(vm.isDirty)
        vm.saveDraft()
        XCTAssertFalse(vm.isDirty)
        vm.updateBody("draft and more")
        XCTAssertTrue(vm.isDirty)
    }

    func testSendPassesThroughSendingState() async {
        var capturedDuringSend: ComposeBroadcastState?
        let vm = makeVM()
        vm.performSend = { [weak vm] _, _ in capturedDuringSend = vm?.state }
        vm.updateBody("Going live")
        await vm.send()
        XCTAssertEqual(capturedDuringSend, .sending)
    }

    func testSendSuccessResetsComposerAndKeepsAudience() async {
        var sentCalled = false
        let vm = makeVM(onSent: { sentCalled = true })
        vm.setAudience(.silverPlus)
        vm.updateBody("Q&A recording is up")
        await vm.send()
        XCTAssertTrue(sentCalled)
        XCTAssertEqual(vm.state, .empty)
        XCTAssertEqual(vm.draft.audience, .silverPlus, "Audience persists as next-broadcast default")
        XCTAssertFalse(vm.isDirty)
    }

    func testSendFailureSurfacesErrorAndPreservesDraft() async {
        struct SendError: LocalizedError { var errorDescription: String? { "Network down" } }
        let vm = makeVM()
        vm.performSend = { _, _ in throw SendError() }
        vm.updateBody("keep me")
        await vm.send()
        XCTAssertEqual(vm.state, .error(message: "Network down"))
        XCTAssertEqual(vm.draft.body, "keep me")
        vm.retry()
        XCTAssertEqual(vm.state, .composing(ComposeBroadcastDraft(body: "keep me", audience: .allBeacons)))
    }

    func testEditingClearsPriorSendError() async {
        struct SendError: LocalizedError { var errorDescription: String? { "Oops" } }
        let vm = makeVM()
        vm.performSend = { _, _ in throw SendError() }
        vm.updateBody("first")
        await vm.send()
        XCTAssertEqual(vm.state, .error(message: "Oops"))
        vm.updateBody("first edited")
        XCTAssertEqual(vm.state, .composing(vm.draft))
    }

    func testPrimaryActionTitleReflectsFirstRun() {
        XCTAssertEqual(makeVM(recents: []).primaryActionTitle, "Send your first broadcast")
        XCTAssertEqual(
            makeVM(recents: ComposeBroadcastSampleData.recentBroadcasts).primaryActionTitle,
            "Send broadcast"
        )
    }

    func testSampleDataProvidesAtLeastThreeRecentBroadcastsWithStats() {
        let recents = ComposeBroadcastSampleData.recentBroadcasts
        XCTAssertGreaterThanOrEqual(recents.count, 3)
        for broadcast in recents {
            XCTAssertFalse(broadcast.reach.isEmpty)
            XCTAssertFalse(broadcast.read.isEmpty)
            XCTAssertFalse(broadcast.reactions.isEmpty)
            XCTAssertFalse(broadcast.replies.isEmpty)
        }
    }

    func testPreviewFactoriesMatchTheirStates() {
        XCTAssertEqual(ComposeBroadcastViewModel.previewEmpty().state, .empty)

        let populated = ComposeBroadcastViewModel.previewPopulated()
        XCTAssertEqual(populated.state, .composing(populated.draft))
        XCTAssertNotNil(populated.draft.media)

        let scheduled = ComposeBroadcastViewModel.previewScheduled()
        guard case .scheduled = scheduled.state else {
            return XCTFail("Expected .scheduled, got \(scheduled.state)")
        }
    }
}
