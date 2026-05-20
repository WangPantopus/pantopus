package app.pantopus.android.ui.screens.discoverhub

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

/** Test tag on the Discover hub screen root container. */
const val DISCOVER_HUB_TAG = "discoverHub"

/**
 * T5.4.1 Discover hub. Thin wrapper around [ListOfRowsScreen] —
 * no tabs, chip-strip filter row above the body, and four typed
 * `RowSection`s (People · Businesses · Gigs · Listings) rendered
 * as cards. The VM owns the data + the parallel fan-out fetch.
 */
@Composable
fun DiscoverHubScreen(
    onBack: () -> Unit,
    onSelect: (DiscoverHubTarget) -> Unit,
    viewModel: DiscoverHubViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chipStrip by viewModel.chipStrip.collectAsStateWithLifecycle()
    val topBarAction by viewModel.topBarAction.collectAsStateWithLifecycle()
    val showFilterSheet by viewModel.showFilterSheet.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onSelect = onSelect
        viewModel.load()
    }

    Box(modifier = Modifier.fillMaxSize().testTag(DISCOVER_HUB_TAG)) {
        ListOfRowsScreen(
            title = "Discover hub",
            state = state,
            onRefresh = { viewModel.refresh() },
            onEndReached = {},
            topBarAction = topBarAction,
            onBack = onBack,
            chipStrip = chipStrip,
        )
    }

    if (showFilterSheet) {
        DiscoveryFilterSheet(
            initialFilters = filters,
            onApply = { viewModel.applyFilters(it) },
            onDismiss = { viewModel.dismissFilters() },
        )
    }
}
