@file:Suppress("MagicNumber", "LongMethod", "PackageNaming", "CyclomaticComplexMethod")
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.pantopus.android.ui.screens.gigs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.screens.shared.feed.FeedComposeFAB
import app.pantopus.android.ui.screens.shared.feed.FeedSkeletonCard
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Gigs feed (T2.3). Reached from Hub → Gigs pillar. Three frames:
 * shimmer-loading, briefcase-empty, populated. Category chips are
 * per-category brand-colored when active, matching the row chip tint.
 */
@Composable
fun GigsFeedScreen(
    onOpenGig: (String) -> Unit = {},
    onCompose: (GigsCategory) -> Unit = {},
    onOpenMap: (GigsCategory) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: GigsFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeCategory by viewModel.activeCategory.collectAsStateWithLifecycle()
    val activeSort by viewModel.activeSort.collectAsStateWithLifecycle()
    val activeFilterCount by viewModel.activeFilterCount.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val radiusSuggestion by viewModel.radiusSuggestion.collectAsStateWithLifecycle()
    val newTaskCount by viewModel.newTaskCount.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    // P1.D — undo window: the toast auto-dismisses after ~5s.
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
                .background(PantopusColors.appBg)
                .testTag("gigsFeed"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GigsTopBar(onBack = onBack, onOpenMap = { onOpenMap(activeCategory) })
            GigsSearchBar(onOpenSearch = onOpenSearch)
            GigsCategoryChipRow(
                active = activeCategory,
                onSelect = viewModel::selectCategory,
            )
            GigsSortFilterRow(
                activeSort = activeSort,
                activeFilterCount = activeFilterCount,
                onSelectSort = viewModel::selectSort,
                onOpenFilters = { showFilters = true },
            )
            // P1.B — slim radius-suggestion banner above the list.
            radiusSuggestion?.let { suggestion ->
                if (state is GigsFeedUiState.Loaded || state is GigsFeedUiState.Empty) {
                    RadiusSuggestionBanner(
                        suggestion = suggestion,
                        onAccept = viewModel::acceptRadiusSuggestion,
                        onDismiss = viewModel::dismissRadiusSuggestion,
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val s = state) {
                    is GigsFeedUiState.Loading -> LoadingFrame()
                    is GigsFeedUiState.BrowseLoading -> BrowseLoadingFrame()
                    is GigsFeedUiState.Empty -> EmptyFrame(radiusMiles = s.radiusMiles) { onCompose(activeCategory) }
                    is GigsFeedUiState.Loaded ->
                        PopulatedFrame(
                            rows = s.rows,
                            onOpenGig = onOpenGig,
                            onDismissGig = viewModel::dismissGig,
                            onHideCategory = viewModel::hideCategory,
                        )
                    is GigsFeedUiState.BrowseLoaded ->
                        BrowseFrame(
                            content = s.browse,
                            onOpenGig = onOpenGig,
                            onSeeAll = viewModel::exitBrowse,
                            onSelectCategory = viewModel::selectCategory,
                            onSeeAllTasks = { viewModel.exitBrowse(GigsSort.Newest) },
                            onSeeAllQuickJobs = viewModel::seeAllQuickJobs,
                        )
                    is GigsFeedUiState.Error ->
                        ErrorFrame(message = s.message, onRetry = viewModel::refresh)
                }
                // P1.E — floating "new tasks" pill; hidden while loading.
                val loadingNow = state is GigsFeedUiState.Loading || state is GigsFeedUiState.BrowseLoading
                if (newTaskCount > 0 && !loadingNow) {
                    NewTasksBanner(
                        count = newTaskCount,
                        onTap = viewModel::refreshFromNewTasksBanner,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.s2),
                    )
                }
            }
        }
        FeedComposeFAB(
            onClick = { onCompose(activeCategory) },
            contentDescription = "Post a task",
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Spacing.s4, bottom = Spacing.s10)
                    .testTag("gigsComposeFab"),
        )
        toast?.let { payload ->
            GigsFeedToastOverlay(payload = payload, onUndo = viewModel::undo)
        }
    }

    if (showFilters) {
        GigFilterSheet(
            criteria = filters,
            onApply = viewModel::applyFilters,
            onDismiss = { showFilters = false },
        )
    }
}

