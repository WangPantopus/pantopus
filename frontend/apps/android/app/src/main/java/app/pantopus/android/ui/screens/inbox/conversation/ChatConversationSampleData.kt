@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.inbox.conversation

/**
 * Deterministic AI-thread fixtures for previews and the Paparazzi
 * snapshot tests. The AI thread has no backend wiring yet, so these stand
 * in for what the SSE stream will eventually produce.
 */
object ChatConversationSampleData {
    const val AI_NAME = "Ask Pantopus"

    val dmCounterparty =
        ChatCounterparty.Person(
            displayName = "Jamal T.",
            initials = "JT",
            locality = "Elm Park",
            verified = true,
            online = false,
        )

    val queuedAttachments =
        listOf(
            ChatQueuedAttachment("queued_photo", ChatQueuedAttachmentKind.Image, "shelves.jpg"),
            ChatQueuedAttachment("queued_pdf", ChatQueuedAttachmentKind.Document, "shelf.pdf"),
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

    val dmPhotoReadRows: List<ChatTimelineRow> =
        listOf(
            ChatTimelineRow.DayDivider(ChatDayDivider(id = "today", label = "Today")),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "m1",
                    side = ChatMessageSide.Incoming,
                    body = ChatBubbleBody.Text("8:30 sharp. I'll grab two."),
                    hasTail = true,
                    stamp = "9:10 AM",
                    deliveryState = null,
                ),
            ),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "m2",
                    side = ChatMessageSide.Outgoing,
                    body = ChatBubbleBody.Text("Deal — see you at the bench."),
                    hasTail = false,
                    stamp = null,
                    deliveryState = null,
                ),
            ),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "m3",
                    side = ChatMessageSide.Outgoing,
                    body = ChatBubbleBody.Text("Snapped a photo of the spot:"),
                    hasTail = false,
                    stamp = null,
                    deliveryState = null,
                    isContinuation = true,
                ),
            ),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "m4",
                    side = ChatMessageSide.Outgoing,
                    body = ChatBubbleBody.Image(url = null),
                    hasTail = true,
                    stamp = "9:14",
                    deliveryState = ChatDeliveryState.Read,
                    isContinuation = true,
                ),
            ),
        )

    val dmTypingRows: List<ChatTimelineRow> =
        listOf(
            ChatTimelineRow.DayDivider(ChatDayDivider(id = "today", label = "Today")),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "m1",
                    side = ChatMessageSide.Incoming,
                    body = ChatBubbleBody.Text("Btw — here's the bakery I keep raving about."),
                    hasTail = true,
                    stamp = "6:42 PM",
                    deliveryState = null,
                ),
            ),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "m2",
                    side = ChatMessageSide.Outgoing,
                    body = ChatBubbleBody.Text("Bookmarked. Sunday morning mission."),
                    hasTail = true,
                    stamp = "6:44 PM",
                    deliveryState = ChatDeliveryState.Read,
                ),
            ),
        )

    val dmQueuedAttachmentRows: List<ChatTimelineRow> =
        listOf(
            ChatTimelineRow.DayDivider(ChatDayDivider(id = "today", label = "Today")),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "m1",
                    side = ChatMessageSide.Incoming,
                    body = ChatBubbleBody.Text("Can you send the shelf photo and measurements?"),
                    hasTail = true,
                    stamp = "9:12 AM",
                    deliveryState = null,
                ),
            ),
            ChatTimelineRow.Bubble(
                ChatBubbleContent(
                    id = "m2",
                    side = ChatMessageSide.Outgoing,
                    body = ChatBubbleBody.Text("Uploading both now."),
                    hasTail = true,
                    stamp = "9:13 AM",
                    deliveryState = ChatDeliveryState.Delivered,
                ),
            ),
        )
}
