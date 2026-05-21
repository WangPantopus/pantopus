@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.profile.professional

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfessionalProfileViewModelTest {
    private fun loaded(
        seed: ProfessionalProfileContent = ProfessionalProfileSampleData.published,
        baseline: ProfessionalProfileContent? = null,
    ): ProfessionalProfileViewModel {
        val viewModel = ProfessionalProfileViewModel(seed = seed, baseline = baseline)
        viewModel.load()
        return viewModel
    }

    @Test fun loadPublishedSeedIsVerifiedAndClean() {
        val viewModel = loaded()
        val state = viewModel.state.value as ProfessionalProfileUiState.Verified
        assertEquals(92, state.content.strength)
        assertEquals(0, state.content.dirtyCount)
        assertEquals(0, state.content.pendingCount)
    }

    @Test fun loadFailureIsError() {
        val viewModel = ProfessionalProfileViewModel(simulateFailure = true)
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
}
