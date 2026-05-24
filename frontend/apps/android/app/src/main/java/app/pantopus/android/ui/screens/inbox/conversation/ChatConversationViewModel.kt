@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "TooManyFunctions", "LongParameterList")

package app.pantopus.android.ui.screens.inbox.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.chats.ChatMessageDto
import app.pantopus.android.data.api.models.chats.ChatMessageSender
import app.pantopus.android.data.api.models.chats.SendChatMessageBody
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.chats.ChatRepository
import app.pantopus.android.data.realtime.SocketManager
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatConversationViewModel
    @Inject
    constructor(
        private val repo: ChatRepository,
        private val socket: SocketManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow<ChatConversationUiState>(ChatConversationUiState.Loading)
        val state: StateFlow<ChatConversationUiState> = _state.asStateFlow()

        private val _counterparty =
            MutableStateFlow<ChatCounterparty>(ChatCounterparty.Person(displayName = "", initials = ""))
        val counterparty: StateFlow<ChatCounterparty> = _counterparty.asStateFlow()

        private val _composerText = MutableStateFlow("")
        val composerText: StateFlow<String> = _composerText.asStateFlow()

        private val _isSending = MutableStateFlow(false)
        val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

        private val _isCounterpartyTyping = MutableStateFlow(false)
        val isCounterpartyTyping: StateFlow<Boolean> = _isCounterpartyTyping.asStateFlow()

        // Timeline row id ("bubble_<messageId>") the screen should scroll
        // to — set once when opened from Chat Search with a matched message
        // present in the loaded page. Cleared via [consumePendingScroll].
        private val _pendingScrollTarget = MutableStateFlow<String?>(null)
        val pendingScrollTarget: StateFlow<String?> = _pendingScrollTarget.asStateFlow()

        // A15.3 capability chips for the AI welcome card (tap-to-send).
        val aiPrompts: List<ChatPromptChip> =
            listOf(
                ChatPromptChip("price", "Price a task", PantopusIcon.Hammer),
                ChatPromptChip("draft", "Draft a Pulse post", PantopusIcon.Pencil),
                ChatPromptChip("mail", "Summarize mail", PantopusIcon.Mailbox),
                ChatPromptChip("neighbor", "Find a neighbor", PantopusIcon.Search),
            )

        val emptyChips: List<ChatPromptChip> =
            listOf(
                ChatPromptChip("intro", "Introduce yourself", PantopusIcon.Hand),
                ChatPromptChip("gig", "Ask about the gig", PantopusIcon.Briefcase),
                ChatPromptChip("listing", "Share a listing", PantopusIcon.Tag),
            )

        private var mode: ChatThreadMode = ChatThreadMode.Ai
        private var currentUserId: String = ""
        private var scrollToMessageId: String? = null
        private var didResolveScrollTarget = false
        private var messages: MutableList<ChatMessageDto> = mutableListOf()
        private val pendingByClientId = LinkedHashMap<String, ChatMessageDto>()
        private val failedClientIds = mutableSetOf<String>()
        private var hasMore = false
        private var oldestCursor: String? = null
        private var markReadJob: Job? = null
        private var messageJob: Job? = null
        private var updateJob: Job? = null
        private var deleteJob: Job? = null
        private var reactJob: Job? = null

        fun configure(
            mode: ChatThreadMode,
            counterparty: ChatCounterparty,
            currentUserId: String,
            scrollToMessageId: String? = null,
        ) {
            this.mode = mode
            this.currentUserId = currentUserId
            this._counterparty.value = counterparty
            this.scrollToMessageId = scrollToMessageId
        }

        /** Clear the scroll target after the screen has scrolled to it. */
        fun consumePendingScroll() {
            _pendingScrollTarget.value = null
        }

        fun load() {
            if (_state.value is ChatConversationUiState.Loaded) return
            fetch(initial = true)
            subscribeToSockets()
        }

        fun refresh() = fetch(initial = true)

        fun loadOlder() {
            if (!hasMore) return
            val cursor = oldestCursor ?: return
            fetch(initial = false, before = cursor)
        }

        fun setComposerText(value: String) {
            _composerText.value = value
        }

        fun tapPrompt(chip: ChatPromptChip) {
            _composerText.value = chip.label
        }

        /**
         * Tap a welcome-card capability chip — send its label as the
         * thread's first message (transitions the AI thread out of the
         * welcome/empty state).
         */
        fun sendCapabilityPrompt(chip: ChatPromptChip) {
            _composerText.value = chip.label
            send()
        }

        fun send() {
            val trimmed = _composerText.value.trim()
            if (trimmed.isEmpty() || _isSending.value) return
            _composerText.value = ""
            _isSending.value = true

            viewModelScope.launch {
                try {
                    if (mode is ChatThreadMode.Ai) {
                        // AI thread: no backend wiring in T2.2 — push
                        // a local optimistic row so the composer feels
                        // alive. SSE streaming lands later.
                        val pending = optimistic(text = trimmed, roomId = "ai")
                        pendingByClientId[pending.clientMessageId ?: pending.id] = pending
                        rebuild()
                        return@launch
                    }
                    val roomId = resolveRoomId() ?: return@launch
                    val pending = optimistic(text = trimmed, roomId = roomId)
                    pendingByClientId[pending.clientMessageId ?: pending.id] = pending
                    rebuild()
                    val result =
                        repo.sendMessage(
                            SendChatMessageBody(
                                roomId = roomId,
                                messageText = trimmed,
                                messageType = "text",
                                clientMessageId = pending.clientMessageId,
                            ),
                        )
                    when (result) {
                        is NetworkResult.Success -> {
                            val clientId = pending.clientMessageId
                            if (clientId != null) pendingByClientId.remove(clientId)
                            messages.add(result.data.message)
                            rebuild()
                            scheduleMarkRead()
                        }
                        is NetworkResult.Failure -> {
                            pending.clientMessageId?.let { failedClientIds.add(it) }
                            rebuild()
                            Timber.w("chat send failed: ${result.error.message}")
                        }
                    }
                } finally {
                    _isSending.value = false
                }
            }
        }

        fun retry(clientId: String) {
            val pending = pendingByClientId[clientId] ?: return
            val text = pending.messageText ?: return
            failedClientIds.remove(clientId)
            pendingByClientId.remove(clientId)
            _composerText.value = text
            rebuild()
            send()
        }

        fun react(
            messageId: String,
            reaction: String,
        ) {
            viewModelScope.launch {
                when (val result = repo.reactToMessage(messageId, reaction)) {
                    is NetworkResult.Failure -> Timber.w("chat react failed: ${result.error.message}")
                    else -> Unit
                }
            }
        }

        fun scheduleMarkRead() {
            markReadJob?.cancel()
            markReadJob =
                viewModelScope.launch {
                    delay(600)
                    when (val target = mode) {
                        is ChatThreadMode.Room -> repo.markRoomRead(target.id)
                        is ChatThreadMode.Person -> repo.markConversationRead(target.otherUserId)
                        ChatThreadMode.Ai -> Unit
                    }
                }
        }

        fun teardown() {
            markReadJob?.cancel()
            messageJob?.cancel()
            updateJob?.cancel()
            deleteJob?.cancel()
            reactJob?.cancel()
            markReadJob = null
            messageJob = null
            updateJob = null
            deleteJob = null
            reactJob = null
        }

        override fun onCleared() {
            super.onCleared()
            teardown()
        }

        // MARK: - Fetch

        private fun fetch(
            initial: Boolean,
            before: String? = null,
        ) {
            viewModelScope.launch {
                if (initial) {
                    _state.value = ChatConversationUiState.Loading
                    messages = mutableListOf()
                    pendingByClientId.clear()
                    failedClientIds.clear()
                    oldestCursor = null
                    hasMore = false
                }
                if (mode is ChatThreadMode.Ai) {
                    _state.value = ChatConversationUiState.Empty
                    return@launch
                }
                val response =
                    when (val target = mode) {
                        is ChatThreadMode.Room -> repo.roomMessages(target.id, before)
                        is ChatThreadMode.Person -> repo.conversationMessages(target.otherUserId, before)
                        ChatThreadMode.Ai -> return@launch
                    }
                when (response) {
                    is NetworkResult.Success -> {
                        val ordered = response.data.messages.asReversed()
                        messages.addAll(0, ordered)
                        hasMore = response.data.hasMore ?: false
                        oldestCursor = messages.firstOrNull()?.createdAt
                        rebuild()
                        scheduleMarkRead()
                    }
                    is NetworkResult.Failure -> {
                        _state.value = ChatConversationUiState.Error(response.error.message)
                    }
                }
            }
        }

        private fun resolveRoomId(): String? =
            when (val target = mode) {
                is ChatThreadMode.Room -> target.id
                is ChatThreadMode.Person -> messages.firstOrNull()?.roomId ?: target.otherUserId
                ChatThreadMode.Ai -> null
            }

        // MARK: - Realtime

        private fun subscribeToSockets() {
            if (messageJob == null) {
                messageJob =
                    viewModelScope.launch {
                        socket.eventsOf("message:new").collect { handleIncoming(it) }
                    }
            }
            if (updateJob == null) {
                updateJob =
                    viewModelScope.launch {
                        socket.eventsOf("messageUpdated").collect { handleUpdate(it) }
                    }
            }
            if (deleteJob == null) {
                deleteJob =
                    viewModelScope.launch {
                        socket.eventsOf("messageDeleted").collect { handleDelete(it) }
                    }
            }
            if (reactJob == null) {
                reactJob =
                    viewModelScope.launch {
                        socket.eventsOf("message:react").collect { handleReaction(it) }
                    }
            }
        }

        /**
         * Reactions don't change the message body — trigger a re-fetch
         * so the server-canonical count lands in the projection.
         */
        private fun handleReaction(json: JSONObject) {
            val id =
                json.optString("message_id").takeIf { it.isNotEmpty() }
                    ?: json.optString("messageId").takeIf { it.isNotEmpty() } ?: return
            if (messages.none { it.id == id }) return
            fetch(initial = true)
        }

        private fun handleIncoming(json: JSONObject) {
            val clientId = json.optString("client_message_id").takeIf { it.isNotEmpty() }
            if (clientId != null && pendingByClientId.containsKey(clientId)) {
                pendingByClientId.remove(clientId)
                fetch(initial = true)
                return
            }
            fetch(initial = true)
        }

        private fun handleUpdate(json: JSONObject) {
            val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return
            if (messages.none { it.id == id }) return
            fetch(initial = true)
        }

        private fun handleDelete(json: JSONObject) {
            val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return
            val index = messages.indexOfFirst { it.id == id }
            if (index >= 0) {
                messages.removeAt(index)
                rebuild()
            }
        }

        // MARK: - Projection

        private fun rebuild() {
            val combined = messages + pendingByClientId.values
            if (combined.isEmpty()) {
                _state.value = ChatConversationUiState.Empty
                return
            }
            val rows = mutableListOf<ChatTimelineRow>()
            var lastDayKey: String? = null
            combined.forEachIndexed { index, message ->
                val dayKey = dayKey(message.createdAt)
                if (dayKey != lastDayKey) {
                    rows.add(ChatTimelineRow.DayDivider(ChatDayDivider(id = dayKey, label = dayLabel(message.createdAt))))
                    lastDayKey = dayKey
                }
                if (message.messageType == "broadcast_reference") {
                    rows.add(ChatTimelineRow.BroadcastReference(broadcastReferenceOf(message)))
                    return@forEachIndexed
                }
                val side = if (message.userId == currentUserId) ChatMessageSide.Outgoing else ChatMessageSide.Incoming
                val nextSameSide =
                    index + 1 < combined.size &&
                        combined[index + 1].userId == message.userId &&
                        dayKey(combined[index + 1].createdAt) == dayKey
                val hasTail = !nextSameSide
                val stamp = if (hasTail) stampLabel(message) else null
                val deliveryState =
                    if (side != ChatMessageSide.Outgoing) {
                        null
                    } else if (message.clientMessageId != null && failedClientIds.contains(message.clientMessageId)) {
                        ChatDeliveryState.Failed
                    } else if (message.id.startsWith("client_")) {
                        ChatDeliveryState.Sending
                    } else if (message.readAt != null) {
                        ChatDeliveryState.Read
                    } else {
                        ChatDeliveryState.Delivered
                    }
                val body = bodyOf(message)
                rows.add(
                    ChatTimelineRow.Bubble(
                        ChatBubbleContent(
                            id = message.id,
                            side = side,
                            body = body,
                            hasTail = hasTail,
                            stamp = stamp,
                            deliveryState = deliveryState,
                        ),
                    ),
                )
            }
            _state.value = ChatConversationUiState.Loaded(rows)
            resolveScrollTarget(rows)
        }

        /**
         * Resolve the Chat Search scroll target once: if the deep-linked
         * message is in the loaded page, publish its row id for the screen
         * to scroll to. The match is guaranteed present because search
         * indexes the same most-recent page the conversation loads.
         */
        private fun resolveScrollTarget(rows: List<ChatTimelineRow>) {
            if (didResolveScrollTarget) return
            val target = scrollToMessageId ?: return
            val rowId = "bubble_$target"
            if (rows.none { it.rowId == rowId }) return
            didResolveScrollTarget = true
            _pendingScrollTarget.value = rowId
        }

        private fun bodyOf(message: ChatMessageDto): ChatBubbleBody =
            when (message.messageType) {
                "image" -> ChatBubbleBody.Image(message.metadata?.get("image_url") as? String)
                "file", "audio" ->
                    ChatBubbleBody.Attachment(
                        filename = (message.metadata?.get("filename") as? String) ?: "Attachment",
                    )
                "gig_offer" ->
                    ChatBubbleBody.SystemLink(
                        label = "Shared gig ·",
                        sub = (message.metadata?.get("title") as? String) ?: "Shared gig",
                        accent = ChatSystemLinkAccent.Primary,
                    )
                "listing_offer" ->
                    ChatBubbleBody.SystemLink(
                        label = "Shared listing ·",
                        sub = (message.metadata?.get("title") as? String) ?: "Shared listing",
                        accent = ChatSystemLinkAccent.Success,
                    )
                "location" ->
                    ChatBubbleBody.SystemLink(
                        label = "Location ·",
                        sub = (message.metadata?.get("address") as? String) ?: "Pinned location",
                        accent = ChatSystemLinkAccent.Warning,
                    )
                else -> ChatBubbleBody.Text(message.messageText.orEmpty())
            }

        private fun broadcastReferenceOf(message: ChatMessageDto): ChatBroadcastReference =
            ChatBroadcastReference(
                id = message.id,
                title = (message.metadata?.get("title") as? String) ?: "Broadcast referenced",
                subtitle =
                    (message.metadata?.get("subtitle") as? String)
                        ?: "This conversation started from a creator broadcast.",
                metric = (message.metadata?.get("metric") as? String) ?: "Audience update",
            )

        private fun optimistic(
            text: String,
            roomId: String,
        ): ChatMessageDto {
            val clientId = "client_${UUID.randomUUID()}"
            return ChatMessageDto(
                id = clientId,
                roomId = roomId,
                userId = currentUserId,
                messageText = text,
                messageType = "text",
                clientMessageId = clientId,
                createdAt = Instant.now().toString(),
                sender =
                    ChatMessageSender(
                        id = currentUserId,
                        username = null,
                        name = null,
                        profilePictureUrl = null,
                    ),
            )
        }

        private fun dayKey(iso: String): String =
            runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate().toString() }
                .getOrDefault(iso)

        private fun dayLabel(iso: String): String =
            runCatching {
                val date = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
                val today = java.time.LocalDate.now()
                when (date) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    else -> date.format(DateTimeFormatter.ofPattern("MMM d")).uppercase()
                }
            }.getOrDefault(iso)

        private fun stampLabel(message: ChatMessageDto): String? =
            runCatching {
                val time =
                    Instant
                        .parse(message.createdAt)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("h:mm a"))
                if (message.userId == currentUserId) {
                    time
                } else {
                    val sender = message.sender?.name?.takeIf { it.isNotEmpty() }
                    sender?.let { "$it · $time" } ?: time
                }
            }.getOrNull()
    }
