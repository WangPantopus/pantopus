@file:Suppress("MagicNumber", "LongMethod", "UnusedPrivateMember", "PackageNaming")

package app.pantopus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import org.junit.Rule
import org.junit.Test

/**
 * B1.3 — Paparazzi snapshots for the identity-preview primitives
 * [ViewerPicker] (+ [LiveBadge]) and [RedactionScrim]. Mirrors
 * `PantopusTests/Core/Design/Components/IdentityPreviewPrimitivesSnapshotTests.swift`.
 *
 *   - `viewer_picker_*` — one baseline per selectable [ViewerAudience]
 *     (the selected chip recolours to its pillar).
 *   - `redaction_scrim_*` — one baseline per [RedactionLevel].
 *
 * Baselines live under `app/src/test/snapshots/images/`; regenerate via
 * `./gradlew paparazziRecord` (requires the Android SDK).
 */
class IdentityPreviewPrimitivesSnapshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(softButtons = false),
        )

    // ── ViewerPicker (one frame per selection) ──────────────────────

    @Test
    fun viewer_picker_public() {
        paparazzi.snapshot { PickerFrame(ViewerAudience.Public) }
    }

    @Test
    fun viewer_picker_persona_audience() {
        paparazzi.snapshot { PickerFrame(ViewerAudience.PersonaAudience) }
    }

    @Test
    fun viewer_picker_neighbor() {
        paparazzi.snapshot { PickerFrame(ViewerAudience.Neighbor) }
    }

    @Test
    fun viewer_picker_connection() {
        paparazzi.snapshot { PickerFrame(ViewerAudience.Connection) }
    }

    @Test
    fun viewer_picker_gig_participant() {
        paparazzi.snapshot { PickerFrame(ViewerAudience.GigParticipant) }
    }

    @Test
    fun viewer_picker_household() {
        paparazzi.snapshot { PickerFrame(ViewerAudience.Household) }
    }

    @Test
    fun live_badge_variants() {
        paparazzi.snapshot {
            Column(
                modifier =
                    Modifier
                        .background(PantopusColors.appBg)
                        .padding(Spacing.s4),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                LiveBadge()
                LiveBadge(label = "Preview", toneColor = PantopusColors.warning)
            }
        }
    }

    // ── RedactionScrim (one frame per level) ────────────────────────

    @Test
    fun redaction_scrim_hidden() {
        paparazzi.snapshot { ScrimFrame(RedactionLevel.Hidden) }
    }

    @Test
    fun redaction_scrim_fuzzed() {
        paparazzi.snapshot { ScrimFrame(RedactionLevel.Fuzzed) }
    }

    @Test
    fun redaction_scrim_partial() {
        paparazzi.snapshot { ScrimFrame(RedactionLevel.Partial) }
    }
}

@Composable
private fun PickerFrame(selection: ViewerAudience) {
    Column(modifier = Modifier.background(PantopusColors.appBg)) {
        ViewerPicker(
            selection = selection,
            onSelect = {},
            title = "Preview your profile as",
        )
    }
}

@Composable
private fun ScrimFrame(level: RedactionLevel) {
    Column(
        modifier =
            Modifier
                .background(PantopusColors.appBg)
                .padding(Spacing.s4),
    ) {
        RedactionScrim(level = level, label = "Hidden from public") {
            SampleField()
        }
    }
}

@Composable
private fun SampleField() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .padding(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Text(
            text = "CONTACT",
            style = PantopusTextStyle.overline,
            color = PantopusColors.appTextMuted,
        )
        Text(
            text = "(555) 010-2837",
            style = PantopusTextStyle.small,
            color = PantopusColors.appText,
        )
    }
}
