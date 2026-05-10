package app.pantopus.android.ui.screens.mailbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen

/**
 * `GET /api/mailbox/v2/drawers` wrapped in the List-of-Rows archetype.
 */
@Composable
fun MailboxDrawersScreen(
    onOpenDrawer: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: MailboxDrawersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.configureNavigation(onOpenDrawer = onOpenDrawer)
        viewModel.load()
        Analytics.track(AnalyticsEvent.ScreenMailboxDrawersViewed)
    }
    ListOfRowsScreen(
        title = "Mailbox",
        state = state,
        onRefresh = { viewModel.refresh() },
        onEndReached = {},
        onBack = onBack,
    )
}
