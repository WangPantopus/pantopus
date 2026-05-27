@file:Suppress("MagicNumber")

package app.pantopus.android.ui.components

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.screens.shared.list_of_rows.Bidder
import app.pantopus.android.ui.screens.shared.list_of_rows.BidderTone
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Unit + Paparazzi coverage for [BidderStack]. iOS counterpart:
 * `PantopusTests/Design/Components/ComponentRenderTests.swift` does the
 * smoke render; the accessibility label assertions mirror
 * `Core/Design/Components/BidderStack.swift:70-79`.
 *
 * The snapshot is sized to the iOS fixture cell width (200pt) so a
 * cross-platform diff stays meaningful.
 */
class BidderStackTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 400,
                    softButtons = false,
                ),
        )

    @Test
    fun a11yLabel_empty_is_no_bidders() {
        assertEquals("No bidders", bidderStackA11yLabel(0))
    }

    @Test
    fun a11yLabel_single_is_singular() {
        assertEquals("1 bidder", bidderStackA11yLabel(1))
    }

    @Test
    fun a11yLabel_many_is_plural() {
        assertEquals("12 bidders", bidderStackA11yLabel(12))
    }

    @Test
    fun snapshot_three_plus_overflow() {
        paparazzi.snapshot {
            BidderStackGallery()
        }
    }
}

@Composable
private fun BidderStackGallery() {
    Column(
        modifier =
            Modifier
                .background(PantopusColors.appSurface)
                .padding(Spacing.s4),
        verticalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(Spacing.s3),
    ) {
        Text("Three + 9 overflow", style = PantopusTextStyle.caption)
        BidderStack(
            bidders =
                listOf(
                    Bidder(id = "1", initials = "AR", tone = BidderTone.Violet),
                    Bidder(id = "2", initials = "MT", tone = BidderTone.Amber),
                    Bidder(id = "3", initials = "JP", tone = BidderTone.Teal),
                ),
            overflow = 9,
        )

        Text("Single (no overflow)", style = PantopusTextStyle.caption)
        BidderStack(bidders = listOf(Bidder("1", "AR", BidderTone.Sky)))

        Text("All six tones", style = PantopusTextStyle.caption)
        BidderStack(
            bidders =
                listOf(
                    Bidder("1", "Sk", BidderTone.Sky),
                    Bidder("2", "Te", BidderTone.Teal),
                    Bidder("3", "Am", BidderTone.Amber),
                    Bidder("4", "Ro", BidderTone.Rose),
                    Bidder("5", "Vi", BidderTone.Violet),
                    Bidder("6", "Sl", BidderTone.Slate),
                ),
        )

        Text("Overflow only (zero bidders)", style = PantopusTextStyle.caption)
        BidderStack(bidders = emptyList(), overflow = 4)
    }
}
