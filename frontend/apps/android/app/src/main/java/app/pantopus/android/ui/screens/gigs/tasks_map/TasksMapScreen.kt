@file:Suppress("MagicNumber", "LongMethod", "PackageNaming", "LongParameterList", "TooManyFunctions")

package app.pantopus.android.ui.screens.gigs.tasks_map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.screens.gigs.GigFilterCriteria
import app.pantopus.android.ui.screens.gigs.GigFilterSheet
import app.pantopus.android.ui.screens.gigs.GigsCategory
import app.pantopus.android.ui.screens.gigs.GigsSort
import app.pantopus.android.ui.screens.shared.map_list_hybrid.MapListHybridDetent
import app.pantopus.android.ui.screens.shared.map_list_hybrid.MapListHybridShell
import app.pantopus.android.ui.screens.shared.map_list_hybrid.MapListHybridShellStaticPreview
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii

/**
 * A11.1 Tasks map — the Gigs-only mode of the MapListHybrid archetype.
 * Same canvas as the generic Nearby map, filtered to tasks, titled
 * "Tasks map", and topped with a primary "Post task" FAB stacked above the
 * locate / layers controls. Reached from the Gigs feed's list/map toggle;
 * the floating-pill chevron returns to the list.
 *
 * Mirrors iOS `TasksMapView`.
 */
@Composable
fun TasksMapScreen(
    onOpenTask: (String) -> Unit = {},
    onCompose: (GigsCategory) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: TasksMapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeCategory by viewModel.activeCategory.collectAsStateWithLifecycle()
    val activeSort by viewModel.activeSort.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()

    var detent by remember { mutableStateOf(MapListHybridDetent.Standard) }
    var recenterToken by remember { mutableIntStateOf(0) }
    var showFilters by remember { mutableStateOf(false) }
    var filterCriteria by remember { mutableStateOf(GigFilterCriteria()) }

    LaunchedEffect(Unit) { viewModel.load() }

    val items = (state as? TasksMapUiState.Populated)?.items.orEmpty()

    MapListHybridShell(
        pins = items.map { it.toPin() },
        detent = detent,
        onDetentChange = { detent = it },
        anchor = viewModel.anchor,
        selectedPinId = selectedId,
        recenterTrigger = recenterToken,
        onPinTap = { id ->
            viewModel.select(id)
            detent = MapListHybridDetent.Standard
        },
        topPill = { TasksMapTopPill(onBack = onBack, onFilters = { showFilters = true }) },
        categoryChips = {
            TasksMapCategoryChips(active = activeCategory, onSelect = viewModel::selectCategory)
        },
        mapControls = {
            TasksMapControls(
                onPostTask = { onCompose(activeCategory) },
                onLocate = { recenterToken++ },
                onLayers = { showFilters = true },
            )
        },
        sheetHeader = {
            TasksMapSheetHeader(
                count = items.size,
                activeSort = activeSort,
                onSelectSort = viewModel::selectSort,
            )
        },
        sheetBody = {
            TasksMapSheetBody(
                state = state,
                selectedId = selectedId,
                onTap = { id ->
                    viewModel.select(id)
                    onOpenTask(id)
                },
                onPost = { onCompose(activeCategory) },
                onWiden = { viewModel.selectCategory(GigsCategory.All) },
                onRetry = viewModel::refresh,
            )
        },
        modifier = Modifier.testTag("tasksMap"),
    )

    if (showFilters) {
        GigFilterSheet(
            criteria = filterCriteria,
            onApply = { filterCriteria = it },
            onDismiss = { showFilters = false },
        )
    }
}

// MARK: - Chrome

@Composable
internal fun TasksMapTopPill(
    onBack: () -> Unit,
    onFilters: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(Color.White.copy(alpha = 0.96f))
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.pill))
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(Radii.pill))
                .padding(start = 6.dp, end = Spacing.s2, top = Spacing.s2, bottom = Spacing.s2)
                .fillMaxWidth()
                .testTag("tasksMapPill"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .testTag("tasksMapBack")
                    .semantics { contentDescription = "Back to list" },
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.ChevronLeft,
                contentDescription = null,
                size = 18.dp,
                strokeWidth = 2.2f,
                tint = PantopusColors.appText,
            )
        }
        Text(
            text = "Tasks map",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onFilters)
                    .testTag("tasksMapFilters")
                    .semantics { contentDescription = "Filters" },
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.SlidersHorizontal,
                contentDescription = null,
                size = 16.dp,
                strokeWidth = 2.2f,
                tint = PantopusColors.appText,
            )
        }
    }
}

