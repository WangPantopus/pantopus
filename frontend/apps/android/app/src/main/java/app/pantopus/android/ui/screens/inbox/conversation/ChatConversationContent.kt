@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.inbox.conversation

import androidx.compose.runtime.Immutable
import app.pantopus.android.ui.theme.PantopusIcon

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
}

@Immutable
data class ChatBubbleContent(
    val id: String,
    val side: ChatMessageSide,
    val body: ChatBubbleBody,
    val hasTail: Boolean,
    val stamp: String?,
    val deliveryState: ChatDeliveryState?,
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
