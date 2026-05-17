@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "LongParameterList")

package app.pantopus.android.ui.screens.homes.calendar

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.homes.CalendarEventDto
import app.pantopus.android.data.api.models.homes.GetHomeEventsResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.RowLeading
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Covers the Home calendar VM (T6.4c / P18):
 *  - four-state transitions (loading / empty / error / loaded),
 *  - section bucketing (Today / Tomorrow / per-day this-week / Next
 *    week / Later),
 *  - the selectedDate filter that the month-strip taps drive,
 *  - month-strip dot counts + today highlight derivation,
 *  - row mapping (category palette + chip + leading icon),
 *  - banner summary projection ("N events this week · Next …"),
 *  - week-shift navigation,
 *  - FAB intent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeCalendarViewModelTest {
    private val repo: HomesRepository = mockk()

    /** Sunday 2025-10-12 12:00 UTC — same anchor as the design fixtures. */
    private val fixedNow: Instant = Instant.parse("2025-10-12T12:00:00Z")
    private val zone: ZoneId = ZoneId.of("UTC")

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(): HomeCalendarViewModel =
        HomeCalendarViewModel(
            repo = repo,
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
        location: String? = null,
        rrule: String? = null,
        attendees: List<String>? = null,
    ) = CalendarEventDto(
        id = id,
        homeId = "home-1",
        eventType = type,
        title = title,
        startAt = start,
        endAt = end,
        locationNotes = location,
        recurrenceRule = rrule,
        assignedTo = attendees,
    )

    // ─── Four states ───────────────────────────────────────────

    @Test fun empty_response_renders_empty_state() =
        runTest {
            coEvery { repo.getHomeEvents(any(), any(), any()) } returns
                NetworkResult.Success(GetHomeEventsResponse(events = emptyList()))
            val vm = makeVm()
            vm.load()
            val state = vm.state.value
            assertTrue(state is ListOfRowsUiState.Empty)
            assertEquals("No events scheduled", (state as ListOfRowsUiState.Empty).headline)
            assertEquals("Add event", state.ctaTitle)
            // Banner is hidden on empty.
            assertNull(vm.banner.value)
        }

    @Test fun failure_renders_error_state() =
        runTest {
            coEvery { repo.getHomeEvents(any(), any(), any()) } returns
                NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = makeVm()
            vm.load()
            assertTrue(vm.state.value is ListOfRowsUiState.Error)
        }

    @Test fun loaded_response_buckets_today_section() =
        runTest {
            coEvery { repo.getHomeEvents(any(), any(), any()) } returns
                NetworkResult.Success(
                    GetHomeEventsResponse(
                        events =
                            listOf(
                                event(
                                    id = "e1",
                                    type = "trash",
                                    title = "Trash & recycling out",
                                    start = "2025-10-12T09:00:00Z",
                                    end = "2025-10-12T09:15:00Z",
                                    rrule = "FREQ=WEEKLY",
                                ),
                            ),
                    ),
                )
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as ListOfRowsUiState.Loaded
            assertEquals(1, loaded.sections.size)
            assertEquals("Today", loaded.sections[0].header)
            assertEquals(1, loaded.sections[0].rows.size)
            assertEquals("Trash & recycling out", loaded.sections[0].rows[0].title)
            val leading = loaded.sections[0].rows[0].leading as RowLeading.TypeIcon
            assertEquals(PantopusIcon.Trash2, leading.icon)
            assertNotNull(vm.banner.value)
        }

    // ─── Section bucketing across the week ─────────────────────

    @Test fun bucketing_distributes_events_across_today_tomorrow_thisweek_nextweek_later() =
        runTest {
            coEvery { repo.getHomeEvents(any(), any(), any()) } returns
                NetworkResult.Success(
                    GetHomeEventsResponse(
                        events =
                            listOf(
                                event(id = "today1", type = "trash", start = "2025-10-12T09:00:00Z"),
                                event(id = "tom1", type = "maintenance", start = "2025-10-13T10:00:00Z"),
                                event(id = "tue1", type = "birthday", start = "2025-10-14T00:00:00Z"),
                                event(id = "fri1", type = "school", start = "2025-10-17T16:00:00Z"),
                                event(id = "nw1", type = "social", start = "2025-10-20T18:00:00Z"),
                                event(id = "lt1", type = "delivery", start = "2025-11-02T12:00:00Z"),
                            ),
                    ),
                )
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as ListOfRowsUiState.Loaded
            val headers = loaded.sections.mapNotNull { it.header }
            assertTrue("Today" in headers)
            assertTrue("Tomorrow" in headers)
            assertTrue("Tue Oct 14" in headers)
            assertTrue("Fri Oct 17" in headers)
            assertTrue("Next week" in headers)
            assertTrue("Later" in headers)
        }

    // ─── selectedDate filter ──────────────────────────────────

    @Test fun select_day_filters_agenda_to_that_day() =
        runTest {
            coEvery { repo.getHomeEvents(any(), any(), any()) } returns
                NetworkResult.Success(
                    GetHomeEventsResponse(
                        events =
                            listOf(
                                event(id = "e1", type = "trash", title = "Trash", start = "2025-10-12T09:00:00Z"),
                                event(
                                    id = "e2",
                                    type = "birthday",
                                    title = "Mom's birthday",
                                    start = "2025-10-14T00:00:00Z",
                                ),
                            ),
                    ),
                )
            val vm = makeVm()
            vm.load()
            // Initially both rows are visible across two sections.
            val initialLoaded = vm.state.value as ListOfRowsUiState.Loaded
            assertEquals(2, initialLoaded.sections.sumOf { it.rows.size })

            // Tap Oct 14 → single "Tue Oct 14" section.
            vm.selectDay("2025-10-14")
            val filtered = vm.state.value as ListOfRowsUiState.Loaded
            assertEquals(1, filtered.sections.size)
            assertEquals("Tue Oct 14", filtered.sections[0].header)
            assertEquals(1, filtered.sections[0].rows.size)
            assertEquals("e2", filtered.sections[0].rows[0].id)
            assertEquals("2025-10-14", vm.monthStrip.value?.selectedIsoDate)

            // Tap the same day again → clears the filter.
            vm.selectDay("2025-10-14")
            val cleared = vm.state.value as ListOfRowsUiState.Loaded
            assertEquals(2, cleared.sections.sumOf { it.rows.size })
            assertNull(vm.monthStrip.value?.selectedIsoDate)
        }

    @Test fun selecting_day_with_no_events_renders_empty() =
        runTest {
            coEvery { repo.getHomeEvents(any(), any(), any()) } returns
                NetworkResult.Success(
                    GetHomeEventsResponse(
                        events =
                            listOf(
                                event(id = "e1", type = "trash", title = "Trash", start = "2025-10-12T09:00:00Z"),
                            ),
                    ),
                )
            val vm = makeVm()
            vm.load()
            // Thursday — no events.
            vm.selectDay("2025-10-16")
            val state = vm.state.value
            assertTrue(state is ListOfRowsUiState.Empty)
            assertEquals("Nothing on this day", (state as ListOfRowsUiState.Empty).headline)
        }

    @Test fun jump_to_today_clears_selection() =
        runTest {
            coEvery { repo.getHomeEvents(any(), any(), any()) } returns
                NetworkResult.Success(
                    GetHomeEventsResponse(
                        events =
                            listOf(
                                event(id = "e1", type = "trash", title = "Trash", start = "2025-10-12T09:00:00Z"),
                            ),
                    ),
                )
            val vm = makeVm()
            vm.load()
            vm.selectDay("2025-10-14")
            assertEquals("2025-10-14", vm.monthStrip.value?.selectedIsoDate)
            vm.jumpToToday()
            assertNull(vm.monthStrip.value?.selectedIsoDate)
            assertEquals("2025-10-12", vm.monthStrip.value?.todayIsoDate)
        }

    // ─── Month-strip dot count + week shift ───────────────────

    @Test fun month_strip_renders_dots_per_day() =
        runTest {
            coEvery { repo.getHomeEvents(any(), any(), any()) } returns
                NetworkResult.Success(
                    GetHomeEventsResponse(
                        events =
                            listOf(
                                event(id = "e1", type = "trash", start = "2025-10-12T09:00:00Z"),
                                event(id = "e2", type = "family", start = "2025-10-12T16:00:00Z"),
                                event(id = "e3", type = "social", start = "2025-10-12T18:30:00Z"),
                                event(id = "e4", type = "maintenance", start = "2025-10-13T10:00:00Z"),
                                event(id = "e5", type = "birthday", start = "2025-10-14T00:00:00Z"),
                                event(id = "e6", type = "delivery", start = "2025-10-14T20:00:00Z"),
                            ),
                    ),
                )
            val vm = makeVm()
            vm.load()
            val strip = vm.monthStrip.value
            assertNotNull(strip)
            assertEquals("October 2025", strip!!.monthLabel)
            assertEquals(7, strip.days.size)
            assertEquals("2025-10-12", strip.days[0].id)
            assertEquals(3, strip.days[0].eventCount)
            assertEquals(1, strip.days[1].eventCount)
            assertEquals(2, strip.days[2].eventCount)
            assertEquals(0, strip.days[3].eventCount)
            assertEquals("2025-10-12", strip.todayIsoDate)
            assertNull(strip.selectedIsoDate)
        }

    @Test fun shift_week_rolls_anchor_by_seven_days() =
        runTest {
            coEvery { repo.getHomeEvents(any(), any(), any()) } returns
                NetworkResult.Success(GetHomeEventsResponse(events = emptyList()))
            val vm = makeVm()
            vm.load()
            // Empty state still produces a month strip pinned to today.
            assertEquals("2025-10-12", vm.monthStrip.value?.days?.first()?.id)
            vm.shiftWeek(HomeCalendarViewModel.WeekShift.Next)
            assertEquals("2025-10-19", vm.monthStrip.value?.days?.first()?.id)
            vm.shiftWeek(HomeCalendarViewModel.WeekShift.Previous)
            vm.shiftWeek(HomeCalendarViewModel.WeekShift.Previous)
            assertEquals("2025-10-05", vm.monthStrip.value?.days?.first()?.id)
        }

    // ─── Row mapping ──────────────────────────────────────────

    @Test fun row_mapping_uses_category_palette() =
        runTest {
            coEvery { repo.getHomeEvents(any(), any(), any()) } returns
                NetworkResult.Success(
                    GetHomeEventsResponse(
                        events =
                            listOf(
                                event(
                                    id = "soccer",
                                    type = "family",
                                    title = "Soccer game · Ava",
                                    start = "2025-10-12T16:00:00Z",
                                    end = "2025-10-12T17:30:00Z",
                                    location = "Riverside Field 3",
                                    attendees = listOf("m1", "m2", "m3"),
                                ),
                            ),
                    ),
                )
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as ListOfRowsUiState.Loaded
            val row = loaded.sections[0].rows[0]
            assertEquals("Soccer game · Ava", row.title)
            val leading = row.leading as RowLeading.TypeIcon
            assertEquals(PantopusIcon.UsersRound, leading.icon)
            assertEquals("4:00 PM", row.timeMeta)
            assertEquals(2, row.chips?.size)
            assertEquals("Family", row.chips?.firstOrNull()?.text)
            assertEquals("3 attendees", row.chips?.lastOrNull()?.text)
        }

    // ─── Category inference helper ────────────────────────────

    @Test fun category_inference_falls_back_to_generic_for_unknown_type() {
        assertEquals(CalendarEventCategory.Generic, CalendarEventCategory.from("qq_unknown"))
        assertEquals(CalendarEventCategory.Generic, CalendarEventCategory.from(null))
        assertEquals(CalendarEventCategory.Pet, CalendarEventCategory.from("vet_visit"))
        assertEquals(CalendarEventCategory.Trash, CalendarEventCategory.from("trash_day"))
        assertEquals(CalendarEventCategory.Birthday, CalendarEventCategory.from("birthday_party"))
        assertEquals(CalendarEventCategory.Delivery, CalendarEventCategory.from("delivery"))
        assertEquals(CalendarEventCategory.Generic, CalendarEventCategory.from("general"))
    }

    // ─── Banner summary projection ────────────────────────────

    @Test fun summarize_counts_events_in_active_week() {
        val now = Instant.parse("2025-10-12T12:00:00Z")
        val events =
            listOf(
                parsed(id = "today1", type = "trash", start = "2025-10-12T09:00:00Z"),
                parsed(id = "tom1", type = "maintenance", start = "2025-10-13T10:00:00Z"),
                parsed(id = "tue1", type = "birthday", start = "2025-10-14T00:00:00Z"),
                // next Sunday — should not be in active week.
                parsed(id = "nw1", type = "social", start = "2025-10-19T19:00:00Z"),
            )
        val summary = HomeCalendarViewModel.summarize(events, now, zone)
        assertEquals(3, summary.count)
        assertNotNull(summary.nextLabel)
    }

    private fun parsed(
        id: String,
        type: String,
        start: String,
    ): ParsedCalendarEvent {
        val instant = Instant.parse(start)
        return ParsedCalendarEvent(
            dto =
                CalendarEventDto(
                    id = id,
                    homeId = "home-1",
                    eventType = type,
                    title = id,
                    startAt = start,
                ),
            start = instant,
            isoDate = instant.atZone(zone).toLocalDate().toString(),
        )
    }
}
