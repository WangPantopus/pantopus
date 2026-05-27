@file:Suppress("MagicNumber")

package app.pantopus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.screens.shared.list_of_rows.CompactButtonVariant
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Unit + Paparazzi coverage for [CompactButton]. Geometry assertions
 * mirror `Core/Design/Components/CompactButton.swift:88-95`:
 *
 *   - footer        → 34dp regardless of variant
 *   - inline primary → 30dp
 *   - inline ghost  → 28dp (the only variant-driven height)
 *   - inline destructive → 30dp
 */
class CompactButtonTest {
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
    fun footer_is_34dp_for_every_variant() {
        for (variant in CompactButtonVariant.entries) {
            assertEquals(
                "Footer height for $variant",
                34,
                resolvedHeightDp(CompactButtonSize.Footer, variant),
            )
        }
    }

    @Test
    fun inline_primary_is_30dp() {
        assertEquals(
            30,
            resolvedHeightDp(CompactButtonSize.InlineAction, CompactButtonVariant.Primary),
        )
    }

    @Test
    fun inline_ghost_is_28dp() {
        assertEquals(
            28,
            resolvedHeightDp(CompactButtonSize.InlineAction, CompactButtonVariant.Ghost),
        )
    }

    @Test
    fun inline_destructive_is_30dp() {
        assertEquals(
            30,
            resolvedHeightDp(CompactButtonSize.InlineAction, CompactButtonVariant.Destructive),
        )
    }

    @Test
    fun snapshot_footer_and_inline_gallery() {
        paparazzi.snapshot {
            CompactButtonGallery()
        }
    }
}

@Composable
private fun CompactButtonGallery() {
    Column(
        modifier =
            Modifier
                .background(PantopusColors.appSurface)
                .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Text("Footer (34dp)", style = PantopusTextStyle.caption)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            CompactButton(
                title = "Withdraw",
                variant = CompactButtonVariant.Destructive,
                size = CompactButtonSize.Footer,
                onClick = {},
                icon = PantopusIcon.X,
                modifier = Modifier.weight(1f),
            )
            CompactButton(
                title = "Edit bid",
                variant = CompactButtonVariant.Primary,
                size = CompactButtonSize.Footer,
                onClick = {},
                icon = PantopusIcon.Check,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            CompactButton(
                title = "Message",
                variant = CompactButtonVariant.Ghost,
                size = CompactButtonSize.Footer,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
            CompactButton(
                title = "Mark complete",
                variant = CompactButtonVariant.Primary,
                size = CompactButtonSize.Footer,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
        }

        Text("Inline (30/28dp)", style = PantopusTextStyle.caption)
        CompactButton(
            title = "Accept",
            variant = CompactButtonVariant.Primary,
            size = CompactButtonSize.InlineAction,
            onClick = {},
        )
        CompactButton(
            title = "Ignore",
            variant = CompactButtonVariant.Ghost,
            size = CompactButtonSize.InlineAction,
            onClick = {},
        )
    }
}
