@file:Suppress("PackageNaming", "MagicNumber", "TooManyFunctions", "LongMethod", "LongParameterList")

package app.pantopus.android.ui.screens.homes.calendar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.data.api.models.homes.CalendarEventDto
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─── Projection models ────────────────────────────────────────────────────

/** One agenda row. `isBooking` rows are read-only (eventId is null). */
@Immutable
data class HomeAgendaItem(
    val id: String,
    val time: String,
    val ampm: String,
    val title: String,
    val category: CalendarEventCategory,
    val location: String?,
    val members: List<HomeMember>,
    val isBooking: Boolean,
    val bookingStatus: String?,
    val bookingId: String?,
    val eventId: String?,
)

/** A day-grouped agenda section. */
@Immutable
data class HomeAgendaSection(
    val id: String,
    val header: String,
    val items: List<HomeAgendaItem>,
)

/** The member-scoped agenda filter. */
sealed interface MemberFilter {
    data object All : MemberFilter

    data object Mine : MemberFilter

    data class Member(val id: String, val name: String) : MemberFilter
}

/** Empty-agenda reasons. */
sealed interface AgendaEmpty {
    data object FirstRun : AgendaEmpty

    data class FilteredMember(val name: String) : AgendaEmpty

    data object FilteredDay : AgendaEmpty
}

// ─── Pure projection ──────────────────────────────────────────────────────

/**
 * Pure agenda/month-strip projection. Mirrors iOS `HomeAgendaBuilder` —
 * UTC-anchored, Sunday-first, deterministic for unit tests.
 */
object HomeAgendaBuilder {
    private val isoDate = DateTimeFormatter.ISO_DATE
    private val headerFmt = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm", Locale.US)
    private val ampmFmt = DateTimeFormatter.ofPattern("a", Locale.US)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
    private val dowFmt = DateTimeFormatter.ofPattern("EEE", Locale.US)

    fun parseInstant(iso: String?): Instant? {
        if (iso.isNullOrBlank()) return null
        return runCatching { Instant.parse(iso) }
            .recoverCatching { LocalDate.parse(iso).atStartOfDay(ZoneId.of("UTC")).toInstant() }
            .getOrNull()
    }

    private fun isoDay(
        instant: Instant,
        zone: ZoneId,
    ): String = instant.atZone(zone).toLocalDate().format(isoDate)

    private fun isMidnight(zoned: ZonedDateTime): Boolean = zoned.hour == 0 && zoned.minute == 0 && zoned.second == 0

    /** Project a single DTO into an agenda item. */
    fun item(
        dto: CalendarEventDto,
        start: Instant,
        members: Map<String, HomeMember>,
        zone: ZoneId,
    ): HomeAgendaItem {
        val zoned = start.atZone(zone)
        val allDay = dto.endAt == null && isMidnight(zoned)
        val time = if (allDay) "All day" else timeFmt.format(zoned)
        val ampm = if (allDay) "" else ampmFmt.format(zoned)
        val assigned = dto.assignedTo.orEmpty().mapNotNull { members[it] }
        return HomeAgendaItem(
            id = dto.id,
            time = time,
            ampm = ampm,
            title = dto.title,
            category = CalendarEventCategory.from(dto.eventType),
            location = dto.locationNotes?.takeIf { it.isNotBlank() },
            members = assigned,
            isBooking = dto.isBooking,
            bookingStatus = dto.bookingStatus,
            bookingId = dto.bookingId,
            eventId = if (dto.isBooking) null else dto.id,
        )
    }

    /** Day-grouped sections. `selectedIsoDate` pins one day; else past events drop. */
    fun sections(
        events: List<CalendarEventDto>,
        members: Map<String, HomeMember>,
        now: Instant,
        zone: ZoneId,
        selectedIsoDate: String? = null,
        onlyUserId: String? = null,
    ): List<HomeAgendaSection> {
        val parsed =
            events
                .mapNotNull { dto -> parseInstant(dto.startAt)?.let { dto to it } }
                .filter { (dto, _) -> onlyUserId == null || dto.assignedTo.orEmpty().contains(onlyUserId) }
                .sortedBy { it.second }
        val todayStart = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val kept =
            if (selectedIsoDate != null) {
                parsed.filter { isoDay(it.second, zone) == selectedIsoDate }
            } else {
                parsed.filter { !it.second.isBefore(todayStart) }
            }
        return kept
            .groupBy { isoDay(it.second, zone) }
            .toSortedMap()
            .map { (iso, bucket) ->
                HomeAgendaSection(
                    id = iso,
                    header = header(iso, now, zone),
                    items = bucket.map { (dto, start) -> item(dto, start, members, zone) },
                )
            }
    }

    private fun header(
        iso: String,
        now: Instant,
        zone: ZoneId,
    ): String {
        val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
        val stamp = headerFmt.format(date)
        val today = now.atZone(zone).toLocalDate()
        return when (java.time.temporal.ChronoUnit.DAYS.between(today, date)) {
            0L -> "Today · $stamp"
            1L -> "Tomorrow · $stamp"
            else -> stamp
        }
    }

    /** Sunday-anchored start-of-week ISO date for [now]. */
    fun weekAnchorIso(
        now: Instant,
        zone: ZoneId,
    ): String {
        val date = now.atZone(zone).toLocalDate()
        val back =
            when (date.dayOfWeek) {
                java.time.DayOfWeek.SUNDAY -> 0
                java.time.DayOfWeek.MONDAY -> 1
                java.time.DayOfWeek.TUESDAY -> 2
                java.time.DayOfWeek.WEDNESDAY -> 3
                java.time.DayOfWeek.THURSDAY -> 4
                java.time.DayOfWeek.FRIDAY -> 5
                java.time.DayOfWeek.SATURDAY -> 6
            }
        return date.minusDays(back.toLong()).format(isoDate)
    }

