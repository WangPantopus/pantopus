@file:Suppress("MagicNumber")

package app.pantopus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Unit + Paparazzi coverage for [ToastController] + [ToastView].
 *
 * Unit tests cover the StateFlow contract: show / replace / dismiss.
 * The auto-dismiss timer is exercised at the [ToastHost] level via the
 * existing motion infrastructure; pure controller behaviour is what
 * unit tests need to lock in.
 */
class ToastTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 480,
                    softButtons = false,
                ),
        )

    @Test
    fun controller_show_emits_message() = runTest {
        val controller = ToastController()
        controller.show("Saved", ToastKind.Success)
        val current = controller.current.first()
        assertEquals("Saved", current?.text)
        assertEquals(ToastKind.Success, current?.kind)
    }

    @Test
    fun controller_show_replaces_pending_message() = runTest {
        val controller = ToastController()
        controller.show("First")
        val first = controller.current.first()
        controller.show("Second")
        val second = controller.current.first()
        assertEquals("Second", second?.text)
        assertNotEquals(first?.id, second?.id)
    }

    @Test
    fun controller_dismiss_clears_current() = runTest {
        val controller = ToastController()
        controller.show("Bye", ToastKind.Info)
        controller.dismiss()
        assertNull(controller.current.first())
    }

    @Test
    fun controller_default_kind_is_info() = runTest {
        val controller = ToastController()
        controller.show("hello")
        assertEquals(ToastKind.Info, controller.current.first()?.kind)
    }

    @Test
    fun snapshot_all_four_kinds() {
        paparazzi.snapshot {
            ToastGallery()
        }
    }
}

@Composable
private fun ToastGallery() {
    Column(
        modifier =
            Modifier
                .background(PantopusColors.appSurface)
                .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Text("Success", style = PantopusTextStyle.caption)
        ToastView(message = ToastMessage("Bid submitted.", ToastKind.Success))

        Text("Warning", style = PantopusTextStyle.caption)
        ToastView(message = ToastMessage("Check your address.", ToastKind.Warning))

        Text("Error", style = PantopusTextStyle.caption)
        ToastView(message = ToastMessage("Could not send.", ToastKind.Error))

        Text("Info", style = PantopusTextStyle.caption)
        ToastView(message = ToastMessage("Edits discarded.", ToastKind.Info))
    }
}
