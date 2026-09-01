package app.pantopus.android.ui.screens.compose.placepicker

import app.pantopus.android.data.api.models.geo.GeoNearbyPlacesResponse
import app.pantopus.android.data.api.models.geo.GeoPlace
import app.pantopus.android.data.api.models.geo.GeoPlaceCenter
import app.pantopus.android.data.api.models.geo.GeoPlaceSearchResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.location.LocationProvider
import app.pantopus.android.data.location.UserCoordinate
import app.pantopus.android.data.place.PlaceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
 * Coverage for [PlacePickerViewModel] — mirrors the iOS
 * `PlacePickerViewModelTests`: nearby load, search-only degradation,
 * debounce coalescing, min-query short-circuit, empty + error states,
 * and proximity biasing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlacePickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo: PlaceRepository = mockk()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        coordinate: UserCoordinate? = FIX,
        fixDelayMillis: Long = 0,
    ): PlacePickerViewModel = PlacePickerViewModel(repo, FakeLocationProvider(coordinate, fixDelayMillis))

    // MARK: - Nearby load

    @Test fun loadWithFixShowsNearbyAndLocality() =
        runTest {
            coEvery { repo.geoNearbyPlaces(FIX.latitude, FIX.longitude) } returns
                NetworkResult.Success(GeoNearbyPlacesResponse(places = listOf(POI), locality = LOCALITY))
            val vm = viewModel()
            vm.load()
            advanceUntilIdle()
            val state = vm.state.value as PlacePickerUiState.Loaded
            assertEquals(listOf(POI), state.nearby)
            assertEquals(LOCALITY, state.locality)
            assertFalse(vm.searchOnly.value)
        }

    @Test fun loadWithoutFixEntersSearchOnlyMode() =
        runTest {
            val vm = viewModel(coordinate = null)
            vm.load()
            advanceUntilIdle()
            assertTrue(vm.searchOnly.value)
            // Loaded (not Empty) — same render state as iOS's .loaded([], nil);
            // the sheet shows the search-only hint off the searchOnly flag.
            assertEquals(
                PlacePickerUiState.Loaded(nearby = emptyList(), locality = null),
                vm.state.value,
            )
            coVerify(exactly = 0) { repo.geoNearbyPlaces(any(), any()) }
        }

    @Test fun loadWithNoResultsStaysLoaded() =
        runTest {
            coEvery { repo.geoNearbyPlaces(any(), any()) } returns
                NetworkResult.Success(GeoNearbyPlacesResponse(places = emptyList(), locality = null))
            val vm = viewModel()
            vm.load()
            advanceUntilIdle()
            // The sheet renders "No places nearby" from the loaded state.
            assertEquals(
                PlacePickerUiState.Loaded(nearby = emptyList(), locality = null),
                vm.state.value,
            )
            assertFalse(vm.searchOnly.value)
        }

    @Test fun loadFailureSurfacesError() =
        runTest {
            coEvery { repo.geoNearbyPlaces(any(), any()) } returns
                NetworkResult.Failure(NetworkError.Server(500, "down"))
            val vm = viewModel()
            vm.load()
            advanceUntilIdle()
            assertTrue(vm.state.value is PlacePickerUiState.Error)
        }

    // MARK: - Search

    @Test fun searchDebounceCoalescesKeystrokes() =
        runTest {
            coEvery { repo.geoNearbyPlaces(any(), any()) } returns
                NetworkResult.Success(GeoNearbyPlacesResponse(places = listOf(POI), locality = LOCALITY))
            coEvery { repo.geoSearchPlaces("coffee", FIX.latitude, FIX.longitude) } returns
                NetworkResult.Success(GeoPlaceSearchResponse(places = listOf(POI)))
            val vm = viewModel()
            vm.load()
            advanceUntilIdle()
            // Three quick keystrokes — only the final query should fire.
            vm.onQueryChange("cof")
            vm.onQueryChange("coffe")
            vm.onQueryChange("coffee")
            advanceUntilIdle()
            coVerify(exactly = 1) { repo.geoSearchPlaces(any(), any(), any()) }
            coVerify(exactly = 1) { repo.geoSearchPlaces("coffee", FIX.latitude, FIX.longitude) }
            assertEquals(PlacePickerUiState.SearchResults(listOf(POI)), vm.state.value)
        }

    @Test fun shortQueryShortCircuitsToNearby() =
        runTest {
            coEvery { repo.geoNearbyPlaces(any(), any()) } returns
                NetworkResult.Success(GeoNearbyPlacesResponse(places = listOf(POI), locality = LOCALITY))
            val vm = viewModel()
            vm.load()
            advanceUntilIdle()
            vm.onQueryChange("c")
            advanceUntilIdle()
            coVerify(exactly = 0) { repo.geoSearchPlaces(any(), any(), any()) }
            assertTrue(vm.state.value is PlacePickerUiState.Loaded)
        }

    @Test fun clearingQueryRestoresNearbyWithoutRefetch() =
        runTest {
            coEvery { repo.geoNearbyPlaces(any(), any()) } returns
                NetworkResult.Success(GeoNearbyPlacesResponse(places = listOf(POI), locality = LOCALITY))
            coEvery { repo.geoSearchPlaces(any(), any(), any()) } returns
                NetworkResult.Success(GeoPlaceSearchResponse(places = listOf(POI)))
            val vm = viewModel()
            vm.load()
            advanceUntilIdle()
            vm.onQueryChange("coffee")
            advanceUntilIdle()
            vm.onQueryChange("")
            advanceUntilIdle()
            assertTrue(vm.state.value is PlacePickerUiState.Loaded)
            coVerify(exactly = 1) { repo.geoNearbyPlaces(any(), any()) }
        }

    @Test fun emptySearchResultsShowEmpty() =
        runTest {
            coEvery { repo.geoSearchPlaces("nowhere", null, null) } returns
                NetworkResult.Success(GeoPlaceSearchResponse(places = emptyList()))
            val vm = viewModel(coordinate = null)
            vm.load()
            advanceUntilIdle()
            // No fix — search still works, with null proximity coords.
            vm.onQueryChange("nowhere")
            advanceUntilIdle()
            assertEquals(PlacePickerUiState.Empty, vm.state.value)
            coVerify(exactly = 1) { repo.geoSearchPlaces("nowhere", null, null) }
        }

    @Test fun searchFailureSurfacesErrorAndRetryReruns() =
        runTest {
            coEvery { repo.geoSearchPlaces("coffee", null, null) } returnsMany
                listOf(
                    NetworkResult.Failure(NetworkError.Server(500, "down")),
                    NetworkResult.Success(GeoPlaceSearchResponse(places = listOf(POI))),
                )
            val vm = viewModel(coordinate = null)
            vm.load()
            advanceUntilIdle()
            vm.onQueryChange("coffee")
            advanceUntilIdle()
            assertTrue(vm.state.value is PlacePickerUiState.Error)
            vm.retry()
            advanceUntilIdle()
            assertEquals(PlacePickerUiState.SearchResults(listOf(POI)), vm.state.value)
        }

    @Test fun slowLoadDoesNotClobberActiveSearchResults() =
        runTest {
            // GPS fix takes 3s (virtual) — the user types meanwhile.
            coEvery { repo.geoNearbyPlaces(FIX.latitude, FIX.longitude) } returns
                NetworkResult.Success(GeoNearbyPlacesResponse(places = listOf(POI), locality = LOCALITY))
            coEvery { repo.geoSearchPlaces("coffee", null, null) } returns
                NetworkResult.Success(GeoPlaceSearchResponse(places = listOf(POI)))
            val vm = viewModel(coordinate = FIX, fixDelayMillis = 3_000)
            vm.load()
            vm.onQueryChange("coffee")
            advanceUntilIdle()
            // The late-finishing load cached nearby but left the live
            // search results on screen.
            assertEquals(PlacePickerUiState.SearchResults(listOf(POI)), vm.state.value)
            vm.onQueryChange("")
            assertEquals(
                PlacePickerUiState.Loaded(nearby = listOf(POI), locality = LOCALITY),
                vm.state.value,
            )
        }

    @Test fun shortQueryAfterFailedLoadKeepsErrorState() =
        runTest {
            coEvery { repo.geoNearbyPlaces(any(), any()) } returns
                NetworkResult.Failure(NetworkError.Server(500, "down"))
            val vm = viewModel()
            vm.load()
            advanceUntilIdle()
            assertTrue(vm.state.value is PlacePickerUiState.Error)
            // A 1-char query must not downgrade the Error card (and its
            // Retry) to Empty — nothing was cached to restore.
            vm.onQueryChange("c")
            advanceUntilIdle()
            assertTrue(vm.state.value is PlacePickerUiState.Error)
        }

    // MARK: - Tag mapping

    @Test fun postPlaceTagMapsPlaceFields() {
        val tag = PostPlaceTag(POI)
        assertEquals("Blue Bottle", tag.name)
        assertEquals("123 Main St", tag.address)
        assertEquals(45.52, tag.latitude, 0.0)
        assertEquals(-122.68, tag.longitude, 0.0)
        assertEquals("poi.123", tag.placeId)
        assertEquals("poi", tag.kind)
    }

    @Test fun postPlaceTagFallsBackToFullAddress() {
        val tag = PostPlaceTag(LOCALITY)
        assertEquals("Portland, Oregon, United States", tag.address)
        assertEquals("place", tag.kind)
    }

    private class FakeLocationProvider(
        private val coordinate: UserCoordinate?,
        private val delayMillis: Long = 0,
    ) : LocationProvider {
        override fun cachedCoordinate(): UserCoordinate? = coordinate

        override suspend fun requestCurrent(timeoutMillis: Long): UserCoordinate? {
            if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis)
            return coordinate
        }
    }

    private companion object {
        val FIX = UserCoordinate(latitude = 45.52, longitude = -122.68, accuracyMeters = 20.0)
        val POI =
            GeoPlace(
                placeId = "poi.123",
                name = "Blue Bottle",
                category = "coffee shop, cafe",
                address = "123 Main St",
                fullAddress = "123 Main St, Portland, Oregon",
                center = GeoPlaceCenter(lat = 45.52, lng = -122.68),
                kind = "poi",
                distanceM = 120.0,
            )
        val LOCALITY =
            GeoPlace(
                placeId = "place.456",
                name = "Portland",
                category = null,
                address = null,
                fullAddress = "Portland, Oregon, United States",
                center = GeoPlaceCenter(lat = 45.515, lng = -122.679),
                kind = "place",
                distanceM = null,
            )
    }
}
