@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.businesses.owner_dashboard

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A10.7 — owner-dashboard view-model coverage. B3.2 is sample-driven, so the
 * suite pins the loaded projection, the seed hook used by previews, and the
 * local-state reply stub. Mirrors iOS `BusinessOwnerViewModelTests`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BusinessOwnerViewModelTest {
    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(): BusinessOwnerViewModel =
        BusinessOwnerViewModel(
            savedStateHandle = SavedStateHandle(mapOf(BUSINESS_OWNER_BUSINESS_ID_KEY to "marlow")),
        )

    @Test fun load_emitsLoadedSample() =
        runTest {
            val vm = makeVm()
            vm.load()
            advanceUntilIdle()
            val state = vm.state.value
            assertTrue(state is BusinessOwnerUiState.Loaded)
            val content = (state as BusinessOwnerUiState.Loaded).content
            assertEquals("marlow", content.businessId)
            assertTrue(content.isLive)
            assertEquals(3, content.insights.size)
            assertEquals(92, content.profileStrength.percent)
            assertEquals("Marlow & Co. Cleaning", content.publicProfile.header.displayName)
        }

    @Test fun submitReply_setsReplyOnReview() =
        runTest {
            val vm = makeVm()
            vm.seedForPreview(BusinessOwnerSampleData.marlow)
            vm.submitReply("dana", "Thanks for the feedback, Dana!")
            val content = (vm.state.value as BusinessOwnerUiState.Loaded).content
            assertEquals("Thanks for the feedback, Dana!", content.reviews.first { it.id == "dana" }.reply)
        }

    @Test fun submitReply_blankText_isIgnored() =
        runTest {
            val vm = makeVm()
            vm.seedForPreview(BusinessOwnerSampleData.marlow)
            vm.submitReply("dana", "   \n ")
            val content = (vm.state.value as BusinessOwnerUiState.Loaded).content
            assertNull(content.reviews.first { it.id == "dana" }.reply)
        }

    @Test fun applyingReply_recomputesPendingReplyLabel() {
        val base = BusinessOwnerSampleData.marlow
        assertEquals("2 to reply", base.reviewsToReplyLabel)
        val updated = base.applyingReply("Appreciate it", "dana")
        assertEquals("Appreciate it", updated.reviews.first { it.id == "dana" }.reply)
        assertNull(updated.reviewsToReplyLabel)
    }
}
