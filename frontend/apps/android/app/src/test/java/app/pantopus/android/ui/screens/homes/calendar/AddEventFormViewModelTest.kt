@file:Suppress(
    "MagicNumber",
    "PackageNaming",
    "LongMethod",
    "LongParameterList",
    "MaxLineLength",
)

package app.pantopus.android.ui.screens.homes.calendar

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.homes.CalendarEventDto
import app.pantopus.android.data.api.models.homes.CreateHomeEventRequest
import app.pantopus.android.data.api.models.homes.GetHomeEventsResponse
import app.pantopus.android.data.api.models.homes.HomeEventResponse
import app.pantopus.android.data.api.models.homes.OccupantDto
import app.pantopus.android.data.api.models.homes.OccupantsResponse
import app.pantopus.android.data.api.models.homes.UpdateHomeEventRequest
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomeMembersRepository
import app.pantopus.android.data.homes.HomesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Covers the Add / Edit Event form VM (P2.7):
 *  - empty pose,
 *  - validation (title required, end ≥ start, all-day clears end),
 *  - attendee multi-pick,
 *  - recurrence ↔ RRULE round-trip,
 *  - create + edit submit (POST / PUT happy paths),
 *  - edit-mode hydration from `CalendarEventDto`,
 *  - all-day hydration on edit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddEventFormViewModelTest {
    private val repo: HomesRepository = mockk(relaxed = true)
    private val membersRepo: HomeMembersRepository = mockk()

    /** Sunday 2025-10-12 12:00 UTC — same anchor as the design fixtures. */
    private val fixedNow: Instant = Instant.parse("2025-10-12T12:00:00Z")
    private val zone: ZoneId = ZoneId.of("UTC")

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(
        eventId: String? = null,
        prefilledCategory: String? = null,
    ): AddEventFormViewModel {
        val handle =
            SavedStateHandle(
                buildMap {
                    put(ADD_EVENT_HOME_ID_KEY, "home-1")
                    if (eventId != null) put(ADD_EVENT_EVENT_ID_KEY, eventId)
                    if (prefilledCategory != null) put(ADD_EVENT_PREFILLED_CATEGORY_KEY, prefilledCategory)
                },
            )
        return AddEventFormViewModel(
            repo = repo,
            membersRepo = membersRepo,
            savedStateHandle = handle,
            clock = { fixedNow },
            zone = zone,
        )
    }

    private fun stubOccupants(occupants: List<OccupantDto>) {
        coEvery { membersRepo.listOccupants("home-1") } returns
            NetworkResult.Success(OccupantsResponse(occupants = occupants, pendingInvites = emptyList()))
    }

    private fun stubLoadEvents(events: List<CalendarEventDto>) {
        coEvery { repo.getHomeEvents("home-1", any(), any()) } returns
            NetworkResult.Success(GetHomeEventsResponse(events = events))
    }

    private fun occupant(
        userId: String,
        name: String,
    ): OccupantDto =
        OccupantDto(
            id = "o-$userId",
            userId = userId,
            isActive = true,
            displayName = name,
            username = name.lowercase(),
        )

    // MARK: - Empty pose

    @Test fun initial_state_is_clean_invalid_and_empty() =
        runTest {
            stubOccupants(emptyList())
            val vm = makeVm()
            val state = vm.state.value
            assertFalse(state.isDirty)
            assertFalse(state.isValid)
            assertEquals(CalendarEventCategory.Generic, state.category)
            assertFalse(state.allDay)
            assertNull(state.endDate)
            assertEquals(AddEventRecurrence.None, state.recurrence)
            assertEquals(AddEventReminder.None, state.reminder)
            assertTrue(state.selectedAttendeeIds.isEmpty())
            assertEquals("Add event", state.title)
            assertEquals("Add", state.commitLabel)
        }

    // MARK: - Validation

    @Test fun title_required_gates_submit() {
        val vm = makeVm()
        assertFalse(vm.state.value.isValid)
        vm.updateField(AddEventField.Title, "   ")
        assertFalse(vm.state.value.isValid)
        vm.updateField(AddEventField.Title, "Soccer game")
        assertTrue(vm.state.value.isValid)
    }

    @Test fun end_before_start_flags_inline_error() {
        val vm = makeVm()
        vm.updateField(AddEventField.Title, "Soccer game")
        val start = vm.state.value.startDate
        vm.setEndDate(start.minusHours(1))
        val state = vm.state.value
        assertNotNull(state.endError)
        assertFalse(state.isValid)
    }

    @Test fun end_after_start_is_valid() {
        val vm = makeVm()
        vm.updateField(AddEventField.Title, "Soccer game")
        vm.setEndEnabled(true)
        val end = vm.state.value.endDate!!
        vm.setEndDate(end.plusHours(1))
        val state = vm.state.value
        assertNull(state.endError)
        assertTrue(state.isValid)
    }

    @Test fun all_day_clears_end_and_resets_time_on_disable() {
        val vm = makeVm()
        vm.updateField(AddEventField.Title, "Mom's birthday")
        vm.setEndEnabled(true)
        assertNotNull(vm.state.value.endDate)
        vm.setAllDay(true)
        assertNull(vm.state.value.endDate)
        assertEquals(0, vm.state.value.startDate.hour)
        vm.setAllDay(false)
        // Coming off all-day re-seeds 9 AM.
        assertEquals(9, vm.state.value.startDate.hour)
    }

    // MARK: - Recurring pose

    @Test fun recurrence_maps_to_rrule() {
        assertEquals("FREQ=WEEKLY", AddEventRecurrence.Weekly.rrule)
        assertEquals("FREQ=YEARLY", AddEventRecurrence.Yearly.rrule)
        assertEquals("FREQ=MONTHLY", AddEventRecurrence.Monthly.rrule)
        assertEquals("FREQ=DAILY", AddEventRecurrence.Daily.rrule)
        assertNull(AddEventRecurrence.None.rrule)
    }

    @Test fun recurrence_round_trips_from_rrule() {
        assertEquals(AddEventRecurrence.Weekly, AddEventRecurrence.from("FREQ=WEEKLY;BYDAY=SU"))
        assertEquals(AddEventRecurrence.Yearly, AddEventRecurrence.from("FREQ=YEARLY"))
        assertEquals(AddEventRecurrence.None, AddEventRecurrence.from(null))
        assertEquals(AddEventRecurrence.None, AddEventRecurrence.from(""))
    }

    // MARK: - With-attendees pose

    @Test fun load_populates_attendees_from_roster() =
        runTest {
            stubOccupants(
                listOf(
                    occupant("u1", "Maria Patel"),
                    occupant("u2", "John Patel"),
                    occupant("u3", "Ava Patel"),
                ),
            )
            val vm = makeVm()
            vm.load()
            val state = vm.state.value
            assertEquals(3, state.attendees.size)
            assertEquals("Maria Patel", state.attendees[0].displayName)
            assertEquals("MP", state.attendees[0].initials)
            assertFalse(state.isLoadingMembers)
        }

    @Test fun toggle_attendee_flips_selection() =
        runTest {
            stubOccupants(emptyList())
            val vm = makeVm()
            vm.toggleAttendee("u1")
            assertTrue("u1" in vm.state.value.selectedAttendeeIds)
            vm.toggleAttendee("u1")
            assertFalse("u1" in vm.state.value.selectedAttendeeIds)
        }

    // MARK: - Submit (create)

    @Test fun create_submit_posts_request_and_yields_created_commit() =
        runTest {
            stubOccupants(emptyList())
            val captured = slot<CreateHomeEventRequest>()
            coEvery { repo.createHomeEvent("home-1", capture(captured)) } returns
                NetworkResult.Success(
                    HomeEventResponse(
                        event =
                            CalendarEventDto(
                                id = "e1",
                                homeId = "home-1",
                                eventType = "social",
                                title = "Soccer game",
                                startAt = "2025-10-12T16:00:00Z",
                            ),
                    ),
                )
            val vm = makeVm()
            vm.load()
            vm.updateField(AddEventField.Title, "Soccer game · Ava")
            vm.selectCategory(CalendarEventCategory.Social)
            vm.setRecurrence(AddEventRecurrence.Weekly)
            vm.setReminder(AddEventReminder.OneHour)
            vm.submit()
            val commit = vm.state.value.commit
            assertNotNull(commit)
            assertTrue(commit is AddEventCommit.Created)
            assertEquals("e1", (commit as AddEventCommit.Created).eventId)
            assertEquals("social", captured.captured.eventType)
            assertEquals("Soccer game · Ava", captured.captured.title)
            assertEquals("FREQ=WEEKLY", captured.captured.recurrenceRule)
            assertTrue(captured.captured.alertsEnabled == true)
            assertEquals("Event added.", vm.state.value.toast?.text)
        }

    @Test fun create_submit_blocks_when_title_invalid() =
        runTest {
            stubOccupants(emptyList())
            val vm = makeVm()
            vm.load()
            vm.submit()
            assertTrue(vm.state.value.toast?.isError == true)
            coVerify(exactly = 0) { repo.createHomeEvent(any(), any()) }
        }

    @Test fun create_submit_surfaces_backend_error_in_toast() =
        runTest {
            stubOccupants(emptyList())
            coEvery { repo.createHomeEvent(any(), any()) } returns
                NetworkResult.Failure(NetworkError.ClientError(400, "Bad request"))
            val vm = makeVm()
            vm.load()
            vm.updateField(AddEventField.Title, "Soccer game")
            vm.submit()
            val toast = vm.state.value.toast
            assertTrue(toast?.isError == true)
            assertNull(vm.state.value.commit)
        }

    // MARK: - Edit-mode hydration + PUT

    @Test fun editing_hydrates_from_source_event() =
        runTest {
            stubOccupants(emptyList())
            stubLoadEvents(
                listOf(
                    CalendarEventDto(
                        id = "e1",
                        homeId = "home-1",
                        eventType = "social",
                        title = "Soccer game · Ava",
                        description = "Bring water",
                        startAt = "2025-10-12T16:00:00Z",
                        endAt = "2025-10-12T17:30:00Z",
                        locationNotes = "Riverside Field 3",
                        recurrenceRule = "FREQ=WEEKLY",
                        assignedTo = listOf("u1", "u3"),
                        alertsEnabled = true,
                    ),
                ),
            )
            val vm = makeVm(eventId = "e1")
            vm.load()
            val state = vm.state.value
            assertTrue(state.isEditing)
            assertEquals("Edit event", state.title)
            assertEquals("Save", state.commitLabel)
            assertEquals("Soccer game · Ava", state.fields[AddEventField.Title]?.value)
            assertEquals("Riverside Field 3", state.fields[AddEventField.Location]?.value)
            assertEquals("Bring water", state.fields[AddEventField.Notes]?.value)
            assertEquals(CalendarEventCategory.Social, state.category)
            assertEquals(AddEventRecurrence.Weekly, state.recurrence)
            assertEquals(AddEventReminder.FifteenMin, state.reminder)
            assertEquals(setOf("u1", "u3"), state.selectedAttendeeIds)
            assertFalse(state.allDay)
            assertNotNull(state.endDate)
            assertFalse(state.isDirty)
            assertTrue(state.isValid)
        }

    @Test fun editing_all_day_event_hydrates_all_day_pose() =
        runTest {
            stubOccupants(emptyList())
            stubLoadEvents(
                listOf(
                    CalendarEventDto(
                        id = "e2",
                        homeId = "home-1",
                        eventType = "birthday",
                        title = "Mom turns 62",
                        startAt = "2025-10-14T00:00:00Z",
                        endAt = null,
                        recurrenceRule = "FREQ=YEARLY",
                        alertsEnabled = false,
                    ),
                ),
            )
            val vm = makeVm(eventId = "e2")
            vm.load()
            val state = vm.state.value
            assertTrue(state.allDay)
            assertNull(state.endDate)
            assertEquals(AddEventRecurrence.Yearly, state.recurrence)
            assertEquals(AddEventReminder.None, state.reminder)
        }

    @Test fun edit_submit_puts_request_and_yields_updated_commit() =
        runTest {
            stubOccupants(emptyList())
            stubLoadEvents(
                listOf(
                    CalendarEventDto(
                        id = "e1",
                        homeId = "home-1",
                        eventType = "social",
                        title = "Soccer game",
                        startAt = "2025-10-12T16:00:00Z",
                    ),
                ),
            )
            val captured = slot<UpdateHomeEventRequest>()
            coEvery { repo.updateHomeEvent("home-1", "e1", capture(captured)) } returns
                NetworkResult.Success(
                    HomeEventResponse(
                        event =
                            CalendarEventDto(
                                id = "e1",
                                homeId = "home-1",
                                eventType = "social",
                                title = "Soccer game · Ava",
                                startAt = "2025-10-12T16:00:00Z",
                            ),
                    ),
                )
            val vm = makeVm(eventId = "e1")
            vm.load()
            vm.updateField(AddEventField.Title, "Soccer game · Ava")
            vm.submit()
            val commit = vm.state.value.commit
            assertTrue(commit is AddEventCommit.Updated)
            assertEquals("e1", (commit as AddEventCommit.Updated).eventId)
            assertEquals("Soccer game · Ava", captured.captured.title)
        }
}
