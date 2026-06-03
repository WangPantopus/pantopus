@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.homes.verify_landlord

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.homes.PostcardInfoDto
import app.pantopus.android.data.api.models.homes.RequestPostcardResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomeVerificationRepository
import app.pantopus.android.data.network.NetworkMonitor
import io.mockk.any
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class VerifyLandlordWizardViewModelTest {
    private val networkMonitor: NetworkMonitor =
        mockk<NetworkMonitor>(relaxed = true).also {
            every { it.isOnline } returns MutableStateFlow(true)
        }

    private val verificationRepository: HomeVerificationRepository = mockk(relaxed = true)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { verificationRepository.requestPostcard(any()) } returns
            NetworkResult.Success(RequestPostcardResponse("ok", PostcardInfoDto("p1")))
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private class TestVm(
        networkMonitor: NetworkMonitor,
        handle: SavedStateHandle,
        verificationRepository: HomeVerificationRepository,
    ) : VerifyLandlordWizardViewModel(networkMonitor, handle, verificationRepository) {
        override val submitDelayMillis: Long = 0L
    }

    private fun makeVm(homeId: String = "home-1"): TestVm =
        TestVm(
            networkMonitor = networkMonitor,
            handle = SavedStateHandle(mapOf(VERIFY_LANDLORD_HOME_ID_KEY to homeId)),
            verificationRepository = verificationRepository,
        )

    // MARK: - Step machine

    @Test fun initial_state_is_start_step() {
        val vm = makeVm()
        assertEquals(VerifyLandlordStep.Start, vm.state.value.currentStep)
        assertEquals("Start verification", vm.chrome.primaryCtaLabel)
        assertTrue(vm.chrome.primaryCtaEnabled)
    }

    @Test fun primary_on_start_advances_to_details() {
        val vm = makeVm()
        vm.onPrimary()
        assertEquals(VerifyLandlordStep.Details, vm.state.value.currentStep)
        assertEquals("Submit", vm.chrome.primaryCtaLabel)
    }

    @Test fun back_on_details_returns_to_start_and_clears_errors() =
        runTest {
            val vm = makeVm()
            vm.onPrimary()
            // Force errors to surface so we can confirm they get cleared.
            VerifyLandlordSampleData.errorForm.let { errored ->
                vm.setOwnerName(errored.ownerName)
                vm.setEmail(errored.email)
                vm.setLease(errored.lease)
            }
            vm.onPrimary() // submit with errors
            assertNotNull(vm.state.value.errors)
            vm.onLeading() // back to start
            assertEquals(VerifyLandlordStep.Start, vm.state.value.currentStep)
            assertNull(vm.state.value.errors)
        }

    @Test fun leading_on_start_dismisses() {
        val vm = makeVm()
        vm.onLeading()
        assertEquals(VerifyLandlordOutboundEvent.Dismiss, vm.pendingEvent.value)
    }

    // MARK: - Variants

    @Test fun fast_track_variant_surfaces_existing_landlord() {
        val vm = makeVm("home-fast-track")
        assertTrue(vm.state.value.startContent.isFastTrack)
        assertNotNull(vm.state.value.startContent.existingLandlord)
    }

    @Test fun canonical_variant_has_no_existing_landlord() {
        val vm = makeVm()
        assertFalse(vm.state.value.startContent.isFastTrack)
        assertNull(vm.state.value.startContent.existingLandlord)
    }

    // MARK: - Validation

    @Test fun validation_catches_missing_tld() {
        val errors =
            VerifyLandlordSampleData.populatedForm
                .copy(email = "mira@elmstholdings")
                .validate()
        assertEquals("Missing top-level domain", errors.email)
    }

    @Test fun validation_catches_lease_unit_mismatch() {
        val errors = VerifyLandlordSampleData.errorForm.validate()
        assertNotNull(errors.lease)
        assertEquals("Missing top-level domain", errors.email)
        assertEquals(2, errors.count)
    }

    @Test fun validation_count_and_compact_summary_match_iOS_order() {
        val errors =
            VerifyLandlordValidationErrors(
                email = "Missing top-level domain",
                lease = "Unit mismatch",
            )
        assertEquals(2, errors.count)
        assertEquals("Email format · Lease unit mismatch", errors.compactSummary)
    }

    @Test fun pm_required_when_toggle_on() {
        val errors =
            VerifyLandlordSampleData.populatedForm
                .copy(pmEnabled = true, pmName = "", pmEmail = "")
                .validate()
        assertEquals("Required", errors.pmName)
        assertEquals("Required", errors.pmEmail)
    }

    @Test fun pm_not_required_when_toggle_off() {
        val errors =
            VerifyLandlordSampleData.populatedForm
                .copy(pmEnabled = false, pmName = "", pmEmail = "")
                .validate()
        assertNull(errors.pmName)
        assertNull(errors.pmEmail)
    }

    // MARK: - Submit state machine

    @Test fun submit_blocked_when_errors_present() =
        runTest {
            val vm = makeVm()
            vm.onPrimary() // -> details
            // Feed the errored form through the public mutators so the
            // VM's validation pipeline runs identically to the runtime.
            vm.setOwnerName(VerifyLandlordSampleData.errorForm.ownerName)
            vm.setContactName(VerifyLandlordSampleData.errorForm.contactName)
            vm.setEmail(VerifyLandlordSampleData.errorForm.email)
            vm.setLease(VerifyLandlordSampleData.errorForm.lease)
            vm.onPrimary() // submit
            assertEquals(VerifyLandlordStep.Details, vm.state.value.currentStep)
            val state = vm.state.value
            assertTrue(state.submitState is VerifyLandlordSubmitState.Error)
            assertEquals(2, state.errors?.count)
            assertNull(vm.pendingEvent.value)
            assertFalse(vm.chrome.primaryCtaEnabled)
        }

    @Test fun submit_happy_path_fires_open_postcard_event() =
        runTest {
            val vm = makeVm("home-42")
            vm.onPrimary()
            VerifyLandlordSampleData.populatedForm.let { f ->
                vm.setOwnerName(f.ownerName)
                vm.setContactName(f.contactName)
                vm.setEmail(f.email)
                vm.setPhone(f.phone)
                vm.setLease(f.lease)
                vm.setPMEnabled(true)
                vm.setPMName(f.pmName)
                vm.setPMEmail(f.pmEmail)
                vm.setPMPhone(f.pmPhone)
            }
            vm.onPrimary() // submit
            assertEquals(
                VerifyLandlordOutboundEvent.OpenPostcardVerification("home-42"),
                vm.pendingEvent.value,
            )
            assertEquals(VerifyLandlordSubmitState.Submitted, vm.state.value.submitState)
        }

    // MARK: - Field mutations

    @Test fun pm_toggle_off_clears_pm_fields() {
        val vm = makeVm()
        // Seed PM fields then flip the toggle off.
        vm.setPMEnabled(true)
        vm.setPMName("Daniel")
        vm.setPMEmail("d@x.co")
        vm.setPMPhone("(415) 555")
        vm.setPMEnabled(false)
        assertFalse(vm.state.value.form.pmEnabled)
        assertEquals("", vm.state.value.form.pmName)
        assertEquals("", vm.state.value.form.pmEmail)
        assertEquals("", vm.state.value.form.pmPhone)
    }

    @Test fun field_update_revalidates_when_errors_shown() =
        runTest {
            val vm = makeVm()
            vm.onPrimary()
            vm.setOwnerName(VerifyLandlordSampleData.errorForm.ownerName)
            vm.setContactName(VerifyLandlordSampleData.errorForm.contactName)
            vm.setEmail(VerifyLandlordSampleData.errorForm.email)
            vm.setLease(VerifyLandlordSampleData.errorForm.lease)
            vm.onPrimary() // submit with errors
            assertEquals(2, vm.state.value.errors?.count)
            vm.setEmail("mira@elmstholdings.com")
            assertEquals(1, vm.state.value.errors?.count)
        }

    @Test fun field_updates_do_not_show_errors_until_submit_attempt() {
        val vm = makeVm()
        vm.onPrimary()
        assertNull(vm.state.value.errors)
        vm.setEmail("typing@")
        assertNull(vm.state.value.errors)
    }
}
