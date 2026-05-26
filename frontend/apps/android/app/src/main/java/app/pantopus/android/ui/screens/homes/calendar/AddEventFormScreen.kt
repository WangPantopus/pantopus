@file:Suppress(
    "PackageNaming",
    "MagicNumber",
    "LongMethod",
    "LongParameterList",
    "TooManyFunctions",
)
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantopus.android.ui.screens.homes.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.PantopusFieldState
import app.pantopus.android.ui.components.PantopusTextField
import app.pantopus.android.ui.screens.shared.form.FormFieldGroup
import app.pantopus.android.ui.screens.shared.form.FormShell
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Test tag on the screen root. */
const val ADD_EVENT_SCREEN_TAG = "addEventForm"

/**
 * P2.7 — Add / edit calendar event form. Mirrors iOS
 * `AddEventFormView`. Reuses the shared [FormShell] for chrome.
 *
 * @param onClose Pop callback.
 * @param onCommit Fires with the created / updated event id so the host
 *     can swap the form for the new detail.
 */
@Composable
fun AddEventFormScreen(
    onClose: () -> Unit,
    onCommit: (AddEventCommit) -> Unit,
    viewModel: AddEventFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(state.toast) {
        if (state.toast != null) {
            delay(2_500)
            viewModel.dismissToast()
        }
    }

    LaunchedEffect(state.commit) {
        state.commit?.let { commit ->
            viewModel.acknowledgeCommit()
            onCommit(commit)
        }
    }

    AddEventFormBody(
        state = state,
        onClose = onClose,
        onCommit = { viewModel.submit() },
        onUpdateField = viewModel::updateField,
        onSelectCategory = viewModel::selectCategory,
        onAllDay = viewModel::setAllDay,
        onSetStart = viewModel::setStartDate,
        onSetEndEnabled = viewModel::setEndEnabled,
        onSetEnd = viewModel::setEndDate,
        onSetRecurrence = viewModel::setRecurrence,
        onSetReminder = viewModel::setReminder,
        onToggleAttendee = viewModel::toggleAttendee,
    )
}

/**
 * Stateless form body — exposed separately so Paparazzi snapshots can
 * render the form against fixture state without standing the VM up.
 */
@Composable
fun AddEventFormBody(
    state: AddEventUiState,
    onClose: () -> Unit,
    onCommit: () -> Unit,
    onUpdateField: (AddEventField, String) -> Unit,
    onSelectCategory: (CalendarEventCategory) -> Unit,
    onAllDay: (Boolean) -> Unit,
    onSetStart: (java.time.ZonedDateTime) -> Unit,
    onSetEndEnabled: (Boolean) -> Unit,
    onSetEnd: (java.time.ZonedDateTime) -> Unit,
    onSetRecurrence: (AddEventRecurrence) -> Unit,
    onSetReminder: (AddEventReminder) -> Unit,
    onToggleAttendee: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().testTag(ADD_EVENT_SCREEN_TAG)) {
        FormShell(
            title = state.title,
            rightActionLabel = state.commitLabel,
            isValid = state.isValid,
            isDirty = state.isDirty,
            isSaving = state.isSaving,
            onClose = onClose,
            onCommit = onCommit,
        ) {
            TitleGroup(state = state, onUpdate = onUpdateField)
            CategoryGroup(state = state, onSelect = onSelectCategory)
            ScheduleGroup(
                state = state,
                onAllDay = onAllDay,
                onSetStart = onSetStart,
                onSetEndEnabled = onSetEndEnabled,
                onSetEnd = onSetEnd,
            )
            LocationGroup(state = state, onUpdate = onUpdateField)
            RecurrenceGroup(state = state, onSelect = onSetRecurrence)
            AttendeesGroup(state = state, onToggle = onToggleAttendee)
            ReminderGroup(state = state, onSelect = onSetReminder)
            NotesGroup(state = state, onUpdate = onUpdateField)
            Box(modifier = Modifier.height(Spacing.s5))
        }

        state.toast?.let { toast ->
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Spacing.s8)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(
                            if (toast.isError) PantopusColors.error else PantopusColors.success,
                        ).padding(horizontal = Spacing.s4, vertical = Spacing.s2),
            ) {
                Text(
                    text = toast.text,
                    style = PantopusTextStyle.small,
                    color = PantopusColors.appTextInverse,
                )
            }
        }
    }
}

