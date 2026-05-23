@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.mailbox.item_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.BookletBody
import app.pantopus.android.ui.theme.PantopusColors
import org.junit.Rule
import org.junit.Test

/**
 * A17.2 — Booklet mail body. Paparazzi snapshots cover two deterministic
 * booklet types and exercise the folded-paper page swiper with dots.
 */
class BookletBodySnapshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2200, softButtons = false),
        )

    @Test fun booklet_voter_guide_shell() {
        paparazzi.snapshot {
            Root {
                MailboxItemDetailShell(
                    category = MailItemCategory.Booklet,
                    trust = MailTrust.Verified,
                    sender = SenderBlockContent("League of Women Voters", "2d ago", "LV"),
                    onBack = {},
                ) {
                    BookletBody(booklet = MailItemSampleData.bookletVoterGuide)
                }
            }
        }
    }

    @Test fun booklet_catalog_shell() {
        paparazzi.snapshot {
            Root {
                MailboxItemDetailShell(
                    category = MailItemCategory.Booklet,
                    trust = MailTrust.Verified,
                    sender = SenderBlockContent("Elm Park Merchant Guild", "3h ago", "EP"),
                    onBack = {},
                ) {
                    BookletBody(booklet = MailItemSampleData.bookletNeighborhoodCatalog)
                }
            }
        }
    }

    @Composable
    private fun Root(content: @Composable () -> Unit) {
        Box(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) { content() }
    }
}
