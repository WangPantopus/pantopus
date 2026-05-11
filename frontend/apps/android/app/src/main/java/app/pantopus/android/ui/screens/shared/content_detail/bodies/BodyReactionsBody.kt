@file:Suppress("MagicNumber", "PackageNaming", "LongParameterList", "LongMethod", "TooManyFunctions")

package app.pantopus.android.ui.screens.shared.content_detail.bodies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.components.AvatarWithIdentityRing
import app.pantopus.android.ui.components.IdentityPillar
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import coil.compose.SubcomposeAsyncImage

/** One row in the flattened comment thread. */
data class PostCommentRow(
    val id: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val authorIdentity: IdentityPillar,
    val body: String,
    val timestamp: String,
    /** 0 for top-level, 1 for nested. Deeper threads collapse to 1. */
    val indentLevel: Int,
    val authorUserId: String?,
)

/** Reaction-bar counts + the user's currently-selected reaction (if any). */
data class PostReactionCounts(
    val helpful: Int = 0,
    val heart: Int = 0,
    val going: Int = 0,
    val userReaction: app.pantopus.android.data.api.models.posts.PostReactionKind? = null,
)

/**
 * Pulse post body — text + media + reactions + comment composer + flat
 * thread. Pure render surface; all state lives in the host VM.
 */
@Composable
fun BodyReactionsBody(
    body: String,
    mediaUrls: List<String>,
    reactions: PostReactionCounts,
    onReactionTap: (app.pantopus.android.data.api.models.posts.PostReactionKind) -> Unit,
    composerAvatarUrl: String?,
    composerAvatarName: String,
    composerText: String,
    onComposerTextChange: (String) -> Unit,
    isSending: Boolean,
    onSendTap: () -> Unit,
    comments: List<PostCommentRow>,
    hiddenReplyCount: Int = 0,
    onShowMoreReplies: (() -> Unit)? = null,
    onCommentAvatarTap: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        if (body.isNotEmpty()) {
            Text(
                text = body,
                fontSize = 15.sp,
                color = PantopusColors.appText,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = Spacing.s4),
            )
        }
        if (mediaUrls.isNotEmpty()) {
            PostMediaGrid(
                urls = mediaUrls,
                modifier = Modifier.padding(horizontal = Spacing.s4),
            )
        }
        ReactionsBar(
            counts = reactions,
            onTap = onReactionTap,
            modifier = Modifier.padding(horizontal = Spacing.s4),
        )
        CommentComposer(
            avatarName = composerAvatarName,
            avatarUrl = composerAvatarUrl,
            text = composerText,
            onTextChange = onComposerTextChange,
            isSending = isSending,
            onSend = onSendTap,
            modifier = Modifier.padding(horizontal = Spacing.s4),
        )
        if (comments.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.s4),
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                comments.forEach { row ->
                    CommentRow(
                        row = row,
                        onAvatarTap = {
                            row.authorUserId?.let(onCommentAvatarTap)
                        },
                    )
                }
                if (hiddenReplyCount > 0 && onShowMoreReplies != null) {
                    Text(
                        text =
                            "View $hiddenReplyCount more " +
                                if (hiddenReplyCount == 1) "reply" else "replies",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PantopusColors.primary600,
                        modifier =
                            Modifier
                                .padding(start = (28 + 12).dp)
                                .heightIn(min = 44.dp)
                                .clickable(onClick = onShowMoreReplies)
                                .semantics { contentDescription = "View $hiddenReplyCount more replies" },
                    )
                }
            }
        }
    }
}

