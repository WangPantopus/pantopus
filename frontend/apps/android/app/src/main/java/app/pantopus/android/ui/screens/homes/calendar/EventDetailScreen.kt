@file:Suppress(
    "PackageNaming",
    "MagicNumber",
    "LongMethod",
    "LongParameterList",
    "TooManyFunctions",
)

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.api.models.homes.CalendarEventDto
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.shared.content_detail.ContentDetailShell
import app.pantopus.android.ui.screens.shared.content_detail.ContentDetailTopBarAction
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

const val EVENT_DETAIL_SCREEN_TAG = "eventDetail"

/**
 * P2.7 — Read-only event detail. Mirrors iOS `EventDetailView`. Edit
 * pushes back to the form; delete fires the host's `onDeleted`.
 */
@Composable
fun EventDetailScreen(
    onBack: () -> Unit,
    onEdit: (CalendarEventDto) -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.configure(onDeleted = onBack)
        viewModel.load()
    }

    Box(modifier = Modifier.fillMaxSize().testTag(EVENT_DETAIL_SCREEN_TAG)) {
        when (val current = state) {
            is EventDetailUiState.Loading -> LoadingShell(onBack = onBack)
            is EventDetailUiState.Error ->
                ErrorShell(
                    message = current.message,
                    onBack = onBack,
                    onRetry = { viewModel.load() },
                )
            is EventDetailUiState.Loaded ->
                LoadedShell(
                    snapshot = current,
                    onBack = onBack,
                    onEdit = { onEdit(current.event) },
                    onDelete = { viewModel.delete() },
                )
        }
    }
}

@Composable
private fun LoadingShell(onBack: () -> Unit) {
    ContentDetailShell(
        title = "Event",
        onBack = onBack,
        header = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                modifier = Modifier.padding(horizontal = Spacing.s4),
            ) {
                Shimmer(width = 320.dp, height = 24.dp, cornerRadius = Radii.sm)
                Shimmer(width = 220.dp, height = 14.dp, cornerRadius = Radii.sm)
                Shimmer(width = 120.dp, height = 14.dp, cornerRadius = Radii.pill)
            }
        },
        body = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                modifier = Modifier.padding(horizontal = Spacing.s4),
            ) {
                Shimmer(width = 320.dp, height = 60.dp, cornerRadius = Radii.md)
                Shimmer(width = 320.dp, height = 60.dp, cornerRadius = Radii.md)
            }
        },
    )
}

@Composable
private fun ErrorShell(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    ContentDetailShell(
        title = "Event",
        onBack = onBack,
        header = {},
        body = {
            EmptyState(
                icon = PantopusIcon.AlertCircle,
                headline = "Couldn't load this event",
                subcopy = message,
                ctaTitle = "Try again",
                onCta = onRetry,
                modifier = Modifier.height(400.dp),
            )
        },
    )
}

