@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.homes.claim_ownership

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.homes.FileUploadResponse
import app.pantopus.android.data.api.models.homes.SubmitClaimEnvelope
import app.pantopus.android.data.api.models.homes.SubmitClaimRequest
import app.pantopus.android.data.api.models.homes.SubmitClaimResponse
import app.pantopus.android.data.api.models.homes.UploadEvidenceRequest
import app.pantopus.android.data.api.models.homes.UploadEvidenceResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.network.NetworkMonitor
import app.pantopus.android.ui.screens.shared.wizard.WizardLeadingControl
import app.pantopus.android.ui.screens.shared.wizard.WizardProgressLabel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
class ClaimOwnershipWizardViewModelTest {
    private val repo: HomesRepository = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor =
        mockk<NetworkMonitor>(relaxed = true).also {
            every { it.isOnline } returns MutableStateFlow(true)
        }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(): ClaimOwnershipWizardViewModel =
        ClaimOwnershipWizardViewModel(
            repository = repo,
            networkMonitor = networkMonitor,
            savedStateHandle = SavedStateHandle(mapOf(CLAIM_OWNERSHIP_HOME_ID_KEY to "home-1")),
        )

    private fun pickedFile(name: String): ClaimPickedFile =
        ClaimPickedFile(filename = name, mimeType = "image/jpeg", bytes = byteArrayOf(1, 2, 3))

    @Test fun initial_state_is_start_step() {
        val vm = makeVm()
        assertEquals(ClaimOwnershipStep.Start, vm.state.value.currentStep)
        assertEquals("Start claim", vm.chrome.primaryCtaLabel)
        assertTrue(vm.chrome.primaryCtaEnabled)
        assertEquals(WizardLeadingControl.Close, vm.chrome.leading)
    }

    @Test fun primary_on_start_advances_to_upload() {
        val vm = makeVm()
        vm.onPrimary()
        assertEquals(ClaimOwnershipStep.Upload, vm.state.value.currentStep)
        assertEquals("Submit claim", vm.chrome.primaryCtaLabel)
        // Slots empty → CTA disabled.
        assertFalse(vm.chrome.primaryCtaEnabled)
        assertEquals(WizardLeadingControl.Back, vm.chrome.leading)
    }

    @Test fun submit_blocked_without_both_files() =
        runTest {
            val vm = makeVm()
            vm.onPrimary()
            vm.picked(ClaimEvidenceSlot.Identity, pickedFile("id.jpg"))
            // Only one slot filled.
            assertFalse(vm.state.value.bothSlotsHaveFiles)
            vm.onPrimary() // submit — should no-op
            coVerify(exactly = 0) { repo.submitClaim(any(), any()) }
        }

    @Test fun submit_failure_keeps_files_and_surfaces_error() =
        runTest {
            coEvery { repo.submitClaim(any(), any()) } returns
                NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = makeVm()
            vm.onPrimary()
            vm.picked(ClaimEvidenceSlot.Identity, pickedFile("id.jpg"))
            vm.picked(ClaimEvidenceSlot.Ownership, pickedFile("deed.pdf"))
            vm.onPrimary() // submit
            assertEquals(ClaimOwnershipStep.Upload, vm.state.value.currentStep)
            assertEquals("Couldn't submit. Retry.", vm.state.value.submitError)
            assertTrue(vm.state.value.slots[ClaimEvidenceSlot.Identity] is ClaimSlotState.Picked)
            assertTrue(vm.state.value.slots[ClaimEvidenceSlot.Ownership] is ClaimSlotState.Picked)
        }

    @Test fun submit_happy_path_advances_to_success() =
        runTest {
            coEvery { repo.submitClaim("home-1", any()) } returns
                NetworkResult.Success(
                    SubmitClaimResponse(
                        message = "ok",
                        claim = SubmitClaimEnvelope(id = "claim-1", status = "under_review"),
                    ),
                )
            coEvery { repo.uploadFile(any(), any(), any()) } returns
                NetworkResult.Success(
                    FileUploadResponse(
                        message = "uploaded",
                        file = FileUploadResponse.FileRef(id = "f", url = "https://files/x"),
                    ),
                )
            coEvery { repo.uploadEvidence(any(), any(), any()) } returns
                NetworkResult.Success(
                    UploadEvidenceResponse(
                        evidence = emptyMap<String, Any?>(),
                        verificationTier = null,
                    ),
                )
            val vm = makeVm()
            vm.onPrimary()
            vm.picked(ClaimEvidenceSlot.Identity, pickedFile("id.jpg"))
            vm.picked(ClaimEvidenceSlot.Ownership, pickedFile("deed.pdf"))
            vm.onPrimary()
            assertEquals(ClaimOwnershipStep.Success, vm.state.value.currentStep)
            assertEquals("View status", vm.chrome.primaryCtaLabel)
            assertEquals(WizardProgressLabel.Hidden, vm.chrome.progressLabel)
            assertNotNull(vm.chrome.secondaryCta)
        }

