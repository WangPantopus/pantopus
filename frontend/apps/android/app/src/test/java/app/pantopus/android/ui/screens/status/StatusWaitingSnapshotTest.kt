@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.status

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
 * Paparazzi snapshots for T3.6 Status / Waiting. Three frames mirror
 * the design prompt: claim submitted, under review, check your email.
 */
class StatusWaitingSnapshotTest {
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
    fun status_claim_submitted() {
        paparazzi.snapshot {
            Frame {
                StatusWaitingScreen(content = StatusWaitingContent.claimSubmitted(homeName = "412 Elm St"))
            }
        }
    }

    @Test
    fun status_under_review() {
        paparazzi.snapshot {
            Frame {
                StatusWaitingScreen(
                    content =
                        StatusWaitingContent.underReview(
                            homeName = "412 Elm St",
                            submittedAgo = "2 days ago",
                        ),
                )
            }
        }
    }

    @Test
    fun status_check_your_email() {
        paparazzi.snapshot {
            Frame {
                StatusWaitingScreen(content = StatusWaitingContent.checkYourEmail(email = "alice@example.com"))
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
            ) { content() }
        }
    }
}
