@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.inbox.conversation

/**
 * Deterministic AI-thread fixtures for previews and the Paparazzi
 * snapshot tests. The AI thread has no backend wiring yet, so these stand
 * in for what the SSE stream will eventually produce.
 */
object ChatConversationSampleData {
    const val AI_NAME = "Ask Pantopus"

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
}
