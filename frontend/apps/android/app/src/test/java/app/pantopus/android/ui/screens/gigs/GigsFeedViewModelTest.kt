@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.gigs

import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.models.gigs.GigsListResponse
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

    @Test fun apply_budget_filter_refetches_with_price_params() =
        runTest {
            coEvery {
                repo.list(null, "newest", null, null, 1.0, 20, 0)
            } returns NetworkResult.Success(GigsListResponse(listOf(handymanGig(), cleaningGig()), 2))
            // P0.4 — budget pushes minPrice/maxPrice server-side on refetch.
            coEvery {
                repo.list(null, "newest", null, null, 1.0, 20, 0, maxPrice = 100.0)
            } returns NetworkResult.Success(GigsListResponse(listOf(handymanGig()), 1))
            val vm = GigsFeedViewModel(repo)
            vm.load()
            // handyman is $60, cleaning is $180 → a $0–$100 budget keeps only the first.
            vm.applyFilters(GigFilterCriteria(budgetLower = 0f, budgetUpper = 100f))
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(listOf("g1"), loaded.rows.map { it.id })
            assertEquals(1, vm.activeFilterCount.value)
            coVerify(exactly = 1) {
                repo.list(null, "newest", null, null, 1.0, 20, 0, maxPrice = 100.0)
            }
        }

    @Test fun apply_filters_maps_every_server_expressible_dimension() =
        runTest {
            coEvery {
                repo.list(null, "newest", null, null, 1.0, 20, 0)
            } returns NetworkResult.Success(GigsListResponse(listOf(handymanGig(), cleaningGig()), 2))
            val scheduledOpenGig = handymanGig().copy(scheduleType = "scheduled")
            coEvery {
                repo.list(
                    null,
                    "newest",
                    null,
                    null,
                    1.0,
                    20,
                    0,
                    minPrice = 50.0,
                    maxPrice = 100.0,
                    scheduleType = "scheduled",
                    payType = "offers",
                )
            } returns NetworkResult.Success(GigsListResponse(listOf(scheduledOpenGig), 1))
            val vm = GigsFeedViewModel(repo)
            vm.load()
            vm.applyFilters(
                GigFilterCriteria(
                    budgetLower = 50f,
                    budgetUpper = 100f,
                    schedules = setOf(GigScheduleFilter.OneTime),
                    openToBids = true,
                ),
            )
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(listOf("g1"), loaded.rows.map { it.id })
            coVerify(exactly = 1) {
                repo.list(
                    null,
                    "newest",
                    null,
                    null,
                    1.0,
                    20,
                    0,
                    minPrice = 50.0,
                    maxPrice = 100.0,
                    scheduleType = "scheduled",
                    payType = "offers",
                )
            }
        }

    @Test fun multi_schedule_selection_stays_client_side() =
        runTest {
            // Two schedule buckets can't ride the single-value backend
            // param — the refetch carries no schedule_type and the
            // intersection happens client-side.
            coEvery {
                repo.list(null, "newest", null, null, 1.0, 20, 0)
            } returns
                NetworkResult.Success(
                    GigsListResponse(
                        listOf(
                            handymanGig().copy(scheduleType = "scheduled"),
                            cleaningGig().copy(scheduleType = "recurring"),
                        ),
                        2,
                    ),
                )
            val vm = GigsFeedViewModel(repo)
            vm.load()
            vm.applyFilters(
                GigFilterCriteria(schedules = setOf(GigScheduleFilter.OneTime, GigScheduleFilter.Flexible)),
            )
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(listOf("g1"), loaded.rows.map { it.id })
        }

    @Test fun apply_filter_with_no_matches_falls_to_empty() =
        runTest {
            coEvery {
                repo.list(null, "newest", null, null, 1.0, 20, 0)
            } returns NetworkResult.Success(GigsListResponse(listOf(handymanGig(), cleaningGig()), 2))
            val vm = GigsFeedViewModel(repo)
            vm.load()
            vm.applyFilters(GigFilterCriteria(categories = setOf(GigsCategory.Tech)))
            assertTrue(vm.state.value is GigsFeedUiState.Empty)
        }

    @Test fun resetting_filters_restores_full_list() =
        runTest {
            coEvery {
                repo.list(null, "newest", null, null, 1.0, 20, 0)
            } returns NetworkResult.Success(GigsListResponse(listOf(handymanGig(), cleaningGig()), 2))
            val vm = GigsFeedViewModel(repo)
            vm.load()
            vm.applyFilters(GigFilterCriteria(categories = setOf(GigsCategory.Tech)))
            vm.applyFilters(GigFilterCriteria())
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(2, loaded.rows.size)
            assertEquals(0, vm.activeFilterCount.value)
        }
}
