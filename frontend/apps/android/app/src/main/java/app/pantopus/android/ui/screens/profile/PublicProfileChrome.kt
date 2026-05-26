@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "LongParameterList")

package app.pantopus.android.ui.screens.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/**
 * P6.5 — Kind-aware chrome components for the Public Profile screen.
 *
 * - [PublicProfileBanner]: flat 100dp banner tinted per kind.
 * - [PublicProfileBroadcastCard]: persona broadcast card with the
 *   visibility chip and optional "Subscribe to unlock" paywall overlay.
 * - [PublicProfileLocalPostCard]: Pulse-style neighborhood post card
 *   with the optional intent chip.
 * - [PublicProfilePostsFeed]: kind-routing wrapper that picks between
 *   the two card styles.
 *
 * Colors and spacing come from the token set — never raw hex literals.
 */

// MARK: - Banner

/**
 * Flat, kind-tinted banner above the profile header. The design pack
 * sketches a gradient, but the mobile spec pins us to flat surfaces
 * (the only gradient in the app is the marketing landing hero).
 * Persona uses `primary50` + `primary600` trim; Local uses `homeBg` +
 * `home` trim.
 */
@Composable
fun PublicProfileBanner(kind: PublicProfileKind) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Spacing.s16)
                    .background(if (kind == PublicProfileKind.Persona) PantopusColors.primary50 else PantopusColors.homeBg)
                    .testTag(
                        if (kind == PublicProfileKind.Persona) {
                            "publicProfilePersonaBanner"
                        } else {
                            "publicProfileLocalBanner"
                        },
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(if (kind == PublicProfileKind.Persona) PantopusColors.primary600 else PantopusColors.home),
        )
    }
}

// MARK: - Persona broadcast card

/**
 * Persona broadcast card: meta row (timeAgo + visibility chip), body,
 * reactions/replies footer. When [PublicProfilePost.isLocked] is true,
 * swap the body + reactions row for a tinted paywall overlay inviting
 * the visitor to subscribe.
 */
@Composable
fun PublicProfileBroadcastCard(
    post: PublicProfilePost,
    onUnlock: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("publicProfileBroadcastCard_${post.id}")
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s3)
                .semantics { contentDescription = accessibilitySummary(post) },
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        BroadcastMetaRow(post = post)
        if (post.isLocked) {
            LockedBroadcastOverlay(post = post, onUnlock = onUnlock)
        } else {
            Text(
                text = post.body,
                fontSize = 14.sp,
                color = PantopusColors.appText,
                maxLines = 3,
            )
            ReactionsRow(post = post, leadingIcon = PantopusIcon.Heart)
        }
    }
}

@Composable
private fun BroadcastMetaRow(post: PublicProfilePost) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Text(post.timeAgo, fontSize = 12.sp, color = PantopusColors.appTextSecondary)
        Text("·", fontSize = 12.sp, color = PantopusColors.appTextMuted)
        post.visibility?.let { VisibilityChip(visibility = it) }
        Box(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LockedBroadcastOverlay(
    post: PublicProfilePost,
    onUnlock: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurfaceSunken)
                .padding(vertical = Spacing.s3, horizontal = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.warningBg),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Lock,
                contentDescription = null,
                size = 18.dp,
                tint = PantopusColors.warning,
            )
        }
        Text(
            text = "Subscribe to ${unlockTierLabel(post.visibility)} to unlock",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
        )
        Row(
            modifier =
                Modifier
                    .testTag("publicProfileBroadcastUnlock_${post.id}")
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.warning)
                    .heightIn(min = 32.dp)
                    .clickable(onClick = onUnlock)
                    .padding(horizontal = Spacing.s4)
                    .semantics {
                        contentDescription = "Subscribe to unlock ${unlockTierLabel(post.visibility)} broadcast"
                    },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Subscribe to unlock",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
    }
}

private fun unlockTierLabel(visibility: PublicProfilePost.Visibility?): String =
    when (visibility) {
        PublicProfilePost.Visibility.Free -> "Free"
        PublicProfilePost.Visibility.Bronze, null -> "Bronze"
        PublicProfilePost.Visibility.Silver -> "Silver"
        PublicProfilePost.Visibility.Gold -> "Gold"
    }

@Composable
private fun VisibilityChip(visibility: PublicProfilePost.Visibility) {
    val spec =
        when (visibility) {
            PublicProfilePost.Visibility.Free ->
                VisibilityChipSpec("FREE", PantopusIcon.Globe, PantopusColors.success, PantopusColors.successBg)
            PublicProfilePost.Visibility.Bronze ->
                VisibilityChipSpec("BRONZE+", PantopusIcon.Lock, PantopusColors.warning, PantopusColors.warningBg)
            PublicProfilePost.Visibility.Silver ->
                VisibilityChipSpec("SILVER+", PantopusIcon.Lock, PantopusColors.warning, PantopusColors.warningBg)
            PublicProfilePost.Visibility.Gold ->
                VisibilityChipSpec("GOLD+", PantopusIcon.Lock, PantopusColors.warning, PantopusColors.warningBg)
        }
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(spec.bg)
                .padding(horizontal = Spacing.s2, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PantopusIconImage(icon = spec.icon, contentDescription = null, size = 10.dp, tint = spec.fg)
        Text(text = spec.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = spec.fg)
    }
}

