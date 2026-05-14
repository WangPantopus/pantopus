@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "CyclomaticComplexMethod")

package app.pantopus.android.ui.screens.inbox.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/**
 * Chat conversation screen (T2.2). Three frames: shimmer loading,
 * counterparty-specific empty state, populated thread. Composer has
 * attach + send discs; send is color-bound to text presence and
 * disabled while in flight.
 */
@Composable
fun ChatConversationScreen(
    mode: ChatThreadMode,
    counterparty: ChatCounterparty,
    currentUserId: String,
    onBack: () -> Unit = {},
    viewModel: ChatConversationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeCounterparty by viewModel.counterparty.collectAsStateWithLifecycle()
    val composerText by viewModel.composerText.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val isCounterpartyTyping by viewModel.isCounterpartyTyping.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.configure(mode, counterparty, currentUserId)
        viewModel.load()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.teardown() }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appSurface)
                .testTag("chatConversation"),
    ) {
        ChatHeader(counterparty = activeCounterparty, onBack = onBack)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                ChatConversationUiState.Loading -> LoadingFrame()
                ChatConversationUiState.Empty -> EmptyFrame(
                    counterparty = activeCounterparty,
                    aiPrompts = viewModel.aiPrompts,
                    emptyChips = viewModel.emptyChips,
                    onChipTap = viewModel::tapPrompt,
                )
                is ChatConversationUiState.Loaded -> PopulatedFrame(
                    rows = s.rows,
                    onRetry = viewModel::retry,
                    onLoadOlder = viewModel::loadOlder,
                )
                is ChatConversationUiState.Error -> ErrorFrame(message = s.message, onRetry = viewModel::refresh)
            }
        }
        if (isCounterpartyTyping) {
            TypingIndicator(name = activeCounterparty.displayName)
        }
        Composer(
            text = composerText,
            placeholder = composerPlaceholder(activeCounterparty),
            canSend = composerText.isNotBlank() && !isSending,
            onTextChange = viewModel::setComposerText,
            onSend = viewModel::send,
        )
    }
}

private fun composerPlaceholder(c: ChatCounterparty): String =
    when (c) {
        is ChatCounterparty.Ai -> "Ask anything…"
        is ChatCounterparty.Group -> "Message ${c.displayName.firstWord()}…"
        is ChatCounterparty.Person -> "Message ${c.displayName.firstWord()}…"
    }

private fun String.firstWord(): String = split(" ").firstOrNull() ?: this

// MARK: - Header

@Composable
private fun ChatHeader(
    counterparty: ChatCounterparty,
    onBack: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
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
        HeaderAvatar(counterparty)
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
                if (counterparty is ChatCounterparty.Ai) BetaPill()
            }
            presenceFor(counterparty)?.let { (online, text) ->
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
        when (counterparty) {
            is ChatCounterparty.Person -> {
                Row {
                    HeaderIcon(PantopusIcon.Send)
                    HeaderIcon(PantopusIcon.Camera)
                    HeaderIcon(PantopusIcon.MoreHorizontal)
                }
            }
            else -> HeaderIcon(PantopusIcon.MoreHorizontal)
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
private fun HeaderAvatar(counterparty: ChatCounterparty) {
    when (counterparty) {
        is ChatCounterparty.Person ->
            PersonAvatar(
                initials = counterparty.initials,
                verified = counterparty.verified,
                online = counterparty.online,
                size = 32.dp,
            )
        is ChatCounterparty.Group ->
            PersonAvatar(initials = counterparty.displayName.initials(), verified = false, online = false, size = 32.dp)
        is ChatCounterparty.Ai -> AiAvatar(size = 32.dp)
    }
}

private fun String.initials(): String =
    split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()

@Composable
private fun PersonAvatar(
    initials: String,
    verified: Boolean,
    online: Boolean,
    size: androidx.compose.ui.unit.Dp,
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
private fun AiAvatar(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(PantopusColors.primary500, PantopusColors.primary700),
                    ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Info,
            contentDescription = "AI",
            size = size * 0.5f,
            tint = PantopusColors.appTextInverse,
        )
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
private fun EmptyFrame(
    counterparty: ChatCounterparty,
    aiPrompts: List<ChatPromptChip>,
    emptyChips: List<ChatPromptChip>,
    onChipTap: (ChatPromptChip) -> Unit,
) {
    when (counterparty) {
        is ChatCounterparty.Ai -> AiWelcomeFrame(prompts = aiPrompts, onChipTap = onChipTap)
        is ChatCounterparty.Person ->
            PersonEmptyFrame(counterparty = counterparty, chips = emptyChips, onChipTap = onChipTap)
        is ChatCounterparty.Group ->
            PersonEmptyFrame(
                counterparty =
                    ChatCounterparty.Person(
                        displayName = counterparty.displayName,
                        initials = counterparty.displayName.initials(),
                    ),
                chips = emptyChips,
                onChipTap = onChipTap,
            )
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
    onChipTap: (ChatPromptChip) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = 18.dp, start = 14.dp, end = 14.dp)
                .testTag("chatConversationAI"),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AiAvatar(size = 32.dp)
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = Radii.lg, bottomEnd = Radii.lg, bottomStart = Radii.lg))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(PantopusColors.primary50, PantopusColors.appSurface),
                            ),
                        )
                        .border(
                            1.dp,
                            PantopusColors.primary100,
                            RoundedCornerShape(topStart = 4.dp, topEnd = Radii.lg, bottomEnd = Radii.lg, bottomStart = Radii.lg),
                        )
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PantopusIconImage(
                        icon = PantopusIcon.Info,
                        contentDescription = null,
                        size = 10.dp,
                        tint = PantopusColors.primary700,
                    )
                    Text(
                        text = "PANTOPUS AI",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PantopusColors.primary700,
                    )
                }
                Text(
                    text = "Hi! I can help you post tasks, find listings, or summarize mail. What can I help with today?",
                    fontSize = 14.sp,
                    color = PantopusColors.appText,
                )
            }
        }
        Spacer(modifier = Modifier.size(14.dp))
        Column(
            modifier = Modifier.padding(start = 42.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = "SUGGESTED",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                prompts.forEach { prompt ->
                    QuickChip(chip = prompt, onTap = onChipTap)
                }
            }
        }
    }
}