/** P1.D — undo window before the toast auto-dismisses. */
private const val TOAST_DISMISS_DELAY_MS = 5_000L

// MARK: - Chrome

@Composable
private fun GigsTopBar(
    onBack: (() -> Unit)?,
    onOpenMap: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PantopusColors.appBg)
                .padding(start = Spacing.s4, end = Spacing.s2, top = 10.dp, bottom = Spacing.s1)
                .testTag("gigsTopBar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .testTag("gigsBackButton"),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.ChevronLeft,
                    contentDescription = "Back",
                    size = 22.dp,
                    tint = PantopusColors.appText,
                )
            }
        }
        Text(
            text = "Gigs",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.weight(1f))
        GigsViewModeToggle(onOpenMap = onOpenMap)
    }
}

/**
 * List/Map view-mode toggle for the Gigs feed top bar. "List" is the
 * active segment; tapping "Map" pushes the Tasks map (which returns to the
 * feed via its floating-pill chevron). Mirrors iOS `GigsViewModeToggle`.
 */
@Composable
private fun GigsViewModeToggle(onOpenMap: () -> Unit) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.appSurfaceSunken)
                .padding(2.dp)
                .testTag("gigsViewModeToggle"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GigsViewModeSegment(icon = PantopusIcon.Menu, label = "List", active = true, onClick = {})
        GigsViewModeSegment(icon = PantopusIcon.Map, label = "Map", active = false, onClick = onOpenMap)
    }
}

@Composable
private fun GigsViewModeSegment(
    icon: PantopusIcon,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(if (active) PantopusColors.primary600 else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.s3)
                .height(32.dp)
                .testTag("gigsViewMode_${label.lowercase()}")
                .semantics { contentDescription = if (active) "$label view, selected" else "$label view" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        PantopusIconImage(
            icon = icon,
            contentDescription = null,
            size = 14.dp,
            strokeWidth = 2.2f,
            tint = if (active) PantopusColors.appTextInverse else PantopusColors.appTextSecondary,
        )
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) PantopusColors.appTextInverse else PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun GigsSearchBar(onOpenSearch: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4, vertical = Spacing.s1)
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurfaceSunken)
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = 14.dp)
                .heightIn(min = 44.dp)
                .testTag("gigsSearchBar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Search,
            contentDescription = null,
            size = 17.dp,
            tint = PantopusColors.appTextSecondary,
        )
        Text(
            text = "Search gigs, skills, neighborhoods…",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
internal fun GigsCategoryChipRow(
    active: GigsCategory,
    onSelect: (GigsCategory) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = Spacing.s4, vertical = Spacing.s3)
                .testTag("gigsChipRow"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GigsCategory.entries.forEach { category ->
            val isActive = category == active
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(if (isActive) category.color else PantopusColors.appSurface)
                        .border(
                            width = if (isActive) 0.dp else 1.dp,
                            color = if (isActive) Color.Transparent else PantopusColors.appBorder,
                            shape = RoundedCornerShape(Radii.pill),
                        )
                        .clickable { onSelect(category) }
                        .padding(horizontal = 14.dp)
                        .heightIn(min = 28.dp)
                        .testTag("gigsChip_${category.key}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = category.label,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) PantopusColors.appTextInverse else PantopusColors.appTextStrong,
                )
            }
        }
    }
}

