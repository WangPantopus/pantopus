package app.pantopus.android.ui.screens.homes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.screens.shared.list_of_rows.FabAction
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * `GET /api/homes/my-homes` wrapped in the List-of-Rows archetype.
 *
 * @param onOpenHome Invoked with the home id when a row is tapped.
 * @param onAddHome Invoked when the FAB or empty-state CTA fires.
 * @param onBack Optional back handler.
 */
@Composable
fun MyHomesListScreen(
    onOpenHome: (String) -> Unit,
    onAddHome: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: MyHomesListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.configureNavigation(onOpenHome = onOpenHome, onAddHome = onAddHome)
        viewModel.load()
    }
    ListOfRowsScreen(
        title = "My homes",
        state = state,
        onRefresh = { viewModel.refresh() },
        onEndReached = { /* not paginated */ },
        fab = FabAction(icon = PantopusIcon.PlusCircle, contentDescription = "Claim a home", onClick = onAddHome),
        onBack = onBack,
    )
}
