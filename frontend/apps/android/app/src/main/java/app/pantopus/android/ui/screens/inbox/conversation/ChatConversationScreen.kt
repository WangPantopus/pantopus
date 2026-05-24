@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "CyclomaticComplexMethod")

package app.pantopus.android.ui.screens.inbox.conversation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.screens.inbox.conversation.ai.AiCapabilityChip
import app.pantopus.android.ui.screens.inbox.conversation.ai.AiEstimateCard
import app.pantopus.android.ui.screens.inbox.conversation.ai.ChatAiAvatar
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import coil.compose.AsyncImage

/**
 * Chat conversation screen (T2.2). Three frames: shimmer loading,
 * counterparty-specific empty state, populated thread. Composer has
 * attach + send discs; send is color-bound to text presence and
 * disabled while in flight.
 */
@Composable
fun ChatConversationScreen(
    args: ChatConversationRouteArgs,
    conversationMode: ChatConversationMode = ChatConversationMode.Dm,
    creatorChrome: ChatCreatorThreadChrome? = null,
    onBack: () -> Unit = {},
    viewModel: ChatConversationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeCounterparty by viewModel.counterparty.collectAsStateWithLifecycle()
    val composerText by viewModel.composerText.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val isCounterpartyTyping by viewModel.isCounterpartyTyping.collectAsStateWithLifecycle()
    val queuedAttachments by viewModel.queuedAttachments.collectAsStateWithLifecycle()
    val pendingScroll by viewModel.pendingScrollTarget.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.configure(args.mode, args.counterparty, args.currentUserId, args.scrollToMessageId)
        viewModel.load()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.teardown() }
    }
    val resolvedCreatorContext = creatorChrome?.context ?: ChatCreatorThreadContext.defaults()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appSurface)
                .testTag("chatConversation"),
    ) {
        ChatHeader(
            conversationMode = conversationMode,
            counterparty = activeCounterparty,
            creatorContext = resolvedCreatorContext,
            onBack = onBack,
        )
        if (conversationMode == ChatConversationMode.CreatorThread) {
            CreatorAudienceStrip(
                context = resolvedCreatorContext,
                onOpenAudienceProfile = creatorChrome?.onOpenAudienceProfile ?: {},
            )
            CreatorQuotaMeter(quota = resolvedCreatorContext.quota)
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
                        onCapabilityTap = viewModel::sendCapabilityPrompt,
                    )
                is ChatConversationUiState.Loaded ->
                    PopulatedFrame(
                        rows = s.rows,
                        onRetry = viewModel::retry,
                        onLoadOlder = viewModel::loadOlder,
                        scrollToRowId = pendingScroll,
                        onScrollConsumed = viewModel::consumePendingScroll,
                        incomingInitials = incomingInitialsFor(conversationMode, activeCounterparty),
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
        Composer(
            text = composerText,
            placeholder = composerPlaceholder(conversationMode, activeCounterparty),
            canSend = composerText.isNotBlank() && !isSending,
            onTextChange = viewModel::setComposerText,
            onSend = viewModel::send,
            onAttach = viewModel::queueSampleAttachments,
        )
    }
}

