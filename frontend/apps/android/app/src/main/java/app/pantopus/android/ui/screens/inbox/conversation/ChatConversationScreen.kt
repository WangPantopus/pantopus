@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "CyclomaticComplexMethod", "TooManyFunctions", "LongParameterList")

package app.pantopus.android.ui.screens.inbox.conversation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.inbox.conversation.ai.AiCapabilityChip
import app.pantopus.android.ui.screens.inbox.conversation.ai.AiEstimateCard
import app.pantopus.android.ui.screens.inbox.conversation.ai.ChatAiAvatar
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Chat conversation screen (T2.2). Three frames: shimmer loading,
 * counterparty-specific empty state, populated thread. Composer has
 * attach + send discs; send is color-bound to text presence and
 * disabled while in flight.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatConversationScreen(
    args: ChatConversationRouteArgs,
    chrome: ChatConversationChrome = ChatConversationChrome(),
    onBack: () -> Unit = {},
    onUseAIDraft: (ChatAIDraftCard) -> Unit = {},
    onOpenGig: (String) -> Unit = {},
    onOpenListing: (String) -> Unit = {},
    viewModel: ChatConversationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeCounterparty by viewModel.counterparty.collectAsStateWithLifecycle()
    val composerText by viewModel.composerText.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val replyingTo by viewModel.replyingTo.collectAsStateWithLifecycle()
    val editingMessageId by viewModel.editingMessageId.collectAsStateWithLifecycle()
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val selectedTopicId by viewModel.selectedTopicId.collectAsStateWithLifecycle()
    val isCounterpartyTyping by viewModel.isCounterpartyTyping.collectAsStateWithLifecycle()
    val queuedAttachments by viewModel.queuedAttachments.collectAsStateWithLifecycle()
    val pendingScroll by viewModel.pendingScrollTarget.collectAsStateWithLifecycle()
    val sendLimitNotice by viewModel.sendLimitNotice.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedMessageIds by viewModel.selectedMessageIds.collectAsStateWithLifecycle()
    val isBlocking by viewModel.isBlocking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showFanUpgradePrompt by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<ChatBubbleContent?>(null) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showGigPicker by remember { mutableStateOf(false) }
    var showListingPicker by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showEmojiSheet by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            scope.launch {
                uris.take(5).forEach { uri ->
                    withContext(Dispatchers.IO) {
                        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext
                        val extension = mimeType.substringAfter('/', "jpg").substringBefore('+')
                        viewModel.queueAttachment(
                            kind = ChatQueuedAttachmentKind.Image,
                            filename = "chat-${UUID.randomUUID()}.$extension",
                            mimeType = mimeType,
                            bytes = bytes,
                        )
                    }
                }
            }
        }
    val attachmentPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            scope.launch {
                val attachments =
                    withContext(Dispatchers.IO) {
                        uris.take(5).mapNotNull { uri ->
                            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                            val bytes =
                                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                    ?: return@mapNotNull null
                            val extension = mimeType.substringAfter('/', "bin").substringBefore('+')
                            Triple(mimeType, "chat-${UUID.randomUUID()}.$extension", bytes)
                        }
                    }
                attachments.forEach { (mimeType, filename, bytes) ->
                    viewModel.queueAttachment(
                        kind = if (mimeType.startsWith("image/")) ChatQueuedAttachmentKind.Image else ChatQueuedAttachmentKind.Document,
                        filename = filename,
                        mimeType = mimeType,
                        bytes = bytes,
                    )
                }
            }
        }
    val conversationMode = chrome.mode
    val resolvedFanEntitlement = chrome.fanEntitlement ?: ChatConversationSampleData.fanEntitlement
    val isFanThread = conversationMode == ChatConversationMode.FanThread
    val isFanReplyLocked = isFanThread && !resolvedFanEntitlement.canReply

    LaunchedEffect(Unit) {
        viewModel.configure(args.mode, args.counterparty, args.currentUserId, args.scrollToMessageId, args.initialTopic)
        viewModel.load()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.teardown() }
    }
    val resolvedCreatorContext = chrome.creatorThread?.context ?: ChatCreatorThreadContext.defaults()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appSurface)
                .testTag("chatConversation"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isSelectionMode) {
                SelectionTopBar(
                    count = selectedMessageIds.size,
                    onCancel = viewModel::exitSelectionMode,
                )
            } else {
                ChatHeader(
                    conversationMode = conversationMode,
                    counterparty = activeCounterparty,
                    creatorContext = resolvedCreatorContext,
                    onBack = onBack,
                    onOpenDetails = { showDetailsSheet = true },
                )
            }
            if (conversationMode == ChatConversationMode.CreatorThread) {
                CreatorAudienceStrip(
                    context = resolvedCreatorContext,
                    onOpenAudienceProfile = chrome.creatorThread?.onOpenAudienceProfile ?: {},
                )
                CreatorQuotaMeter(quota = resolvedCreatorContext.quota)
            }
            if (isFanThread) {
                FanMembershipStripe(
                    entitlement = resolvedFanEntitlement,
                    onManage = { showFanUpgradePrompt = true },
                )
            }
            if (topics.isNotEmpty()) {
                TopicStrip(
                    topics = topics,
                    selectedTopicId = selectedTopicId,
                    onSelect = viewModel::selectTopic,
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val s = state) {
                    ChatConversationUiState.Loading -> LoadingFrame()
                    ChatConversationUiState.Empty ->
                        EmptyFrame(
                            counterparty = activeCounterparty,
                            aiPrompts = viewModel.aiPrompts,
                            emptyChips = viewModel.emptyChips,
                            onChipTap = viewModel::tapPrompt,
                            conversationMode = conversationMode,
                            fanEntitlement = resolvedFanEntitlement,
                            onCapabilityTap = viewModel::sendCapabilityPrompt,
                        )
                    is ChatConversationUiState.Loaded ->
                        PopulatedFrame(
                            rows = s.rows,
                            onRetry = viewModel::retry,
                            onLoadOlder = viewModel::loadOlder,
                            scrollToRowId = pendingScroll,
                            onScrollConsumed = viewModel::consumePendingScroll,
                            conversationMode = conversationMode,
                            incomingInitials = incomingInitialsFor(conversationMode, activeCounterparty),
                            onLockedAction = { showFanUpgradePrompt = true },
                            onBubbleLongPress = {
                                if (isSelectionMode) viewModel.toggleSelection(it.id) else actionTarget = it
                            },
                            selectedMessageIds = if (isSelectionMode) selectedMessageIds else emptySet(),
                            onBubbleTap = { if (isSelectionMode) viewModel.toggleSelection(it.id) },
                            onUseAIDraft = onUseAIDraft,
                            onOpenGig = onOpenGig,
                            onOpenListing = onOpenListing,
                            onOpenLocation = { lat, lng ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng"))
                                runCatching { context.startActivity(intent) }
                            },
                        )
                    is ChatConversationUiState.Error -> ErrorFrame(message = s.message, onRetry = viewModel::refresh)
                }
            }
            if (isCounterpartyTyping) {
                val typingInitials =
                    incomingInitialsFor(conversationMode, activeCounterparty)
                        ?: activeCounterparty.displayName.initials()
                TypingIndicator(initials = typingInitials)
            }
            if (queuedAttachments.isNotEmpty()) {
                AttachmentStripView(
                    attachments = queuedAttachments,
                    onRemove = viewModel::removeQueuedAttachment,
                )
            }
            if (isFanThread) {
                FanQuotaGate(
                    entitlement = resolvedFanEntitlement,
                    onUpgrade = { showFanUpgradePrompt = true },
                )
            }
            if (isSelectionMode) {
                SelectionDeleteBar(
                    enabled = selectedMessageIds.isNotEmpty(),
                    onDelete = { showBulkDeleteConfirm = true },
                )
            } else {
                sendLimitNotice?.let { notice ->
                    SendLimitNoticeBanner(text = notice, onDismiss = viewModel::dismissSendLimitNotice)
                }
                ComposerContextBanner(
                    reply = replyingTo,
                    editing = editingMessageId != null,
                    onCancel = viewModel::cancelMessageAction,
                )
                Composer(
                    text = composerText,
                    placeholder = composerPlaceholder(conversationMode, activeCounterparty, resolvedFanEntitlement),
                    canSend = composerText.isNotBlank() && !isSending,
                    showsSendCost = isFanThread && !isFanReplyLocked,
                    isLockedAction = isFanReplyLocked,
                    onTextChange = viewModel::setComposerText,
                    onSend = {
                        if (isFanReplyLocked) {
                            showFanUpgradePrompt = true
                        } else {
                            viewModel.send()
                        }
                    },
                    onAttach = { showAttachSheet = true },
                    onEmoji = { showEmojiSheet = true },
                )
            }
        }

        if (showAttachSheet) {
            ChatAttachSheet(
                onDismiss = { showAttachSheet = false },
                onPhotos = { photoPicker.launch("image/*") },
                onDocument = { attachmentPicker.launch(arrayOf("*/*")) },
                onLocation = { viewModel.sendCurrentLocation() },
                onGig = { showGigPicker = true },
                onListing = { showListingPicker = true },
            )
        }
        if (showGigPicker) {
            val shareGigs by viewModel.shareableGigs.collectAsStateWithLifecycle()
            val loadingShare by viewModel.isLoadingShareOptions.collectAsStateWithLifecycle()
            val shareError by viewModel.shareOptionsError.collectAsStateWithLifecycle()
            ChatShareGigPickerSheet(
                gigs = shareGigs,
                isLoading = loadingShare,
                error = shareError,
                onDismiss = { showGigPicker = false },
                onSelect = { gig ->
                    showGigPicker = false
                    viewModel.sendGigOffer(gig)
                },
                onLoad = viewModel::loadShareableGigs,
            )
        }
        if (showListingPicker) {
            val shareListings by viewModel.shareableListings.collectAsStateWithLifecycle()
            val loadingShare by viewModel.isLoadingShareOptions.collectAsStateWithLifecycle()
            val shareError by viewModel.shareOptionsError.collectAsStateWithLifecycle()
            ChatShareListingPickerSheet(
                listings = shareListings,
                isLoading = loadingShare,
                error = shareError,
                onDismiss = { showListingPicker = false },
                onSelect = { listing ->
                    showListingPicker = false
                    viewModel.sendListingOffer(listing)
                },
                onLoad = viewModel::loadShareableListings,
            )
        }

        if (showFanUpgradePrompt) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showFanUpgradePrompt = false },
                sheetState = sheetState,
                containerColor = PantopusColors.appSurface,
            ) {
                FanTierUpgradePromptSheet(entitlement = resolvedFanEntitlement)
            }
        }
        actionTarget?.let { target ->
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { actionTarget = null },
                sheetState = sheetState,
                containerColor = PantopusColors.appSurface,
            ) {
                MessageActionSheet(
                    content = target,
                    onCopy = { actionTarget = null },
                    onReply = {
                        viewModel.beginReply(target.id)
                        actionTarget = null
                    },
                    onSelect = {
                        viewModel.enterSelectionMode(target.id)
                        actionTarget = null
                    },
                    onEdit = {
                        viewModel.beginEdit(target.id)
                        actionTarget = null
                    },
                    onDelete = {
                        viewModel.delete(target.id)
                        actionTarget = null
                    },
                    onReact = { reaction ->
                        viewModel.react(target.id, reaction)
                        actionTarget = null
                    },
                )
            }
        }
        if (showDetailsSheet) {
            ConversationDetailsSheet(
                counterpartyName = activeCounterparty.displayName,
                topics = topics,
                isBlocking = isBlocking,
                onDismiss = { showDetailsSheet = false },
                onBlock = { showBlockConfirm = true },
            )
        }
        if (showEmojiSheet) {
            ChatEmojiPickerSheet(
                onDismiss = { showEmojiSheet = false },
                // Append and keep the sheet open so multiple emoji can be
                // picked in one pass.
                onPick = { emoji -> viewModel.setComposerText(viewModel.composerText.value + emoji) },
            )
        }
        if (showBlockConfirm) {
            AlertDialog(
                onDismissRequest = { showBlockConfirm = false },
                containerColor = PantopusColors.appSurface,
                title = { Text(text = "Block ${activeCounterparty.displayName}?") },
                text = { Text(text = "They won't be able to message you anymore. You can unblock them later.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showBlockConfirm = false
                            viewModel.blockUser {
                                showDetailsSheet = false
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("chatBlockConfirm"),
                    ) {
                        Text(text = "Block", color = PantopusColors.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBlockConfirm = false }) {
                        Text(text = "Cancel", color = PantopusColors.appTextSecondary)
                    }
                },
            )
        }
        if (showBulkDeleteConfirm) {
            val count = selectedMessageIds.size
            AlertDialog(
                onDismissRequest = { showBulkDeleteConfirm = false },
                containerColor = PantopusColors.appSurface,
                title = { Text(text = "Delete $count ${if (count == 1) "message" else "messages"}?") },
                text = { Text(text = "Deleted messages are removed for everyone in the conversation.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showBulkDeleteConfirm = false
                            viewModel.deleteSelected()
                        },
                        modifier = Modifier.testTag("chatBulkDeleteConfirm"),
                    ) {
                        Text(text = "Delete", color = PantopusColors.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkDeleteConfirm = false }) {
                        Text(text = "Cancel", color = PantopusColors.appTextSecondary)
                    }
                },
            )
        }
    }
}

