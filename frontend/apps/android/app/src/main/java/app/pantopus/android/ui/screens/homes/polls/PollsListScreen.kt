@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.polls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen

/**
 * Concrete Polls list screen wired to
 * `GET /api/homes/:id/polls` — `backend/routes/home.js:6984`.
 *
 * @param onOpenPoll Invoked when a poll row is tapped.
 * @param onStartPoll Invoked when the FAB or empty-state CTA fires.
 * @param onBack Optional back handler.
 */
@Composable
fun PollsListScreen(
    onOpenPoll: (String) -> Unit,
    onStartPoll: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: PollsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.configureNavigation(onOpenPoll = onOpenPoll, onStartPoll = onStartPoll)
        viewModel.load()
        Analytics.track(AnalyticsEvent.ScreenPollsViewed)
    }

    Box(modifier = Modifier.fillMaxSize().testTag("pollsList")) {
        ListOfRowsScreen(
            title = "Polls",
            state = state,
            onRefresh = { viewModel.refresh() },
            onEndReached = { },
            tabs = tabs,
            selectedTab = selectedTab,
            onSelectTab = viewModel::selectTab,
            topBarAction = viewModel.topBarAction,
            fab = viewModel.fab(),
            onBack = onBack,
            banner = banner,
        )
    }
}
