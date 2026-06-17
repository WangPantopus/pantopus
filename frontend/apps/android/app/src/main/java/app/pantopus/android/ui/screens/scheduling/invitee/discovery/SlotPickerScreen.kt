@file:Suppress("PackageNaming", "LongParameterList", "MagicNumber", "TooManyFunctions", "LongMethod")

package app.pantopus.android.ui.screens.scheduling.invitee.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.api.models.scheduling.SlotDto
import app.pantopus.android.ui.components.ErrorState
import app.pantopus.android.ui.screens.scheduling._shared.MonthCalendar
import app.pantopus.android.ui.screens.scheduling._shared.SlotTimeList
import app.pantopus.android.ui.screens.scheduling._shared.slotTimeLabel
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

const val SLOT_PICKER_CONTINUE_TAG = "slotPickerContinueBar"

/**
 * C6 Date + time slot picker (stateful). Owns a [SlotPickerViewModel] scoped to
 * the public-booking route, starts it with the chosen event type's [args], and
 * presents the C7 timezone sheet locally. Selecting a slot + Continue is the
 * terminal A5 action — it raises [onContinue] with the chosen slot (UTC start +
 * tz) for A6 to confirm.
 */
@Composable
fun SlotPickerScreen(
    args: SlotPickerArgs,
    onBack: () -> Unit,
    onContinue: (SlotSelection) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SlotPickerViewModel = hiltViewModel(),
) {
    LaunchedEffect(args) { viewModel.start(args) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showTzSheet by remember { mutableStateOf(false) }

    SlotPickerContent(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onSelectDay = viewModel::selectDay,
        onSelectSlot = viewModel::selectSlot,
        onPrevMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        onSeeNextAvailable = viewModel::seeNextAvailable,
        onTimezoneClick = { showTzSheet = true },
        onRetry = viewModel::retry,
        onContinue = {
            val slot = viewModel.selectedSlot()
            val content = state as? SlotPickerUiState.Content
            if (slot != null && content != null) {
                onContinue(
                    SlotSelection(
                        slug = args.slug,
                        oneOffToken = args.oneOffToken,
                        eventTypeSlug = args.eventTypeSlug,
                        eventTypeName = args.eventTypeName,
                        startAtUtc = slot.start,
                        startLocalLabel = slotTimeLabel(slot),
                        dayLabel = content.selectedDayHeading.orEmpty(),
                        durationMin = args.durationMin,
                        timezone = content.tzId,
                    ),
                )
            }
        },
    )

    if (showTzSheet) {
        val content = state as? SlotPickerUiState.Content
        TimezoneSelectorSheet(
            selectedId = content?.tzId ?: args.detectedTimezone,
            detectedId = args.detectedTimezone,
            accent = content?.pillar?.accent ?: PantopusColors.primary600,
            onSelect = viewModel::selectTimezone,
            onDismiss = { showTzSheet = false },
        )
    }
}

/** C6 stateless body — exhaustively switches loading / error / content. */
@Composable
fun SlotPickerContent(
    state: SlotPickerUiState,
    onBack: () -> Unit,
    onSelectDay: (Int) -> Unit,
    onSelectSlot: (SlotDto) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSeeNextAvailable: () -> Unit,
    onTimezoneClick: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(PantopusColors.appBg)) {
        PickerTopBar(onBack = onBack)
        when (state) {
            is SlotPickerUiState.Loading ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.s4),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                ) {
                    DiscoverySkeletonSlots(count = 6)
                }
            is SlotPickerUiState.Error ->
                ErrorState(message = state.message, onRetry = onRetry, modifier = Modifier.fillMaxSize())
            is SlotPickerUiState.Content ->
                SlotPickerLoaded(
                    state = state,
                    onSelectDay = onSelectDay,
                    onSelectSlot = onSelectSlot,
                    onPrevMonth = onPrevMonth,
                    onNextMonth = onNextMonth,
                    onSeeNextAvailable = onSeeNextAvailable,
                    onTimezoneClick = onTimezoneClick,
                    onContinue = onContinue,
                )
        }
    }
}

@Composable
private fun SlotPickerLoaded(
    state: SlotPickerUiState.Content,
    onSelectDay: (Int) -> Unit,
    onSelectSlot: (SlotDto) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSeeNextAvailable: () -> Unit,
    onTimezoneClick: () -> Unit,
    onContinue: () -> Unit,
) {
    val accent = state.pillar.accent
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            SummaryHeader(state = state, onTimezoneClick = onTimezoneClick)
            MonthNavRow(
                monthHasAvailability = state.monthHasAvailability,
                canGoPrevious = state.canGoPreviousMonth,
                accent = accent,
                onPrev = onPrevMonth,
                onNext = onNextMonth,
                onSeeNextAvailable = onSeeNextAvailable,
            )
            MonthCalendar(
                monthLabel = state.monthLabel,
                daysInMonth = state.daysInMonth,
                firstWeekdayIndex = state.firstWeekdayIndex,
                availableDays = state.availableDays,
                selectedDay = state.selectedDay,
                onSelectDay = onSelectDay,
                today = state.today,
                accent = accent,
            )
            SlotRegion(state = state, accent = accent, onSelectSlot = onSelectSlot, onSeeNextAvailable = onSeeNextAvailable)
            // bottom spacer so the docked Continue bar never covers the last row
            if (state.selectedSlotStart != null) Box(modifier = Modifier.size(72.dp))
        }
        if (state.selectedSlotStart != null) {
            ContinueBar(
                dayLabel = state.selectedDayHeading.orEmpty(),
                timeLabel = state.daySlots.firstOrNull { it.start == state.selectedSlotStart }?.let(::slotTimeLabel).orEmpty(),
                accent = accent,
                onContinue = onContinue,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Summary header: event-type tile + the timezone chip. */
@Composable
private fun SummaryHeader(
    state: SlotPickerUiState.Content,
    onTimezoneClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.xl))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                    .padding(horizontal = Spacing.s3, vertical = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(Radii.md)).background(state.pillar.accentBg),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(icon = state.locationIcon, contentDescription = null, size = 16.dp, tint = state.pillar.accent)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.eventTypeName,
                    style = PantopusTextStyle.small,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appText,
                )
                Text(text = state.subLabel, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
            }
        }
        TimezoneChip(label = state.tzLabel, onClick = onTimezoneClick)
    }
}