private fun composerPlaceholder(
    conversationMode: ChatConversationMode,
    c: ChatCounterparty,
    fanEntitlement: ChatFanEntitlement = ChatConversationSampleData.fanEntitlement,
): String =
    when {
        conversationMode == ChatConversationMode.AiAssistant -> "Ask Pantopus AI…"
        conversationMode == ChatConversationMode.FanThread -> {
            val required = fanEntitlement.requiredReplyTier
            if (required != null) {
                "Upgrade to $required to reply…"
            } else {
                "Message ${c.displayName.firstWord()}… (uses 1 of ${fanEntitlement.messageLimit})"
            }
        }
        else -> {
            when (c) {
                is ChatCounterparty.Ai -> "Ask Pantopus AI…"
                is ChatCounterparty.Group -> "Message ${c.displayName.firstWord()}…"
                is ChatCounterparty.Person -> "Message ${c.displayName.firstWord()}…"
            }
        }
    }

private fun String.firstWord(): String = split(" ").firstOrNull() ?: this

private fun incomingInitialsFor(
    conversationMode: ChatConversationMode,
    counterparty: ChatCounterparty,
): String? =
    if (conversationMode != ChatConversationMode.Dm) {
        null
    } else {
        when (counterparty) {
            is ChatCounterparty.Person -> counterparty.initials
            is ChatCounterparty.Group -> counterparty.displayName.initials()
            is ChatCounterparty.Ai -> null
        }
    }

// MARK: - Header

@Composable
internal fun ChatHeader(
    counterparty: ChatCounterparty,
    onBack: () -> Unit,
    conversationMode: ChatConversationMode = ChatConversationMode.Dm,
    creatorContext: ChatCreatorThreadContext = ChatCreatorThreadContext.defaults(),
    // Opens the conversation-details drawer (topics + safety actions).
    onOpenDetails: () -> Unit = {},
) {
    val isAi = conversationMode == ChatConversationMode.AiAssistant || counterparty is ChatCounterparty.Ai
    val isFanThread = conversationMode == ChatConversationMode.FanThread
    val isCreator = conversationMode == ChatConversationMode.CreatorThread
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(if (isCreator) 64.dp else 56.dp)
                .background(PantopusColors.appSurface)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("chatConversationHeader"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.ChevronLeft,
                contentDescription = "Back",
                size = Radii.xl2,
                tint = PantopusColors.appText,
            )
        }
        HeaderAvatar(
            isAi = isAi,
            isFanThread = isFanThread,
            counterparty = counterparty,
            tierRank = if (isCreator) creatorContext.fanTierRank else null,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = counterparty.displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isAi) BetaPill()
                if (isFanThread) PersonaPill()
                if (isCreator) {
                    CreatorTierChip(name = creatorContext.fanTierName, rank = creatorContext.fanTierRank)
                }
            }
            val presence =
                when {
                    isFanThread -> presenceFor(counterparty, isFanThread = true)
                    isCreator -> (creatorContext.fanTierRank > 1) to creatorContext.fanSubtitle
                    else -> presenceFor(counterparty)
                }
            presence?.let { (online, text) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (online) {
                        Box(
                            modifier =
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PantopusColors.success),
                        )
                    }
                    Text(
                        text = text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = PantopusColors.appTextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
        when {
            isFanThread -> {
                Row {
                    HeaderIcon(PantopusIcon.ExternalLink)
                    HeaderIcon(PantopusIcon.MoreHorizontal)
                }
            }
            isCreator -> {
                Row {
                    HeaderIcon(PantopusIcon.User)
                    HeaderIcon(PantopusIcon.MoreHorizontal)
                }
            }
            else ->
                when (counterparty) {
                    is ChatCounterparty.Person -> {
                        // RN replaces call/video chrome with a single info
                        // button that opens the conversation details drawer.
                        Box(
                            modifier =
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onOpenDetails),
                            contentAlignment = Alignment.Center,
                        ) {
                            PantopusIconImage(
                                icon = PantopusIcon.Info,
                                contentDescription = "Conversation details",
                                size = Spacing.s5,
                                tint = PantopusColors.appTextSecondary,
                            )
                        }
                    }
                    is ChatCounterparty.Ai -> {
                        Row {
                            HeaderIcon(PantopusIcon.History)
                            HeaderIcon(PantopusIcon.MoreVertical)
                        }
                    }
                    is ChatCounterparty.Group -> HeaderIcon(PantopusIcon.MoreVertical)
                }
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
}

@Composable
private fun BetaPill() {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.pill))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "BETA",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun PersonaPill() {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.businessBg)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Briefcase,
            contentDescription = null,
            size = 9.dp,
            tint = PantopusColors.business,
        )
        Text(
            text = "Persona",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.business,
        )
    }
}

@Composable
internal fun CreatorAudienceStrip(
    context: ChatCreatorThreadContext,
    onOpenAudienceProfile: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.s3, top = 10.dp, end = Spacing.s3)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.businessBg)
                .border(1.dp, PantopusColors.business.copy(alpha = 0.18f), RoundedCornerShape(Radii.lg))
                .clickable(onClick = onOpenAudienceProfile)
                .padding(horizontal = 10.dp, vertical = Spacing.s2)
                .testTag("chatCreatorAudienceStrip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.business),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Users,
                contentDescription = null,
                size = 15.dp,
                strokeWidth = 2.4f,
                tint = PantopusColors.appTextInverse,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = "CREATOR INBOX · ${context.personaName.uppercase()}",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.business,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = context.audienceSummary,
                fontSize = 11.sp,
                color = PantopusColors.appTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PantopusIconImage(
            icon = PantopusIcon.ChevronRight,
            contentDescription = "Open audience profile",
            size = Radii.xl,
            strokeWidth = 2.4f,
            tint = PantopusColors.business,
        )
    }
}

