@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.following

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.components.VerifiedBadge
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/** Test tag on the Following root container. */
const val FOLLOWING_TAG = "followingScreen"

private const val TOAST_DISMISS_DELAY_MS = 2_500L
private const val DIVIDER_INSET_DP = 70

/**
 * §1A① — "Following" (Beacons you follow). A pushed sub-route: back chevron,
 * centred title + count line, a segmented sort control, and the list grouped
 * client-side into New updates · Active · Quiet. Models the My bids screen's
 * screen + view-model + repository shape; rendered bespoke so every row
 * carries the cross-platform contract identifiers.
 */
@Composable
fun FollowingScreen(
    onBack: () -> Unit,
    onDiscover: () -> Unit = {},
    onOpenPersona: (String) -> Unit = {},
    viewModel: FollowingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sort by viewModel.selectedSort.collectAsStateWithLifecycle()
    val actionTarget by viewModel.actionTarget.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(TOAST_DISMISS_DELAY_MS)
            viewModel.dismissToast()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appSurfaceMuted)
                .testTag(FOLLOWING_TAG),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FollowingTopBar(state = state, onBack = onBack)
            when (val s = state) {
                is FollowingUiState.Loading -> {
                    FollowingSortControl(sort, viewModel::selectSort)
                    FollowingLoadingList()
                }
                is FollowingUiState.Loaded -> {
                    FollowingSortControl(sort, viewModel::selectSort)
                    FollowingLoadedList(s, onOpenPersona, viewModel::openActions)
                }
                is FollowingUiState.Empty -> FollowingEmpty(onDiscover)
                is FollowingUiState.Error -> FollowingError(s.message, viewModel::refresh)
            }
        }
        toast?.let { FollowingToastOverlay(it) }
    }

    actionTarget?.let { target ->
        FollowingActionSheet(
            target = target,
            onMarkSeen = { viewModel.markSeen(target) },
            onMute = { days -> viewModel.mute(target, days) },
            onUnfollow = { viewModel.unfollow(target) },
            onDismiss = { viewModel.closeActions() },
        )
    }
}

// region Chrome

@Composable
private fun FollowingTopBar(
    state: FollowingUiState,
    onBack: () -> Unit,
) {
    Column {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(PantopusColors.appSurfaceMuted),
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = Spacing.s1)
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .testTag("followingBack"),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.ChevronLeft,
                    contentDescription = "Back",
                    size = 25.dp,
                    strokeWidth = 2.2f,
                    tint = PantopusColors.appText,
                )
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Following", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
                countLine(state)?.let {
                    Text(it, fontSize = 11.5.sp, color = PantopusColors.appTextSecondary)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
    }
}

private fun countLine(state: FollowingUiState): String? =
    when (state) {
        is FollowingUiState.Loaded -> {
            when {
                state.totalFollowing == 0 -> null
                state.unreadBeacons > 0 -> "${state.totalFollowing} Beacons · ${state.unreadBeacons} with updates"
                else -> "${state.totalFollowing} Beacon${if (state.totalFollowing == 1) "" else "s"}"
            }
        }
        is FollowingUiState.Empty -> "0 Beacons"
        else -> null
    }

@Composable
private fun FollowingSortControl(
    sort: FollowingSort,
    onSelect: (FollowingSort) -> Unit,
) {
    Column {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(PantopusColors.appSurfaceMuted)
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s3)
                    .testTag("followingSortControl"),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.lg))
                        .background(PantopusColors.appSurfaceSunken)
                        .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                FollowingSort.entries.forEach { option ->
                    val active = option == sort
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(Radii.md))
                                .background(if (active) PantopusColors.appSurface else Color.Transparent)
                                .clickable { onSelect(option) }
                                .padding(vertical = 6.dp)
                                .testTag("followingSort.${option.wire}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option.label,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) PantopusColors.appText else PantopusColors.appTextSecondary,
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
    }
}

// endregion

// region Loaded list

