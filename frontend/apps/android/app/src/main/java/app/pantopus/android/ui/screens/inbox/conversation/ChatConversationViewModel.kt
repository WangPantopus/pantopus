@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "TooManyFunctions", "LongParameterList")

package app.pantopus.android.ui.screens.inbox.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.ai.AIChatRepository
import app.pantopus.android.data.ai.AIChatStreamEvent
import app.pantopus.android.data.ai.AIConversationSession
import app.pantopus.android.data.api.models.chats.ChatMessageDto
import app.pantopus.android.data.api.models.chats.ChatMessageSender
import app.pantopus.android.data.api.models.chats.ChatMessagesResponse
import app.pantopus.android.data.api.models.chats.FindOrCreateTopicBody
import app.pantopus.android.data.api.models.chats.SendChatMessageBody
import app.pantopus.android.data.api.models.chats.resolvedText
import app.pantopus.android.data.api.models.chats.resolvedType
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.services.GeoApi
import app.pantopus.android.data.blocks.BlocksRepository
import app.pantopus.android.data.chats.ChatRepository
import app.pantopus.android.data.gigs.GigsRepository
import app.pantopus.android.data.listings.ListingsRepository
import app.pantopus.android.data.location.LocationProvider
import app.pantopus.android.data.realtime.SocketManager
import app.pantopus.android.data.upload.UploadFile
import app.pantopus.android.data.upload.UploadRepository
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatConversationViewModel
    @Inject
    constructor(
        private val repo: ChatRepository,
        private val socket: SocketManager,
        private val uploadRepo: UploadRepository,
        private val aiRepo: AIChatRepository,
        private val gigsRepo: GigsRepository,
        private val listingsRepo: ListingsRepository,
        private val geoApi: GeoApi,
        private val locationProvider: LocationProvider,
        private val blocksRepo: BlocksRepository,
        private val aiSession: AIConversationSession,
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

        private val _replyingTo = MutableStateFlow<ChatReplyPreview?>(null)
        val replyingTo: StateFlow<ChatReplyPreview?> = _replyingTo.asStateFlow()

        private val _editingMessageId = MutableStateFlow<String?>(null)
        val editingMessageId: StateFlow<String?> = _editingMessageId.asStateFlow()

        private val _topics = MutableStateFlow<List<ChatConversationTopic>>(emptyList())
        val topics: StateFlow<List<ChatConversationTopic>> = _topics.asStateFlow()

        private val _selectedTopicId = MutableStateFlow<String?>(null)
        val selectedTopicId: StateFlow<String?> = _selectedTopicId.asStateFlow()

        private val _isCounterpartyTyping = MutableStateFlow(false)
        val isCounterpartyTyping: StateFlow<Boolean> = _isCounterpartyTyping.asStateFlow()

        private val _queuedAttachments = MutableStateFlow<List<ChatQueuedAttachment>>(emptyList())
        val queuedAttachments: StateFlow<List<ChatQueuedAttachment>> = _queuedAttachments.asStateFlow()

        private val _shareableGigs = MutableStateFlow<List<ChatShareGigOption>>(emptyList())
        val shareableGigs: StateFlow<List<ChatShareGigOption>> = _shareableGigs.asStateFlow()

        private val _shareableListings = MutableStateFlow<List<ChatShareListingOption>>(emptyList())
        val shareableListings: StateFlow<List<ChatShareListingOption>> = _shareableListings.asStateFlow()

        private val _isLoadingShareOptions = MutableStateFlow(false)
        val isLoadingShareOptions: StateFlow<Boolean> = _isLoadingShareOptions.asStateFlow()

        private val _shareOptionsError = MutableStateFlow<String?>(null)
        val shareOptionsError: StateFlow<String?> = _shareOptionsError.asStateFlow()

        private val _isSharingLocation = MutableStateFlow(false)
        val isSharingLocation: StateFlow<Boolean> = _isSharingLocation.asStateFlow()

        // Timeline row id ("bubble_<messageId>") the screen should scroll
        // to — set once when opened from Chat Search with a matched message
        // present in the loaded page. Cleared via [consumePendingScroll].
        private val _pendingScrollTarget = MutableStateFlow<String?>(null)
        val pendingScrollTarget: StateFlow<String?> = _pendingScrollTarget.asStateFlow()

        // Pre-bid gig-room send limit notice (429 PRE_BID_LIMIT). Rendered
        // as a dismissible banner above the composer; cleared on the next
        // successful send or via [dismissSendLimitNotice].
        private val _sendLimitNotice = MutableStateFlow<String?>(null)
        val sendLimitNotice: StateFlow<String?> = _sendLimitNotice.asStateFlow()

        private val _isSelectionMode = MutableStateFlow(false)
        val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

        private val _selectedMessageIds = MutableStateFlow<Set<String>>(emptySet())
        val selectedMessageIds: StateFlow<Set<String>> = _selectedMessageIds.asStateFlow()

        private val _isBlocking = MutableStateFlow(false)
        val isBlocking: StateFlow<Boolean> = _isBlocking.asStateFlow()

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
        private var initialTopic: ChatInitialTopic? = null
        private var currentUserId: String = ""
        private var scrollToMessageId: String? = null
        private var didResolveScrollTarget = false
        private var messages: MutableList<ChatMessageDto> = mutableListOf()
        private val pendingByClientId = LinkedHashMap<String, ChatMessageDto>()
        private val failedClientIds = mutableSetOf<String>()
        private var hasMore = false
        private var oldestCursor: String? = null
        private var activeRoomIds: Set<String> = emptySet()
        private val joinedRoomIds = mutableSetOf<String>()

        // Person-mode direct room — resolved once via `POST /api/chat/direct`
        // (find-or-create, idempotent server-side) and cached for the VM's
        // lifetime. Person threads must always send here, never to a shared
        // gig/group room surfaced by the aggregated fetch.
        private var directRoomId: String? = null

        // In-flight direct-room resolution, shared so concurrent sends make
        // a single `POST /api/chat/direct`.
        private var directRoomDeferred: Deferred<String?>? = null

        // Send inputs captured per optimistic row, keyed by its bare-UUID
        // `clientMessageId`. Survives refetches so retry resends the same
        // payload under the same idempotency key.
        private val sendContextsByClientId = LinkedHashMap<String, PendingSendContext>()

        /**
         * Inputs needed to (re)send one optimistic message. Captured at
         * send time so retry doesn't re-read mutated composer state.
         *
         * @property fileIds uploaded attachment ids — set after the first
         *   successful upload so a retry after a failed POST doesn't
         *   re-upload.
         * @property messageType explicit type for rich sends (`gig_offer`,
         *   `location`, …). `null` for composer sends, which infer
         *   `text` / `file` and are the only sends that carry the queued
         *   composer attachments.
         */
        private data class PendingSendContext(
            val text: String,
            val fileIds: List<String>? = null,
            val replyToId: String? = null,
            val topicId: String? = null,
            val messageType: String? = null,
            val metadata: Map<String, Any>? = null,
        )

        private var aiConversationId: String? = null
        private val aiDraftsByMessageId = mutableMapOf<String, List<ChatAIDraftCard>>()
        private var markReadJob: Job? = null
        private var connectionJob: Job? = null
        private var messageJob: Job? = null
        private var updateJob: Job? = null
        private var deleteJob: Job? = null
        private var reactJob: Job? = null

        fun configure(
            mode: ChatThreadMode,
            counterparty: ChatCounterparty,
            currentUserId: String,
            scrollToMessageId: String? = null,
            initialTopic: ChatInitialTopic? = null,
        ) {
            this.mode = mode
            this.currentUserId = currentUserId
            this._counterparty.value = counterparty
            this.scrollToMessageId = scrollToMessageId
            this.initialTopic = initialTopic
        }

        /** Clear the scroll target after the screen has scrolled to it. */
        fun consumePendingScroll() {
            _pendingScrollTarget.value = null
        }

        fun load() {
            if (_state.value is ChatConversationUiState.Loaded) return
            // Seed AI continuity: a new VM instance for the AI thread keeps
            // appending to the conversation the app session already opened.
            if (mode is ChatThreadMode.Ai && aiConversationId == null) {
                aiConversationId = aiSession.conversationId
            }
            loadTopicsIfNeeded()
            fetch(initial = true)
            subscribeToSockets()
        }

        fun refresh() {
            loadTopicsIfNeeded()
            fetch(initial = true)
        }

        fun selectTopic(topicId: String?) {
            _selectedTopicId.value = if (_selectedTopicId.value == topicId) null else topicId
            fetch(initial = true)
        }

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

        fun queueSamplePhotoAttachment() {
            appendQueuedAttachment(
                ChatQueuedAttachment("queued_photo", ChatQueuedAttachmentKind.Image, "shelves.jpg", "image/jpeg"),
            )
        }

        fun queueSampleDocumentAttachment() {
            appendQueuedAttachment(
                ChatQueuedAttachment("queued_pdf", ChatQueuedAttachmentKind.Document, "shelf.pdf", "application/pdf"),
            )
        }

        fun queueSampleAttachments() {
            queueSamplePhotoAttachment()
            queueSampleDocumentAttachment()
        }

        fun removeQueuedAttachment(id: String) {
            _queuedAttachments.value = _queuedAttachments.value.filterNot { it.id == id }
        }

        fun queueAttachment(
            kind: ChatQueuedAttachmentKind,
            filename: String,
            mimeType: String,
            bytes: ByteArray,
        ) {
            appendQueuedAttachment(
                ChatQueuedAttachment(
                    id = UUID.randomUUID().toString(),
                    kind = kind,
                    filename = filename,
                    mimeType = mimeType,
                    bytes = bytes,
                ),
            )
        }

        private fun appendQueuedAttachment(attachment: ChatQueuedAttachment) {
            if (_queuedAttachments.value.any { it.id == attachment.id }) return
            _queuedAttachments.value = (_queuedAttachments.value + attachment).take(5)
        }

        fun loadShareableGigs() {
            viewModelScope.launch {
                _isLoadingShareOptions.value = true
                _shareOptionsError.value = null
                when (val result = gigsRepo.myGigs()) {
                    is NetworkResult.Success ->
                        _shareableGigs.value =
                            result.data.gigs.map {
                                ChatShareGigOption(
                                    id = it.id,
                                    title = it.title,
                                    category = it.category,
                                    price = it.price,
                                    status = it.status,
                                )
                            }
                    is NetworkResult.Failure -> {
                        _shareOptionsError.value = result.error.message
                        _shareableGigs.value = emptyList()
                    }
                }
                _isLoadingShareOptions.value = false
            }
        }

        fun loadShareableListings() {
            viewModelScope.launch {
                _isLoadingShareOptions.value = true
                _shareOptionsError.value = null
                when (val result = listingsRepo.myListings(limit = 100)) {
                    is NetworkResult.Success ->
                        _shareableListings.value =
                            result.data.listings.mapNotNull { listing ->
                                val title = listing.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                ChatShareListingOption(
                                    id = listing.id,
                                    title = title,
                                    category = listing.category,
                                    price = listing.price,
                                    isFree = listing.isFree == true,
                                    condition = listing.condition,
                                    imageUrl = listing.firstImage ?: listing.mediaUrls?.firstOrNull(),
                                )
                            }
                    is NetworkResult.Failure -> {
                        _shareOptionsError.value = result.error.message
                        _shareableListings.value = emptyList()
                    }
                }
                _isLoadingShareOptions.value = false
            }
        }

        fun sendCurrentLocation() {
            if (_isSharingLocation.value || _isSending.value) return
            viewModelScope.launch {
                _isSharingLocation.value = true
                try {
                    val coordinate = locationProvider.requestCurrent() ?: return@launch
                    var address =
                        String.format(
                            Locale.US,
                            "%.2f, %.2f",
                            coordinate.latitude,
                            coordinate.longitude,
                        )
                    try {
                        val response = geoApi.reverse(coordinate.latitude, coordinate.longitude)
                        val locality = response.normalized.localityLabel
                        if (locality.isNotEmpty()) address = locality
                    } catch (_: Exception) {
                        // keep coordinate fallback
                    }
                    sendRichMessage(
                        messageText = address,
                        messageType = "location",
                        metadata =
                            mapOf(
                                "latitude" to coordinate.latitude,
                                "longitude" to coordinate.longitude,
                                "address" to address,
                            ),
                    )
                } finally {
                    _isSharingLocation.value = false
                }
            }
        }

        fun sendGigOffer(gig: ChatShareGigOption) {
            val metadata =
                buildMap<String, Any> {
                    put("gigId", gig.id)
                    put("title", gig.title)
                    gig.category?.let { put("category", it) }
                    gig.price?.let { put("price", it) }
                    gig.status?.let { put("status", it) }
                }
            sendRichMessage(
                messageText = gig.title,
                messageType = "gig_offer",
                metadata = metadata,
                topicForPerson = Triple("task", gig.id, gig.title),
            )
        }

        fun sendListingOffer(listing: ChatShareListingOption) {
            val metadata =
                buildMap<String, Any> {
                    put("listingId", listing.id)
                    put("title", listing.title)
                    put("isFree", listing.isFree)
                    listing.category?.let { put("category", it) }
                    listing.price?.let { put("price", it) }
                    listing.condition?.let { put("condition", it) }
                    listing.imageUrl?.let { put("imageUrl", it) }
                }
            sendRichMessage(
                messageText = listing.title,
                messageType = "listing_offer",
                metadata = metadata,
                topicForPerson = Triple("listing", listing.id, listing.title),
            )
        }

        private fun sendRichMessage(
            messageText: String,
            messageType: String,
            metadata: Map<String, Any>,
            topicForPerson: Triple<String, String, String>? = null,
        ) {
            if (mode is ChatThreadMode.Ai || _isSending.value) return
            _isSending.value = true
            viewModelScope.launch {
                try {
                    val topicId = resolveTopicIdForShare(topicForPerson)
                    val clientId = newClientMessageId()
                    val pending =
                        optimisticRich(
                            text = messageText,
                            clientId = clientId,
                            roomId = knownRoomIdHint(),
                            messageType = messageType,
                            metadata = metadata,
                        )
                    pendingByClientId[clientId] = pending
                    sendContextsByClientId[clientId] =
                        PendingSendContext(
                            text = messageText,
                            replyToId = _replyingTo.value?.messageId,
                            topicId = topicId,
                            messageType = messageType,
                            metadata = metadata,
                        )
                    rebuild()
                    val succeeded = performSend(clientId)
                    if (succeeded && topicForPerson != null) loadTopicsIfNeeded()
                } finally {
                    _isSending.value = false
                }
            }
        }

        private suspend fun resolveTopicIdForShare(topic: Triple<String, String, String>?): String? {
            if (topic == null) return _selectedTopicId.value
            val target = mode as? ChatThreadMode.Person ?: return _selectedTopicId.value
            return when (
                val result =
                    repo.findOrCreateTopic(
                        target.otherUserId,
                        FindOrCreateTopicBody(
                            topicType = topic.first,
                            topicRefId = topic.second,
                            title = topic.third,
                        ),
                    )
            ) {
                is NetworkResult.Success -> {
                    _selectedTopicId.value = result.data.topic.id
                    result.data.topic.id
                }
                is NetworkResult.Failure -> {
                    Timber.w("find/create share topic failed: ${result.error.message}")
                    _selectedTopicId.value
                }
            }
        }

        private fun optimisticRich(
            text: String,
            clientId: String,
            roomId: String,
            messageType: String,
            metadata: Map<String, Any>,
        ): ChatMessageDto =
            ChatMessageDto(
                id = "client_$clientId",
                roomId = roomId,
                userId = currentUserId,
                messageText = text,
                messageType = messageType,
                metadata = metadata,
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
            val hasQueuedFiles = _queuedAttachments.value.any { it.bytes != null }
            if ((trimmed.isEmpty() && !hasQueuedFiles) || _isSending.value) return
            _composerText.value = ""
            _isSending.value = true

            viewModelScope.launch {
                try {
                    val editingId = _editingMessageId.value
                    if (editingId != null) {
                        when (val result = repo.editMessage(editingId, trimmed)) {
                            is NetworkResult.Success -> {
                                applyUpdatedMessage(result.data.message)
                                cancelMessageAction()
                            }
                            is NetworkResult.Failure -> {
                                _composerText.value = trimmed
                                Timber.w("chat edit failed: ${result.error.message}")
                            }
                        }
                        return@launch
                    }
                    if (mode is ChatThreadMode.Ai) {
                        streamAiMessage(trimmed)
                        return@launch
                    }
                    val clientId = newClientMessageId()
                    val pending =
                        optimistic(
                            text = trimmed.ifEmpty { "Attachment" },
                            clientId = clientId,
                            roomId = knownRoomIdHint(),
                        )
                    pendingByClientId[clientId] = pending
                    sendContextsByClientId[clientId] =
                        PendingSendContext(
                            text = trimmed,
                            replyToId = _replyingTo.value?.messageId,
                            topicId = _selectedTopicId.value,
                        )
                    rebuild()
                    performSend(clientId)
                } finally {
                    _isSending.value = false
                }
            }
        }

        /**
         * Retry a failed optimistic send. Accepts the timeline row id
         * (`client_<uuid>`) or the bare client id, and resends with the
         * same `clientMessageId` — the backend dedups on it, so a retry
         * after a lost response can't double-post.
         */
        fun retry(clientId: String) {
            val bareId = clientId.removePrefix("client_")
            if (!pendingByClientId.containsKey(bareId)) return
            if (!sendContextsByClientId.containsKey(bareId)) return
            if (_isSending.value) return
            failedClientIds.remove(bareId)
            _isSending.value = true
            viewModelScope.launch {
                try {
                    rebuild()
                    performSend(bareId)
                } finally {
                    _isSending.value = false
                }
            }
        }

        /**
         * Shared send/retry tail. Resolves the destination room (creating
         * the direct room on a person thread's first message), uploads the
         * composer's queued attachments at most once, and POSTs under the
         * pending row's idempotency key.
         */
        private suspend fun performSend(clientId: String): Boolean {
            var context = sendContextsByClientId[clientId] ?: return false
            val roomId = ensureRoomId()
            if (roomId == null) {
                failedClientIds.add(clientId)
                rebuild()
                return false
            }
            if (context.messageType == null && context.fileIds == null) {
                context =
                    try {
                        context.copy(fileIds = uploadQueuedAttachmentsIfNeeded(roomId))
                    } catch (error: IllegalStateException) {
                        Timber.w(error, "chat attachment upload failed")
                        failedClientIds.add(clientId)
                        rebuild()
                        return false
                    }
                sendContextsByClientId[clientId] = context
            }
            val fileIds = context.fileIds.orEmpty()
            val result =
                repo.sendMessage(
                    SendChatMessageBody(
                        roomId = roomId,
                        messageText = context.text.ifEmpty { null },
                        messageType = context.messageType ?: if (fileIds.isEmpty()) "text" else "file",
                        fileIds = fileIds.ifEmpty { null },
                        clientMessageId = clientId,
                        replyToId = sanitizedReplyToId(context.replyToId, roomId),
                        topicId = context.topicId,
                        metadata = context.metadata,
                    ),
                )
            return when (result) {
                is NetworkResult.Success -> {
                    // Swap optimistic → server message. The socket echo may
                    // have landed it via a refetch already, so replace-by-id.
                    pendingByClientId.remove(clientId)
                    sendContextsByClientId.remove(clientId)
                    failedClientIds.remove(clientId)
                    upsert(result.data.message)
                    _replyingTo.value = null
                    _sendLimitNotice.value = null
                    if (context.messageType == null) _queuedAttachments.value = emptyList()
                    rebuild()
                    scheduleMarkRead()
                    true
                }
                is NetworkResult.Failure -> {
                    if (isPreBidLimit(result.error)) {
                        _sendLimitNotice.value = PRE_BID_LIMIT_NOTICE
                    }
                    // Don't resurrect a row that a concurrent socket echo
                    // already confirmed and retired (lost-response race —
                    // the server broadcasts `message:new` before the POST's
                    // HTTP response).
                    if (sendContextsByClientId.containsKey(clientId)) {
                        failedClientIds.add(clientId)
                        rebuild()
                    }
                    Timber.w("chat send failed: ${result.error.message}")
                    false
                }
            }
        }

        /**
         * `POST /api/chat/messages` answers 429 with a body carrying
         * `code: "PRE_BID_LIMIT"` when a non-bidder exceeds the gig room's
         * pre-bid message allowance (`backend/routes/chats.js:1574`).
         */
        private fun isPreBidLimit(error: NetworkError): Boolean =
            error is NetworkError.ClientError &&
                error.code == 429 &&
                error.body?.contains("PRE_BID_LIMIT") == true

        /**
         * The wire `replyToId` is accepted only when it points at a
         * persisted message that lives in the room we're actually sending
         * to. The backend rejects the whole send (400) if the reply target
         * is missing, soft-deleted, or in a different room
         * (`backend/routes/chats.js:1654`). Two cases this guards:
         * - person threads aggregate messages across every shared room
         *   (direct + gig + group), but we always send to the direct room —
         *   a reply to a gig/group bubble would 400;
         * - an optimistic (`client_`-prefixed) row isn't a UUID and isn't
         *   persisted yet.
         * In either case we drop the linkage and still deliver the text,
         * rather than failing the message permanently.
         */
        private fun sanitizedReplyToId(
            replyToId: String?,
            roomId: String,
        ): String? {
            if (replyToId == null) return null
            return messages.firstOrNull { it.id == replyToId && it.roomId == roomId }?.id
        }

        /**
         * Insert a persisted message, replacing any copy that already
         * arrived through a socket-triggered refetch.
         */
        private fun upsert(message: ChatMessageDto) {
            val index = messages.indexOfFirst { it.id == message.id }
            if (index >= 0) messages[index] = message else messages.add(message)
        }

        /**
         * Bare UUID — the send validator is `Joi.string().uuid()`
         * (`backend/routes/chats.js:159`), so a prefixed id fails the
         * whole request with a 400. The `client_` prefix lives only on
         * the local optimistic row id.
         */
        private fun newClientMessageId(): String = UUID.randomUUID().toString()

        private suspend fun streamAiMessage(text: String) {
            val imageUrls = uploadQueuedAIImagesIfNeeded()
            val userMessage =
                localMessage(
                    id = "ai_user_${UUID.randomUUID()}",
                    text = text,
                    userId = currentUserId,
                    type = "text",
                    imageUrls = imageUrls,
                )
            val assistantId = "ai_assistant_${UUID.randomUUID()}"
            messages.add(userMessage)
            messages.add(localMessage(id = assistantId, text = "", userId = "ai", type = "ai_reply"))
            _queuedAttachments.value = emptyList()
            rebuild()

            var streamedText = ""
            runCatching {
                aiRepo.streamChat(
                    message = text.ifEmpty { "What can you tell me about this image?" },
                    conversationId = aiConversationId,
                    images = imageUrls,
                ).collect { event ->
                    when (event) {
                        is AIChatStreamEvent.Conversation -> {
                            aiConversationId = event.id
                            aiSession.conversationId = event.id
                        }
                        is AIChatStreamEvent.TextDelta -> {
                            streamedText += event.delta
                            replaceLocalMessage(assistantId, streamedText, "ai_reply", "ai")
                        }
                        is AIChatStreamEvent.Draft -> {
                            aiDraftsByMessageId[assistantId] = aiDraftsByMessageId[assistantId].orEmpty() + event.draft
                            rebuild()
                        }
                        is AIChatStreamEvent.Error -> replaceLocalMessage(assistantId, event.message, "ai_reply", "ai")
                        AIChatStreamEvent.Done -> Unit
                    }
                }
            }.onFailure {
                replaceLocalMessage(assistantId, "I lost the connection. Please try again.", "ai_reply", "ai")
            }
        }

        fun react(
            messageId: String,
            reaction: String,
        ) {
            viewModelScope.launch {
                when (val result = repo.reactToMessage(messageId, reaction)) {
                    is NetworkResult.Failure -> Timber.w("chat react failed: ${result.error.message}")
                    is NetworkResult.Success -> result.data.reactions?.let { applyReactions(messageId, it) }
                    else -> Unit
                }
            }
        }

        fun beginReply(messageId: String) {
            val message = (messages + pendingByClientId.values).firstOrNull { it.id == messageId } ?: return
            _replyingTo.value =
                ChatReplyPreview(
                    messageId = message.id,
                    senderName = message.sender?.name ?: if (message.userId == currentUserId) "You" else "Message",
                    text = message.resolvedText,
                )
            _editingMessageId.value = null
        }

        fun beginEdit(messageId: String) {
            val message = messages.firstOrNull { it.id == messageId && it.userId == currentUserId } ?: return
            _editingMessageId.value = messageId
            _replyingTo.value = null
            _composerText.value = message.resolvedText
        }

        fun cancelMessageAction() {
            _replyingTo.value = null
            _editingMessageId.value = null
        }

        fun delete(messageId: String) {
            if (messages.none { it.id == messageId && it.userId == currentUserId }) return
            viewModelScope.launch {
                when (val result = repo.deleteMessage(messageId)) {
                    is NetworkResult.Success -> {
                        messages.removeAll { it.id == messageId }
                        rebuild()
                    }
                    is NetworkResult.Failure -> Timber.w("chat delete failed: ${result.error.message}")
                }
            }
        }

        fun dismissSendLimitNotice() {
            _sendLimitNotice.value = null
        }

        /**
         * Block the person counterparty via `POST /api/users/:userId/block`
         * (route `backend/routes/blocks.js:13`). Person threads only — on
         * success the caller dismisses the details sheet and leaves the
         * thread via [onBlocked].
         */
        fun blockUser(onBlocked: () -> Unit = {}) {
            val target = mode as? ChatThreadMode.Person ?: return
            if (_isBlocking.value) return
            viewModelScope.launch {
                _isBlocking.value = true
                try {
                    when (val result = blocksRepo.block(target.otherUserId)) {
                        is NetworkResult.Success -> onBlocked()
                        is NetworkResult.Failure -> Timber.w("block user failed: ${result.error.message}")
                    }
                } finally {
                    _isBlocking.value = false
                }
            }
        }

        // MARK: - Selection mode (bulk delete)

        /** Only own persisted rows are selectable — optimistic `client_` rows have no server id yet. */
        private fun isSelectable(messageId: String): Boolean =
            messages.any { it.id == messageId && it.userId == currentUserId && !it.id.startsWith("client_") }

        fun enterSelectionMode(messageId: String) {
            if (!isSelectable(messageId)) return
            _isSelectionMode.value = true
            _selectedMessageIds.value = setOf(messageId)
        }

        fun toggleSelection(messageId: String) {
            if (!_isSelectionMode.value || !isSelectable(messageId)) return
            val current = _selectedMessageIds.value
            _selectedMessageIds.value = if (messageId in current) current - messageId else current + messageId
        }

        fun exitSelectionMode() {
            _isSelectionMode.value = false
            _selectedMessageIds.value = emptySet()
        }

        /**
         * Delete every selected message through the existing per-message
         * endpoint (no bulk route exists), drop the deleted rows, and exit
         * selection mode.
         */
        fun deleteSelected() {
            val ids = _selectedMessageIds.value
            if (ids.isEmpty()) {
                exitSelectionMode()
                return
            }
            viewModelScope.launch {
                ids.forEach { id ->
                    when (val result = repo.deleteMessage(id)) {
                        is NetworkResult.Success -> messages.removeAll { it.id == id }
                        is NetworkResult.Failure -> Timber.w("bulk delete failed for $id: ${result.error.message}")
                    }
                }
                rebuild()
                exitSelectionMode()
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
            connectionJob?.cancel()
            messageJob?.cancel()
            updateJob?.cancel()
            deleteJob?.cancel()
            reactJob?.cancel()
            markReadJob = null
            connectionJob = null
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
                    // Pending / failed optimistic rows survive refetches —
                    // socket events trigger `fetch(initial = true)`, and
                    // eating an in-flight or failed send here would lose the
                    // message and its retry CTA. Confirmed rows are retired
                    // below by `client_message_id` match.
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
                        is ChatThreadMode.Person -> repo.conversationMessages(target.otherUserId, before, topicId = _selectedTopicId.value)
                        ChatThreadMode.Ai -> return@launch
                    }
                when (response) {
                    is NetworkResult.Success -> {
                        // Drop ids we already hold — a send completing while
                        // this fetch was in flight may have upserted its
                        // message ahead of us.
                        val existingIds = messages.mapTo(mutableSetOf()) { it.id }
                        val ordered = response.data.messages.asReversed().filterNot { existingIds.contains(it.id) }
                        messages.addAll(0, ordered)
                        // A fetched row carrying one of our client ids means
                        // that send landed server-side (e.g. the POST response
                        // was lost, then a socket refetch ran) — retire every
                        // trace of the optimistic copy so a delivered message
                        // can never linger as "failed".
                        response.data.messages.forEach { fetched ->
                            val confirmedId = fetched.clientMessageId ?: return@forEach
                            val held =
                                pendingByClientId.containsKey(confirmedId) ||
                                    sendContextsByClientId.containsKey(confirmedId) ||
                                    failedClientIds.contains(confirmedId)
                            if (held) {
                                pendingByClientId.remove(confirmedId)
                                sendContextsByClientId.remove(confirmedId)
                                failedClientIds.remove(confirmedId)
                            }
                        }
                        updateActiveRooms(response.data)
                        hasMore = response.data.hasMore ?: false
                        oldestCursor =
                            response.data.nextCursor
                                ?: paginationCursor(messages.firstOrNull())
                        rebuild()
                        joinActiveRoomsIfPossible()
                        scheduleMarkRead()
                        if (initial) prefetchDirectRoomIfNeeded()
                    }
                    is NetworkResult.Failure -> {
                        if (initial) {
                            _state.value = ChatConversationUiState.Error(response.error.message)
                        } else {
                            Timber.w("chat pagination failed: ${response.error.message}")
                        }
                    }
                }
            }
        }

        /**
         * Resolve the room to send into. Room threads use their fixed id.
         * Person threads always send to the pair's direct room — found or
         * created via `POST /api/chat/direct` and cached — never to a
         * shared gig/group room surfaced by the aggregated fetch, which
         * would misfile plain DMs and trip gig pre-bid send limits.
         */
        private suspend fun ensureRoomId(): String? =
            when (val target = mode) {
                is ChatThreadMode.Room -> target.id
                is ChatThreadMode.Person -> directRoomId ?: resolveDirectRoom(target.otherUserId)
                ChatThreadMode.Ai -> null
            }

        private suspend fun resolveDirectRoom(otherUserId: String): String? {
            val deferred =
                directRoomDeferred ?: viewModelScope.async {
                    when (val result = repo.createDirectChat(otherUserId)) {
                        is NetworkResult.Success -> result.data.roomId
                        is NetworkResult.Failure -> {
                            Timber.w("create direct chat failed: ${result.error.message}")
                            null
                        }
                    }
                }.also { directRoomDeferred = it }
            val roomId = deferred.await()
            if (directRoomDeferred === deferred) directRoomDeferred = null
            if (roomId != null && directRoomId == null) {
                directRoomId = roomId
                // Join the freshly resolved room so the socket delivers
                // `message:new` for replies that land in it.
                if (!activeRoomIds.contains(roomId)) {
                    activeRoomIds = activeRoomIds + roomId
                    joinActiveRoomsIfPossible()
                }
            }
            return roomId
        }

        /**
         * Best-known room id for the optimistic row (cosmetic only — the
         * authoritative id is resolved by [ensureRoomId] at send time).
         */
        private fun knownRoomIdHint(): String =
            when (val target = mode) {
                is ChatThreadMode.Room -> target.id
                is ChatThreadMode.Person -> directRoomId ?: ""
                ChatThreadMode.Ai -> ""
            }

        /**
         * Resolve the person thread's direct room in the background so the
         * first send doesn't pay the find-or-create round trip. Only runs
         * when history exists — opening an empty thread must not create
         * rooms for conversations that never start.
         */
        private fun prefetchDirectRoomIfNeeded() {
            if (mode !is ChatThreadMode.Person) return
            if (directRoomId != null || messages.isEmpty()) return
            viewModelScope.launch { ensureRoomId() }
        }

        private fun loadTopicsIfNeeded() {
            val target = mode as? ChatThreadMode.Person ?: return
            viewModelScope.launch {
                val pendingInitial = initialTopic
                if (pendingInitial != null && _selectedTopicId.value == null) {
                    when (
                        val result =
                            repo.findOrCreateTopic(
                                target.otherUserId,
                                FindOrCreateTopicBody(
                                    topicType = pendingInitial.topicType,
                                    topicRefId = pendingInitial.topicRefId,
                                    title = pendingInitial.title,
                                ),
                            )
                    ) {
                        is NetworkResult.Success -> _selectedTopicId.value = result.data.topic.id
                        is NetworkResult.Failure -> Timber.w("find/create topic failed: ${result.error.message}")
                    }
                    initialTopic = null
                }
                when (val result = repo.conversationTopics(target.otherUserId)) {
                    is NetworkResult.Success ->
                        _topics.value =
                            result.data.topics.mapNotNull { topic ->
                                val id = topic.id ?: return@mapNotNull null
                                val type = topic.topicType ?: "general"
                                ChatConversationTopic(
                                    id = id,
                                    topicType = type,
                                    title = topic.title ?: "Topic",
                                    status = topic.status,
                                )
                            }
                    is NetworkResult.Failure -> Timber.w("load topics failed: ${result.error.message}")
                }
            }
        }

        private fun updateActiveRooms(response: ChatMessagesResponse) {
            activeRoomIds =
                buildSet {
                    response.roomIds?.forEach(::add)
                    response.messages.forEach { add(it.roomId) }
                    (mode as? ChatThreadMode.Room)?.id?.let(::add)
                    directRoomId?.let(::add)
                }
        }

        // MARK: - Realtime

        private fun subscribeToSockets() {
            if (connectionJob == null) {
                connectionJob =
                    viewModelScope.launch {
                        socket.connectionState
                            .filter { it == SocketManager.ConnectionState.Connected }
                            .collect {
                                joinedRoomIds.clear()
                                joinActiveRoomsIfPossible()
                            }
                    }
            }
            if (messageJob == null) {
                messageJob =
                    viewModelScope.launch {
                        socket.eventsOf("message:new").collect { handleIncoming(it) }
                    }
            }
            if (updateJob == null) {
                updateJob =
                    viewModelScope.launch {
                        socket.eventsOf("message:edited").collect { handleUpdate(it) }
                    }
            }
            if (deleteJob == null) {
                deleteJob =
                    viewModelScope.launch {
                        socket.eventsOf("message:deleted").collect { handleDelete(it) }
                    }
            }
            if (reactJob == null) {
                reactJob =
                    viewModelScope.launch {
                        socket.eventsOf("message:reaction_updated").collect { handleReaction(it) }
                    }
            }
            joinActiveRoomsIfPossible()
        }

        private fun joinActiveRoomsIfPossible() {
            if (socket.connectionState.value != SocketManager.ConnectionState.Connected) return
            val pending = activeRoomIds - joinedRoomIds
            pending.forEach { roomId ->
                joinedRoomIds.add(roomId)
                viewModelScope.launch {
                    val ack = socket.emitWithAck("room:join", JSONObject().put("roomId", roomId))
                    if (ack?.optBoolean("success") != true) return@launch
                    val backfill = parseMessages(ack.optJSONArray("messages"))
                    if (backfill.isNotEmpty()) mergeBackfill(backfill)
                }
            }
        }

        private fun mergeBackfill(backfill: List<ChatMessageDto>) {
            val existing = messages.map { it.id }.toSet()
            val fresh = backfill.filterNot { existing.contains(it.id) }
            if (fresh.isEmpty()) return
            messages.addAll(fresh)
            messages.sortBy { it.createdAt }
            rebuild()
            scheduleMarkRead()
        }

        private fun applyUpdatedMessage(updated: ChatMessageDto) {
            val index = messages.indexOfFirst { it.id == updated.id }
            if (index >= 0) {
                messages[index] = updated
            } else {
                messages.add(updated)
            }
            rebuild()
        }

        private fun applyReactions(
            messageId: String,
            reactions: List<app.pantopus.android.data.api.models.chats.ChatReactionSummary>,
        ) {
            val index = messages.indexOfFirst { it.id == messageId }
            if (index < 0) return
            messages[index] = messages[index].copy(reactions = reactions)
            rebuild()
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
            val roomId = json.optStringValue("room_id") ?: json.optStringValue("roomId")
            if (roomId != null && !activeRoomIds.contains(roomId)) return
            val clientId = json.optString("client_message_id").takeIf { it.isNotEmpty() }
            if (clientId != null && pendingByClientId.containsKey(clientId)) {
                pendingByClientId.remove(clientId)
                sendContextsByClientId.remove(clientId)
                failedClientIds.remove(clientId)
                fetch(initial = true)
                return
            }
            // Our own REST response usually lands first — skip the refetch
            // when the broadcast echoes a message we already hold.
            val id = json.optStringValue("id")
            if (id != null && messages.any { it.id == id }) return
            fetch(initial = true)
        }

        private fun handleUpdate(json: JSONObject) {
            val roomId = json.optStringValue("room_id") ?: json.optStringValue("roomId")
            if (roomId != null && !activeRoomIds.contains(roomId)) return
            val id =
                json.optStringValue("id")
                    ?: json.optStringValue("message_id")
                    ?: json.optStringValue("messageId")
                    ?: json.optJSONObject("message")?.optStringValue("id")
                    ?: return
            if (messages.none { it.id == id }) return
            fetch(initial = true)
        }

        private fun handleDelete(json: JSONObject) {
            val roomId = json.optStringValue("room_id") ?: json.optStringValue("roomId")
            if (roomId != null && !activeRoomIds.contains(roomId)) return
            val id =
                json.optStringValue("id")
                    ?: json.optStringValue("message_id")
                    ?: json.optStringValue("messageId")
                    ?: return
            val index = messages.indexOfFirst { it.id == id }
            if (index >= 0) {
                messages.removeAt(index)
                rebuild()
            }
        }

        private suspend fun uploadQueuedAttachmentsIfNeeded(roomId: String): List<String> {
            val files =
                _queuedAttachments.value.mapNotNull { attachment ->
                    val bytes = attachment.bytes ?: return@mapNotNull null
                    UploadFile(
                        filename = attachment.filename,
                        mimeType = attachment.mimeType,
                        bytes = bytes,
                    )
                }
            if (files.isEmpty()) return emptyList()
            return when (val result = uploadRepo.uploadChatMedia(roomId, files)) {
                is NetworkResult.Success -> result.data.media.map { it.id }
                is NetworkResult.Failure -> throw IllegalStateException(result.error.message)
            }
        }

        private suspend fun uploadQueuedAIImagesIfNeeded(): List<String> {
            val files =
                _queuedAttachments.value.mapNotNull { attachment ->
                    val bytes = attachment.bytes ?: return@mapNotNull null
                    if (!attachment.mimeType.startsWith("image/")) return@mapNotNull null
                    UploadFile(
                        filename = attachment.filename,
                        mimeType = attachment.mimeType,
                        bytes = bytes,
                    )
                }
            if (files.isEmpty()) return emptyList()
            return when (val result = uploadRepo.uploadAIMedia(files)) {
                is NetworkResult.Success -> result.data.images.map { it.url }
                is NetworkResult.Failure -> emptyList()
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
            // Topic dividers mark where the thread switches topics — only on
            // the unfiltered ("All") person view, where messages from every
            // topic interleave. A topic filter or room thread stays clean.
            val showTopicDividers =
                mode is ChatThreadMode.Person && _selectedTopicId.value == null && _topics.value.isNotEmpty()
            val topicTitlesById = _topics.value.associate { it.id to it.title }
            combined.forEachIndexed { index, message ->
                val dayKey = dayKey(message.createdAt)
                if (dayKey != lastDayKey) {
                    rows.add(ChatTimelineRow.DayDivider(ChatDayDivider(id = dayKey, label = dayLabel(message.createdAt))))
                    lastDayKey = dayKey
                }
                if (showTopicDividers && index > 0 && combined[index - 1].topicId != message.topicId) {
                    rows.add(
                        ChatTimelineRow.TopicDivider(
                            id = message.id,
                            label = message.topicId?.let { topicTitlesById[it] } ?: "General",
                        ),
                    )
                }
                if (message.resolvedType == "broadcast_reference") {
                    rows.add(ChatTimelineRow.BroadcastReference(broadcastReferenceOf(message)))
                    return@forEachIndexed
                }
                val side = if (message.userId == currentUserId) ChatMessageSide.Outgoing else ChatMessageSide.Incoming
                val previousSameSide =
                    index > 0 &&
                        combined[index - 1].userId == message.userId &&
                        dayKey(combined[index - 1].createdAt) == dayKey
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
                val replyPreview = replyPreviewOf(message, combined)
                val reactions =
                    message.reactions.map {
                        ChatBubbleReaction(
                            reaction = it.reaction,
                            count = it.count,
                            reactedByMe = it.reactedByMe,
                        )
                    }
                rows.add(
                    ChatTimelineRow.Bubble(
                        ChatBubbleContent(
                            id = message.id,
                            side = side,
                            body = body,
                            replyPreview = replyPreview,
                            reactions = reactions,
                            hasTail = hasTail,
                            stamp = stamp,
                            deliveryState = deliveryState,
                            lockedTier = lockedTierOf(message),
                            sentSupportTier = sentSupportTierOf(message),
                            isContinuation = previousSameSide,
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
            message.attachments.firstOrNull()?.let { attachment ->
                val mime = attachment.mimeType.orEmpty()
                if (mime.startsWith("image/")) {
                    ChatBubbleBody.Image(ChatMediaUrl.resolve(attachment.fileUrl))
                } else {
                    ChatBubbleBody.Attachment(
                        filename = attachment.originalFilename ?: "Attachment",
                        sizeLabel = attachment.fileSize?.let(::fileSizeLabel),
                    )
                }
            } ?: when (message.resolvedType) {
                "ai_reply" ->
                    ChatBubbleBody.AiReply(
                        text = message.resolvedText,
                        estimate = null,
                        drafts = aiDraftsByMessageId[message.id].orEmpty(),
                    )
                "text" -> {
                    val imageUrls =
                        (message.metadata?.get("image_urls") as? List<*>)
                            ?.filterIsInstance<String>()
                            ?.mapNotNull(ChatMediaUrl::resolve)
                            .orEmpty()
                    if (imageUrls.isNotEmpty()) {
                        ChatBubbleBody.TextWithImages(message.resolvedText, imageUrls)
                    } else {
                        ChatBubbleBody.Text(message.resolvedText)
                    }
                }
                "image" ->
                    ChatBubbleBody.Image(
                        ChatMediaUrl.resolve(message.metadata?.get("image_url") as? String),
                    )
                "file", "audio" ->
                    ChatBubbleBody.Attachment(
                        filename = (message.metadata?.get("filename") as? String) ?: "Attachment",
                    )
                "gig_offer" -> {
                    val meta = message.metadata
                    val gigId =
                        metadataString(meta, "gigId", "gig_id")
                            ?: extractEntityId(message.resolvedText, "gigs")
                    val title = metadataString(meta, "title") ?: message.resolvedText.ifEmpty { null } ?: "Shared gig"
                    ChatBubbleBody.GigOfferCard(
                        ChatGigOfferCard(
                            gigId = gigId.orEmpty(),
                            title = title,
                            category = metadataString(meta, "category"),
                            priceLabel = metadataPriceLabel(meta),
                            status = metadataString(meta, "status"),
                        ),
                    )
                }
                "listing_offer" -> {
                    val meta = message.metadata
                    val listingId =
                        metadataString(meta, "listingId", "listing_id")
                            ?: extractEntityId(message.resolvedText, "listings")
                    val title = metadataString(meta, "title") ?: message.resolvedText.ifEmpty { null } ?: "Shared listing"
                    val isFree = metadataBool(meta, "isFree", "is_free") == true
                    ChatBubbleBody.ListingOfferCard(
                        ChatListingOfferCard(
                            listingId = listingId.orEmpty(),
                            title = title,
                            category = metadataString(meta, "category"),
                            priceLabel = metadataListingPriceLabel(meta, isFree),
                            condition = metadataString(meta, "condition"),
                            imageUrl = metadataString(meta, "imageUrl", "image_url"),
                        ),
                    )
                }
                "location" -> {
                    val meta = message.metadata
                    ChatBubbleBody.LocationCard(
                        ChatLocationCard(
                            latitude = metadataDouble(meta, "latitude", "lat") ?: 0.0,
                            longitude = metadataDouble(meta, "longitude", "lng", "lon") ?: 0.0,
                            address = metadataString(meta, "address") ?: message.resolvedText.ifEmpty { null } ?: "Pinned location",
                        ),
                    )
                }
                else -> ChatBubbleBody.Text(message.resolvedText)
            }

        private fun fileSizeLabel(bytes: Int): String =
            when {
                bytes >= 1_000_000 -> String.format("%.1f MB", bytes.toDouble() / 1_000_000)
                bytes >= 1_000 -> "${bytes / 1_000} KB"
                else -> "$bytes B"
            }

        private fun replyPreviewOf(
            message: ChatMessageDto,
            allMessages: List<ChatMessageDto>,
        ): ChatReplyPreview? {
            val replyToId = message.replyToId ?: return null
            val original = allMessages.firstOrNull { it.id == replyToId } ?: return null
            return ChatReplyPreview(
                messageId = original.id,
                senderName = original.sender?.name ?: "Message",
                text = original.resolvedText,
            )
        }

        private fun lockedTierOf(message: ChatMessageDto): String? {
            val metadata = message.metadata ?: return null
            val isLocked =
                metadata["tier_locked"] as? Boolean
                    ?: metadata["is_locked"] as? Boolean
                    ?: metadata["locked"] as? Boolean
                    ?: false
            if (!isLocked) return null
            return metadata["required_tier"] as? String
                ?: metadata["locked_tier"] as? String
                ?: metadata["tier"] as? String
                ?: "Silver"
        }

        private fun sentSupportTierOf(message: ChatMessageDto): String? {
            val metadata = message.metadata ?: return null
            return metadata["sent_support_tier"] as? String
                ?: metadata["support_tier"] as? String
                ?: metadata["paid_support_tier"] as? String
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

        /**
         * Optimistic local row. Its `id` keeps the `client_` prefix — the
         * projection and screen key "still sending" off that — while
         * `clientMessageId` stays a bare UUID for the wire.
         */
        private fun optimistic(
            text: String,
            clientId: String,
            roomId: String,
        ): ChatMessageDto =
            ChatMessageDto(
                id = "client_$clientId",
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

        private fun localMessage(
            id: String,
            text: String,
            userId: String,
            type: String,
            imageUrls: List<String> = emptyList(),
        ): ChatMessageDto =
            ChatMessageDto(
                id = id,
                roomId = "ai",
                userId = userId,
                messageText = text,
                messageType = type,
                metadata = if (imageUrls.isEmpty()) null else mapOf("image_urls" to imageUrls),
                createdAt = Instant.now().toString(),
            )

        private fun replaceLocalMessage(
            id: String,
            text: String,
            type: String,
            userId: String,
        ) {
            val index = messages.indexOfFirst { it.id == id }
            if (index < 0) return
            messages[index] = localMessage(id = id, text = text, userId = userId, type = type)
            rebuild()
        }

        private fun parseMessages(array: JSONArray?): List<ChatMessageDto> {
            if (array == null) return emptyList()
            return buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    add(
                        ChatMessageDto(
                            id = json.optStringValue("id") ?: continue,
                            roomId = json.optStringValue("room_id") ?: json.optStringValue("roomId") ?: continue,
                            userId = json.optStringValue("user_id") ?: json.optStringValue("userId"),
                            messageText =
                                json.optStringValue("message_text")
                                    ?: json.optStringValue("messageText")
                                    ?: json.optStringValue("message"),
                            message =
                                json.optStringValue("message")
                                    ?: json.optStringValue("message_text")
                                    ?: json.optStringValue("messageText"),
                            messageType =
                                json.optStringValue("message_type")
                                    ?: json.optStringValue("messageType")
                                    ?: json.optStringValue("type"),
                            type =
                                json.optStringValue("type")
                                    ?: json.optStringValue("message_type")
                                    ?: json.optStringValue("messageType"),
                            topicId = json.optStringValue("topic_id") ?: json.optStringValue("topicId"),
                            clientMessageId = json.optStringValue("client_message_id") ?: json.optStringValue("clientMessageId"),
                            createdAt = json.optStringValue("created_at") ?: json.optStringValue("createdAt") ?: continue,
                        ),
                    )
                }
            }
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

        private fun metadataString(
            metadata: Map<String, Any>?,
            vararg keys: String,
        ): String? {
            if (metadata == null) return null
            for (key in keys) {
                val value = metadata[key] as? String
                if (!value.isNullOrBlank()) return value
            }
            return null
        }

        private fun metadataDouble(
            metadata: Map<String, Any>?,
            vararg keys: String,
        ): Double? {
            if (metadata == null) return null
            for (key in keys) {
                when (val value = metadata[key]) {
                    is Number -> return value.toDouble()
                    is String -> value.toDoubleOrNull()?.let { return it }
                }
            }
            return null
        }

        private fun metadataBool(
            metadata: Map<String, Any>?,
            vararg keys: String,
        ): Boolean? {
            if (metadata == null) return null
            for (key in keys) {
                when (val value = metadata[key]) {
                    is Boolean -> return value
                }
            }
            return null
        }

        private fun metadataPriceLabel(metadata: Map<String, Any>?): String? {
            val raw = metadataDouble(metadata, "price") ?: return null
            return "$${raw.toInt()}"
        }

        private fun metadataListingPriceLabel(
            metadata: Map<String, Any>?,
            isFree: Boolean,
        ): String {
            if (isFree) return "FREE"
            val raw = metadataDouble(metadata, "price") ?: return "Make Offer"
            return "$${raw.toInt()}"
        }

        private fun extractEntityId(
            text: String?,
            collection: String,
        ): String? {
            if (text.isNullOrBlank()) return null
            val pattern = Regex("""/$collection/([A-Za-z0-9-]+)""")
            return pattern.find(text)?.groupValues?.getOrNull(1)
        }

        /** Stable `(created_at|id)` cursor — avoids `+` offsets mangled in query strings. */
        private fun paginationCursor(message: ChatMessageDto?): String? {
            val createdAt = message?.createdAt ?: return null
            val id = message.id
            val timestamp =
                createdAt
                    .replace("+00:00", "Z")
                    .replace("+0000", "Z")
            return "$timestamp|$id"
        }
    }

private fun JSONObject.optStringValue(key: String): String? = optString(key).takeIf { it.isNotEmpty() }

/** Banner copy for the pre-bid gig-room send limit (429 `PRE_BID_LIMIT`). */
private const val PRE_BID_LIMIT_NOTICE =
    "Message limit reached — place a bid or wait for acceptance to keep chatting."