@Composable
internal fun TasksMapCategoryChips(
    active: GigsCategory,
    onSelect: (GigsCategory) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 14.dp)
                .testTag("tasksMapCategoryChips"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GigsCategory.entries.forEach { category ->
            val isActive = category == active
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(if (isActive) category.color else Color.White.copy(alpha = 0.96f))
                        .border(
                            width = if (isActive) 0.dp else 1.dp,
                            color = if (isActive) Color.Transparent else PantopusColors.appBorder,
                            shape = RoundedCornerShape(Radii.pill),
                        )
                        .shadow(elevation = 4.dp, shape = RoundedCornerShape(Radii.pill))
                        .clickable { onSelect(category) }
                        .padding(horizontal = Spacing.s3)
                        .height(28.dp)
                        .testTag("tasksMapCategoryChip_${category.key}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (category != GigsCategory.All) {
                    Box(
                        modifier =
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isActive) Color.White else category.color),
                    )
                }
                Text(
                    text = category.label,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) PantopusColors.appTextInverse else PantopusColors.appTextStrong,
                )
            }
        }
    }
}

@Composable
internal fun TasksMapControls(
    onPostTask: () -> Unit,
    onLocate: () -> Unit,
    onLayers: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        // Post-task FAB — extended pill, stacked above the controls.
        Row(
            modifier =
                Modifier
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(Radii.pill))
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.primary600)
                    .clickable(onClick = onPostTask)
                    .padding(start = 14.dp, end = 18.dp)
                    .height(48.dp)
                    .testTag("tasksMapPostFab")
                    .semantics { contentDescription = "Post task" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Plus,
                contentDescription = null,
                size = 18.dp,
                strokeWidth = 2.6f,
                tint = PantopusColors.appTextInverse,
            )
            Text(
                text = "Post task",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
        TasksMapControlButton(
            icon = PantopusIcon.MapPin,
            label = "Locate me",
            testTagId = "tasksMapLocate",
            onClick = onLocate,
        )
        TasksMapControlButton(
            icon = PantopusIcon.Map,
            label = "Layers",
            testTagId = "tasksMapLayers",
            onClick = onLayers,
        )
    }
}

@Composable
private fun TasksMapControlButton(
    icon: PantopusIcon,
    label: String,
    testTagId: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(38.dp)
                .shadow(elevation = 4.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.96f))
                .border(1.dp, PantopusColors.appBorder, CircleShape)
                .clickable(onClick = onClick)
                .testTag(testTagId)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(
            icon = icon,
            contentDescription = null,
            size = 16.dp,
            tint = PantopusColors.appText,
        )
    }
}

@Composable
internal fun TasksMapSheetHeader(
    count: Int,
    activeSort: GigsSort,
    onSelectSort: (GigsSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = Spacing.s1, bottom = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$count ${if (count == 1) "task" else "tasks"} nearby",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            modifier = Modifier.testTag("tasksMapCount").semantics { heading() },
        )
        Spacer(modifier = Modifier.weight(1f))
        Box {
            Row(
                modifier = Modifier.clickable { expanded = true }.testTag("tasksMapSort"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                Text(
                    text = "Sort:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = PantopusColors.appTextSecondary,
                )
                Text(
                    text = activeSort.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appTextStrong,
                )
                PantopusIconImage(
                    icon = PantopusIcon.ChevronDown,
                    contentDescription = null,
                    size = 12.dp,
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
    }
}

// MARK: - Sheet body (four states)

@Composable
internal fun TasksMapSheetBody(
    state: TasksMapUiState,
    selectedId: String?,
    onTap: (String) -> Unit,
    onPost: () -> Unit,
    onWiden: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        is TasksMapUiState.Loading -> TasksMapLoadingRail()
        is TasksMapUiState.Populated ->
            TasksMapRail(items = state.items, selectedId = selectedId, onTap = onTap)
        is TasksMapUiState.Empty -> TasksMapEmptyHero(onPost = onPost, onWiden = onWiden)
        is TasksMapUiState.Error -> TasksMapErrorBody(message = state.message, onRetry = onRetry)
    }
}

@Composable
private fun TasksMapRail(
    items: List<TaskMapItem>,
    selectedId: String?,
    onTap: (String) -> Unit,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = Spacing.s4, end = Spacing.s4, bottom = Spacing.s3)
                    .testTag("tasksMapRail"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items.forEach { item ->
                TaskRailCard(
                    item = item,
                    selected = item.id == selectedId,
                    onTap = { onTap(item.id) },
                )
            }
        }
        TasksMapPaginationDots(total = minOf(items.size, 3), index = 0)
    }
}

