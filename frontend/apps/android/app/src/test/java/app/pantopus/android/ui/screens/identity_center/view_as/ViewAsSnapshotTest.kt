@file:Suppress("PackageNaming", "FunctionNaming")

package app.pantopus.android.ui.screens.identity_center.view_as

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.components.ViewerAudience
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Rule
import org.junit.Test

/**
 * B5.2 (A18.5) — Paparazzi baselines for the "View as" identity preview.
 * Mirrors the iOS `ViewAsSnapshotTests` render set so the cross-platform
 * snapshots line up: the loading shimmer plus the two designed endpoints
 * the audit calls out — Public (heavily redacted) and Connection (rich).
 *
 * NOTE: new Paparazzi tests need recorded golden PNGs. Generate them with
 * `./gradlew :app:recordPaparazziDebug` (the cloud Linux env has no Android
 * SDK, so recording happens on a workstation / CI) before `paparazziVerify`
 * can pass.
 */
class ViewAsSnapshotTest {
    @get:Rule
    val paparazzi: Paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 2200,
                    softButtons = false,
                ),
        )

    @Test
    fun view_as_public() {
        paparazzi.snapshot {
            Frame {
                ViewAsScreenContent(
                    state =
                        ViewAsUiState.Loaded(
                            selected = ViewerAudience.Public,
                            render = ViewAsSampleData.render(ViewerAudience.Public),
                        ),
                    selected = ViewerAudience.Public,
                    onSelect = {},
                    onBack = {},
                    onManagePrivacy = {},
                    onEdit = {},
                )
            }
        }
    }

    @Test
    fun view_as_connection() {
        paparazzi.snapshot {
            Frame {
                ViewAsScreenContent(
                    state =
                        ViewAsUiState.Loaded(
                            selected = ViewerAudience.Connection,
                            render = ViewAsSampleData.render(ViewerAudience.Connection),
                        ),
                    selected = ViewerAudience.Connection,
                    onSelect = {},
                    onBack = {},
                    onManagePrivacy = {},
                    onEdit = {},
                )
            }
        }
    }

    @Test
    fun view_as_loading() {
        paparazzi.snapshot {
            Frame {
                ViewAsScreenContent(
                    state = ViewAsUiState.Loading,
                    selected = ViewerAudience.Connection,
                    onSelect = {},
                    onBack = {},
                    onManagePrivacy = {},
                    onEdit = {},
                )
            }
        }
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        PantopusTheme {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(PantopusColors.appBg),
            ) {
                content()
            }
        }
    }
}
