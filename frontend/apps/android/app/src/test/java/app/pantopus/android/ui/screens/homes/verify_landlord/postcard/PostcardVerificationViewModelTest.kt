@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.homes.verify_landlord.postcard

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.homes.HomeVerificationRepository
import app.pantopus.android.ui.screens.homes.verify_landlord.VerifyLandlordSubmitState
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A12.7 — mirrors iOS `PostcardVerificationViewModelTests`. The delivery
 * stage is chrome only; the code field is live at all times so
 * `POST /api/homes/:id/verify-postcard` is reachable for real homes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PostcardVerificationViewModelTest {
    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private class TestVm(
        handle: SavedStateHandle,
        verificationRepository: HomeVerificationRepository = mockk(relaxed = true),
        override val submitDelayMillis: Long = 0L,
        override val expectedCode: String = PostcardVerificationViewModel.DEFAULT_EXPECTED_CODE,
    ) : PostcardVerificationViewModel(handle, verificationRepository)

    private fun makeVm(
        homeId: String = "home-1",
        expectedCode: String = "4Q2K7B",
    ): TestVm =
        TestVm(
            handle = SavedStateHandle(mapOf(POSTCARD_VERIFICATION_HOME_ID_KEY to homeId)),
            expectedCode = expectedCode,
        )

    // MARK: - Code entry is never gated on delivery

    @Test fun in_transit_stage_still_allows_code_entry() {
        val vm = makeVm()
        assertTrue(vm.state.value.isCodeInputUnlocked)
        assertFalse(vm.state.value.primaryCtaEnabled)
    }

    @Test fun real_home_id_does_not_lock_the_field() {
        // Regression: the old sample helper only unlocked home ids
        // containing "delivered", so every production home was locked.
        val vm = makeVm("0f0d7f0e-1c3a-4a1e-9a2b-6f0f7d6f1a2c")
        vm.updateCode("4Q2K7B")
        assertTrue(vm.state.value.isCodeInputUnlocked)
        assertTrue(vm.state.value.primaryCtaEnabled)
    }

    @Test fun have_code_escape_hatch_flips_frame() {
        val vm = makeVm()
        assertFalse(vm.state.value.showsCodeEntryFrame)
        vm.markHasCode()
        assertTrue(vm.state.value.showsCodeEntryFrame)
    }

    @Test fun setStage_delivered_enters_code_frame() {
        val vm = makeVm()
        vm.setStage(PostcardDeliveryStage.Delivered)
        assertEquals(PostcardDeliveryStage.Delivered, vm.state.value.stage)
        assertNotNull(vm.state.value.content.deliveredOn)
        assertTrue(vm.state.value.showsCodeEntryFrame)
    }

    // MARK: - Code typing

    @Test fun updateCode_uppercases_and_clamps() {
        val vm = makeVm()
        vm.updateCode("abc123extra")
        assertEquals("ABC123", vm.state.value.codeInput)
    }

    // MARK: - Verify

    @Test fun verify_correct_code_fires_verified_event() =
        runTest {
            val vm = makeVm("home-42", expectedCode = "4Q2K7B")
            vm.updateCode("4Q2K7B")
            vm.verifyTapped()
            assertEquals(
                PostcardVerificationOutboundEvent.Verified("home-42"),
                vm.pendingEvent.value,
            )
            assertEquals(VerifyLandlordSubmitState.Submitted, vm.state.value.submitState)
        }

    @Test fun verify_wrong_code_surfaces_error_and_clears_input() =
        runTest {
            val vm = makeVm("home-1", expectedCode = "ABCDEF")
            vm.updateCode("4Q2K7B")
            vm.verifyTapped()
            assertTrue(vm.state.value.submitState is VerifyLandlordSubmitState.Error)
            assertEquals("", vm.state.value.codeInput)
            assertNull(vm.pendingEvent.value)
        }

    @Test fun verify_from_in_transit_frame_still_submits() =
        runTest {
            val vm = makeVm("home-7", expectedCode = "4Q2K7B")
            assertFalse(vm.state.value.showsCodeEntryFrame)
            vm.updateCode("4Q2K7B")
            vm.verifyTapped()
            assertEquals(
                PostcardVerificationOutboundEvent.Verified("home-7"),
                vm.pendingEvent.value,
            )
        }

    @Test fun short_code_does_not_submit() =
        runTest {
            val vm = makeVm()
            vm.updateCode("4Q2")
            vm.verifyTapped()
            assertEquals(VerifyLandlordSubmitState.Idle, vm.state.value.submitState)
            assertNull(vm.pendingEvent.value)
        }

    // MARK: - Resend

    @Test fun request_new_code_clears_code_input() {
        val vm = makeVm()
        vm.updateCode("4Q2K7B")
        vm.requestNewCode()
        assertEquals("", vm.state.value.codeInput)
    }

    // MARK: - Outbound dismiss

    @Test fun dismiss_tapped_emits_dismiss_event() {
        val vm = makeVm()
        vm.dismissTapped()
        assertNotNull(vm.pendingEvent.value)
        assertEquals(PostcardVerificationOutboundEvent.Dismiss, vm.pendingEvent.value)
    }
}
