@file:Suppress("MagicNumber", "UnusedPrivateMember", "PackageNaming", "LongMethod", "LongParameterList")

package app.pantopus.android.ui.screens.shared.list_of_rows

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.components.AvatarWithIdentityRing
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.components.StatusChip
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/** Test tag on the list-of-rows root container — used by Compose tests. */
const val LIST_OF_ROWS_TAG = "listOfRows"

/**
 * Reusable List-of-Rows screen.
 *
 * @param title Bar title.
 * @param state Observed UI state.
 * @param onRefresh Called on pull-to-refresh. Fire-and-forget — the VM
 *     launches any coroutine internally and mutates [state].
 * @param onEndReached Fires once when the list nears the bottom.
 * @param tabs Optional tab entries. Empty list = no tab strip.
 * @param selectedTab Currently-selected tab id.
 * @param onSelectTab Tab-change handler.
 * @param topBarAction Optional trailing top-bar action.
 * @param fab Optional floating action button.
 * @param onBack Optional back handler; renders a chevron when set.
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
                title = { Text(title, style = PantopusTextStyle.h3, color = PantopusColors.appText) },
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
                        IconButton(onClick = topBarAction.onClick) {
                            PantopusIconImage(
                                icon = topBarAction.icon,
                                contentDescription = topBarAction.contentDescription,
                                tint = PantopusColors.appText,
                            )
                        }
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
            }
        },
        containerColor = PantopusColors.appBg,
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (tabs.isNotEmpty()) {
                TabStrip(tabs = tabs, selectedId = selectedTab, onSelect = onSelectTab)
                HorizontalDivider(color = PantopusColors.appBorderSubtle)
            }
            Box(modifier = Modifier.fillMaxSize().pullRefresh(pullState)) {
                when (state) {
                    ListOfRowsUiState.Loading -> LoadingRows()
                    is ListOfRowsUiState.Loaded -> LoadedList(state, onEndReached)
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
    onEndReached: () -> Unit,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
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
        state.sections.forEach { section ->
            if (section.header != null) {
                item(key = "header_${section.id}") {
                    Text(
                        text = section.header.uppercase(),
                        style = PantopusTextStyle.overline,
                        color = PantopusColors.appTextSecondary,
                        modifier = Modifier.padding(vertical = Spacing.s2),
                    )
                }
            }
            items(section.rows, key = { it.id }) { row -> RowView(row) }
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
private fun RowView(row: RowModel) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurface)
                .clickable(onClick = row.onTap)
                .padding(Spacing.s3)
                .heightIn(min = 60.dp)
                .semantics { contentDescription = row.rowAccessibilityLabel() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        when (val leading = row.leading) {
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
            RowLeading.None -> Unit
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = PantopusTextStyle.body,
                color = PantopusColors.appText,
                maxLines = 2,
            )
            if (row.subtitle != null) {
                Text(
                    text = row.subtitle,
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                    maxLines = 2,
                )
            }
        }
        when (val trailing = row.trailing) {
            RowTrailing.Chevron ->
                PantopusIconImage(
                    icon = PantopusIcon.ChevronRight,
                    contentDescription = null,
                    size = 18.dp,
                    tint = PantopusColors.appTextSecondary,
                )
            RowTrailing.Kebab -> {
                val handler = row.onSecondary
                if (handler != null) {
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clickable(onClick = handler)
                                .semantics { contentDescription = "More actions for ${row.title}" },
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
            is RowTrailing.Status -> StatusChip(text = trailing.text, variant = trailing.variant)
            RowTrailing.None -> Unit
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

private fun RowModel.rowAccessibilityLabel(): String {
    val parts =
        buildList {
            add(title)
            subtitle?.let(::add)
            (trailing as? RowTrailing.Status)?.let { add(it.text) }
        }
    return parts.joinToString(separator = ", ")
}
