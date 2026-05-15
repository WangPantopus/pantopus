@file:Suppress("MagicNumber")

package app.pantopus.android.ui.screens.contentdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.data.api.models.gigs.GigBidDto
import app.pantopus.android.data.api.models.gigs.GigCreator
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.models.listings.ListingDto
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Rule
import org.junit.Test

/**
 * Paparazzi baselines for the T2.6 ContentDetail shell. Three frames
 * proving the same canvas renders gig / listing / invoice from
 * different projected payloads.
 */
class ContentDetailShellSnapshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 2400,
                    softButtons = false,
                ),
        )

    @Test
    fun content_detail_gig_with_bids() {
        paparazzi.snapshot {
            Frame {
                ContentDetailShell(
                    state = ContentDetailUiState.Loaded(gigContent()),
                    onBack = {},
                    onPrimaryAction = {},
                    onSecondaryAction = {},
                    onMessageCounterparty = null,
                )
            }
        }
    }

    @Test
    fun content_detail_listing_with_cover_and_offer_dock() {
        paparazzi.snapshot {
            Frame {
                ContentDetailShell(
                    state = ContentDetailUiState.Loaded(listingContent()),
                    onBack = {},
                    onPrimaryAction = {},
                    onSecondaryAction = {},
                    onMessageCounterparty = {},
                )
            }
        }
    }

    @Test
    fun content_detail_invoice_with_line_items_and_full_width_pay_dock() {
        paparazzi.snapshot {
            Frame {
                ContentDetailShell(
                    state = ContentDetailUiState.Loaded(InvoiceDetailViewModel.Projection.fixture("INV-00247")),
                    onBack = {},
                    onPrimaryAction = {},
                    onSecondaryAction = null,
                    onMessageCounterparty = null,
                )
            }
        }
    }

    private fun gigContent(): ContentDetailContent {
        val gig =
            GigDto(
                id = "g1",
                title = "Hang 3 shelves",
                description = "Three IKEA Lack shelves on drywall — studs already located and marked.",
                price = 60.0,
                category = "handyman",
                status = "open",
                userId = "u1",
                bidCount = 4,
                distanceMiles = 0.2,
                pickupAddress = "Rose Court, Unit 4B",
            )
        val bids =
            (1..4).map { i ->
                GigBidDto(
                    id = "b$i",
                    userId = "u$i",
                    bidAmount = (50 + i * 5).toDouble(),
                    status = "pending",
                    bidder =
                        GigCreator(
                            id = "u$i",
                            username = "u$i",
                            name = "Bidder $i",
                            verified = true,
                        ),
                )
            }
        return GigDetailViewModel.Projection.project(gig, bids)
    }

    private fun listingContent(): ContentDetailContent {
        val listing =
            ListingDto(
                id = "l1",
                userId = "u1",
                title = "Mid-century sofa",
                description = "Walnut frame, original cushions in great shape. Pickup only from West Adams.",
                price = 320.0,
                isFree = false,
                category = "furniture",
                condition = "like_new",
                status = "active",
                mediaUrls = emptyList(),
                firstImage = null,
                layer = "goods",
                listingType = "sell_item",
                locationName = "West Adams",
                distanceMeters = 644.0,
            )
        return ListingDetailViewModel.Projection.project(listing)
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        PantopusTheme {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(PantopusColors.appBg),
            ) { content() }
        }
    }
}