// MARK: - Title

@Composable
private fun TitleGroup(
    state: AddEventUiState,
    onUpdate: (AddEventField, String) -> Unit,
) {
    FormFieldGroup(title = "Title") {
        val snapshot = state.fields[AddEventField.Title]
        val fieldState =
            when {
                snapshot == null || !snapshot.touched -> PantopusFieldState.Default
                snapshot.error != null -> PantopusFieldState.Error(snapshot.error)
                snapshot.value.trim().isEmpty() -> PantopusFieldState.Default
                else -> PantopusFieldState.Valid
            }
        PantopusTextField(
            label = "Title",
            value = snapshot?.value.orEmpty(),
            onValueChange = { onUpdate(AddEventField.Title, it) },
            placeholder = "What's the event?",
            state = fieldState,
            fieldTestTag = "addEvent_titleField",
        )
    }
}

// MARK: - Category

private val DESIGNED_CATEGORIES =
    listOf(
        CalendarEventCategory.Chore,
        CalendarEventCategory.Birthday,
        CalendarEventCategory.Maintenance,
        CalendarEventCategory.School,
        CalendarEventCategory.Medical,
        CalendarEventCategory.Social,
        CalendarEventCategory.Family,
        CalendarEventCategory.Pet,
        CalendarEventCategory.Delivery,
        CalendarEventCategory.Trash,
        CalendarEventCategory.Bill,
        CalendarEventCategory.Generic,
    )

@Composable
private fun CategoryGroup(
    state: AddEventUiState,
    onSelect: (CalendarEventCategory) -> Unit,
) {
    FormFieldGroup(title = "Category") {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.heightIn(min = 240.dp, max = 360.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            items(DESIGNED_CATEGORIES) { category ->
                CategoryChip(
                    category = category,
                    isSelected = state.category == category,
                    onClick = { onSelect(category) },
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: CalendarEventCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(Radii.md))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) PantopusColors.primary600 else PantopusColors.appBorder,
                    shape = RoundedCornerShape(Radii.md),
                ).clickable(onClick = onClick)
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2)
                .testTag("addEvent_category_${category.rawValue}")
                .semantics { contentDescription = category.label },
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(Radii.sm))
                    .background(category.background),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = category.icon,
                contentDescription = null,
                size = Radii.xl,
                tint = category.foreground,
            )
        }
        Text(
            text = category.label,
            style = PantopusTextStyle.small,
            color = PantopusColors.appText,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (isSelected) {
            PantopusIconImage(
                icon = PantopusIcon.Check,
                contentDescription = null,
                size = 14.dp,
                tint = PantopusColors.primary600,
            )
        }
    }
}

// MARK: - Schedule

@Composable
private fun ScheduleGroup(
    state: AddEventUiState,
    onAllDay: (Boolean) -> Unit,
    onSetStart: (java.time.ZonedDateTime) -> Unit,
    onSetEndEnabled: (Boolean) -> Unit,
    onSetEnd: (java.time.ZonedDateTime) -> Unit,
) {
    FormFieldGroup(title = "Schedule") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        ) {
            Text(
                text = "All day",
                style = PantopusTextStyle.body,
                color = PantopusColors.appText,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.allDay,
                onCheckedChange = onAllDay,
                colors =
                    SwitchDefaults.colors(
                        checkedTrackColor = PantopusColors.primary600,
                        uncheckedTrackColor = PantopusColors.appBorderStrong,
                    ),
                modifier = Modifier.testTag("addEvent_allDayToggle"),
            )
        }

        // Start
        DateTimeRow(
            label = "Starts",
            value = state.startDate,
            allDay = state.allDay,
            testTagPrefix = "addEvent_startDate",
            onChange = onSetStart,
        )

        // End — only when not all-day
        if (!state.allDay) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                ) {
                    Text(
                        text = "Ends",
                        style = PantopusTextStyle.body,
                        color = PantopusColors.appText,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.endDate != null,
                        onCheckedChange = onSetEndEnabled,
                        colors =
                            SwitchDefaults.colors(
                                checkedTrackColor = PantopusColors.primary600,
                                uncheckedTrackColor = PantopusColors.appBorderStrong,
                            ),
                        modifier = Modifier.testTag("addEvent_hasEndToggle"),
                    )
                }
                if (state.endDate != null) {
                    DateTimeRow(
                        label = "End",
                        value = state.endDate,
                        allDay = false,
                        testTagPrefix = "addEvent_endDate",
                        onChange = onSetEnd,
                    )
                }
                state.endError?.let { error ->
                    Text(
                        text = error,
                        style = PantopusTextStyle.caption,
                        color = PantopusColors.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun DateTimeRow(
    label: String,
    value: java.time.ZonedDateTime,
    allDay: Boolean,
    testTagPrefix: String,
    onChange: (java.time.ZonedDateTime) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE MMM d, yyyy", Locale.US) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.US) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Text(
            text = label,
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            PickerChip(
                label = dateFmt.format(value),
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f).testTag("${testTagPrefix}_date"),
            )
            if (!allDay) {
                PickerChip(
                    label = timeFmt.format(value),
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f).testTag("${testTagPrefix}_time"),
                )
            }
        }
    }

    if (showDatePicker) {
        SimpleDatePickerDialog(
            initial = value.toLocalDate(),
            onDismiss = { showDatePicker = false },
            onSelect = { picked ->
                showDatePicker = false
                onChange(value.with(picked).withZoneSameInstant(value.zone))
            },
        )
    }
    if (showTimePicker) {
        SimpleTimePickerDialog(
            initial = value.toLocalTime(),
            onDismiss = { showTimePicker = false },
            onSelect = { picked ->
                showTimePicker = false
                onChange(
                    LocalDateTime
                        .of(value.toLocalDate(), picked)
                        .atZone(value.zone),
                )
            },
        )
    }
}

