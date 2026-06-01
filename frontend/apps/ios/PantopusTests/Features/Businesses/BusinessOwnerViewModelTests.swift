//
//  BusinessOwnerViewModelTests.swift
//  PantopusTests
//
//  A10.7 — owner-dashboard view-model coverage. B3.2 is sample-driven
//  (no analytics / review-reply backend), so the suite pins the loaded
//  projection, the injected-content path used by snapshots, and the
//  local-state reply stub (`submitReply` → `applyingReply`).
//

import XCTest
@testable import Pantopus

@MainActor
final class BusinessOwnerViewModelTests: XCTestCase {
    func test_load_withInjectedContent_emitsLoaded() async {
        let viewModel = BusinessOwnerViewModel(
            businessId: "marlow",
            content: BusinessOwnerSampleData.marlow
        )
        await viewModel.load()
        guard case let .loaded(content) = viewModel.state else {
            return XCTFail("Expected loaded state")
        }
        XCTAssertEqual(content.businessId, "marlow")
        XCTAssertTrue(content.isLive)
        XCTAssertEqual(content.insights.count, 3)
        XCTAssertEqual(content.profileStrength.percent, 92)
        // The preview render is the A10.6 populated sample.
        XCTAssertEqual(content.publicProfile.header.displayName, "Marlow & Co. Cleaning")
    }

    func test_load_default_derivesSampleContent() async {
        let viewModel = BusinessOwnerViewModel(businessId: "marlow")
        await viewModel.load()
        guard case .loaded = viewModel.state else {
            return XCTFail("Expected loaded state")
        }
    }

    func test_submitReply_setsReplyOnReview() async {
        let viewModel = BusinessOwnerViewModel(
            businessId: "marlow",
            content: BusinessOwnerSampleData.marlow
        )
        await viewModel.load()
        viewModel.submitReply(reviewId: "dana", text: "Thanks for the feedback, Dana!")
        guard case let .loaded(content) = viewModel.state else {
            return XCTFail("Expected loaded state")
        }
        XCTAssertEqual(
            content.reviews.first { $0.id == "dana" }?.reply,
            "Thanks for the feedback, Dana!"
        )
    }

    func test_submitReply_blankText_isIgnored() async {
        let viewModel = BusinessOwnerViewModel(
            businessId: "marlow",
            content: BusinessOwnerSampleData.marlow
        )
        await viewModel.load()
        viewModel.submitReply(reviewId: "dana", text: "   \n ")
        guard case let .loaded(content) = viewModel.state else {
            return XCTFail("Expected loaded state")
        }
        XCTAssertNil(content.reviews.first { $0.id == "dana" }?.reply)
    }

    func test_applyingReply_recomputesPendingReplyLabel() {
        let base = BusinessOwnerSampleData.marlow
        XCTAssertEqual(base.reviewsToReplyLabel, "2 to reply")
        // Dana is the only unanswered review shown; answering it clears the
        // pending-reply affordance.
        let updated = base.applyingReply("Appreciate it", to: "dana")
        XCTAssertEqual(updated.reviews.first { $0.id == "dana" }?.reply, "Appreciate it")
        XCTAssertNil(updated.reviewsToReplyLabel)
    }
}
