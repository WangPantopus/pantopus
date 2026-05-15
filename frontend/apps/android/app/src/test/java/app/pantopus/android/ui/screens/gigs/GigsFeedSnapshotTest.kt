@file:Suppress("MagicNumber")

package app.pantopus.android.ui.screens.gigs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Rule
import org.junit.Test

/**
 * Paparazzi snapshots for the T2.3 Gigs feed: category chip row in the
 * default (all) state, populated grid covering five distinct
 * category colours + the amber bid pill + the "Be the first" zero-
 * bid affordance, empty frame with the radius hint pill, loading
 * skeleton.
 */
class GigsFeedSnapshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 2200,
                    softButtons = false,
                ),
        )

    @Test
    fun gigs_category_chip_row_active_all() {
        paparazzi.snapshot {
            Frame {
                GigsCategoryChipRow(active = GigsCategory.All, onSelect = {})
            }
        }
    }

    @Test
    fun gigs_populated_with_brand_colors_and_bid_pills() {
        paparazzi.snapshot {
            Frame { PopulatedFrame(rows = populatedRows(), onOpenGig = {}) }
        }
    }

    @Test
    fun gigs_empty_with_radius_hint() {
        paparazzi.snapshot {
            Frame { EmptyFrame(radiusMiles = 1.0, onPostTask = {}) }
        }
    }

    @Test
    fun gigs_loading_skeleton() {
        paparazzi.snapshot {
            Frame { LoadingFrame() }
        }
    }

    private fun populatedRows(): List<GigCardContent> =
        listOf(
            GigCardContent(
                id = "g1",
                category = GigsCategory.Handyman,
                metaLine = "0.2mi · 2h ago",
                title = "Hang 3 floating shelves in living room",
                body = "Need 3 IKEA Lack shelves mounted on drywall — studs marked.",
                price = "$60",
                bidCount = 4,
                distanceLabel = "0.2mi",
            ),
            GigCardContent(
                id = "g2",
                category = GigsCategory.Cleaning,
                metaLine = "0.5mi · 5h ago",
                title = "Deep clean 2BR apartment before move-out",
                body = "Kitchen, bath, baseboards, inside oven.",
                price = "$180",
                bidCount = 7,
                distanceLabel = "0.5mi",
            ),
            GigCardContent(
                id = "g3",
                category = GigsCategory.PetCare,
                metaLine = "0.3mi · 1d ago",
                title = "Midday dog walks Tue/Thu — friendly shepherd mix",
                body = "20-min loop, ongoing.",
                price = "$22 / walk",
                bidCount = 2,
                distanceLabel = "0.3mi",
            ),
            GigCardContent(
                id = "g4",
                category = GigsCategory.Moving,
                metaLine = "0.8mi · 2d ago",
                title = "Help moving small studio — Saturday morning",
                body = "Sofa, bed frame, ~8 boxes. U-Haul rented.",
                price = "$80",
                // Zero-bid row exercises the "Be the first" affordance.
                bidCount = 0,
                distanceLabel = "0.8mi",
            ),
            GigCardContent(
                id = "g5",
                category = GigsCategory.Tutoring,
                metaLine = "1.0mi · 6h ago",
                title = "Algebra tutoring, 10th grade",
                body = "Weekly sessions, in person or remote.",
                price = "$40 / hr",
                bidCount = 3,
                distanceLabel = "1.0mi",
            ),
        )

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
