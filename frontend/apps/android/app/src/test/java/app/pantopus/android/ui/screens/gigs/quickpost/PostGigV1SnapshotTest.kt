@file:Suppress("MagicNumber")

package app.pantopus.android.ui.screens.gigs.quickpost

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.screens.shared.form.FormShell
import app.pantopus.android.ui.screens.shared.form.FormShellLeading
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Paparazzi snapshots for A13.8 Post Gig V1: the filled-ready sofa move
 * frame and the round-trip server-validation error frame.
 */
class PostGigV1SnapshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 1800,
                    softButtons = false,
                ),
        )

    @Test
    fun post_gig_v1_filled_ready() {
        paparazzi.snapshot {
            SnapshotFrame(PostGigV1SampleData.filledState as PostGigV1UiState.Content)
        }
    }

    @Test
    fun post_gig_v1_validation_errors() {
        paparazzi.snapshot {
            SnapshotFrame(PostGigV1SampleData.validationErrorState as PostGigV1UiState.Content)
        }
    }

    @Test
    fun post_gig_v1_round_trip_validation_then_success() {
        val vm = PostGigV1ViewModel()
        vm.updateCategory(PostGigV1SampleData.validationErrorForm.category)
        vm.updateTitle(PostGigV1SampleData.validationErrorForm.title)
        vm.updateDescription(PostGigV1SampleData.validationErrorForm.description)
        vm.updatePrice(PostGigV1SampleData.validationErrorForm.price)
        vm.updateScheduledAt(PostGigV1SampleData.validationErrorForm.scheduledAt)
        vm.updateLocation(PostGigV1SampleData.validationErrorForm.location)

        vm.submit(now = PostGigV1SampleData.referenceNow)

        val rejected = vm.state.value as PostGigV1UiState.Content
        assertEquals(
            listOf(PostGigV1Field.Description, PostGigV1Field.Price, PostGigV1Field.DateTime),
            rejected.validationErrors.map { it.field },
        )

        vm.updateDescription(PostGigV1SampleData.filledForm.description)
        vm.updatePrice(PostGigV1SampleData.filledForm.price)
        vm.updateScheduledAt(PostGigV1SampleData.filledForm.scheduledAt)
        vm.submit(now = PostGigV1SampleData.referenceNow)

        val posted = vm.state.value as PostGigV1UiState.Content
        assertTrue(posted.validationErrors.isEmpty())
        assertNotNull(posted.postedGigId)
    }

    @Composable
    private fun SnapshotFrame(state: PostGigV1UiState.Content) {
        PantopusTheme {
            FormShell(
                title = "Post gig",
                leading = FormShellLeading.Back,
                rightActionLabel = "Post",
                isValid = state.canAttemptSubmit,
                isDirty = state.isPostEnabled,
                isSaving = state.isSubmitting,
                onClose = {},
                onCommit = {},
            ) {
                PostGigV1Content(state = state, actions = PostGigV1Actions())
            }
        }
    }
}