@Composable
private fun GigsSortFilterRow(
    activeSort: GigsSort,
    activeFilterCount: Int,
    onSelectSort: (GigsSort) -> Unit,
    onOpenFilters: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.s4, end = Spacing.s4, top = Spacing.s1, bottom = 10.dp)
                .testTag("gigsSortFilterRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Row(
                modifier = Modifier.clickable { expanded = true }.testTag("gigsSortMenu"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "Sort:",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = PantopusColors.appTextSecondary,
                )
                Text(
                    text = activeSort.label,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appTextStrong,
                )
                PantopusIconImage(
                    icon = PantopusIcon.ChevronDown,
                    contentDescription = null,
                    size = 13.dp,
                    strokeWidth = 2.4f,
                    tint = PantopusColors.appTextStrong,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                GigsSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.label) },
                        onClick = {
                            expanded = false
                            onSelectSort(sort)
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        val filtersActive = activeFilterCount > 0
        Box(
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onOpenFilters)
                    .testTag("gigsFiltersButton"),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(if (filtersActive) PantopusColors.primary50 else PantopusColors.appSurface)
                        .border(
                            1.dp,
                            if (filtersActive) PantopusColors.primary100 else PantopusColors.appBorder,
                            RoundedCornerShape(Radii.pill),
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.SlidersHorizontal,
                    contentDescription = null,
                    size = 11.dp,
                    strokeWidth = 2.4f,
                    tint = if (filtersActive) PantopusColors.primary700 else PantopusColors.appTextSecondary,
                )
                Text(
                    text = if (filtersActive) "$activeFilterCount filters" else "Filters",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (filtersActive) PantopusColors.primary700 else PantopusColors.appTextSecondary,
                )
            }
        }
    }
}

// MARK: - Frames

@Composable
internal fun LoadingFrame() {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("gigsFeedLoading"),
        contentPadding = PaddingValues(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        items(4) { FeedSkeletonCard() }
    }
}

@Composable
internal fun EmptyFrame(
    radiusMiles: Double,
    onPostTask: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.s6)
                .testTag("gigsFeedEmpty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.primary50),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Briefcase,
                contentDescription = null,
                size = 32.dp,
                strokeWidth = 1.8f,
                tint = PantopusColors.primary600,
            )
        }
        Spacer(modifier = Modifier.size(Spacing.s3))
        Text(
            text = "No gigs nearby",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.size(Spacing.s1))
        Text(
            text = "Be the first to post one.",
            fontSize = 13.5.sp,
            color = PantopusColors.appTextSecondary,
        )
        Spacer(modifier = Modifier.size(Spacing.s3))
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.primary600)
                    .clickable(onClick = onPostTask)
                    .padding(horizontal = 22.dp)
                    .heightIn(min = 44.dp)
                    .testTag("gigsEmptyPostTask"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Pencil,
                contentDescription = null,
                size = 15.dp,
                strokeWidth = 2.4f,
                tint = PantopusColors.appTextInverse,
            )
            Text(
                text = "Post a task",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
        Spacer(modifier = Modifier.size(Spacing.s4))
        RadiusHintPill(radiusMiles = radiusMiles)
    }
}