    @Test fun note_carried_as_metadata_on_first_evidence_only() =
        runTest {
            coEvery { repo.submitClaim(any(), any()) } returns
                NetworkResult.Success(
                    SubmitClaimResponse(
                        message = "ok",
                        claim = SubmitClaimEnvelope(id = "claim-2", status = "under_review"),
                    ),
                )
            coEvery { repo.uploadFile(any(), any(), any()) } returns
                NetworkResult.Success(
                    FileUploadResponse(
                        message = "ok",
                        file = FileUploadResponse.FileRef(id = "f", url = "https://files/x"),
                    ),
                )
            val captured = mutableListOf<UploadEvidenceRequest>()
            coEvery { repo.uploadEvidence(any(), any(), io.mockk.capture(captured)) } returns
                NetworkResult.Success(
                    UploadEvidenceResponse(evidence = emptyMap<String, Any?>(), verificationTier = null),
                )
            val vm = makeVm()
            vm.onPrimary()
            vm.setNote("Inherited from grandparents")
            vm.picked(ClaimEvidenceSlot.Identity, pickedFile("id.jpg"))
            vm.picked(ClaimEvidenceSlot.Ownership, pickedFile("deed.pdf"))
            vm.onPrimary()
            assertEquals(2, captured.size)
            assertEquals("Inherited from grandparents", captured[0].metadata?.get("note"))
            assertNull(captured[1].metadata)
        }

    @Test fun duplicate_claim_nil_id_surfaces_friendly_error() =
        runTest {
            coEvery { repo.submitClaim(any(), any()) } returns
                NetworkResult.Success(
                    SubmitClaimResponse(
                        message = "duplicate",
                        claim = SubmitClaimEnvelope(id = null, status = "under_review"),
                    ),
                )
            val vm = makeVm()
            vm.onPrimary()
            vm.picked(ClaimEvidenceSlot.Identity, pickedFile("id.jpg"))
            vm.picked(ClaimEvidenceSlot.Ownership, pickedFile("deed.pdf"))
            vm.onPrimary()
            assertEquals(ClaimOwnershipStep.Upload, vm.state.value.currentStep)
            assertEquals(
                "We're already working on a claim for this home.",
                vm.state.value.submitError,
            )
        }

    @Test fun back_on_upload_step_returns_to_start() {
        val vm = makeVm()
        vm.onPrimary() // → upload
        vm.onLeading() // back
        assertEquals(ClaimOwnershipStep.Start, vm.state.value.currentStep)
    }

    @Test fun success_primary_emits_open_claims_list() =
        runTest {
            coEvery { repo.submitClaim(any(), any()) } returns
                NetworkResult.Success(
                    SubmitClaimResponse(
                        message = "ok",
                        claim = SubmitClaimEnvelope(id = "claim-3", status = "under_review"),
                    ),
                )
            coEvery { repo.uploadFile(any(), any(), any()) } returns
                NetworkResult.Success(
                    FileUploadResponse("ok", FileUploadResponse.FileRef("f", "https://files/x")),
                )
            coEvery { repo.uploadEvidence(any(), any(), any()) } returns
                NetworkResult.Success(
                    UploadEvidenceResponse(emptyMap<String, Any?>(), null),
                )
            val vm = makeVm()
            vm.onPrimary()
            vm.picked(ClaimEvidenceSlot.Identity, pickedFile("id.jpg"))
            vm.picked(ClaimEvidenceSlot.Ownership, pickedFile("deed.pdf"))
            vm.onPrimary()
            vm.onPrimary() // tap "View status"
            assertEquals(ClaimOwnershipOutboundEvent.OpenClaimsList, vm.pendingEvent.value)
        }

    @Test fun offline_blocks_submit() =
        runTest {
            val offlineMonitor =
                mockk<NetworkMonitor>(relaxed = true).also {
                    every { it.isOnline } returns MutableStateFlow(false)
                }
            val vm =
                ClaimOwnershipWizardViewModel(
                    repository = repo,
                    networkMonitor = offlineMonitor,
                    savedStateHandle = SavedStateHandle(mapOf(CLAIM_OWNERSHIP_HOME_ID_KEY to "home-1")),
                )
            vm.onPrimary()
            vm.picked(ClaimEvidenceSlot.Identity, pickedFile("id.jpg"))
            vm.picked(ClaimEvidenceSlot.Ownership, pickedFile("deed.pdf"))
            vm.onPrimary()
            coVerify(exactly = 0) { repo.submitClaim(any(), any()) }
            assertNotNull(vm.state.value.submitError)
        }

    @Test fun submit_builds_doc_upload_request() =
        runTest {
            val captured = slot<SubmitClaimRequest>()
            coEvery { repo.submitClaim("home-1", io.mockk.capture(captured)) } returns
                NetworkResult.Success(
                    SubmitClaimResponse(
                        message = "ok",
                        claim = SubmitClaimEnvelope(id = "claim-4", status = "under_review"),
                    ),
                )
            coEvery { repo.uploadFile(any(), any(), any()) } returns
                NetworkResult.Success(
                    FileUploadResponse("ok", FileUploadResponse.FileRef("f", "https://files/x")),
                )
            coEvery { repo.uploadEvidence(any(), any(), any()) } returns
                NetworkResult.Success(
                    UploadEvidenceResponse(emptyMap<String, Any?>(), null),
                )
            val vm = makeVm()
            vm.onPrimary()
            vm.picked(ClaimEvidenceSlot.Identity, pickedFile("id.jpg"))
            vm.picked(ClaimEvidenceSlot.Ownership, pickedFile("deed.pdf"))
            vm.onPrimary()
            assertEquals("doc_upload", captured.captured.method)
            assertEquals("owner", captured.captured.claimType)
        }
}
