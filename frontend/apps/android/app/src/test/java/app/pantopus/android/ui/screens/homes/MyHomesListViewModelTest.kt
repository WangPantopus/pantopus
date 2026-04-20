package app.pantopus.android.ui.screens.homes

import app.cash.turbine.test
import app.pantopus.android.data.api.models.homes.HomeOccupancy
import app.pantopus.android.data.api.models.homes.MyHome
import app.pantopus.android.data.api.models.homes.MyHomesResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
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
class MyHomesListViewModelTest {
    private val repo: HomesRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeHome(
        id: String,
        name: String? = "Main",
        city: String? = "X",
        ownership: String? = "verified",
    ) = MyHome(
        id = id,
        name = name,
        address = "1 Main",
        city = city,
        state = "CA",
        zipcode = "90000",
        homeType = "single_family",
        visibility = "public",
        description = null,
        createdAt = null,
        updatedAt = null,
        occupancy = null as HomeOccupancy?,
        ownershipStatus = ownership,
        verificationTier = "attom",
        isPrimaryOwner = true,
        pendingClaimId = null,
    )

    @Test
    fun happy_path_emits_loaded_rows() =
        runTest {
            coEvery { repo.myHomes() } returns
                NetworkResult.Success(
                    MyHomesResponse(homes = listOf(makeHome("h1")), message = null),
                )
            val vm = MyHomesListViewModel(repo)
            vm.state.test {
                assertEquals(ListOfRowsUiState.Loading, awaitItem())
                vm.load()
                val loaded = awaitItem() as ListOfRowsUiState.Loaded
                assertEquals(1, loaded.sections.first().rows.size)
                assertEquals("Main", loaded.sections.first().rows.first().title)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun empty_response_surfaces_empty_state() =
        runTest {
            coEvery { repo.myHomes() } returns NetworkResult.Success(MyHomesResponse(homes = emptyList(), message = null))
            val vm = MyHomesListViewModel(repo)
            vm.state.test {
                awaitItem() // Loading
                vm.load()
                val empty = awaitItem() as ListOfRowsUiState.Empty
                assertEquals("No homes claimed yet", empty.headline)
                assertEquals("Claim a home", empty.ctaTitle)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun failure_surfaces_error_state() =
        runTest {
            coEvery { repo.myHomes() } returns NetworkResult.Failure(NetworkError.NotFound)
            val vm = MyHomesListViewModel(repo)
            vm.state.test {
                awaitItem()
                vm.load()
                assertTrue(awaitItem() is ListOfRowsUiState.Error)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun empty_state_cta_fires_onAddHome() =
        runTest {
            coEvery { repo.myHomes() } returns NetworkResult.Success(MyHomesResponse(homes = emptyList(), message = null))
            var added = false
            val vm =
                MyHomesListViewModel(repo).apply {
                    configureNavigation(onOpenHome = {}, onAddHome = { added = true })
                }
            vm.load()
            val empty = vm.state.value as ListOfRowsUiState.Empty
            empty.onCta?.invoke()
            assertTrue(added)
        }
}
