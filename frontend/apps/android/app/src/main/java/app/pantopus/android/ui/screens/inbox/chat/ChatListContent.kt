@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.inbox.chat

import androidx.compose.runtime.Immutable

/** Filter-tab key. */
enum class ChatFilter(val key: String, val label: String) {
    All("all", "All"),
    Unread("unread", "Unread"),
    Gigs("gigs", "Gigs"),
    Market("market", "Market"),
    ;

    companion object {
        fun fromKey(key: String): ChatFilter = entries.firstOrNull { it.key == key } ?: All
    }
}

/** Variant for the per-row avatar treatment. */
sealed interface ConversationRowVariant {
    data object Dm : ConversationRowVariant

    data class Group(
        val extraAvatars: List<String> = emptyList(),
        val extraCount: Int = 0,
    ) : ConversationRowVariant

    data object AiAssistant : ConversationRowVariant
}

/** Identity disclosure chip rendered next to the name. */
enum class ConversationIdentityChip(val label: String) {
    Business("Business"),
    Home("Home"),
}

/** Render-only content for one row. */
@Immutable
data class ConversationRowContent(
    val id: String,
    val variant: ConversationRowVariant,
    val displayName: String,
    val initials: String,
    val avatarUrl: String?,
    val identityChip: ConversationIdentityChip?,
    val verified: Boolean,
    val preview: String,
    val timeLabel: String,
    val unread: Int,
    val pinned: Boolean,
    val topicKinds: Set<String>,
)

/** Filter-tab entry the view renders. */
@Immutable
data class ChatFilterTab(
    val filter: ChatFilter,
    val badgeCount: Int? = null,
)

/** Top-level render state for the chat list. */
sealed interface ChatListUiState {
    data object Loading : ChatListUiState

    data object Empty : ChatListUiState

    data class Loaded(val rows: List<ConversationRowContent>) : ChatListUiState

    data class Error(val message: String) : ChatListUiState
}
