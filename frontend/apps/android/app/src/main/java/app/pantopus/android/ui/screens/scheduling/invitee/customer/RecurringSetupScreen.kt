@file:Suppress("PackageNaming", "MagicNumber", "LongParameterList", "LongMethod")

package app.pantopus.android.ui.screens.scheduling.invitee.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.ErrorState
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingLoadingSkeleton
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.screens.scheduling.invitee.edge.EdgeHalo
import app.pantopus.android.ui.screens.scheduling.invitee.edge.EdgeTone
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

const val RECURRING_SETUP_TAG = "schedulingRecurringSetup"

private val ACCENT get() = SchedulingPillar.Personal.accent
private val ACCENT_BG get() = SchedulingPillar.Personal.accentBg

@Composable
fun RecurringSetupScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: RecurringSetupViewModel = hiltViewModel(),
) {
    val load by viewModel.load.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val occurrences by viewModel.occurrences.collectAsStateWithLifecycle()
    val submit by viewModel.submit.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.start() }

    Column(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg).testTag(RECURRING_SETUP_TAG)) {
        TopBar(onBack = onBack)
        when (val l = load) {
            is RecurringLoadState.Loading -> SchedulingLoadingSkeleton(modifier = Modifier.weight(1f), rows = 4)
            is RecurringLoadState.Empty ->
                EmptyState(
                    icon = PantopusIcon.Calendar,
                    headline = "Create an event type first",
                    subcopy = "Recurring series book one of your event types every week. Add one to get started.",
                    modifier = Modifier.weight(1f),
                )
            is RecurringLoadState.Error ->
                ErrorState(
                    message = l.message,
                    modifier = Modifier.weight(1f),
                    onRetry = viewModel::loadEventType,
                )
            is RecurringLoadState.Loaded ->
                RecurringBody(
                    config = config,
                    occurrences = occurrences,
                    submit = submit,
                    rangeLabel = viewModel.rangeLabel(),
                    weekdayShort = viewModel.weekdayShort(),
                    timeLabel = viewModel.timeLabel(),
                    modifier = Modifier.weight(1f),
                    onRepeat = viewModel::setRepeat,
                    onWeekday = viewModel::setWeekday,
                    onStepTime = viewModel::stepTime,
                    onSetCount = viewModel::setCount,
                    onStepCount = viewModel::stepCount,
                    onConfirm = viewModel::confirm,
                    onDone = onBack,
                    onRetry = viewModel::resetSubmit,
                )
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface).padding(horizontal = Spacing.s2, vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(Radii.md)).clickable(onClickLabel = "Back", onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = PantopusIcon.ChevronLeft, contentDescription = "Back", size = 20.dp, tint = PantopusColors.appText)
        }
        Text(
            text = "Set up your series",
            style = PantopusTextStyle.h3,
            color = PantopusColors.appText,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.size(34.dp))
    }
}

