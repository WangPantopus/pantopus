@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.profile.professional

import app.pantopus.android.data.api.models.professional.ProfessionalProfileDto
import app.pantopus.android.data.api.models.professional.ProfessionalProfileResponse
import app.pantopus.android.data.api.models.professional.ProfessionalServiceAreaDto
import app.pantopus.android.data.api.models.professional.ProfessionalVerificationStatusResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.professional.ProfessionalRepository
import io.mockk.coEvery
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfessionalProfileViewModelTest {
    private val repository: ProfessionalRepository = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun loaded(
        seed: ProfessionalProfileContent = ProfessionalProfileSampleData.published,
        baseline: ProfessionalProfileContent? = null,
    ): ProfessionalProfileViewModel {
        val viewModel = ProfessionalProfileViewModel(repository, seed = seed, baseline = baseline)
        viewModel.load()
        return viewModel
    }

    // Sample seam

    @Test fun loadPublishedSeedIsVerifiedAndClean() {
        val viewModel = loaded()
        val state = viewModel.state.value as ProfessionalProfileUiState.Verified
        assertEquals(92, state.content.strength)
        assertEquals(0, state.content.dirtyCount)
        assertEquals(0, state.content.pendingCount)
    }

    @Test fun loadFailureIsError() {
        val viewModel = ProfessionalProfileViewModel(repository, simulateFailure = true)
        viewModel.load()
        assertTrue(viewModel.state.value is ProfessionalProfileUiState.Error)
    }

    @Test fun pendingSeedDerivesPendingWithTwoClaims() {
        val viewModel =
            loaded(
                seed = ProfessionalProfileSampleData.pendingEdits,
                baseline = ProfessionalProfileSampleData.published,
            )
        val state = viewModel.state.value as ProfessionalProfileUiState.Pending
        assertEquals(68, state.content.strength)
        assertEquals(5, state.dirtyCount)
        assertEquals(2, state.pendingCount)
    }

    @Test fun addSkillTransitionsToPendingWithoutPendingClaim() {
        val viewModel = loaded()
        viewModel.addSkill()
        val state = viewModel.state.value as ProfessionalProfileUiState.Pending
        assertEquals(1, state.dirtyCount)
        assertEquals(0, state.pendingCount)
        assertTrue(state.content.skills.last().isFresh)
    }

    @Test fun addCertificationTransitionsToPendingWithPendingClaim() {
        val viewModel = loaded()
        viewModel.addCertification()
        val state = viewModel.state.value as ProfessionalProfileUiState.Pending
        assertEquals(1, state.dirtyCount)
        assertEquals(1, state.pendingCount)
    }

    @Test fun updateYearsInRoleKeepsOnlyDigits() {
        val viewModel = loaded()
        viewModel.updateYearsInRole("12 years")
        val state = viewModel.state.value as ProfessionalProfileUiState.Pending
        assertEquals("12", state.content.yearsInRole.value)
        assertTrue(state.content.yearsInRole.isDirty)
    }

    @Test fun discardRevertsPendingSeedToPublishedBaseline() {
        val viewModel =
            loaded(
                seed = ProfessionalProfileSampleData.pendingEdits,
                baseline = ProfessionalProfileSampleData.published,
            )
        viewModel.discard()
        val state = viewModel.state.value as ProfessionalProfileUiState.Verified
        assertEquals(ProVerificationStatus.Verified, state.content.company.status)
        assertFalse(state.content.skills.any { it.label == "Tile work" })
    }

    @Test fun saveAndSubmitCommitsFreshMarkersButKeepsClaimsInReview() {
        val viewModel =
            loaded(
                seed = ProfessionalProfileSampleData.pendingEdits,
                baseline = ProfessionalProfileSampleData.published,
            )
        viewModel.saveAndSubmit()
        val state = viewModel.state.value as ProfessionalProfileUiState.Verified
        assertEquals(0, state.content.dirtyCount)
        assertEquals(2, state.content.pendingCount)
        assertFalse(state.content.skills.any { it.isFresh })
        assertEquals("Submitted — 2 claims in review.", viewModel.toast.value?.text)
    }

    @Test fun saveAndSubmitWhenCleanIsNoop() {
        val viewModel = loaded()
        viewModel.saveAndSubmit()
        assertTrue(viewModel.state.value is ProfessionalProfileUiState.Verified)
        assertNull(viewModel.toast.value)
    }

    // Live read-path

    @Test fun liveLoadHydratesFromProfileMe() =
        runTest {
            coEvery { repository.profileMe() } returns
                NetworkResult.Success(
                    ProfessionalProfileResponse(
                        ProfessionalProfileDto(
                            headline = "Licensed Handyman",
                            categories = listOf("handyman", "carpentry"),
                            serviceArea = ProfessionalServiceAreaDto(city = "Elm Park", state = "NY"),
                            isPublic = true,
                            isActive = false,
                            verificationStatus = "verified",
                        ),
                    ),
                )
            coEvery { repository.verificationStatus() } returns
                NetworkResult.Success(ProfessionalVerificationStatusResponse(tier = 2, status = "verified"))

            val viewModel = ProfessionalProfileViewModel(repository)
            viewModel.load()

            val state = viewModel.state.value as ProfessionalProfileUiState.Verified
            assertEquals("Licensed Handyman", state.content.title.value)
            assertEquals(2, state.content.skills.size)
            assertEquals(ProVerificationStatus.Verified, state.content.company.status)
        }

    @Test fun liveLoadFailureSurfacesError() =
        runTest {
            coEvery { repository.profileMe() } returns NetworkResult.Failure(NetworkError.Server(500, "boom"))

            val viewModel = ProfessionalProfileViewModel(repository)
            viewModel.load()

            assertTrue(viewModel.state.value is ProfessionalProfileUiState.Error)
        }
}