@Composable
private fun RadiusHintPill(radiusMiles: Double) {
    val label =
        if (radiusMiles % 1.0 == 0.0) {
            "${radiusMiles.toInt()} mi"
        } else {
            String.format("%.1f mi", radiusMiles)
        }
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("gigsEmptyRadiusPill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.MapPin,
            contentDescription = null,
            size = 13.dp,
            tint = PantopusColors.appTextMuted,
        )
        Text(
            text = "Within ",
            fontSize = 11.5.sp,
            color = PantopusColors.appTextSecondary,
        )
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appTextStrong,
        )
        Text(
            text = " · widen in filter",
            fontSize = 11.5.sp,
            color = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
internal fun PopulatedFrame(
    rows: List<GigCardContent>,
    onOpenGig: (String) -> Unit,
    onDismissGig: (String) -> Unit = {},
    onHideCategory: (GigsCategory) -> Unit = {},
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("gigsFeedList"),
        contentPadding = PaddingValues(start = Spacing.s4, end = Spacing.s4, top = Spacing.s1, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        items(items = rows, key = { it.id }) { row ->
            DismissableGigRow(
                content = row,
                onTap = { onOpenGig(row.id) },
                onDismiss = { onDismissGig(row.id) },
                onHideCategory = { onHideCategory(row.category) },
            )
        }
    }
}

/**
 * P1.D — feed row wrapped in a swipe-to-dismiss ("Not interested") with a
 * long-press menu carrying the same action plus "Hide all <Category>".
 */
@Composable
private fun DismissableGigRow(
    content: GigCardContent,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    onHideCategory: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) onDismiss()
                // Never settle — the VM removes the row optimistically and
                // an undo re-inserts it in place.
                false
            },
        )
    Box {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            backgroundContent = { NotInterestedSwipeBackground() },
            modifier = Modifier.testTag("gigsRowSwipe_${content.id}"),
        ) {
            GigRow(content = content, onTap = onTap, onLongPress = { menuOpen = true })
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Not interested") },
                onClick = {
                    menuOpen = false
                    onDismiss()
                },
                modifier = Modifier.testTag("gigsRowMenu_notInterested"),
            )
            DropdownMenuItem(
                text = { Text("Hide all ${content.category.label}") },
                onClick = {
                    menuOpen = false
                    onHideCategory()
                },
                modifier = Modifier.testTag("gigsRowMenu_hideCategory"),
            )
        }
    }
}

/** P1.D — revealed behind the row while swiping toward "Not interested". */
@Composable
private fun NotInterestedSwipeBackground() {
    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.errorBg)
                .padding(horizontal = Spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        PantopusIconImage(
            icon = PantopusIcon.EyeOff,
            contentDescription = null,
            size = 15.dp,
            tint = PantopusColors.error,
        )
        Text(
            text = "Not interested",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.error,
        )
    }
}

@Composable
internal fun GigRow(
    content: GigCardContent,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .combinedClickable(onClick = onTap, onLongClick = onLongPress)
                .padding(Spacing.s4)
                .testTag("gigsRow_${content.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            CategoryChip(category = content.category)
            if (content.isUrgent) {
                UrgentPill()
            }
            if (content.metaLine.isNotEmpty()) {
                Text(
                    text = content.metaLine,
                    fontSize = 10.sp,
                    color = PantopusColors.appTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = content.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 19.sp,
        )
        if (content.body.isNotEmpty()) {
            Text(
                text = content.body,
                fontSize = 12.sp,
                color = PantopusColors.appTextStrong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = content.price,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.primary600,
            )
            if (content.bidCount > 0) {
                BidPill(count = content.bidCount)
            } else {
                BeTheFirstPill()
            }
            Spacer(modifier = Modifier.weight(1f))
            if (content.distanceLabel != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.MapPin,
                        contentDescription = null,
                        size = 11.dp,
                        tint = PantopusColors.appTextSecondary,
                    )
                    Text(
                        text = content.distanceLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = PantopusColors.appTextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(category: GigsCategory) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(category.color.copy(alpha = 0.12f))
                .padding(horizontal = Spacing.s2, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = category.label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = category.color,
            letterSpacing = 0.4.sp,
        )
    }
}

/** P1.A — amber "URGENT" pill beside the category badge. */
@Composable
private fun UrgentPill() {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.warningBg)
                .padding(horizontal = Spacing.s2, vertical = 2.dp)
                .testTag("gigRow.urgent"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "URGENT",
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.warning,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun BidPill(count: Int) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.warningBg)
                .padding(horizontal = Spacing.s2, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Gavel,
            contentDescription = null,
            size = 9.dp,
            strokeWidth = 2.5f,
            tint = PantopusColors.warning,
        )
        Text(
            text = "$count bids",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.warning,
        )
    }
}

@Composable
private fun BeTheFirstPill() {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.primary50)
                .padding(horizontal = Spacing.s2, vertical = 2.dp),
    ) {
        Text(
            text = "Be the first",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.primary700,
        )
    }
}

