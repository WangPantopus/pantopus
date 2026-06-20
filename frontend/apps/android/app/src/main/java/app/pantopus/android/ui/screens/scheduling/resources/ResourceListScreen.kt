@file:Suppress("PackageNaming", "MagicNumber", "LongMethod")

package app.pantopus.android.ui.screens.scheduling.resources

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.OfflineBannerHost
import app.pantopus.android.ui.components.StatusChipVariant
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingRoutes
import app.pantopus.android.ui.screens.shared.list_of_rows.FabAction
import app.pantopus.android.ui.screens.shared.list_of_rows.FabTint
import app.pantopus.android.ui.screens.shared.list_of_rows.FabVariant
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.RowChip
import app.pantopus.android.ui.screens.shared.list_of_rows.RowLeading
import app.pantopus.android.ui.screens.shared.list_of_rows.RowModel
import app.pantopus.android.ui.screens.shared.list_of_rows.RowSection
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTemplate
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTrailing
import app.pantopus.android.ui.screens.shared.list_of_rows.SectionStyle
import app.pantopus.android.ui.screens.shared.list_of_rows.TopBarAction
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

const val RESOURCE_LIST_TAG = "scheduling.resourceList"

// ─── Template seeds shown in the Empty frame ───────────────────────────────

private data class ResourceTemplate(
    val icon: PantopusIcon,
    val label: String,
)

private val RESOURCE_TEMPLATES = listOf(
    // bed-double has no PantopusIcon equivalent; DoorOpen matches ResourceKind.Room
    ResourceTemplate(PantopusIcon.DoorOpen, "Guest room"),
    ResourceTemplate(PantopusIcon.Car, "Driveway"),
    ResourceTemplate(PantopusIcon.Zap, "EV charger"),
    ResourceTemplate(PantopusIcon.Wrench, "Tools"),
    ResourceTemplate(PantopusIcon.Plus, "Other"),
)

/**
 * F9 Bookable Home Resources · List. The view-model owns the data + status
 * projection; this screen maps it onto the ListOfRows shell (Home-green
 * identity) and owns navigation to the editor / detail.
 *
 * Bespoke states (rendered with a local Scaffold, not ListOfRows):
 *  - Empty  → explainer card + TEMPLATES overline + 5 tappable template rows
 *  - Error  → cloud-off in errorBg circle, "Couldn't load resources", "Retry"
 *
 * Delegated to ListOfRows shell:
 *  - Loading → standard shimmer skeleton
 *  - Loaded  → list rows; offline amber banner + 0.55-alpha icon tints when offline
 */
