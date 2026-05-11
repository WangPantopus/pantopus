@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.claims

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen

/**
 * Concrete List-of-Rows screen for the user's ownership claims. Wired
 * to `GET /api/homes/my-ownership-claims`.
 *
 * @param onStartNewClaim Invoked when the empty-state CTA fires.
 * @param onBack Optional back handler.
 */
@Composable
fun MyClaimsListScreen(
    onStartNewClaim: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: MyClaimsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.configureNavigation(onStartNewClaim = onStartNewClaim)
        viewModel.load()
        Analytics.track(AnalyticsEvent.ScreenMyClaimsViewed)
    }
    ListOfRowsScreen(
        title = "My claims",
        state = state,
        onRefresh = { viewModel.refresh() },
        onEndReached = { /* not paginated */ },
        onBack = onBack,
    )
}
