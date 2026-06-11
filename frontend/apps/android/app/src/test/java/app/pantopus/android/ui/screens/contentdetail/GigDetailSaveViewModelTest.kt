@file:Suppress("PackageNaming", "FunctionNaming", "MagicNumber")

package app.pantopus.android.ui.screens.contentdetail

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.gigs.GigBidsResponse
import app.pantopus.android.data.api.models.gigs.GigDetailResponse
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.models.gigs.GigQuestionsResponse
import app.pantopus.android.data.api.models.gigs.GigSaveResponse
import app.pantopus.android.data.api.models.users.UserDto
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.files.FilesRepository
import app.pantopus.android.data.gigs.GigsRepository
import app.pantopus.android.data.payments.PaymentsRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P1.C — save/bookmark toggle on the gig detail: initial state from
 * `saved_by_user`, optimistic flip with the matching endpoint, and a
 * revert + error callback on failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GigDetailSaveViewModelTest {
    private val repo: GigsRepository = mockk()
    private val authRepo: AuthRepository = mockk()
    private val filesRepo: FilesRepository = mockk()
    private val paymentsRepo: PaymentsRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val signed =
            AuthRepository.State.SignedIn(
                user = UserDto(id = "viewer-1", email = "v@example.com", displayName = "Viewer", avatarUrl = null),
            )
        every { authRepo.state } returns MutableStateFlow<AuthRepository.State>(signed)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun openGig(savedByUser: Boolean?) =
        GigDto(
            id = "g1",
            title = "Hang shelves",
            userId = "poster-1",
            status = "open",
            savedByUser = savedByUser,
        )

    private fun loadedVm(savedByUser: Boolean?): GigDetailViewModel {
        coEvery { repo.detail("g1") } returns NetworkResult.Success(GigDetailResponse(gig = openGig(savedByUser)))
        coEvery { repo.bids("g1") } returns NetworkResult.Success(GigBidsResponse(bids = emptyList()))
        coEvery { repo.questions("g1") } returns NetworkResult.Success(GigQuestionsResponse(questions = emptyList()))
        val vm =
            GigDetailViewModel(
                repo,
                authRepo,
                filesRepo,
                paymentsRepo,
                SavedStateHandle(mapOf(GigDetailViewModel.GIG_ID_KEY to "g1")),
            )
        vm.load()
        return vm
    }

    @Test
    fun initial_saved_state_comes_from_saved_by_user() =
        runTest {
            assertTrue(loadedVm(savedByUser = true).saved.value)
            assertFalse(loadedVm(savedByUser = false).saved.value)
            assertFalse(loadedVm(savedByUser = null).saved.value)
        }

    @Test
    fun toggle_save_flips_optimistically_and_posts() =
        runTest {
            coEvery { repo.save("g1") } returns NetworkResult.Success(GigSaveResponse(saved = true))
            val vm = loadedVm(savedByUser = false)
            vm.toggleSave()
            assertTrue(vm.saved.value)
            coVerify(exactly = 1) { repo.save("g1") }
        }

    @Test
    fun toggle_from_saved_calls_unsave() =
        runTest {
            coEvery { repo.unsave("g1") } returns NetworkResult.Success(GigSaveResponse(saved = false))
            val vm = loadedVm(savedByUser = true)
            vm.toggleSave()
            assertFalse(vm.saved.value)
            coVerify(exactly = 1) { repo.unsave("g1") }
        }

    @Test
    fun failed_save_reverts_and_reports() =
        runTest {
            coEvery { repo.save("g1") } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = loadedVm(savedByUser = false)
            var error: String? = null
            vm.toggleSave { error = it }
            assertFalse("Optimistic flip must revert on failure", vm.saved.value)
            assertEquals("Couldn't save this task.", error)
        }

    @Test
    fun failed_unsave_reverts_back_to_saved() =
        runTest {
            coEvery { repo.unsave("g1") } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = loadedVm(savedByUser = true)
            var error: String? = null
            vm.toggleSave { error = it }
            assertTrue(vm.saved.value)
            assertEquals("Couldn't remove the save.", error)
        }
}
