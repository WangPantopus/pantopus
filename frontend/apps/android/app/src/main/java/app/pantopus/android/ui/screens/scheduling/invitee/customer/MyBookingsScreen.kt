@file:Suppress("PackageNaming", "MagicNumber", "LongParameterList", "LongMethod")

package app.pantopus.android.ui.screens.scheduling.invitee.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.ErrorState
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.screens.scheduling.invitee.edge.RescheduleCancelPolicyScreen
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay

object MyBookingsTags {
    const val SCREEN = "myBookings"
    const val TAB_PREFIX = "myBookingsTab_"
    const val ROW_PREFIX = "myBookingRow_"
}

private const val TOAST_MS = 2200L

@Composable
fun MyBookingsScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: MyBookingsViewModel = hiltViewModel(),
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openManage by viewModel.openManage.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var manageToken by remember { mutableStateOf<String?>(null) }
    var toastText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.start() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(openManage) {
        openManage?.let {
            manageToken = it
            viewModel.openManageConsumed()
        }
    }
    LaunchedEffect(toast) {
        toast?.let {
            toastText = it
            viewModel.toastConsumed()
        }
    }
    LaunchedEffect(toastText) {
        if (toastText != null) {
            delay(TOAST_MS)
            toastText = null
        }
    }

    val activeToken = manageToken
    if (activeToken != null) {
        RescheduleCancelPolicyScreen(
            manageToken = activeToken,
            onBack = {
                manageToken = null
                viewModel.refresh()
            },
            onNavigate = onNavigate,
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) {
        MyBookingsContent(
            state = state,
            tab = tab,
            onBack = onBack,
            onTab = viewModel::selectTab,
            onRow = viewModel::onRowTap,
            onRetry = viewModel::load,
        )
        toastText?.let { ToastBar(it, modifier = Modifier.align(Alignment.BottomCenter)) }
    }
}

@Composable
fun MyBookingsContent(
    state: MyBookingsUiState,
    tab: MyBookingsTab,
    onTab: (MyBookingsTab) -> Unit,
    onRow: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().testTag(MyBookingsTags.SCREEN)) {
        TopBar(onBack = onBack)
        SegmentedTabs(tab = tab, onTab = onTab)
        when (state) {
            is MyBookingsUiState.Loading -> LoadingSkeleton()
            is MyBookingsUiState.Empty -> EmptyBody()
            is MyBookingsUiState.Error -> ErrorState(message = state.message, modifier = Modifier.fillMaxSize(), onRetry = onRetry)
            is MyBookingsUiState.Loaded -> Groups(state.groups, onRow = onRow)
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface).padding(horizontal = Spacing.s2, vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(Radii.md)).clickable(onClickLabel = "Back", onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = PantopusIcon.ChevronLeft, contentDescription = "Back", size = 20.dp, tint = PantopusColors.appText)
        }
        Text(
            text = "My bookings",
            style = PantopusTextStyle.h3,
            color = PantopusColors.appText,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            PantopusIconImage(icon = PantopusIcon.Search, contentDescription = null, size = 18.dp, tint = PantopusColors.appTextMuted)
        }
    }
}

@Composable
private fun SegmentedTabs(
    tab: MyBookingsTab,
    onTab: (MyBookingsTab) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s3, vertical = Spacing.s3)
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurfaceSunken)
                .padding(Spacing.s0),
    ) {
        MyBookingsTab.entries.forEach { entry ->
            val selected = entry == tab
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(Spacing.s0)
                        .clip(RoundedCornerShape(Radii.sm))
                        .background(if (selected) PantopusColors.appSurface else Color.Transparent)
                        .clickable { onTab(entry) }
                        .padding(vertical = Spacing.s2)
                        .testTag(MyBookingsTags.TAB_PREFIX + entry.name),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (entry == MyBookingsTab.Upcoming) "Upcoming" else "Past",
                    style = PantopusTextStyle.small,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) PantopusColors.primary700 else PantopusColors.appTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun Groups(
    groups: List<MyBookingGroup>,
    onRow: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Text(
            text = "Everything you've booked, in one place.",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s2),
        )
        groups.forEach { group ->
            GroupOverline(group)
            group.rows.forEach { row -> BookingRow(row = row, onClick = { onRow(row.id) }) }
        }
        Box(modifier = Modifier.size(Spacing.s8))
    }
}

