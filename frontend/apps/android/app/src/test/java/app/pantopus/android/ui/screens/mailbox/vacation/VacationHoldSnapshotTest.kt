@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.mailbox.vacation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.screens.mailbox.vacation.components.HeldList
import app.pantopus.android.ui.screens.mailbox.vacation.components.HoldStatusHero
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.Spacing
import org.junit.Rule
import org.junit.Test

/**
 * A14.8 — Paparazzi snapshots for the Vacation Hold screen in both
 * variants plus the two feature-local primitives. Mirrors the iOS
 * `VacationHoldSnapshotTests`.
 */
class VacationHoldSnapshotTest {
    @get:Rule
    val paparazzi: Paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 2800,
                    softButtons = false,
                ),
        )

    @Test
    fun vacation_hold_scheduling_frame() {
        paparazzi.snapshot {
            VacationHoldScreen(
                onBack = {},
                viewModel = VacationHoldViewModel(VacationHoldSeed.Scheduling),
                seed = VacationHoldSeed.Scheduling,
            )
        }
    }

    @Test
    fun vacation_hold_active_frame() {
        paparazzi.snapshot {
            VacationHoldScreen(
                onBack = {},
                viewModel = VacationHoldViewModel(VacationHoldSeed.Active),
                seed = VacationHoldSeed.Active,
            )
        }
    }

    @Test
    fun hold_status_hero_primitive() {
        paparazzi.snapshot {
            Root {
                HoldStatusHero(
                    daysLeft = 5,
                    untilLabel = "Dec 12",
                    stats = VacationHoldSampleData.activeHold.stats,
                    reduceMotionOverride = true,
                )
            }
        }
    }

    @Test
    fun held_list_primitive() {
        paparazzi.snapshot {
            Root {
                HeldList(items = VacationHoldSampleData.activeHold.heldItems)
            }
        }
    }

    @Composable
    private fun Root(content: @Composable () -> Unit) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(PantopusColors.appBg)
                    .padding(Spacing.s3),
        ) {
            content()
        }
    }
}
