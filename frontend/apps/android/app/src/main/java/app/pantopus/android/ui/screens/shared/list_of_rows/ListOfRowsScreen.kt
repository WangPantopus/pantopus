@file:Suppress(
    "MagicNumber",
    "UnusedPrivateMember",
    "PackageNaming",
    "LongMethod",
    "LongParameterList",
    "ComplexMethod",
    "CyclomaticComplexMethod",
    "TooManyFunctions",
)

package app.pantopus.android.ui.screens.shared.list_of_rows

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.components.AvatarWithIdentityRing
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.components.StatusChip
import app.pantopus.android.ui.components.VerifiedBadge
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/**
 * Test tag on the list-of-rows root container — used by Compose tests.
 * Mirrors iOS `accessibilityIdentifier("listOfRowsContainer")`.
 */
const val LIST_OF_ROWS_TAG = "listOfRowsContainer"

/**
 * Reusable List-of-Rows screen.
 *
 * T5.0 — extended additively. Original render path (icon-leading +
 * chevron/chip-trailing, no body/chips/footer/highlight) is unchanged.
 * New optional chrome (searchBar, chipStrip, banner) and FAB variants
 * are opt-in.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ListOfRowsScreen(
    title: String,
    state: ListOfRowsUiState,
    onRefresh: () -> Unit,
    onEndReached: () -> Unit,
    tabs: List<ListOfRowsTab> = emptyList(),
    selectedTab: String = "",
    onSelectTab: (String) -> Unit = {},
    topBarAction: TopBarAction? = null,
    fab: FabAction? = null,
    onBack: (() -> Unit)? = null,
    searchBar: SearchBarConfig? = null,
    chipStrip: ChipStripConfig? = null,
    banner: BannerConfig? = null,
    listingContext: ListingContextConfig? = null,
    subtitle: String? = null,
) {
    val pullState =
        rememberPullRefreshState(
            refreshing = state is ListOfRowsUiState.Loading,
            onRefresh = onRefresh,
        )

    Scaffold(
        modifier = Modifier.testTag(LIST_OF_ROWS_TAG),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = title,
                            style = PantopusTextStyle.h3,
                            color = PantopusColors.appText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = PantopusTextStyle.caption,
                                color = PantopusColors.appTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            PantopusIconImage(
                                icon = PantopusIcon.ChevronLeft,
                                contentDescription = "Back",
                                tint = PantopusColors.appText,
                            )
                        }
                    }
                },
                actions = {
                    if (topBarAction != null) {
                        TopBarActionButton(action = topBarAction)
                    }
                },
                colors =
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = PantopusColors.appSurface,
                    ),
            )
        },
        floatingActionButton = {
            if (fab != null) {
                FabComposable(fab)
            }
        },
        containerColor = PantopusColors.appBg,
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (searchBar != null) {
                SearchBarRow(searchBar)
                HorizontalDivider(color = PantopusColors.appBorderSubtle)
            }
            if (chipStrip != null) {
                ChipStripRow(chipStrip)
                HorizontalDivider(color = PantopusColors.appBorderSubtle)
            } else if (tabs.isNotEmpty()) {
                TabStrip(tabs = tabs, selectedId = selectedTab, onSelect = onSelectTab)
                HorizontalDivider(color = PantopusColors.appBorderSubtle)
            }
            Box(modifier = Modifier.fillMaxSize().pullRefresh(pullState)) {
                when (state) {
                    ListOfRowsUiState.Loading -> LoadingRows()
                    is ListOfRowsUiState.Loaded -> LoadedList(state, banner, listingContext, onEndReached)
                    is ListOfRowsUiState.Empty ->
                        EmptyState(
                            icon = state.icon,
                            headline = state.headline,
                            subcopy = state.subcopy,
                            ctaTitle = state.ctaTitle,
                            onCta = state.onCta,
                        )
                    is ListOfRowsUiState.Error -> ErrorBanner(state.message, onRetry = onRefresh)
                }
                PullRefreshIndicator(
                    refreshing = state is ListOfRowsUiState.Loading,
                    state = pullState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    contentColor = PantopusColors.primary600,
                )
            }
        }
    }
}

// ─── Chrome ────────────────────────────────────────────────────

@Composable
private fun TabStrip(
    tabs: List<ListOfRowsTab>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.s4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        tabs.forEach { tab ->
            Column(
                modifier =
                    Modifier
                        .clickable { onSelect(tab.id) }
                        .sizeIn(minHeight = 44.dp)
                        .padding(vertical = Spacing.s2)
                        .testTag("tab.${tab.id}")
                        .semantics { contentDescription = tab.label },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val active = tab.id == selectedId
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                    Text(
                        text = tab.label,
                        style = PantopusTextStyle.small,
                        color = if (active) PantopusColors.primary600 else PantopusColors.appTextSecondary,
                    )
                    if (tab.count != null) {
                        Text(
                            text = "${tab.count}",
                            style = PantopusTextStyle.caption,
                            color = if (active) PantopusColors.primary600 else PantopusColors.appTextMuted,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.s1))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (active) PantopusColors.primary600 else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun SearchBarRow(config: SearchBarConfig) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurfaceSunken)
                    .padding(horizontal = Spacing.s3, vertical = Spacing.s2)
                    .testTag("listOfRowsSearchBar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Search,
                contentDescription = null,
                size = 16.dp,
                tint = PantopusColors.appTextSecondary,
            )
            BasicTextField(
                value = config.text,
                onValueChange = config.onChange,
                textStyle =
                    TextStyle(
                        color = PantopusColors.appText,
                        fontSize = 14.sp,
                    ),
                cursorBrush = SolidColor(PantopusColors.primary600),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (config.text.isEmpty()) {
                        Text(
                            text = config.placeholder,
                            style = PantopusTextStyle.small,
                            color = PantopusColors.appTextMuted,
                        )
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
private fun ChipStripRow(config: ChipStripConfig) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        config.chips.forEach { chip ->
            val active = chip.id == config.selectedId
            val bg = if (active) PantopusColors.primary600 else PantopusColors.appSurface
            val fg = if (active) PantopusColors.appTextInverse else PantopusColors.appTextStrong
            val border = if (active) PantopusColors.primary600 else PantopusColors.appBorder
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(bg)
                        .border(1.dp, border, RoundedCornerShape(Radii.pill))
                        .clickable { config.onSelect(chip.id) }
                        .heightIn(min = 30.dp)
                        .padding(horizontal = Spacing.s3, vertical = Spacing.s1)
                        .testTag("chip.${chip.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                if (chip.icon != null) {
                    PantopusIconImage(
                        icon = chip.icon,
                        contentDescription = null,
                        size = 12.dp,
                        tint = fg,
                    )
                }
                Text(text = chip.label, style = PantopusTextStyle.caption, color = fg)
            }
        }
    }
}

@Composable
private fun BannerCard(config: BannerConfig) {
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.primary50)
                    .border(1.dp, PantopusColors.primary100, RoundedCornerShape(Radii.md))
                    .padding(Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(Radii.sm))
                        .background(PantopusColors.appSurface)
                        .border(1.dp, PantopusColors.primary100, RoundedCornerShape(Radii.sm)),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = config.icon,
                    contentDescription = null,
                    size = 16.dp,
                    tint = PantopusColors.primary600,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = config.title, style = PantopusTextStyle.small, color = PantopusColors.appText)
                if (config.subtitle != null) {
                    Text(
                        text = config.subtitle,
                        style = PantopusTextStyle.caption,
                        color = PantopusColors.appTextSecondary,
                    )
                }
            }
        }
    }

    if (config.onTap != null) {
        Box(modifier = Modifier.fillMaxWidth().clickable(onClick = config.onTap)) { rowContent() }
    } else {
        rowContent()
    }
}

@Composable
private fun ListingContextHeader(config: ListingContextConfig) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                    .padding(Spacing.s3)
                    .testTag("listingContextHeader"),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                ListingContextThumbnail(image = config.thumbnail)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                    ) {
                        Text(
                            text = config.title,
                            style = PantopusTextStyle.body,
                            fontWeight = FontWeight.SemiBold,
                            color = PantopusColors.appText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = config.askPrice,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PantopusColors.appText,
                        )
                    }
                    if (config.meta.isNotEmpty()) {
                        ListingContextMetaRow(items = config.meta)
                    }
                    Spacer(Modifier.height(2.dp))
                    StatusChip(
                        text = config.statusChip.label,
                        variant = config.statusChip.variant,
                        icon = config.statusChip.icon,
                    )
                }
            }
        }
        if (config.offerCount != null || config.sortLabel != null) {
            Spacer(Modifier.height(Spacing.s3))
            ListingContextSortStrip(
                count = config.offerCount,
                sortLabel = config.sortLabel,
                onSort = config.onSort,
            )
        }
    }
}

@Composable
private fun ListingContextThumbnail(image: ThumbnailImage) {
    val gradient =
        when (image) {
            is ThumbnailImage.IconOnGradient -> image.gradient
            is ThumbnailImage.Remote -> image.gradient
        }
    val icon =
        when (image) {
            is ThumbnailImage.IconOnGradient -> image.icon
            is ThumbnailImage.Remote -> image.fallback
        }
    Box(
        modifier =
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(Radii.md))
                .background(
                    Brush.linearGradient(listOf(gradient.start, gradient.end)),
                ),
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(
            icon = icon,
            contentDescription = null,
            size = 28.dp,
            tint = PantopusColors.appTextInverse,
        )
    }
}

@Composable
private fun ListingContextMetaRow(items: List<ListingContextMeta>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Text(
                    text = "·",
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextMuted,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (item.icon != null) {
                    PantopusIconImage(
                        icon = item.icon,
                        contentDescription = null,
                        size = 11.dp,
                        tint = PantopusColors.appTextSecondary,
                    )
                }
                Text(
                    text = item.text,
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ListingContextSortStrip(
    count: Int?,
    sortLabel: String?,
    onSort: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (count != null) {
            Text(
                text = "$count ${if (count == 1) "offer" else "offers"}",
                style = PantopusTextStyle.caption,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appTextStrong,
                modifier = Modifier.testTag("listingContextOfferCount"),
            )
        }
        Spacer(Modifier.weight(1f))
        if (sortLabel != null) {
            val rowModifier =
                if (onSort != null) {
                    Modifier
                        .clip(RoundedCornerShape(Radii.sm))
                        .clickable(onClick = onSort)
                        .padding(horizontal = Spacing.s1, vertical = 2.dp)
                        .testTag("listingContextSort")
                } else {
                    Modifier
                }
            Row(
                modifier = rowModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.ArrowsRepeat,
                    contentDescription = null,
                    size = 12.dp,
                    tint = PantopusColors.appTextSecondary,
                )
                Text(
                    text = sortLabel,
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                )
                PantopusIconImage(
                    icon = PantopusIcon.ChevronDown,
                    contentDescription = null,
                    size = 12.dp,
                    tint = PantopusColors.appTextSecondary,
                )
            }
        }
    }
}

// ─── States ────────────────────────────────────────────────────

@Composable
private fun LoadingRows() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        items(6) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.md))
                        .background(PantopusColors.appSurface)
                        .padding(Spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                Shimmer(width = 40.dp, height = 40.dp, cornerRadius = 20.dp)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                    Shimmer(width = 180.dp, height = 14.dp)
                    Shimmer(width = 120.dp, height = 12.dp)
                }
            }
        }
    }
}

@Composable
private fun LoadedList(
    state: ListOfRowsUiState.Loaded,
    banner: BannerConfig?,
    listingContext: ListingContextConfig?,
    onEndReached: () -> Unit,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: return@derivedStateOf false
            state.hasMore && total > 0 && last >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onEndReached() }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (listingContext != null) {
            item(key = "listingContext") { ListingContextHeader(listingContext) }
        }
        if (banner != null) {
            item(key = "banner") { BannerCard(banner) }
        }
        state.sections.forEach { section ->
            if (section.header != null || section.count != null || section.onSeeAll != null) {
                item(key = "header_${section.id}") {
                    SectionHeaderRow(section)
                }
            }
            when (section.style) {
                SectionStyle.Flat ->
                    items(section.rows, key = { it.id }) { row ->
                        RowView(row = row, cardContext = RowCardContext.Standalone)
                    }
                SectionStyle.Card ->
                    item(key = "card_${section.id}") {
                        SectionCard(section)
                    }
            }
        }
        if (state.hasMore) {
            item(key = "end-sentinel") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.s3),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = PantopusColors.primary600, strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(section: RowSection) {
    Row(
        modifier = Modifier.padding(vertical = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        if (section.header != null) {
            Text(
                text = section.header.uppercase(),
                style = PantopusTextStyle.overline,
                color = PantopusColors.appTextSecondary,
            )
        }
        if (section.count != null) {
            Text(
                text = "(${section.count})",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextMuted,
            )
        }
        Spacer(Modifier.weight(1f))
        if (section.onSeeAll != null) {
            Row(
                modifier =
                    Modifier
                        .clickable(onClick = section.onSeeAll)
                        .padding(Spacing.s1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                Text(
                    text = "See all",
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.primary600,
                )
                PantopusIconImage(
                    icon = PantopusIcon.ChevronRight,
                    contentDescription = null,
                    size = 12.dp,
                    tint = PantopusColors.primary600,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(section: RowSection) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg)),
    ) {
        section.rows.forEachIndexed { index, row ->
            RowView(row = row, cardContext = RowCardContext.Grouped)
            if (index < section.rows.size - 1) {
                HorizontalDivider(color = PantopusColors.appBorder)
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.s6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 40.dp,
            tint = PantopusColors.error,
        )
        Spacer(Modifier.height(Spacing.s3))
        Text("Couldn't load the list", style = PantopusTextStyle.h3, color = PantopusColors.appText)
        Spacer(Modifier.height(Spacing.s1))
        Text(
            message,
            style = PantopusTextStyle.small,
            color = PantopusColors.appTextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.s4))
        PrimaryButton(title = "Try again", onClick = onRetry, modifier = Modifier.sizeIn(maxWidth = 240.dp))
    }
}

// ─── Row ───────────────────────────────────────────────────────

internal enum class RowCardContext { Standalone, Grouped }

@Composable
internal fun RowView(
    row: RowModel,
    cardContext: RowCardContext = RowCardContext.Standalone,
) {
    val isGrouped = cardContext == RowCardContext.Grouped
    val background =
        when {
            isGrouped -> Color.Transparent
            row.highlight is RowHighlight.Unread -> PantopusColors.primary25
            else -> PantopusColors.appSurface
        }
    val border: Color? =
        when {
            isGrouped -> null
            row.highlight is RowHighlight.Unread -> PantopusColors.personalBg
            row.highlight is RowHighlight.Leading -> PantopusColors.warning
            else -> PantopusColors.appBorder
        }
    val cornerRadius = if (isGrouped) 0.dp else Radii.lg
    val opacity =
        if (row.highlight is RowHighlight.Archived || row.highlight is RowHighlight.Muted) 0.78f else 1f

    val baseModifier =
        if (isGrouped) {
            Modifier.fillMaxWidth().clickable(onClick = row.onTap)
        } else {
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius))
                .background(background)
                .let { mod -> if (border != null) mod.border(1.dp, border, RoundedCornerShape(cornerRadius)) else mod }
                .clickable(onClick = row.onTap)
        }
    val modifier = baseModifier.alpha(opacity)

    Column(
        modifier = modifier.padding(Spacing.s3).heightIn(min = 60.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        if (row.highlight is RowHighlight.Leading) {
            LeadingBadge()
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            LeadingView(row.leading)
            ContentColumn(
                row = row,
                modifier = Modifier.weight(1f),
            )
            TrailingView(
                trailing = row.trailing,
                onSecondary = row.onSecondary,
                rowTitle = row.title,
            )
        }
        if (row.note != null) {
            NoteBlock(row.note)
        }
        if (row.footer != null) {
            FooterStack(row.footer)
        }
    }
}

@Composable
private fun ContentColumn(
    row: RowModel,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            Text(
                text = row.title,
                style = PantopusTextStyle.body,
                fontWeight =
                    if (row.highlight is RowHighlight.Unread) {
                        FontWeight.Bold
                    } else {
                        FontWeight.SemiBold
                    },
                color = PantopusColors.appText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (row.inlineChip != null) {
                ChipPill(row.inlineChip)
            }
            if (row.highlight is RowHighlight.Unread) {
                Spacer(Modifier.weight(1f))
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PantopusColors.primary600),
                )
            }
        }
        if (row.subtitle != null) {
            Spacer(Modifier.height(2.dp))
            MetaLine(text = row.subtitle, icon = row.subtitleIcon)
        }
        if (row.body != null) {
            Spacer(Modifier.height(Spacing.s1))
            MetaLine(text = row.body, icon = row.bodyIcon)
        }
        if (!row.chips.isNullOrEmpty()) {
            Spacer(Modifier.height(Spacing.s1))
            ChipRowView(chips = row.chips, timeMeta = row.timeMeta, metaTail = row.metaTail)
        }
    }
}

@Composable
private fun MetaLine(
    text: String,
    icon: PantopusIcon?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        if (icon != null) {
            PantopusIconImage(
                icon = icon,
                contentDescription = null,
                size = 11.dp,
                tint = PantopusColors.appTextSecondary,
            )
        }
        Text(
            text = text,
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─── Leading view ──────────────────────────────────────────────

@Composable
private fun LeadingView(leading: RowLeading) {
    when (leading) {
        is RowLeading.None -> Unit
        is RowLeading.Icon ->
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .background(PantopusColors.appSurfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = leading.icon,
                    contentDescription = null,
                    size = 20.dp,
                    tint = leading.tint,
                )
            }
        is RowLeading.Avatar ->
            AvatarWithIdentityRing(
                name = leading.name,
                identity = leading.identity,
                ringProgress = leading.ringProgress,
                imageUrl = leading.imageUrl,
            )
        is RowLeading.TypeIcon ->
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .background(leading.background),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = leading.icon,
                    contentDescription = null,
                    size = 19.dp,
                    tint = leading.foreground,
                )
            }
        is RowLeading.CategoryGradientIcon ->
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(leading.gradient.start, leading.gradient.end),
                            ),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = leading.icon,
                    contentDescription = null,
                    size = 20.dp,
                    tint = PantopusColors.appTextInverse,
                )
            }
        is RowLeading.AvatarWithBadge -> AvatarWithBadgeView(leading)
        is RowLeading.Thumbnail -> ThumbnailView(leading)
        is RowLeading.BidderStack -> BidderStackView(leading)
    }
}

@Composable
private fun AvatarWithBadgeView(leading: RowLeading.AvatarWithBadge) {
    val size = leading.size.sizeDp.dp
    val initials =
        leading.name
            .split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercase() }
            .joinToString("")
    Box(modifier = Modifier.size(size)) {
        Box(
            modifier =
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(
                        when (val bg = leading.background) {
                            is AvatarBackground.Solid -> SolidColor(bg.color)
                            is AvatarBackground.Gradient ->
                                Brush.linearGradient(listOf(bg.gradient.start, bg.gradient.end))
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                color = PantopusColors.appTextInverse,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.32f).sp,
            )
        }
        if (leading.verified) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp),
            ) {
                VerifiedBadge(size = 16.dp)
            }
        }
    }
}

@Composable
private fun ThumbnailView(leading: RowLeading.Thumbnail) {
    val size = leading.size.sizeDp.dp
    val image = leading.image
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(Radii.md))
                .background(
                    Brush.linearGradient(
                        colors =
                            when (image) {
                                is ThumbnailImage.IconOnGradient ->
                                    listOf(image.gradient.start, image.gradient.end)
                                is ThumbnailImage.Remote ->
                                    listOf(image.gradient.start, image.gradient.end)
                            },
                    ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        val icon =
            when (image) {
                is ThumbnailImage.IconOnGradient -> image.icon
                is ThumbnailImage.Remote -> image.fallback
            }
        PantopusIconImage(
            icon = icon,
            contentDescription = null,
            size = (size.value * 0.42f).dp,
            tint = PantopusColors.appTextInverse,
        )
        // Remote photo overlay deferred to feature PRs that wire Coil
        // through the design tokens; this fallback renders the icon-on-
        // gradient placeholder which the design accepts when no photo
        // is available.
    }
}

@Composable
private fun BidderStackView(leading: RowLeading.BidderStack) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        leading.bidders.forEachIndexed { index, bidder ->
            val tileSize = 22.dp
            val offset = if (index == 0) 0.dp else (-8).dp
            Box(
                modifier =
                    Modifier
                        .offset(x = offset)
                        .size(tileSize)
                        .clip(CircleShape)
                        .background(toneBackground(bidder.tone))
                        .border(2.dp, PantopusColors.appSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = bidder.initials.take(2).uppercase(),
                    color = toneForeground(bidder.tone),
                    fontSize = (tileSize.value * 0.36f).sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (leading.overflow > 0) {
            val tileSize = 22.dp
            val offset = if (leading.bidders.isEmpty()) 0.dp else (-8).dp
            Box(
                modifier =
                    Modifier
                        .offset(x = offset)
                        .size(tileSize)
                        .clip(CircleShape)
                        .background(PantopusColors.appSurfaceSunken)
                        .border(2.dp, PantopusColors.appSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+${leading.overflow}",
                    color = PantopusColors.appTextStrong,
                    fontSize = (tileSize.value * 0.36f).sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun toneBackground(tone: BidderTone): Color =
    when (tone) {
        BidderTone.Sky -> PantopusColors.primary200
        BidderTone.Teal -> PantopusColors.successLight
        BidderTone.Amber -> PantopusColors.warningLight
        BidderTone.Rose -> PantopusColors.errorLight
        BidderTone.Violet -> PantopusColors.businessBg
        BidderTone.Slate -> PantopusColors.appSurfaceSunken
    }

private fun toneForeground(tone: BidderTone): Color =
    when (tone) {
        BidderTone.Sky -> PantopusColors.primary800
        BidderTone.Teal -> PantopusColors.success
        BidderTone.Amber -> PantopusColors.warning
        BidderTone.Rose -> PantopusColors.error
        BidderTone.Violet -> PantopusColors.business
        BidderTone.Slate -> PantopusColors.appTextStrong
    }

// ─── Trailing view ─────────────────────────────────────────────

@Composable
private fun TrailingView(
    trailing: RowTrailing,
    onSecondary: (() -> Unit)?,
    rowTitle: String,
) {
    when (trailing) {
        is RowTrailing.None -> Unit
        is RowTrailing.Status -> StatusChip(text = trailing.text, variant = trailing.variant)
        is RowTrailing.Chevron ->
            PantopusIconImage(
                icon = PantopusIcon.ChevronRight,
                contentDescription = null,
                size = 18.dp,
                tint = PantopusColors.appTextSecondary,
            )
        is RowTrailing.Kebab -> {
            if (onSecondary != null) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clickable(onClick = onSecondary)
                            .semantics { contentDescription = "More actions for $rowTitle" },
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.MoreHorizontal,
                        contentDescription = null,
                        size = 20.dp,
                        tint = PantopusColors.appTextSecondary,
                    )
                }
            }
        }
        is RowTrailing.AmountWithChip ->
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = trailing.amount,
                    style = PantopusTextStyle.body,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appText,
                )
                StatusChip(
                    text = trailing.chipText,
                    variant = trailing.chipVariant,
                    icon = trailing.chipIcon,
                )
            }
        is RowTrailing.CircularAction ->
            Box(
                modifier =
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(trailing.background)
                        .clickable(onClick = trailing.onClick)
                        .semantics { contentDescription = trailing.accessibilityLabel },
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = trailing.icon,
                    contentDescription = null,
                    size = 17.dp,
                    tint = trailing.foreground,
                )
            }
        is RowTrailing.VerticalActions ->
            Column(
                modifier = Modifier.width(90.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                CompactButton(
                    title = trailing.primary.label,
                    variant = trailing.primary.variant,
                    size = CompactButtonSize.InlineAction,
                    onClick = trailing.primary.onClick,
                )
                CompactButton(
                    title = trailing.secondary.label,
                    variant = trailing.secondary.variant,
                    size = CompactButtonSize.InlineAction,
                    onClick = trailing.secondary.onClick,
                )
            }
        is RowTrailing.PriceStack ->
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = trailing.amount,
                    style = PantopusTextStyle.body,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appText,
                )
                if (trailing.sublabel != null) {
                    Text(
                        text = trailing.sublabel,
                        style = PantopusTextStyle.caption,
                        color = PantopusColors.appTextMuted,
                    )
                }
            }
    }
}

// ─── Chip row ──────────────────────────────────────────────────

@Composable
private fun ChipRowView(
    chips: List<RowChip>,
    timeMeta: String?,
    metaTail: String?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        modifier = Modifier.fillMaxWidth(),
    ) {
        chips.forEach { chip -> ChipPill(chip) }
        if (metaTail != null) {
            Text(
                text = metaTail,
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        if (timeMeta != null) {
            Text(
                text = timeMeta,
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextMuted,
            )
        }
    }
}

@Composable
private fun ChipPill(chip: RowChip) {
    val background: Color
    val foreground: Color
    when (val tint = chip.tint) {
        is RowChip.Tint.Status -> {
            background = tint.variant.background
            foreground = tint.variant.foreground
        }
        is RowChip.Tint.Custom -> {
            background = tint.background
            foreground = tint.foreground
        }
    }
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(background)
                .padding(horizontal = Spacing.s2, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        if (chip.icon != null) {
            PantopusIconImage(
                icon = chip.icon,
                contentDescription = null,
                size = 11.dp,
                tint = foreground,
            )
        }
        Text(text = chip.text, style = PantopusTextStyle.caption, color = foreground)
    }
}

// ─── Note + footer + leading badge ─────────────────────────────

@Composable
private fun NoteBlock(text: String) {
    Row(
        modifier =
            Modifier
                .padding(start = 52.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.sm))
                .background(PantopusColors.appSurfaceSunken),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(2.dp)
                    .heightIn(min = 16.dp)
                    .background(PantopusColors.appBorder),
        )
        Text(
            text = "“$text”",
            style = PantopusTextStyle.caption.copy(fontStyle = FontStyle.Italic),
            color = PantopusColors.appTextStrong,
            modifier = Modifier.padding(Spacing.s2),
        )
    }
}

@Composable
private fun FooterStack(footer: RowFooter) {
    Column {
        HorizontalDivider(color = PantopusColors.appBorder)
        Spacer(Modifier.height(Spacing.s2))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            footer.actions.forEach { action ->
                CompactButton(
                    title = action.title,
                    icon = action.icon,
                    variant = action.variant,
                    size = CompactButtonSize.Footer,
                    onClick = action.onClick,
                    modifier = Modifier.weight(action.flex.toFloat()),
                )
            }
        }
    }
}

@Composable
private fun LeadingBadge() {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.warning)
                .padding(horizontal = Spacing.s2, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 9.dp,
            tint = PantopusColors.appTextInverse,
        )
        Text(
            text = "LEADING",
            style = PantopusTextStyle.caption,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appTextInverse,
        )
    }
}

// ─── Top-bar action ────────────────────────────────────────────

@Composable
private fun TopBarActionButton(action: TopBarAction) {
    val tint =
        if (action.isEnabled) PantopusColors.primary600 else PantopusColors.appTextMuted
    val iconTint =
        if (action.isEnabled) PantopusColors.appText else PantopusColors.appTextMuted
    if (action.label != null) {
        Box(
            modifier =
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(Radii.sm))
                    .clickable(enabled = action.isEnabled, onClick = action.onClick)
                    .padding(horizontal = Spacing.s2, vertical = Spacing.s1)
                    .semantics { contentDescription = action.contentDescription }
                    .testTag("listOfRowsTopBarAction"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = action.label,
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.SemiBold,
                color = tint,
            )
        }
    } else {
        IconButton(
            onClick = action.onClick,
            enabled = action.isEnabled,
            modifier = Modifier.testTag("listOfRowsTopBarAction"),
        ) {
            PantopusIconImage(
                icon = action.icon,
                contentDescription = action.contentDescription,
                tint = iconTint,
            )
        }
    }
}

// ─── FAB ───────────────────────────────────────────────────────

@Composable
private fun FabComposable(fab: FabAction) {
    when (val variant = fab.variant) {
        is FabVariant.CanonicalCreate ->
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.primary600)
                        .clickable(onClick = fab.onClick)
                        .semantics { contentDescription = fab.contentDescription },
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = fab.icon,
                    contentDescription = null,
                    size = 24.dp,
                    tint = PantopusColors.appTextInverse,
                )
            }
        is FabVariant.SecondaryCreate ->
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.primary600)
                        .clickable(onClick = fab.onClick)
                        .semantics { contentDescription = fab.contentDescription },
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = fab.icon,
                    contentDescription = null,
                    size = 22.dp,
                    tint = PantopusColors.appTextInverse,
                )
            }
        is FabVariant.ExtendedNav ->
            Row(
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.primary600)
                        .clickable(onClick = fab.onClick)
                        .padding(horizontal = Spacing.s5, vertical = Spacing.s2)
                        .semantics { contentDescription = fab.contentDescription },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                PantopusIconImage(
                    icon = fab.icon,
                    contentDescription = null,
                    size = 18.dp,
                    tint = PantopusColors.appTextInverse,
                )
                Text(
                    text = variant.label,
                    style = PantopusTextStyle.body,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appTextInverse,
                )
            }
    }
}

// ─── Compact button ────────────────────────────────────────────

/** Size variant for [CompactButton]. */
enum class CompactButtonSize {
    /** 34dp height — in-card row footer button. */
    Footer,