@Composable
internal fun CreatorQuotaMeter(quota: ChatCreatorQuota) {
    val progress =
        if (quota.total <= 0) {
            0f
        } else {
            (quota.used.toFloat() / quota.total.toFloat()).coerceIn(0f, 1f)
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .padding(horizontal = 14.dp, vertical = 9.dp)
                .testTag("chatCreatorQuotaMeter"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.MessageSquare,
                    contentDescription = null,
                    size = 11.dp,
                    strokeWidth = 2.5f,
                    tint = PantopusColors.business,
                )
                Text(
                    text = "Replies this week",
                    fontSize = 11.sp,
                    color = PantopusColors.appTextSecondary,
                )
            }
            Text(
                text = "${quota.used} of ${quota.total} replies this week",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appText,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(Radii.xs))
                    .background(PantopusColors.appSurfaceSunken),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .clip(RoundedCornerShape(Radii.xs))
                        .background(PantopusColors.primary600),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            PantopusIconImage(
                icon = PantopusIcon.RefreshCw,
                contentDescription = null,
                size = 10.dp,
                strokeWidth = 2.4f,
                tint = PantopusColors.appTextMuted,
            )
            Text(
                text = quota.resetCopy,
                fontSize = 10.sp,
                color = PantopusColors.appTextMuted,
            )
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
}

@Composable
private fun CreatorTierChip(
    name: String,
    rank: Int,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(creatorTierBgColor(rank))
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .testTag("chatCreatorTierChip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val icon =
            when {
                rank >= 4 -> PantopusIcon.Crown
                rank >= 2 -> PantopusIcon.Shield
                else -> null
            }
        if (icon != null) {
            PantopusIconImage(
                icon = icon,
                contentDescription = null,
                size = 9.dp,
                strokeWidth = 2.4f,
                tint = creatorTierColor(rank),
            )
        }
        Text(
            text = name.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = creatorTierColor(rank),
        )
    }
}

private fun creatorTierColor(rank: Int): Color =
    when (rank) {
        1 -> PantopusColors.appTextSecondary
        2 -> PantopusColors.warning
        3 -> PantopusColors.appTextStrong
        4 -> PantopusColors.warning
        else -> PantopusColors.appTextSecondary
    }

private fun creatorTierBgColor(rank: Int): Color =
    when (rank) {
        1 -> PantopusColors.appSurfaceSunken
        2 -> PantopusColors.warningBg
        3 -> PantopusColors.appSurfaceSunken
        4 -> PantopusColors.warningLight
        else -> PantopusColors.appSurfaceSunken
    }

private fun presenceFor(counterparty: ChatCounterparty): Pair<Boolean, String>? = presenceFor(counterparty, false)

private fun presenceFor(
    counterparty: ChatCounterparty,
    isFanThread: Boolean,
): Pair<Boolean, String>? =
    if (isFanThread) {
        val text =
            when (counterparty) {
                is ChatCounterparty.Person -> counterparty.locality?.let { "$it · creator" } ?: "Creator"
                else -> "Creator"
            }
        true to text
    } else {
        when (counterparty) {
            is ChatCounterparty.Person -> {
                val prefix = if (counterparty.online) "Active now" else "Verified neighbor"
                val text = if (counterparty.locality != null) "$prefix · ${counterparty.locality}" else prefix
                counterparty.online to text
            }
            is ChatCounterparty.Group -> counterparty.memberCount?.let { false to "$it members" }
            is ChatCounterparty.Ai -> false to "Replies in seconds · powered by Pantopus AI"
        }
    }

@Composable
private fun HeaderIcon(icon: PantopusIcon) {
    Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
        PantopusIconImage(icon = icon, contentDescription = null, size = 18.dp, tint = PantopusColors.appText)
    }
}

@Composable
private fun HeaderAvatar(
    isAi: Boolean,
    isFanThread: Boolean,
    counterparty: ChatCounterparty,
    tierRank: Int? = null,
) {
    when {
        isAi -> ChatAiAvatar(size = 36.dp)
        isFanThread -> FanPersonaAvatar(initials = fanInitials(counterparty), size = 36.dp)
        counterparty is ChatCounterparty.Person ->
            PersonAvatar(
                initials = counterparty.initials,
                verified = counterparty.verified,
                online = counterparty.online,
                size = 36.dp,
                ringColor = tierRank?.let(::creatorTierColor),
            )
        counterparty is ChatCounterparty.Group ->
            PersonAvatar(
                initials = counterparty.displayName.initials(),
                verified = false,
                online = false,
                size = 36.dp,
                ringColor = tierRank?.let(::creatorTierColor),
            )
        else -> ChatAiAvatar(size = 36.dp)
    }
}

private fun fanInitials(counterparty: ChatCounterparty): String =
    when (counterparty) {
        is ChatCounterparty.Person -> counterparty.initials
        is ChatCounterparty.Group -> counterparty.displayName.initials()
        is ChatCounterparty.Ai -> "AI"
    }

private fun String.initials(): String = split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()

@Composable
private fun PersonAvatar(
    initials: String,
    verified: Boolean,
    online: Boolean,
    size: androidx.compose.ui.unit.Dp,
    ringColor: Color? = null,
) {
    Box(modifier = Modifier.size(size + 4.dp), contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier =
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(PantopusColors.primary600),
            contentAlignment = Alignment.Center,
        ) {
            if (ringColor != null) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .border(2.dp, PantopusColors.appSurface, CircleShape)
                            .padding(2.dp)
                            .border(2.dp, ringColor, CircleShape),
                )
            }
            Text(
                text = initials,
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
        if (verified) {
            Box(
                modifier =
                    Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.primary600)
                        .border(2.dp, PantopusColors.appSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Check,
                    contentDescription = null,
                    size = 7.dp,
                    tint = PantopusColors.appTextInverse,
                )
            }
        }
        if (online) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.success)
                        .border(2.dp, PantopusColors.appSurface, CircleShape),
            )
        }
    }
}

@Composable
private fun FanPersonaAvatar(
    initials: String,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(modifier = Modifier.size(size + 4.dp), contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier =
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(PantopusColors.business),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                fontSize = (size.value * 0.35f).sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
        Box(
            modifier =
                Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.business)
                    .border(2.dp, PantopusColors.appSurface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Check,
                contentDescription = null,
                size = 7.dp,
                tint = PantopusColors.appTextInverse,
            )
        }
    }
}

@Composable
private fun FanMembershipStripe(
    entitlement: ChatFanEntitlement,
    onManage: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(PantopusColors.appSurface)
                .padding(start = 14.dp, end = Spacing.s2)
                .testTag("chatFanMembershipStripe"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.warningBg)
                    .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            PantopusIconImage(icon = PantopusIcon.Crown, contentDescription = null, size = 10.dp, tint = PantopusColors.warning)
            Text(
                text = entitlement.currentTier.uppercase(),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.warning,
            )
        }
        PantopusIconImage(icon = PantopusIcon.Calendar, contentDescription = null, size = 11.dp, tint = PantopusColors.appTextMuted)
        Text(
            text = "renews ${entitlement.renewsOn}",
            fontSize = 11.sp,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier =
                Modifier
                    .heightIn(min = 44.dp)
                    .clickable(onClick = onManage)
                    .padding(horizontal = 6.dp)
                    .testTag("chatFanManageMembership"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "Manage",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.primary600,
            )
            PantopusIconImage(icon = PantopusIcon.ChevronRight, contentDescription = null, size = 11.dp, tint = PantopusColors.primary600)
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorderSubtle))
}

@Composable
private fun FanQuotaGate(
    entitlement: ChatFanEntitlement,
    onUpgrade: () -> Unit,
) {
    val gateColor = if (entitlement.canReply) PantopusColors.primary700 else PantopusColors.warning
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(if (entitlement.canReply) PantopusColors.appSurface else PantopusColors.warningBg)
                .padding(start = 14.dp, end = Spacing.s2)
                .testTag("chatFanQuotaGate"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(if (entitlement.canReply) PantopusColors.infoBg else PantopusColors.appSurface)
                    .border(
                        1.dp,
                        if (entitlement.canReply) PantopusColors.infoLight else PantopusColors.warningLight,
                        RoundedCornerShape(Radii.pill),
                    )
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            PantopusIconImage(icon = PantopusIcon.MessageSquare, contentDescription = null, size = 11.dp, tint = gateColor)
            Text(
                text = "${entitlement.messagesLeft} of ${entitlement.messageLimit} left",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = gateColor,
            )
        }
        Text(
            text = entitlement.requiredReplyTier?.let { "$it required" } ?: entitlement.resetCopy,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = PantopusColors.appTextSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier =
                Modifier
                    .heightIn(min = 44.dp)
                    .clickable(onClick = onUpgrade)
                    .padding(horizontal = 6.dp)
                    .testTag("chatFanQuotaUpgrade"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "Upgrade",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.primary600,
            )
            PantopusIconImage(icon = PantopusIcon.ArrowUpRight, contentDescription = null, size = 11.dp, tint = PantopusColors.primary600)
        }
    }
}

// MARK: - Frames

@Composable
private fun LoadingFrame() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.s4)
                .semantics { contentDescription = "Loading conversation" }
                .testTag("chatConversationLoading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        repeat(6) { index ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    if (index % 2 == 0) Arrangement.End else Arrangement.Start,
            ) {
                Shimmer(
                    width = if (index % 2 == 0) 220.dp else 180.dp,
                    height = if (index % 3 == 0) 60.dp else 40.dp,
                    cornerRadius = Radii.xl,
                )
            }
        }
    }
}

