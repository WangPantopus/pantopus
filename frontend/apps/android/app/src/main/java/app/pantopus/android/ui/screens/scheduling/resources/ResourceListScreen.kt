@file:Suppress("PackageNaming", "MagicNumber", "LongMethod")

package app.pantopus.android.ui.screens.scheduling.resources

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

const val RESOURCE_LIST_TAG = "scheduling.resourceList"

/**
 * F9 Bookable Home Resources · List. The view-model owns the data + status
 * projection; this screen maps it onto the ListOfRows shell (Home-green
 * identity) and owns navigation to the editor / detail.
 */
@Composable
fun ResourceListScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: ResourceListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.start() }

    val openEditor = { onNavigate(SchedulingRoutes.resourceEditor("new")) }

    Box(modifier = Modifier.fillMaxSize().testTag(RESOURCE_LIST_TAG)) {
        ListOfRowsScreen(
            title = "Resources",
            state =
                state.toShellState(
                    onOpenDetail = { onNavigate(SchedulingRoutes.resourceDetail(it)) },
                    onCreate = openEditor,
                ),
            onRefresh = viewModel::refresh,
            onEndReached = {},
            onBack = onBack,
            topBarAction =
                TopBarAction(
                    icon = PantopusIcon.Plus,
                    contentDescription = "Add a resource",
                    onClick = openEditor,
                    label = "Add",
                ),
            fab =
                FabAction(
                    icon = PantopusIcon.Plus,
                    contentDescription = "Add a resource",
                    variant = FabVariant.SecondaryCreate,
                    tint = FabTint.Home,
                    onClick = openEditor,
                ),
        )
    }
}

private fun ResourceListUiState.toShellState(
    onOpenDetail: (String) -> Unit,
    onCreate: () -> Unit,
): ListOfRowsUiState =
    when (this) {
        ResourceListUiState.Loading -> ListOfRowsUiState.Loading
        ResourceListUiState.Empty ->
            ListOfRowsUiState.Empty(
                icon = PantopusIcon.Package,
                headline = "Add what your household shares",
                subcopy = "Anything members book — rooms, the driveway, tools. Add your first resource to get started.",
                ctaTitle = "Add a resource",
                onCta = onCreate,
                tint = PantopusColors.homeBg,
                accent = PantopusColors.home,
            )
        is ResourceListUiState.Error -> ListOfRowsUiState.Error(message)
        is ResourceListUiState.Loaded ->
            ListOfRowsUiState.Loaded(
                sections =
                    listOf(
                        RowSection(
                            id = "resources",
                            style = SectionStyle.Flat,
                            rows =
                                rows.map { row ->
                                    RowModel(
                                        id = row.id,
                                        title = row.name,
                                        template = RowTemplate.StatusChip,
                                        leading =
                                            RowLeading.TypeIcon(
                                                icon = row.kind.icon,
                                                background = PantopusColors.homeBg,
                                                foreground = PantopusColors.home,
                                            ),
                                        trailing =
                                            RowTrailing.Status(
                                                text = row.statusLabel,
                                                variant =
                                                    if (row.isFree) StatusChipVariant.Success else StatusChipVariant.Neutral,
                                            ),
                                        chips =
                                            listOf(
                                                RowChip(
                                                    text = row.kind.label,
                                                    tint =
                                                        RowChip.Tint.Status(
                                                            StatusChipVariant.Neutral,
                                                        ),
                                                ),
                                            ),
                                        onTap = { onOpenDetail(row.id) },
                                    )
                                },
                        ),
                    ),
                hasMore = false,
            )
    }
