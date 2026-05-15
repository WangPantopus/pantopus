@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.contentdetail

import app.pantopus.android.data.api.models.gigs.GigBidDto
import app.pantopus.android.data.api.models.gigs.GigCreator
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.models.listings.ListingDto
import app.pantopus.android.ui.screens.gigs.GigsCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the T2.6 ContentDetail projection (Android). Mirrors the iOS
 * suite — gig with bids, gig without bids hides the bids module,
 * listing with cover + condition trust, free listing renders "Free",
 * and invoice fixture carries from/to + line items + summary + full-
 * width Pay dock.
 */
class ContentDetailProjectionTest {
    @Test fun gig_projection_fills_status_hero_and_bids() {
        val gig =
            GigDto(
                id = "g1",
                title = "Hang 3 shelves",
                description = "Three IKEA Lack shelves on drywall.",
                price = 60.0,
                category = "handyman",
                status = "open",
                userId = "u1",
                pickupAddress = "Rose Court, Unit 4B",
                bidCount = 4,
                distanceMiles = 0.2,
            )
        val bids =
            (1..4).map { i ->
                GigBidDto(
                    id = "b$i",
                    userId = "u$i",
                    bidAmount = (50 + i * 5).toDouble(),
                    status = "pending",
                    bidder = GigCreator(id = "u$i", username = "u$i", name = "Bidder $i", verified = true),
                )
            }
        val content = GigDetailViewModel.Projection.project(gig, bids)
        assertEquals(ContentDetailKind.Gig, content.kind)
        assertEquals("Open · 4 bids", content.statusPill?.label)
        assertEquals("Hang 3 shelves", content.hero.title)
        assertEquals("$60", content.hero.priceLine)
        assertEquals("budget", content.hero.priceCaption)
        assertEquals(GigsCategory.Handyman, content.hero.categoryChip?.category)
        assertTrue(content.trustCapsules.isNotEmpty())
        assertEquals("Message", content.dock.secondary?.label)
        assertEquals("Place bid", content.dock.primary.label)
        val bidsModule = content.modules.filterIsInstance<ContentDetailModule.Bids>().firstOrNull()
        assertNotNull(bidsModule)
        assertEquals(4, bidsModule!!.bids.size)
        assertEquals("$55", bidsModule.bids.first().amount)
    }

    @Test fun gig_projection_zero_bids_hides_bid_module() {
        val gig =
            GigDto(
                id = "g2",
                title = "Walk Lily Tue/Thu",
                description = null,
                price = 22.0,
                category = "petcare",
                status = "open",
                userId = "u1",
                bidCount = 0,
                payType = "per_walk",
            )
        val content = GigDetailViewModel.Projection.project(gig, emptyList())
        assertEquals("Open", content.statusPill?.label)
        assertEquals("$22 / walk", content.hero.priceLine)
        val bidsModule = content.modules.filterIsInstance<ContentDetailModule.Bids>().firstOrNull()
        assertNull(bidsModule)
    }

    @Test fun listing_projection_carries_cover_and_condition_trust() {
        val listing =
            ListingDto(
                id = "l1",
                userId = "u1",
                title = "Mid-century sofa",
                description = "Walnut frame, original cushions.",
                price = 320.0,
                isFree = false,
                category = "furniture",
                condition = "like_new",
                status = "active",
                mediaUrls = listOf("https://example.com/sofa.jpg"),
                firstImage = "https://example.com/sofa.jpg",
                layer = "goods",
                listingType = "sell_item",
                locationName = "West Adams",
                distanceMeters = 644.0,
            )
        val content = ListingDetailViewModel.Projection.project(listing)
        assertEquals(ContentDetailKind.Listing, content.kind)
        assertNotNull(content.cover)
        assertEquals("$320", content.hero.priceLine)
        assertNotNull(content.counterparty)
        assertTrue(content.trustCapsules.any { it.label == "Like new" })
        assertEquals("Make offer", content.dock.primary.label)
    }

    @Test fun listing_projection_free_renders_free_price() {
        val listing =
            ListingDto(
                id = "l2",
                userId = "u2",
                title = "Moving boxes",
                price = 0.0,
                isFree = true,
                category = "free_stuff",
                status = "active",
                layer = "goods",
                listingType = "free_item",
            )
        val content = ListingDetailViewModel.Projection.project(listing)
        assertEquals("Free", content.hero.priceLine)
        assertTrue(content.trustCapsules.any { it.label == "Free" })
    }

    @Test fun invoice_fixture_carries_from_to_line_items_and_full_width_dock() {
        val content = InvoiceDetailViewModel.Projection.fixture("INV-00247")
        assertEquals(ContentDetailKind.Invoice, content.kind)
        assertEquals("Due in 3 days", content.statusPill?.label)
        assertEquals("Bathroom retile", content.hero.title)
        assertTrue(content.hero.monoId?.contains("INV-00247") == true)
        assertTrue(content.modules.any { it is ContentDetailModule.FromTo })
        assertTrue(content.modules.any { it is ContentDetailModule.LineItems })
        assertTrue(content.modules.any { it is ContentDetailModule.Summary })
        assertNull(content.dock.secondary)
        assertTrue(content.dock.primary.label.contains("Pay"))
    }
}