/** Right-aligned month controls (next-available link + prev/next), above the calendar card. */
@Composable
private fun MonthNavRow(
    monthHasAvailability: Boolean,
    canGoPrevious: Boolean,
    accent: Color,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeeNextAvailable: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        if (monthHasAvailability) {
            Text(
                text = "Next available",
                style = PantopusTextStyle.caption,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier.clickable(onClick = onSeeNextAvailable).padding(horizontal = Spacing.s2, vertical = Spacing.s1),
            )
        }
        NavChevron(icon = PantopusIcon.ChevronLeft, label = "Previous month", enabled = canGoPrevious, onClick = onPrev)
        NavChevron(icon = PantopusIcon.ChevronRight, label = "Next month", enabled = true, onClick = onNext)
    }
}

@Composable
private fun NavChevron(
    icon: PantopusIcon,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(28.dp).clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(
            icon = icon,
            contentDescription = label,
            size = 17.dp,
            tint = if (enabled) PantopusColors.appTextSecondary else PantopusColors.appBorderStrong,
        )
    }
}

/** The slot area: skeleton (loading) / paused-or-empty card (C8) / day-full / the time list. */
@Composable
private fun SlotRegion(
    state: SlotPickerUiState.Content,
    accent: Color,
    onSelectSlot: (SlotDto) -> Unit,
    onSeeNextAvailable: () -> Unit,
) {
    when {
        state.slotsLoading -> {
            state.selectedDayHeading?.let { DayHeading(it) }
            DiscoverySkeletonSlots(count = 6)
        }
        !state.monthHasAvailability ->
            NoAvailabilityState(
                icon = PantopusIcon.CalendarX,
                title = "No open times in ${state.monthLabel.substringBefore(' ')}",
                body = "Availability changes often. Try a later month.",
                primaryLabel = "See ${state.nextMonthLabel}",
                primaryIcon = PantopusIcon.ArrowRight,
                onPrimary = onSeeNextAvailable,
                accent = accent,
            )
        state.selectedDay == null ->
            NoAvailabilityState(
                icon = PantopusIcon.CalendarClock,
                title = "Pick a day with open times",
                body = "Tap a highlighted date above to see its times.",
                primaryLabel = "Jump to next available",
                primaryIcon = PantopusIcon.ArrowRight,
                onPrimary = onSeeNextAvailable,
                accent = accent,
            )
        state.daySlots.isEmpty() -> {
            state.selectedDayHeading?.let { DayHeading(it) }
            DayFullyBookedNotice(onSeeNextAvailable = onSeeNextAvailable, accent = accent)
        }
        else -> {
            state.selectedDayHeading?.let { DayHeading(it) }
            SlotTimeList(
                slots = state.daySlots,
                selectedStart = state.selectedSlotStart,
                onSelect = onSelectSlot,
                accent = accent,
            )
        }
    }
}

@Composable
private fun DayHeading(text: String) {
    Text(
        text = text,
        style = PantopusTextStyle.small,
        fontWeight = FontWeight.Bold,
        color = PantopusColors.appText,
        modifier = Modifier.padding(horizontal = Spacing.s1),
    )
}

/** Docked confirmation bar after a slot is picked — the A5 → A6 hand-off CTA. */
@Composable
private fun ContinueBar(
    dayLabel: String,
    timeLabel: String,
    accent: Color,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(topStart = Radii.xl, topEnd = Radii.xl))
                .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Text(
            text = listOf(dayLabel, timeLabel).filter { it.isNotBlank() }.joinToString(" · "),
            style = PantopusTextStyle.small,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
        )
        AccentFilledButton(
            label = "Continue",
            accent = accent,
            icon = PantopusIcon.ArrowRight,
            onClick = onContinue,
            modifier = Modifier.testTag(SLOT_PICKER_CONTINUE_TAG),
        )
    }
}

@Composable
private fun PickerTopBar(onBack: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .border(width = 1.dp, color = PantopusColors.appBorder, shape = RoundedCornerShape(0.dp))
                .padding(horizontal = Spacing.s2, vertical = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clickable(onClickLabel = "Back", onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = PantopusIcon.ChevronLeft, contentDescription = "Back", size = 20.dp, tint = PantopusColors.appText)
        }
        Text(
            text = "Pick a time",
            style = PantopusTextStyle.body,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.size(34.dp))
    }
}
