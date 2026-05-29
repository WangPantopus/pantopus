@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListCallbacks
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListScreen
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListUiState
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Rule
import org.junit.Test

/**
 * P5.1 / A14.1 — Paparazzi baselines for the per-home Settings index.
 * Two frames cover the audit:
 *   - populated  established home, address verified, Leave home
 *                destructive.
 *   - pending    newly claimed, amber Verifying chip, Cancel claim
 *                destructive.
 */
class HomeSettingsSnapshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 2600,
                    softButtons = false,
                ),
        )

    @Test
    fun home_settings_populated() {
        paparazzi.snapshot {
            Frame {
                renderFrame(HomeSettingsSampleData.Frame.Populated, homeId = "home-1")
            }
        }
    }

    @Test
    fun home_settings_pending() {
        paparazzi.snapshot {
            Frame {
                renderFrame(HomeSettingsSampleData.Frame.Pending, homeId = "pending-1")
            }
        }
    }

    @Composable
    private fun renderFrame(
        frame: HomeSettingsSampleData.Frame,
        homeId: String,
    ) {
        // Build a fully-resolved Loaded state from the deterministic
        // sample data so the snapshot doesn't depend on coroutine
        // dispatching.
        val vm =
            HomeSettingsViewModel(
                savedStateHandle = SavedStateHandle(mapOf(HOME_SETTINGS_HOME_ID_KEY to homeId)),
            )
        vm.load()
        val state = vm.state.value as GroupedListUiState.Loaded
        GroupedListScreen(
            title = vm.title,
            state = state,
            footerCaption = vm.footerCaption,
            callbacks = GroupedListCallbacks(onBack = {}),
            header = { HomeSettingsIdentityCard(identity = HomeSettingsSampleData.identity(frame)) },
        )
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        PantopusTheme {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(PantopusColors.appBg),
            ) { content() }
        }
    }
}