@Composable
private fun FollowingLoadedList(
    state: FollowingUiState.Loaded,
    onOpenPersona: (String) -> Unit,
    onOverflow: (FollowingRow) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        state.sections.forEach { section ->
            item(key = "header-${section.kind.name}") {
                FollowingSectionHeader(section)
            }
            item(key = "group-${section.kind.name}") {
                FollowingRowGroup(section.rows, onOpenPersona, onOverflow)
            }
        }
        item { Spacer(Modifier.height(Spacing.s5)) }
    }
}

@Composable
private fun FollowingSectionHeader(section: FollowingSection) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.s5, end = Spacing.s5, top = 18.dp, bottom = Spacing.s2)
                .testTag(section.kind.testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Text(
            text = section.kind.header.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = if (section.isTinted) PantopusColors.primary600 else PantopusColors.appTextMuted,
        )
        Text("·", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appTextMuted)
        Text("${section.count}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appTextMuted)
        Box(Modifier.weight(1f).height(1.dp).background(PantopusColors.appBorderSubtle))
    }
}

@Composable
private fun FollowingRowGroup(
    rows: List<FollowingRow>,
    onOpenPersona: (String) -> Unit,
    onOverflow: (FollowingRow) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s3)
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl)),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(start = DIVIDER_INSET_DP.dp)
                        .background(PantopusColors.appBorderSubtle),
                )
            }
            FollowingRowItem(row, onOpenPersona, onOverflow)
        }
    }
}

@Composable
private fun FollowingRowItem(
    row: FollowingRow,
    onOpenPersona: (String) -> Unit,
    onOverflow: (FollowingRow) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (row.isMuted) 0.62f else 1f)
                .padding(start = 14.dp, end = Spacing.s3, top = 11.dp, bottom = 11.dp)
                .testTag("followingRow.${row.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).clickable { onOpenPersona(row.handle) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            FollowingAvatar(row.initials, row.tone.color, row.avatarUrl, row.verified, 44.dp)
            FollowingRowText(row, modifier = Modifier.weight(1f))
        }
        FollowingTrailing(row.trailing)
        Spacer(Modifier.width(Spacing.s1))
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onOverflow(row) }
                    .testTag("followingRow.overflow"),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.MoreHorizontal,
                contentDescription = "More",
                size = 18.dp,
                tint = PantopusColors.appTextMuted,
            )
        }
    }
}

@Composable
private fun FollowingRowText(
    row: FollowingRow,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = row.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (row.verified) {
                PantopusIconImage(
                    icon = PantopusIcon.BadgeCheck,
                    contentDescription = "Verified",
                    size = 14.dp,
                    tint = PantopusColors.primary600,
                )
            }
            row.tierName?.let { TierPill(it) }
        }
        Text(
            text = row.subtitle,
            fontSize = 11.5.sp,
            color = PantopusColors.appTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.padding(top = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Text(
                text = row.bodyText,
                fontSize = 12.sp,
                fontStyle = if (row.bodyIsQuiet) FontStyle.Italic else FontStyle.Normal,
                color = if (row.bodyIsQuiet) PantopusColors.appTextMuted else PantopusColors.appTextStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            row.timeLabel?.let {
                Text(it, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = PantopusColors.appTextMuted)
            }
        }
    }
}

@Composable
private fun TierPill(name: String) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.primary100)
                .padding(start = 5.dp, end = 7.dp, top = 1.dp, bottom = 1.dp)
                .testTag("followingRow.tierPill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Star,
            contentDescription = null,
            size = 9.dp,
            strokeWidth = 2.5f,
            tint = PantopusColors.primary700,
        )
        Text(name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PantopusColors.primary700)
    }
}

@Composable
private fun FollowingTrailing(trailing: FollowingRowTrailing) {
    when (trailing) {
        is FollowingRowTrailing.Unread ->
            Box(
                modifier =
                    Modifier
                        .defaultMinSize(minWidth = 20.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.primary600)
                        .padding(horizontal = 6.dp)
                        .testTag("followingRow.unreadBadge"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = trailing.text,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appTextInverse,
                )
            }
        is FollowingRowTrailing.Muted ->
            PantopusIconImage(
                icon = PantopusIcon.BellOff,
                contentDescription = "Muted",
                size = 16.dp,
                tint = PantopusColors.appTextMuted,
            )
        is FollowingRowTrailing.Chevron ->
            PantopusIconImage(
                icon = PantopusIcon.ChevronRight,
                contentDescription = null,
                size = 18.dp,
                tint = PantopusColors.appBorderStrong,
            )
    }
}