@Composable
internal fun EmptyFrame(
    counterparty: ChatCounterparty,
    aiPrompts: List<ChatPromptChip>,
    emptyChips: List<ChatPromptChip>,
    onChipTap: (ChatPromptChip) -> Unit,
    conversationMode: ChatConversationMode = ChatConversationMode.Dm,
    fanEntitlement: ChatFanEntitlement = ChatConversationSampleData.fanEntitlement,
    onCapabilityTap: (ChatPromptChip) -> Unit = {},
) {
    val isAi = conversationMode == ChatConversationMode.AiAssistant || counterparty is ChatCounterparty.Ai
    when {
        isAi -> AiWelcomeFrame(prompts = aiPrompts, onCapabilityTap = onCapabilityTap)
        conversationMode == ChatConversationMode.FanThread ->
            FanEmptyFrame(
                counterparty = counterparty,
                entitlement = fanEntitlement,
                onOpenerTap = { label -> onChipTap(ChatPromptChip(id = "fan-opener", label = label, icon = PantopusIcon.MessageSquare)) },
            )
        counterparty is ChatCounterparty.Person ->
            PersonEmptyFrame(counterparty = counterparty, chips = emptyChips, onChipTap = onChipTap)
        counterparty is ChatCounterparty.Group ->
            PersonEmptyFrame(
                counterparty =
                    ChatCounterparty.Person(
                        displayName = counterparty.displayName,
                        initials = counterparty.displayName.initials(),
                    ),
                chips = emptyChips,
                onChipTap = onChipTap,
            )
        else -> AiWelcomeFrame(prompts = aiPrompts, onCapabilityTap = onCapabilityTap)
    }
}

@Composable
private fun PersonEmptyFrame(
    counterparty: ChatCounterparty.Person,
    chips: List<ChatPromptChip>,
    onChipTap: (ChatPromptChip) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.s6)
                .testTag("chatConversationEmpty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PersonAvatar(
            initials = counterparty.initials,
            verified = counterparty.verified,
            online = counterparty.online,
            size = 64.dp,
        )
        Spacer(modifier = Modifier.size(18.dp))
        Text(
            text = "Say hi to ${counterparty.displayName.firstWord()}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text =
                counterparty.locality?.let { "You're both verified neighbors on $it. New conversations stay private." }
                    ?: "You're both verified neighbors. New conversations stay private.",
            fontSize = 12.5.sp,
            color = PantopusColors.appTextSecondary,
        )
        Spacer(modifier = Modifier.size(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            chips.forEach { chip ->
                QuickChip(chip = chip, onTap = onChipTap)
            }
        }
        Spacer(modifier = Modifier.size(14.dp))
        EncryptionPill()
    }
}

@Composable
private fun FanEmptyFrame(
    counterparty: ChatCounterparty,
    entitlement: ChatFanEntitlement,
    onOpenerTap: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 14.dp, start = 14.dp, end = 14.dp, bottom = Spacing.s4)
                .testTag("chatConversationFanEmpty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        FanAutoWelcomeCard()
        Text(
            text = "Start a conversation",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text =
                "You can message ${counterparty.displayName.firstWord()} directly. " +
                    "Each send uses one of your monthly ${entitlement.currentTier} replies.",
            fontSize = 12.5.sp,
            color = PantopusColors.appTextSecondary,
        )
        FanQuotaHero(entitlement)
        FanOpeners(onOpenerTap = onOpenerTap)
    }
}

@Composable
private fun FanAutoWelcomeCard(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s3)
                .testTag("chatFanAutoWelcome"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.businessBg)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            PantopusIconImage(icon = PantopusIcon.Sparkles, contentDescription = null, size = 9.dp, tint = PantopusColors.business)
            Text(
                text = "AUTO-WELCOME · FREE",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.business,
            )
        }
        Text(
            text = "Welcome to the Diary, Maria.",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        Text(
            text =
                "First message is on me — ask anything bread-related, share a bake, " +
                    "or just say hi. I read everything personally on Sunday evenings.",
            fontSize = 12.5.sp,
            color = PantopusColors.appTextStrong,
        )
        Text(
            text = "— Wynn",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun FanQuotaHero(entitlement: ChatFanEntitlement) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.infoBg)
                .border(1.dp, PantopusColors.infoLight, RoundedCornerShape(Radii.pill))
                .padding(horizontal = Spacing.s3, vertical = 6.dp)
                .testTag("chatFanQuotaHero"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PantopusIconImage(icon = PantopusIcon.MessageSquare, contentDescription = null, size = 13.dp, tint = PantopusColors.primary700)
        Text(
            text = "${entitlement.messageLimit} messages this period",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.primary700,
        )
        Text(text = "·", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PantopusColors.primary300)
        Text(text = entitlement.resetCopy, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = PantopusColors.primary600)
    }
}

@Composable
private fun FanOpeners(onOpenerTap: (String) -> Unit) {
    val openers =
        listOf(
            Triple(PantopusIcon.HelpCircle, "Recipe question", "Why does my crumb come out tight on day 2?"),
            Triple(PantopusIcon.Image, "Share a bake", "Send a photo for feedback"),
            Triple(PantopusIcon.Calendar, "Workshops", "When's the next hands-on session?"),
        )
    Column(
        modifier = Modifier.fillMaxWidth().testTag("chatFanOpeners"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        openers.forEachIndexed { index, opener ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .clip(RoundedCornerShape(Radii.lg))
                        .background(PantopusColors.appSurface)
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                        .clickable { onOpenerTap(opener.third) }
                        .padding(horizontal = Spacing.s3)
                        .testTag("chatFanOpener_$index"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(Radii.md))
                            .background(PantopusColors.businessBg),
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(icon = opener.first, contentDescription = null, size = 14.dp, tint = PantopusColors.business)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = opener.second.uppercase(),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = PantopusColors.appTextMuted,
                    )
                    Text(
                        text = opener.third,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = PantopusColors.appText,
                    )
                }
                PantopusIconImage(
                    icon = PantopusIcon.ChevronRight,
                    contentDescription = null,
                    size = 14.dp,
                    tint = PantopusColors.appTextMuted,
                )
            }
        }
    }
}

@Composable
private fun QuickChip(
    chip: ChatPromptChip,
    onTap: (ChatPromptChip) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.pill))
                .clickable { onTap(chip) }
                .padding(horizontal = 14.dp)
                .heightIn(min = 32.dp)
                .testTag("chatQuickChip_${chip.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PantopusIconImage(icon = chip.icon, contentDescription = null, size = 13.dp, tint = PantopusColors.primary600)
        Text(
            text = chip.label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appTextStrong,
        )
    }
}

@Composable
private fun EncryptionPill() {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurfaceMuted)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.ShieldCheck,
            contentDescription = null,
            size = Radii.lg,
            tint = PantopusColors.primary600,
        )
        Text(
            text = "DMs end-to-end encrypted between verified addresses",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun AiWelcomeFrame(
    prompts: List<ChatPromptChip>,
    onCapabilityTap: (ChatPromptChip) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 14.dp, start = 14.dp, end = 14.dp)
                .testTag("chatConversationAI"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.xl))
                    .background(PantopusColors.magicBgSoft)
                    .border(1.dp, PantopusColors.magicBorder, RoundedCornerShape(Radii.xl))
                    .padding(14.dp)
                    .testTag("chatAIWelcomeCard"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChatAiAvatar(size = 32.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "Hi — I'm Pantopus AI",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PantopusColors.appText,
                    )
                    Text(
                        text = "I can use your verified neighbors, tasks, and mailbox to help.",
                        fontSize = 11.sp,
                        color = PantopusColors.appTextSecondary,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                prompts.chunked(2).forEach { rowChips ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowChips.forEach { chip ->
                            AiCapabilityChip(
                                chip = chip,
                                onTap = onCapabilityTap,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowChips.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.ShieldCheck,
                contentDescription = null,
                size = 11.dp,
                tint = PantopusColors.success,
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = "Private to your account · never shared with neighbors",
                fontSize = 11.sp,
                color = PantopusColors.appTextSecondary,
            )
        }
    }
}

@Composable
internal fun PopulatedFrame(
    rows: List<ChatTimelineRow>,
    onRetry: (String) -> Unit,
    onLoadOlder: () -> Unit,
    scrollToRowId: String? = null,
    onScrollConsumed: () -> Unit = {},
    conversationMode: ChatConversationMode = ChatConversationMode.Dm,
    incomingInitials: String? = null,
    onLockedAction: () -> Unit = {},
    onBubbleLongPress: (ChatBubbleContent) -> Unit = {},
    selectedMessageIds: Set<String> = emptySet(),
    onBubbleTap: (ChatBubbleContent) -> Unit = {},
    onUseAIDraft: (ChatAIDraftCard) -> Unit = {},
    onOpenGig: (String) -> Unit = {},
    onOpenListing: (String) -> Unit = {},
    onOpenLocation: (Double, Double) -> Unit = { _, _ -> },
) {
    val listState = rememberLazyListState()
    LaunchedEffect(rows.size) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 100) {
            // No-op: keep position when new rows append at bottom.
        }
    }
    // Chat Search deep-link: animate to the matched row once it lands in
    // the list, then clear the target. The +1 offset skips the leading
    // pagination spacer item. Compose honors the system "remove
    // animations" setting, so this is instant under reduced motion.
    LaunchedEffect(scrollToRowId, rows) {
        val target = scrollToRowId ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.rowId == target }
        if (index >= 0) {
            listState.animateScrollToItem(index + 1)
            onScrollConsumed()
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("chatConversationContent"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = Spacing.s3),
    ) {
        item {
            // Top-of-list trigger for cursor pagination backwards.
            LaunchedEffect(rows.firstOrNull()?.rowId) {
                onLoadOlder()
            }
            Spacer(modifier = Modifier.size(1.dp))
        }
        if (conversationMode == ChatConversationMode.FanThread) {
            item(key = "fan_auto_welcome") {
                FanAutoWelcomeCard(modifier = Modifier.padding(bottom = Spacing.s3))
            }
        }
        items(items = rows, key = { it.rowId }) { row ->
            when (row) {
                is ChatTimelineRow.DayDivider -> DayDividerRow(label = row.divider.label)
                is ChatTimelineRow.TopicDivider -> TopicDividerRow(label = row.label)
                is ChatTimelineRow.BroadcastReference -> BroadcastReferenceCard(reference = row.reference)
                is ChatTimelineRow.Bubble ->
                    BubbleRow(
                        content = row.content,
                        incomingInitials = incomingInitials,
                        onLockedAction = onLockedAction,
                        onLongPress = { onBubbleLongPress(row.content) },
                        isSelected = selectedMessageIds.contains(row.content.id),
                        onTap = { onBubbleTap(row.content) },
                        onUseAIDraft = onUseAIDraft,
                        onOpenGig = onOpenGig,
                        onOpenListing = onOpenListing,
                        onOpenLocation = onOpenLocation,
                        onRetry = {
                            if (row.content.id.startsWith("client_")) onRetry(row.content.id)
                        },
                    )
            }
        }
    }
}

