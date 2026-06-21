@file:Suppress("PackageNaming", "TooManyFunctions", "MagicNumber", "LongMethod", "CyclomaticComplexMethod")

package app.pantopus.android.ui.screens.scheduling.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.scheduling.AvailabilityRuleDto
import app.pantopus.android.data.api.models.scheduling.BookingDto
import app.pantopus.android.data.api.models.scheduling.BookingPageDto
import app.pantopus.android.data.api.models.scheduling.BookingSummaryResponse
import app.pantopus.android.data.api.models.scheduling.ConnectedCalendarDto
import app.pantopus.android.data.api.models.scheduling.EventTypeDto
import app.pantopus.android.data.api.models.scheduling.UpdateBookingPageRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.scheduling.SchedulingError
import app.pantopus.android.data.scheduling.SchedulingErrorDecoder
import app.pantopus.android.data.scheduling.SchedulingOwner
import app.pantopus.android.data.scheduling.SchedulingRepository
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingRoutes
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/**
 * A1 Scheduling Hub. One owner-polymorphic front door: the pillar pill row
 * re-scopes the whole screen (Personal default; Home/Business resolve the
 * owner id from [HomesRepository]/[AuthRepository] since the A0 route is
 * arg-less), the booking-link card is the hero, a master toggle pauses new
 * bookings, and the agenda + manage rows route onward via [onNavigate].
 */