@Composable
private fun PopulatedFrame(
    rows: List<ChatTimelineRow>,
    onRetry: (String) -> Unit,
    onLoadOlder: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(rows.size) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 100) {
            // No-op: keep position when new rows append at bottom.
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
                is ChatTimelineRow.Bubble ->
                    BubbleRow(
                        content = row.content,
                        onRetry = {
                            if (row.content.id.startsWith("client_")) onRetry(row.content.id)
                        },
                    )
            }
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
    onRetry: () -> Unit,
) {
    val isOut = content.side == ChatMessageSide.Outgoing
    val bubbleColor = if (isOut) PantopusColors.primary600 else PantopusColors.appSurfaceSunken
    val textColor = if (isOut) PantopusColors.appTextInverse else PantopusColors.appText
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = if (content.stamp == null) 3.dp else 0.dp),
        horizontalAlignment = if (isOut) Alignment.End else Alignment.Start,
    ) {
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
            is ChatBubbleBody.Image ->
                BubbleContainer(isOut = isOut, hasTail = content.hasTail, bubbleColor = PantopusColors.appSurfaceSunken) {
                    Box(
                        modifier =
                            Modifier
                                .size(220.dp, 140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PantopusColors.appBorderSubtle),
                    )
                }
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
        }
        if (content.stamp != null) {
            StampRow(content = content, onRetry = onRetry)
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
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
            ChatDeliveryState.Read -> "$stamp · Read"
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
        Text(
            text = raw,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = PantopusColors.appTextSecondary,
        )
        if (content.side == ChatMessageSide.Outgoing) {
            when (content.deliveryState) {
                ChatDeliveryState.Read ->
                    Row(modifier = Modifier.padding(start = 4.dp)) {
                        PantopusIconImage(
                            icon = PantopusIcon.Check,
                            contentDescription = null,
                            size = 9.dp,
                            tint = PantopusColors.primary600,
                        )
                        PantopusIconImage(
                            icon = PantopusIcon.Check,
                            contentDescription = null,
                            size = 9.dp,
                            tint = PantopusColors.primary600,
                        )
                    }
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
private fun TypingIndicator(name: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, top = 4.dp)
                .testTag("chatTypingIndicator"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.appSurfaceSunken)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier.size(5.dp).clip(CircleShape).background(PantopusColors.appTextSecondary),
                )
            }
        }
        Text(
            text = "$name is typing…",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun Composer(
    text: String,
    placeholder: String,
    canSend: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.appSurfaceSunken)
                        .clickable {}
                        .testTag("chatComposerAttach"),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.PlusCircle,
                    contentDescription = "Attach",
                    size = 18.dp,
                    tint = PantopusColors.appTextStrong,
                )
            }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.appSurfaceSunken)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
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
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (canSend) PantopusColors.primary600 else PantopusColors.appSurfaceSunken)
                        .clickable(enabled = canSend, onClick = onSend)
                        .testTag("chatComposerSend"),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Send,
                    contentDescription = "Send",
                    size = 17.dp,
                    tint = if (canSend) PantopusColors.appTextInverse else PantopusColors.appTextMuted,
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
