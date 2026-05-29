@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListCallbacks
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListScreen

/**
 * T3.1 Settings index. Thin wrapper around [GroupedListScreen] —
 * the [SettingsIndexViewModel] projects the auth state into chevron
 * rows + status chips and routes taps via `onNavigate`.
 */
@Composable
fun SettingsIndexScreen(
    onClose: () -> Unit = {},
    onNavigate: (SettingsRoute) -> Unit = {},
    viewModel: SettingsIndexViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val footer by viewModel.footerCaption.collectAsStateWithLifecycle()
    val navigation by viewModel.navigation.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(navigation) {
        navigation?.let {
            viewModel.consumeNavigation()
            onNavigate(it)
        }
    }

    GroupedListScreen(
        title = viewModel.title,
        state = state,
        footerCaption = footer,
        callbacks =
            GroupedListCallbacks(
                onBack = onClose,
                onTapRow = viewModel::onRow,
                onRetry = viewModel::load,
            ),
    )
}

/** T3.1 Notification preferences (toggles). */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val dimmed by viewModel.dimmed.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    GroupedListScreen(
        title = viewModel.title,
        state = state,
        footerCaption = viewModel.footerCaption,
        banner = banner,
        contentDimmed = dimmed,
        callbacks =
            GroupedListCallbacks(
                onBack = onBack,
                onToggleRow = viewModel::onToggle,
                onToggleChannel = viewModel::onToggleChannel,
                onTapBanner = viewModel::onTapBanner,
                onRetry = viewModel::load,
            ),
    )
}

/** T3.1 Privacy preferences (radio + slider + toggles). */
@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit = {},
    viewModel: PrivacySettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    GroupedListScreen(
        title = viewModel.title,
        state = state,
        callbacks =
            GroupedListCallbacks(
                onBack = onBack,
                onToggleRow = viewModel::onToggle,
                onSelectRadio = viewModel::onRadio,
                onSetSlider = viewModel::onSlider,
                onRetry = viewModel::load,
            ),
    )
}
