@file:Suppress("PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.homes.members

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen

/** Test tag on the Members list root container. */
const val MEMBERS_LIST_TAG = "membersList"

/**
 * T6.3a / P9 Members per-home list. Thin wrapper around
 * [ListOfRowsScreen]; the VM supplies the rows + chrome and emits a
 * [MembersListEvent] when a row action needs the screen to present a
 * sheet or confirm dialog.
 */
@Composable
fun MembersListScreen(
    onBack: () -> Unit,
    onAddGuest: () -> Unit = {},
    viewModel: MembersListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val pendingEvent by viewModel.pendingEvent.collectAsStateWithLifecycle()

    var inviting by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.load()
        Analytics.track(AnalyticsEvent.ScreenMembersListViewed)
    }

    LaunchedEffect(pendingEvent) {
        when (val event = pendingEvent) {
            null -> Unit
            MembersListEvent.OpenInvite -> {
                inviting = true
                viewModel.acknowledgeEvent()
            }
            MembersListEvent.OpenAddGuest -> {
                onAddGuest()
                viewModel.acknowledgeEvent()
            }
            is MembersListEvent.ConfirmRemove -> {
                removeTarget = event.userId to event.name
                viewModel.acknowledgeEvent()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().testTag(MEMBERS_LIST_TAG)) {
        ListOfRowsScreen(
            title = "Members",
            state = state,
            onRefresh = { viewModel.refresh() },
            onEndReached = { viewModel.loadMoreIfNeeded() },
            tabs = tabs,
            selectedTab = selectedTab,
            onSelectTab = viewModel::selectTab,
            fab = viewModel.fab,
            onBack = onBack,
        )
    }

    if (inviting) {
        InviteMemberWizardSheet(
            homeId = viewModel.homeId,
            onClose = { invitation ->
                inviting = false
                invitation?.let(viewModel::handleInvited)
            },
        )
    }

    removeTarget?.let { (userId, name) ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove member?") },
            text = { Text("$name will lose access to this home. They can be re-invited later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.remove(userId)
                        removeTarget = null
                    },
                    modifier = Modifier.testTag("membersList_removeConfirm"),
                ) { Text("Remove $name") }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("Cancel") }
            },
        )
    }
}
