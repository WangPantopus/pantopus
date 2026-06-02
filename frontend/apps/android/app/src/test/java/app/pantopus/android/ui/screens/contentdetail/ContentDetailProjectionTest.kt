@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.contentdetail

import app.pantopus.android.data.api.models.gigs.GigBidDto
import app.pantopus.android.data.api.models.gigs.GigCreator
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.models.listings.ListingDto
import app.pantopus.android.ui.screens.gigs.GigsCategory
import app.pantopus.android.ui.theme.PantopusIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the T2.6 / A09 ContentDetail projection (Android). Mirrors the
 * iOS suite — gig V2 (populated + no-bids), gig V1 (populated + awarded),
 * listing (populated + sold), invoice (due + paid), plus sample-frame
 * signature checks.
 */
class ContentDetailProjectionTest {
    @Test fun task_v2_projection_fills_status_hero_bids_and_two_stop() {
        val gig =
            baseGig.copy(
                title = "Move queen mattress + frame",
                price = 85.0,
                category = "moving",
                status = "open",
                isV2 = true,
                pickupAddress = "712 Maplewood, Apt 2B",
                dropoffAddress = "209 Cedar Ave, Apt 7",
                bidCount = 6,
                distanceMiles = 0.6,
            )
        val bids = (1..6).map { bid("b$it", "u$it", (50 + it * 5).toDouble(), "Bidder $it") }
        val content = GigDetailViewModel.Projection.project(gig, bids)
        assertEquals(ContentDetailKind.Gig, content.kind)
        assertEquals("Open · 6 bids", content.statusPill?.label)
        assertEquals(GigsCategory.Moving, content.hero.categoryChip?.category)
        assertEquals("Place bid", content.dock.primary.label)
        assertTrue(content.dock.primary.enabled)
        val twoStop = content.modules.filterIsInstance<ContentDetailModule.TwoStop>().firstOrNull()
        assertNotNull(twoStop)
        assertEquals(2, twoStop!!.stops.size)
        assertEquals(ContentDetailModule.TwoStop.StopTone.Primary, twoStop.stops.first().tone)
        assertEquals(ContentDetailModule.TwoStop.StopTone.Success, twoStop.stops.last().tone)
    }

    @Test fun task_v2_no_bids_renders_be_first_callout() {
        val gig =
            baseGig.copy(title = "Move queen mattress", price = 85.0, category = "moving", status = "open", isV2 = true)
        val content = GigDetailViewModel.Projection.project(gig, emptyList())
        assertEquals("Open · No bids yet", content.statusPill?.label)
        assertNull(content.modules.filterIsInstance<ContentDetailModule.Bids>().firstOrNull())
        val callout = content.modules.filterIsInstance<ContentDetailModule.Callout>().firstOrNull()
        assertEquals(ContentDetailModule.Callout.Style.Empty, callout?.style)
        assertEquals("Be the first to bid", callout?.title)
    }

    @Test fun task_v2_worker_in_progress_shows_deliver_dock() {
        val gig =
            baseGig.copy(
                title = "Move a mattress",
                price = 85.0,
                category = "moving",
                status = "in_progress",
                isV2 = true,
                acceptedBy = "me",
                bidCount = 1,
            )
        val content = GigDetailViewModel.Projection.project(gig, emptyList(), canMarkDelivered = true)
        assertEquals("In progress", content.statusPill?.label)
        assertEquals(ContentDetailPill.Tone.Warning, content.statusPill?.tone)
        assertEquals("Mark as delivered", content.dock.primary.label)
        assertEquals(PantopusIcon.CheckCheck, content.dock.primary.icon)
        assertTrue(content.dock.primary.enabled)
        assertEquals("Message", content.dock.secondary?.label)
    }

    @Test fun task_v2_without_worker_keeps_bid_dock() {
        val gig =
            baseGig.copy(
                title = "Move a mattress",
                price = 85.0,
                category = "moving",
                status = "in_progress",
                isV2 = true,
                acceptedBy = "me",
                bidCount = 1,
            )
        // canMarkDelivered defaults false — the bidder dock holds.
        val content = GigDetailViewModel.Projection.project(gig, emptyList())
        assertEquals("Place bid", content.dock.primary.label)
    }