@HiltViewModel
class SchedulingHubViewModel
    @Inject
    constructor(
        private val repo: SchedulingRepository,
        private val homes: HomesRepository,
        private val auth: AuthRepository,
        private val errors: SchedulingErrorDecoder,
    ) : ViewModel() {
        private val _pillar = MutableStateFlow(SchedulingPillar.Personal)
        val pillar: StateFlow<SchedulingPillar> = _pillar.asStateFlow()

        private val _state = MutableStateFlow<SchedulingHubUiState>(SchedulingHubUiState.Loading)
        val state: StateFlow<SchedulingHubUiState> = _state.asStateFlow()

        /** One-shot: the share URL to hand to the system share sheet, then cleared. */
        private val _shareRequest = MutableStateFlow<String?>(null)
        val shareRequest: StateFlow<String?> = _shareRequest.asStateFlow()

        /** One-shot: true briefly after Copy link, drives the toast. */
        private val _copied = MutableStateFlow(false)
        val copied: StateFlow<Boolean> = _copied.asStateFlow()

        private var owner: SchedulingOwner = SchedulingOwner.Personal
        private var started = false
        private var fetchJob: Job? = null
        private var pauseJob: Job? = null

        // Cached for actions that don't re-fetch the whole screen.
        private var page: BookingPageDto? = null
        private var canEdit: Boolean = true

        fun start() {
            if (started) {
                refresh()
            } else {
                started = true
                load()
            }
        }

        fun load() {
            fetchJob?.cancel()
            fetchJob =
                viewModelScope.launch {
                    _state.value = SchedulingHubUiState.Loading
                    fetch()
                }
        }

        fun refresh() {
            fetchJob?.cancel()
            fetchJob = viewModelScope.launch { fetch() }
        }

        fun selectPillar(target: SchedulingPillar) {
            if (target == _pillar.value) return
            _pillar.value = target
            fetchJob?.cancel()
            fetchJob =
                viewModelScope.launch {
                    _state.value = SchedulingHubUiState.Loading
                    val resolved = resolveOwner(target)
                    if (resolved == null) {
                        _state.value =
                            SchedulingHubUiState.Error(
                                when (target) {
                                    SchedulingPillar.Home -> "No household yet. Create one to share a family booking link."
                                    SchedulingPillar.Business -> "Couldn't load your business scheduling."
                                    SchedulingPillar.Personal -> "Couldn't load your scheduling hub."
                                },
                            )
                        return@launch
                    }
                    owner = resolved
                    fetch()
                }
        }

        private suspend fun resolveOwner(target: SchedulingPillar): SchedulingOwner? =
            when (target) {
                SchedulingPillar.Personal -> SchedulingOwner.Personal
                SchedulingPillar.Home ->
                    when (val r = homes.myHomes()) {
                        is NetworkResult.Success -> r.data.homes.firstOrNull()?.id?.let { SchedulingOwner.Home(it) }
                        is NetworkResult.Failure -> null
                    }
                SchedulingPillar.Business ->
                    (auth.state.value as? AuthRepository.State.SignedIn)?.user?.id?.let { SchedulingOwner.Business(it) }
            }

        @Suppress("LongMethod", "CyclomaticComplexMethod")
        private suspend fun fetch() {
            canEdit = true
            val pageResult = repo.getBookingPage(owner)
            val loadedPage =
                when (pageResult) {
                    is NetworkResult.Success -> pageResult.data.page
                    is NetworkResult.Failure -> {
                        val decoded = errors.decode(pageResult.error)
                        canEdit = decoded !is SchedulingError.Secret
                        _state.value = SchedulingHubUiState.Error(decoded.hubMessage())
                        return
                    }
                }
            page = loadedPage

            val pillar = _pillar.value
            val isPersonal = owner is SchedulingOwner.Personal

            // Structured concurrency: these reads are children of this fetch coroutine, so a
            // pillar switch / refresh that cancels fetchJob cancels them too.
            val data =
                coroutineScope {
                    val eventTypesDef = async { repo.getEventTypes(owner).dataOrNull()?.eventTypes.orEmpty() }
                    val summaryDef = async { repo.getBookingsSummary(owner) }
                    val upcomingDef = async { repo.getBookings(owner, status = "upcoming").dataOrNull()?.bookings.orEmpty() }
                    val pendingDef = async { repo.getBookings(owner, status = "pending").dataOrNull()?.bookings.orEmpty() }
                    val availabilityDef = async { if (isPersonal) repo.getAvailability().dataOrNull()?.rules.orEmpty() else emptyList() }
                    val calendarsDef =
                        async { if (isPersonal) repo.getConnectedCalendars().dataOrNull()?.calendars.orEmpty() else emptyList() }
                    HubFetchData(
                        eventTypes = eventTypesDef.await(),
                        summaryResult = summaryDef.await(),
                        upcoming = upcomingDef.await(),
                        pending = pendingDef.await(),
                        availability = availabilityDef.await(),
                        calendars = calendarsDef.await(),
                    )
                }
            val eventTypes = data.eventTypes
            val summaryResult = data.summaryResult
            val upcoming = data.upcoming
            val pending = data.pending
            val availability = data.availability
            val calendars = data.calendars

            val summary = (summaryResult as? NetworkResult.Success)?.data
            val summaryUi = summary?.let { HubSummaryUi.from(it, eventTypes) }
            val summaryFailed = summaryResult is NetworkResult.Failure

            if (eventTypes.isEmpty()) {
                _state.value = SchedulingHubUiState.Empty(pillar = pillar, canEdit = canEdit)
                return
            }

            val zone = loadedPage.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
            val typesById = eventTypes.associateBy { it.id }

            _state.value =
                SchedulingHubUiState.Loaded(
                    pillar = pillar,
                    canEdit = canEdit,
                    isPaused = loadedPage.isPaused,
                    handle = bookingHandle(loadedPage),
                    shareUrl = shareUrl(loadedPage),
                    displayName = displayName(loadedPage, pillar),
                    displayRole = displayRole(loadedPage, pillar, eventTypes),
                    isComposed = !isPersonal,
                    summary = summaryUi,
                    summaryFailed = summaryFailed,
                    agenda = buildAgenda(upcoming, pending, typesById, zone),
                    manageRows = buildManageRows(isPersonal, eventTypes, availability, calendars, pending),
                )
        }

        // ─── Actions ────────────────────────────────────────────────────────

        fun setPaused(paused: Boolean) {
            if (!canEdit) return
            val current = _state.value as? SchedulingHubUiState.Loaded ?: return
            _state.value = current.copy(isPaused = paused)
            pauseJob?.cancel()
            pauseJob =
                viewModelScope.launch {
                    when (val r = repo.updateBookingPage(owner, UpdateBookingPageRequest(isPaused = paused))) {
                        is NetworkResult.Success -> {
                            page = r.data.page
                            val live = _state.value as? SchedulingHubUiState.Loaded ?: return@launch
                            _state.value = live.copy(isPaused = r.data.page.isPaused)
                        }
                        is NetworkResult.Failure -> {
                            val decoded = errors.decode(r.error)
                            if (decoded is SchedulingError.Secret) canEdit = false
                            val live = _state.value as? SchedulingHubUiState.Loaded ?: return@launch
                            // Only revert if our optimistic value still stands (no newer toggle won the race).
                            if (live.isPaused == paused) _state.value = live.copy(isPaused = !paused, canEdit = canEdit)
                        }
                    }
                }
        }

        fun copyLink() {
            _copied.value = true
        }

        fun copyToastShown() {
            _copied.value = false
        }

        fun shareLink() {
            val live = _state.value as? SchedulingHubUiState.Loaded ?: return
            if (live.shareUrl.isNotEmpty()) _shareRequest.value = live.shareUrl
        }

        fun shareRequestConsumed() {
            _shareRequest.value = null
        }

        fun footerTapped() {
            val live = _state.value as? SchedulingHubUiState.Loaded ?: return
            if (live.isPaused) setPaused(false) else shareLink()
        }

        // ─── Derived display strings ──────────────────────────────────────────

        private fun bookingHandle(p: BookingPageDto): String =
            if (p.slug.isNullOrBlank()) "pantopus.com/book/…" else "pantopus.com/book/${p.slug}"

        private fun shareUrl(p: BookingPageDto): String = if (p.slug.isNullOrBlank()) "" else "https://pantopus.com/book/${p.slug}"

        private fun displayName(
            p: BookingPageDto,
            pillar: SchedulingPillar,
        ): String =
            p.title?.takeIf { it.isNotBlank() } ?: when (pillar) {
                SchedulingPillar.Personal -> "Your booking page"
                SchedulingPillar.Home -> "Household"
                SchedulingPillar.Business -> "Your business"
            }

        private fun displayRole(
            p: BookingPageDto,
            pillar: SchedulingPillar,
            eventTypes: List<EventTypeDto>,
        ): String {
            p.tagline?.takeIf { it.isNotBlank() }?.let { return it }
            return when (pillar) {
                SchedulingPillar.Home -> "Household booking"
                SchedulingPillar.Business -> if (eventTypes.size == 1) "1 service" else "${eventTypes.size} services"
                SchedulingPillar.Personal -> {
                    val first = eventTypes.firstOrNull()
                    val mins = first?.defaultDuration ?: first?.durations?.firstOrNull()
                    if (mins != null) "$mins min" else "Booking page"
                }
            }
        }

        // ─── Agenda projection ────────────────────────────────────────────────

        private fun buildAgenda(
            upcoming: List<BookingDto>,
            pending: List<BookingDto>,
            typesById: Map<String, EventTypeDto>,
            zone: ZoneId,
        ): List<HubAgendaSection> {
            val merged = (upcoming + pending).associateBy { it.id }.values
            val rows =
                merged
                    .mapNotNull { booking ->
                        val start = parseInstant(booking.startAt) ?: return@mapNotNull null
                        booking to start
                    }.sortedBy { it.second }

            if (rows.isEmpty()) return emptyList()

            val today = ZonedDateTime.now(zone).toLocalDate()
            val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
            val subFmt = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)

            val grouped = LinkedHashMap<String, MutableList<HubBookingRowUi>>()
            val headers = LinkedHashMap<String, Pair<String, String>>()

            rows.forEach { (booking, instant) ->
                val zdt = instant.atZone(zone)
                val date = zdt.toLocalDate()
                val key: String
                val header: String
                when {
                    date.isBefore(today) -> {
                        key = "past"
                        header = "Earlier"
                    }
                    date == today -> {
                        key = "today"
                        header = "Today"
                    }
                    date == today.plusDays(1) -> {
                        key = "tomorrow"
                        header = "Tomorrow"
                    }
                    else -> {
                        key = date.toString()
                        header = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).uppercase(Locale.US)
                    }
                }
                headers.getOrPut(key) { header to zdt.format(subFmt) }
                val type = booking.eventTypeId?.let { typesById[it] }
                grouped.getOrPut(key) { mutableListOf() }.add(
                    HubBookingRowUi(
                        id = booking.id,
                        kind = locationKind(type?.locationMode),
                        title = type?.name ?: "Booking",
                        timeLabel = zdt.format(timeFmt),
                        metaLabel = durationMeta(type),
                        bookerName = booking.inviteeName ?: booking.inviteeEmail ?: "Invitee",
                        bookerInitials = initials(booking.inviteeName ?: booking.inviteeEmail ?: "?"),
                        bookerTone = toneFor(booking.id),
                        status = booking.status ?: "confirmed",
                    ),
                )
            }
            return grouped.map { (key, rowList) ->
                val (header, sub) = headers.getValue(key)
                HubAgendaSection(key = key, header = header, sub = sub, rows = rowList)
            }
        }

        private fun durationMeta(type: EventTypeDto?): String {
            val mins = type?.defaultDuration ?: type?.durations?.firstOrNull()
            val location = locationLabel(type?.locationMode)
            return listOfNotNull(mins?.let { "$it min" }, location.takeIf { it.isNotEmpty() }).joinToString(" · ")
        }

        // ─── Manage rows ──────────────────────────────────────────────────────

        private fun buildManageRows(
            isPersonal: Boolean,
            eventTypes: List<EventTypeDto>,
            availability: List<AvailabilityRuleDto>,
            calendars: List<ConnectedCalendarDto>,
            pending: List<BookingDto>,
        ): List<HubManageItem> {
            val activeCount = eventTypes.count { it.isActive != false }
            val rows = mutableListOf<HubManageItem>()
            rows +=
                HubManageItem(
                    id = "eventTypes",
                    icon = PantopusIcon.LayoutGrid,
                    label = "Event types",
                    value = if (activeCount == 1) "1 active" else "$activeCount active",
                    route = SchedulingRoutes.EVENT_TYPE_LIST,
                )
            rows +=
                if (isPersonal) {
                    HubManageItem(
                        id = "availability",
                        icon = PantopusIcon.Clock,
                        label = "Availability",
                        value = availabilitySummary(availability) ?: "Set hours",
                        route = SchedulingRoutes.AVAILABILITY_LIST,
                    )
                } else {
                    HubManageItem(
                        id = "memberAvailability",
                        icon = PantopusIcon.Users,
                        label = "Member availability",
                        value = null,
                        route = SchedulingRoutes.AVAILABILITY_LIST,
                    )
                }
            rows +=
                HubManageItem(
                    id = "calendars",
                    icon = PantopusIcon.CalendarSync,
                    label = "Connected calendars",
                    value = connectedCalendarsValue(calendars),
                    route = SchedulingRoutes.CONNECTED_CALENDARS,
                )
            if (pending.isNotEmpty()) {
                rows +=
                    HubManageItem(
                        id = "bookings",
                        icon = PantopusIcon.Inbox,
                        label = "Bookings",
                        value = if (pending.size == 1) "1 needs approval" else "${pending.size} need approval",
                        alert = true,
                        route = SchedulingRoutes.BOOKINGS_INBOX,
                    )
            }
            if (canEdit) {
                rows +=
                    HubManageItem(
                        id = "settings",
                        icon = PantopusIcon.Settings,
                        label = "Settings",
                        value = null,
                        route = SchedulingRoutes.SETTINGS,
                    )
            }
            return rows
        }

        private fun availabilitySummary(rules: List<AvailabilityRuleDto>): String? {
            if (rules.isEmpty()) return null
            val weekdays = rules.map { it.weekday }.toSet()
            val isMonFri = weekdays == setOf(1, 2, 3, 4, 5)
            return if (isMonFri) "Mon–Fri, 9–5" else "${weekdays.size} days set"
        }

        private fun connectedCalendarsValue(calendars: List<ConnectedCalendarDto>): String {
            val live = calendars.filter { it.status != "disabled" }.mapNotNull { it.provider }
            if (live.isEmpty()) return "Not connected"
            return live.joinToString(" · ") { it.replaceFirstChar { c -> c.uppercase(Locale.US) } }
        }

        // ─── Navigation route helpers (the screen calls onNavigate with these) ──

        fun setupRoute(): String = SchedulingRoutes.SETUP_WIZARD

        fun onboardingRoute(): String = SchedulingRoutes.ONBOARDING

        fun startSetupRoute(): String =
            if (owner is SchedulingOwner.Personal) SchedulingRoutes.SETUP_WIZARD else SchedulingRoutes.ONBOARDING

        fun bookingsRoute(): String = SchedulingRoutes.BOOKINGS_INBOX

        private fun parseInstant(raw: String?): Instant? {
            if (raw.isNullOrBlank()) return null
            return runCatching { Instant.parse(raw) }
                .recoverCatching { OffsetDateTime.parse(raw).toInstant() }
                .getOrNull()
        }

        private fun SchedulingError.hubMessage(): String =
            when (this) {
                is SchedulingError.Secret -> "You don't have access to this scheduling hub. Ask an owner for access."
                is SchedulingError.Generic -> message
                else -> "Couldn't load your scheduling hub."
            }

        private fun <T> NetworkResult<T>.dataOrNull(): T? = (this as? NetworkResult.Success)?.data
    }

