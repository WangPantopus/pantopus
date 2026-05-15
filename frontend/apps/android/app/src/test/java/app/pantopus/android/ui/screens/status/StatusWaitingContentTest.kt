@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors iOS [StatusWaitingContentTests]. Pins the slot output of
 * the three preset factories so design tweaks don't silently drop a
 * required slot.
 */
class StatusWaitingContentTest {
    // MARK: - Frame 1: claim submitted

    @Test fun claim_submitted_fills_all_required_slots() {
        val content = StatusWaitingContent.claimSubmitted(homeName = "412 Elm St")
        assertEquals(StatusIllustration.Success, content.illustration)
        assertEquals("Claim submitted", content.headline)
        assertTrue(content.subcopy.contains("412 Elm St"))
        assertEquals(3, content.timeline.size)
        assertEquals("submitted", content.currentStageId)
        assertEquals("2–3 days", content.etaChip)
        assertEquals(2, content.actionCards.size)
        assertEquals(listOf("checkInbox", "viewClaim"), content.actionCards.map { it.id })
        assertEquals(3, content.explainerBullets.size)
        assertEquals("back_to_hub", content.primaryCta?.actionKey)
        assertEquals("view_claim", content.secondaryCta?.actionKey)
    }

    @Test fun claim_submitted_without_home_name_falls_back() {
        val content = StatusWaitingContent.claimSubmitted(homeName = null)
        assertTrue(content.subcopy.contains("this home"))
    }

    @Test fun claim_submitted_respects_custom_eta() {
        val content = StatusWaitingContent.claimSubmitted(homeName = "test", eta = "by Friday")
        assertEquals("by Friday", content.etaChip)
    }

    // MARK: - Frame 2: under review

    @Test fun under_review_fills_all_required_slots() {
        val content = StatusWaitingContent.underReview(homeName = "412 Elm St", submittedAgo = "2 days ago")
        assertEquals(StatusIllustration.Waiting, content.illustration)
        assertEquals("Under review", content.headline)
        assertTrue(content.subcopy.contains("412 Elm St"))
        assertTrue(content.subcopy.contains("2 days ago"))
        assertEquals(3, content.timeline.size)
        assertEquals("review", content.currentStageId)
        assertNotNull(content.etaChip)
        assertEquals(2, content.actionCards.size)
        assertEquals(listOf("addEvidence", "contactSupport"), content.actionCards.map { it.id })
        assertEquals(3, content.explainerBullets.size)
    }

    @Test fun under_review_without_submitted_ago_omits_clause() {
        val content = StatusWaitingContent.underReview(homeName = "412 Elm St", submittedAgo = null)
        assertTrue(!content.subcopy.contains("Submitted "))
    }

    // MARK: - Frame 3: check your email

    @Test fun check_your_email_fills_all_required_slots() {
        val content = StatusWaitingContent.checkYourEmail(email = "alice@example.com")
        assertEquals(StatusIllustration.Email, content.illustration)
        assertEquals("Check your email", content.headline)
        assertTrue(content.subcopy.contains("alice@example.com"))
        assertTrue(content.timeline.isEmpty())
        assertNull(content.currentStageId)
        assertNotNull(content.etaChip)
        assertEquals(2, content.actionCards.size)
        assertEquals(listOf("openMail", "resendEmail"), content.actionCards.map { it.id })
        assertEquals("resend_email", content.primaryCta?.actionKey)
        assertEquals("change_email", content.secondaryCta?.actionKey)
    }

    @Test fun check_your_email_without_email_falls_back() {
        val content = StatusWaitingContent.checkYourEmail(email = null)
        assertTrue(content.subcopy.contains("verification link"))
        assertTrue(!content.subcopy.contains("@"))
    }

    // MARK: - Cross-frame invariants

    @Test fun all_frames_include_primary_cta() {
        val frames =
            listOf(
                StatusWaitingContent.claimSubmitted(homeName = null),
                StatusWaitingContent.underReview(homeName = null),
                StatusWaitingContent.checkYourEmail(email = null),
            )
        frames.forEach { frame ->
            assertNotNull("Frame ${frame.illustration} needs a primary CTA", frame.primaryCta)
        }
    }

    @Test fun all_frames_include_explainer_bullets() {
        val frames =
            listOf(
                StatusWaitingContent.claimSubmitted(homeName = null),
                StatusWaitingContent.underReview(homeName = null),
                StatusWaitingContent.checkYourEmail(email = null),
            )
        frames.forEach { frame ->
            assertTrue(
                "Frame ${frame.illustration} needs explainer bullets",
                frame.explainerBullets.isNotEmpty(),
            )
        }
    }

    @Test fun homes_claim_timeline_has_three_stages() {
        val stages = StatusWaitingContent.homesClaimTimeline
        assertEquals(listOf("submitted", "review", "complete"), stages.map { it.id })
    }

    @Test fun timeline_current_stage_differs_across_claim_and_review() {
        assertEquals("submitted", StatusWaitingContent.claimSubmitted(homeName = null).currentStageId)
        assertEquals("review", StatusWaitingContent.underReview(homeName = null).currentStageId)
    }
}