@Composable
private fun PickerChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(Radii.md))
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurface)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.s3),
    ) {
        Text(
            text = label,
            style = PantopusTextStyle.body,
            color = PantopusColors.appText,
            modifier = Modifier.weight(1f),
        )
        PantopusIconImage(
            icon = PantopusIcon.ChevronDown,
            contentDescription = null,
            size = Radii.xl,
            tint = PantopusColors.appTextSecondary,
        )
    }
}

// MARK: - Location

@Composable
private fun LocationGroup(
    state: AddEventUiState,
    onUpdate: (AddEventField, String) -> Unit,
) {
    FormFieldGroup(title = "Location") {
        PantopusTextField(
            label = "Where",
            value = state.fields[AddEventField.Location]?.value.orEmpty(),
            onValueChange = { onUpdate(AddEventField.Location, it) },
            placeholder = "Optional · address, room, link",
            fieldTestTag = "addEvent_locationField",
        )
    }
}

// MARK: - Recurrence

@Composable
private fun RecurrenceGroup(
    state: AddEventUiState,
    onSelect: (AddEventRecurrence) -> Unit,
) {
    FormFieldGroup(title = "Repeat") {
        Column {
            val options = AddEventRecurrence.entries
            options.forEachIndexed { index, option ->
                PickerRow(
                    label = option.label,
                    isSelected = state.recurrence == option,
                    testTag = "addEvent_recurrence_${option.rawValue}",
                    onClick = { onSelect(option) },
                )
                if (index != options.lastIndex) {
                    HorizontalDivider(color = PantopusColors.appBorderSubtle, thickness = 1.dp)
                }
            }
        }
    }
}

// MARK: - Attendees

