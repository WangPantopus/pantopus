@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.calendar

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
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen

/**
 * T6.4c (P18) — concrete Home calendar screen. Reuses the shared
 * `ListOfRowsScreen` shell and threads the feature-local
 * [MonthStripHeader] through the additive `customHeader` slot.
 *
 * @param onAddEvent Invoked when the FAB or empty-state CTA fires.
 * @param onOpenEvent Invoked when an event row is tapped.
 * @param onBack Pops the host nav stack.
 */
@Composable
fun HomeCalendarScreen(
    onAddEvent: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: HomeCalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val monthStrip by viewModel.monthStrip.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.configureNavigation(onAddEvent = onAddEvent, onOpenEvent = onOpenEvent)
        viewModel.load()
        Analytics.track(AnalyticsEvent.ScreenHomeCalendarViewed)
    }

    Box(modifier = Modifier.fillMaxSize().testTag("homeCalendar")) {
        ListOfRowsScreen(
            title = "Home calendar",
            state = state,
            onRefresh = { viewModel.refresh() },
            onEndReached = {},
            fab = viewModel.fab(),
            onBack = onBack,
            banner = banner,
            customHeader = {
                monthStrip?.let { strip ->
                    MonthStripHeader(
                        state = strip,
                        onSelectDay = viewModel::selectDay,
                        onPrevMonth = { viewModel.shiftWeek(HomeCalendarViewModel.WeekShift.Previous) },
                        onNextMonth = { viewModel.shiftWeek(HomeCalendarViewModel.WeekShift.Next) },
                    )
                }
            },
        )
    }
}
