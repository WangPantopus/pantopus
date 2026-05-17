@file:Suppress("PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.homes.owners

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

/** Test tag on the Owners list root container. */
const val OWNERS_LIST_TAG = "ownersList"

/**
 * P15 / T6.3g Owners list. Thin wrapper around [ListOfRowsScreen]; the
 * VM supplies the rows + chrome and emits an [OwnersListEvent] when a
 * row action needs the screen to present a confirm dialog or route to
 * the invite flow.
 *
 * The invite affordance navigates to the existing InviteOwner route
 * (mounted in `RootTabScreen`) via [onOpenInvite]; on return the user
 * can pull-to-refresh to see the new pending row (matches the Bills /
 * Add Bill wizard pattern).
 *
 * @param onOpenInvite Invoked with the home id when the FAB or empty
 *     CTA fires; the host routes to the existing InviteOwner form.
 * @param onBack Pop the back stack.
 */
@Composable
fun OwnersListScreen(
    onOpenInvite: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: OwnersListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingEvent by viewModel.pendingEvent.collectAsStateWithLifecycle()

    var removeTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.load()
        Analytics.track(AnalyticsEvent.ScreenOwnersListViewed)
    }

    LaunchedEffect(pendingEvent) {
        when (val event = pendingEvent) {
            null -> Unit
            OwnersListEvent.OpenInvite -> {
                onOpenInvite(viewModel.homeId)
                viewModel.acknowledgeEvent()
            }
            is OwnersListEvent.ConfirmRemove -> {
                removeTarget = event.ownerId to event.displayName
                viewModel.acknowledgeEvent()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().testTag(OWNERS_LIST_TAG)) {
        ListOfRowsScreen(
            title = "Owners",
            state = state,
            onRefresh = { viewModel.refresh() },
            onEndReached = { viewModel.loadMoreIfNeeded() },
            fab = viewModel.fab,
            onBack = onBack,
        )
    }

    removeTarget?.let { (ownerId, displayName) ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove owner?") },
            text = {
                Text(
                    "$displayName will lose owner privileges. If other owners " +
                        "exist, removal may need quorum approval.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeOwner(ownerId)
                        removeTarget = null
                    },
                    modifier = Modifier.testTag("ownersList_removeConfirm"),
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("Cancel") }
            },
        )
    }
}
