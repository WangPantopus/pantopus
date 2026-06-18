@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "LongParameterList")

package app.pantopus.android.ui.screens.homes.calendar

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.homes.CalendarEventDto
import app.pantopus.android.data.api.models.homes.GetHomeEventsResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.homes.HomeMembersRepository
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.network.NetworkMonitor
import io.mockk.coEvery
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Covers the F1 Home calendar VM (A10):
 *  - four states (loading → loaded / empty / error),
 *  - the booking **union** mapping (`source:'booking'` rows are read-only,
 *    expose `bookingId`, and route to the Booking Detail route),
 *  - the member filter, day filter, month strip, and week shift.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeCalendarViewModelTest {
    private val repo: HomesRepository = mockk()
    private val membersRepo: HomeMembersRepository = mockk()
    private val authRepository: AuthRepository = mockk()
    private val networkMonitor: NetworkMonitor = mockk()

    /** Sunday 2025-10-12 12:00 UTC. */
    private val fixedNow: Instant = Instant.parse("2025-10-12T12:00:00Z")
    private val zone: ZoneId = ZoneId.of("UTC")

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { authRepository.state } returns MutableStateFlow<AuthRepository.State>(AuthRepository.State.SignedOut)
        every { networkMonitor.isOnline } returns MutableStateFlow(true)
        coEvery { membersRepo.listOccupants(any()) } returns NetworkResult.Failure(NetworkError.Server(500, null))
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(): HomeCalendarViewModel =
        HomeCalendarViewModel(
            repo = repo,
            membersRepo = membersRepo,
            authRepository = authRepository,
            networkMonitor = networkMonitor,
            savedStateHandle = SavedStateHandle(mapOf(HOME_CALENDAR_HOME_ID_KEY to "home-1")),
            clock = { fixedNow },
            zone = zone,
        )

    private fun event(
        id: String = "e",
        type: String = "general",
        title: String = "Untitled",
        start: String,
        end: String? = null,
        attendees: List<String>? = null,
        source: String? = null,
        bookingId: String? = null,
        bookingStatus: String? = null,
    ) = CalendarEventDto(
        id = id,
        homeId = "home-1",
        eventType = type,
        title = title,
        startAt = start,
        endAt = end,
        assignedTo = attendees,
        source = source,
        bookingId = bookingId,
        bookingStatus = bookingStatus,
    )

    private fun stubEvents(vararg events: CalendarEventDto) {
        coEvery { repo.getHomeEvents(any(), any(), any()) } returns
            NetworkResult.Success(GetHomeEventsResponse(events = events.toList()))
    }

    @Test fun empty_response_renders_first_run_empty() =
        runTest {
            stubEvents()
            val vm = makeVm()
            vm.load()
            val state = vm.state.value as HomeCalendarUiState.Loaded
            assertEquals(AgendaEmpty.FirstRun, state.empty)
        }

    @Test fun failure_renders_error_state() =
        runTest {
            coEvery { repo.getHomeEvents(any(), any(), any()) } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = makeVm()
            vm.load()
            assertTrue(vm.state.value is HomeCalendarUiState.Error)
        }

    @Test fun loaded_buckets_today_section() =
        runTest {
            stubEvents(event(id = "e1", type = "trash", title = "Trash out", start = "2025-10-12T09:00:00Z"))
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as HomeCalendarUiState.Loaded
            assertNull(loaded.empty)
            assertEquals(1, loaded.sections.size)
            assertEquals("Today · Sun Oct 12", loaded.sections[0].header)
            assertEquals("Trash out", loaded.sections[0].items[0].title)
            assertEquals(CalendarEventCategory.Trash, loaded.sections[0].items[0].category)
        }

    @Test fun booking_union_row_is_read_only_and_routes_to_booking_detail() =
        runTest {
            stubEvents(
                event(id = "ev1", type = "chore", title = "Trash out", start = "2025-10-12T08:00:00Z"),
                event(
                    id = "bk1",
                    type = "appointment",
                    title = "Plumber",
                    start = "2025-10-12T17:00:00Z",
                    source = "booking",
                    bookingId = "booking-77",
                    bookingStatus = "pending",
                ),
            )
            val vm = makeVm()
            var navigated: String? = null
            vm.configureNavigation(onNavigate = { navigated = it })
            vm.load()
            val loaded = vm.state.value as HomeCalendarUiState.Loaded
            val booking = loaded.sections.flatMap { it.items }.first { it.isBooking }
            assertEquals("booking-77", booking.bookingId)
            assertEquals("pending", booking.bookingStatus)
            assertNull("booking rows never expose an event id", booking.eventId)
            vm.openAgendaItem(booking)
            assertEquals("scheduling/bookings/booking-77", navigated)
        }

    @Test fun event_row_opens_event_detail() =
        runTest {
            stubEvents(event(id = "ev1", type = "chore", title = "Trash out", start = "2025-10-12T08:00:00Z"))
            val vm = makeVm()
            var openedEventId: String? = null
            vm.configureNavigation(onOpenEvent = { openedEventId = it })
            vm.load()
            val item = (vm.state.value as HomeCalendarUiState.Loaded).sections.first().items.first()
            vm.openAgendaItem(item)
            assertEquals("ev1", openedEventId)
        }

    @Test fun member_filter_scopes_agenda_to_one_member() =
        runTest {
            stubEvents(
                event(id = "mine", type = "chore", title = "Dishes", start = "2025-10-12T09:00:00Z", attendees = listOf("u1")),
                event(id = "theirs", type = "chore", title = "Mow lawn", start = "2025-10-12T10:00:00Z", attendees = listOf("u2")),
            )
            val vm = makeVm()
            vm.load()
            vm.selectFilter(MemberFilter.Member("u1", "Alex"))
            val filtered = vm.state.value as HomeCalendarUiState.Loaded
            assertEquals(1, filtered.sections.sumOf { it.items.size })
            assertEquals("Dishes", filtered.sections.first().items.first().title)

            vm.selectFilter(MemberFilter.Member("nobody", "Nobody"))
            val empty = vm.state.value as HomeCalendarUiState.Loaded
            assertEquals(AgendaEmpty.FilteredMember("Nobody"), empty.empty)

            vm.clearMemberFilter()
            val all = vm.state.value as HomeCalendarUiState.Loaded
            assertEquals(2, all.sections.sumOf { it.items.size })
        }

    @Test fun select_day_filters_then_clears() =
        runTest {
            stubEvents(
                event(id = "e1", type = "trash", title = "Trash", start = "2025-10-12T09:00:00Z"),
                event(id = "e2", type = "birthday", title = "Mom's birthday", start = "2025-10-14T00:00:00Z"),
            )
            val vm = makeVm()
            vm.load()
            vm.selectDay("2025-10-14")
            val filtered = vm.state.value as HomeCalendarUiState.Loaded
            assertEquals(1, filtered.sections.sumOf { it.items.size })
            assertEquals("e2", filtered.sections.first().items.first().id)
            assertEquals("2025-10-14", vm.monthStrip.value?.selectedIsoDate)

            vm.selectDay("2025-10-14")
            val cleared = vm.state.value as HomeCalendarUiState.Loaded
            assertEquals(2, cleared.sections.sumOf { it.items.size })
            assertNull(vm.monthStrip.value?.selectedIsoDate)
        }

    @Test fun month_strip_dot_counts_and_week_shift() =
        runTest {
            stubEvents(
                event(id = "e1", type = "trash", start = "2025-10-12T09:00:00Z"),
                event(id = "e2", type = "family", start = "2025-10-12T16:00:00Z"),
                event(id = "e3", type = "maintenance", start = "2025-10-13T10:00:00Z"),
            )
            val vm = makeVm()
            vm.load()
            val strip = vm.monthStrip.value!!
            assertEquals("October 2025", strip.monthLabel)
            assertEquals(7, strip.days.size)
            assertEquals("2025-10-12", strip.days[0].id)
            assertEquals(2, strip.days[0].eventCount)
            assertEquals(1, strip.days[1].eventCount)
            assertEquals("2025-10-12", strip.todayIsoDate)

            vm.shiftWeek(HomeCalendarViewModel.WeekShift.Next)
            assertEquals("2025-10-19", vm.monthStrip.value?.days?.first()?.id)
            vm.shiftWeek(HomeCalendarViewModel.WeekShift.Previous)
            vm.shiftWeek(HomeCalendarViewModel.WeekShift.Previous)
            assertEquals("2025-10-05", vm.monthStrip.value?.days?.first()?.id)
        }

    @Test fun category_inference_falls_back_to_generic_for_unknown_type() {
        assertEquals(CalendarEventCategory.Generic, CalendarEventCategory.from("qq_unknown"))
        assertEquals(CalendarEventCategory.Generic, CalendarEventCategory.from(null))
        assertEquals(CalendarEventCategory.Pet, CalendarEventCategory.from("vet_visit"))
        assertEquals(CalendarEventCategory.Trash, CalendarEventCategory.from("trash_day"))
        assertEquals(CalendarEventCategory.Meal, CalendarEventCategory.from("family_dinner"))
        assertNotNull(CalendarEventCategory.from("delivery"))
    }
}
