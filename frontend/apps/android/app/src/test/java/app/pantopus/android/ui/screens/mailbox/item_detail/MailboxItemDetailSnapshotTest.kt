@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.mailbox.item_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.components.KeyFactRow
import app.pantopus.android.ui.components.TimelineStep
import app.pantopus.android.ui.components.TimelineStepState
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.PackageBody
import app.pantopus.android.ui.theme.PantopusColors
import org.junit.Rule
import org.junit.Test

/** Paparazzi snapshot for the populated Package-category detail shell. */
class MailboxItemDetailSnapshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2000, softButtons = false),
        )

    @Test fun package_detail_populated_shell() {
        paparazzi.snapshot {
            Root {
                MailboxItemDetailShell(
                    category = MailItemCategory.Package,
                    trust = MailTrust.Verified,
                    sender = SenderBlockContent("Acme Labs", "2026-04-19", "AL"),
                    aiElf = AIElfContent("Looks like your Amazon order", "Link", "Not mine"),
                    keyFacts =
                        listOf(
                            KeyFactRow(label = "Tracking #", value = "1Z9990001", isCode = true),
                            KeyFactRow(label = "Sender", value = "Acme Labs"),
                            KeyFactRow(label = "Carrier", value = "UPS"),
                        ),
                    timeline =
                        listOf(
                            TimelineStep("Shipped", TimelineStepState.Done),
                            TimelineStep("In transit", TimelineStepState.Done),
                            TimelineStep("Out for delivery", TimelineStepState.Current),
                            TimelineStep("Delivered", TimelineStepState.Upcoming),
                        ),
                    cta =
                        MailboxCTAShelfContent(
                            primaryTitle = "Log as received",
                            ghostTitle = "Not mine",
                        ),
                    onBack = {},
                ) {
                    PackageBody(carrier = "UPS", etaLine = "Arrives today by 8pm")
                }
            }
        }
    }

    @Composable
    private fun Root(content: @Composable () -> Unit) {
        Box(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) { content() }
    }
}
