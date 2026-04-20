package app.pantopus.android.ui.screens.homes

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.homes.HomeDetail
import app.pantopus.android.data.api.models.homes.HomeDetailResponse
import app.pantopus.android.data.api.models.homes.HomePublicProfile
import app.pantopus.android.data.api.models.homes.HomePublicProfileResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeDashboardViewModelTest {
    private val repo: HomesRepository = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm() =
        HomeDashboardViewModel(
            repo = repo,
            savedStateHandle = SavedStateHandle(mapOf(HOME_DASHBOARD_HOME_ID_KEY to "h1")),
        )

    private fun detail(isOwner: Boolean = true) =
        HomeDetailResponse(
            home =
                HomeDetail(
                    id = "h1",
                    name = "Main",
                    address = "1 Main",
                    city = "X",
                    state = "CA",
                    zipcode = "90000",
                    homeType = "single_family",
                    visibility = "public",
                    description = null,
                    createdAt = null,
                    owner = null,
                    occupants = emptyList(),
                    location = null,
                    isOwner = isOwner,
                    isPendingOwner = false,
                    pendingClaimId = null,
                    isOccupant = !isOwner,
                    owners = emptyList(),
                    canDeleteHome = isOwner,
                ),
        )

    private fun public_() =
        HomePublicProfileResponse(
            home =
                HomePublicProfile(
                    id = "h1",
                    name = null,
                    address = "200 Public St",
                    city = "Y",
                    state = "CA",
                    zipcode = "90000",
                    homeType = "single_family",
                    visibility = "public",
                    description = null,
                    createdAt = "2025-01-01T00:00:00Z",
                    hasVerifiedOwner = true,
                    verifiedOwner = null,
                    userMembershipStatus = "none",
                    userResidencyClaim = null,
                    memberCount = 2,
                    nearbyGigs = 5,
                ),
        )

    @Test
    fun private_detail_happy_path() =
        runTest {
            coEvery { repo.detail("h1") } returns NetworkResult.Success(detail())
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as HomeDashboardUiState.Loaded
            assertEquals("1 Main", loaded.content.address)
            assertTrue(loaded.content.verified)
            assertEquals(4, loaded.content.tabs.size)
        }

    @Test
    fun forbidden_falls_back_to_public_profile() =
        runTest {
            coEvery { repo.detail("h1") } returns NetworkResult.Failure(NetworkError.Forbidden)
            coEvery { repo.publicProfile("h1") } returns NetworkResult.Success(public_())
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as HomeDashboardUiState.Loaded
            assertEquals("200 Public St", loaded.content.address)
            assertTrue(loaded.content.verified)
        }

    @Test
    fun server_error_surfaces_error() =
        runTest {
            coEvery { repo.detail("h1") } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = makeVm()
            vm.load()
            assertTrue(vm.state.value is HomeDashboardUiState.Error)
        }

    @Test
    fun tab_selection_updates_state_flow() =
        runTest {
            coEvery { repo.detail("h1") } returns NetworkResult.Success(detail())
            val vm = makeVm()
            vm.load()
            vm.selectTab("members")
            assertEquals("members", vm.selectedTab.value)
        }
}
