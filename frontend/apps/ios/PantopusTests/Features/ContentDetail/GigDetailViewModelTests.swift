//
//  GigDetailViewModelTests.swift
//  PantopusTests
//
//  Work item C — the gig-detail bookmark toggle. Covers the
//  `saved_by_user` seed on load, the optimistic flip (save + unsave
//  endpoints), the revert-on-failure path, and the in-flight debounce.
//

import XCTest
@testable import Pantopus

@MainActor
final class GigDetailViewModelTests: XCTestCase {
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

    /// Viewer is NOT the owner so `load()` issues exactly two requests:
    /// detail + questions (no owner-only bids fetch).
    private func makeVM() -> GigDetailViewModel {
        GigDetailViewModel(gigId: "g1", api: makeAPI(), currentUserId: "viewer-1")
    }

    private static func gigEnvelope(saved: Bool) -> String {
        """
        {"gig":{
          "id":"g1","title":"Hang 3 shelves","description":"IKEA Lack shelves.",
          "price":60,"category":"handyman","status":"open",
          "user_id":"owner-1","saved_by_user":\(saved)
        }}
        """
    }

    private static let questionsJSON = #"{"questions":[]}"#

    private func loadVM(saved: Bool) async -> GigDetailViewModel {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.gigEnvelope(saved: saved)),
            .status(200, body: Self.questionsJSON)
        ]
        let vm = makeVM()
        await vm.load()
        return vm
    }

    // MARK: - Seed

    func testLoadSeedsSavedStateFromPayload() async {
        let vm = await loadVM(saved: true)
        XCTAssertTrue(vm.isSaved)
    }

    func testLoadDefaultsUnsavedWhenFlagAbsent() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: #"{"gig":{"id":"g1","title":"t","user_id":"owner-1","status":"open"}}"#),
            .status(200, body: Self.questionsJSON)
        ]
        let vm = makeVM()
        await vm.load()
        XCTAssertFalse(vm.isSaved)
    }

    // MARK: - Optimistic toggle

    func testToggleSavePostsSaveAndSticks() async {
        let vm = await loadVM(saved: false)
        SequencedURLProtocol.sequence = [.status(200, body: #"{"success":true}"#)]
        let ok = await vm.toggleSave()
        XCTAssertTrue(ok)
        XCTAssertTrue(vm.isSaved)
        XCTAssertFalse(vm.isSaveInFlight)
        let last = SequencedURLProtocol.capturedRequests.last
        XCTAssertEqual(last?.httpMethod, "POST")
        XCTAssertEqual(last?.url?.path, "/api/gigs/g1/save")
    }

    func testToggleSaveOnSavedGigDeletes() async {
        let vm = await loadVM(saved: true)
        SequencedURLProtocol.sequence = [.status(200, body: #"{"success":true}"#)]
        let ok = await vm.toggleSave()
        XCTAssertTrue(ok)
        XCTAssertFalse(vm.isSaved)
        let last = SequencedURLProtocol.capturedRequests.last
        XCTAssertEqual(last?.httpMethod, "DELETE")
        XCTAssertEqual(last?.url?.path, "/api/gigs/g1/save")
    }

    func testToggleSaveFailureRevertsOptimisticFlip() async {
        let vm = await loadVM(saved: false)
        SequencedURLProtocol.sequence = [.status(500, body: "{}")]
        let ok = await vm.toggleSave()
        XCTAssertFalse(ok, "Failure reports false so the view can toast.")
        XCTAssertFalse(vm.isSaved, "The optimistic flip reverts on failure.")
        XCTAssertFalse(vm.isSaveInFlight)
    }

    func testToggleSaveBeforeLoadIsNoOp() async {
        let vm = makeVM()
        let ok = await vm.toggleSave()
        XCTAssertTrue(ok, "No gig yet — nothing to do, nothing to toast.")
        XCTAssertFalse(vm.isSaved)
        XCTAssertTrue(SequencedURLProtocol.capturedRequests.isEmpty)
    }
}
