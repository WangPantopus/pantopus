@file:Suppress("MagicNumber", "UnusedPrivateMember", "LongMethod", "LongParameterList")

package app.pantopus.android.ui.screens.hub.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.components.ActionChip
import app.pantopus.android.ui.components.AvatarWithIdentityRing
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.components.SectionHeader
import app.pantopus.android.ui.components.SegmentedProgressBar
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.components.StatusChip
import app.pantopus.android.ui.components.StatusChipVariant
import app.pantopus.android.ui.screens.hub.ActionChipContent
import app.pantopus.android.ui.screens.hub.ActivityEntry
import app.pantopus.android.ui.screens.hub.DiscoveryCardContent
import app.pantopus.android.ui.screens.hub.FirstRunContent
import app.pantopus.android.ui.screens.hub.JumpBackItem
import app.pantopus.android.ui.screens.hub.PillarTile
import app.pantopus.android.ui.screens.hub.SetupBannerContent
import app.pantopus.android.ui.screens.hub.SetupStep
import app.pantopus.android.ui.screens.hub.TodaySummary
import app.pantopus.android.ui.screens.hub.TopBarContent
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusElevations
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import app.pantopus.android.ui.theme.pantopusShadow

@Composable
fun HubTopBar(
    content: TopBarContent,
    onBellTap: () -> Unit,
    onMenuTap: () -> Unit,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(PantopusColors.appSurface)
                    .padding(horizontal = Spacing.s4)
                    .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            AvatarWithIdentityRing(
                name = content.name,
                identity = app.pantopus.android.ui.components.IdentityPillar.Personal,
                ringProgress = content.ringProgress,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    content.greeting,
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                )
                Text(
                    content.name,
                    style = PantopusTextStyle.h3,
                    color = PantopusColors.appText,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clickable(onClick = onBellTap)
                        .semantics { contentDescription = "Notifications" },
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Bell,
                    contentDescription = null,
                    size = 22.dp,
                    tint = PantopusColors.appText,
                )
                if (content.unreadCount > 0) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(PantopusColors.error)
                                .border(2.dp, PantopusColors.appSurface, CircleShape),
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clickable(onClick = onMenuTap)
                        .semantics { contentDescription = "Menu" },
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Menu,
                    contentDescription = null,
                    size = 22.dp,
                    tint = PantopusColors.appText,
                )
            }
        }
        HorizontalDivider(color = PantopusColors.appBorderSubtle, thickness = 1.dp)
    }
}

@Composable
fun HubActionStrip(
    chips: List<ActionChipContent>,
    onTap: (ActionChipContent.Kind) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        chips.forEach { chip ->
            ActionChip(
                icon = chip.icon,
                label = chip.label,
                onClick = { onTap(chip.kind) },
                isActive = chip.active,
            )
        }
    }
}

@Composable
fun HubSetupBanner(
    content: SetupBannerContent,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.warningBg)
                .border(1.dp, PantopusColors.warningLight, RoundedCornerShape(Radii.lg))
                .padding(Spacing.s3)
                .semantics { contentDescription = content.title },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.ShieldCheck,
            contentDescription = null,
            size = 22.dp,
            tint = PantopusColors.warning,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Text(content.title, style = PantopusTextStyle.body, color = PantopusColors.appText)
            Text(
                "Unlock trusted neighborhood features by verifying your address.",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
            Text(
                text = content.ctaTitle,
                style = PantopusTextStyle.small,
                color = PantopusColors.warning,
                modifier =
                    Modifier
                        .heightIn(min = 44.dp)
                        .clickable(onClick = onStart)
                        .semantics { contentDescription = "${content.ctaTitle} ${content.title}" },
            )
        }
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clickable(onClick = onDismiss)
                    .semantics { contentDescription = "Dismiss banner" },
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.X,
                contentDescription = null,
                size = 18.dp,
                tint = PantopusColors.warning,
            )
        }
    }
}

