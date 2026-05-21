@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.audience_profile.edit_persona

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
 * Paparazzi snapshots for A13.12 Edit persona. The two frames mirror the
 * design source: published & monetized (live) and mid-setup draft (setup).
 */
class EditPersonaSnapshotTest {
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
    fun edit_persona_live() {
        paparazzi.snapshot {
            Frame {
                EditPersonaLoadedContent(
                    content = EditPersonaSampleData.live,
                    variant = EditPersonaVariant.Live,
                )
            }
        }
    }

    @Test
    fun edit_persona_setup() {
        paparazzi.snapshot {
            Frame {
                EditPersonaLoadedContent(
                    content = EditPersonaSampleData.setup,
                    variant = EditPersonaVariant.Setup,
                    stepsDone = EditPersonaSampleData.SETUP_STEPS_DONE,
                    stepsTotal = EditPersonaSampleData.SETUP_STEPS_TOTAL,
                )
            }
        }
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        PantopusTheme {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(PantopusColors.appBg),
            ) {
                content()
            }
        }
    }
}
