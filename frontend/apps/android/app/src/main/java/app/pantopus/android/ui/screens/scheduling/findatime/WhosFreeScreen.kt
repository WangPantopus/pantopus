@file:Suppress("PackageNaming", "TooManyFunctions", "LongMethod", "MagicNumber", "LongParameterList")

package app.pantopus.android.ui.screens.scheduling.findatime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.ErrorState
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingRoutes
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

const val WHOS_FREE_TAG = "whosFreeScreen"

/** A tapped free block, surfaced for the local plan-something action card. */
data class TappedBlock(val memberName: String, val columnLabel: String)

/**
 * F7 Who's Free — Household Availability. A glanceable heat grid composed from
 * each member's personal availability (`GET /whos-free`); tapping a free block
 * opens F4 scoped or the calendar add-event form.
 */
@Composable
fun WhosFreeScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: WhosFreeViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.start() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tapped by remember { mutableStateOf<TappedBlock?>(null) }

    WhosFreeContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onAdd = { viewModel.addEventRoute()?.let(onNavigate) },
        onSelectFilter = viewModel::selectFilter,
        onSetView = viewModel::setView,
        onTryNext = viewModel::tryNextWindow,
        onTapFree = { name, col -> tapped = TappedBlock(name, col) },
        tapped = tapped,
        onDismissTapped = { tapped = null },
        onFindTimeHere = {
            viewModel.seedFindATime()
            tapped = null
            onNavigate(SchedulingRoutes.FIND_A_TIME)
        },
        onAddEvent = {
            tapped = null
            viewModel.addEventRoute()?.let(onNavigate)
        },
    )
}

@Composable
fun WhosFreeContent(
    state: WhosFreeUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAdd: () -> Unit,
    onSelectFilter: (String) -> Unit,
    onSetView: (GridView) -> Unit,
    onTryNext: () -> Unit,
    onTapFree: (String, String) -> Unit,
    tapped: TappedBlock?,
    onDismissTapped: () -> Unit,
    onFindTimeHere: () -> Unit,
    onAddEvent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(PantopusColors.appBg).testTag(WHOS_FREE_TAG)) {
        val canAdd = state is WhosFreeUiState.Loaded
        FtTopBar(title = "Who's free", onBack = onBack, trailingText = "Add", trailingEnabled = canAdd, onTrailing = onAdd)
        Head(view = (state as? WhosFreeUiState.Loaded)?.view ?: GridView.Day, onSetView = onSetView)
        when (state) {
            is WhosFreeUiState.Loading -> ComposingGrid()
            is WhosFreeUiState.Error -> ErrorState(message = state.message, onRetry = onRetry)
            is WhosFreeUiState.Loaded ->
                LoadedGrid(
                    state = state,
                    onSelectFilter = onSelectFilter,
                    onTryNext = onTryNext,
                    onTapFree = onTapFree,
                    tapped = tapped,
                    onDismissTapped = onDismissTapped,
                    onFindTimeHere = onFindTimeHere,
                    onAddEvent = onAddEvent,
                )
        }
    }
}

@Composable
private fun Head(
    view: GridView,
    onSetView: (GridView) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface).padding(horizontal = Spacing.s4, vertical = Spacing.s3), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        FtSegmented(
            options = listOf("Day", "Week"),
            selectedIndex = if (view == GridView.Day) 0 else 1,
            onSelect = { onSetView(if (it == 0) GridView.Day else GridView.Week) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            PantopusIconImage(icon = PantopusIcon.Users, contentDescription = null, size = 11.dp, tint = HomeAccent)
            Text(
                text = "Composed from each member's personal availability.",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
                modifier = Modifier.padding(start = Spacing.s1),
            )
        }
    }
    HorizontalDivider(thickness = 1.dp, color = PantopusColors.appBorder)
}

@Composable
private fun LoadedGrid(
    state: WhosFreeUiState.Loaded,
    onSelectFilter: (String) -> Unit,
    onTryNext: () -> Unit,
    onTapFree: (String, String) -> Unit,
    tapped: TappedBlock?,
    onDismissTapped: () -> Unit,
    onFindTimeHere: () -> Unit,
    onAddEvent: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        FilterChips(filters = state.filters, selected = state.selectedFilter, onSelect = onSelectFilter)

        if (state.emptyAllBusy) {
            FtBanner(
                tone = FtBannerTone.Info,
                icon = PantopusIcon.CalendarX,
                title = "No overlapping free time",
                body = "Everyone's booked up for ${state.windowLabel}. Try the next window to find a shared opening.",
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.xl))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                    .padding(Spacing.s3),
        ) {
            HeatGridView(
                columns = state.grid.columns,
                rows = state.visibleRows,
                onTapFree = onTapFree,
            )
            Legend()
        }

        if (tapped != null) {
            TappedActionCard(tapped = tapped, onFindTimeHere = onFindTimeHere, onAddEvent = onAddEvent, onDismiss = onDismissTapped)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                PantopusIconImage(icon = PantopusIcon.Plus, contentDescription = null, size = 12.dp, tint = PantopusColors.appTextMuted)
                Text(text = "Tap a free block to plan something", style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary, modifier = Modifier.padding(start = Spacing.s1))
            }
        }

        if (state.optedOutNames.isNotEmpty()) {
            FtBanner(
                tone = FtBannerTone.Warning,
                icon = PantopusIcon.EyeOff,
                title = "${state.optedOutNames.joinToString(", ")} hasn't shared free/busy",
                body = "You can't see their availability or include them in Find a time until they share it.",
            )
        }

        if (state.emptyAllBusy) {
            FtSecondaryButton(label = "Try the next window", icon = PantopusIcon.ChevronRight, tint = HomeAccentDark, onClick = onTryNext)
        }
    }
}