@Composable
fun HubFirstRunHero(
    content: FirstRunContent,
    onStart: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4)
                .pantopusShadow(PantopusElevations.md, RoundedCornerShape(Radii.xl))
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .padding(Spacing.s5),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Text(
            "${content.greeting}, ${content.name}",
            style = PantopusTextStyle.h2,
            color = PantopusColors.appText,
        )
        Text("Verify your home", style = PantopusTextStyle.h1, color = PantopusColors.appText)
        Text(
            "Claim your address to unlock your neighborhood pulse, mailbox, and more.",
            style = PantopusTextStyle.body,
            color = PantopusColors.appTextSecondary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            content.steps.forEach { step -> SetupStepRow(step) }
        }
        PrimaryButton(title = "Verify my home", onClick = onStart)
    }
}

@Composable
private fun SetupStepRow(step: SetupStep) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PantopusIconImage(
            icon = if (step.done) PantopusIcon.CheckCircle else PantopusIcon.Circle,
            contentDescription = null,
            size = 18.dp,
            tint = if (step.done) PantopusColors.success else PantopusColors.appTextMuted,
        )
        Text(
            step.title,
            style =
                PantopusTextStyle.body.copy(
                    textDecoration = if (step.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
            color = if (step.done) PantopusColors.appTextSecondary else PantopusColors.appText,
        )
    }
}

@Composable
fun HubTodayCard(summary: TodaySummary) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4)
                .pantopusShadow(PantopusElevations.sm, RoundedCornerShape(Radii.lg))
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .padding(Spacing.s3)
                .semantics { contentDescription = "Today" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(PantopusColors.warning, PantopusColors.error),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Sun,
                contentDescription = null,
                size = 28.dp,
                tint = PantopusColors.appTextInverse,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Text("TODAY", style = PantopusTextStyle.overline, color = PantopusColors.appTextSecondary)
            val temp = summary.temperatureFahrenheit
            if (temp != null) {
                Text("$temp°", style = PantopusTextStyle.h2, color = PantopusColors.appText)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                summary.conditions?.let {
                    Text(it, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
                }
                summary.aqiLabel?.let { StatusChip("AQI $it", StatusChipVariant.Info) }
                summary.commuteLabel?.let { StatusChip(it, StatusChipVariant.Neutral) }
            }
        }
    }
}

@Composable
fun HubPillarGrid(
    tiles: List<PillarTile>,
    onTap: (PillarTile.Pillar) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                pair.forEach { tile ->
                    PillarTileView(tile, onTap, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PillarTileView(
    tile: PillarTile,
    onTap: (PillarTile.Pillar) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .pantopusShadow(PantopusElevations.sm, RoundedCornerShape(Radii.lg))
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .clickable { onTap(tile.pillar) }
                .padding(Spacing.s3)
                .semantics { contentDescription = "${tile.label}${tile.chip?.let { ", $it" }.orEmpty()}" },
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .background(tile.tint.backgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = tile.icon,
                    contentDescription = null,
                    size = 20.dp,
                    tint = tile.tint.color,
                )
            }
            tile.chip?.let {
                StatusChip(
                    text = it,
                    variant = if (tile.chipSetupState) StatusChipVariant.Warning else StatusChipVariant.Info,
                )
            }
        }
        Text(tile.label, style = PantopusTextStyle.body, color = PantopusColors.appText)
    }
}

@Composable
fun HubDiscoveryRail(
    items: List<DiscoveryCardContent>,
    onTap: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Box(modifier = Modifier.padding(horizontal = Spacing.s4)) {
            SectionHeader("Discover nearby")
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.s4),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            items.forEach { item ->
                Column(
                    modifier =
                        Modifier
                            .width(140.dp)
                            .height(180.dp)
                            .pantopusShadow(PantopusElevations.sm, RoundedCornerShape(Radii.lg))
                            .clip(RoundedCornerShape(Radii.lg))
                            .background(PantopusColors.appSurface)
                            .clickable { onTap(item.id) }
                            .padding(Spacing.s3)
                            .semantics { contentDescription = "${item.title}, ${item.meta}" },
                    verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    AvatarWithIdentityRing(
                        name = item.avatarInitials,
                        identity = app.pantopus.android.ui.components.IdentityPillar.Personal,
                        ringProgress = 1f,
                        size = 48.dp,
                    )
                    Text(
                        item.title,
                        style = PantopusTextStyle.body,
                        color = PantopusColors.appText,
                        maxLines = 2,
                    )
                    Text(item.meta, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
                    Spacer(Modifier.weight(1f))
                    StatusChip(item.category, StatusChipVariant.Neutral)
                }
            }
        }
    }
}