    /** Build the 7-day month-strip state from the visible week anchor. */
    fun weekStrip(
        events: List<CalendarEventDto>,
        anchorIso: String,
        selectedIso: String?,
        now: Instant,
        zone: ZoneId,
    ): MonthStripState {
        val anchor = runCatching { LocalDate.parse(anchorIso) }.getOrNull() ?: now.atZone(zone).toLocalDate()
        val dotCounts = mutableMapOf<String, Int>()
        for (dto in events) {
            val start = parseInstant(dto.startAt) ?: continue
            val iso = isoDay(start, zone)
            dotCounts[iso] = (dotCounts[iso] ?: 0) + 1
        }
        val days =
            (0 until 7).map { offset ->
                val date = anchor.plusDays(offset.toLong())
                val iso = date.format(isoDate)
                MonthStripState.Day(
                    id = iso,
                    dayOfWeek = dowFmt.format(date),
                    date = date.dayOfMonth,
                    eventCount = dotCounts[iso] ?: 0,
                )
            }
        return MonthStripState(
            monthLabel = monthFmt.format(anchor),
            days = days,
            selectedIsoDate = selectedIso,
            todayIsoDate = isoDay(now, zone),
        )
    }
}

// ─── Row components ───────────────────────────────────────────────────────

/** The agenda union row. Mirrors iOS `HomeAgendaRowCard`. */
@Composable
fun HomeAgendaRowCard(
    item: HomeAgendaItem,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    enabled: Boolean = true,
    onTap: () -> Unit = {},
) {
    val rowLabel =
        buildString {
            append("${item.time} ${item.ampm}, ${item.title}, ${item.category.label}")
            if (item.isBooking) append(", Booking")
            item.location?.let { append(", $it") }
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .then(if (enabled) Modifier.clickable(onClick = onTap) else Modifier)
                .padding(horizontal = Spacing.s3, vertical = 11.dp)
                .alpha(if (dimmed) 0.55f else 1f)
                .testTag("homeAgendaRow_${item.id}")
                .semantics { contentDescription = rowLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Column(
            modifier = Modifier.width(42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(text = item.time, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
            if (item.ampm.isNotEmpty()) {
                Text(text = item.ampm, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appTextMuted)
            }
        }
        Box(modifier = Modifier.width(1.dp).height(36.dp).background(PantopusColors.appBorder))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appText,
                maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                CategoryChipMini(category = item.category)
                if (item.isBooking) {
                    HomeBookingTag()
                    item.bookingStatus?.let { SchedulingStatusBadge(status = it) }
                }
                item.location?.let { loc ->
                    Text(text = loc, fontSize = 10.5.sp, color = PantopusColors.appTextSecondary, maxLines = 1)
                }
            }
        }
        if (item.members.isNotEmpty()) {
            HomeAvatarStack(members = item.members, size = 26.dp)
        }
    }
}

/** Mini category chip — dot + label. Mirrors iOS `CategoryChipMini`. */
@Composable
fun CategoryChipMini(
    category: CalendarEventCategory,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(PantopusColors.appSurfaceSunken)
                .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(category.foreground))
        Text(text = category.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appTextStrong)
    }
}

/** Loading skeleton row mirroring the loaded geometry. */
@Composable
fun HomeAgendaSkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(horizontal = Spacing.s3, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Column(
            modifier = Modifier.width(42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Shimmer(width = 30.dp, height = 11.dp, cornerRadius = Radii.xs)
            Shimmer(width = 20.dp, height = 8.dp, cornerRadius = Radii.xs)
        }
        Box(modifier = Modifier.width(1.dp).height(36.dp).background(PantopusColors.appBorder))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Shimmer(width = 150.dp, height = 11.dp, cornerRadius = Radii.xs)
            Shimmer(width = 90.dp, height = 9.dp, cornerRadius = Radii.xs)
        }
        Shimmer(width = 26.dp, height = 26.dp, cornerRadius = Radii.pill)
    }
}

/** Horizontal member-filter chip row. Mirrors iOS `FilterChipRow`. */
@Composable
fun FilterChipRow(
    chips: List<MemberFilter>,
    selected: MemberFilter,
    onSelect: (MemberFilter) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .horizontalScroll(scrollState)
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { chip ->
            val label = filterLabel(chip)
            val active = chip == selected
            Row(
                modifier =
                    Modifier
                        .heightIn(min = 30.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (active) PantopusColors.homeBg else PantopusColors.appSurface)
                        .then(
                            if (active) {
                                Modifier
                            } else {
                                Modifier.border(1.dp, PantopusColors.appBorder, RoundedCornerShape(percent = 50))
                            },
                        ).clickable { onSelect(chip) }
                        .padding(horizontal = 13.dp, vertical = 6.dp)
                        .testTag("homeCalendar_filter_$label")
                        .semantics { selected = active },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (active) PantopusColors.homeDark else PantopusColors.appTextStrong,
                )
            }
        }
    }
}

/** Filter chip label: All / Mine / first word of a member's name. */
fun filterLabel(filter: MemberFilter): String =
    when (filter) {
        MemberFilter.All -> "All"
        MemberFilter.Mine -> "Mine"
        is MemberFilter.Member -> filter.name.split(' ').firstOrNull().orEmpty().ifEmpty { filter.name }
    }

/** Section header text for the bespoke agenda body. */
@Composable
fun AgendaSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = PantopusTextStyle.caption,
        fontWeight = FontWeight.Bold,
        color = PantopusColors.appTextSecondary,
        modifier = modifier.padding(horizontal = Spacing.s1, vertical = Spacing.s1),
    )
}