@Composable
fun ResourceListScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: ResourceListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.start() }

    val openEditor = { onNavigate(SchedulingRoutes.resourceEditor("new")) }

    when (val state = uiState) {
        ResourceListUiState.Loading -> {
            // Delegate to ListOfRows shell so the standard shimmer skeleton renders.
            ListOfRowsScreen(
                title = "Resources",
                state = ListOfRowsUiState.Loading,
                onRefresh = viewModel::refresh,
                onEndReached = {},
                onBack = onBack,
                topBarAction = resourceAddAction(openEditor),
            )
        }

        ResourceListUiState.Empty -> {
            // Bespoke empty frame: same top-bar chrome + explainer card + TEMPLATES.
            ResourceScaffold(
                onBack = onBack,
                onAdd = openEditor,
                fab = FabAction(
                    icon = PantopusIcon.Plus,
                    contentDescription = "Add a resource",
                    variant = FabVariant.SecondaryCreate,
                    tint = FabTint.Home,
                    onClick = openEditor,
                ),
            ) { innerPadding ->
                ResourceEmptyBody(
                    modifier = Modifier.padding(innerPadding),
                    onTemplate = openEditor,
                )
            }
        }

        is ResourceListUiState.Error -> {
            // Bespoke error frame: cloud-off in errorBg circle, correct copy + Retry.
            ResourceScaffold(
                onBack = onBack,
                onAdd = openEditor,
                fab = null,
            ) { innerPadding ->
                ResourceErrorBody(
                    modifier = Modifier.padding(innerPadding),
                    onRetry = viewModel::refresh,
                )
            }
        }

        is ResourceListUiState.Loaded -> {
            val loaded = state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(RESOURCE_LIST_TAG),
            ) {
                ListOfRowsScreen(
                    title = "Resources",
                    state = loaded.toShellState(
                        onOpenDetail = { onNavigate(SchedulingRoutes.resourceDetail(it)) },
                        isOffline = loaded.isOffline,
                    ),
                    onRefresh = viewModel::refresh,
                    onEndReached = {},
                    onBack = onBack,
                    topBarAction = resourceAddAction(openEditor),
                    fab = if (!loaded.isOffline) {
                        FabAction(
                            icon = PantopusIcon.Plus,
                            contentDescription = "Add a resource",
                            variant = FabVariant.SecondaryCreate,
                            tint = FabTint.Home,
                            onClick = openEditor,
                        )
                    } else {
                        null
                    },
                    // Offline amber banner rendered as a fixed strip above the list.
                    customHeader = if (loaded.isOffline) {
                        { OfflineBannerHost(isOffline = true) {} }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

// ─── Shared top-bar trailing "Add" action ──────────────────────────────────

private fun resourceAddAction(onClick: () -> Unit) = TopBarAction(
    icon = PantopusIcon.Plus,
    contentDescription = "Add a resource",
    onClick = onClick,
    label = "Add",
)

// ─── Reusable Scaffold for Empty/Error frames ──────────────────────────────

/**
 * Minimal Scaffold for the bespoke Empty and Error frames. Provides the same
 * chrome structure as [ListOfRowsScreen] (CenterAlignedTopAppBar + optional FAB
 * + appBg container) without binding to a list-of-rows state machine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResourceScaffold(
    onBack: (() -> Unit)?,
    onAdd: () -> Unit,
    fab: FabAction?,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Resources",
                        style = PantopusTextStyle.h3,
                        color = PantopusColors.appText,
                    )
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
                    // Matches ListOfRowsScreen's TopBarActionButton text-label render.
                    Box(
                        modifier = Modifier
                            .clickable(onClick = onAdd)
                            .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
                    ) {
                        Text(
                            text = "Add",
                            style = PantopusTextStyle.body,
                            fontWeight = FontWeight.SemiBold,
                            color = PantopusColors.primary600,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = PantopusColors.appSurface,
                ),
            )
        },
        floatingActionButton = {
            if (fab != null) {
                // SecondaryCreate FAB — 52dp Home-green circle, mirrors FabVariant.SecondaryCreate.
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.home)
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
            }
        },
        containerColor = PantopusColors.appBg,
        content = content,
    )
}

// ─── Empty frame body ──────────────────────────────────────────────────────

/**
 * Bespoke empty body (design: resources-list-frames.jsx FrameEmpty).
 * Renders the explainer card + TEMPLATES overline + 5 tappable template rows.
 */
@Composable
private fun ResourceEmptyBody(
    modifier: Modifier = Modifier,
    onTemplate: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.s3,
            end = Spacing.s3,
            top = Spacing.s3,
            bottom = 92.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        // Explainer card (package-open icon, headline, subcopy)
        item(key = "explainer") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                    .padding(horizontal = Spacing.s4, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PantopusColors.homeBg),
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.PackageOpen,
                        contentDescription = null,
                        size = 24.dp,
                        tint = PantopusColors.home,
                    )
                }
                Spacer(Modifier.height(Spacing.s3))
                Text(
                    text = "Add what your household shares",
                    style = PantopusTextStyle.h3,
                    color = PantopusColors.appText,
                )
                Spacer(Modifier.height(Spacing.s1))
                Text(
                    text = "Anything members book — rooms, the driveway, tools. Start from a template.",
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                )
            }
        }

        // "TEMPLATES" overline
        item(key = "templates-header") {
            Text(
                text = "Templates",
                style = PantopusTextStyle.overline,
                color = PantopusColors.appTextSecondary,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = Spacing.s1),
            )
        }

        // 5 tappable template rows
        items(RESOURCE_TEMPLATES, key = { it.label }) { template ->
            val isOther = template.label == "Other"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                    .clickable(onClick = onTemplate)
                    .padding(horizontal = Spacing.s3, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isOther) PantopusColors.appSurfaceSunken else PantopusColors.homeBg,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = template.icon,
                        contentDescription = null,
                        size = 18.dp,
                        tint = if (isOther) PantopusColors.appTextSecondary else PantopusColors.home,
                    )
                }
                Text(
                    text = template.label,
                    style = PantopusTextStyle.body,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appText,
                    modifier = Modifier.weight(1f),
                )
                PantopusIconImage(
                    icon = PantopusIcon.ChevronRight,
                    contentDescription = null,
                    size = Radii.xl,
                    tint = PantopusColors.appTextMuted,
                )
            }
        }
    }
}