@Composable
private fun BroadcastReferenceCard(reference: ChatBroadcastReference) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.business.copy(alpha = 0.18f), RoundedCornerShape(Radii.xl))
                .padding(Spacing.s3)
                .testTag("chatBroadcastReference_${reference.id}"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.businessBg),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.RadioTower,
                contentDescription = null,
                size = 15.dp,
                strokeWidth = 2.5f,
                tint = PantopusColors.business,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Text(
                text = "BROADCAST REFERENCED",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.business,
                maxLines = 1,
            )
            Text(
                text = reference.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = reference.subtitle,
                fontSize = 11.5.sp,
                color = PantopusColors.appTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = reference.metric,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appTextStrong,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DayDividerRow(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(PantopusColors.appBorder))
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appTextSecondary,
        )
        Box(modifier = Modifier.weight(1f).height(1.dp).background(PantopusColors.appBorder))
    }
}

/**
 * Topic-change marker on the unfiltered person thread — same geometry as
 * [DayDividerRow] but primary-tinted so it reads as a topic, not a date.
 */
@Composable
private fun TopicDividerRow(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(PantopusColors.appBorder))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.primary600,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(modifier = Modifier.weight(1f).height(1.dp).background(PantopusColors.appBorder))
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BubbleRow(
    content: ChatBubbleContent,
    incomingInitials: String? = null,
    onLockedAction: () -> Unit,
    onLongPress: () -> Unit,
    isSelected: Boolean = false,
    onTap: () -> Unit = {},
    onUseAIDraft: (ChatAIDraftCard) -> Unit,
    onOpenGig: (String) -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenLocation: (Double, Double) -> Unit,
    onRetry: () -> Unit,
) {
    val isOut = content.side == ChatMessageSide.Outgoing
    // Incoming bubbles are white with a hairline border (RN); outgoing
    // are solid blue. RN caps a bubble at ~75% of the row width — derive
    // it from the screen minus the list's horizontal padding (14dp each).
    val bubbleColor = if (isOut) PantopusColors.primary600 else PantopusColors.appSurface
    val textColor = if (isOut) PantopusColors.appTextInverse else PantopusColors.appText
    val bubbleMaxWidth = ((LocalConfiguration.current.screenWidthDp - 28) * 0.75f).dp
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (isSelected) Modifier.background(PantopusColors.primary50) else Modifier)
                .combinedClickable(onClick = onTap, onLongClick = onLongPress)
                .padding(top = if (content.isContinuation) 2.dp else 8.dp, bottom = if (content.stamp == null) 3.dp else 0.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = if (isOut) Arrangement.End else Arrangement.Start,
    ) {
        if (isOut) {
            if (isSelected) {
                // Selection-mode checkmark in the slot the outgoing layout
                // already reserves on the leading edge.
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    PantopusIconImage(
                        icon = PantopusIcon.CheckCircle,
                        contentDescription = "Selected",
                        size = 18.dp,
                        strokeWidth = 2.4f,
                        tint = PantopusColors.primary600,
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(44.dp))
            }
        } else if (incomingInitials != null && !content.body.isRichCard) {
            MiniAvatar(initials = incomingInitials, hidden = content.isContinuation)
            Spacer(modifier = Modifier.size(8.dp))
        }
        Column(horizontalAlignment = if (isOut) Alignment.End else Alignment.Start) {
            when (val body = content.body) {
                is ChatBubbleBody.Text ->
                    if (isEmojiOnly(body.text)) {
                        // Emoji-only messages render large with no bubble (RN).
                        Text(
                            text = body.text,
                            fontSize = 48.sp,
                            lineHeight = 56.sp,
                            color = textColor,
                        )
                    } else {
                        BubbleContainer(
                            isOut = isOut,
                            hasTail = content.hasTail,
                            bubbleColor = bubbleColor,
                            lockedTier = content.lockedTier?.takeIf { !isOut },
                            onLockedAction = onLockedAction,
                            contentId = content.id,
                        ) {
                            Column(modifier = Modifier.widthIn(max = bubbleMaxWidth)) {
                                ReplyPreview(preview = content.replyPreview, isOut = isOut)
                                Text(
                                    text = body.text,
                                    fontSize = 15.sp,
                                    lineHeight = 21.sp,
                                    color = textColor,
                                )
                            }
                        }
                    }
                is ChatBubbleBody.TextWithImages ->
                    BubbleContainer(
                        isOut = isOut,
                        hasTail = content.hasTail,
                        bubbleColor = bubbleColor,
                        lockedTier = content.lockedTier?.takeIf { !isOut },
                        onLockedAction = onLockedAction,
                        contentId = content.id,
                    ) {
                        Column(modifier = Modifier.widthIn(max = bubbleMaxWidth), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                            if (body.text.isNotBlank()) {
                                Text(text = body.text, fontSize = 14.sp, color = textColor)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                body.imageUrls.take(3).forEach { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "AI prompt image",
                                        contentScale = ContentScale.Crop,
                                        modifier =
                                            Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(Radii.md)),
                                    )
                                }
                            }
                        }
                    }
                is ChatBubbleBody.Image ->
                    PhotoBubble(
                        url = body.url,
                        isOut = isOut,
                        hasTail = content.hasTail,
                        lockedTier = content.lockedTier?.takeIf { !isOut },
                        onLockedAction = onLockedAction,
                        contentId = content.id,
                    )
                is ChatBubbleBody.Attachment ->
                    BubbleContainer(
                        isOut = isOut,
                        hasTail = content.hasTail,
                        bubbleColor = bubbleColor,
                        lockedTier = content.lockedTier?.takeIf { !isOut },
                        onLockedAction = onLockedAction,
                        contentId = content.id,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                            PantopusIconImage(
                                icon = PantopusIcon.File,
                                contentDescription = null,
                                size = 18.dp,
                                tint = if (isOut) PantopusColors.appTextInverse else PantopusColors.primary600,
                            )
                            Text(
                                text = body.filename,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor,
                            )
                        }
                    }
                is ChatBubbleBody.SystemLink -> SystemLinkPill(body)
                is ChatBubbleBody.LocationCard ->
                    ChatLocationCardView(
                        card = body.card,
                        isOutgoing = isOut,
                        onOpen = { onOpenLocation(body.card.latitude, body.card.longitude) },
                    )
                is ChatBubbleBody.GigOfferCard ->
                    ChatGigOfferCardView(
                        card = body.card,
                        isOutgoing = isOut,
                        onOpen = { body.card.gigId.takeIf { it.isNotBlank() }?.let(onOpenGig) },
                    )
                is ChatBubbleBody.ListingOfferCard ->
                    ChatListingOfferCardView(
                        card = body.card,
                        isOutgoing = isOut,
                        onOpen = { body.card.listingId.takeIf { it.isNotBlank() }?.let(onOpenListing) },
                    )
                is ChatBubbleBody.AiReply -> AiReplyBubble(body = body, hasTail = content.hasTail, onUseDraft = onUseAIDraft)
            }
            if (content.sentSupportTier != null && isOut) {
                PaidSupportFooter(tier = content.sentSupportTier, contentId = content.id)
            }
            if (content.reactions.isNotEmpty()) {
                ReactionRow(reactions = content.reactions, onReact = {})
            }
            if (content.stamp != null) {
                StampRow(content = content, onRetry = onRetry)
            }
        }
        if (!isOut) {
            Spacer(modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun ReplyPreview(
    preview: ChatReplyPreview?,
    isOut: Boolean,
) {
    if (preview == null) return
    Row(
        modifier = Modifier.padding(bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .widthIn(min = 3.dp, max = 3.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(Radii.xs))
                    .background(if (isOut) PantopusColors.appTextInverse.copy(alpha = 0.45f) else PantopusColors.primary600),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = preview.senderName,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOut) PantopusColors.appTextInverse.copy(alpha = 0.9f) else PantopusColors.primary600,
                maxLines = 1,
            )
            Text(
                text = preview.text,
                fontSize = 11.sp,
                color = if (isOut) PantopusColors.appTextInverse.copy(alpha = 0.72f) else PantopusColors.appTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * True when [raw] is 1–N emoji and nothing else — those render large
 * with no bubble (RN / iOS parity). Mirrors the iOS `isEmojiOnly`:
 * non-empty, <= 30 chars, every code point an emoji (allowing variation
 * selectors, ZWJ, and skin-tone modifiers as connective glue).
 */
private fun isEmojiOnly(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.length > 30) return false
    var sawEmoji = false
    var index = 0
    while (index < trimmed.length) {
        val cp = trimmed.codePointAt(index)
        index += Character.charCount(cp)
        when {
            isEmojiCodePoint(cp) -> sawEmoji = true
            // Glue that may appear between emoji: VS16, ZWJ, skin tones.
            cp == 0xFE0F || cp == 0x200D || cp in 0x1F3FB..0x1F3FF -> Unit
            else -> return false
        }
    }
    return sawEmoji
}

private fun isEmojiCodePoint(cp: Int): Boolean =
    cp in 0x1F300..0x1FAFF || // symbols & pictographs, supplemental, extended-A
        cp in 0x1F1E6..0x1F1FF || // regional indicators (flags)
        cp in 0x2600..0x27BF || // misc symbols + dingbats
        cp in 0x2300..0x23FF || // misc technical (⌚ ⏰ …)
        cp == 0x2B50 || cp == 0x2B55 || // ⭐ ⭕
        cp in 0x2190..0x21FF // arrows used as emoji

@Composable
private fun BubbleContainer(
    isOut: Boolean,
    hasTail: Boolean,
    bubbleColor: Color,
    lockedTier: String? = null,
    onLockedAction: () -> Unit = {},
    contentId: String = "",
    inner: @Composable () -> Unit,
) {
    val shape =
        RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomEnd = if (isOut && hasTail) 4.dp else 18.dp,
            bottomStart = if (!isOut && hasTail) 4.dp else 18.dp,
        )
    Box(
        modifier =
            Modifier
                .clip(shape)
                .background(bubbleColor)
                .then(
                    if (!isOut) Modifier.border(1.dp, PantopusColors.appBorder, shape) else Modifier,
                ),
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            inner()
        }
        if (lockedTier != null) {
            LockedPaywallOverlay(
                tier = lockedTier,
                contentId = contentId,
                onLockedAction = onLockedAction,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ComposerContextBanner(
    reply: ChatReplyPreview?,
    editing: Boolean,
    onCancel: () -> Unit,
) {
    if (reply == null && !editing) return
    // RN reply/edit bars: tinted bar with a 3dp accent stripe.
    val accent = if (editing) PantopusColors.success else PantopusColors.primary600
    val barBackground = if (editing) PantopusColors.successBg else PantopusColors.primary50
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(barBackground)
                .padding(start = Spacing.s3, end = Spacing.s1)
                .testTag("chatComposerContext"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = if (editing) "Editing message" else "Replying to ${reply?.senderName.orEmpty()}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
            )
            Text(
                text = if (editing) "Make your changes, then send." else reply?.text.orEmpty(),
                fontSize = 13.sp,
                color = PantopusColors.appTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.X,
                contentDescription = "Cancel message action",
                size = 14.dp,
                tint = PantopusColors.appTextSecondary,
            )
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
}

@Composable
private fun TopicStrip(
    topics: List<ChatConversationTopic>,
    selectedTopicId: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .background(PantopusColors.appSurface)
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2)
                .testTag("chatTopicStrip"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopicChip(title = "All", icon = PantopusIcon.MessageCircle, selected = selectedTopicId == null) {
            onSelect(null)
        }
        topics.forEach { topic ->
            TopicChip(
                title = topic.title,
                icon = topicIcon(topic.topicType),
                selected = selectedTopicId == topic.id,
            ) {
                onSelect(topic.id)
            }
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
}

@Composable
private fun TopicChip(
    title: String,
    icon: PantopusIcon,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(Radii.pill))
                .background(if (selected) PantopusColors.primary50 else PantopusColors.appSurfaceSunken)
                // RN: only the active chip carries a border (primary200).
                .then(
                    if (selected) {
                        Modifier.border(1.dp, PantopusColors.primary200, RoundedCornerShape(Radii.pill))
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        val color = if (selected) PantopusColors.primary600 else PantopusColors.appTextSecondary
        PantopusIconImage(icon = icon, contentDescription = null, size = Radii.lg, strokeWidth = 2.4f, tint = color)
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = color,
            maxLines = 1,
        )
    }
}

private fun topicIcon(type: String): PantopusIcon =
    when (type) {
        "task" -> PantopusIcon.Briefcase
        "listing" -> PantopusIcon.Tag
        "home" -> PantopusIcon.Home
        "business" -> PantopusIcon.Building2
        else -> PantopusIcon.MessageCircle
    }

/** Raw text for the clipboard — `null` hides the Copy action for non-text bodies. */
private val ChatBubbleBody.copyableText: String?
    get() =
        when (this) {
            is ChatBubbleBody.Text -> text.takeIf { it.isNotBlank() }
            is ChatBubbleBody.TextWithImages -> text.takeIf { it.isNotBlank() }
            is ChatBubbleBody.AiReply -> text.takeIf { it.isNotBlank() }
            else -> null
        }

@Composable
private fun MessageActionSheet(
    content: ChatBubbleContent,
    // Invoked after the text has landed on the clipboard — dismisses the sheet.
    onCopy: () -> Unit,
    onReply: () -> Unit,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReact: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val copyText = content.body.copyableText
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            listOf("👍", "❤️", "😂", "🔥").forEach { reaction ->
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PantopusColors.appSurfaceSunken)
                            .clickable { onReact(reaction) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = reaction, fontSize = 20.sp)
                }
            }
        }
        if (copyText != null) {
            MessageActionRow(
                label = "Copy",
                icon = PantopusIcon.Copy,
                onClick = {
                    clipboard.setText(AnnotatedString(copyText))
                    onCopy()
                },
            )
        }
        MessageActionRow(label = "Reply", icon = PantopusIcon.Reply, onClick = onReply)
        if (content.side == ChatMessageSide.Outgoing && !content.id.startsWith("client_")) {
            MessageActionRow(label = "Select", icon = PantopusIcon.CheckCircle, onClick = onSelect)
            MessageActionRow(label = "Edit", icon = PantopusIcon.Pencil, onClick = onEdit)
            MessageActionRow(label = "Delete", icon = PantopusIcon.Trash2, destructive = true, onClick = onDelete)
        }
    }
}

@Composable
private fun MessageActionRow(
    label: String,
    icon: PantopusIcon,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        val color = if (destructive) PantopusColors.error else PantopusColors.appTextStrong
        PantopusIconImage(icon = icon, contentDescription = null, size = 18.dp, tint = color)
        Text(text = label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

// MARK: - Selection mode chrome

@Composable
private fun SelectionTopBar(
    count: Int,
    onCancel: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(PantopusColors.appSurface)
                .padding(horizontal = Spacing.s4)
                .testTag("chatSelectionTopBar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$count selected",
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
        )
        Text(
            text = "Cancel",
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.md))
                    .clickable(onClick = onCancel)
                    .padding(Spacing.s2)
                    .testTag("chatSelectionCancel"),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.primary600,
        )
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
}

@Composable
private fun SelectionDeleteBar(
    enabled: Boolean,
    onDelete: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s3)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(if (enabled) PantopusColors.error else PantopusColors.appSurfaceSunken)
                    .clickable(enabled = enabled, onClick = onDelete)
                    .padding(vertical = Spacing.s3)
                    .testTag("chatSelectionDelete"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Trash2,
                contentDescription = null,
                size = 18.dp,
                tint = if (enabled) PantopusColors.appTextInverse else PantopusColors.appTextMuted,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Delete",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) PantopusColors.appTextInverse else PantopusColors.appTextMuted,
            )
        }
    }
}

