@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.inbox.conversation

/**
 * Deterministic AI-thread fixtures for previews and the Paparazzi
 * snapshot tests. The AI thread has no backend wiring yet, so these stand
 * in for what the SSE stream will eventually produce.
 */
object ChatConversationSampleData {
    const val AI_NAME = "Ask Pantopus"
    const val FAN_PERSONA_NAME = "Wynn B."

    val fanCounterparty =
        ChatCounterparty.Person(
            displayName = FAN_PERSONA_NAME,
            initials = "WB",
            locality = "The Sourdough Diary",
            verified = true,
            online = true,
        )

    val fanEntitlement =
        ChatFanEntitlement(
            currentTier = "Bronze",
            renewsOn = "Apr 12",
            messagesLeft = 3,
            messageLimit = 5,
            resetCopy = "Resets May 1",
        )

    val fanLockedEntitlement =
        ChatFanEntitlement(
            currentTier = "Bronze",
            renewsOn = "Apr 12",
            messagesLeft = 3,
            messageLimit = 5,
            resetCopy = "Resets May 1",
            requiredReplyTier = "Silver",
        )

    /**
     * An active AI thread: a user question followed by a structured AI
     * reply carrying an inline estimate card.
     */
    val aiActiveRows: List<ChatTimelineRow> =
        listOf(
            ChatTimelineRow.DayDivider(ChatDayDivider(id = "today", label = "Today")),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "u1",
                    side = ChatMessageSide.Outgoing,
                    body = ChatBubbleBody.Text("What's a fair price to hang 3 shelves in my living room?"),
                    hasTail = true,
                    stamp = "9:07 AM",
                    deliveryState = ChatDeliveryState.Read,
                ),
            ),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "ai1",
                    side = ChatMessageSide.Incoming,
                    body =
                        ChatBubbleBody.AiReply(
                            text = "For drywall, 3 shelves, ~1.5 hours of work, neighbors in Elm Park are paying:",
                            estimate =
                                ChatEstimate(
                                    amount = "$55–70",
                                    basis = "based on 8 nearby jobs",
                                    confidence = "Medium–High",
                                ),
                        ),
                    hasTail = true,
                    stamp = "9:08 AM",
                    deliveryState = null,
                ),
            ),
        )

    val fanActiveRows: List<ChatTimelineRow> =
        listOf(
            ChatTimelineRow.DayDivider(ChatDayDivider(id = "today", label = "Today")),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "fan1",
                    side = ChatMessageSide.Outgoing,
                    body = ChatBubbleBody.Text("Loved this week's loaf — quick question: can I sub bread flour for AP?"),
                    hasTail = true,
                    stamp = "8:51 AM",
                    deliveryState = ChatDeliveryState.Read,
                    sentSupportTier = "Bronze",
                ),
            ),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "creator1",
                    side = ChatMessageSide.Incoming,
                    body = ChatBubbleBody.Text("Short answer — yes, bread flour gives more chew. Drop hydration by about 5g per 100g."),
                    hasTail = true,
                    stamp = "Wynn · 9:03 AM",
                    deliveryState = null,
                ),
            ),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "creator2",
                    side = ChatMessageSide.Incoming,
                    body = ChatBubbleBody.Text("Silver members also get my starter troubleshooting checklist and bake timing notes."),
                    hasTail = true,
                    stamp = "Wynn · 9:04 AM",
                    deliveryState = null,
                    lockedTier = "Silver",
                ),
            ),
        )
}