// ─── Error frame body ──────────────────────────────────────────────────────

/**
 * Bespoke error body (design: resources-list-frames.jsx FrameError).
 * 56dp errorBg circle + cloud-off icon, "Couldn't load resources",
 * "Check your connection and try again.", "Retry" pill with rotate-cw icon.
 */
@Composable
private fun ResourceErrorBody(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = Spacing.s6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.errorBg),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.CloudOff,
                contentDescription = null,
                size = 26.dp,
                tint = PantopusColors.error,
            )
        }
        Spacer(Modifier.height(Spacing.s3))
        Text(
            text = "Couldn't load resources",
            style = PantopusTextStyle.h3,
            color = PantopusColors.appText,
        )
        Spacer(Modifier.height(Spacing.s1))
        Text(
            text = "Check your connection and try again.",
            style = PantopusTextStyle.small,
            color = PantopusColors.appTextSecondary,
        )
        Spacer(Modifier.height(Spacing.s4))
        // Retry pill: primary blue, leading rotate-cw icon, "Retry" label.
        Row(
            modifier = Modifier
                .sizeIn(maxWidth = 160.dp)
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.primary600)
                .clickable(onClick = onRetry)
                .padding(horizontal = Spacing.s4, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.RefreshCw,
                contentDescription = null,
                size = 16.dp,
                tint = PantopusColors.appTextInverse,
            )
            Text(
                text = "Retry",
                style = PantopusTextStyle.body,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appTextInverse,
            )
        }
    }
}

// ─── Loaded → shell state projection ──────────────────────────────────────

private fun ResourceListUiState.Loaded.toShellState(
    onOpenDetail: (String) -> Unit,
    isOffline: Boolean,
): ListOfRowsUiState =
    ListOfRowsUiState.Loaded(
        sections = listOf(
            RowSection(
                id = "resources",
                style = SectionStyle.Flat,
                rows = rows.map { row ->
                    RowModel(
                        id = row.id,
                        title = row.name,
                        template = RowTemplate.StatusChip,
                        leading = RowLeading.TypeIcon(
                            icon = row.kind.icon,
                            background = if (isOffline) {
                                PantopusColors.homeBg.copy(alpha = 0.55f)
                            } else {
                                PantopusColors.homeBg
                            },
                            foreground = if (isOffline) {
                                PantopusColors.home.copy(alpha = 0.55f)
                            } else {
                                PantopusColors.home
                            },
                        ),
                        trailing = RowTrailing.Status(
                            text = row.statusLabel,
                            variant = if (row.isFree) StatusChipVariant.Success else StatusChipVariant.Neutral,
                        ),
                        chips = listOf(
                            RowChip(
                                text = row.kind.label,
                                tint = RowChip.Tint.Status(StatusChipVariant.Neutral),
                            ),
                        ),
                        onTap = { onOpenDetail(row.id) },
                    )
                },
            ),
        ),
        hasMore = false,
    )