private fun locationLabel(mode: String?): String =
    when (mode) {
        "video" -> "Video call"
        "phone" -> "Phone"
        "in_person" -> "In person"
        "custom" -> "Custom"
        "ask" -> "Ask invitee"
        else -> ""
    }

private fun locationKind(mode: String?): HubBookingKind =
    when (mode) {
        "video" -> HubBookingKind.Video
        "phone" -> HubBookingKind.Phone
        "in_person" -> HubBookingKind.InPerson
        else -> HubBookingKind.Consult
    }

private fun initials(name: String): String {
    val parts = name.trim().split(" ", "@").filter { it.isNotBlank() }
    if (parts.isEmpty()) return "?"
    return parts.take(2).joinToString("") { it.first().uppercase() }
}

private fun toneFor(seed: String): HubAvatarTone {
    val tones = HubAvatarTone.entries
    val idx = (seed.hashCode() and Int.MAX_VALUE) % tones.size
    return tones[idx]
}

/** Bundle of the hub's parallel reads, awaited together inside a [coroutineScope]. */
private data class HubFetchData(
    val eventTypes: List<EventTypeDto>,
    val summaryResult: NetworkResult<BookingSummaryResponse>,
    val upcoming: List<BookingDto>,
    val pending: List<BookingDto>,
    val availability: List<AvailabilityRuleDto>,
    val calendars: List<ConnectedCalendarDto>,
)