private fun composerPlaceholder(
    conversationMode: ChatConversationMode,
    c: ChatCounterparty,
): String =
    if (conversationMode == ChatConversationMode.AiAssistant) {
        "Ask Pantopus AI…"
    } else {
        when (c) {
            is ChatCounterparty.Ai -> "Ask Pantopus AI…"
            is ChatCounterparty.Group -> "Message ${c.displayName.firstWord()}…"
            is ChatCounterparty.Person -> "Message ${c.displayName.firstWord()}…"
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
) {
    val isAi = conversationMode == ChatConversationMode.AiAssistant || counterparty is ChatCounterparty.Ai
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
                size = 20.dp,
                tint = PantopusColors.appText,
            )
        }
        HeaderAvatar(
            isAi = isAi,
            counterparty = counterparty,
            tierRank = if (isCreator) creatorContext.fanTierRank else null,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = counterparty.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isAi) BetaPill()
                if (isCreator) {
                    CreatorTierChip(name = creatorContext.fanTierName, rank = creatorContext.fanTierRank)
                }
            }
            val presence =
                if (isCreator) {
                    (creatorContext.fanTierRank > 1) to creatorContext.fanSubtitle
                } else {
                    presenceFor(counterparty)
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
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = PantopusColors.appTextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
        if (isCreator) {
            Row {
                HeaderIcon(PantopusIcon.User)
                HeaderIcon(PantopusIcon.MoreHorizontal)
            }
        } else {
            when (counterparty) {
                is ChatCounterparty.Person -> {
                    Row {
                        HeaderIcon(PantopusIcon.Phone)
                        HeaderIcon(PantopusIcon.Video)
                        HeaderIcon(PantopusIcon.MoreVertical)
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
internal fun CreatorAudienceStrip(
    context: ChatCreatorThreadContext,
    onOpenAudienceProfile: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 10.dp, end = 12.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.businessBg)
                .border(1.dp, PantopusColors.business.copy(alpha = 0.18f), RoundedCornerShape(Radii.lg))
                .clickable(onClick = onOpenAudienceProfile)
                .padding(horizontal = 10.dp, vertical = 8.dp)
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
            size = 16.dp,
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
                    .clip(RoundedCornerShape(4.dp))
                    .background(PantopusColors.appSurfaceSunken),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(PantopusColors.primary600),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

private fun presenceFor(counterparty: ChatCounterparty): Pair<Boolean, String>? =
    when (counterparty) {
        is ChatCounterparty.Person -> {
            val prefix = if (counterparty.online) "Active now" else "Verified neighbor"
            val text = if (counterparty.locality != null) "$prefix · ${counterparty.locality}" else prefix
            counterparty.online to text
        }
        is ChatCounterparty.Group -> counterparty.memberCount?.let { false to "$it members" }
        is ChatCounterparty.Ai -> false to "Replies in seconds · powered by Pantopus AI"
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
    counterparty: ChatCounterparty,
    tierRank: Int? = null,
) {
    when {
        isAi -> ChatAiAvatar(size = 32.dp)
        counterparty is ChatCounterparty.Person ->
            PersonAvatar(
                initials = counterparty.initials,
                verified = counterparty.verified,
                online = counterparty.online,
                size = 32.dp,
                ringColor = tierRank?.let(::creatorTierColor),
            )
        counterparty is ChatCounterparty.Group ->
            PersonAvatar(
                initials = counterparty.displayName.initials(),
                verified = false,
                online = false,
                size = 32.dp,
                ringColor = tierRank?.let(::creatorTierColor),
            )
        else -> ChatAiAvatar(size = 32.dp)
    }
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
                    .background(PantopusColors.primary500),
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

// MARK: - Frames

@Composable
private fun LoadingFrame() {
    Box(
        modifier = Modifier.fillMaxSize().testTag("chatConversationLoading"),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = PantopusColors.primary600)
    }
}

@Composable
internal fun EmptyFrame(
    counterparty: ChatCounterparty,
    aiPrompts: List<ChatPromptChip>,
    emptyChips: List<ChatPromptChip>,
    onChipTap: (ChatPromptChip) -> Unit,
    conversationMode: ChatConversationMode = ChatConversationMode.Dm,
    onCapabilityTap: (ChatPromptChip) -> Unit = {},
) {
    val isAi = conversationMode == ChatConversationMode.AiAssistant || counterparty is ChatCounterparty.Ai
    when {
        isAi -> AiWelcomeFrame(prompts = aiPrompts, onCapabilityTap = onCapabilityTap)
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
                .padding(horizontal = 24.dp)
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { chip ->
                QuickChip(chip = chip, onTap = onChipTap)
            }
        }
        Spacer(modifier = Modifier.size(14.dp))
        EncryptionPill()
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.ShieldCheck,
            contentDescription = null,
            size = 12.dp,
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
    incomingInitials: String? = null,
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
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        item {
            // Top-of-list trigger for cursor pagination backwards.
            LaunchedEffect(rows.firstOrNull()?.rowId) {
                onLoadOlder()
            }
            Spacer(modifier = Modifier.size(1.dp))
        }
        items(items = rows, key = { it.rowId }) { row ->
            when (row) {
                is ChatTimelineRow.DayDivider -> DayDividerRow(label = row.divider.label)
                is ChatTimelineRow.BroadcastReference -> BroadcastReferenceCard(reference = row.reference)
                is ChatTimelineRow.Bubble ->
                    BubbleRow(
                        content = row.content,
                        incomingInitials = incomingInitials,
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
                .padding(12.dp)
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
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

@Composable
private fun BubbleRow(
    content: ChatBubbleContent,
    incomingInitials: String? = null,
    onRetry: () -> Unit,
) {
    val isOut = content.side == ChatMessageSide.Outgoing
    val bubbleColor = if (isOut) PantopusColors.primary600 else PantopusColors.appSurfaceSunken
    val textColor = if (isOut) PantopusColors.appTextInverse else PantopusColors.appText
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = if (content.isContinuation) 2.dp else 8.dp, bottom = if (content.stamp == null) 3.dp else 0.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = if (isOut) Arrangement.End else Arrangement.Start,
    ) {
        if (isOut) {
            Spacer(modifier = Modifier.size(44.dp))
        } else if (incomingInitials != null && content.body !is ChatBubbleBody.SystemLink) {
            MiniAvatar(initials = incomingInitials, hidden = content.isContinuation)
            Spacer(modifier = Modifier.size(8.dp))
        }
        Column(horizontalAlignment = if (isOut) Alignment.End else Alignment.Start) {
            when (val body = content.body) {
                is ChatBubbleBody.Text ->
                    BubbleContainer(isOut = isOut, hasTail = content.hasTail, bubbleColor = bubbleColor) {
                        Text(
                            text = body.text,
                            fontSize = 14.sp,
                            color = textColor,
                            modifier = Modifier.widthIn(max = 260.dp),
                        )
                    }
                is ChatBubbleBody.Image -> PhotoBubble(url = body.url, isOut = isOut, hasTail = content.hasTail)
                is ChatBubbleBody.Attachment ->
                    BubbleContainer(isOut = isOut, hasTail = content.hasTail, bubbleColor = bubbleColor) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                is ChatBubbleBody.AiReply -> AiReplyBubble(body = body, hasTail = content.hasTail)
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
private fun BubbleContainer(
    isOut: Boolean,
    hasTail: Boolean,
    bubbleColor: Color,
    inner: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomEnd = if (isOut && hasTail) 4.dp else 16.dp,
                        bottomStart = if (!isOut && hasTail) 4.dp else 16.dp,
                    ),
                )
                .background(bubbleColor)
                .padding(horizontal = 13.dp, vertical = 9.dp),
    ) {
        inner()
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
                .background(if (hidden) Color.Transparent else PantopusColors.primary500),
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
) {
    val shape =
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomEnd = if (isOut && hasTail) 4.dp else 16.dp,
            bottomStart = if (!isOut && hasTail) 4.dp else 16.dp,
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
    }
}

@Composable
private fun PhotoPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(PantopusColors.appSurfaceSunken)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 8.dp),
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
                    .padding(start = 10.dp, bottom = 8.dp)
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
) {
    Column(
        modifier =
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomEnd = 16.dp,
                        bottomStart = if (hasTail) 4.dp else 16.dp,
                    ),
                )
                .background(PantopusColors.appSurfaceSunken)
                .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AiTag()
        Text(text = body.text, fontSize = 14.sp, color = PantopusColors.appText)
        body.estimate?.let { AiEstimateCard(estimate = it, modifier = Modifier.fillMaxWidth()) }
    }
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(Radii.pill))
                .background(bg)
                .border(1.dp, fg.copy(alpha = 0.2f), RoundedCornerShape(Radii.pill))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                size = 12.dp,
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
            ChatDeliveryState.Delivered -> "$stamp · Delivered"
            ChatDeliveryState.Sending -> "Sending…"
            ChatDeliveryState.Failed -> "Couldn't send"
            null -> stamp
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 12.dp),
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
                fontWeight = FontWeight.Medium,
                color = PantopusColors.appTextSecondary,
            )
        }
        if (content.side == ChatMessageSide.Outgoing && content.deliveryState != ChatDeliveryState.Read) {
            when (content.deliveryState) {
                ChatDeliveryState.Read -> Unit
                ChatDeliveryState.Delivered ->
                    PantopusIconImage(
                        icon = PantopusIcon.Check,
                        contentDescription = null,
                        size = 11.dp,
                        tint = PantopusColors.appTextSecondary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                ChatDeliveryState.Sending ->
                    CircularProgressIndicator(
                        modifier = Modifier.padding(start = 4.dp).size(10.dp),
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
                            fontWeight = FontWeight.Bold,
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Read $timestamp",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = PantopusColors.appTextSecondary,
        )
        PantopusIconImage(
            icon = PantopusIcon.CheckCheck,
            contentDescription = null,
            size = 12.dp,
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
                .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 6.dp)
                .testTag("chatTypingIndicator"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MiniAvatar(initials = initials, hidden = false)
        Row(
            modifier =
                Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomEnd = 16.dp,
                            bottomStart = 4.dp,
                        ),
                    )
                    .background(PantopusColors.appSurfaceSunken)
                    .border(
                        1.dp,
                        PantopusColors.appBorder,
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomEnd = 16.dp,
                            bottomStart = 4.dp,
                        ),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit = {},
    onEmoji: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PantopusColors.appSurfaceSunken),
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.Plus,
                        contentDescription = "Attach",
                        size = 18.dp,
                        strokeWidth = 2.4f,
                        tint = PantopusColors.appTextStrong,
                    )
                }
            }
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.appSurfaceSunken)
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.pill))
                        .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 14.sp,
                            color = PantopusColors.appTextMuted,
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle =
                            TextStyle(
                                fontSize = 14.sp,
                                color = PantopusColors.appText,
                            ),
                        cursorBrush = SolidColor(PantopusColors.primary600),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onEmoji)
                            .testTag("chatComposerEmoji"),
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.Smile,
                        contentDescription = "Emoji",
                        size = 17.dp,
                        tint = PantopusColors.appTextMuted,
                    )
                }
            }
            Box(
                modifier =
                    (
                        if (canSend) {
                            Modifier
                                .size(44.dp)
                                .shadow(
                                    elevation = 10.dp,
                                    shape = CircleShape,
                                    ambientColor = PantopusColors.primary600,
                                    spotColor = PantopusColors.primary600,
                                )
                        } else {
                            Modifier.size(44.dp)
                        }
                    )
                        .clip(CircleShape)
                        .clickable(enabled = canSend, onClick = onSend)
                        .testTag("chatComposerSend"),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (canSend) PantopusColors.primary600 else PantopusColors.appSurfaceSunken),
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.ArrowUp,
                        contentDescription = "Send",
                        size = 18.dp,
                        strokeWidth = 2.5f,
                        tint = if (canSend) PantopusColors.appTextInverse else PantopusColors.appTextMuted,
                    )
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
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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

@Composable
private fun ErrorFrame(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp)
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
