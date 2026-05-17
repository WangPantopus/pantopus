@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.emergency

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
import app.pantopus.android.data.api.models.homes.HomeEmergencyDto
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen

/**
 * T6.4b / P17 — Concrete Emergency info list screen wired to
 * `GET /api/homes/:id/emergencies` (route `backend/routes/home.js:5406`).
 *
 * @param onAction Invoked when an emergency row's circular action (or
 *     the row tap itself) fires — tap-to-call / view-photo / open-in-maps.
 * @param onAdd Invoked when the FAB or empty-state CTA fires.
 * @param onShare Invoked when the top-bar share action fires.
 * @param onPrintCard Invoked when the banner's "Print card" CTA fires.
 * @param onBack Optional back handler.
 */
@Composable
fun EmergencyInfoScreen(
    onAction: (HomeEmergencyDto) -> Unit,
    onAdd: () -> Unit,
    onShare: () -> Unit,
    onPrintCard: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: EmergencyInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val chipStrip by viewModel.chipStrip.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.configureNavigation(
            onAction = onAction,
            onAdd = onAdd,
            onShare = onShare,
            onPrintCard = onPrintCard,
        )
        viewModel.load()
        Analytics.track(AnalyticsEvent.ScreenEmergencyInfoViewed)
    }

    Box(modifier = Modifier.fillMaxSize().testTag("emergencyInfoList")) {
        ListOfRowsScreen(
            title = "Emergency info",
            state = state,
            onRefresh = { viewModel.refresh() },
            onEndReached = { },
            chipStrip = chipStrip.copy(selectedId = selectedFilter),
            topBarAction = viewModel.topBarAction,
            fab = viewModel.fab(),
            onBack = onBack,
            banner = banner,
        )
    }
}