// ─── Filter chips ─────────────────────────────────────────────────────────────

@Composable
private fun FilterChips(
    filters: List<FilterChip>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        filters.forEach { chip ->
            val on = chip.id == selected
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(if (on) HomeAccentBg else PantopusColors.appSurface)
                        .border(1.dp, if (on) HomeAccent else PantopusColors.appBorder, RoundedCornerShape(Radii.pill))
                        .clickable(onClickLabel = chip.label) { onSelect(chip.id) }
                        .padding(horizontal = Spacing.s3, vertical = Spacing.s1),
            ) {
                Text(text = chip.label, style = PantopusTextStyle.caption, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) HomeAccentDark else PantopusColors.appTextStrong)
            }
        }
    }
}

// ─── Heat grid ────────────────────────────────────────────────────────────────

private val NAME_COL_WIDTH = 64.dp
private val CELL_HEIGHT = 26.dp

@Composable
private fun HeatGridView(
    columns: List<GridColumn>,
    rows: List<MemberRow>,
    onTapFree: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(NAME_COL_WIDTH))
            columns.forEach { col ->
                Text(
                    text = col.label,
                    style = PantopusTextStyle.overline,
                    color = PantopusColors.appTextMuted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        rows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.width(NAME_COL_WIDTH), verticalAlignment = Alignment.CenterVertically) {
                    FtAvatar(member = row.member, size = 18.dp)
                    Text(
                        text = row.member.name.substringBefore(" "),
                        style = PantopusTextStyle.caption,
                        fontWeight = FontWeight.SemiBold,
                        color = PantopusColors.appText,
                        maxLines = 1,
                        modifier = Modifier.padding(start = Spacing.s1),
                    )
                }
                row.cells.forEachIndexed { i, cell ->
                    HeatCell(
                        state = cell,
                        modifier = Modifier.weight(1f).padding(start = 3.dp),
                        onClick = if (cell == CellState.Free) {
                            { onTapFree(row.member.name.substringBefore(" "), columns.getOrNull(i)?.label ?: "") }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatCell(
    state: CellState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val bg =
        when (state) {
            CellState.Free -> HomeAccentBg
            CellState.Busy -> PantopusColors.appSurfaceSunken
            CellState.Unknown -> PantopusColors.appSurfaceRaised
        }
    Box(
        modifier =
            modifier
                .height(CELL_HEIGHT)
                .clip(RoundedCornerShape(Radii.sm))
                .background(bg)
                .then(if (onClick != null) Modifier.clickable(onClickLabel = "Free block", onClick = onClick) else Modifier),
        contentAlignment = Alignment.TopStart,
    ) {
        when (state) {
            CellState.Free ->
                Box(modifier = Modifier.padding(3.dp).size(5.dp).clip(CircleShape).background(HomeAccent))
            CellState.Unknown ->
                Text(text = "?", style = PantopusTextStyle.caption, fontWeight = FontWeight.Bold, color = PantopusColors.appTextMuted, modifier = Modifier.align(Alignment.Center))
            CellState.Busy -> Unit
        }
    }
}

@Composable
private fun Legend() {
    val items =
        listOf(
            CellState.Free to "Free",
            CellState.Busy to "Busy",
            CellState.Unknown to "Unknown",
        )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.s3),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        items.forEach { (cell, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeatCell(state = cell, modifier = Modifier.size(13.dp))
                Text(text = label, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary, modifier = Modifier.padding(start = Spacing.s1))
            }
        }
    }
}

// ─── Tapped action card / popover ─────────────────────────────────────────────

@Composable
private fun TappedActionCard(
    tapped: TappedBlock,
    onFindTimeHere: () -> Unit,
    onAddEvent: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, HomeAccent, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s3)
                .testTag("whosFreePopover"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${tapped.memberName} · ${tapped.columnLabel} · free",
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appText,
                modifier = Modifier.weight(1f),
            )
            Box(modifier = Modifier.size(24.dp).clip(CircleShape).clickable(onClickLabel = "Dismiss", onClick = onDismiss), contentAlignment = Alignment.Center) {
                PantopusIconImage(icon = PantopusIcon.X, contentDescription = "Dismiss", size = 14.dp, tint = PantopusColors.appTextMuted)
            }
        }
        FtPrimaryButton(label = "Find a time here", icon = PantopusIcon.Users, onClick = onFindTimeHere)
        FtSecondaryButton(label = "Add event", icon = PantopusIcon.CalendarPlus, onClick = onAddEvent)
    }
}

// ─── Loading ──────────────────────────────────────────────────────────────────

@Composable
private fun ComposingGrid() {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.s4), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s2), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Building this week's availability", style = PantopusTextStyle.small, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.xl))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                    .padding(Spacing.s3),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(4) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.width(NAME_COL_WIDTH), verticalAlignment = Alignment.CenterVertically) {
                        Shimmer(width = 18.dp, height = 18.dp, cornerRadius = Radii.pill)
                        Shimmer(width = 28.dp, height = 9.dp, cornerRadius = Radii.xs, modifier = Modifier.padding(start = Spacing.s1))
                    }
                    repeat(6) {
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(start = 3.dp)
                                    .height(CELL_HEIGHT)
                                    .clip(RoundedCornerShape(Radii.sm))
                                    .background(PantopusColors.appSurfaceSunken),
                        )
                    }
                }
            }
        }
    }
}