    @Test fun viewer_can_mark_delivered_gate() {
        val inProgress = baseGig.copy(status = "in_progress", isV2 = true, acceptedBy = "me")
        assertTrue(GigDetailViewModel.viewerCanMarkDelivered(inProgress, "me"))
        assertFalse(GigDetailViewModel.viewerCanMarkDelivered(inProgress, "someone-else"))
        assertFalse(GigDetailViewModel.viewerCanMarkDelivered(inProgress, null))
        // Assigned-but-not-started tasks are not yet completable.
        val assigned = baseGig.copy(status = "assigned", isV2 = true, acceptedBy = "me")
        assertFalse(GigDetailViewModel.viewerCanMarkDelivered(assigned, "me"))
    }

    @Test fun gig_v1_projection_is_sparse() {
        val gig =
            baseGig.copy(
                title = "Dog walk · 45 min",
                description = "Walk Biscuit.",
                price = 22.0,
                category = "petcare",
                status = "open",
                isV2 = false,
                bidCount = 3,
            )
        val bids = (1..3).map { bid("b$it", "u$it", (18 + it * 2).toDouble(), "Bidder $it") }
        val content = GigDetailViewModel.Projection.project(gig, bids)
        assertEquals("Open", content.statusPill?.label)
        assertNull(content.hero.categoryChip)
        assertTrue(content.statStrip.isEmpty())
        assertTrue(content.trustCapsules.isEmpty())
        assertTrue(content.dock.primary.enabled)
        assertEquals("Place bid", content.dock.primary.label)
    }

    @Test fun gig_v1_awarded_dims_losers_and_locks_dock() {
        val gig =
            baseGig.copy(
                title = "Dog walk · 45 min",
                price = 22.0,
                category = "petcare",
                status = "accepted",
                isV2 = false,
                acceptedBy = "u1",
                acceptedAt = "2025-11-14T17:30:00Z",
                bidCount = 3,
            )
        val bids =
            listOf(
                bid("b1", "u1", 20.0, "Tomás G."),
                bid("b2", "u2", 22.0, "Rae N."),
                bid("b3", "u3", 25.0, "Carla W."),
            )
        val content = GigDetailViewModel.Projection.project(gig, bids)
        assertEquals("Awarded", content.statusPill?.label)
        assertEquals(ContentDetailPill.Tone.Success, content.statusPill?.tone)
        assertEquals("winning bid", content.hero.priceCaption)
        assertTrue(content.modules.any { it is ContentDetailModule.Callout && it.id == "awarded" })
        val bidsModule = content.modules.filterIsInstance<ContentDetailModule.Bids>().firstOrNull()
        assertEquals("closed", bidsModule?.sub)
        assertTrue(bidsModule!!.bids.first().won)
        assertTrue(bidsModule.bids.drop(1).all { it.dimmed })
        assertFalse(content.dock.primary.enabled)
        assertEquals("Bidding closed", content.dock.primary.label)
    }

    @Test fun listing_projection_carries_cover_inline_pills_and_offer_dock() {
        val listing =
            baseListing.copy(
                title = "Mid-century sofa",
                price = 320.0,
                condition = "like_new",
                locationName = "West Adams",
                distanceMeters = 644.0,
            )
        val content = ListingDetailViewModel.Projection.project(listing)
        assertEquals(ContentDetailKind.Listing, content.kind)
        assertNotNull(content.cover)
        assertFalse(content.cover!!.sold)
        assertEquals(listOf(PantopusIcon.Share, PantopusIcon.Bookmark), content.cover.glassActions)
        assertEquals("$320", content.hero.priceLine)
        assertFalse(content.hero.priceStrikethrough)
        assertTrue(content.hero.inlinePills.any { it.label == "Like new" })
        assertEquals("Make offer", content.dock.primary.label)
    }

    @Test fun listing_sold_desaturates_strikes_price_and_pivots_dock() {
        val listing =
            baseListing.copy(title = "Bianchi", price = 410.0, condition = "good", status = "sold", soldAt = "2025-12-14T10:00:00Z")
        val content = ListingDetailViewModel.Projection.project(listing)
        assertTrue(content.cover!!.sold)
        assertEquals("Sold", content.statusPill?.label)
        assertEquals(ContentDetailPill.Tone.Error, content.statusPill?.tone)
        assertTrue(content.hero.priceStrikethrough)
        assertEquals("Seller", content.dock.secondary?.label)
        assertEquals("Find similar", content.dock.primary.label)
        assertTrue(content.modules.any { it is ContentDetailModule.Callout && it.id == "alert-similar" })
    }

    @Test fun listing_free_renders_free_price() {
        val listing =
            baseListing.copy(title = "Moving boxes", price = 0.0, isFree = true, category = "free_stuff", layer = "goods")
        val content = ListingDetailViewModel.Projection.project(listing)
        assertEquals("Free", content.hero.priceLine)
        assertTrue(content.hero.inlinePills.any { it.label == "Free" })
    }

