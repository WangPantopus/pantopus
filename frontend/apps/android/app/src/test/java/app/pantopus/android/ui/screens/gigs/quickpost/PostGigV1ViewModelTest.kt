@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.gigs.quickpost

import app.pantopus.android.data.api.models.gigs.CreateGigResponse
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.gigs.GigsRepository
import io.mockk.coEvery
import io.mockk.coVerify
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

/**
 * A13.8 — covers the legacy gig composer's `submit()` now that it posts to
 * `POST /api/gigs` (`GigsRepository.create`). Parity with the iOS
 * `PostGigV1ViewModelTests`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PostGigV1ViewModelTest {
    private val repo: GigsRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun filledVm(): PostGigV1ViewModel {
        val vm = PostGigV1ViewModel(repo)
        val form = PostGigV1SampleData.filledForm
        vm.updateCategory(form.category)
        vm.updateTitle(form.title)
        vm.updateDescription(form.description)
        vm.updatePrice(form.price)
        vm.updateScheduledAt(form.scheduledAt)
        vm.updateLocation(form.location)
        return vm
    }

    @Test
    fun submit_valid_form_posts_and_emits_event() =
        runTest {
            coEvery { repo.create(any()) } returns
                NetworkResult.Success(CreateGigResponse(gig = GigDto(id = "server-gig", title = "Help moving a sofa")))
            val vm = filledVm()
            vm.submit(now = PostGigV1SampleData.referenceNow)
            val content = vm.state.value as PostGigV1UiState.Content
            assertTrue(content.validationErrors.isEmpty())
            assertFalse(content.isSubmitting)
            assertEquals("server-gig", content.postedGigId)
            assertEquals(PostGigV1Event.Posted("server-gig"), vm.pendingEvent.value)
        }

    @Test
    fun submit_invalid_form_surfaces_validation_and_skips_network() =
        runTest {
            val vm = PostGigV1ViewModel(repo) // empty default form
            vm.submit(now = PostGigV1SampleData.referenceNow)
            val content = vm.state.value as PostGigV1UiState.Content
            assertTrue(content.validationErrors.isNotEmpty())
            assertNull(content.postedGigId)
            coVerify(exactly = 0) { repo.create(any()) }
        }

    @Test
    fun submit_server_error_flips_to_fatal_error_and_retry_restores_form() =
        runTest {
            coEvery { repo.create(any()) } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = filledVm()
            vm.submit(now = PostGigV1SampleData.referenceNow)
            assertTrue(vm.state.value is PostGigV1UiState.FatalError)
            // Form survives the failure so retry re-attempts without re-entry.
            vm.retry()
            val restored = vm.state.value as PostGigV1UiState.Content
            assertEquals(PostGigV1SampleData.filledForm.title, restored.form.title)
        }
}