// MARK: - Pre-bid send limit banner

@Composable
private fun SendLimitNoticeBanner(
    text: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PantopusColors.warningBg)
                .padding(start = Spacing.s3)
                .testTag("chatSendLimitNotice"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertTriangle,
            contentDescription = null,
            size = 15.dp,
            tint = PantopusColors.warning,
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f).padding(vertical = Spacing.s2),
            fontSize = 13.sp,
            color = PantopusColors.warning,
        )
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .testTag("chatSendLimitNoticeDismiss"),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.X,
                contentDescription = "Dismiss notice",
                size = 14.dp,
                tint = PantopusColors.warning,
            )
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
}

// MARK: - Conversation details

/**
 * Conversation-details drawer (person threads): topics filed under the
 * conversation plus the Safety section. Mirrors the RN drawer
 * (`apps/mobile/src/app/chat/conversation/[otherUserId].tsx`), minus the
 * Report row — there is no report endpoint yet; add it in Phase 4.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationDetailsSheet(
    counterpartyName: String,
    topics: List<ChatConversationTopic>,
    isBlocking: Boolean,
    onDismiss: () -> Unit,
    onBlock: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PantopusColors.appSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.s4)
                    .padding(bottom = Spacing.s6)
                    .testTag("chatDetailsSheet"),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Text(
                text = "Conversation details",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appText,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "TOPICS",
                modifier = Modifier.padding(top = Spacing.s2),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appTextSecondary,
            )
            if (topics.isEmpty()) {
                Text(
                    text = "Topics appear when you chat about a task or listing.",
                    fontSize = 14.sp,
                    color = PantopusColors.appTextMuted,
                )
            } else {
                topics.forEach { topic -> DetailsTopicRow(topic = topic) }
            }
            Text(
                text = "SAFETY",
                modifier = Modifier.padding(top = Spacing.s3),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appTextSecondary,
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(Radii.lg))
                        .clickable(enabled = !isBlocking, onClick = onBlock)
                        .padding(horizontal = Spacing.s1)
                        .testTag("chatDetailsBlock"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Ban,
                    contentDescription = null,
                    size = 18.dp,
                    tint = PantopusColors.error,
                )
                Text(
                    text = "Block $counterpartyName",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.error,
                )
                if (isBlocking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = PantopusColors.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsTopicRow(topic: ChatConversationTopic) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .padding(horizontal = Spacing.s1, vertical = Spacing.s2)
                .testTag("chatDetailsTopic_${topic.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        PantopusIconImage(
            icon = topicIcon(topic.topicType),
            contentDescription = null,
            size = 18.dp,
            tint = PantopusColors.primary600,
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = topic.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = PantopusColors.appText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            topic.status?.takeIf { it.isNotBlank() }?.let { status ->
                Text(
                    text = status.replaceFirstChar { it.uppercase() },
                    fontSize = 12.sp,
                    color = PantopusColors.appTextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

// MARK: - Emoji picker

/** Curated quick-picker set for the composer (8 columns × 8 rows). */
private val CHAT_COMPOSER_EMOJI =
    listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
        "😊", "😇", "🙂", "😉", "😍", "🥰", "😘", "😎",
        "🤩", "🥳", "😏", "😴", "🤔", "🤨", "😬", "🙄",
        "😢", "😭", "😤", "😡", "🤯", "😱", "🥺", "😳",
        "👍", "👎", "👏", "🙏", "🤝", "💪", "✌️", "🤞",
        "👋", "🙌", "👐", "🤲", "🤜", "🤛", "✊", "👊",
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "💕",
        "🎉", "🎊", "🔥", "✨", "⭐", "💯", "✅", "🙈",
    )

