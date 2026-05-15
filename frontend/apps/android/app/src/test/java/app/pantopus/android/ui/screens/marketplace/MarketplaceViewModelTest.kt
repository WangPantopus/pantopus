@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.marketplace

import app.pantopus.android.data.api.models.listings.ListingDto
import app.pantopus.android.data.api.models.listings.ListingsNearbyResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.listings.ListingsRepository
import app.pantopus.android.data.location.LocationProvider
import app.pantopus.android.data.location.UserCoordinate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Mirrors the iOS MarketplaceViewModelTests: load → loaded/empty/error,
 * category chip drives a refetch, projection produces "Free" + suppresses
 * the condition badge for rentals/free, search submit refetches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarketplaceViewModelTest {
    private val repo: ListingsRepository = mockk()
    private val location: LocationProvider = mockk()
    private val center = UserCoordinate(40.7484, -73.9857, 50.0)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { location.cachedCoordinate() } returns center
        coEvery { location.requestCurrent(any()) } returns center
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun goodsListing(): ListingDto =
        ListingDto(
            id = "l1",
            title = "Mid-century sofa, walnut frame",
            price = 320.0,
            isFree = false,
            category = "furniture",
            condition = "like_new",
            status = "active",
            mediaUrls = emptyList(),
            firstImage = null,
            layer = "goods",
            listingType = "sell_item",
            distanceMeters = 644.0,
            createdAt = "2026-05-14T08:00:00Z",
        )

    private fun freeListing(): ListingDto =
        ListingDto(
            id = "l2",
            title = "Moving boxes — bundle of 18",
            price = 0.0,
            isFree = true,
            category = "free_stuff",
            condition = null,
            status = "active",
            layer = "goods",
            listingType = "free_item",
            distanceMeters = 160.0,
            createdAt = "2026-05-14T09:00:00Z",
        )

    private fun rentalListing(): ListingDto =
        ListingDto(
            id = "l3",
            title = "Peloton Bike+ (rental, week)",
            price = 45.0,
            isFree = false,
            category = "sports_outdoors",
            condition = "good",
            status = "active",
            layer = "rentals",
            listingType = "rent_sublet",
            distanceMeters = 1280.0,
            createdAt = "2026-05-13T08:00:00Z",
        )

    private fun makeVm(): MarketplaceViewModel = MarketplaceViewModel(repo, location)

    @Test fun load_with_listings_transitions_loaded() =
        runTest {
            coEvery {
                repo.nearby(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns NetworkResult.Success(ListingsNearbyResponse(listOf(goodsListing(), freeListing(), rentalListing())))
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as MarketplaceUiState.Loaded
            assertEquals(3, loaded.rows.size)
            val sofa = loaded.rows.first { it.id == "l1" }
            assertEquals("$320", sofa.price)
            assertEquals("Like new", sofa.conditionBadge)
            val freebie = loaded.rows.first { it.id == "l2" }
            assertEquals("Free", freebie.price)
            assertTrue(freebie.isFree)
            assertNull(freebie.conditionBadge)
            val rental = loaded.rows.first { it.id == "l3" }
            assertEquals("$45 / wk", rental.price)
            assertNull(rental.conditionBadge)
        }

    @Test fun load_empty_transitions_empty_with_radius() =
        runTest {
            coEvery {
                repo.nearby(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns NetworkResult.Success(ListingsNearbyResponse(emptyList()))
            val vm = makeVm()
            vm.configureRadius(5.0)
            vm.load()
            val empty = vm.state.value as MarketplaceUiState.Empty
            assertEquals(5.0, empty.radiusMiles, 0.0)
        }

    @Test fun load_failure_transitions_error() =
        runTest {
            coEvery {
                repo.nearby(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = makeVm()
            vm.load()
            assertTrue(vm.state.value is MarketplaceUiState.Error)
        }

    @Test fun select_category_refetches() =
        runTest {
            coEvery {
                repo.nearby(any(), any(), any(), null, null, any(), any(), any(), any())
            } returns NetworkResult.Success(ListingsNearbyResponse(listOf(goodsListing(), freeListing(), rentalListing())))
            coEvery {
                repo.nearby(any(), any(), any(), null, true, any(), any(), any(), any())
            } returns NetworkResult.Success(ListingsNearbyResponse(listOf(freeListing())))
            val vm = makeVm()
            vm.load()
            vm.selectCategory(MarketplaceCategory.Free)
            assertEquals(MarketplaceCategory.Free, vm.activeCategory.value)
            val loaded = vm.state.value as MarketplaceUiState.Loaded
            assertEquals(1, loaded.rows.size)
            assertTrue(loaded.rows.first().isFree)
        }

    @Test fun submit_search_refetches() =
        runTest {
            coEvery {
                repo.nearby(any(), any(), any(), any(), any(), null, any(), any(), any())
            } returns NetworkResult.Success(ListingsNearbyResponse(listOf(goodsListing())))
            coEvery {
                repo.nearby(any(), any(), any(), any(), any(), "bike", any(), any(), any())
            } returns NetworkResult.Success(ListingsNearbyResponse(listOf(rentalListing())))
            val vm = makeVm()
            vm.load()
            vm.setSearchText("bike")
            vm.submitSearch()
            val loaded = vm.state.value as MarketplaceUiState.Loaded
            assertEquals("l3", loaded.rows.first().id)
        }
}