// MARK: - P1.B / P1.D / P1.E banners + toast

/**
 * P1.B — slim suggestion banner above the list: "Only N tasks within
 * X mi" + a "Search Y mi" action bumping the radius ladder, dismissible
 * for the session.
 */
@Composable
internal fun RadiusSuggestionBanner(
    suggestion: GigsRadiusSuggestion,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    val taskWord = if (suggestion.visibleCount == 1) "task" else "tasks"
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4, vertical = Spacing.s1)
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.primary50)
                .padding(start = Spacing.s3, end = Spacing.s1)
                .heightIn(min = 40.dp)
                .testTag("gigsFeed.radiusSuggestion"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.MapPin,
            contentDescription = null,
            size = 13.dp,
            tint = PantopusColors.primary600,
        )
        Text(
            text = "Only ${suggestion.visibleCount} $taskWord within ${milesLabel(suggestion.currentRadiusMiles)} mi",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = PantopusColors.primary600,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Search ${milesLabel(suggestion.suggestedRadiusMiles)} mi",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.primary700,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .clickable(onClick = onAccept)
                    .padding(horizontal = Spacing.s2, vertical = Spacing.s2)
                    .testTag("gigsFeed.radiusSuggestion.accept"),
        )
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .testTag("gigsFeed.radiusSuggestion.dismiss"),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.X,
                contentDescription = "Dismiss",
                size = 12.dp,
                strokeWidth = 2.4f,
                tint = PantopusColors.primary600,
            )
        }
    }
}

private fun milesLabel(miles: Double): String =
    if (miles % 1.0 == 0.0) "${miles.toInt()}" else String.format("%.1f", miles)

/** P1.E — floating "N new tasks — tap to refresh" pill over the feed. */
@Composable
internal fun NewTasksBanner(
    count: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val taskWord = if (count == 1) "new task" else "new tasks"
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.primary600)
                .clickable(onClick = onTap)
                .padding(horizontal = Spacing.s4)
                .heightIn(min = 36.dp)
                .testTag("gigsFeed.newTasksBanner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.ArrowUp,
            contentDescription = null,
            size = 13.dp,
            strokeWidth = 2.4f,
            tint = PantopusColors.appTextInverse,
        )
        Text(
            text = "$count $taskWord — tap to refresh",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appTextInverse,
        )
    }
}

/**
 * P1.D — transient bottom toast (mirrors `MyBidsToastOverlay`) with an
 * optional Undo action for dismissals / category hides.
 */
@Composable
internal fun GigsFeedToastOverlay(
    payload: GigsFeedToast,
    onUndo: (GigsFeedUndo) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(bottom = Spacing.s10, start = Spacing.s4, end = Spacing.s4)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(if (payload.isError) PantopusColors.error else PantopusColors.success)
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s2)
                    .testTag("gigsFeed.toast"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(
                text = payload.text,
                style = PantopusTextStyle.small,
                color = PantopusColors.appTextInverse,
            )
            payload.undo?.let { undo ->
                Text(
                    text = "Undo",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appTextInverse,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(Radii.pill))
                            .clickable { onUndo(undo) }
                            .padding(horizontal = Spacing.s1)
                            .testTag("gigsFeed.undoButton"),
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
                .padding(Spacing.s6)
                .testTag("gigsFeedError"),
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
            text = "Couldn't load Gigs",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        Spacer(modifier = Modifier.size(Spacing.s2))
        Text(
            text = message,
            fontSize = 13.5.sp,
            color = PantopusColors.appTextSecondary,
        )
        Spacer(modifier = Modifier.size(Spacing.s4))
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.primary600)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 22.dp)
                    .heightIn(min = 44.dp)
                    .testTag("gigsFeedRetry"),
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
