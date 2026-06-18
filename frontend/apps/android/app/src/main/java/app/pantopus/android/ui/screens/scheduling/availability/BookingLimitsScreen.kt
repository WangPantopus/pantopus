@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "LongParameterList")

package app.pantopus.android.ui.screens.scheduling.availability

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.ErrorState
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingLoadingSkeleton
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Spacing

@Composable
fun BookingLimitsScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: BookingLimitsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                BookingLimitsEvent.Saved -> onBack()
                is BookingLimitsEvent.Toast -> Unit
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) {
        AvailabilityTopBar(
            title = "Booking limits",
            onBack = onBack,
            trailing = {
                val form = (state as? BookingLimitsUiState.Content)?.form
                TopBarTextAction(
                    label = "Done",
                    enabled = form != null && form.isValid && !form.saving,
                    onClick = viewModel::save,
                )
            },
        )
        when (val s = state) {
            BookingLimitsUiState.Loading -> SchedulingLoadingSkeleton(modifier = Modifier.fillMaxWidth().weight(1f), rows = 5)
            BookingLimitsUiState.Empty ->
                EmptyState(
                    icon = PantopusIcon.SlidersHorizontal,
                    headline = "No event types yet",
                    subcopy = "Create an event type first — booking limits and notice rules are set per event type.",
                )
            is BookingLimitsUiState.Error ->
                Box(
                    Modifier.fillMaxWidth().weight(1f),
                ) { ErrorState(message = s.message, onRetry = viewModel::refresh) }
            is BookingLimitsUiState.Content -> LimitsBody(form = s.form, viewModel = viewModel)
        }
    }
}

@Composable
private fun LimitsBody(
    form: BookingLimitsForm,
    viewModel: BookingLimitsViewModel,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        SectionOverline("Personal · ${form.selectedName}", modifier = Modifier.padding(start = Spacing.s1, top = Spacing.s1))
        if (form.eventTypes.size > 1) {
            EventTypeSelector(form = form, onSelect = viewModel::selectEventType)
        }
        Text(
            "Sensible defaults are set, so you usually don't need to touch these.",
            color = PantopusColors.appTextSecondary,
            fontSize = 11.5.sp,
            modifier = Modifier.padding(horizontal = Spacing.s1, vertical = Spacing.s1),
        )
        StepperRow(
            label = "Minimum notice",
            value = form.minNoticeHours.toString(),
            unit = if (form.minNoticeHours == 1) "hour" else "hours",
            caption = "Can't be booked inside this window.",
            onMinus = { viewModel.changeMinNotice(-1) },
            onPlus = { viewModel.changeMinNotice(1) },
        )
        StepperRow(
            label = "Book up to",
            value = form.bookUpToDays.toString(),
            unit = if (form.bookUpToDays == 1) "day" else "days",
            caption = "How far ahead people can book.",
            error = form.windowError,
            errorMessage = "Your booking window is shorter than your minimum notice, so no times will show.",
            onMinus = { viewModel.changeBookUpTo(-1) },
            onPlus = { viewModel.changeBookUpTo(1) },
        )
        StepperRow(
            label = "Max per day",
            value = if (form.maxPerDay == 0) "No limit" else form.maxPerDay.toString(),
            unit = null,
            caption = "Most bookings you'll take in a day.",
            onMinus = { viewModel.changeMaxPerDay(-1) },
            onPlus = { viewModel.changeMaxPerDay(1) },
        )
        StepperRow(
            label = "Per-person limit",
            value = if (form.perPerson == 0) "No limit" else form.perPerson.toString(),
            unit = if (form.perPerson <= 1) null else "bookings",
            caption = "How many one person can hold at once.",
            onMinus = { viewModel.changePerPerson(-1) },
            onPlus = { viewModel.changePerPerson(1) },
        )
        SegmentRow(
            label = "Start times",
            options = StartInterval.entries.map { it.label },
            selectedIndex = StartInterval.entries.indexOf(form.startInterval),
            caption = "Where bookings can start within the hour.",
            onSelect = { viewModel.setStartInterval(StartInterval.entries[it]) },
        )
    }
}

@Composable
private fun EventTypeSelector(
    form: BookingLimitsForm,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    A3Card {
        FieldLabel("Applies to")
        Box {
            A3FieldButton(icon = PantopusIcon.SlidersHorizontal, value = form.selectedName, onClick = { open = true })
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                form.eventTypes.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.name) },
                        onClick = {
                            open = false
                            onSelect(option.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    unit: String?,
    caption: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    error: Boolean = false,
    errorMessage: String? = null,
) {
    A3Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = PantopusColors.appText,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            A3Stepper(value = value, unit = unit, error = error, onMinus = onMinus, onPlus = onPlus)
        }
        if (error && errorMessage != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                PantopusIconImage(icon = PantopusIcon.AlertCircle, contentDescription = null, size = 12.dp, tint = PantopusColors.error)
                Text(errorMessage, color = PantopusColors.error, fontSize = 10.5.sp)
            }
        } else {
            Text(caption, color = PantopusColors.appTextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SegmentRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    caption: String,
    onSelect: (Int) -> Unit,
) {
    A3Card {
        Text(label, color = PantopusColors.appText, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        A3Segmented(options = options, selectedIndex = selectedIndex, onSelect = onSelect, small = true)
        Text(caption, color = PantopusColors.appTextSecondary, fontSize = 11.sp)
    }
}