/**
 * Compact emoji grid for the composer's emoji button. Picking appends to
 * the composer text and keeps the sheet open for multi-pick.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatEmojiPickerSheet(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PantopusColors.appSurface,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(330.dp)
                    .padding(horizontal = Spacing.s3)
                    .padding(bottom = Spacing.s4)
                    .testTag("chatEmojiPicker"),
        ) {
            items(CHAT_COMPOSER_EMOJI.size) { index ->
                val emoji = CHAT_COMPOSER_EMOJI[index]
                Box(
                    modifier =
                        Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(Radii.md))
                            .clickable { onPick(emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = emoji, fontSize = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun ReactionRow(
    reactions: List<ChatBubbleReaction>,
    onReact: (String) -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = Spacing.s1).testTag("chatReactions"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        reactions.forEach { reaction ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.lg))
                        .background(if (reaction.reactedByMe) PantopusColors.primary50 else PantopusColors.appSurfaceSunken)
                        // RN: only the reacted-by-me pill carries a border (blue).
                        .then(
                            if (reaction.reactedByMe) {
                                Modifier.border(1.dp, PantopusColors.primary600, RoundedCornerShape(Radii.lg))
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onReact(reaction.reaction) }
                        .padding(horizontal = Spacing.s2, vertical = 3.dp),
            ) {
                Text(text = reaction.reaction, fontSize = 14.sp)
                Text(
                    text = reaction.count.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (reaction.reactedByMe) PantopusColors.primary600 else PantopusColors.appTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun LockedPaywallOverlay(
    tier: String,
    contentId: String,
    onLockedAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                PantopusColors.appSurface.copy(alpha = 0.15f),
                                PantopusColors.appSurface.copy(alpha = 0.96f),
                            ),
                    ),
                )
                .testTag("chatPaywallOverlay_$contentId"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.appSurface)
                    .clickable(onClick = onLockedAction)
                    .padding(horizontal = 10.dp)
                    .testTag("chatLockedUpgrade_$contentId"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PantopusIconImage(icon = PantopusIcon.Lock, contentDescription = null, size = Radii.lg, tint = PantopusColors.primary600)
            Text(
                text = "Upgrade to read",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.primary600,
            )
            Text(
                text = tier,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextSecondary,
            )
        }
    }
}

@Composable
private fun PaidSupportFooter(
    tier: String,
    contentId: String,
) {
    Row(
        modifier =
            Modifier
                .padding(top = Spacing.s1)
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.warningBg)
                .padding(horizontal = 9.dp, vertical = Spacing.s1)
                .testTag("chatPaidSupportFooter_$contentId"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        PantopusIconImage(icon = PantopusIcon.Crown, contentDescription = null, size = 10.dp, tint = PantopusColors.warning)
        Text(
            text = "Sent with $tier support",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.warning,
        )
    }
}

@Composable
private fun MiniAvatar(
    initials: String,
    hidden: Boolean,
) {
    Box(
        modifier =
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (hidden) Color.Transparent else PantopusColors.primary600),
        contentAlignment = Alignment.Center,
    ) {
        if (!hidden) {
            Text(
                text = initials,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
    }
}

@Composable
private fun PhotoBubble(
    url: String?,
    isOut: Boolean,
    hasTail: Boolean,
    lockedTier: String? = null,
    onLockedAction: () -> Unit = {},
    contentId: String = "",
) {
    val shape =
        RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomEnd = if (isOut && hasTail) 4.dp else 18.dp,
            bottomStart = if (!isOut && hasTail) 4.dp else 18.dp,
        )
    Box(
        modifier =
            Modifier
                .size(200.dp, 130.dp)
                .clip(shape)
                .background(PantopusColors.appSurfaceSunken)
                .border(1.dp, if (isOut) Color.Transparent else PantopusColors.appBorder, shape)
                .testTag("chatPhotoBubble"),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = "Photo attachment",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PhotoPlaceholder(modifier = Modifier.fillMaxSize())
        }
        if (lockedTier != null) {
            LockedPaywallOverlay(
                tier = lockedTier,
                contentId = contentId,
                onLockedAction = onLockedAction,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PhotoPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(PantopusColors.appSurfaceSunken)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = Spacing.s2),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(6) { index ->
                Box(
                    modifier =
                        Modifier
                            .size(width = 18.dp, height = 150.dp)
                            .clip(RoundedCornerShape(Radii.xs))
                            .background(
                                if (index % 2 == 0) {
                                    PantopusColors.appBorder
                                } else {
                                    PantopusColors.appBorderStrong
                                },
                            ),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = Spacing.s2)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.appSurface.copy(alpha = 0.72f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                text = "Photo",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appTextSecondary,
            )
        }
    }
}

@Composable
private fun AiReplyBubble(
    body: ChatBubbleBody.AiReply,
    hasTail: Boolean,
    onUseDraft: (ChatAIDraftCard) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomEnd = 18.dp,
                        bottomStart = if (hasTail) 4.dp else 18.dp,
                    ),
                )
                .background(PantopusColors.appSurfaceSunken)
                .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        AiTag()
        Text(text = body.text, fontSize = 14.sp, color = PantopusColors.appText)
        body.estimate?.let { AiEstimateCard(estimate = it, modifier = Modifier.fillMaxWidth()) }
        body.drafts.forEach { draft ->
            AiDraftCard(draft = draft, onUse = onUseDraft)
        }
    }
}

@Composable
private fun AiDraftCard(
    draft: ChatAIDraftCard,
    onUse: (ChatAIDraftCard) -> Unit,
) {
    val accent =
        when (draft.type) {
            "listing" -> PantopusColors.success
            "mail_summary" -> PantopusColors.warning
            else -> PantopusColors.magic
        }
    val background =
        when (draft.type) {
            "listing" -> PantopusColors.successBg
            "mail_summary" -> PantopusColors.warningBg
            else -> PantopusColors.magicBg
        }
    val icon =
        when (draft.type) {
            "gig" -> PantopusIcon.Hammer
            "listing" -> PantopusIcon.Tag
            "post" -> PantopusIcon.Pencil
            "mail_summary" -> PantopusIcon.Mailbox
            else -> PantopusIcon.Sparkles
        }
    val label =
        when (draft.type) {
            "gig" -> "Task Draft"
            "listing" -> "Listing Draft"
            "post" -> "Post Draft"
            "mail_summary" -> "Mail Summary"
            else -> "Draft"
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(Radii.lg))
                .padding(Spacing.s3)
                .testTag("chatAIDraft_${draft.type}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            PantopusIconImage(
                icon = icon,
                contentDescription = null,
                size = 14.dp,
                tint = accent,
                modifier =
                    Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .background(background)
                        .padding(6.dp),
            )
            Text(text = label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent)
            Spacer(modifier = Modifier.weight(1f))
            if (draft.valid) {
                Text(text = "Draft ready", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.success)
            }
        }
        Text(text = draft.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appText, maxLines = 2)
        if (!draft.summary.isNullOrBlank() && draft.summary != draft.title) {
            Text(
                text = draft.summary,
                fontSize = 11.5.sp,
                color = PantopusColors.appTextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        draft.priceLabel?.let {
            Text(text = it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        if (draft.type != "mail_summary") {
            Row(
                modifier =
                    Modifier
                        .heightIn(min = 34.dp)
                        .clickable { onUse(draft) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(text = draftActionTitle(draft.type), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
                PantopusIconImage(icon = PantopusIcon.ArrowRight, contentDescription = null, size = 11.dp, tint = accent)
            }
        }
    }
}

private fun draftActionTitle(type: String): String =
    when (type) {
        "gig" -> "Use in task composer"
        "listing" -> "Use in listing composer"
        "post" -> "Use in Pulse composer"
        else -> "Use draft"
    }

@Composable
private fun AiTag() {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.magicBg)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        PantopusIconImage(icon = PantopusIcon.Bot, contentDescription = null, size = 9.dp, tint = PantopusColors.magic)
        Text(
            text = "PANTOPUS AI",
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.magic,
        )
    }
}

@Composable
private fun SystemLinkPill(body: ChatBubbleBody.SystemLink) {
    val fg =
        when (body.accent) {
            ChatSystemLinkAccent.Primary -> PantopusColors.primary600
            ChatSystemLinkAccent.Success -> PantopusColors.success
            ChatSystemLinkAccent.Warning -> PantopusColors.warning
            ChatSystemLinkAccent.Error -> PantopusColors.error
        }
    val bg =
        when (body.accent) {
            ChatSystemLinkAccent.Primary -> PantopusColors.primary50
            ChatSystemLinkAccent.Success -> PantopusColors.successBg
            ChatSystemLinkAccent.Warning -> PantopusColors.warningBg
            ChatSystemLinkAccent.Error -> PantopusColors.errorBg
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.s1)
                .clip(RoundedCornerShape(Radii.pill))
                .background(bg)
                .border(1.dp, fg.copy(alpha = 0.2f), RoundedCornerShape(Radii.pill))
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(fg),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Info,
                contentDescription = null,
                size = Radii.lg,
                tint = PantopusColors.appTextInverse,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = body.label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = PantopusColors.appTextSecondary,
            )
            Text(
                text = body.sub,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PantopusIconImage(icon = PantopusIcon.ChevronRight, contentDescription = null, size = 13.dp, tint = fg)
    }
}

@Composable
private fun StampRow(
    content: ChatBubbleContent,
    onRetry: () -> Unit,
) {
    val stamp = content.stamp ?: return
    val raw =
        when (content.deliveryState) {
            ChatDeliveryState.Read -> "Read $stamp"
            // Delivered is conveyed by the small check icon, not a loud word (RN).
            ChatDeliveryState.Delivered -> stamp
            ChatDeliveryState.Sending -> "Sending..."
            ChatDeliveryState.Failed -> "Failed to send"
            null -> stamp
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            if (content.side == ChatMessageSide.Outgoing) Arrangement.End else Arrangement.Start,
    ) {
        if (content.side == ChatMessageSide.Outgoing && content.deliveryState == ChatDeliveryState.Read) {
            ReadReceipt(timestamp = stamp)
        } else {
            Text(
                text = raw,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                color = PantopusColors.appTextMuted,
            )
        }
        if (content.side == ChatMessageSide.Outgoing && content.deliveryState != ChatDeliveryState.Read) {
            when (content.deliveryState) {
                ChatDeliveryState.Read -> Unit
                ChatDeliveryState.Delivered ->
                    PantopusIconImage(
                        icon = PantopusIcon.Check,
                        contentDescription = null,
                        size = 10.dp,
                        tint = PantopusColors.appTextMuted,
                        modifier = Modifier.padding(start = Spacing.s1),
                    )
                ChatDeliveryState.Sending ->
                    CircularProgressIndicator(
                        modifier = Modifier.padding(start = Spacing.s1).size(10.dp),
                        strokeWidth = 1.5.dp,
                        color = PantopusColors.appTextSecondary,
                    )
                ChatDeliveryState.Failed ->
                    Row(
                        modifier =
                            Modifier
                                .padding(start = 6.dp)
                                .clickable(onClick = onRetry)
                                .testTag("chatRetry_${content.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        PantopusIconImage(
                            icon = PantopusIcon.AlertCircle,
                            contentDescription = null,
                            size = 11.dp,
                            tint = PantopusColors.error,
                        )
                        Text(
                            text = "Retry",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            textDecoration = TextDecoration.Underline,
                            color = PantopusColors.error,
                        )
                    }
                null -> Unit
            }
        }
    }
}

@Composable
private fun ReadReceipt(timestamp: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Text(
            text = "Read $timestamp",
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            color = PantopusColors.appTextMuted,
        )
        PantopusIconImage(
            icon = PantopusIcon.CheckCheck,
            contentDescription = null,
            size = Radii.lg,
            strokeWidth = 2.5f,
            tint = PantopusColors.primary600,
        )
    }
}

@Composable
internal fun TypingIndicator(initials: String) {
    val transition = rememberInfiniteTransition(label = "typingDots")
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = Spacing.s2, end = 14.dp, bottom = 6.dp)
                .testTag("chatTypingIndicator"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        MiniAvatar(initials = initials, hidden = false)
        Row(
            modifier =
                Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = Radii.xl,
                            topEnd = Radii.xl,
                            bottomEnd = Radii.xl,
                            bottomStart = Radii.xs,
                        ),
                    )
                    .background(PantopusColors.appSurfaceSunken)
                    .border(
                        1.dp,
                        PantopusColors.appBorder,
                        RoundedCornerShape(
                            topStart = Radii.xl,
                            topEnd = Radii.xl,
                            bottomEnd = Radii.xl,
                            bottomStart = Radii.xs,
                        ),
                    )
                    .padding(horizontal = Spacing.s3, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            repeat(3) { index ->
                val opacity by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 600),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset =
                                StartOffset(
                                    offsetMillis = index * 180,
                                    offsetType = StartOffsetType.Delay,
                                ),
                        ),
                    label = "typingDot$index",
                )
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(PantopusColors.appTextSecondary.copy(alpha = opacity)),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun Composer(
    text: String,
    placeholder: String,
    canSend: Boolean,
    showsSendCost: Boolean = false,
    isLockedAction: Boolean = false,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit = {},
    onEmoji: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = Spacing.s2, bottom = Spacing.s4),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onAttach)
                        .testTag("chatComposerAttach"),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PantopusColors.primary50),
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.Plus,
                        contentDescription = "Attach",
                        size = Spacing.s5,
                        strokeWidth = 2.4f,
                        tint = PantopusColors.primary600,
                    )
                }
            }
            // RN order: [+] → emoji → rounded input → send.
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onEmoji)
                        .testTag("chatComposerEmoji"),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Smile,
                    contentDescription = "Emoji",
                    size = 18.dp,
                    tint = PantopusColors.appTextMuted,
                )
            }
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(Radii.xl2))
                        .background(PantopusColors.appSurfaceSunken)
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 15.sp,
                            color = PantopusColors.appTextMuted,
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle =
                            TextStyle(
                                fontSize = 15.sp,
                                color = PantopusColors.appText,
                            ),
                        cursorBrush = SolidColor(PantopusColors.primary600),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(enabled = canSend, onClick = onSend)
                        .testTag("chatComposerSend"),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(sendBackground(canSend = canSend, isLockedAction = isLockedAction)),
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = if (isLockedAction) PantopusIcon.Lock else PantopusIcon.Send,
                        contentDescription = "Send",
                        size = 18.dp,
                        strokeWidth = 2.5f,
                        tint = if (canSend) PantopusColors.appTextInverse else PantopusColors.appTextMuted,
                    )
                }
                if (showsSendCost && canSend) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .clip(RoundedCornerShape(Radii.pill))
                                .background(PantopusColors.appSurface)
                                .border(1.dp, PantopusColors.primary600, RoundedCornerShape(Radii.pill))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "-1",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PantopusColors.primary700,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AttachmentStripView(
    attachments: List<ChatQueuedAttachment>,
    onRemove: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface).testTag("chatAttachmentStrip")) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
        Row(
            modifier =
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = Spacing.s3, end = Spacing.s3, top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            attachments.forEach { attachment ->
                AttachmentTile(attachment = attachment, onRemove = onRemove)
            }
        }
    }
}

@Composable
private fun AttachmentTile(
    attachment: ChatQueuedAttachment,
    onRemove: (String) -> Unit,
) {
    Box(modifier = Modifier.size(64.dp)) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.appSurfaceSunken)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg)),
            contentAlignment = Alignment.Center,
        ) {
            when (attachment.kind) {
                ChatQueuedAttachmentKind.Image -> PhotoPlaceholder(modifier = Modifier.fillMaxSize())
                ChatQueuedAttachmentKind.Document ->
                    Column(
                        modifier = Modifier.fillMaxSize().background(PantopusColors.appSurface),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        PantopusIconImage(
                            icon = PantopusIcon.FileText,
                            contentDescription = null,
                            size = 22.dp,
                            tint = PantopusColors.primary600,
                        )
                        Text(
                            text = attachment.filename,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PantopusColors.appTextStrong,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
            }
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(44.dp)
                    .clickable { onRemove(attachment.id) }
                    .testTag("chatAttachmentRemove_${attachment.id}"),
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(top = 3.dp, end = 3.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.appText.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.X,
                    contentDescription = "Remove ${attachment.filename}",
                    size = 11.dp,
                    strokeWidth = 3f,
                    tint = PantopusColors.appTextInverse,
                )
            }
        }
    }
}

private fun sendBackground(
    canSend: Boolean,
    isLockedAction: Boolean,
): Color =
    when {
        !canSend -> PantopusColors.appSurfaceSunken
        isLockedAction -> PantopusColors.warning
        else -> PantopusColors.primary600
    }

@Composable
internal fun FanTierUpgradePromptSheet(entitlement: ChatFanEntitlement) {
    val targetTier = entitlement.requiredReplyTier ?: "Silver"
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .padding(start = Spacing.s5, end = Spacing.s5, top = Spacing.s3, bottom = Spacing.s6)
                .testTag("chatFanUpgradePromptSheet"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(PantopusColors.primary50),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(icon = PantopusIcon.Lock, contentDescription = null, size = Radii.xl2, tint = PantopusColors.primary600)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = "Upgrade to $targetTier",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appText,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Your ${entitlement.currentTier} support does not unlock this reply yet.",
                    fontSize = 12.5.sp,
                    color = PantopusColors.appTextSecondary,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FanUpgradeBenefit(icon = PantopusIcon.MessageSquare, title = "Read tier-locked replies")
            FanUpgradeBenefit(icon = PantopusIcon.ArrowUpRight, title = "Reply without the current tier gate")
            FanUpgradeBenefit(icon = PantopusIcon.Crown, title = "Keep supporting this creator")
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.primary600)
                    .clickable {}
                    .testTag("chatFanUpgradeConfirm"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Upgrade membership",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
    }
}

@Composable
private fun FanUpgradeBenefit(
    icon: PantopusIcon,
    title: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(PantopusColors.primary50),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = icon, contentDescription = null, size = 14.dp, tint = PantopusColors.primary600)
        }
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appTextStrong,
        )
    }
}

@Composable
private fun ErrorFrame(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.s6)
                .testTag("chatConversationError"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 40.dp,
            tint = PantopusColors.error,
        )
        Spacer(modifier = Modifier.size(Spacing.s3))
        Text(
            text = "Couldn't load this conversation",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        Spacer(modifier = Modifier.size(Spacing.s2))
        Text(text = message, fontSize = 13.5.sp, color = PantopusColors.appTextSecondary)
        Spacer(modifier = Modifier.size(Spacing.s4))
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.primary600)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 22.dp)
                    .heightIn(min = 44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Try again",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
    }
}