@Composable
internal fun FollowingAvatar(
    initials: String,
    color: Color,
    avatarUrl: String?,
    verified: Boolean,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(size).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = initials,
                    color = PantopusColors.appTextInverse,
                    fontSize = (size.value * 0.34f).sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (verified) {
            VerifiedBadge(size = if (size.value >= 44f) 16.dp else 13.dp, tint = PantopusColors.primary600)
        }
    }
}

// endregion

// region Loading / Empty / Error

@Composable
private fun FollowingLoadingList() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s3)
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s3)
                .testTag("followingLoading"),
    ) {
        repeat(6) { index ->
            if (index > 0) Spacer(Modifier.height(Spacing.s3))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(PantopusColors.appSurfaceSunken))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2), modifier = Modifier.weight(1f)) {
                    Shimmer(width = 140.dp, height = 12.dp)
                    Shimmer(width = 90.dp, height = 10.dp, cornerRadius = Radii.xs)
                    Shimmer(width = 200.dp, height = 10.dp, cornerRadius = Radii.xs)
                }
            }
        }
    }
}

@Composable
private fun FollowingEmpty(onDiscover: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appSurfaceMuted)
                .padding(horizontal = Spacing.s6)
                .testTag("followingEmpty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(76.dp).clip(CircleShape).background(PantopusColors.primary50),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.RadioTower,
                contentDescription = null,
                size = 34.dp,
                strokeWidth = 1.7f,
                tint = PantopusColors.primary600,
            )
        }
        Spacer(Modifier.height(Spacing.s3))
        Text(
            text = "You're not following any Beacons yet",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            modifier = Modifier.padding(horizontal = Spacing.s4),
        )
        Spacer(Modifier.height(Spacing.s2))
        Text(
            text = "Follow Beacons — verified people, businesses, and civic accounts — to get their updates here.",
            fontSize = 13.5.sp,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.padding(horizontal = Spacing.s2),
        )
        Spacer(Modifier.height(Spacing.s4))
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.primary600)
                    .clickable(onClick = onDiscover)
                    .padding(horizontal = Spacing.s6, vertical = 12.dp)
                    .testTag("followingEmpty.discoverBtn"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Compass,
                contentDescription = null,
                size = 16.dp,
                strokeWidth = 2.4f,
                tint = PantopusColors.appTextInverse,
            )
            Text("Discover Beacons", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appTextInverse)
        }
    }
}

@Composable
private fun FollowingError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appSurfaceMuted)
                .padding(Spacing.s5)
                .testTag("followingError"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 34.dp,
            tint = PantopusColors.appTextMuted,
        )
        Spacer(Modifier.height(Spacing.s3))
        Text("Couldn't load who you follow", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appText)
        Spacer(Modifier.height(Spacing.s2))
        Text(message, fontSize = 13.5.sp, color = PantopusColors.appTextSecondary)
        Spacer(Modifier.height(Spacing.s3))
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.primary600)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = Spacing.s5, vertical = 12.dp)
                    .testTag("followingError.retry"),
            contentAlignment = Alignment.Center,
        ) {
            Text("Retry", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appTextInverse)
        }
    }
}

@Composable
private fun FollowingToastOverlay(toast: FollowingToast) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            modifier =
                Modifier
                    .padding(bottom = Spacing.s10, start = Spacing.s4, end = Spacing.s4)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(if (toast.isError) PantopusColors.error else PantopusColors.success)
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s2)
                    .testTag("followingToast"),
        ) {
            Text(text = toast.text, fontSize = 14.sp, color = PantopusColors.appTextInverse)
        }
    }
}

// endregion
