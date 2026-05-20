@file:Suppress("PackageNaming", "LongParameterList", "LongMethod")

package app.pantopus.android.ui.screens.mailbox.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.data.api.models.mailbox.MailItem
import app.pantopus.android.ui.screens.mailbox.MailboxListViewModel
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Paparazzi snapshots for the P4.2 Mailbox Search surface. One baseline
 * per render state (loading / populated / empty / error).
 *
 * Baselines are recorded on first run via `./gradlew paparazziRecord` and
 * verified on every CI run via `./gradlew paparazziVerify`. Annotated
 * `@Ignore` until baselines land so the first PR doesn't fail CI on a
 * missing image — the follow-up records baselines and removes the
 * annotation.
 */
@Ignore("Baselines recorded in follow-up — see commit body")
class MailboxSearchSnapshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 1600,
                    softButtons = false,
                ),
        )

    private val corpus =
        listOf(
            mail(id = "m1", type = "bill", mailType = "bill", subject = "Water bill", previewText = "Due June 1", sender = "City of Oakland"),
            mail(id = "m2", type = "booklet", mailType = "booklet", displayTitle = "Welcome packet", previewText = "Booklet enclosed", sender = "Maria Kovacs", viewed = true),
        )

    @Test
    fun mailbox_search_loading() {
        paparazzi.snapshot {
            Frame {
                MailboxSearchContent(
                    loadPhase = MailboxSearchViewModel.LoadPhase.Loading,
                    query = "wa",
                    results = emptyList(),
                    rowOf = { MailboxListViewModel.makeRow(it) {} },
                    onQueryChange = {},
                    onCancel = {},
                    onRetry = {},
                )
            }
        }
    }

    @Test
    fun mailbox_search_populated() {
        paparazzi.snapshot {
            Frame {
                MailboxSearchContent(
                    loadPhase = MailboxSearchViewModel.LoadPhase.Ready,
                    query = "wa",
                    results = corpus,
                    rowOf = { MailboxListViewModel.makeRow(it) {} },
                    onQueryChange = {},
                    onCancel = {},
                    onRetry = {},
                )
            }
        }
    }

    @Test
    fun mailbox_search_empty() {
        paparazzi.snapshot {
            Frame {
                MailboxSearchContent(
                    loadPhase = MailboxSearchViewModel.LoadPhase.Ready,
                    query = "zzzzzz",
                    results = emptyList(),
                    rowOf = { MailboxListViewModel.makeRow(it) {} },
                    onQueryChange = {},
                    onCancel = {},
                    onRetry = {},
                )
            }
        }
    }

    @Test
    fun mailbox_search_error() {
        paparazzi.snapshot {
            Frame {
                MailboxSearchContent(
                    loadPhase = MailboxSearchViewModel.LoadPhase.Error("Couldn't load your mailbox."),
                    query = "",
                    results = emptyList(),
                    rowOf = { MailboxListViewModel.makeRow(it) {} },
                    onQueryChange = {},
                    onCancel = {},
                    onRetry = {},
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
            ) { content() }
        }
    }

    private fun mail(
        id: String,
        type: String = "general",
        mailType: String? = null,
        subject: String? = null,
        displayTitle: String? = null,
        previewText: String? = null,
        sender: String? = null,
        viewed: Boolean = false,
    ) = MailItem(
        id = id,
        recipientUserId = null,
        recipientHomeId = null,
        deliveryTargetType = null,
        deliveryTargetId = null,
        addressHomeId = null,
        attnUserId = null,
        attnLabel = null,
        deliveryVisibility = null,
        mailType = mailType,
        displayTitle = displayTitle,
        previewText = previewText,
        primaryAction = null,
        actionRequired = null,
        ackRequired = null,
        ackStatus = null,
        type = type,
        subject = subject,
        content = null,
        senderUserId = null,
        senderBusinessName = sender,
        senderAddress = null,
        viewed = viewed,
        viewedAt = null,
        archived = false,
        starred = false,
        payoutAmount = null,
        payoutStatus = null,
        category = null,
        tags = emptyList(),
        priority = "normal",
        attachments = null,
        expiresAt = null,
        createdAt = "2026-05-15T12:00:00Z",
    )
}
