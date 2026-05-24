@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.inbox.conversation

/**
 * Deterministic AI-thread fixtures for previews and the Paparazzi
 * snapshot tests. The AI thread has no backend wiring yet, so these stand
 * in for what the SSE stream will eventually produce.
 */
object ChatConversationSampleData {
    const val AI_NAME = "Ask Pantopus"
    const val CREATOR_FAN_NAME = "Priya R."

    val creatorContext: ChatCreatorThreadContext = ChatCreatorThreadContext.defaults(fanTierName = "Bronze", fanTierRank = 2)

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

    /**
     * Creator-side thread from A15.4: audience chrome, Bronze tier fan,
     * quota meter, and an inline broadcast reference before the fan's
     * workshop follow-up.
     */
    val creatorThreadRows: List<ChatTimelineRow> =
        listOf(
            ChatTimelineRow.DayDivider(ChatDayDivider(id = "today", label = "Today")),
            ChatTimelineRow.BroadcastReference(
                ChatBroadcastReference(
                    id = "workshop-broadcast",
                    title = "Workshop interest list",
                    subtitle = "Sunday bake workshop poll sent to Bronze+ members.",
                    metric = "2,340 reached · engagement up 12%",
                ),
            ),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "creator_m1",
                    side = ChatMessageSide.Incoming,
                    body = ChatBubbleBody.Text("Hi! Loved this week's loaf — quick question: can I sub bread flour for AP?"),
                    hasTail = true,
                    stamp = "Priya · 8:51 AM",
                    deliveryState = null,
                ),
            ),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "creator_m2",
                    side = ChatMessageSide.Outgoing,
                    body = ChatBubbleBody.Text("Yes — bread flour gives more chew. Use 5g less water per 100g."),
                    hasTail = true,
                    stamp = "9:02 AM",
                    deliveryState = ChatDeliveryState.Read,
                ),
            ),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "creator_m3",
                    side = ChatMessageSide.Incoming,
                    body = ChatBubbleBody.Text("Also — would you ever do a hands-on workshop? I'd pay."),
                    hasTail = true,
                    stamp = "Priya · 9:14 AM",
                    deliveryState = null,
                ),
            ),
        )
}
