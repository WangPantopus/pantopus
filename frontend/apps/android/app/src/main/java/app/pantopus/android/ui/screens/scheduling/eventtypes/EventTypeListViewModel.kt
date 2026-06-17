@file:Suppress("PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.scheduling.eventtypes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.scheduling.CreateEventTypeRequest
import app.pantopus.android.data.api.models.scheduling.EventTypeDto
import app.pantopus.android.data.api.models.scheduling.UpdateEventTypeRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.scheduling.SchedulingError
import app.pantopus.android.data.scheduling.SchedulingErrorDecoder
import app.pantopus.android.data.scheduling.SchedulingFeatureFlags
import app.pantopus.android.data.scheduling.SchedulingOwner
import app.pantopus.android.data.scheduling.SchedulingRepository
import app.pantopus.android.ui.screens.scheduling._shared.MoneyAndFlag
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * B1 Event Type / Service list. One owner-polymorphic catalog: the pillar pill
 * re-scopes via [HomesRepository]/[AuthRepository] (the A0 route is arg-less),
 * the Active/Hidden segment filters by `is_active`, the per-row toggle and the
 * overflow menu (copy link / duplicate / share / hide / delete) act through the
 * repository. `DELETE` that returns `409 HAS_UPCOMING_BOOKINGS` routes into the
 * deactivate-instead prompt (`PUT is_active=false`). Priced/business price
 * labels sit behind [SchedulingFeatureFlags].
 */
@HiltViewModel
class EventTypeListViewModel
    @Inject
    constructor(
        private val repo: SchedulingRepository,
        private val homes: HomesRepository,
        private val auth: AuthRepository,
        private val errors: SchedulingErrorDecoder,
        private val flags: SchedulingFeatureFlags,
        private val ownerRelay: SchedulingEditorOwnerRelay,
    ) : ViewModel() {
        private val _pillar = MutableStateFlow(SchedulingPillar.Personal)
        val pillar: StateFlow<SchedulingPillar> = _pillar.asStateFlow()

        private val _tab = MutableStateFlow(EventTypeTab.Active)
        val tab: StateFlow<EventTypeTab> = _tab.asStateFlow()

        private val _state = MutableStateFlow<EventTypeListUiState>(EventTypeListUiState.Loading)
        val state: StateFlow<EventTypeListUiState> = _state.asStateFlow()

        /** One-shot: a URL to hand to the system share sheet, then cleared. */
        private val _shareRequest = MutableStateFlow<String?>(null)
        val shareRequest: StateFlow<String?> = _shareRequest.asStateFlow()

        /** One-shot: a URL to copy to the clipboard, then cleared. */
        private val _copyRequest = MutableStateFlow<String?>(null)
        val copyRequest: StateFlow<String?> = _copyRequest.asStateFlow()

        /** When non-null, the row whose delete hit `HAS_UPCOMING_BOOKINGS` → offer deactivate. */
        private val _deactivatePrompt = MutableStateFlow<EventTypeRowUi?>(null)
        val deactivatePrompt: StateFlow<EventTypeRowUi?> = _deactivatePrompt.asStateFlow()

        /** One-shot transient message (duplicate / errors). */
        private val _toast = MutableStateFlow<String?>(null)
        val toast: StateFlow<String?> = _toast.asStateFlow()

        /** One-shot: a route to navigate to (template quick-create → its editor). */
        private val _navRequest = MutableStateFlow<String?>(null)
        val navRequest: StateFlow<String?> = _navRequest.asStateFlow()

        private var owner: SchedulingOwner = SchedulingOwner.Personal
        private var started = false
        private var fetchJob: Job? = null

        private var allTypes: List<EventTypeDto> = emptyList()
        private var pageSlug: String? = null
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
                    _state.value = EventTypeListUiState.Loading
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
                    _state.value = EventTypeListUiState.Loading
                    val resolved = resolveOwner(target)
                    if (resolved == null) {
                        _state.value = EventTypeListUiState.Error(noOwnerMessage(target))
                        return@launch
                    }
                    owner = resolved
                    fetch()
                }
        }

        fun selectTab(target: EventTypeTab) {
            if (target == _tab.value) return
            _tab.value = target
            rebuild()
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

        private suspend fun fetch() {
            canEdit = true
            // Booking-page slug (for per-event-type share links); failures are non-fatal.
            pageSlug = repo.getBookingPage(owner).dataOrNull()?.page?.slug

            when (val result = repo.getEventTypes(owner)) {
                is NetworkResult.Success -> {
                    allTypes = result.data.eventTypes.sortedBy { it.sortOrder ?: Int.MAX_VALUE }
                    rebuild()
                }
                is NetworkResult.Failure -> {
                    val decoded = errors.decode(result.error)
                    canEdit = decoded !is SchedulingError.Secret
                    _state.value = EventTypeListUiState.Error(decoded.listMessage())
                }
            }
        }

        private fun rebuild() {
            val pillar = _pillar.value
            val active = allTypes.filter { it.isActive != false }
            val hidden = allTypes.filter { it.isActive == false }
            val visible = if (_tab.value == EventTypeTab.Active) active else hidden
            _state.value =
                EventTypeListUiState.Content(
                    pillar = pillar,
                    tab = _tab.value,
                    rows = visible.map { it.toRowUi(pillar) },
                    activeCount = active.size,
                    hiddenCount = hidden.size,
                    canEdit = canEdit,
                )
        }

        // ─── Row actions ──────────────────────────────────────────────────────

        fun toggleActive(
            id: String,
            active: Boolean,
        ) {
            if (!canEdit) return
            val target = allTypes.firstOrNull { it.id == id } ?: return
            allTypes = allTypes.map { if (it.id == id) it.copy(isActive = active) else it }
            rebuild()
            viewModelScope.launch {
                when (val r = repo.updateEventType(owner, id, UpdateEventTypeRequest(isActive = active))) {
                    is NetworkResult.Success ->
                        allTypes = allTypes.map { if (it.id == id) r.data.eventType else it }
                    is NetworkResult.Failure -> {
                        allTypes = allTypes.map { if (it.id == id) target else it }
                        rebuild()
                        _toast.value = "Couldn't update ${target.name}."
                    }
                }
            }
        }

        fun hide(id: String) = toggleActive(id, active = false)

        fun duplicate(id: String) {
            val src = allTypes.firstOrNull { it.id == id } ?: return
            viewModelScope.launch {
                val body =
                    CreateEventTypeRequest(
                        name = "${src.name} copy",
                        slug = uniqueSlug("${src.slug}-copy"),
                        durations = src.durations.ifEmpty { listOf(src.defaultDuration ?: DEFAULT_DURATION) },
                        description = src.description,
                        color = src.color,
                        defaultDuration = src.defaultDuration,
                        locationMode = src.locationMode,
                        locationDetail = src.locationDetail,
                        visibility = src.visibility,
                        requiresApproval = src.requiresApproval,
                    )
                when (val r = repo.createEventType(owner, body)) {
                    is NetworkResult.Success -> {
                        _toast.value = "Duplicated"
                        refresh()
                    }
                    is NetworkResult.Failure -> _toast.value = "Couldn't duplicate ${src.name}."
                }
            }
        }

        fun delete(id: String) {
            if (!canEdit) return
            val target = allTypes.firstOrNull { it.id == id } ?: return
            viewModelScope.launch {
                when (val r = repo.deleteEventType(owner, id)) {
                    is NetworkResult.Success -> {
                        allTypes = allTypes.filterNot { it.id == id }
                        rebuild()
                    }
                    is NetworkResult.Failure -> {
                        val decoded = errors.decode(r.error)
                        if (decoded is SchedulingError.Generic && decoded.code == CODE_HAS_UPCOMING) {
                            _deactivatePrompt.value = target.toRowUi(_pillar.value)
                        } else {
                            _toast.value = "Couldn't delete ${target.name}."
                        }
                    }
                }
            }
        }

        /** Confirm path from the `HAS_UPCOMING_BOOKINGS` prompt — deactivate instead. */
        fun confirmDeactivate() {
            val row = _deactivatePrompt.value ?: return
            _deactivatePrompt.value = null
            toggleActive(row.id, active = false)
        }

        /** Empty-state template chip → quick-create with [minutes], then open its editor. */
        fun createFromTemplate(minutes: Int) {
            viewModelScope.launch {
                val body =
                    CreateEventTypeRequest(
                        name = "$minutes minute meeting",
                        slug = uniqueSlug("$minutes-min-meeting"),
                        durations = listOf(minutes),
                        defaultDuration = minutes,
                        locationMode = "video",
                    )
                when (val r = repo.createEventType(owner, body)) {
                    is NetworkResult.Success -> _navRequest.value = editorRoute(r.data.eventType.id)
                    is NetworkResult.Failure -> _toast.value = "Couldn't create event type."
                }
            }
        }

        fun navRequestConsumed() {
            _navRequest.value = null
        }

        fun dismissDeactivate() {
            _deactivatePrompt.value = null
        }

        fun copyLink(id: String) {
            shareUrlFor(id)?.let { _copyRequest.value = it } ?: run { _toast.value = "Set up your booking link first." }
        }

        fun share(id: String) {
            shareUrlFor(id)?.let { _shareRequest.value = it } ?: run { _toast.value = "Set up your booking link first." }
        }

        fun shareRequestConsumed() {
            _shareRequest.value = null
        }

        fun copyRequestConsumed() {
            _copyRequest.value = null
        }

        fun toastConsumed() {
            _toast.value = null
        }

        // ─── Navigation routes ──────────────────────────────────────────────────

        fun createRoute(): String {
            ownerRelay.pending = owner
            return SchedulingRoutes.eventTypeEditor(NEW_EVENT_TYPE_ID)
        }

        fun editorRoute(id: String): String {
            ownerRelay.pending = owner
            return SchedulingRoutes.eventTypeEditor(id)
        }

        // ─── Helpers ────────────────────────────────────────────────────────────

        private fun shareUrlFor(id: String): String? {
            val slug = pageSlug ?: return null
            val type = allTypes.firstOrNull { it.id == id } ?: return null
            return "https://pantopus.com/book/$slug/${type.slug}"
        }

        private fun uniqueSlug(base: String): String {
            val taken = allTypes.map { it.slug }.toSet()
            if (base !in taken) return base
            var n = 2
            while ("$base-$n" in taken) n++
            return "$base-$n"
        }

        private fun EventTypeDto.toRowUi(pillar: SchedulingPillar): EventTypeRowUi {
            val mins = defaultDuration ?: durations.firstOrNull()
            val meta = listOfNotNull(mins?.let { "$it min" }, locationShort(locationMode)).joinToString(" · ")
            val showPrice = pillar == SchedulingPillar.Business && flags.paidSchedulingEnabled
            return EventTypeRowUi(
                id = id,
                name = name,
                meta = meta,
                colorHex = color,
                isActive = isActive != false,
                isSecret = visibility == VISIBILITY_SECRET,
                priceLabel = if (showPrice) MoneyAndFlag.formatPrice(priceCents, currency) else null,
                slug = slug,
            )
        }

        private fun noOwnerMessage(target: SchedulingPillar): String =
            when (target) {
                SchedulingPillar.Home -> "No household yet. Create one to add bookable event types."
                SchedulingPillar.Business -> "Couldn't load your business services."
                SchedulingPillar.Personal -> "Couldn't load your event types."
            }

        private fun SchedulingError.listMessage(): String =
            when (this) {
                is SchedulingError.Secret -> "Only owners can edit this catalog."
                is SchedulingError.Generic -> message
                else -> "Couldn't load your event types."
            }

        private fun <T> NetworkResult<T>.dataOrNull(): T? = (this as? NetworkResult.Success)?.data

        companion object {
            const val NEW_EVENT_TYPE_ID = "new"
            private const val CODE_HAS_UPCOMING = "HAS_UPCOMING_BOOKINGS"
            private const val VISIBILITY_SECRET = "secret"
            private const val DEFAULT_DURATION = 30
        }
    }

private fun locationShort(mode: String?): String =
    when (mode) {
        "video" -> "Video"
        "phone" -> "Phone"
        "in_person" -> "In person"
        "custom" -> "Custom"
        "ask" -> "Ask invitee"
        else -> ""
    }