@Composable
private fun GroupOverline(group: MyBookingGroup) {
    Row(
        modifier = Modifier.padding(top = Spacing.s3, bottom = Spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        if (group.attention) {
            PantopusIconImage(icon = PantopusIcon.AlertCircle, contentDescription = null, size = 12.dp, tint = PantopusColors.warning)
        }
        Text(
            text = group.overline.uppercase(),
            style = PantopusTextStyle.overline,
            color = if (group.attention) PantopusColors.warning else PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun BookingRow(
    row: MyBookingRow,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .clickable(onClick = onClick)
                .padding(Spacing.s3)
                .testTag(MyBookingsTags.ROW_PREFIX + row.id),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        HostAvatar(pillar = row.pillar, dimmed = row.dimmed)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.Bold,
                color = if (row.dimmed) PantopusColors.appTextSecondary else PantopusColors.appText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.subtitle,
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusPill(kind = row.pill)
        PantopusIconImage(icon = PantopusIcon.ChevronRight, contentDescription = null, size = 16.dp, tint = PantopusColors.appTextMuted)
    }
}

@Composable
private fun HostAvatar(
    pillar: SchedulingPillar,
    dimmed: Boolean,
) {
    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(if (dimmed) PantopusColors.appSurfaceSunken else pillar.accentBg),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = PantopusIcon.User, contentDescription = null, size = 18.dp, tint = pillar.accent)
        }
        Box(
            modifier =
                Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.appSurface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(pillar.accent),
        )
    }
}

@Composable
private fun StatusPill(kind: BookingPillKind) {
    val (bg, fg, border, label) =
        when (kind) {
            BookingPillKind.Confirmed ->
                PillStyle(
                    PantopusColors.successBg,
                    PantopusColors.success,
                    PantopusColors.successLight,
                    "Confirmed",
                )
            BookingPillKind.Pending -> PillStyle(PantopusColors.infoBg, PantopusColors.info, PantopusColors.infoLight, "Pending")
            BookingPillKind.Past ->
                PillStyle(
                    PantopusColors.appSurfaceSunken,
                    PantopusColors.appTextSecondary,
                    PantopusColors.appBorder,
                    "Past",
                )
            BookingPillKind.Cancelled -> PillStyle(PantopusColors.errorBg, PantopusColors.error, PantopusColors.errorLight, "Cancelled")
        }
    Text(
        text = label,
        style = PantopusTextStyle.overline,
        color = fg,
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(Radii.pill))
                .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
    )
}

private data class PillStyle(val bg: Color, val fg: Color, val border: Color, val label: String)

@Composable
private fun EmptyBody() {
    EmptyState(
        icon = PantopusIcon.Calendar,
        headline = "You haven't booked anything yet",
        subcopy = "Bookings you make show up here — everything in one place.",
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun LoadingSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.s4, vertical = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        repeat(4) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.xl))
                        .background(PantopusColors.appSurface)
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                        .padding(Spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                Shimmer(width = 42.dp, height = 42.dp, cornerRadius = Radii.pill)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Shimmer(width = 140.dp, height = 11.dp, cornerRadius = Radii.xs)
                    Shimmer(width = 180.dp, height = 9.dp, cornerRadius = Radii.xs)
                }
            }
        }
    }
}

@Composable
private fun ToastBar(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .padding(Spacing.s4)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appText)
                .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = PantopusTextStyle.small, color = PantopusColors.appBg)
    }
}
