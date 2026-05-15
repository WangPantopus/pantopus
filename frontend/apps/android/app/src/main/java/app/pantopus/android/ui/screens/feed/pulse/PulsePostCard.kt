@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.feed.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.components.AvatarWithIdentityRing
import app.pantopus.android.ui.components.IdentityPillar
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/** VM-prepared content for a single Pulse card. */
@Immutable
data class PulsePostCardContent(
    val id: String,
    val authorName: String,
    val authorInitials: String,
    val authorVerified: Boolean,
    val meta: String,
    val intent: PulseIntent,
    val title: String?,
    val body: String,
    val reactions: List<PulseReaction>,
    val attendees: PulseAttendeeStrip?,
    val userHasReacted: Boolean,
)

/** Event card attendee strip — stacked avatars + going count + RSVP CTA. */
@Immutable
data class PulseAttendeeStrip(
    val avatars: List<String>,
    val goingCount: Int,
    val userIsGoing: Boolean,
)

/**
 * Pulse post card — entirely render-only; tap dispatch is parent-controlled.
 */
@Composable
fun PulsePostCard(
    content: PulsePostCardContent,
    onTap: () -> Unit,
    onPrimaryReaction: () -> Unit,
    onRSVP: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .clickable(onClick = onTap)
                .padding(Spacing.s3)
                .semantics(mergeDescendants = true) {
                    contentDescription = buildA11yLabel(content)
                }
                .testTag("pulsePostCard_${content.id}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        CardHeader(content = content)
        if (!content.title.isNullOrEmpty()) {
            Text(
                text = content.title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (content.body.isNotEmpty()) {
            Text(
                text = content.body,
                fontSize = 12.5.sp,
                color = PantopusColors.appTextStrong,
                maxLines = if (!content.title.isNullOrEmpty()) 2 else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        content.attendees?.let { attendees ->
            AttendeeStrip(attendees = attendees, onRSVP = onRSVP, postId = content.id)
        }
        ReactionStrip(
            content = content,
            onPrimary = onPrimaryReaction,
        )
    }
}

@Composable
private fun CardHeader(content: PulsePostCardContent) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        AvatarWithIdentityRing(
            name = content.authorInitials,
            identity = IdentityPillar.Personal,
            ringProgress = if (content.authorVerified) 1f else 0.35f,
            size = 32.dp,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = content.authorName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = content.meta,
                fontSize = 10.5.sp,
                color = PantopusColors.appTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PulseIntentChip(intent = content.intent)
    }
}

@Composable
private fun AttendeeStrip(
    attendees: PulseAttendeeStrip,
    onRSVP: (() -> Unit)?,
    postId: String,
) {
    Column {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PantopusColors.appBorderSubtle),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            // Stacked avatars
            Box {
                attendees.avatars.take(4).forEachIndexed { index, initials ->
                    Box(
                        modifier =
                            Modifier
                                .padding(start = (index * 14).dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .border(2.dp, PantopusColors.appSurface, CircleShape),
                    ) {
                        AvatarWithIdentityRing(
                            name = initials,
                            identity = IdentityPillar.Personal,
                            ringProgress = 1f,
                            size = 22.dp,
                        )
                    }
                }
            }
            Text(
                text = "+ ${attendees.goingCount} going",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = PantopusColors.appTextSecondary,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (onRSVP != null) {
                Row(
                    modifier =
                        Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(Radii.pill))
                            .background(PantopusColors.businessBg)
                            .clickable(onClick = onRSVP)
                            .padding(horizontal = 12.dp)
                            .testTag("pulseRSVP_$postId"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PantopusIconImage(
                        icon = if (attendees.userIsGoing) PantopusIcon.Check else PantopusIcon.PlusCircle,
                        contentDescription = null,
                        size = 10.dp,
                        tint = PantopusColors.business,
                    )
                    Text(
                        text = if (attendees.userIsGoing) "Going" else "RSVP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PantopusColors.business,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionStrip(
    content: PulsePostCardContent,
    onPrimary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        content.reactions.forEach { reaction ->
            ReactionPill(
                reaction = reaction,
                active = content.userHasReacted && reaction.kind == content.reactions.firstOrNull()?.kind,
                onClick = if (reaction.isInteractive) onPrimary else null,
                postId = content.id,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PantopusIconImage(
                icon = PantopusIcon.Send,
                contentDescription = null,
                size = 12.dp,
                tint = PantopusColors.appTextSecondary,
            )
            Text(
                text = "Reply",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = PantopusColors.appTextSecondary,
            )
        }
    }
}

@Composable
private fun ReactionPill(
    reaction: PulseReaction,
    active: Boolean,
    onClick: (() -> Unit)?,
    postId: String,
) {
    val tint = if (active) PantopusColors.primary600 else PantopusColors.appTextSecondary
    val base =
        Modifier
            .semantics {
                contentDescription = "${reaction.label.ifEmpty { "Count" }}, ${reaction.count}"
            }
    val withClick =
        if (onClick != null) {
            base.clickable(onClick = onClick).testTag("pulseReaction_${postId}_${reaction.kind.key}")
        } else {
            base
        }
    Row(
        modifier = withClick,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PantopusIconImage(
            icon = reaction.icon,
            contentDescription = null,
            size = 12.dp,
            tint = tint,
        )
        if (reaction.label.isNotEmpty()) {
            Text(
                text = reaction.label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = tint,
            )
        }
        Text(
            text = "${reaction.count}",
            fontSize = 11.5.sp,
            color = tint,
        )
    }
}

/**
 * Right-aligned colored chip in the post header. Resolves intent →
 * foreground/background tokens against the existing design system.
 */
@Composable
fun PulseIntentChip(intent: PulseIntent) {
    val (fg, bg) = intent.tintColors()
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(bg)
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .semantics { contentDescription = "${intent.label} post" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PantopusIconImage(
            icon = intent.icon,
            contentDescription = null,
            size = 10.dp,
            tint = fg,
        )
        Text(
            text = intent.cardChipLabel.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

private fun PulseIntent.tintColors(): Pair<Color, Color> =
    when (this) {
        PulseIntent.All -> PantopusColors.appTextSecondary to PantopusColors.appSurfaceSunken
        PulseIntent.Ask -> PantopusColors.warning to PantopusColors.warningBg
        PulseIntent.Recommend -> PantopusColors.success to PantopusColors.successBg
        PulseIntent.Event -> PantopusColors.business to PantopusColors.businessBg
        PulseIntent.Lost -> PantopusColors.error to PantopusColors.errorBg
        PulseIntent.Announce -> PantopusColors.appTextStrong to PantopusColors.appSurfaceSunken
    }

private fun buildA11yLabel(content: PulsePostCardContent): String {
    val parts = mutableListOf<String>()
    parts.add(content.authorName)
    if (content.intent.cardChipLabel.isNotEmpty()) parts.add(content.intent.cardChipLabel)
    content.title?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    if (content.body.isNotEmpty()) parts.add(content.body)
    return parts.joinToString(". ")
}
