@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.audience_profile.edit_persona

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.audience.AudienceProfileRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Mirrors `EditPersonaViewModelTests` (iOS): the persona id selects the
 * frame — the sourdough fixture id loads SETUP (3-of-7 checklist, locked
 * monetization); any other id loads LIVE (Stripe connected, paid tiers).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditPersonaViewModelTest {
    private val repository: AudienceProfileRepository = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // No live persona overlay in these tests — `me()` fails so `load()`
        // falls back to the pure sample frame the assertions below pin.
        coEvery { repository.me() } returns NetworkResult.Failure(NetworkError.Unauthorized)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(personaId: String): EditPersonaViewModel =
        EditPersonaViewModel(
            SavedStateHandle(mapOf(EDIT_PERSONA_PERSONA_ID_KEY to personaId)),
            repository,
        )

    @Test
    fun loadProjectsLiveFrame() =
        runTest {
            val vm = makeVm(EditPersonaSampleData.PERSONA_ID)
            vm.load()
            val state = vm.state.value
            assertTrue("Expected Live, got $state", state is EditPersonaUiState.Live)
            val content = (state as EditPersonaUiState.Live).content
            assertEquals("elmpark.watch", content.handle)
            assertEquals(PersonaHandleStatus.Reserved, content.handleStatus)
            assertEquals(PersonaCapOption.Weekly3, content.cap)
            assertTrue(content.canAddTier)
            assertTrue(content.stripe is PersonaStripeState.Connected)
            assertFalse(content.tiers.any { it.kind == PersonaTierCard.Kind.PaidLocked })
        }

    @Test
    fun loadProjectsSetupFrame() =
        runTest {
            val vm = makeVm(EditPersonaSampleData.setup.personaId)
            vm.load()
            val state = vm.state.value
            assertTrue("Expected Setup, got $state", state is EditPersonaUiState.Setup)
            state as EditPersonaUiState.Setup
            assertEquals(3, state.stepsDone)
            assertEquals(7, state.stepsTotal)
            assertEquals(7, state.content.checklist.size)
            assertEquals(3, state.content.checklist.count { it.done })
            assertEquals("stripe", state.content.checklist.first { it.isNext }.id)
            assertEquals(PersonaHandleStatus.Available, state.content.handleStatus)
            assertEquals(PersonaCapOption.Weekly1, state.content.cap)
            assertFalse(state.content.canAddTier)
            assertTrue(state.content.stripe is PersonaStripeState.NotConnected)
            assertTrue(state.content.tiers.any { it.kind == PersonaTierCard.Kind.PaidLocked })
        }
}
