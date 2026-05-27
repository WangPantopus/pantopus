@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.homes.verify_landlord.postcard

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.ui.screens.homes.verify_landlord.VerifyLandlordSubmitState
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
        override val submitDelayMillis: Long = 0L,
        override val expectedCode: String = PostcardVerificationViewModel.DEFAULT_EXPECTED_CODE,
    ) : PostcardVerificationViewModel(handle)

    private fun makeVm(
        homeId: String = "home-1",
        expectedCode: String = "4Q2K7B",
    ): TestVm =
        TestVm(
            handle = SavedStateHandle(mapOf(POSTCARD_VERIFICATION_HOME_ID_KEY to homeId)),
            expectedCode = expectedCode,
        )

    // MARK: - Stage gating

    @Test fun in_transit_stage_locks_code_input() {
        val vm = makeVm()
        assertFalse(vm.state.value.isCodeInputUnlocked)
        assertFalse(vm.state.value.primaryCtaEnabled)
    }

    @Test fun delivered_homeid_resolves_to_delivered_stage() {
        val vm = makeVm("home-delivered")
        assertEquals(PostcardDeliveryStage.Delivered, vm.state.value.stage)
        assertTrue(vm.state.value.isCodeInputUnlocked)
    }

    @Test fun setStage_transitions_and_resets_code_when_locking() {
        val vm = makeVm("home-delivered")
        vm.updateCode("4Q2K7B")
        vm.setStage(PostcardDeliveryStage.InTransit)
        assertEquals(PostcardDeliveryStage.InTransit, vm.state.value.stage)
        assertEquals("", vm.state.value.codeInput)
        assertNull(vm.state.value.content.deliveredOn)
    }

    // MARK: - Code typing

    @Test fun updateCode_uppercases_and_clamps() {
        val vm = makeVm("home-delivered")
        vm.updateCode("abc123extra")
        assertEquals("ABC123", vm.state.value.codeInput)
    }

    @Test fun filled_code_enables_cta_on_delivered() {
        val vm = makeVm("home-delivered")
        vm.updateCode("4Q2K7B")
        assertTrue(vm.state.value.primaryCtaEnabled)
    }

    // MARK: - Verify

    @Test fun verify_correct_code_fires_verified_event() =
        runTest {
            val vm = makeVm("home-42-delivered", expectedCode = "4Q2K7B")
            vm.updateCode("4Q2K7B")
            vm.verifyTapped()
            assertEquals(
                PostcardVerificationOutboundEvent.Verified("home-42-delivered"),
                vm.pendingEvent.value,
            )
            assertEquals(VerifyLandlordSubmitState.Submitted, vm.state.value.submitState)
        }

    @Test fun verify_wrong_code_surfaces_error_and_clears_input() =
        runTest {
            val vm = makeVm("home-delivered", expectedCode = "ABCDEF")
            vm.updateCode("4Q2K7B")
            vm.verifyTapped()
            assertTrue(vm.state.value.submitState is VerifyLandlordSubmitState.Error)
            assertEquals("", vm.state.value.codeInput)
            assertNull(vm.pendingEvent.value)
        }

    @Test fun in_transit_verify_is_noop() =
        runTest {
            val vm = makeVm() // in-transit
            // Even if a faulty caller pushes a code, in-transit blocks
            // submit.
            vm.updateCode("4Q2K7B")
            vm.verifyTapped()
            assertEquals(VerifyLandlordSubmitState.Idle, vm.state.value.submitState)
            assertNull(vm.pendingEvent.value)
        }

    // MARK: - Resend

    @Test fun resend_clears_code_input() {
        val vm = makeVm("home-delivered")
        vm.updateCode("4Q2K7B")
        vm.resendPostcard()
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
