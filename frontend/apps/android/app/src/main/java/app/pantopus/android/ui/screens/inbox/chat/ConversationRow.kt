@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.inbox.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.screens.inbox.conversation.ai.ChatAiAvatar
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii

@Composable
fun ConversationRow(
    content: ConversationRowContent,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    if (content.pinned) PantopusColors.primary50.copy(alpha = 0.5f) else PantopusColors.appSurface,
                )
                .clickable(onClick = onTap)
                .padding(horizontal = Spacing.s4, vertical = 14.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = buildA11yLabel(content)
                }
                .testTag("conversationRow_${content.id}"),
    ) {
        if (content.pinned) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-16).dp)
                        .width(3.dp)
                        .height(48.dp)
                        .background(PantopusColors.primary600),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Avatar(content)
            Middle(content, modifier = Modifier.weight(1f))
            Trailing(content)
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PantopusColors.appBorderSubtle),
        )
    }
}

@Composable
private fun Avatar(content: ConversationRowContent) {
    when (val variant = content.variant) {
        ConversationRowVariant.AiAssistant -> ChatAiAvatar(size = 44.dp)
        ConversationRowVariant.Dm -> DmAvatar(initials = content.initials, verified = content.verified)
        is ConversationRowVariant.Group -> GroupAvatar(content.initials, variant)
    }
}

@Composable
private fun DmAvatar(
    initials: String,
    verified: Boolean,
) {
    Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.BottomEnd) {
        InitialsCircle(size = 44.dp, color = PantopusColors.appBorderStrong, initials = initials)
        if (verified) {
            Box(
                modifier =
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.primary600)
                        .border(2.dp, PantopusColors.appSurface, CircleShape)
                        .offset(x = 1.dp, y = 1.dp)
                        .semantics { contentDescription = "Verified" },
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Check,
                    contentDescription = null,
                    size = 9.dp,
                    tint = PantopusColors.appTextInverse,
                )
            }
        }
    }
}

@Composable
private fun GroupAvatar(
    primary: String,
    group: ConversationRowVariant.Group,
) {
    Box(modifier = Modifier.size(46.dp)) {
        // Back tile
        Box(modifier = Modifier.offset(x = (-3).dp, y = (-3).dp)) {
            InitialsCircle(size = 32.dp, color = PantopusColors.warning, initials = primary)
        }
        // Front tile (extras)
        val front = group.extraAvatars.firstOrNull() ?: "+${group.extraCount.coerceAtLeast(1)}"
        Box(modifier = Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp)) {
            InitialsCircle(size = 32.dp, color = PantopusColors.success, initials = front)
        }
    }
}

@Composable
private fun InitialsCircle(
    size: Dp,
    color: Color,
    initials: String,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
                .border(2.dp, PantopusColors.appSurface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            fontSize = (size.value * 0.34f).sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appTextInverse,
        )
    }
}

@Composable
private fun Middle(
    content: ConversationRowContent,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = content.displayName,
                fontSize = 14.5.sp,
                fontWeight = if (content.unread > 0) FontWeight.Bold else FontWeight.SemiBold,
                color = PantopusColors.appText,
                maxLines = 1,
            )
            content.identityChip?.let { IdentityDisclosureChip(chip = it) }
        }
        Text(
            text = content.preview,
            fontSize = 12.5.sp,
            fontWeight = if (content.unread > 0) FontWeight.Medium else FontWeight.Normal,
            color = if (content.unread > 0) PantopusColors.appTextStrong else PantopusColors.appTextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun Trailing(content: ConversationRowContent) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = content.timeLabel,
            fontSize = 10.5.sp,
            fontWeight = if (content.unread > 0) FontWeight.Bold else FontWeight.Medium,
            color = if (content.unread > 0) PantopusColors.primary600 else PantopusColors.appTextSecondary,
        )
        if (content.unread > 0) {
            Box(
                modifier =
                    Modifier
                        .heightIn(min = 18.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.primary600)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                        .semantics { contentDescription = "${content.unread} unread" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${content.unread}",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appTextInverse,
                )
            }
        }
    }
}

@Composable
private fun IdentityDisclosureChip(chip: ConversationIdentityChip) {
    val fg: Color
    val bg: Color
    val icon: PantopusIcon
    when (chip) {
        ConversationIdentityChip.Business -> {
            fg = PantopusColors.business
            bg = PantopusColors.businessBg
            icon = PantopusIcon.ShoppingBag
        }
        ConversationIdentityChip.Home -> {
            fg = PantopusColors.home
            bg = PantopusColors.homeBg
            icon = PantopusIcon.Home
        }
    }
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.xs))
                .background(bg)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PantopusIconImage(icon = icon, contentDescription = null, size = Radii.md, tint = fg)
        Text(
            text = chip.label.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

private fun buildA11yLabel(content: ConversationRowContent): String {
    val parts = mutableListOf<String>()
    parts.add(content.displayName)
    content.identityChip?.label?.let { parts.add(it) }
    if (content.verified) parts.add("verified")
    parts.add(content.preview)
    if (content.unread > 0) parts.add("${content.unread} unread")
    return parts.joinToString(". ")
}
