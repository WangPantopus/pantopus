@file:Suppress("PackageNaming", "LongMethod", "LongParameterList", "TooManyFunctions", "CyclomaticComplexMethod", "LargeClass", "MatchingDeclarationName")

package app.pantopus.android.ui.screens.scheduling.bookings_extra

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.scheduling.EventTypeDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.scheduling.SchedulingOwner
import app.pantopus.android.data.scheduling.SchedulingRepository
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WaitlistUiState {
    data object Loading : WaitlistUiState

    data class Loaded(val data: WaitlistData) : WaitlistUiState

    data object Empty : WaitlistUiState

    data class Error(val message: String) : WaitlistUiState
}

data class WaitlistData(
    val options: List<EventTypeOption>,
    val selectedId: String,
    val pillar: SchedulingPillar,
    val seatTotal: Int,
    val entries: List<RosterPerson>,
    // Seated-fill is not derivable here: the host waitlist endpoint is keyed on
    // the event type (not a specific slot), so there is no single slot whose
    // filled count we can read. We surface the seat total in the capacity strip
    // and keep promote enabled; an accurate "N of M filled" + capacity-full
    // (disabled promote) state needs a per-slot fill the API doesn't return.
    val filled: Int = 0,
    val isFull: Boolean = false,
    val seatsOpen: Int = seatTotal,
)

/**
 * E13 Waitlist (host management). The A0 route is arg-less, so this screen
 * resolves the event type itself: it lists the host's event types, lets them
 * pick one, then shows that type's `GET /event-types/:id/waitlist` (waiting
 * entries) with `Promote to seat` actions (`POST /waitlist/:id/promote`).
 */
@HiltViewModel
class WaitlistViewModel
    @Inject
    constructor(
        private val repo: SchedulingRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<WaitlistUiState>(WaitlistUiState.Loading)
        val state: StateFlow<WaitlistUiState> = _state.asStateFlow()

        private val _toast = MutableStateFlow<String?>(null)
        val toast: StateFlow<String?> = _toast.asStateFlow()

        private val owner: SchedulingOwner = SchedulingOwner.Personal
        private var options: List<EventTypeOption> = emptyList()
        private var eventTypesById: Map<String, EventTypeDto> = emptyMap()
        private var selectedId: String? = null
        private var started = false

        fun start() {
            if (started) {
                refresh()
            } else {
                started = true
                load()
            }
        }

        fun load() {
            viewModelScope.launch {
                _state.value = WaitlistUiState.Loading
                when (val r = repo.getEventTypes(owner)) {
                    is NetworkResult.Failure -> _state.value = WaitlistUiState.Error("Couldn't load your event types.")
                    is NetworkResult.Success -> {
                        val active = r.data.eventTypes.filter { it.isActive != false }
                        eventTypesById = active.associateBy { it.id }
                        options = active.map { EventTypeOption(it.id, it.name) }
                        if (options.isEmpty()) {
                            _state.value = WaitlistUiState.Empty
                        } else {
                            selectedId = selectedId?.takeIf { id -> id in eventTypesById } ?: options.first().id
                            loadWaitlist()
                        }
                    }
                }
            }
        }

        fun refresh() {
            if (selectedId == null) load() else loadWaitlist()
        }

        fun select(id: String) {
            if (id == selectedId) return
            selectedId = id
            loadWaitlist()
        }

        private fun loadWaitlist() {
            val id = selectedId ?: return
            viewModelScope.launch {
                _state.value = WaitlistUiState.Loading
                val entries =
                    repo.getWaitlist(owner, id).let { r ->
                        when (r) {
                            is NetworkResult.Success -> r.data.waitlist.filter { it.status == "waiting" }
                            is NetworkResult.Failure -> {
                                _state.value = WaitlistUiState.Error("Couldn't load the waitlist.")
                                return@launch
                            }
                        }
                    }
                val rows =
                    entries.mapIndexed { index, entry ->
                        RosterPerson(
                            id = entry.id,
                            name = entry.inviteeName ?: entry.inviteeEmail ?: "Guest",
                            meta = "#${index + 1} · ${BookingsExtrasFormatting.joinedLabel(entry.createdAt)}",
                            status = "waiting",
                        )
                    }
                _state.value =
                    WaitlistUiState.Loaded(
                        WaitlistData(
                            options = options,
                            selectedId = id,
                            pillar = SchedulingPillar.Personal,
                            seatTotal = eventTypesById[id]?.seatCap ?: 1,
                            entries = rows,
                        ),
                    )
            }
        }

        fun promote(entryId: String) {
            viewModelScope.launch {
                when (repo.promoteWaitlist(owner, entryId)) {
                    is NetworkResult.Success -> {
                        _toast.value = "Notified — they can grab the seat."
                        loadWaitlist()
                    }
                    is NetworkResult.Failure -> _toast.value = "Couldn't promote — try again."
                }
            }
        }

        fun toastConsumed() {
            _toast.value = null
        }
    }