    /** 30dp primary / 28dp ghost — inline row-trailing pill. */
    InlineAction,
}

/**
 * Compact in-row action button. See `CompactButton.swift` for parity.
 */
@Composable
fun CompactButton(
    title: String,
    variant: CompactButtonVariant,
    size: CompactButtonSize,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: PantopusIcon? = null,
) {
    val height: Int =
        when {
            size == CompactButtonSize.Footer -> 34
            variant == CompactButtonVariant.Ghost -> 28
            else -> 30
        }
    val background =
        when (variant) {
            CompactButtonVariant.Primary -> PantopusColors.primary600
            CompactButtonVariant.Ghost -> PantopusColors.appSurface
            CompactButtonVariant.Destructive -> PantopusColors.appSurface
        }
    val foreground =
        when (variant) {
            CompactButtonVariant.Primary -> PantopusColors.appTextInverse
            CompactButtonVariant.Ghost -> PantopusColors.appTextStrong
            CompactButtonVariant.Destructive -> PantopusColors.error
        }
    val borderStroke =
        if (variant == CompactButtonVariant.Primary) null else BorderStroke(1.dp, PantopusColors.appBorder)

    val rowMod =
        modifier
            .height(height.dp)
            .clip(RoundedCornerShape(Radii.md))
            .background(background)
            .let { m -> borderStroke?.let { m.border(it, RoundedCornerShape(Radii.md)) } ?: m }
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.s3)
            .semantics { contentDescription = title }

    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            PantopusIconImage(
                icon = icon,
                contentDescription = null,
                size = 13.dp,
                tint = foreground,
            )
        }
        Text(text = title, style = PantopusTextStyle.caption, color = foreground)
    }
}
