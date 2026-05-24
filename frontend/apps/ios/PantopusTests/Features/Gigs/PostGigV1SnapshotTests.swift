//
//  PostGigV1SnapshotTests.swift
//  PantopusTests
//
//  A13.8 — structural snapshot coverage for the V1 single-screen gig form.
//  Mirrors the design's filled-ready and validation-errors-on-post frames.
//

import SwiftUI
import XCTest
@testable import Pantopus

@MainActor
final class PostGigV1SnapshotTests: XCTestCase {
    func test_post_gig_v1_filled_ready_frame_renders() {
        let vm = PostGigV1SampleData.filledViewModel()
        XCTAssertTrue(vm.state.validationErrors.isEmpty)
        assertRenders(PostGigV1View(viewModel: vm))
    }

    func test_post_gig_v1_validation_errors_frame_renders() {
        let vm = PostGigV1SampleData.validationErrorViewModel()
        XCTAssertEqual(vm.state.validationErrors.count, 3)
        assertRenders(PostGigV1View(viewModel: vm))
    }

    func test_post_gig_v1_round_trip_validation_then_success() {
        let vm = PostGigV1ViewModel(
            initialState: PostGigV1State(form: PostGigV1SampleData.validationErrorForm),
            referenceNow: PostGigV1SampleData.referenceNow
        ) {
            "gig-v1-sofa-move"
        }

        XCTAssertNil(vm.submit())
        XCTAssertEqual(vm.state.validationErrors.map(\.field), [.description, .price, .dateTime])

        vm.updateDescription(PostGigV1SampleData.filledForm.description)
        vm.updatePrice("80")
        vm.updateScheduledAt(PostGigV1SampleData.filledForm.scheduledAt)

        XCTAssertEqual(vm.submit(), "gig-v1-sofa-move")
        XCTAssertTrue(vm.state.validationErrors.isEmpty)
        XCTAssertEqual(vm.state.postedGigId, "gig-v1-sofa-move")
    }

    private func assertRenders(
        _ view: some View,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let host = UIHostingController(rootView: view.frame(width: 390, height: 900))
        host.view.frame = CGRect(x: 0, y: 0, width: 390, height: 900)
        host.view.layoutIfNeeded()
        XCTAssertGreaterThan(host.view.frame.size.width, 0, file: file, line: line)
        XCTAssertGreaterThan(host.view.frame.size.height, 0, file: file, line: line)
    }
}
