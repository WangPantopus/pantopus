@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.gigs

import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.models.gigs.GigsListResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.gigs.GigsRepository
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

/**
 * Mirrors the iOS [GigsFeedViewModelTests]: load → loaded/empty/error,
 * chip + sort each drive a refetch, and projection maps category +
 * bid-count correctly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GigsFeedViewModelTest {
    private val repo: GigsRepository = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun handymanGig(
        id: String = "g1",
        bidCount: Int = 4,
    ): GigDto =
        GigDto(
            id = id,
            title = "Hang 3 floating shelves in living room",
            description = "Need 3 IKEA Lack shelves mounted on drywall.",
            price = 60.0,
            category = "handyman",
            status = "open",
            createdAt = "2026-05-14T08:00:00Z",
            userId = "u1",
            bidCount = bidCount,
            distanceMiles = 0.2,
        )

    private fun cleaningGig(): GigDto =
        GigDto(
            id = "g2",
            title = "Deep clean 2BR apartment",
            description = "Kitchen, bath, baseboards.",
            price = 180.0,
            category = "cleaning",
            status = "open",
            createdAt = "2026-05-14T05:00:00Z",
            userId = "u2",
            bidCount = 0,
            distanceMiles = 0.5,
        )

    @Test fun load_with_gigs_transitions_loaded() =
        runTest {
            coEvery {
                repo.list(null, "newest", null, null, 1.0, 20, 0)
            } returns NetworkResult.Success(GigsListResponse(listOf(handymanGig(), cleaningGig()), 2))
            val vm = GigsFeedViewModel(repo)
            vm.load()
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(2, loaded.rows.size)
            assertEquals(GigsCategory.Handyman, loaded.rows[0].category)
            assertEquals(4, loaded.rows[0].bidCount)
            assertEquals("$60", loaded.rows[0].price)
            assertEquals(GigsCategory.Cleaning, loaded.rows[1].category)
            assertEquals(0, loaded.rows[1].bidCount)
        }

    @Test fun load_empty_transitions_empty_with_radius() =
        runTest {
            coEvery {
                repo.list(null, "newest", null, null, 2.0, 20, 0)
            } returns NetworkResult.Success(GigsListResponse(emptyList(), 0))
            val vm = GigsFeedViewModel(repo)
            vm.configureLocation(latitude = null, longitude = null, radiusMiles = 2.0)
            vm.load()
            val empty = vm.state.value as GigsFeedUiState.Empty
            assertEquals(2.0, empty.radiusMiles, 0.0)
        }

    @Test fun load_failure_transitions_error() =
        runTest {
            coEvery {
                repo.list(null, "newest", null, null, 1.0, 20, 0)
            } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = GigsFeedViewModel(repo)
            vm.load()
            assertTrue(vm.state.value is GigsFeedUiState.Error)
        }

    @Test fun select_category_refetches() =
        runTest {
            coEvery {
                repo.list(null, "newest", null, null, 1.0, 20, 0)
            } returns NetworkResult.Success(GigsListResponse(listOf(handymanGig(), cleaningGig()), 2))
            coEvery {
                repo.list("cleaning", "newest", null, null, 1.0, 20, 0)
            } returns NetworkResult.Success(GigsListResponse(listOf(cleaningGig()), 1))
            val vm = GigsFeedViewModel(repo)
            vm.load()
            vm.selectCategory(GigsCategory.Cleaning)
            assertEquals(GigsCategory.Cleaning, vm.activeCategory.value)
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(1, loaded.rows.size)
            assertEquals(GigsCategory.Cleaning, loaded.rows.first().category)
        }

    @Test fun select_sort_refetches() =
        runTest {
            coEvery {
                repo.list(null, "newest", null, null, 1.0, 20, 0)
            } returns NetworkResult.Success(GigsListResponse(listOf(handymanGig()), 1))
            coEvery {
                repo.list(null, "highest_pay", null, null, 1.0, 20, 0)
            } returns NetworkResult.Success(GigsListResponse(listOf(cleaningGig()), 1))
            val vm = GigsFeedViewModel(repo)
            vm.load()
            vm.selectSort(GigsSort.HighestPay)
            assertEquals(GigsSort.HighestPay, vm.activeSort.value)
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(GigsCategory.Cleaning, loaded.rows.first().category)
        }
}