@Composable
fun HubJumpBackIn(
    items: List<JumpBackItem>,
    onTap: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Box(modifier = Modifier.padding(horizontal = Spacing.s4)) { SectionHeader("Jump back in") }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            items.forEach { item ->
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 124.dp)
                            .pantopusShadow(PantopusElevations.sm, RoundedCornerShape(Radii.lg))
                            .clip(RoundedCornerShape(Radii.lg))
                            .background(PantopusColors.appSurface)
                            .clickable { onTap(item.id) }
                            .padding(Spacing.s3)
                            .semantics { contentDescription = item.title },
                    verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(Radii.md))
                                .background(PantopusColors.primary100),
                        contentAlignment = Alignment.Center,
                    ) {
                        PantopusIconImage(
                            icon = item.icon,
                            contentDescription = null,
                            size = 22.dp,
                            tint = PantopusColors.primary600,
                        )
                    }
                    Text(item.title, style = PantopusTextStyle.body, color = PantopusColors.appText, maxLines = 2)
                }
            }
        }
    }
}

@Composable
fun HubRecentActivity(entries: List<ActivityEntry>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Box(modifier = Modifier.padding(horizontal = Spacing.s4)) { SectionHeader("Recent activity") }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.s4)
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.appSurface),
        ) {
            entries.forEach { entry -> ActivityRow(entry) }
        }
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntry) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s3)
                .semantics { contentDescription = "${entry.title}, ${entry.timeAgo}" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(entry.tint.backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = entry.icon,
                contentDescription = null,
                size = 18.dp,
                tint = entry.tint.color,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, style = PantopusTextStyle.body, color = PantopusColors.appText)
            Text(entry.timeAgo, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
        }
    }
}

@Composable
fun HubFloatingProgress(fraction: Float) {
    Row(
        modifier =
            Modifier
                .pantopusShadow(PantopusElevations.md, RoundedCornerShape(Radii.pill))
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.appText.copy(alpha = 0.9f))
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2)
                .sizeIn(maxWidth = 260.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Profile ${(fraction * 100).toInt()}%",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextInverse,
        )
        SegmentedProgressBar(
            currentStep = (fraction * 4).toInt().coerceIn(0, 4),
            totalSteps = 4,
            modifier = Modifier.width(140.dp),
        )
    }
}

@Composable
fun HubSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.CenterVertically) {
            Shimmer(width = 40.dp, height = 40.dp, cornerRadius = 20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                Shimmer(width = 80.dp, height = 10.dp)
                Shimmer(width = 140.dp, height = 14.dp)
            }
            Spacer(Modifier.weight(1f))
            Shimmer(width = 44.dp, height = 44.dp, cornerRadius = 8.dp)
            Shimmer(width = 44.dp, height = 44.dp, cornerRadius = 8.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            repeat(4) { Shimmer(width = 100.dp, height = 36.dp, cornerRadius = 9999.dp) }
        }
        Shimmer(width = 328.dp, height = 96.dp, cornerRadius = 12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Shimmer(width = 160.dp, height = 92.dp, cornerRadius = 12.dp)
            Shimmer(width = 160.dp, height = 92.dp, cornerRadius = 12.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Shimmer(width = 160.dp, height = 92.dp, cornerRadius = 12.dp)
            Shimmer(width = 160.dp, height = 92.dp, cornerRadius = 12.dp)
        }
        Shimmer(width = 328.dp, height = 180.dp, cornerRadius = 12.dp)
    }
}
