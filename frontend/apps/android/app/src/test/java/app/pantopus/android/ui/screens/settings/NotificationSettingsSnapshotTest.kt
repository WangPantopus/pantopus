@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListScreen
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * P7.5 / A14.5 — Paparazzi render spec for the reshaped notification
 * matrix (populated + paused frames). Mirror of the iOS
 * `NotificationSettingsSnapshotTests` baseline gate.
 *
 * `@Ignore`d while baselines are pending: this container has no Android
 * SDK, so the golden PNGs can't be recorded here. Record + commit them
 * (and drop the `@Ignore`) in a follow-up via:
 *
 *   ./gradlew paparazziRecord --tests "*NotificationSettingsSnapshotTest*"
 *
 * Keeping it ignored means `paparazziVerify` stays green until the
 * goldens land — the same "baseline pending follow-up" posture as iOS.
 */
@Ignore("A14.5 baselines pending — record via ./gradlew paparazziRecord (needs Android SDK).")
class NotificationSettingsSnapshotTest {
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
    fun notifications_populated() {
        val viewModel = NotificationSettingsViewModel().apply { load() }
        paparazzi.snapshot { Frame { NotificationFrame(viewModel) } }
    }

    @Test
    fun notifications_paused() {
        val viewModel =
            NotificationSettingsViewModel().apply {
                setVariant(NotificationSettingsViewModel.Variant.Paused)
            }
        paparazzi.snapshot { Frame { NotificationFrame(viewModel) } }
    }

    @Composable
    private fun NotificationFrame(viewModel: NotificationSettingsViewModel) {
        GroupedListScreen(
            title = viewModel.title,
            state = viewModel.state.value,
            footerCaption = viewModel.footerCaption,
            banner = viewModel.banner.value,
            contentDimmed = viewModel.dimmed.value,
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
