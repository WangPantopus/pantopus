@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.offers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.api.models.offers.BidDto
import app.pantopus.android.ui.screens.shared.activity_filter_sheet.ActivityFilterSheet
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen

/** Test tag on the offers root container. */
const val OFFERS_TAG = "offers"

/**
 * T5.2.4 — Cross-listing Offers. Thin wrapper around [ListOfRowsScreen].
 * Two tabs (Received / Sent), no FAB, filter icon in the top-bar
 * trailing slot. Row taps surface a [BidDto] so the host nav graph can
 * push the gig (offer) detail.
 */
@Composable
fun OffersScreen(
    onBack: () -> Unit,
    onOpenOfferDetail: (BidDto) -> Unit,
    onBrowseListings: () -> Unit = {},
    onPostTask: () -> Unit = {},
    viewModel: OffersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val topBarAction by viewModel.topBarAction.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val showFilterSheet by viewModel.showFilterSheet.collectAsStateWithLifecycle()
    val activityFilter by viewModel.activityFilter.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.bindCallbacks(
            onOpenOfferDetail = onOpenOfferDetail,
            onBrowseListings = onBrowseListings,
            onPostTask = onPostTask,
        )
        viewModel.load()
    }

    Box(modifier = Modifier.fillMaxSize().testTag(OFFERS_TAG)) {
        ListOfRowsScreen(
            title = "Offers",
            state = state,
            onRefresh = { viewModel.refresh() },
            onEndReached = { viewModel.loadMoreIfNeeded() },
            tabs = tabs,
            selectedTab = selectedTab,
            onSelectTab = { viewModel.selectTab(it) },
            topBarAction = topBarAction,
            onBack = onBack,
        )
    }

    if (showFilterSheet) {
        ActivityFilterSheet(
            statusTitle = viewModel.statusFilterTitle,
            statusOptions = viewModel.statusFilterOptions,
            sortOptions = viewModel.sortFilterOptions,
            filter = activityFilter,
            onApply = { viewModel.applyFilter(it) },
            onDismiss = { viewModel.dismissFilterSheet() },
        )
    }
}
