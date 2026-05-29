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
 * P7.6 / A14.7 — Paparazzi render spec for the reshaped Privacy screen
 * (defaults + stealth frames). Mirror of the iOS `PrivacySnapshotTests`
 * baseline gate.
 *
 * `@Ignore`d while baselines are pending: this container has no Android
 * SDK, so the golden PNGs can't be recorded here. Record + commit them
 * (and drop the `@Ignore`) in a follow-up via:
 *
 *   ./gradlew paparazziRecord --tests "*PrivacySnapshotTest*"
 *
 * Keeping it ignored leaves `paparazziVerify` green until the goldens
 * land — the same "baseline pending follow-up" posture as iOS.
 */
@Ignore("A14.7 baselines pending — record via ./gradlew paparazziRecord (needs Android SDK).")
class PrivacySnapshotTest {
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
    fun privacy_defaults() {
        val viewModel = PrivacySettingsViewModel().apply { load() }
        paparazzi.snapshot { Frame { PrivacyFrame(viewModel) } }
    }

    @Test
    fun privacy_stealth() {
        val viewModel =
            PrivacySettingsViewModel().apply {
                setVariant(PrivacySettingsViewModel.Variant.Stealth)
            }
        paparazzi.snapshot { Frame { PrivacyFrame(viewModel) } }
    }

    @Composable
    private fun PrivacyFrame(viewModel: PrivacySettingsViewModel) {
        GroupedListScreen(
            title = viewModel.title,
            state = viewModel.state.value,
            footerCaption = viewModel.footerCaption,
            banner = viewModel.banner.value,
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
