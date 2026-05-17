@file:Suppress(
    "PackageNaming",
    "MagicNumber",
    "TooManyFunctions",
    "LongMethod",
    "ComplexMethod",
    "CyclomaticComplexMethod",
    "LongParameterList",
)

package app.pantopus.android.ui.screens.homes.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.homes.CalendarEventDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.ui.components.StatusChipVariant
import app.pantopus.android.ui.screens.shared.list_of_rows.BannerConfig
import app.pantopus.android.ui.screens.shared.list_of_rows.BannerCta
import app.pantopus.android.ui.screens.shared.list_of_rows.BannerCtaTint
import app.pantopus.android.ui.screens.shared.list_of_rows.FabAction
import app.pantopus.android.ui.screens.shared.list_of_rows.FabTint
import app.pantopus.android.ui.screens.shared.list_of_rows.FabVariant
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.RowChip
import app.pantopus.android.ui.screens.shared.list_of_rows.RowLeading
import app.pantopus.android.ui.screens.shared.list_of_rows.RowModel
import app.pantopus.android.ui.screens.shared.list_of_rows.RowSection
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTemplate
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTrailing
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Nav arg key for the Home calendar route. */
const val HOME_CALENDAR_HOME_ID_KEY = "homeId"

/**
 * Banner summary projected from the loaded events + clock. Pure value —
 * exposed for tests that exercise the projection without standing the
 * view-model up.
 */
data class HomeCalendarBannerSummary(
    val count: Int,
    val nextLabel: String?,
) {
    val hasContent: Boolean get() = count > 0 || nextLabel != null

    val title: String
        get() =
            when (count) {
                0 -> "Nothing scheduled this week"
                1 -> "1 event this week"
                else -> "$count events this week"
            }

    val subtitle: String?
        get() = nextLabel?.let { "Next · $it" }
}

/**
 * One parsed event row in the agenda. Pure value — exposed for test
 * fixtures.
 */
data class ParsedCalendarEvent(
    val dto: CalendarEventDto,
    val start: Instant,
    val isoDate: String,
)

/**
 * T6.4c (P18) — drives the Home calendar surface. Fetches
 * `GET /api/homes/:id/events` and projects the response into:
 *  - a [MonthStripState] for [MonthStripHeader] (month label, 7-day
 *    window, today index, per-day event dots, user-selected day),
 *  - a [BannerConfig] summary ("N events this week · next: …") with a
 *    "Today" CTA that clears the selection,
 *  - a list of [RowSection]s grouped by relative day bucket (Today /
 *    Tomorrow / day-name / Next week / Later, or a single date section
 *    when a day is selected).
 *
 * The view-model uses an injectable `now` clock so unit tests can drive
 * deterministic section bucketing + month-strip dot counts.
 */
