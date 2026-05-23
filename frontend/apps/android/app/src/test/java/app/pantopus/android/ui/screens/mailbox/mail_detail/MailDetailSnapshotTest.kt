@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.mailbox.mail_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.screens.mailbox.item_detail.MailItemCategory
import app.pantopus.android.ui.screens.mailbox.item_detail.MailTrust
import app.pantopus.android.ui.theme.PantopusColors
import org.junit.Rule
import org.junit.Test

/** A17.1 generic mail-detail snapshots across three category accents. */
class MailDetailSnapshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2400, softButtons = false),
        )

    @Test
    fun notice_generic_detail() {
        snapshot(MailItemCategory.Notice, "Notice of public hearing")
    }

    @Test
    fun package_generic_detail() {
        snapshot(MailItemCategory.Package, "Package delivered to porch")
    }

    @Test
    fun coupon_generic_detail() {
        snapshot(MailItemCategory.Coupon, "Neighborhood bakery coupon")
    }

    private fun snapshot(
        category: MailItemCategory,
        title: String,
    ) {
        paparazzi.snapshot {
            Root {
                GenericMailDetailLayout(
                    content = makeContent(category = category, title = title),
                    ackInFlight = false,
                    onBack = {},
                    onAcknowledge = {},
                    onOpenSenderProfile = {},
                    onSaveToVault = {},
                )
            }
        }
    }

    private fun makeContent(
        category: MailItemCategory,
        title: String,
    ): MailDetailContent =
        MailDetailContent(
            mailId = "mail-${category.raw}",
            category = category,
            trust = MailTrust.Verified,
            detailTrust = category.detailTrust,
            senderDisplayName = "City of Oakland",
            senderMeta = "@oakland",
            senderTypeLabel = "Verified sender",
            carrierLine = "via Pantopus Mail",
            senderInitials = "CO",
            senderUserId = "sender-1",
            title = title,
            excerpt = "A short preview line keeps the subject block in the same shape as production mail.",
            referenceLabel = "Ref ${category.raw.uppercase()}-2026",
            createdAtLabel = "Fri May 15, 2026",
            expiresAtLabel = null,
            readStatusLabel = "Unread",
            bodyParagraphs =
                listOf(
                    "This is the first paragraph of the mail body, rendered in the category body slot.",
                    "A second paragraph validates spacing before the sticky action shelf.",
                ),
            attachments = listOf("notice.pdf"),
            aiSummary = null,
            ackRequired = category == MailItemCategory.Notice,
            isAcknowledged = false,
        )

    @Composable
    private fun Root(content: @Composable () -> Unit) {
        Box(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) { content() }
    }
}