@Composable
fun RecurringBody(
    config: RecurringConfig,
    occurrences: List<RecurrenceOccurrence>,
    submit: RecurringSubmitState,
    rangeLabel: String,
    weekdayShort: String,
    timeLabel: String,
    onRepeat: (RecurrenceRepeat) -> Unit,
    onWeekday: (Int) -> Unit,
    onStepTime: (Int) -> Unit,
    onSetCount: (Int) -> Unit,
    onStepCount: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (submit is RecurringSubmitState.Result) {
        ResultBody(result = submit, occurrences = occurrences, modifier = modifier, onDone = onDone)
        return
    }
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(
                text = "Book the whole series in one go. We'll find the same time each week and flag any that's taken.",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
            ConfigCard(
                config = config,
                weekdayShort = weekdayShort,
                timeLabel = timeLabel,
                onRepeat = onRepeat,
                onWeekday = onWeekday,
                onStepTime = onStepTime,
                onSetCount = onSetCount,
                onStepCount = onStepCount,
            )
            if (occurrences.isNotEmpty()) {
                SeriesStrip(occurrences = occurrences)
                val failedCount = occurrences.count { it.status == OccurrenceStatus.Failed }
                val openCount = occurrences.size - failedCount
                val overlineText =
                    when {
                        failedCount == 0 -> "ALL ${occurrences.size} OPEN"
                        else -> "$openCount OPEN · $failedCount NEEDS A NEW TIME"
                    }
                Text(text = overlineText, style = PantopusTextStyle.overline, color = PantopusColors.appTextSecondary)
                occurrences.forEach { OccurrenceRow(it) }
                SummaryChip(count = occurrences.size, weekdayShort = weekdayShort, timeLabel = timeLabel, rangeLabel = rangeLabel)
            }
            (submit as? RecurringSubmitState.Error)?.let {
                Text(text = it.message, style = PantopusTextStyle.small, color = PantopusColors.error)
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface).padding(Spacing.s4),
        ) {
            PrimaryButton(
                title = if (submit is RecurringSubmitState.Error) "Try again" else "Confirm ${occurrences.size} bookings",
                onClick = if (submit is RecurringSubmitState.Error) onRetry else onConfirm,
                isLoading = submit is RecurringSubmitState.Saving,
                isEnabled = occurrences.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun ConfigCard(
    config: RecurringConfig,
    weekdayShort: String,
    timeLabel: String,
    onRepeat: (RecurrenceRepeat) -> Unit,
    onWeekday: (Int) -> Unit,
    onStepTime: (Int) -> Unit,
    onSetCount: (Int) -> Unit,
    onStepCount: (Int) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        FieldLabel("Repeats")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1), modifier = Modifier.fillMaxWidth()) {
            RecurrenceRepeat.entries.forEach { r ->
                Segment(
                    label = if (r == RecurrenceRepeat.Weekly) "Weekly" else "Daily",
                    selected = r == config.repeat,
                    modifier = Modifier.weight(1f),
                ) {
                    onRepeat(r)
                }
            }
        }
        if (config.repeat == RecurrenceRepeat.Weekly) {
            FieldLabel("On")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1), modifier = Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { i, d ->
                    WeekdayChip(label = d, selected = i == config.weekdayIndex, modifier = Modifier.weight(1f)) { onWeekday(i) }
                }
            }
        }
        FieldLabel("Time")
        Stepper(value = timeLabel, onMinus = { onStepTime(-30) }, onPlus = { onStepTime(30) })
        FieldLabel("How many")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${config.count} sessions",
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                modifier = Modifier.weight(1f),
            )
            CountStepper(value = config.count, onMinus = { onStepCount(-1) }, onPlus = { onStepCount(1) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), modifier = Modifier.fillMaxWidth()) {
            listOf(4, 6, 8, 12).forEach { n ->
                Segment(label = n.toString(), selected = n == config.count, modifier = Modifier.weight(1f)) { onSetCount(n) }
            }
        }
        val cadence = if (config.repeat == RecurrenceRepeat.Weekly) weekdayShort else "day"
        Text(
            text = "We'll find $timeLabel each $cadence and flag any that's taken.",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text = text, style = PantopusTextStyle.caption, fontWeight = FontWeight.SemiBold, color = PantopusColors.appTextSecondary)
}

@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radii.md))
                .background(if (selected) ACCENT else PantopusColors.appSurface)
                .border(1.dp, if (selected) ACCENT else PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                .clickable(onClick = onClick)
                .padding(vertical = Spacing.s2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = PantopusTextStyle.small,
            fontWeight = FontWeight.Bold,
            color = if (selected) PantopusColors.appTextInverse else PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun WeekdayChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radii.md))
                .background(if (selected) ACCENT else PantopusColors.appSurface)
                .border(1.dp, if (selected) ACCENT else PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                .clickable(onClick = onClick)
                .padding(vertical = Spacing.s2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = PantopusTextStyle.small,
            fontWeight = FontWeight.Bold,
            color = if (selected) PantopusColors.appTextInverse else PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(Radii.md)).border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(icon = PantopusIcon.Minus, onClick = onMinus)
        Text(
            text = value,
            style = PantopusTextStyle.small,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            modifier = Modifier.padding(horizontal = Spacing.s4),
        )
        StepButton(icon = PantopusIcon.Plus, onClick = onPlus)
    }
}