@Composable
internal fun TaskRailCard(
    item: TaskMapItem,
    selected: Boolean,
    onTap: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .width(240.dp)
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(PantopusColors.appSurface)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) item.category.color else PantopusColors.appBorder,
                    shape = RoundedCornerShape(14.dp),
                )
                .clickable(onClick = onTap)
                .padding(Spacing.s3)
                .testTag("tasksMapCard_${item.id}"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(item.category.color),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = taskCategoryGlyph(item.category),
                contentDescription = null,
                size = 22.dp,
                tint = Color.White,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Text(
                    text = item.price,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.primary600,
                )
                Text(
                    text = "· ${item.distanceLabel}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = PantopusColors.appTextSecondary,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (item.bidCount > 0) {
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(Radii.pill))
                                .background(PantopusColors.warningBg)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "${item.bidCount} ${if (item.bidCount == 1) "bid" else "bids"}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = PantopusColors.warning,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksMapPaginationDots(
    total: Int,
    index: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s3),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(maxOf(total, 1)) { i ->
            Box(
                modifier =
                    Modifier
                        .width(if (i == index) 16.dp else 5.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(if (i == index) PantopusColors.primary600 else PantopusColors.appBorderStrong),
            )
        }
    }
}

@Composable
private fun TasksMapLoadingRail() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = Spacing.s4, end = Spacing.s4, bottom = Spacing.s3)
                .testTag("tasksMapLoading"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(3) {
            Row(
                modifier =
                    Modifier
                        .width(240.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PantopusColors.appSurface)
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(14.dp))
                        .padding(Spacing.s3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PantopusColors.appSurfaceSunken),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(Radii.sm))
                                .background(PantopusColors.appSurfaceSunken),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.6f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(Radii.sm))
                                .background(PantopusColors.appSurfaceSunken),
                    )
                }
            }
        }
    }
}

@Composable
internal fun TasksMapEmptyHero(
    onPost: () -> Unit,
    onWiden: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = Spacing.s3)
                .testTag("tasksMapEmpty"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PantopusColors.primary50)
                    .border(1.dp, PantopusColors.primary100, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.MapPin,
                contentDescription = null,
                size = 24.dp,
                tint = PantopusColors.primary600,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.s3))
        Text(
            text = "No tasks in this area yet",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = "Be the first to post one — verified neighbors within a half-mile will see it.",
            fontSize = 12.5.sp,
            color = PantopusColors.appTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.widthIn(max = 248.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.primary600)
                        .clickable(onClick = onPost)
                        .padding(horizontal = 14.dp)
                        .height(36.dp)
                        .testTag("tasksMapEmptyPost"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Plus,
                    contentDescription = null,
                    size = 14.dp,
                    strokeWidth = 2.6f,
                    tint = PantopusColors.appTextInverse,
                )
                Text(
                    text = "Post a task",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appTextInverse,
                )
            }
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.appSurface)
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.pill))
                        .clickable(onClick = onWiden)
                        .padding(horizontal = 14.dp)
                        .height(36.dp)
                        .testTag("tasksMapWiden"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Search,
                    contentDescription = null,
                    size = 13.dp,
                    strokeWidth = 2.2f,
                    tint = PantopusColors.appTextSecondary,
                )
                Text(
                    text = "Widen search",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appTextStrong,
                )
            }
        }
    }
}

@Composable
private fun TasksMapErrorBody(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s6, vertical = Spacing.s1)
                .testTag("tasksMapError"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 28.dp,
            tint = PantopusColors.error,
        )
        Text(
            text = message,
            fontSize = 13.sp,
            color = PantopusColors.appTextSecondary,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.primary600)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = Spacing.s4)
                    .height(38.dp)
                    .testTag("tasksMapRetry"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Try again",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
    }
}

/**
 * Static, render-only assembly of the Tasks-map chrome over the shell's
 * flat map stand-in. Used by Paparazzi (and `@Preview`) so the populated /
 * empty frames snapshot without spinning up Google Maps.
 */
@Composable
internal fun TasksMapStaticPreview(
    state: TasksMapUiState,
    activeCategory: GigsCategory = GigsCategory.All,
    selectedId: String? = null,
    activeSort: GigsSort = GigsSort.Closest,
    modifier: Modifier = Modifier,
) {
    val count = (state as? TasksMapUiState.Populated)?.items?.size ?: 0
    MapListHybridShellStaticPreview(
        detent = MapListHybridDetent.Standard,
        topPill = { TasksMapTopPill(onBack = {}, onFilters = {}) },
        categoryChips = { TasksMapCategoryChips(active = activeCategory, onSelect = {}) },
        mapControls = { TasksMapControls(onPostTask = {}, onLocate = {}, onLayers = {}) },
        sheetHeader = { TasksMapSheetHeader(count = count, activeSort = activeSort, onSelectSort = {}) },
        sheetBody = {
            TasksMapSheetBody(
                state = state,
                selectedId = selectedId,
                onTap = {},
                onPost = {},
                onWiden = {},
                onRetry = {},
            )
        },
        modifier = modifier,
    )
}