private data class VisibilityChipSpec(
    val label: String,
    val icon: PantopusIcon,
    val fg: Color,
    val bg: Color,
)

// MARK: - Local Pulse-style post card

@Composable
fun PublicProfileLocalPostCard(post: PublicProfilePost) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("publicProfileLocalPostCard_${post.id}")
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s3)
                .semantics { contentDescription = accessibilitySummary(post) },
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        LocalMetaRow(post = post)
        Text(
            text = post.body,
            fontSize = 14.sp,
            color = PantopusColors.appText,
            maxLines = 3,
        )
        ReactionsRow(post = post, leadingIcon = PantopusIcon.Lightbulb)
    }
}

@Composable
private fun LocalMetaRow(post: PublicProfilePost) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Text(post.timeAgo, fontSize = 12.sp, color = PantopusColors.appTextSecondary)
        if (!post.locality.isNullOrEmpty()) {
            Text("·", fontSize = 12.sp, color = PantopusColors.appTextMuted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                PantopusIconImage(
                    icon = PantopusIcon.MapPin,
                    contentDescription = null,
                    size = 11.dp,
                    tint = PantopusColors.appTextSecondary,
                )
                Text(post.locality, fontSize = 12.sp, color = PantopusColors.appTextSecondary)
            }
        }
        Box(modifier = Modifier.weight(1f))
        post.intent?.let { IntentChip(intent = it) }
    }
}

@Composable
private fun IntentChip(intent: PublicProfilePost.Intent) {
    val spec =
        when (intent) {
            PublicProfilePost.Intent.Offer ->
                IntentChipSpec("OFFER", PantopusIcon.Hand, PantopusColors.home, PantopusColors.homeBg)
            PublicProfilePost.Intent.Alert ->
                IntentChipSpec("ALERT", PantopusIcon.AlertTriangle, PantopusColors.warning, PantopusColors.warningBg)
            PublicProfilePost.Intent.Event ->
                IntentChipSpec("EVENT", PantopusIcon.Calendar, PantopusColors.personal, PantopusColors.personalBg)
            PublicProfilePost.Intent.Ask ->
                IntentChipSpec("ASK", PantopusIcon.HelpCircle, PantopusColors.primary700, PantopusColors.primary50)
        }
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(spec.bg)
                .padding(horizontal = Spacing.s2, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PantopusIconImage(icon = spec.icon, contentDescription = null, size = 10.dp, tint = spec.fg)
        Text(text = spec.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = spec.fg)
    }
}

private data class IntentChipSpec(
    val label: String,
    val icon: PantopusIcon,
    val fg: Color,
    val bg: Color,
)

@Composable
private fun ReactionsRow(
    post: PublicProfilePost,
    leadingIcon: PantopusIcon,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            PantopusIconImage(
                icon = leadingIcon,
                contentDescription = null,
                size = 13.dp,
                tint = PantopusColors.appTextSecondary,
            )
            Text("${post.reactions}", fontSize = 12.sp, color = PantopusColors.appTextSecondary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            PantopusIconImage(
                icon = PantopusIcon.MessageCircle,
                contentDescription = null,
                size = 13.dp,
                tint = PantopusColors.appTextSecondary,
            )
            Text("${post.replies}", fontSize = 12.sp, color = PantopusColors.appTextSecondary)
        }
        Box(modifier = Modifier.weight(1f))
        PantopusIconImage(
            icon = if (leadingIcon == PantopusIcon.Heart) PantopusIcon.Bookmark else PantopusIcon.Share,
            contentDescription = null,
            size = 13.dp,
            tint = PantopusColors.appTextSecondary,
        )
    }
}

// MARK: - Posts feed wrapper

@Composable
fun PublicProfilePostsFeed(
    kind: PublicProfileKind,
    posts: List<PublicProfilePost>,
    onUnlock: (PublicProfilePost) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Text(
            text = if (kind == PublicProfileKind.Persona) "RECENT BROADCASTS" else "RECENT POSTS",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.semantics { heading() },
        )
        if (posts.isEmpty()) {
            Text(
                text =
                    if (kind == PublicProfileKind.Persona) {
                        "No broadcasts yet — check back soon."
                    } else {
                        "No posts from this neighbor yet."
                    },
                fontSize = 14.sp,
                color = PantopusColors.appTextSecondary,
                modifier =
                    Modifier
                        .testTag("publicProfilePostsEmpty")
                        .padding(vertical = Spacing.s3),
            )
        } else {
            posts.forEach { post ->
                when (kind) {
                    PublicProfileKind.Persona ->
                        PublicProfileBroadcastCard(post = post, onUnlock = { onUnlock(post) })
                    PublicProfileKind.Local ->
                        PublicProfileLocalPostCard(post = post)
                }
            }
        }
    }
}

private fun accessibilitySummary(post: PublicProfilePost): String {
    if (post.isLocked) {
        val v = post.visibility?.name ?: "Locked"
        return "Locked broadcast ($v). ${post.timeAgo}. Subscribe to unlock."
    }
    val descriptor =
        when {
            post.intent != null -> "${post.intent.name} post"
            post.visibility != null -> "Broadcast (${post.visibility.name})"
            else -> "Post"
        }
    val localityPart = post.locality?.let { " in $it." } ?: ""
    return "$descriptor.$localityPart ${post.body}. ${post.timeAgo}. " +
        "${post.reactions} reactions, ${post.replies} replies."
}
