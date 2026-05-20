@file:Suppress(
    "MagicNumber",
    "PackageNaming",
    "LongMethod",
    "MaxLineLength",
)

package app.pantopus.android.ui.screens.homes.calendar

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.homes.CalendarEventDto
import app.pantopus.android.data.api.models.homes.GetHomeEventsResponse
import app.pantopus.android.data.api.models.homes.OccupantDto
import app.pantopus.android.data.api.models.homes.OccupantsResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomeMembersRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the Event Detail VM (P2.7):
 *  - four-state shell transitions (loading → loaded / error / not-found),
 *  - attendee name lookup wiring,
 *  - delete happy path (callback fires).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailViewModelTest {
    private val repo: HomesRepository = mockk()
    private val membersRepo: HomeMembersRepository = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(): EventDetailViewModel =
        EventDetailViewModel(
            repo = repo,
            membersRepo = membersRepo,
            savedStateHandle =
                SavedStateHandle(
                    mapOf(
                        EVENT_DETAIL_HOME_ID_KEY to "home-1",
                        EVENT_DETAIL_EVENT_ID_KEY to "e1",
                    ),
                ),
        )

    private fun event(): CalendarEventDto =
        CalendarEventDto(
            id = "e1",
            homeId = "home-1",
            eventType = "social",
            title = "Soccer game",
            description = "Bring water",
            startAt = "2025-10-12T16:00:00Z",
            endAt = "2025-10-12T17:30:00Z",
            locationNotes = "Riverside Field 3",
            recurrenceRule = "FREQ=WEEKLY",
            assignedTo = listOf("u1", "u3"),
            alertsEnabled = true,
        )

    private fun occupants(): OccupantsResponse =
        OccupantsResponse(
            occupants =
                listOf(
                    OccupantDto(id = "o1", userId = "u1", isActive = true, displayName = "Maria Patel"),
                    OccupantDto(id = "o2", userId = "u2", isActive = true, displayName = "John Patel"),
                    OccupantDto(id = "o3", userId = "u3", isActive = true, displayName = "Ava Patel"),
                ),
            pendingInvites = emptyList(),
        )

    @Test fun initial_state_is_loading() {
        coEvery { repo.getHomeEvents(any(), any(), any()) } returns
            NetworkResult.Success(GetHomeEventsResponse(events = emptyList()))
        coEvery { membersRepo.listOccupants(any()) } returns NetworkResult.Success(occupants())
        val vm = makeVm()
        assertTrue(vm.state.value is EventDetailUiState.Loading)
    }

    @Test fun load_success_hydrates_event_and_attendee_names() =
        runTest {
            coEvery { repo.getHomeEvents("home-1", any(), any()) } returns
                NetworkResult.Success(GetHomeEventsResponse(events = listOf(event())))
            coEvery { membersRepo.listOccupants("home-1") } returns NetworkResult.Success(occupants())
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as EventDetailUiState.Loaded
            assertEquals("e1", loaded.event.id)
            assertEquals("Soccer game", loaded.event.title)
            assertEquals("Maria Patel", loaded.attendeeNames["u1"])
            assertEquals("Ava Patel", loaded.attendeeNames["u3"])
        }

    @Test fun load_failure_renders_error() =
        runTest {
            coEvery { repo.getHomeEvents(any(), any(), any()) } returns
                NetworkResult.Failure(NetworkError.Server(500, "boom"))
            coEvery { membersRepo.listOccupants(any()) } returns NetworkResult.Success(occupants())
            val vm = makeVm()
            vm.load()
            assertTrue(vm.state.value is EventDetailUiState.Error)
        }

    @Test fun load_event_not_found_surfaces_friendly_error() =
        runTest {
            coEvery { repo.getHomeEvents("home-1", any(), any()) } returns
                NetworkResult.Success(GetHomeEventsResponse(events = emptyList()))
            coEvery { membersRepo.listOccupants("home-1") } returns NetworkResult.Success(occupants())
            val vm = makeVm()
            vm.load()
            val err = vm.state.value as EventDetailUiState.Error
            assertEquals("This event is no longer available.", err.message)
        }

    @Test fun delete_success_invokes_callback() =
        runTest {
            coEvery { repo.getHomeEvents("home-1", any(), any()) } returns
                NetworkResult.Success(GetHomeEventsResponse(events = listOf(event())))
            coEvery { membersRepo.listOccupants("home-1") } returns NetworkResult.Success(occupants())
            coEvery { repo.deleteHomeEvent("home-1", "e1") } returns NetworkResult.Success(Unit)
            var fired = false
            val vm = makeVm()
            vm.configure(onDeleted = { fired = true })
            vm.load()
            vm.delete()
            assertTrue(fired)
        }

    @Test fun delete_failure_records_error_and_preserves_state() =
        runTest {
            coEvery { repo.getHomeEvents("home-1", any(), any()) } returns
                NetworkResult.Success(GetHomeEventsResponse(events = listOf(event())))
            coEvery { membersRepo.listOccupants("home-1") } returns NetworkResult.Success(occupants())
            coEvery { repo.deleteHomeEvent("home-1", "e1") } returns
                NetworkResult.Failure(NetworkError.Server(500, "nope"))
            val vm = makeVm()
            vm.load()
            vm.delete()
            val loaded = vm.state.value as EventDetailUiState.Loaded
            assertNotNull(loaded.deleteError)
        }

    @Test fun replace_loaded_swaps_in_place() =
        runTest {
            coEvery { repo.getHomeEvents("home-1", any(), any()) } returns
                NetworkResult.Success(GetHomeEventsResponse(events = listOf(event())))
            coEvery { membersRepo.listOccupants("home-1") } returns NetworkResult.Success(occupants())
            val vm = makeVm()
            vm.load()
            val renamed = event().copy(title = "Renamed")
            vm.replaceLoaded(renamed)
            val loaded = vm.state.value as EventDetailUiState.Loaded
            assertEquals("Renamed", loaded.event.title)
        }
}