@Composable
private fun PostMediaGrid(
    urls: List<String>,
    modifier: Modifier = Modifier,
) {
    when (urls.size) {
        0 -> Unit
        1 -> {
            MediaTile(
                url = urls[0],
                modifier =
                    modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(Radii.lg)),
            )
        }
        2 -> {
            Row(
                modifier = modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(Radii.lg)),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                MediaTile(url = urls[0], modifier = Modifier.weight(1f).fillMaxWidth())
                MediaTile(url = urls[1], modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
        3 -> {
            Row(
                modifier = modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(Radii.lg)),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                MediaTile(url = urls[0], modifier = Modifier.weight(1f).fillMaxWidth())
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    MediaTile(url = urls[1], modifier = Modifier.weight(1f).fillMaxWidth())
                    MediaTile(url = urls[2], modifier = Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
        else -> {
            // 4+ → 2x2 of the first 4.
            Column(
                modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.lg)),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    MediaTile(url = urls[0], modifier = Modifier.weight(1f).aspectRatio(1f))
                    MediaTile(url = urls[1], modifier = Modifier.weight(1f).aspectRatio(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    MediaTile(url = urls[2], modifier = Modifier.weight(1f).aspectRatio(1f))
                    MediaTile(url = urls[3], modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
private fun MediaTile(
    url: String,
    modifier: Modifier = Modifier,
) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier.background(PantopusColors.appSurfaceSunken),
        loading = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        },
        error = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PantopusIconImage(
                    icon = PantopusIcon.AlertCircle,
                    contentDescription = null,
                    size = 22.dp,
                    tint = PantopusColors.appTextMuted,
                )
            }
        },
    )
}

@Composable
private fun ReactionsBar(
    counts: PostReactionCounts,
    onTap: (app.pantopus.android.data.api.models.posts.PostReactionKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        ReactionPill(
            label = "Helpful",
            icon = PantopusIcon.ThumbsUp,
            count = counts.helpful,
            isSelected = counts.userReaction == app.pantopus.android.data.api.models.posts.PostReactionKind.Helpful,
            onTap = { onTap(app.pantopus.android.data.api.models.posts.PostReactionKind.Helpful) },
            accessibilityLabel = "Helpful, ${counts.helpful}",
        )
        ReactionPill(
            label = "Heart",
            icon = PantopusIcon.Heart,
            count = counts.heart,
            isSelected = counts.userReaction == app.pantopus.android.data.api.models.posts.PostReactionKind.Heart,
            onTap = { onTap(app.pantopus.android.data.api.models.posts.PostReactionKind.Heart) },
            accessibilityLabel = "Loved, ${counts.heart}",
        )
        ReactionPill(
            label = "Going",
            icon = PantopusIcon.Check,
            count = counts.going,
            isSelected = counts.userReaction == app.pantopus.android.data.api.models.posts.PostReactionKind.Going,
            onTap = { onTap(app.pantopus.android.data.api.models.posts.PostReactionKind.Going) },
            accessibilityLabel = "Going, ${counts.going}",
        )
    }
}

@Composable
private fun ReactionPill(
    label: String,
    icon: PantopusIcon,
    count: Int,
    isSelected: Boolean,
    onTap: () -> Unit,
    accessibilityLabel: String,
) {
    val background = if (isSelected) PantopusColors.primary600 else PantopusColors.appSurfaceSunken
    val foreground = if (isSelected) PantopusColors.appTextInverse else PantopusColors.appText
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(background)
                .sizeIn(minHeight = 44.dp)
                .clickable(onClick = onTap)
                .padding(horizontal = Spacing.s3, vertical = 6.dp)
                .semantics { contentDescription = accessibilityLabel },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            PantopusIconImage(icon = icon, contentDescription = null, size = 14.dp, tint = foreground)
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = foreground,
            )
            Text(
                text = "$count",
                fontSize = 12.sp,
                color = foreground.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun CommentComposer(
    avatarName: String,
    avatarUrl: String?,
    text: String,
    onTextChange: (String) -> Unit,
    isSending: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        AvatarWithIdentityRing(
            name = avatarName,
            imageUrl = avatarUrl,
            identity = IdentityPillar.Personal,
            ringProgress = 1f,
            size = 28.dp,
        )
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("Add a comment", fontSize = 14.sp) },
            singleLine = true,
            shape = RoundedCornerShape(Radii.pill),
            textStyle = TextStyle(fontSize = 14.sp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PantopusColors.appBorder,
                    unfocusedBorderColor = PantopusColors.appBorder,
                    focusedContainerColor = PantopusColors.appSurfaceSunken,
                    unfocusedContainerColor = PantopusColors.appSurfaceSunken,
                ),
            modifier =
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .semantics { contentDescription = "Add a comment" },
        )
        val isEnabled = text.trim().isNotEmpty() && !isSending
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isEnabled) PantopusColors.primary100 else Color.Transparent)
                    .clickable(enabled = isEnabled, onClick = onSend)
                    .semantics { contentDescription = "Send comment" },
            contentAlignment = Alignment.Center,
        ) {
            if (isSending) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                PantopusIconImage(
                    icon = PantopusIcon.Send,
                    contentDescription = null,
                    size = 18.dp,
                    tint = if (isEnabled) PantopusColors.primary600 else PantopusColors.appTextMuted,
                )
            }
        }
    }
}

@Composable
private fun CommentRow(
    row: PostCommentRow,
    onAvatarTap: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        if (row.indentLevel > 0) {
            Spacer(Modifier.width((row.indentLevel * 28).dp))
        }
        Box(
            modifier =
                Modifier
                    .sizeIn(minWidth = 28.dp, minHeight = 28.dp)
                    .clickable(onClick = onAvatarTap)
                    .semantics { contentDescription = "Open ${row.authorName}'s profile" },
        ) {
            AvatarWithIdentityRing(
                name = row.authorName,
                imageUrl = row.authorAvatarUrl,
                identity = row.authorIdentity,
                ringProgress = 1f,
                size = 28.dp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                Text(
                    text = row.authorName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appText,
                )
                Text("·", fontSize = 11.sp, color = PantopusColors.appTextMuted)
                Text(
                    text = row.timestamp,
                    fontSize = 11.sp,
                    color = PantopusColors.appTextSecondary,
                )
            }
            Text(
                text = row.body,
                fontSize = 12.sp,
                color = PantopusColors.appTextStrong,
                lineHeight = 16.sp,
            )
        }
    }
}