@Composable
private fun AttendeesGroup(
    state: AddEventUiState,
    onToggle: (String) -> Unit,
) {
    FormFieldGroup(title = "Attendees") {
        if (state.attendees.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.UsersRound,
                    contentDescription = null,
                    size = 18.dp,
                    tint = PantopusColors.appTextSecondary,
                )
                Text(
                    text =
                        if (state.isLoadingMembers) {
                            "Loading household members…"
                        } else {
                            "No household members loaded yet."
                        },
                    style = PantopusTextStyle.small,
                    color = PantopusColors.appTextSecondary,
                )
            }
        } else {
            Column {
                state.attendees.forEachIndexed { index, attendee ->
                    AttendeeRow(
                        attendee = attendee,
                        isSelected = attendee.id in state.selectedAttendeeIds,
                        onClick = { onToggle(attendee.id) },
                    )
                    if (index != state.attendees.lastIndex) {
                        HorizontalDivider(color = PantopusColors.appBorderSubtle, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendeeRow(
    attendee: AddEventAttendee,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(onClick = onClick)
                .padding(vertical = Spacing.s2)
                .testTag("addEvent_attendee_${attendee.id}")
                .semantics { contentDescription = attendee.displayName },
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.homeBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = attendee.initials.ifEmpty { "·" },
                style = PantopusTextStyle.caption,
                color = PantopusColors.home,
            )
        }
        Text(
            text = attendee.displayName,
            style = PantopusTextStyle.body,
            color = PantopusColors.appText,
            modifier = Modifier.weight(1f),
        )
        CheckMark(isSelected = isSelected)
    }
}

@Composable
private fun CheckMark(isSelected: Boolean) {
    Box(
        modifier =
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(Radii.xs))
                .background(if (isSelected) PantopusColors.primary600 else PantopusColors.appSurface)
                .border(
                    width = if (isSelected) 0.dp else 1.5.dp,
                    color = if (isSelected) PantopusColors.primary600 else PantopusColors.appBorderStrong,
                    shape = RoundedCornerShape(Radii.xs),
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            PantopusIconImage(
                icon = PantopusIcon.Check,
                contentDescription = null,
                size = 14.dp,
                tint = PantopusColors.appTextInverse,
            )
        }
    }
}

// MARK: - Reminder

@Composable
private fun ReminderGroup(
    state: AddEventUiState,
    onSelect: (AddEventReminder) -> Unit,
) {
    FormFieldGroup(title = "Reminder") {
        Column {
            val options = AddEventReminder.entries
            options.forEachIndexed { index, option ->
                PickerRow(
                    label = option.label,
                    isSelected = state.reminder == option,
                    testTag = "addEvent_reminder_${option.rawValue}",
                    onClick = { onSelect(option) },
                )
                if (index != options.lastIndex) {
                    HorizontalDivider(color = PantopusColors.appBorderSubtle, thickness = 1.dp)
                }
            }
        }
    }
}

// MARK: - Notes

@Composable
private fun NotesGroup(
    state: AddEventUiState,
    onUpdate: (AddEventField, String) -> Unit,
) {
    FormFieldGroup(title = "Notes") {
        val snapshot = state.fields[AddEventField.Notes]
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 88.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .background(PantopusColors.appSurface)
                        .border(
                            width = 1.dp,
                            color =
                                if (snapshot?.error == null) {
                                    PantopusColors.appBorder
                                } else {
                                    PantopusColors.error
                                },
                            shape = RoundedCornerShape(Radii.md),
                        ).padding(Spacing.s3),
            ) {
                BasicTextField(
                    value = snapshot?.value.orEmpty(),
                    onValueChange = { onUpdate(AddEventField.Notes, it) },
                    textStyle = PantopusTextStyle.body.copy(color = PantopusColors.appText),
                    cursorBrush = SolidColor(PantopusColors.primary600),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth().testTag("addEvent_notesField"),
                    decorationBox = { inner ->
                        if ((snapshot?.value ?: "").isEmpty()) {
                            Text(
                                text = "Notes (optional)",
                                style = PantopusTextStyle.body,
                                color = PantopusColors.appTextMuted,
                            )
                        }
                        inner()
                    },
                )
            }
            snapshot?.error?.let { error ->
                Text(
                    text = error,
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.error,
                )
            }
        }
    }
}

// MARK: - Helpers

@Composable
private fun PickerRow(
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(onClick = onClick)
                .padding(vertical = Spacing.s2)
                .testTag(testTag)
                .semantics { contentDescription = label },
    ) {
        Text(
            text = label,
            style = PantopusTextStyle.body,
            color = PantopusColors.appText,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            PantopusIconImage(
                icon = PantopusIcon.Check,
                contentDescription = null,
                size = 18.dp,
                tint = PantopusColors.primary600,
            )
        }
    }
}

@Composable
private fun SimpleDatePickerDialog(
    initial: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis =
        initial
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val picked = state.selectedDateMillis
                if (picked != null) {
                    val date =
                        java.time.Instant
                            .ofEpochMilli(picked)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    onSelect(date)
                } else {
                    onDismiss()
                }
            }) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun SimpleTimePickerDialog(
    initial: LocalTime,
    onSelect: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = false,
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onSelect(LocalTime.of(state.hour, state.minute))
            }) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        Box(modifier = Modifier.padding(Spacing.s4)) { TimePicker(state = state) }
    }
}
