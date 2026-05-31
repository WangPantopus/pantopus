@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.settings.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Rule
import org.junit.Test

/**
 * B6.1 — full-screen Paparazzi baselines for the A19 long-form legal viewer.
 * Four frames, one per design entry: Privacy + Terms, each in its TOC-expanded
 * entry state and its collapsed mid-scroll reading state (TOC closed,
 * back-to-top fab visible).
 *
 * The iOS mirror is `LegalDocumentSnapshotTests` (ImageRenderer); the two
 * render the same four frames from the same verbatim copy.
 *
 * Baselines live under `app/src/test/snapshots/images/`; regenerate via
 * `./gradlew paparazziRecord`.
 */
class LegalDocumentSnapshotTest {
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
    fun privacy_toc_expanded_entry() {
        snapshot(LegalDocs.privacy, "Privacy policy", "legalContent.privacy", tocOpen = true, showTop = false)
    }

    @Test
    fun privacy_collapsed_reading() {
        snapshot(LegalDocs.privacy, "Privacy policy", "legalContent.privacy", tocOpen = false, showTop = true)
    }

    @Test
    fun terms_toc_expanded_entry() {
        snapshot(LegalDocs.terms, "Terms of service", "legalContent.terms", tocOpen = true, showTop = false)
    }

    @Test
    fun terms_collapsed_reading() {
        snapshot(LegalDocs.terms, "Terms of service", "legalContent.terms", tocOpen = false, showTop = true)
    }

    private fun snapshot(
        model: LegalDocModel,
        title: String,
        tag: String,
        tocOpen: Boolean,
        showTop: Boolean,
    ) {
        paparazzi.snapshot {
            Frame {
                LegalScaffold(
                    model = model,
                    title = title,
                    screenTestTag = tag,
                    tocOpen = tocOpen,
                    showBackToTop = showTop,
                    listState = rememberLazyListState(),
                    onBack = {},
                    onToggleTOC = {},
                    onJump = {},
                    onBackToTop = {},
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
            ) { content() }
        }
    }
}
