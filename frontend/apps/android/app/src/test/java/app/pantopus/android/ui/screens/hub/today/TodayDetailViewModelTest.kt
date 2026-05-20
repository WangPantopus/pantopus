@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.hub.today

import app.pantopus.android.data.api.models.homes.CalendarEventDto
import app.pantopus.android.data.api.models.hub.HubTodayResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.hub.HubRepository
import app.pantopus.android.ui.theme.PantopusIcon
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
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TodayDetailViewModelTest {
    private val hubRepo: HubRepository = mockk()
    private val homesRepo: HomesRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = TodayDetailViewModel(hubRepo, homesRepo)

    // MARK: - Four states

    @Test
    fun loadErrorWhenTodayFails() =
        runTest {
            coEvery { hubRepo.today() } returns NetworkResult.Failure(NetworkError.Server(500, "boom"))
            val viewModel = vm()
            viewModel.load()
            assertTrue(viewModel.state.value is TodayDetailViewModel.UiState.Error)
        }

    @Test
    fun loadEmptyWhenNoContext() =
        runTest {
            coEvery { hubRepo.today() } returns NetworkResult.Success(HubTodayResponse(today = null, error = null))
            coEvery { hubRepo.overview() } returns NetworkResult.Failure(NetworkError.Server(500, "x"))
            val viewModel = vm()
            viewModel.load()
            assertTrue(viewModel.state.value is TodayDetailViewModel.UiState.Empty)
        }

    @Test
    fun loadLoadedWithWeather() =
        runTest {
            coEvery { hubRepo.today() } returns
                NetworkResult.Success(
                    HubTodayResponse(
                        today = mapOf("weather" to mapOf("temperatureF" to 72, "conditions" to "Sunny")),
                        error = null,
                    ),
                )
            coEvery { hubRepo.overview() } returns NetworkResult.Failure(NetworkError.Server(500, "x"))
            val viewModel = vm()
            viewModel.load()
            val state = viewModel.state.value
            assertTrue(state is TodayDetailViewModel.UiState.Loaded)
            assertEquals(72, (state as TodayDetailViewModel.UiState.Loaded).content.temperatureFahrenheit)
        }

    // MARK: - Pure projections

    @Test
    fun projectTodayExtractsFields() {
        val response =
            HubTodayResponse(
                today =
                    mapOf(
                        "weather" to mapOf("temperatureF" to 58, "conditions" to "Cloudy"),
                        "aqi" to mapOf("label" to "Moderate", "value" to 80),
                        "commute" to mapOf("label" to "20 min"),
                    ),
                error = null,
            )
        val projection = TodayDetailViewModel.projectToday(response)
        assertEquals(58, projection.temperatureFahrenheit)
        assertEquals("Cloudy", projection.conditions)
        assertEquals("Moderate", projection.aqiLabel)
        assertEquals(80, projection.aqiValue)
        assertEquals("20 min", projection.commute)
    }

    @Test
    fun projectTodayNullPayload() {
        val projection = TodayDetailViewModel.projectToday(HubTodayResponse(today = null, error = null))
        assertEquals(null, projection.temperatureFahrenheit)
        assertEquals(null, projection.aqiLabel)
        assertEquals(null, projection.commute)
    }

    @Test
    fun todaysEventsFiltersToTodayAndSorts() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-05-20T12:00:00Z")
        val events =
            listOf(
                event("e1", "social", "2026-05-20T16:00:00Z"),
                event("e2", "chore", "2026-05-20T09:00:00Z"),
                event("e3", "repair", "2026-05-21T10:00:00Z"),
            )
        val rows = TodayDetailViewModel.todaysEvents(events, now, zone)
        assertEquals(listOf("e2", "e1"), rows.map { it.id })
        assertEquals("Chore", rows.first().typeLabel)
    }

    @Test
    fun eventIconMapping() {
        assertEquals(PantopusIcon.Hammer, TodayDetailViewModel.iconFor("repair"))
        assertEquals(PantopusIcon.PawPrint, TodayDetailViewModel.iconFor("pet"))
        assertEquals(PantopusIcon.CalendarDays, TodayDetailViewModel.iconFor("social"))
    }

    private fun event(
        id: String,
        type: String,
        start: String,
    ): CalendarEventDto =
        CalendarEventDto(
            id = id,
            homeId = "h",
            eventType = type,
            title = "Event $id",
            startAt = start,
        )
}