@Composable
private fun LoadedShell(
    snapshot: EventDetailUiState.Loaded,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val event = snapshot.event
    val category = remember(event.eventType) { CalendarEventCategory.from(event.eventType) }
    var showsDeleteConfirm by remember { mutableStateOf(false) }

    ContentDetailShell(
        title = "Event",
        onBack = onBack,
        topBarAction =
            ContentDetailTopBarAction(
                icon = PantopusIcon.Pencil,
                contentDescription = "Edit event",
                onClick = onEdit,
            ),
        header = {
            EventHeader(
                event = event,
                category = category,
                modifier = Modifier.padding(horizontal = Spacing.s4),
            )
        },
        body = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.s4),
                modifier = Modifier.padding(horizontal = Spacing.s4),
            ) {
                DetailGrid(event = event, category = category)
                val assigned = event.assignedTo.orEmpty()
                if (assigned.isNotEmpty()) {
                    AttendeesSection(ids = assigned, nameLookup = snapshot.attendeeNames)
                }
                val description = event.description.orEmpty()
                if (description.isNotEmpty()) {
                    NotesSection(text = description)
                }
                snapshot.deleteError?.let { error ->
                    Text(
                        text = error,
                        style = PantopusTextStyle.small,
                        color = PantopusColors.error,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                    modifier =
                        Modifier
                            .heightIn(min = 44.dp)
                            .clickable(enabled = !snapshot.isDeleting) {
                                showsDeleteConfirm = true
                            }.testTag("eventDetail_delete"),
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.Trash2,
                        contentDescription = null,
                        size = 16.dp,
                        tint = PantopusColors.error,
                    )
                    Text(
                        text = "Delete event",
                        style = PantopusTextStyle.small,
                        color = PantopusColors.error,
                    )
                }
            }
        },
        cta = {
            PrimaryButton(
                title = "Edit",
                onClick = onEdit,
                isEnabled = !snapshot.isDeleting,
                modifier = Modifier.testTag("eventDetail_edit"),
            )
        },
    )

    if (showsDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showsDeleteConfirm = false },
            title = { Text("Delete this event?") },
            text = { Text("Members will no longer see this event on the calendar.") },
            confirmButton = {
                TextButton(onClick = {
                    showsDeleteConfirm = false
                    onDelete()
                }) { Text("Delete", color = PantopusColors.error) }
            },
            dismissButton = {
                TextButton(onClick = { showsDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EventHeader(
    event: CalendarEventDto,
    category: CalendarEventCategory,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorderSubtle, RoundedCornerShape(Radii.lg))
                .padding(Spacing.s4),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(Radii.sm))
                        .background(category.background),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = category.icon,
                    contentDescription = null,
                    size = 24.dp,
                    tint = category.foreground,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                Text(
                    text = event.title,
                    style = PantopusTextStyle.h3,
                    color = PantopusColors.appText,
                    maxLines = 3,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = formattedTimeRange(event),
                    style = PantopusTextStyle.body,
                    color = PantopusColors.appTextSecondary,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            CategoryPill(category = category)
            val location = event.locationNotes.orEmpty()
            if (location.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.MapPin,
                        contentDescription = null,
                        size = 12.dp,
                        tint = PantopusColors.appTextSecondary,
                    )
                    Text(
                        text = location,
                        style = PantopusTextStyle.caption,
                        color = PantopusColors.appTextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(category: CalendarEventCategory) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(category.background)
                .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
    ) {
        PantopusIconImage(
            icon = category.icon,
            contentDescription = null,
            size = 10.dp,
            tint = category.foreground,
        )
        Text(
            text = category.label,
            style = PantopusTextStyle.caption,
            color = category.foreground,
        )
    }
}

@Composable
private fun DetailGrid(
    event: CalendarEventDto,
    category: CalendarEventCategory,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorderSubtle, RoundedCornerShape(Radii.lg)),
    ) {
        DetailRow("Type", category.label)
        recurrenceLabel(event.recurrenceRule)?.let { label ->
            HorizontalDivider(color = PantopusColors.appBorderSubtle, thickness = 1.dp)
            DetailRow("Repeats", label)
        }
        HorizontalDivider(color = PantopusColors.appBorderSubtle, thickness = 1.dp)
        DetailRow(
            "Reminder",
            if (event.alertsEnabled == true) "Enabled" else "Off",
        )
        val location = event.locationNotes.orEmpty()
        if (location.isNotEmpty()) {
            HorizontalDivider(color = PantopusColors.appBorderSubtle, thickness = 1.dp)
            DetailRow("Location", location)
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = PantopusTextStyle.body,
            color = PantopusColors.appText,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AttendeesSection(
    ids: List<String>,
    nameLookup: Map<String, String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.UsersRound,
                contentDescription = null,
                size = 16.dp,
                tint = PantopusColors.home,
            )
            Text(
                text = "Attendees",
                style = PantopusTextStyle.small,
                color = PantopusColors.appText,
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorderSubtle, RoundedCornerShape(Radii.lg)),
        ) {
            ids.forEachIndexed { index, id ->
                val name = nameLookup[id] ?: "Member"
                AttendeeRow(name = name, initials = initials(name))
                if (index < ids.lastIndex) {
                    HorizontalDivider(color = PantopusColors.appBorderSubtle, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun AttendeeRow(
    name: String,
    initials: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4, vertical = Spacing.s3),
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.homeBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials.ifEmpty { "·" },
                style = PantopusTextStyle.caption,
                color = PantopusColors.home,
            )
        }
        Text(
            text = name,
            style = PantopusTextStyle.body,
            color = PantopusColors.appText,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NotesSection(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.FileText,
                contentDescription = null,
                size = 16.dp,
                tint = PantopusColors.primary600,
            )
            Text(
                text = "Notes",
                style = PantopusTextStyle.small,
                color = PantopusColors.appText,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorderSubtle, RoundedCornerShape(Radii.lg))
                    .padding(Spacing.s4),
        ) {
            Text(
                text = text,
                style = PantopusTextStyle.body,
                color = PantopusColors.appText,
            )
        }
    }
}

private fun formattedTimeRange(event: CalendarEventDto): String {
    val zone = ZoneId.systemDefault()
    val startInstant = AddEventFormViewModel.parseIsoInstant(event.startAt) ?: return event.startAt
    val start = startInstant.atZone(zone)
    val endInstant = event.endAt?.let { AddEventFormViewModel.parseIsoInstant(it) }
    val end = endInstant?.atZone(zone)
    if (end == null && isMidnight(start)) {
        return "${longDateLabel(start)} · All day"
    }
    return "${longDateLabel(start)} · ${formattedTime(start, end)}"
}

private fun isMidnight(date: ZonedDateTime): Boolean = date.hour == 0 && date.minute == 0 && date.second == 0

private fun longDateLabel(date: ZonedDateTime): String = DateTimeFormatter.ofPattern("EEEE MMM d", Locale.US).format(date)

private fun formattedTime(
    start: ZonedDateTime,
    end: ZonedDateTime?,
): String {
    val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    val startLabel = fmt.format(start)
    return if (end == null) startLabel else "$startLabel – ${fmt.format(end)}"
}

private fun recurrenceLabel(rrule: String?): String? {
    if (rrule.isNullOrEmpty()) return null
    val upper = rrule.uppercase(Locale.ROOT)
    return when {
        "FREQ=WEEKLY" in upper -> "Weekly"
        "FREQ=YEARLY" in upper -> "Yearly"
        "FREQ=MONTHLY" in upper -> "Monthly"
        "FREQ=DAILY" in upper -> "Daily"
        else -> "Yes"
    }
}

private fun initials(name: String): String =
    name
        .split(' ')
        .take(2)
        .mapNotNull { it.firstOrNull()?.toString() }
        .joinToString("")
        .uppercase(Locale.ROOT)
