package app.pantopus.android.ui.screens.businesses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.screens.shared.list_of_rows.FabAction
import app.pantopus.android.ui.screens.shared.list_of_rows.FabTint
import app.pantopus.android.ui.screens.shared.list_of_rows.FabVariant
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * `GET /api/businesses/my-businesses` wrapped in the List-of-Rows
 * archetype (T6.3f / P14). Avatar-first rows tinted business-violet,
 * intro banner, business-tinted secondary-create FAB.
 */
@Composable
fun MyBusinessesScreen(
    onOpenBusiness: (String) -> Unit,
    onRegister: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: MyBusinessesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.configureNavigation(onOpenBusiness = onOpenBusiness, onRegister = onRegister)
        viewModel.load()
        Analytics.track(AnalyticsEvent.ScreenMyBusinessesViewed)
    }
    ListOfRowsScreen(
        title = "My businesses",
        state = state,
        onRefresh = { viewModel.refresh() },
        onEndReached = { /* not paginated */ },
        fab =
            FabAction(
                icon = PantopusIcon.Building2,
                contentDescription = "Register a business",
                variant = FabVariant.SecondaryCreate,
                tint = FabTint.Business,
                onClick = onRegister,
            ),
        onBack = onBack,
        banner = banner,
    )
}
