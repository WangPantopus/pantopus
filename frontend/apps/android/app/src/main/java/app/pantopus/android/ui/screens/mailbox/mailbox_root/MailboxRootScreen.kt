@file:Suppress("PackageNaming", "LongParameterList")

package app.pantopus.android.ui.screens.mailbox.mailbox_root

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.screens.shared.list_of_rows.FabAction
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.TopBarAction
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage

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
    onOpenEarn: () -> Unit = {},
    onOpenVacationHold: () -> Unit = {},
    onOpenStamps: () -> Unit = {},
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
            onOpenEarn = onOpenEarn,
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
        extraTopBarAction = {
            IconButton(onClick = onOpenStamps, modifier = Modifier.testTag("mailboxRootStamps")) {
                PantopusIconImage(
                    icon = PantopusIcon.Gift,
                    contentDescription = "Stamps",
                    size = 22.dp,
                    tint = PantopusColors.appText,
                )
            }
            MailboxRootSettingsMenu(onOpenVacationHold = onOpenVacationHold, onOpenStamps = onOpenStamps)
        },
    )
}

/**
 * Overflow / settings menu on the Mailbox root top bar. Today it
 * carries one entry — Vacation hold (A14.8). Future mailbox-scoped
 * settings can land in the same menu without changing the chrome.
 */
@Composable
private fun MailboxRootSettingsMenu(
    onOpenVacationHold: () -> Unit,
    onOpenStamps: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = { expanded = true },
        modifier = Modifier.testTag("mailboxRootSettings"),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.MoreVertical,
            contentDescription = "Mailbox settings",
            size = 22.dp,
            tint = PantopusColors.appText,
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        DropdownMenuItem(
            text = { Text("Stamps", color = PantopusColors.appText) },
            onClick = {
                expanded = false
                onOpenStamps()
            },
            modifier = Modifier.testTag("mailboxRootSettings.stamps"),
        )
        DropdownMenuItem(
            text = { Text("Vacation hold", color = PantopusColors.appText) },
            onClick = {
                expanded = false
                onOpenVacationHold()
            },
            modifier = Modifier.testTag("mailboxRootSettings.vacationHold"),
        )
    }
}
