@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "LongParameterList")

package app.pantopus.android.ui.screens.homes.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.screens.shared.form.FormFieldState
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Paparazzi snapshots for the P2.7 Add Event form. Locks the four
 * design-spec poses:
 *  - **empty** (fresh form, Add disabled),
 *  - **all-day** (toggle on, time pickers hidden),
 *  - **with-attendees** (multi-pick populated, two selected),
 *  - **recurring** (Weekly selected + 1h reminder).
 *
 * The screen-level [AddEventFormScreen] depends on Hilt; these tests
 * render the stateless [AddEventFormBody] against fixture state instead.
 */
class AddEventFormSnapshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 2800,
                    softButtons = false,
                ),
        )

    private val zone: ZoneId = ZoneId.of("UTC")
    private val anchor =
        LocalDateTime.of(2025, 10, 12, 16, 0).atZone(zone)

    @Test
    fun add_event_empty_pose() {
        paparazzi.snapshot {
            Frame {
                AddEventFormBody(
                    state =
                        baselineState(
                            title = "",
                        ),
                    onClose = {},
                    onCommit = {},
                    onUpdateField = { _, _ -> },
                    onSelectCategory = {},
                    onAllDay = {},
                    onSetStart = {},
                    onSetEndEnabled = {},
                    onSetEnd = {},
                    onSetRecurrence = {},
                    onSetReminder = {},
                    onToggleAttendee = {},
                )
            }
        }
    }

    @Test
    fun add_event_all_day_pose() {
        paparazzi.snapshot {
            Frame {
                AddEventFormBody(
                    state =
                        baselineState(
                            title = "Mom turns 62",
                            category = CalendarEventCategory.Birthday,
                            allDay = true,
                            start = anchor.toLocalDate().atStartOfDay(zone),
                            recurrence = AddEventRecurrence.Yearly,
                            isDirty = true,
                        ),
                    onClose = {},
                    onCommit = {},
                    onUpdateField = { _, _ -> },
                    onSelectCategory = {},
                    onAllDay = {},
                    onSetStart = {},
                    onSetEndEnabled = {},
                    onSetEnd = {},
                    onSetRecurrence = {},
                    onSetReminder = {},
                    onToggleAttendee = {},
                )
            }
        }
    }

    @Test
    fun add_event_with_attendees_pose() {
        val attendees =
            listOf(
                AddEventAttendee(id = "u1", displayName = "Maria Patel", initials = "MP"),
                AddEventAttendee(id = "u2", displayName = "John Patel", initials = "JP"),
                AddEventAttendee(id = "u3", displayName = "Ava Patel", initials = "AP"),
            )
        paparazzi.snapshot {
            Frame {
                AddEventFormBody(
                    state =
                        baselineState(
                            title = "Soccer game · Ava",
                            category = CalendarEventCategory.Social,
                            start = anchor,
                            end = anchor.plusHours(1).plusMinutes(30),
                            attendees = attendees,
                            selectedAttendees = setOf("u1", "u3"),
                            location = "Riverside Field 3",
                            reminder = AddEventReminder.OneHour,
                            isDirty = true,
                        ),
                    onClose = {},
                    onCommit = {},
                    onUpdateField = { _, _ -> },
                    onSelectCategory = {},
                    onAllDay = {},
                    onSetStart = {},
                    onSetEndEnabled = {},
                    onSetEnd = {},
                    onSetRecurrence = {},
                    onSetReminder = {},
                    onToggleAttendee = {},
                )
            }
        }
    }

    @Test
    fun add_event_recurring_pose() {
        paparazzi.snapshot {
            Frame {
                AddEventFormBody(
                    state =
                        baselineState(
                            title = "Trash & recycling out",
                            category = CalendarEventCategory.Trash,
                            start = anchor,
                            end = anchor.plusMinutes(15),
                            recurrence = AddEventRecurrence.Weekly,
                            reminder = AddEventReminder.FifteenMin,
                            isDirty = true,
                        ),
                    onClose = {},
                    onCommit = {},
                    onUpdateField = { _, _ -> },
                    onSelectCategory = {},
                    onAllDay = {},
                    onSetStart = {},
                    onSetEndEnabled = {},
                    onSetEnd = {},
                    onSetRecurrence = {},
                    onSetReminder = {},
                    onToggleAttendee = {},
                )
            }
        }
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        PantopusTheme {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(PantopusColors.appBg),
            ) { content() }
        }
    }

    private fun baselineState(
        title: String,
        location: String = "",
        notes: String = "",
        category: CalendarEventCategory = CalendarEventCategory.Generic,
        allDay: Boolean = false,
        start: java.time.ZonedDateTime = anchor,
        end: java.time.ZonedDateTime? = null,
        recurrence: AddEventRecurrence = AddEventRecurrence.None,
        reminder: AddEventReminder = AddEventReminder.None,
        attendees: List<AddEventAttendee> = emptyList(),
        selectedAttendees: Set<String> = emptySet(),
        isDirty: Boolean = false,
    ): AddEventUiState {
        val titleField =
            FormFieldState(
                id = AddEventField.Title.key,
                value = title,
                originalValue = if (isDirty) "" else title,
                touched = isDirty,
                error = null,
            )
        val locationField =
            FormFieldState(
                id = AddEventField.Location.key,
                value = location,
                originalValue = location,
            )
        val notesField =
            FormFieldState(
                id = AddEventField.Notes.key,
                value = notes,
                originalValue = notes,
            )
        return AddEventUiState(
            fields =
                mapOf(
                    AddEventField.Title to titleField,
                    AddEventField.Location to locationField,
                    AddEventField.Notes to notesField,
                ),
            category = category,
            allDay = allDay,
            startDate = start,
            endDate = end,
            recurrence = recurrence,
            reminder = reminder,
            attendees = attendees,
            selectedAttendeeIds = selectedAttendees,
            isEditing = false,
            isLoadingMembers = false,
            isSaving = false,
            toast = null,
            commit = null,
            baseline =
                AddEventBaseline(
                    category = CalendarEventCategory.Generic,
                    allDay = false,
                    start = start,
                    end = null,
                    recurrence = AddEventRecurrence.None,
                    reminder = AddEventReminder.None,
                    attendees = emptySet(),
                ),
        )
    }
}
