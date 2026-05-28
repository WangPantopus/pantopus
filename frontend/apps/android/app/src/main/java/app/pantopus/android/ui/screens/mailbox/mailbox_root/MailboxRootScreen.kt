@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.mailbox.mailbox_root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.screens.shared.list_of_rows.FabAction
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.TopBarAction
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * B.1 — Mailbox root archetype. One screen: a 4-drawer chip row
 * (Me / Home / Biz / Earn) + a 3-tab segmented bar (Incoming / Counter /
 * Vault) + the mail list for the active (drawer, tab). Replaces the
 * MailboxDrawersScreen (drawer list) + MailboxListScreen (flat list) pair.
 *
 * Built on the List-of-Rows archetype: the drawer chips and tab bar render
 * in the shell's `customHeader`; the list, loading, empty, and error
 * states all come from the shell.
 */
@Composable
fun MailboxRootScreen(
    onOpenMail: (String) -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenMailDay: () -> Unit = {},
    onBrowseGigs: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: MailboxRootViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedDrawer by viewModel.selectedDrawer.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.configureNavigation(
            onOpenMail = onOpenMail,
            onOpenSearch = onOpenSearch,
            onOpenMap = onOpenMap,
            onBrowseGigs = onBrowseGigs,
        )
        viewModel.load()
        Analytics.track(AnalyticsEvent.ScreenMailboxRootViewed)
    }

    ListOfRowsScreen(
        title = "Mailbox",
        state = state,
        onRefresh = { viewModel.refresh() },
        onEndReached = {},
        topBarAction =
            TopBarAction(
                icon = PantopusIcon.Search,
                contentDescription = "Search mail",
                onClick = onOpenSearch,
            ),
        // Scan-line FAB (JSX `MailboxScreen` FAB). Wired to the Mailbox map
        // so the physical-venue surface stays reachable now that the
        // drawers root is gone. Hidden on the empty state, mirroring the
        // design's `mode !== 'empty'` guard.
        fab =
            if (state is ListOfRowsUiState.Loaded) {
                FabAction(
                    icon = PantopusIcon.ScanLine,
                    contentDescription = "Find a mailbox",
                    onClick = onOpenMap,
                )
            } else {
                null
            },
        onBack = onBack,
        customHeader = {
            MailboxRootHeader(
                drawers = viewModel.drawers,
                selectedDrawer = selectedDrawer,
                tabs = viewModel.mailTabs,
                selectedTab = selectedTab,
                drawerBadge = viewModel::drawerBadge,
                tabBadge = viewModel::tabBadge,
                onSelectDrawer = viewModel::selectDrawer,
                onSelectTab = viewModel::selectTab,
                onOpenMailDay = onOpenMailDay,
            )
        },
    )
}
