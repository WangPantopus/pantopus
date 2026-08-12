@file:Suppress(
    "PackageNaming",
    "MagicNumber",
    "TooManyFunctions",
    "LongMethod",
    "LongParameterList",
)

package app.pantopus.android.ui.screens.homes.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.homes.CalendarEventDto
import app.pantopus.android.data.api.models.homes.OccupantDto
import app.pantopus.android.data.api.models.homes.OccupantsResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.homes.HomeMembersRepository
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.network.NetworkMonitor
import app.pantopus.android.data.scheduling.SchedulingOwner
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingRoutes
import app.pantopus.android.ui.screens.scheduling.bookings.BookingsOwnerRelay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Nav arg key for the Home calendar route. */
const val HOME_CALENDAR_HOME_ID_KEY = "homeId"

/** Render state for the bespoke Home calendar agenda (F1). */
sealed interface HomeCalendarUiState {
    data object Loading : HomeCalendarUiState

    data class Error(val message: String) : HomeCalendarUiState

    /** [empty] is non-null when there are no rows to show (first-run / filtered). */
    data class Loaded(
        val sections: List<HomeAgendaSection>,
        val empty: AgendaEmpty?,
    ) : HomeCalendarUiState
}

/**
 * F1 — Home Calendar / Agenda. Fetches the booking **union**
 * (`GET /api/homes/:id/events`, rows tagged `source:'event'|'booking'`)
 * and projects it into a day-grouped agenda + a 7-day month strip + a
 * member filter row. Mirrors iOS `HomeCalendarViewModel`.
 *
 * Booking rows are read-only — tapping one routes to A8's Booking Detail
 * by route; never persisted as a `HomeCalendarEvent`.
 */
