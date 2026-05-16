package app.pantopus.android.ui.screens.connections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen

/** Test tag on the Connections screen root container. */
const val CONNECTIONS_TAG = "connections"

/**
 * T5.2.3 Connections. Thin wrapper around [ListOfRowsScreen] — three
 * tabs (All / Neighbors / Pending), search bar, per-row message-CTA on
 * accepted rows, Accept / Ignore on pending rows. The VM owns the data
 * and the optimistic accept/reject flow.
 */
@Composable
fun ConnectionsScreen(
    onBack: () -> Unit,
    onOpenChat: (ConnectionsChatTarget) -> Unit,
    onFindPeople: () -> Unit,
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val topBarAction by viewModel.topBarAction.collectAsStateWithLifecycle()
    val fab by viewModel.fab.collectAsStateWithLifecycle()
    val searchBar by viewModel.searchBar.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onMessage = onOpenChat
        viewModel.onFindPeople = onFindPeople
        viewModel.load()
    }

    Box(modifier = Modifier.fillMaxSize().testTag(CONNECTIONS_TAG)) {
        ListOfRowsScreen(
            title = "Connections",
            state = state,
            onRefresh = { viewModel.refresh() },
            onEndReached = {},
            tabs = tabs,
            selectedTab = selectedTab,
            onSelectTab = { viewModel.selectTab(it) },
            topBarAction = topBarAction,
            fab = fab,
            onBack = onBack,
            searchBar = searchBar,
        )
    }
}