    @Test fun invoice_due_carries_total_hero_line_items_and_pay_dock() {
        val content = InvoiceDetailViewModel.Projection.fixture("INV-00318")
        assertEquals(ContentDetailKind.Invoice, content.kind)
        assertEquals("Due in 7 days", content.statusPill?.label)
        assertEquals("$642.85", content.hero.priceLine)
        assertEquals("total · USD", content.hero.priceCaption)
        assertFalse(content.hero.priceCheckDisc)
        assertTrue(content.hero.monoId?.contains("INV-00318") == true)
        val items = content.modules.filterIsInstance<ContentDetailModule.LineItems>().firstOrNull()
        assertEquals(3, items?.fees?.size)
        assertEquals("$642.85", items?.totalValue)
        assertEquals(ContentDetailModule.LineItems.TotalTone.Primary, items?.totalTone)
        assertTrue(content.modules.any { it is ContentDetailModule.FromTo })
        assertNull(content.dock.secondary)
        assertTrue(content.dock.primary.label.contains("Pay"))
    }

    @Test fun invoice_paid_recolors_total_adds_receipt_and_pivots_dock() {
        val content = InvoiceDetailViewModel.Projection.paidFixture("INV-00318")
        assertEquals("Paid · Dec 14", content.statusPill?.label)
        assertEquals(ContentDetailPill.Tone.Success, content.statusPill?.tone)
        assertEquals(ContentDetailHero.PriceTone.Success, content.hero.priceTone)
        assertTrue(content.hero.priceCheckDisc)
        assertEquals("paid in full", content.hero.priceTrailingLabel)
        assertTrue(content.modules.any { it is ContentDetailModule.Callout && it.id == "pantopus-pay-receipt" })
        val items = content.modules.filterIsInstance<ContentDetailModule.LineItems>().firstOrNull()
        assertEquals("Paid", items?.totalLabel)
        assertEquals(ContentDetailModule.LineItems.TotalTone.Success, items?.totalTone)
        assertEquals("Share", content.dock.secondary?.label)
        assertEquals("Download receipt", content.dock.primary.label)
    }

    @Test fun sample_frames_carry_signature_elements() {
        val v2 = GigDetailSampleData.taskV2Populated
        assertEquals("Open · 6 bids", v2.statusPill?.label)
        assertTrue(v2.modules.any { it is ContentDetailModule.TwoStop })
        assertTrue(v2.modules.any { it is ContentDetailModule.PhotoStrip })
        val v2Bids = v2.modules.filterIsInstance<ContentDetailModule.Bids>().first()
        assertEquals("fastest reply", v2Bids.bids.first().tag)
        val trustRow = v2.modules.filterIsInstance<ContentDetailModule.CapsuleRow>().firstOrNull()
        assertTrue(trustRow!!.capsules.any { it.label == "5.0★ rating" })

        assertFalse(GigDetailSampleData.gigV1Awarded.dock.primary.enabled)
        assertTrue(ListingDetailSampleData.sold.cover!!.sold)
        assertEquals("Sold for $385", ListingDetailSampleData.sold.hero.saleTag)
    }

    // Fixtures — base DTOs that each test customises with `.copy(...)`,
    // keeping the helpers free of long parameter lists.

    private val baseGig =
        GigDto(
            id = "g1",
            title = "Task",
            price = null,
            category = null,
            status = "open",
            userId = "owner",
            isV2 = false,
            bidCount = 0,
            creator = GigCreator(id = "owner", username = "hana", name = "Hana O.", verified = true),
        )

    private val baseListing =
        ListingDto(
            id = "l1",
            userId = "u1",
            title = "Listing",
            description = "Sample description.",
            price = null,
            isFree = false,
            category = "furniture",
            condition = null,
            status = "active",
            mediaUrls = listOf("https://example.com/a.jpg", "https://example.com/b.jpg"),
            firstImage = "https://example.com/a.jpg",
            layer = "goods",
            listingType = "sell_item",
            locationName = null,
            distanceMeters = null,
            soldAt = null,
        )

    private fun bid(
        id: String,
        userId: String,
        amount: Double,
        name: String,
    ): GigBidDto =
        GigBidDto(
            id = id,
            userId = userId,
            bidAmount = amount,
            status = "pending",
            bidder = GigCreator(id = userId, username = userId, name = name, verified = true),
        )
}
