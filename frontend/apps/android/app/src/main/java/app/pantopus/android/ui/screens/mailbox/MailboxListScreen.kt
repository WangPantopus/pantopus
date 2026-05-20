@file:Suppress("MagicNumber")

package app.pantopus.android.ui.screens.mailbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen
import app.pantopus.android.ui.screens.shared.list_of_rows.TopBarAction
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * `GET /api/mailbox` wrapped in the List-of-Rows archetype with
 * All / Unread / Starred tabs, pagination, and pull-to-refresh.
 *
 * `onOpenSearch` is a nav-host callback that routes to the Mailbox Search
 * surface (P4.2).
 */
@Composable
fun MailboxListScreen(
    onOpenMail: (String) -> Unit,
    onOpenSearch: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: MailboxListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.configureNavigation(onOpenMail = onOpenMail, onOpenSearch = onOpenSearch)
        viewModel.load()
        Analytics.track(AnalyticsEvent.ScreenMailboxListViewed)
    }

    ListOfRowsScreen(
        title = "Mailbox",
        state = state,
        onRefresh = { viewModel.refresh() },
        onEndReached = { viewModel.loadMoreIfNeeded() },
        tabs = viewModel.tabs,
        selectedTab = selectedTab,
        onSelectTab = viewModel::selectTab,
        topBarAction =
            TopBarAction(
                icon = PantopusIcon.Search,
                contentDescription = "Search mail",
                onClick = viewModel::onSearchTapped,
            ),
        onBack = onBack,
    )
}
