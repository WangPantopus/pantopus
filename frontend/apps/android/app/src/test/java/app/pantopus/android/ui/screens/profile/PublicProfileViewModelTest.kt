@file:Suppress("MagicNumber", "LongMethod", "PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.profile

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.profile.PublicProfileDto
import app.pantopus.android.data.api.models.profile.PublicProfileReview
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.models.relationships.ConnectionRequestResponse
import app.pantopus.android.data.blocks.BlocksRepository
import app.pantopus.android.data.profile.ProfileRepository
import app.pantopus.android.data.relationships.RelationshipsRepository
import app.pantopus.android.ui.screens.shared.content_detail.bodies.ProfileTab
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PublicProfileViewModelTest {
    private val repo: ProfileRepository = mockk()
    private val relationships: RelationshipsRepository = mockk()
    private val blocks: BlocksRepository = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(): PublicProfileViewModel =
        PublicProfileViewModel(
            repo = repo,
            relationships = relationships,
            blocks = blocks,
            savedStateHandle = SavedStateHandle(mapOf(PUBLIC_PROFILE_USER_ID_KEY to "u1")),
        )

    private fun profile(
        verified: Boolean = true,
        reviews: List<PublicProfileReview> = emptyList(),
        rating: Double? = 4.8,
        gigs: Int? = 5,
    ): PublicProfileDto =
        PublicProfileDto(
            id = "u1",
            username = "alex",
            firstName = "Alex",
            lastName = "Rivera",
            name = "Alex Rivera",
            bio = "Cambridge transplant.",
            tagline = "Builder",
            city = "Cambridge",
            state = "MA",
            accountType = "personal",
            verified = verified,
            createdAt = "2025-01-01T00:00:00.000Z",
            gigsPosted = 2,
            gigsCompleted = gigs,
            averageRating = rating,
            reviewCount = reviews.size,
            followersCount = 12,
            reviews = reviews,
            skills = listOf("Carpentry", "Spanish"),
        )

    @Test fun load_happy_path() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Success(profile())
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as PublicProfileUiState.Loaded
            assertEquals("Alex Rivera", loaded.content.header.displayName)
            assertEquals("alex", loaded.content.header.handle)
            assertEquals("Cambridge, MA", loaded.content.header.locality)
            assertTrue(loaded.content.header.isVerified)
            assertEquals(listOf("Carpentry", "Spanish"), loaded.content.stats.skills)
        }

    @Test fun tab_switching_does_not_refetch() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Success(profile())
            val vm = makeVm()
            vm.load()
            vm.selectTab(ProfileTab.Reviews)
            vm.selectTab(ProfileTab.Gigs)
            coVerify(exactly = 1) { repo.publicProfile("u1") }
            assertEquals(ProfileTab.Gigs, vm.selectedTab.value)
        }

    @Test fun empty_reviews_state() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns
                NetworkResult.Success(profile(verified = false, reviews = emptyList(), rating = 0.0, gigs = 0))
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as PublicProfileUiState.Loaded
            assertTrue(loaded.content.stats.reviews.isEmpty())
            assertFalse(loaded.content.header.isVerified)
        }

    @Test fun not_found_emits_friendly_message() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Failure(NetworkError.NotFound)
            val vm = makeVm()
            vm.load()
            val errorState = vm.state.value as PublicProfileUiState.Error
            assertTrue(errorState.message.contains("profile"))
        }

    @Test fun connect_sends_request_and_marks_succeeded() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Success(profile())
            coEvery { relationships.sendRequest("u1") } returns
                NetworkResult.Success(ConnectionRequestResponse(message = "ok"))
            val vm = makeVm()
            vm.load()
            vm.connect()
            assertEquals(PublicProfileActionState.Succeeded, vm.connectState.value)
            assertEquals("Connection request sent", vm.toastMessage.value)
        }

    @Test fun connect_failure_surfaces_toast() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Success(profile())
            coEvery { relationships.sendRequest("u1") } returns
                NetworkResult.Failure(NetworkError.Forbidden)
            val vm = makeVm()
            vm.load()
            vm.connect()
            assertTrue(vm.connectState.value is PublicProfileActionState.Failed)
            assertTrue(!vm.toastMessage.value.isNullOrEmpty())
        }

    @Test fun block_succeeds_and_emits_toast() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Success(profile())
            coEvery { blocks.block("u1") } returns NetworkResult.Success(Unit)
            val vm = makeVm()
            vm.load()
            vm.block()
            assertEquals(PublicProfileActionState.Succeeded, vm.blockState.value)
            assertEquals("User blocked", vm.toastMessage.value)
        }

    @Test fun overflow_flag_toggles() =
        runTest {
            coEvery { repo.publicProfile("u1") } returns NetworkResult.Success(profile())
            val vm = makeVm()
            vm.load()
            assertFalse(vm.showOverflow.value)
            vm.setShowOverflow(true)
            assertTrue(vm.showOverflow.value)
        }
}
