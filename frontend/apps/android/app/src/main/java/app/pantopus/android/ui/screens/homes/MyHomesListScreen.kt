package app.pantopus.android.ui.screens.homes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.screens.shared.list_of_rows.FabAction
import app.pantopus.android.ui.screens.shared.list_of_rows.FabTint
import app.pantopus.android.ui.screens.shared.list_of_rows.FabVariant
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * `GET /api/homes/my-homes` wrapped in the List-of-Rows archetype
 * (T6.3f / P14 refresh — `.SecondaryCreate` 52dp FAB tinted
 * [FabTint.Home], plus the home-tinted intro banner).
 */
@Composable
fun MyHomesListScreen(
    onOpenHome: (String) -> Unit,
    onAddHome: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: MyHomesListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.configureNavigation(onOpenHome = onOpenHome, onAddHome = onAddHome)
        viewModel.load()
        Analytics.track(AnalyticsEvent.ScreenMyHomesViewed)
    }
    ListOfRowsScreen(
        title = "My homes",
        state = state,
        onRefresh = { viewModel.refresh() },
        onEndReached = { /* not paginated */ },
        fab =
            FabAction(
                icon = PantopusIcon.PlusCircle,
                contentDescription = "Claim a home",
                variant = FabVariant.SecondaryCreate,
                tint = FabTint.Home,
                onClick = onAddHome,
            ),
        onBack = onBack,
        banner = banner,
    )
}
