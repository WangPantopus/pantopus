@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "TooManyFunctions")

package app.pantopus.android.ui.screens.inbox.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.chats.UnifiedConversationDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.chats.ChatRepository
import app.pantopus.android.data.realtime.SocketManager
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel
    @Inject
    constructor(
        private val repo: ChatRepository,
        private val socket: SocketManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)
        val state: StateFlow<ChatListUiState> = _state.asStateFlow()

        private val _activeFilter = MutableStateFlow(ChatFilter.All)
        val activeFilter: StateFlow<ChatFilter> = _activeFilter.asStateFlow()

        private val _unreadByFilter = MutableStateFlow<Map<ChatFilter, Int>>(emptyMap())
        val unreadByFilter: StateFlow<Map<ChatFilter, Int>> = _unreadByFilter.asStateFlow()

        private var allRows: List<ConversationRowContent> = emptyList()
        private val aiRow =
            ConversationRowContent(
                id = "ai_assistant",
                variant = ConversationRowVariant.AiAssistant,
                displayName = "Ask Pantopus",
                initials = "AP",
                avatarUrl = null,
                identityChip = null,
                verified = false,
                preview = "Summaries, drafts, neighborhood help.",
                timeLabel = "now",
                unread = 0,
                pinned = true,
                topicKinds = emptySet(),
            )

        private var badgeJob: Job? = null
        private var messageJob: Job? = null

        fun load() {
            if (_state.value is ChatListUiState.Loaded) return
            fetch()
            subscribeToSockets()
        }

        fun refresh() = fetch()

        fun selectFilter(filter: ChatFilter) {
            if (_activeFilter.value == filter) return
            _activeFilter.value = filter
            applyFilter()
        }

        fun teardown() {
            badgeJob?.cancel()
            messageJob?.cancel()
            badgeJob = null
            messageJob = null
        }

        override fun onCleared() {
            super.onCleared()
            teardown()
        }

        // MARK: - Fetch

        private fun fetch() {
            viewModelScope.launch {
                val conversationsDeferred = async { repo.unifiedConversations() }
                val statsDeferred = async { repo.stats() }
                val conversationsResult = conversationsDeferred.await()
                val statsResult = statsDeferred.await()

                val response =
                    (conversationsResult as? NetworkResult.Success)?.data
                        ?: run {
                            val message =
                                (conversationsResult as? NetworkResult.Failure)
                                    ?.error?.message
                                    ?: "Couldn't load conversations."
                            _state.value = ChatListUiState.Error(message)
                            return@launch
                        }
                val stats = (statsResult as? NetworkResult.Success)?.data?.stats
                val rows = response.conversations.map(::project)
                allRows = rows
                _unreadByFilter.value =
                    mapOf(
                        ChatFilter.All to 0,
                        ChatFilter.Unread to (stats?.totalUnread ?: response.totalUnread ?: rows.sumOf { it.unread }),
                        ChatFilter.Gigs to (stats?.gigChats ?: rows.count { it.topicKinds.contains("gig") }),
                        ChatFilter.Market to rows.count { it.topicKinds.contains("marketplace") },
                    )
                applyFilter()
            }
        }

        private fun applyFilter() {
            val filtered =
                when (_activeFilter.value) {
                    ChatFilter.All -> allRows
                    ChatFilter.Unread -> allRows.filter { it.unread > 0 }
                    ChatFilter.Gigs -> allRows.filter { it.topicKinds.contains("gig") }
                    ChatFilter.Market -> allRows.filter { it.topicKinds.contains("marketplace") }
                }
            if (filtered.isEmpty()) {
                _state.value = ChatListUiState.Empty
                return
            }
            val combined = listOf(aiRow) + filtered
            _state.value =
                ChatListUiState.Loaded(rows = combined.sortedByDescending { it.pinned })
        }

        // MARK: - Realtime

        private fun subscribeToSockets() {
            if (badgeJob == null) {
                badgeJob =
                    viewModelScope.launch {
                        socket.eventsOf("badge:update").collect { json ->
                            val total = json.optInt("total_unread", -1)
                            if (total >= 0) {
                                _unreadByFilter.value =
                                    _unreadByFilter.value.toMutableMap().apply {
                                        this[ChatFilter.Unread] = total
                                    }
                            }
                        }
                    }
            }
            if (messageJob == null) {
                messageJob =
                    viewModelScope.launch {
                        socket.eventsOf("message:new").collect { json ->
                            handleMessage(json)
                        }
                    }
            }
        }

        private fun handleMessage(json: JSONObject) {
            val otherUserId = json.optString("other_user_id").takeIf { it.isNotEmpty() }
            val roomId = json.optString("room_id").takeIf { it.isNotEmpty() }
            val targetId = otherUserId ?: roomId ?: return
            val index = allRows.indexOfFirst { it.id == targetId }
            if (index < 0) {
                fetch()
                return
            }
            val original = allRows[index]
            val unreadFor =
                if (json.has("unread_for")) json.optInt("unread_for") else original.unread + 1
            val preview = json.optString("preview").ifEmpty { original.preview }
            val createdAt = json.optString("created_at").ifEmpty { null }
            val timeLabel = createdAt?.let { relativeTimestamp(it) } ?: "now"
            val updated = original.copy(preview = preview, timeLabel = timeLabel, unread = unreadFor)
            allRows = allRows.toMutableList().also { it[index] = updated }
            applyFilter()
        }

        // MARK: - Projection

        private fun project(dto: UnifiedConversationDto): ConversationRowContent {
            val isRoom = dto.type == "room"
            val name =
                if (isRoom) {
                    dto.roomName ?: "Group"
                } else {
                    dto.otherParticipantName ?: dto.otherParticipantIdentity?.displayName ?: "Pantopus user"
                }
            val rowId =
                if (isRoom) (dto.id ?: dto.gigId ?: dto.homeId ?: name) else (dto.otherParticipantId ?: name)
            val identityKind = dto.otherParticipantIdentity?.identityKind
            val identityChip =
                when {
                    !isRoom && identityKind == "business" -> ConversationIdentityChip.Business
                    !isRoom && identityKind == "home" -> ConversationIdentityChip.Home
                    isRoom && dto.roomType == "home" -> ConversationIdentityChip.Home
                    else -> null
                }
            val variant: ConversationRowVariant =
                if (isRoom) ConversationRowVariant.Group() else ConversationRowVariant.Dm
            val verified = dto.otherParticipantIdentity?.verified == true
            val preview =
                dto.lastMessagePreview ?: if (isRoom) "No messages yet" else "Start the conversation"
            val timeLabel = dto.lastMessageAt?.let { relativeTimestamp(it) } ?: ""
            return ConversationRowContent(
                id = rowId,
                variant = variant,
                displayName = name,
                initials = initials(name),
                avatarUrl = dto.otherParticipantAvatar,
                identityChip = identityChip,
                verified = verified,
                preview = preview,
                timeLabel = timeLabel,
                unread = dto.totalUnread,
                pinned = false,
                topicKinds = dto.topics?.mapNotNull { it.topicType }?.toSet() ?: emptySet(),
            )
        }

        private fun initials(name: String): String {
            val parts = name.split(" ").take(2)
            val result = parts.mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()
            return result.ifEmpty { "?" }
        }

        private fun relativeTimestamp(iso: String): String =
            runCatching {
                val instant = Instant.parse(iso)
                val seconds = Duration.between(instant, Instant.now()).seconds
                when {
                    seconds < 60 -> "now"
                    seconds < 3_600 -> "${seconds / 60}m"
                    seconds < 86_400 -> "${seconds / 3_600}h"
                    seconds < 604_800 -> "${seconds / 86_400}d"
                    else -> "${seconds / 604_800}w"
                }
            }.getOrElse {
                Timber.w("Couldn't parse chat timestamp: $iso")
                ""
            }

        /** Compose-friendly icon for the chat assistant avatar. */
        val assistantIcon: PantopusIcon = PantopusIcon.Info
    }
