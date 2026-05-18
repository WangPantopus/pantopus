@file:Suppress("MagicNumber")

package app.pantopus.android.ui.screens.inbox.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Rule
import org.junit.Test

/**
 * Paparazzi snapshots for the T2.2 Chat Conversation: header (person
 * vs AI), AI welcome frame, person empty frame, populated thread with
 * day divider + tail-grouped bubbles + delivery states, composer with
 * + without text.
 */
class ChatConversationSnapshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 2200,
                    softButtons = false,
                ),
        )

    private val personCounterparty =
        ChatCounterparty.Person(
            displayName = "Maria K.",
            initials = "MK",
            locality = "Elm Park",
            verified = true,
            online = true,
        )

    private val aiCounterparty = ChatCounterparty.Ai(displayName = "Ask Pantopus")

    @Test
    fun chat_conversation_header_person() {
        paparazzi.snapshot {
            Frame { ChatHeader(counterparty = personCounterparty, onBack = {}) }
        }
    }

    @Test
    fun chat_conversation_header_ai() {
        paparazzi.snapshot {
            Frame { ChatHeader(counterparty = aiCounterparty, onBack = {}) }
        }
    }

    @Test
    fun chat_conversation_empty_person() {
        paparazzi.snapshot {
            Frame {
                EmptyFrame(
                    counterparty = personCounterparty,
                    aiPrompts = emptyList(),
                    emptyChips =
                        listOf(
                            ChatPromptChip("intro", "Introduce yourself", PantopusIcon.Hand),
                            ChatPromptChip("gig", "Ask about the gig", PantopusIcon.Briefcase),
                            ChatPromptChip("listing", "Share a listing", PantopusIcon.Tag),
                        ),
                    onChipTap = {},
                )
            }
        }
    }

    @Test
    fun chat_conversation_empty_ai_welcome() {
        paparazzi.snapshot {
            Frame {
                EmptyFrame(
                    counterparty = aiCounterparty,
                    aiPrompts =
                        listOf(
                            ChatPromptChip("mail", "Summarize my inbox", PantopusIcon.Mailbox),
                            ChatPromptChip("task", "Post a task", PantopusIcon.Pencil),
                            ChatPromptChip("handy", "Find a handyman nearby", PantopusIcon.Hammer),
                        ),
                    emptyChips = emptyList(),
                    onChipTap = {},
                )
            }
        }
    }

    @Test
    fun chat_conversation_populated_with_day_divider_and_grouped_bubbles() {
        paparazzi.snapshot {
            Frame {
                PopulatedFrame(rows = populatedRows(), onRetry = {}, onLoadOlder = {})
            }
        }
    }

    @Test
    fun chat_conversation_composer_empty_and_populated() {
        paparazzi.snapshot {
            Frame {
                Column {
                    Composer(
                        text = "",
                        placeholder = "Message Maria…",
                        canSend = false,
                        onTextChange = {},
                        onSend = {},
                    )
                    Composer(
                        text = "Hey Maria, see you at 9.",
                        placeholder = "Message Maria…",
                        canSend = true,
                        onTextChange = {},
                        onSend = {},
                    )
                }
            }
        }
    }

    private fun populatedRows(): List<ChatTimelineRow> =
        listOf(
            ChatTimelineRow.DayDivider(ChatDayDivider(id = "today", label = "Today")),
            // Incoming pair, tail on second
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "m1",
                    side = ChatMessageSide.Incoming,
                    body = ChatBubbleBody.Text("Hey — are you free Saturday morning?"),
                    hasTail = false,
                    stamp = null,
                    deliveryState = null,
                ),
            ),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "m2",
                    side = ChatMessageSide.Incoming,
                    body = ChatBubbleBody.Text("I could use a hand mounting three IKEA shelves."),
                    hasTail = true,
                    stamp = "Maria · 9:48 AM",
                    deliveryState = null,
                ),
            ),
            // Outgoing pair, tail + delivered stamp on second
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "m3",
                    side = ChatMessageSide.Outgoing,
                    body = ChatBubbleBody.Text("Yes! I can be over by 9."),
                    hasTail = false,
                    stamp = null,
                    deliveryState = null,
                ),
            ),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "m4",
                    side = ChatMessageSide.Outgoing,
                    body = ChatBubbleBody.Text("Bringing my drill."),
                    hasTail = true,
                    stamp = "9:51 AM",
                    deliveryState = ChatDeliveryState.Delivered,
                ),
            ),
            // System link variant
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "m5",
                    side = ChatMessageSide.Incoming,
                    body =
                        ChatBubbleBody.SystemLink(
                            label = "Shared gig ·",
                            sub = "Hang 3 floating shelves",
                            accent = ChatSystemLinkAccent.Primary,
                        ),
                    hasTail = true,
                    stamp = "Maria · 9:52 AM",
                    deliveryState = null,
                ),
            ),
        )

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
