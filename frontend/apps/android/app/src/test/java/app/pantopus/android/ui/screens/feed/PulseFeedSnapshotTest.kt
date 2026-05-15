@file:Suppress("MagicNumber")

package app.pantopus.android.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.screens.feed.pulse.PulseAttendeeStrip
import app.pantopus.android.ui.screens.feed.pulse.PulseIntent
import app.pantopus.android.ui.screens.feed.pulse.PulsePostCard
import app.pantopus.android.ui.screens.feed.pulse.PulsePostCardContent
import app.pantopus.android.ui.screens.feed.pulse.reactionTemplate
import app.pantopus.android.ui.screens.shared.feed.FeedChipItem
import app.pantopus.android.ui.screens.shared.feed.FeedChipRow
import app.pantopus.android.ui.screens.shared.feed.FeedSkeletonCard
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Rule
import org.junit.Test

/**
 * Paparazzi snapshots for the three Pulse-feed frames (T1.2). Each
 * test renders a subset of the screen geometry — the screen is too
 * tall for a single PNG on a Pixel 5 device frame, so the
 * intent-chip row, populated cards, and skeleton run as separate
 * baselines.
 */
class PulseFeedSnapshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 1500,
                    softButtons = false,
                ),
        )

    @Test
    fun pulse_intent_chip_row() {
        paparazzi.snapshot {
            Frame {
                FeedChipRow(
                    chips = PulseIntent.entries.map { FeedChipItem(id = it.key, label = it.label) },
                    activeId = PulseIntent.All.key,
                    onSelect = {},
                )
            }
        }
    }

    @Test
    fun pulse_populated_card_ask() {
        paparazzi.snapshot {
            Frame {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PulsePostCard(
                        content = askPost(),
                        onTap = {},
                        onPrimaryReaction = {},
                        onRSVP = null,
                    )
                }
            }
        }
    }

    @Test
    fun pulse_populated_card_event_with_rsvp_strip() {
        paparazzi.snapshot {
            Frame {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PulsePostCard(
                        content = eventPost(),
                        onTap = {},
                        onPrimaryReaction = {},
                        onRSVP = {},
                    )
                }
            }
        }
    }

    @Test
    fun pulse_loading_skeleton() {
        paparazzi.snapshot {
            Frame {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FeedSkeletonCard()
                    FeedSkeletonCard(withTitle = true)
                    FeedSkeletonCard()
                }
            }
        }
    }

    private fun askPost(): PulsePostCardContent =
        PulsePostCardContent(
            id = "p1",
            authorName = "Maria L.",
            authorInitials = "ML",
            authorVerified = true,
            meta = "2h · Elm Park",
            intent = PulseIntent.Ask,
            title = null,
            body = "Anyone know a good dog-walker for Tue/Thu afternoons?",
            reactions = PulseIntent.Ask.reactionTemplate(helpfulCount = 12, secondaryCount = 3),
            attendees = null,
            userHasReacted = false,
        )

    private fun eventPost(): PulsePostCardContent =
        PulsePostCardContent(
            id = "p2",
            authorName = "Rose Court Block",
            authorInitials = "RC",
            authorVerified = true,
            meta = "Tomorrow · 6pm",
            intent = PulseIntent.Event,
            title = "Block potluck — bring a side",
            body = "Tables out front of #14. Kids welcome.",
            reactions = PulseIntent.Event.reactionTemplate(helpfulCount = 7),
            attendees = PulseAttendeeStrip(avatars = listOf("JT", "ML", "EM"), goingCount = 18, userIsGoing = false),
            userHasReacted = false,
        )

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        PantopusTheme {
            androidx.compose.foundation.layout.Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(PantopusColors.appBg),
            ) { content() }
        }
    }
}