@Composable
private fun CountStepper(
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(Radii.md)).border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(icon = PantopusIcon.Minus, onClick = onMinus)
        Text(
            text = value.toString(),
            style = PantopusTextStyle.small,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            modifier = Modifier.padding(horizontal = Spacing.s4),
        )
        StepButton(icon = PantopusIcon.Plus, onClick = onPlus)
    }
}

@Composable
private fun StepButton(
    icon: PantopusIcon,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(34.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(icon = icon, contentDescription = null, size = 14.dp, tint = ACCENT)
    }
}

@Composable
private fun SeriesStrip(occurrences: List<RecurrenceOccurrence>) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .padding(Spacing.s3)
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        occurrences.forEach { occ ->
            val failed = occ.status == OccurrenceStatus.Failed
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (failed) PantopusColors.warningBg else ACCENT),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = occ.dateLabel.substringAfterLast(' '),
                    style = PantopusTextStyle.caption,
                    fontWeight = FontWeight.Bold,
                    color = if (failed) PantopusColors.warning else PantopusColors.appTextInverse,
                )
            }
        }
    }
}

@Composable
private fun OccurrenceRow(occ: RecurrenceOccurrence) {
    val failed = occ.status == OccurrenceStatus.Failed
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, if (failed) PantopusColors.warningLight else PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .padding(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier =
                Modifier.size(
                    30.dp,
                ).clip(RoundedCornerShape(Radii.md)).background(if (failed) PantopusColors.warningBg else PantopusColors.successBg),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = if (failed) PantopusIcon.AlertCircle else PantopusIcon.CalendarCheck,
                contentDescription = null,
                size = 15.dp,
                tint = if (failed) PantopusColors.warning else PantopusColors.success,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = occ.dateLabel, style = PantopusTextStyle.small, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
            Text(
                text = if (failed) "That time is taken" else occ.timeLabel,
                style = PantopusTextStyle.caption,
                color = if (failed) PantopusColors.warning else PantopusColors.appTextSecondary,
            )
        }
        if (failed) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                Text(text = "PICK ANOTHER", style = PantopusTextStyle.overline, color = ACCENT)
                PantopusIconImage(icon = PantopusIcon.ChevronRight, contentDescription = null, size = 13.dp, tint = ACCENT)
            }
        } else {
            Text(
                text = "OPEN",
                style = PantopusTextStyle.overline,
                color = PantopusColors.success,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.successBg)
                        .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
            )
        }
    }
}

@Composable
private fun SummaryChip(
    count: Int,
    weekdayShort: String,
    timeLabel: String,
    rangeLabel: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(ACCENT_BG)
                .padding(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(icon = PantopusIcon.ArrowsRepeat, contentDescription = null, size = 15.dp, tint = ACCENT)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$count sessions · $weekdayShort $timeLabel",
                style = PantopusTextStyle.caption,
                fontWeight = FontWeight.Bold,
                color = ACCENT,
            )
            if (rangeLabel.isNotEmpty()) {
                Text(text = rangeLabel, style = PantopusTextStyle.overline, color = PantopusColors.appTextSecondary)
            }
        }
    }
}

@Composable
private fun ResultBody(
    result: RecurringSubmitState.Result,
    occurrences: List<RecurrenceOccurrence>,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val partial = result.failed.isNotEmpty()
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            EdgeHalo(
                tone = if (partial) EdgeTone.Warn else EdgeTone.Success,
                icon = if (partial) PantopusIcon.AlertCircle else PantopusIcon.CalendarCheck,
                title = if (partial) "We booked ${result.created} of ${result.requested}" else "Your series is booked",
                body =
                    if (partial) {
                        "Some weeks were full. The ones that worked are confirmed."
                    } else {
                        "All ${result.created} sessions are confirmed."
                    },
            )
            occurrences.forEach { OccurrenceRow(it) }
        }
        Column(modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface).padding(Spacing.s4)) {
            PrimaryButton(title = "Done", onClick = onDone)
        }
    }
}