@HiltViewModel
class HomeCalendarViewModel
    internal constructor(
        private val repo: HomesRepository,
        savedStateHandle: SavedStateHandle,
        private val clock: () -> Instant = Instant::now,
        private val zone: ZoneId = ZoneId.of("UTC"),
    ) : ViewModel() {
        @Inject
        constructor(
            repo: HomesRepository,
            savedStateHandle: SavedStateHandle,
        ) : this(repo, savedStateHandle, Instant::now, ZoneId.of("UTC"))

        private val homeId: String =
            checkNotNull(savedStateHandle.get<String>(HOME_CALENDAR_HOME_ID_KEY)) {
                "HomeCalendarViewModel requires a $HOME_CALENDAR_HOME_ID_KEY nav argument"
            }

        private val _state = MutableStateFlow<ListOfRowsUiState>(ListOfRowsUiState.Loading)
        val state: StateFlow<ListOfRowsUiState> = _state.asStateFlow()

        private val _monthStrip = MutableStateFlow<MonthStripState?>(null)
        val monthStrip: StateFlow<MonthStripState?> = _monthStrip.asStateFlow()

        private val _banner = MutableStateFlow<BannerConfig?>(null)
        val banner: StateFlow<BannerConfig?> = _banner.asStateFlow()

        private var events: List<CalendarEventDto> = emptyList()
        /** First (Sunday) day of the visible week. */
        private var weekAnchor: LocalDate = weekAnchorFor(clock().atZone(zone).toLocalDate())
        private var selectedIsoDate: String? = null

        private var onAddEvent: () -> Unit = {}
        private var onOpenEvent: (String) -> Unit = {}

        fun configureNavigation(
            onAddEvent: () -> Unit = {},
            onOpenEvent: (String) -> Unit = {},
        ) {
            this.onAddEvent = onAddEvent
            this.onOpenEvent = onOpenEvent
        }

        // MARK: - Lifecycle

        fun load() {
            refresh()
        }

        fun refresh() {
            _state.value = ListOfRowsUiState.Loading
            viewModelScope.launch {
                when (val result = repo.getHomeEvents(homeId)) {
                    is NetworkResult.Success -> {
                        events = result.data.events
                        rebuild()
                    }
                    is NetworkResult.Failure -> {
                        events = emptyList()
                        _banner.value = null
                        _monthStrip.value = null
                        _state.value = ListOfRowsUiState.Error(result.error.message)
                    }
                }
            }
        }

        // MARK: - Mutators driven by MonthStripHeader

        /** Toggle a day filter. Selecting the already-selected day clears it. */
        fun selectDay(isoDate: String) {
            if (selectedIsoDate == isoDate) {
                selectedIsoDate = null
            } else {
                selectedIsoDate = isoDate
                val parsed = parseIsoDate(isoDate)
                if (parsed != null) {
                    weekAnchor = weekAnchorFor(parsed)
                }
            }
            rebuild()
        }

        /** Roll the visible week by ±7 days. */
        fun shiftWeek(direction: WeekShift) {
            val delta =
                when (direction) {
                    WeekShift.Previous -> -7L
                    WeekShift.Next -> 7L
                }
            weekAnchor = weekAnchor.plusDays(delta)
            rebuild()
        }

        /** Banner "Today" CTA — clears the day filter and re-anchors the week. */
        fun jumpToToday() {
            selectedIsoDate = null
            weekAnchor = weekAnchorFor(clock().atZone(zone).toLocalDate())
            rebuild()
        }

        fun fab(): FabAction =
            FabAction(
                icon = PantopusIcon.Plus,
                contentDescription = "Add event",
                variant = FabVariant.SecondaryCreate,
                tint = FabTint.Home,
                onClick = { onAddEvent() },
            )

        // MARK: - Projection

        @Suppress("ReturnCount")
        private fun rebuild() {
            val now = clock()
            val parsed =
                events.mapNotNull { dto ->
                    val start = parseIsoInstant(dto.startAt) ?: return@mapNotNull null
                    ParsedCalendarEvent(
                        dto = dto,
                        start = start,
                        isoDate = isoDay(start),
                    )
                }.sortedBy { it.start }

            _monthStrip.value = buildMonthStripState(parsed, now)

            if (parsed.isEmpty() && events.isEmpty()) {
                _banner.value = null
                _state.value =
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.CalendarDays,
                        headline = "No events scheduled",
                        subcopy =
                            "Plan chores, repairs, birthdays, and household milestones. " +
                                "Members get notified automatically.",
                        ctaTitle = "Add event",
                        onCta = { onAddEvent() },
                    )
                return
            }

            val selected = selectedIsoDate
            val filtered =
                if (selected != null) {
                    parsed.filter { it.isoDate == selected }
                } else {
                    parsed
                }

            if (selected != null && filtered.isEmpty()) {
                _banner.value = null
                _state.value =
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.CalendarDays,
                        headline = "Nothing on this day",
                        subcopy = "Pick a different day or tap Today to see the full agenda.",
                        ctaTitle = "Add event",
                        onCta = { onAddEvent() },
                    )
                return
            }

            val sections = makeSections(filtered, now, selected)
            _state.value = ListOfRowsUiState.Loaded(sections = sections, hasMore = false)
            _banner.value = buildBanner(parsed, now)
        }

        private fun buildBanner(
            parsed: List<ParsedCalendarEvent>,
            now: Instant,
        ): BannerConfig? {
            val summary = summarize(parsed, now, zone)
            if (!summary.hasContent) return null
            return BannerConfig(
                icon = PantopusIcon.CalendarDays,
                title = summary.title,
                subtitle = summary.subtitle,
                tint = BannerCtaTint.Home,
                cta =
                    BannerCta(
                        label = "Today",
                        accessibilityLabel = "Jump to today",
                        tint = BannerCtaTint.Home,
                        onClick = { jumpToToday() },
                    ),
            )
        }

        private fun buildMonthStripState(
            parsed: List<ParsedCalendarEvent>,
            now: Instant,
        ): MonthStripState {
            val dotCounts = mutableMapOf<String, Int>()
            for (ev in parsed) {
                dotCounts[ev.isoDate] = (dotCounts[ev.isoDate] ?: 0) + 1
            }
            val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
            val dowFmt = DateTimeFormatter.ofPattern("EEE", Locale.US)
            val days =
                (0 until 7).map { offset ->
                    val date = weekAnchor.plusDays(offset.toLong())
                    val iso = date.format(DateTimeFormatter.ISO_DATE)
                    MonthStripState.Day(
                        id = iso,
                        dayOfWeek = dowFmt.format(date),
                        date = date.dayOfMonth,
                        eventCount = dotCounts[iso] ?: 0,
                    )
                }
            return MonthStripState(
                monthLabel = monthFmt.format(weekAnchor),
                days = days,
                selectedIsoDate = selectedIsoDate,
                todayIsoDate = now.atZone(zone).toLocalDate().format(DateTimeFormatter.ISO_DATE),
            )
        }

        /** Bucket events into "Today / Tomorrow / day-name / Next week / Later". */
        @Suppress("LongParameterList")
        internal fun makeSections(
            events: List<ParsedCalendarEvent>,
            now: Instant,
            selectedIsoDate: String?,
        ): List<RowSection> {
            val todayStart = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
            val tomorrowStart =
                now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            val dayAfterTomorrowStart =
                now.atZone(zone).toLocalDate().plusDays(2).atStartOfDay(zone).toInstant()
            val nextWeekStart =
                now.atZone(zone).toLocalDate().plusDays(7).atStartOfDay(zone).toInstant()
            val twoWeeksOut =
                now.atZone(zone).toLocalDate().plusDays(14).atStartOfDay(zone).toInstant()

            if (selectedIsoDate != null) {
                val rows = events.map { rowFor(it) }
                return listOf(
                    RowSection(
                        id = "day-$selectedIsoDate",
                        header = dayHeader(selectedIsoDate),
                        rows = rows,
                    ),
                )
            }

            val today = mutableListOf<RowModel>()
            val tomorrow = mutableListOf<RowModel>()
            val thisWeek = mutableListOf<Pair<Instant, RowModel>>()
            val nextWeek = mutableListOf<RowModel>()
            val later = mutableListOf<RowModel>()

            for (ev in events) {
                if (ev.start.isBefore(todayStart)) continue
                val row = rowFor(ev)
                when {
                    ev.start.isBefore(tomorrowStart) -> today.add(row)
                    ev.start.isBefore(dayAfterTomorrowStart) -> tomorrow.add(row)
                    ev.start.isBefore(nextWeekStart) -> thisWeek.add(ev.start to row)
                    ev.start.isBefore(twoWeeksOut) -> nextWeek.add(row)
                    else -> later.add(row)
                }
            }

            val sections = mutableListOf<RowSection>()
            if (today.isNotEmpty()) {
                sections.add(RowSection(id = "today", header = "Today", rows = today))
            }
            if (tomorrow.isNotEmpty()) {
                sections.add(RowSection(id = "tomorrow", header = "Tomorrow", rows = tomorrow))
            }
            if (thisWeek.isNotEmpty()) {
                val grouped = thisWeek.groupBy { isoDay(it.first) }
                grouped.keys.sorted().forEach { iso ->
                    val bucket = grouped[iso] ?: return@forEach
                    sections.add(
                        RowSection(
                            id = "thisweek-$iso",
                            header = dayHeader(iso),
                            rows = bucket.map { it.second },
                        ),
                    )
                }
            }
            if (nextWeek.isNotEmpty()) {
                sections.add(RowSection(id = "nextweek", header = "Next week", rows = nextWeek))
            }
            if (later.isNotEmpty()) {
                sections.add(RowSection(id = "later", header = "Later", rows = later))
            }
            return sections
        }

        private fun rowFor(event: ParsedCalendarEvent): RowModel {
            val category = CalendarEventCategory.from(event.dto.eventType)
            val timeLabel = formatTime(event.start, event.dto.endAt)
            val timeRangeLabel = formatTimeRange(event.start, event.dto.endAt)
            val metaParts =
                listOfNotNull(
                    event.dto.locationNotes,
                    recurrenceShortLabel(event.dto.recurrenceRule),
                ).filter { it.isNotEmpty() }
            val subtitle =
                if (metaParts.isEmpty()) {
                    timeRangeLabel
                } else {
                    "$timeRangeLabel · ${metaParts.joinToString(" · ")}"
                }
            val attendeeCount = event.dto.assignedTo?.size ?: 0
            val chips = buildList {
                add(
                    RowChip(
                        text = category.label,
                        icon = category.icon,
                        tint =
                            RowChip.Tint.Custom(
                                background = category.background,
                                foreground = category.foreground,
                            ),
                    ),
                )
                if (attendeeCount > 0) {
                    add(
                        RowChip(
                            text = if (attendeeCount == 1) "1 attendee" else "$attendeeCount attendees",
                            icon = PantopusIcon.Users,
                            tint = RowChip.Tint.Status(StatusChipVariant.Neutral),
                        ),
                    )
                }
            }
            return RowModel(
                id = event.dto.id,
                title = event.dto.title,
                subtitle = subtitle,
                template = RowTemplate.StatusChip,
                leading =
                    RowLeading.TypeIcon(
                        icon = category.icon,
                        background = category.background,
                        foreground = category.foreground,
                    ),
                trailing = RowTrailing.None,
                onTap = { onOpenEvent(event.dto.id) },
                body = event.dto.description,
                chips = chips,
                timeMeta = timeLabel,
            )
        }

        // MARK: - Date helpers

        private fun isoDay(instant: Instant): String =
            instant.atZone(zone).toLocalDate().format(DateTimeFormatter.ISO_DATE)

        private fun parseIsoDate(iso: String): LocalDate? =
            runCatching { LocalDate.parse(iso) }.getOrNull()

        /** First day of the week (Sunday) containing [date]. */
        private fun weekAnchorFor(date: LocalDate): LocalDate {
            val daysBack =
                when (date.dayOfWeek) {
                    DayOfWeek.SUNDAY -> 0
                    DayOfWeek.MONDAY -> 1
                    DayOfWeek.TUESDAY -> 2
                    DayOfWeek.WEDNESDAY -> 3
                    DayOfWeek.THURSDAY -> 4
                    DayOfWeek.FRIDAY -> 5
                    DayOfWeek.SATURDAY -> 6
                }
            return date.minusDays(daysBack.toLong())
        }

        private fun formatTime(
            start: Instant,
            endIso: String?,
        ): String {
            val zoned = start.atZone(zone)
            if (endIso == null && isAllDay(zoned)) return "All day"
            return DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(zoned)
        }

        private fun formatTimeRange(
            start: Instant,
            endIso: String?,
        ): String {
            val zoned = start.atZone(zone)
            val end = endIso?.let(::parseIsoInstant)?.atZone(zone)
            val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
            val startLabel = fmt.format(zoned)
            if (end == null) {
                return if (isAllDay(zoned)) "All day" else startLabel
            }
            return "$startLabel – ${fmt.format(end)}"
        }

        private fun isAllDay(zoned: ZonedDateTime): Boolean =
            zoned.hour == 0 && zoned.minute == 0 && zoned.second == 0

        private fun dayHeader(iso: String): String {
            val parsed = parseIsoDate(iso) ?: return iso
            return DateTimeFormatter.ofPattern("EEE MMM d", Locale.US).format(parsed)
        }

        enum class WeekShift {
            Previous,
            Next,
        }

        companion object {
            fun parseIsoInstant(iso: String?): Instant? {
                if (iso.isNullOrBlank()) return null
                return runCatching { Instant.parse(iso) }
                    .recoverCatching {
                        // Bare yyyy-MM-dd fallback for all-day rows.
                        LocalDate
                            .parse(iso)
                            .atStartOfDay(ZoneId.of("UTC"))
                            .toInstant()
                    }.getOrNull()
            }

            /** Pure summary projection — exposed for tests. */
            @JvmStatic
            fun summarize(
                events: List<ParsedCalendarEvent>,
                now: Instant,
                zone: ZoneId,
            ): HomeCalendarBannerSummary {
                if (events.isEmpty()) return HomeCalendarBannerSummary(count = 0, nextLabel = null)
                val weekStart = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
                val weekEnd =
                    now.atZone(zone).toLocalDate().plusDays(7).atStartOfDay(zone).toInstant()
                var thisWeek = 0
                var next: ParsedCalendarEvent? = null
                for (ev in events) {
                    if (!ev.start.isBefore(weekStart) && ev.start.isBefore(weekEnd)) {
                        thisWeek += 1
                    }
                    if (!ev.start.isBefore(now) && next == null) {
                        next = ev
                    }
                }
                val nextLabel =
                    next?.let { ev ->
                        val category = CalendarEventCategory.from(ev.dto.eventType)
                        val timeLabel = nextTimeLabel(ev.start, now, zone)
                        "${ev.dto.title} · $timeLabel (${category.label})"
                    }
                return HomeCalendarBannerSummary(count = thisWeek, nextLabel = nextLabel)
            }

            private fun nextTimeLabel(
                start: Instant,
                now: Instant,
                zone: ZoneId,
            ): String {
                val nowDay = now.atZone(zone).toLocalDate()
                val evDay = start.atZone(zone).toLocalDate()
                val days = Duration.between(nowDay.atStartOfDay(zone), evDay.atStartOfDay(zone)).toDays()
                val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(zone)
                val timeStr = timeFmt.format(start)
                return when (days) {
                    0L -> "$timeStr today"
                    1L -> "$timeStr tomorrow"
                    else -> {
                        val dayFmt = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US).withZone(zone)
                        "$timeStr · ${dayFmt.format(start)}"
                    }
                }
            }

            /**
             * Light human-readable label for an RRULE string. Mirrors iOS
             * `HomeCalendarViewModel.recurrenceShortLabel`.
             */
            fun recurrenceShortLabel(rrule: String?): String? {
                if (rrule.isNullOrEmpty()) return null
                val upper = rrule.uppercase(Locale.ROOT)
                return when {
                    "FREQ=WEEKLY" in upper -> "Repeats weekly"
                    "FREQ=YEARLY" in upper -> "Repeats yearly"
                    "FREQ=MONTHLY" in upper -> "Repeats monthly"
                    "FREQ=DAILY" in upper -> "Repeats daily"
                    else -> "Repeats"
                }
            }
        }
    }
