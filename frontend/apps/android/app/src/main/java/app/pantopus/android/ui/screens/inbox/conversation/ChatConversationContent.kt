@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.inbox.conversation

import androidx.compose.runtime.Immutable
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * Presentation mode for the conversation surface. Orthogonal to
 * [ChatCounterparty] (who you're talking to) — `mode` drives the chrome
 * (avatar treatment, empty/welcome state, bubble shapes). [Dm] is the
 * default human DM/group thread; [AiAssistant] is the Pantopus AI thread;
 * [CreatorThread] / [FanThread] land in Wave D.
 */
enum class ChatConversationMode { Dm, AiAssistant, CreatorThread, FanThread }

/** Counterparty type — drives the header swap + empty-state copy. */
sealed interface ChatCounterparty {
    val displayName: String

    data class Person(
        override val displayName: String,
        val initials: String,
        val locality: String? = null,
        val verified: Boolean = false,
        val online: Boolean = false,
    ) : ChatCounterparty

    data class Group(
        override val displayName: String,
        val memberCount: Int? = null,
    ) : ChatCounterparty

    data class Ai(
        override val displayName: String,
    ) : ChatCounterparty
}

/** Source-of-truth identifier for the thread. */
sealed interface ChatThreadMode {
    data class Room(val id: String) : ChatThreadMode

    data class Person(val otherUserId: String) : ChatThreadMode

    data object Ai : ChatThreadMode
}

enum class ChatMessageSide { Incoming, Outgoing }

enum class ChatDeliveryState { Sending, Failed, Delivered, Read }

enum class ChatSystemLinkAccent { Primary, Success, Warning, Error }

enum class ChatQueuedAttachmentKind { Image, Document }

@Immutable
data class ChatQueuedAttachment(
    val id: String,
    val kind: ChatQueuedAttachmentKind,
    val filename: String,
)

/**
 * Inline "this would cost about $X" estimate rendered inside an AI reply
 * bubble (`AiEstimateCard`).
 */
@Immutable
data class ChatEstimate(
    val amount: String,
    val basis: String,
    val confidence: String,
)

/** Body of a single bubble. */
sealed interface ChatBubbleBody {
    data class Text(val text: String) : ChatBubbleBody

    data class Image(val url: String?) : ChatBubbleBody

    data class Attachment(
        val filename: String,
        val sizeLabel: String? = null,
    ) : ChatBubbleBody

    data class SystemLink(
        val label: String,
        val sub: String,
        val accent: ChatSystemLinkAccent,
    ) : ChatBubbleBody

    /**
     * Structured AI reply: prose plus an optional inline estimate card.
     * Renders wider than a plain bubble with a "Pantopus AI" tag
     * (`AiAssistant` mode only).
     */
    data class AiReply(
        val text: String,
        val estimate: ChatEstimate?,
    ) : ChatBubbleBody
}

@Immutable
data class ChatBubbleContent(
    val id: String,
    val side: ChatMessageSide,
    val body: ChatBubbleBody,
    val hasTail: Boolean,
    val stamp: String?,
    val deliveryState: ChatDeliveryState?,
    val isContinuation: Boolean = false,
)

@Immutable
data class ChatDayDivider(
    val id: String,
    val label: String,
)

sealed interface ChatTimelineRow {
    val rowId: String

    data class DayDivider(val divider: ChatDayDivider) : ChatTimelineRow {
        override val rowId: String = "divider_${divider.id}"
    }

    data class Bubble(val content: ChatBubbleContent) : ChatTimelineRow {
        override val rowId: String = "bubble_${content.id}"
    }
}

@Immutable
data class ChatPromptChip(
    val id: String,
    val label: String,
    val icon: PantopusIcon,
)

sealed interface ChatConversationUiState {
    data object Loading : ChatConversationUiState

    data object Empty : ChatConversationUiState

    data class Loaded(val rows: List<ChatTimelineRow>) : ChatConversationUiState

    data class Error(val message: String) : ChatConversationUiState
}
