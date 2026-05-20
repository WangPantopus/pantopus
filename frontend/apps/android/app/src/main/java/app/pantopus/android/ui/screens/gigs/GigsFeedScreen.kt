@file:Suppress("MagicNumber", "LongMethod", "PackageNaming", "CyclomaticComplexMethod")

package app.pantopus.android.ui.screens.gigs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

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
    onOpenFilters: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: GigsFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeCategory by viewModel.activeCategory.collectAsStateWithLifecycle()
    val activeSort by viewModel.activeSort.collectAsStateWithLifecycle()
    val activeFilterCount by viewModel.activeFilterCount.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

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
                onOpenFilters = onOpenFilters,
            )
            when (val s = state) {
                is GigsFeedUiState.Loading -> LoadingFrame()
                is GigsFeedUiState.Empty -> EmptyFrame(radiusMiles = s.radiusMiles) { onCompose(activeCategory) }
                is GigsFeedUiState.Loaded ->
                    PopulatedFrame(rows = s.rows, onOpenGig = onOpenGig)
                is GigsFeedUiState.Error ->
                    ErrorFrame(message = s.message, onRetry = viewModel::refresh)
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
    }
}

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
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 4.dp)
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
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenMap)
                    .testTag("gigsMapToggle"),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Map,
                contentDescription = "Open map",
                size = 19.dp,
                tint = PantopusColors.appText,
            )
        }
    }
}

@Composable
private fun GigsSearchBar(onOpenSearch: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("gigsChipRow"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)
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
        if (activeFilterCount > 0) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.primary50)
                        .border(1.dp, PantopusColors.primary100, RoundedCornerShape(Radii.pill))
                        .clickable(onClick = onOpenFilters)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("gigsFiltersPill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.SlidersHorizontal,
                    contentDescription = null,
                    size = 11.dp,
                    strokeWidth = 2.4f,
                    tint = PantopusColors.primary700,
                )
                Text(
                    text = "$activeFilterCount filters",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.primary700,
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
                .padding(24.dp)
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("gigsFeedList"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = rows, key = { it.id }) { row ->
            GigRow(content = row, onTap = { onOpenGig(row.id) })
        }
    }
}

@Composable
internal fun GigRow(
    content: GigCardContent,
    onTap: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .clickable(onClick = onTap)
                .padding(16.dp)
                .testTag("gigsRow_${content.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryChip(category = content.category)
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
                .padding(horizontal = 8.dp, vertical = 2.dp),
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

@Composable
private fun BidPill(count: Int) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.warningBg)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = "Be the first",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.primary700,
        )
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
                .padding(24.dp)
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