@HiltViewModel
class HomeCalendarViewModel
    internal constructor(
        private val repo: HomesRepository,
        private val membersRepo: HomeMembersRepository,
        private val authRepository: AuthRepository,
        private val networkMonitor: NetworkMonitor,
        private val bookingsOwnerRelay: BookingsOwnerRelay,
        savedStateHandle: SavedStateHandle,
        private val clock: () -> Instant = Instant::now,
        // Device-local display zone — agenda rows and the month strip render
        // the same wall time the event form composes instants from (iOS
        // parity). Tests inject a fixed zone; the all-day heuristic stays
        // pinned to UTC inside HomeAgendaBuilder.
        private val zone: ZoneId = ZoneId.systemDefault(),
    ) : ViewModel() {
        @Inject
        constructor(
            repo: HomesRepository,
            membersRepo: HomeMembersRepository,
            authRepository: AuthRepository,
            networkMonitor: NetworkMonitor,
            bookingsOwnerRelay: BookingsOwnerRelay,
            savedStateHandle: SavedStateHandle,
        ) : this(
            repo,
            membersRepo,
            authRepository,
            networkMonitor,
            bookingsOwnerRelay,
            savedStateHandle,
            Instant::now,
            ZoneId.systemDefault(),
        )

        private val homeId: String =
            checkNotNull(savedStateHandle.get<String>(HOME_CALENDAR_HOME_ID_KEY)) {
                "HomeCalendarViewModel requires a $HOME_CALENDAR_HOME_ID_KEY nav argument"
            }

        val isOnline: StateFlow<Boolean> get() = networkMonitor.isOnline

        private val _state = MutableStateFlow<HomeCalendarUiState>(HomeCalendarUiState.Loading)
        val state: StateFlow<HomeCalendarUiState> = _state.asStateFlow()

        private val _monthStrip = MutableStateFlow<MonthStripState?>(null)
        val monthStrip: StateFlow<MonthStripState?> = _monthStrip.asStateFlow()

        private val _filterChips = MutableStateFlow<List<MemberFilter>>(listOf(MemberFilter.All, MemberFilter.Mine))
        val filterChips: StateFlow<List<MemberFilter>> = _filterChips.asStateFlow()

        private val _memberFilter = MutableStateFlow<MemberFilter>(MemberFilter.All)
        val memberFilter: StateFlow<MemberFilter> = _memberFilter.asStateFlow()

        private var events: List<CalendarEventDto> = emptyList()
        private var membersMap: Map<String, HomeMember> = emptyMap()
        private var resolvedUserId: String? = null
        private var weekAnchorIso: String = HomeAgendaBuilder.weekAnchorIso(clock(), zone)
        private var selectedIsoDate: String? = null

        private var onAddEvent: () -> Unit = {}
        private var onOpenEvent: (String) -> Unit = {}
        private var onNavigate: (String) -> Unit = {}

        fun configureNavigation(
            onAddEvent: () -> Unit = {},
            onOpenEvent: (String) -> Unit = {},
            onNavigate: (String) -> Unit = {},
        ) {
            this.onAddEvent = onAddEvent
            this.onOpenEvent = onOpenEvent
            this.onNavigate = onNavigate
        }

        // MARK: - Lifecycle

        fun load() {
            _state.value = HomeCalendarUiState.Loading
            fetch()
        }

        fun refresh() {
            fetch()
        }

        private fun fetch() {
            viewModelScope.launch {
                if (resolvedUserId == null) resolvedUserId = signedInUserId()
                val eventsTask = async { repo.getHomeEvents(homeId) }
                val membersTask = async { membersRepo.listOccupants(homeId) }
                val eventsResult = eventsTask.await()
                applyMembers(membersTask.await())
                when (eventsResult) {
                    is NetworkResult.Success -> {
                        events = eventsResult.data.events
                        rebuild()
                    }
                    is NetworkResult.Failure -> {
                        events = emptyList()
                        // Keep the last-built month strip so a refresh failure
                        // after a successful load retains the chrome (design
                        // FrameError keeps the strip; mirrors iOS, which never
                        // clears `monthStrip` on error).
                        _state.value =
                            HomeCalendarUiState.Error(
                                eventsResult.error.message ?: "Couldn't load your calendar.",
                            )
                    }
                }
            }
        }

        private fun applyMembers(result: NetworkResult<OccupantsResponse>) {
            val occupants =
                when (result) {
                    is NetworkResult.Success -> result.data.occupants.filter { it.isActive }
                    is NetworkResult.Failure -> emptyList()
                }
            val members = occupants.map(::projectMember)
            membersMap = members.associateBy { it.id }
            _filterChips.value =
                buildList {
                    add(MemberFilter.All)
                    add(MemberFilter.Mine)
                    members.forEach { add(MemberFilter.Member(it.id, it.name)) }
                }
        }

        private fun projectMember(occupant: OccupantDto): HomeMember {
            val name =
                occupant.displayName?.takeIf { it.isNotBlank() }
                    ?: occupant.username
                    ?: "Member"
            return HomeMember(
                id = occupant.userId,
                name = name,
                initials = HomeMember.initialsFor(name),
                isYou = occupant.userId == resolvedUserId,
            )
        }

        // MARK: - Mutators

        fun selectDay(isoDate: String) {
            if (selectedIsoDate == isoDate) {
                selectedIsoDate = null
            } else {
                selectedIsoDate = isoDate
                runCatching { LocalDate.parse(isoDate) }.getOrNull()?.let {
                    weekAnchorIso = HomeAgendaBuilder.weekAnchorIso(it.atStartOfDay(zone).toInstant(), zone)
                }
            }
            rebuild()
        }

        fun shiftWeek(direction: WeekShift) {
            val delta = if (direction == WeekShift.Previous) -7L else 7L
            val anchor = runCatching { LocalDate.parse(weekAnchorIso) }.getOrNull() ?: return
            weekAnchorIso = anchor.plusDays(delta).toString()
            rebuild()
        }

        fun jumpToToday() {
            selectedIsoDate = null
            weekAnchorIso = HomeAgendaBuilder.weekAnchorIso(clock(), zone)
            rebuild()
        }

        fun selectFilter(filter: MemberFilter) {
            _memberFilter.value = filter
            rebuild()
        }

        fun clearMemberFilter() {
            _memberFilter.value = MemberFilter.All
            rebuild()
        }

        // MARK: - Navigation

        fun openAgendaItem(item: HomeAgendaItem) {
            if (item.isBooking && item.bookingId != null) {
                // Booking-union rows belong to THIS home — stash the home owner
                // for the arg-less detail route (iOS parity:
                // `.bookingDetail(owner: .home(homeId), …)`), else the detail
                // screen falls back to a stale relay value or Personal.
                bookingsOwnerRelay.pending = SchedulingOwner.Home(homeId)
                onNavigate(SchedulingRoutes.bookingDetail(item.bookingId))
            } else {
                onOpenEvent(item.eventId ?: item.id)
            }
        }

        fun openWhosFree() {
            onNavigate(SchedulingRoutes.WHOS_FREE)
        }

        fun onCreateAction(action: HomeCreateAction) {
            when (action) {
                HomeCreateAction.AddEvent -> onAddEvent()
                HomeCreateAction.FindATime -> onNavigate(SchedulingRoutes.FIND_A_TIME)
                // Carry this calendar's home so F9/F13 act on it, not an inferred one.
                HomeCreateAction.BookResource -> onNavigate(SchedulingRoutes.resourceList(homeId))
                HomeCreateAction.ScheduleVisit -> onNavigate(SchedulingRoutes.visitSetup(homeId))
            }
        }

        fun addEvent() {
            onAddEvent()
        }

        // MARK: - Projection

        private fun rebuild() {
            val now = clock()
            _monthStrip.value =
                HomeAgendaBuilder.weekStrip(
                    events = events,
                    anchorIso = weekAnchorIso,
                    selectedIso = selectedIsoDate,
                    now = now,
                    zone = zone,
                )
            val onlyUser =
                when (val filter = _memberFilter.value) {
                    MemberFilter.All -> null
                    MemberFilter.Mine -> resolvedUserId
                    is MemberFilter.Member -> filter.id
                }
            val sections =
                HomeAgendaBuilder.sections(
                    events = events,
                    members = membersMap,
                    now = now,
                    zone = zone,
                    selectedIsoDate = selectedIsoDate,
                    onlyUserId = onlyUser,
                )
            _state.value = HomeCalendarUiState.Loaded(sections = sections, empty = resolveEmpty(sections))
        }

        private fun resolveEmpty(sections: List<HomeAgendaSection>): AgendaEmpty? {
            if (sections.isNotEmpty()) return null
            if (events.isEmpty()) return AgendaEmpty.FirstRun
            return when (val filter = _memberFilter.value) {
                is MemberFilter.Member -> AgendaEmpty.FilteredMember(filter.name)
                MemberFilter.Mine -> AgendaEmpty.FilteredMember("you")
                MemberFilter.All -> if (selectedIsoDate != null) AgendaEmpty.FilteredDay else AgendaEmpty.FirstRun
            }
        }

        private fun signedInUserId(): String? = (authRepository.state.value as? AuthRepository.State.SignedIn)?.user?.id

        enum class WeekShift { Previous, Next }
    }
