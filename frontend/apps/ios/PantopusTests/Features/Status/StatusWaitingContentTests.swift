//
//  StatusWaitingContentTests.swift
//  PantopusTests
//
//  T3.6 Status / Waiting is presentational — the testable surface is
//  the three preset factories on `StatusWaitingContent`. These tests
//  pin the slot output for each design frame.
//

import XCTest
@testable import Pantopus

final class StatusWaitingContentTests: XCTestCase {
    // MARK: - Frame 1: claim submitted

    func testClaimSubmittedFillsAllRequiredSlots() {
        let content = StatusWaitingContent.claimSubmitted(homeName: "412 Elm St")
        XCTAssertEqual(content.illustration, .success)
        XCTAssertEqual(content.headline, "Claim submitted")
        XCTAssertTrue(content.subcopy.contains("412 Elm St"))
        XCTAssertEqual(content.timeline.count, 3)
        XCTAssertEqual(content.currentStageId, "submitted")
        XCTAssertEqual(content.etaChip, "2–3 days")
        XCTAssertEqual(content.actionCards.count, 2)
        XCTAssertEqual(content.actionCards.map(\.id), ["checkInbox", "viewClaim"])
        XCTAssertEqual(content.explainerBullets.count, 3)
        XCTAssertEqual(content.primaryCta?.actionKey, "back_to_hub")
        XCTAssertEqual(content.secondaryCta?.actionKey, "view_claim")
    }

    func testClaimSubmittedWithoutHomeNameFallsBack() {
        let content = StatusWaitingContent.claimSubmitted(homeName: nil)
        XCTAssertTrue(content.subcopy.contains("this home"))
    }

    func testClaimSubmittedRespectsCustomEta() {
        let content = StatusWaitingContent.claimSubmitted(homeName: "test", eta: "by Friday")
        XCTAssertEqual(content.etaChip, "by Friday")
    }

    // MARK: - Frame 2: under review

    func testUnderReviewFillsAllRequiredSlots() {
        let content = StatusWaitingContent.underReview(homeName: "412 Elm St", submittedAgo: "2 days ago")
        XCTAssertEqual(content.illustration, .waiting)
        XCTAssertEqual(content.headline, "Under review")
        XCTAssertTrue(content.subcopy.contains("412 Elm St"))
        XCTAssertTrue(content.subcopy.contains("2 days ago"))
        XCTAssertEqual(content.timeline.count, 3)
        XCTAssertEqual(content.currentStageId, "review")
        XCTAssertNotNil(content.etaChip)
        XCTAssertEqual(content.actionCards.count, 2)
        XCTAssertEqual(content.actionCards.map(\.id), ["addEvidence", "contactSupport"])
        XCTAssertEqual(content.explainerBullets.count, 3)
    }

    func testUnderReviewWithoutSubmittedAgoOmitsClause() {
        let content = StatusWaitingContent.underReview(homeName: "412 Elm St", submittedAgo: nil)
        XCTAssertFalse(content.subcopy.contains("Submitted "))
    }

    // MARK: - Frame 3: check your email

    func testCheckYourEmailFillsAllRequiredSlots() {
        let content = StatusWaitingContent.checkYourEmail(email: "alice@example.com")
        XCTAssertEqual(content.illustration, .email)
        XCTAssertEqual(content.headline, "Check your email")
        XCTAssertTrue(content.subcopy.contains("alice@example.com"))
        XCTAssertTrue(content.timeline.isEmpty)
        XCTAssertNotNil(content.etaChip)
        XCTAssertEqual(content.actionCards.count, 2)
        XCTAssertEqual(content.actionCards.map(\.id), ["openMail", "resendEmail"])
        XCTAssertEqual(content.primaryCta?.actionKey, "resend_email")
        XCTAssertEqual(content.secondaryCta?.actionKey, "change_email")
    }

    func testCheckYourEmailWithoutEmailFallsBack() {
        let content = StatusWaitingContent.checkYourEmail(email: nil)
        XCTAssertTrue(content.subcopy.contains("verification link"))
        XCTAssertFalse(content.subcopy.contains("@"))
    }

    // MARK: - Cross-frame invariants

    func testAllFramesIncludePrimaryCta() {
        let frames: [StatusWaitingContent] = [
            .claimSubmitted(homeName: nil),
            .underReview(homeName: nil),
            .checkYourEmail(email: nil)
        ]
        for frame in frames {
            XCTAssertNotNil(frame.primaryCta, "Frame \(frame.illustration) needs a primary CTA")
        }
    }

    func testAllFramesIncludeExplainerBullets() {
        let frames: [StatusWaitingContent] = [
            .claimSubmitted(homeName: nil),
            .underReview(homeName: nil),
            .checkYourEmail(email: nil)
        ]
        for frame in frames {
            XCTAssertFalse(
                frame.explainerBullets.isEmpty,
                "Frame \(frame.illustration) needs explainer bullets"
            )
        }
    }

    func testHomesClaimTimelineHasThreeStages() {
        let stages = StatusWaitingContent.homesClaimTimeline
        XCTAssertEqual(stages.map(\.id), ["submitted", "review", "complete"])
    }

    func testTimelineCurrentStageDiffersAcrossClaimAndReview() {
        let submitted = StatusWaitingContent.claimSubmitted(homeName: nil)
        let review = StatusWaitingContent.underReview(homeName: nil)
        XCTAssertEqual(submitted.currentStageId, "submitted")
        XCTAssertEqual(review.currentStageId, "review")
    }
}
