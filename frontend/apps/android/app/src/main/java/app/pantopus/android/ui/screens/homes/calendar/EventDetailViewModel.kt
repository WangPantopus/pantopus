@file:Suppress(
    "PackageNaming",
    "MagicNumber",
    "LongParameterList",
)

package app.pantopus.android.ui.screens.homes.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.homes.CalendarEventDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomeMembersRepository
import app.pantopus.android.data.homes.HomesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav-arg keys for the Event Detail screen. */
const val EVENT_DETAIL_HOME_ID_KEY = "homeId"
const val EVENT_DETAIL_EVENT_ID_KEY = "eventId"

/** UI state for the read-only Event Detail screen. */
sealed interface EventDetailUiState {
    data object Loading : EventDetailUiState

    data class Loaded(
        val event: CalendarEventDto,
        val attendeeNames: Map<String, String>,
        val isDeleting: Boolean = false,
        val deleteError: String? = null,
    ) : EventDetailUiState

    data class Error(val message: String) : EventDetailUiState
}

/**
 * P2.7 — Read-only Event detail view-model. Fetches the parent events
 * list (no `GET /:eventId` on the backend today) + the household roster
 * in parallel so attendee user-ids can be rendered as display names.
 */
@HiltViewModel
class EventDetailViewModel
    @Inject
    constructor(
        private val repo: HomesRepository,
        private val membersRepo: HomeMembersRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val homeId: String =
            requireNotNull(savedStateHandle[EVENT_DETAIL_HOME_ID_KEY]) {
                "EventDetailViewModel requires a '$EVENT_DETAIL_HOME_ID_KEY' nav arg."
            }
        private val eventId: String =
            requireNotNull(savedStateHandle[EVENT_DETAIL_EVENT_ID_KEY]) {
                "EventDetailViewModel requires a '$EVENT_DETAIL_EVENT_ID_KEY' nav arg."
            }

        private val _state = MutableStateFlow<EventDetailUiState>(EventDetailUiState.Loading)
        val state: StateFlow<EventDetailUiState> = _state.asStateFlow()

        private var onDeleted: () -> Unit = {}

        fun configure(onDeleted: () -> Unit) {
            this.onDeleted = onDeleted
        }

        fun load() {
            _state.value = EventDetailUiState.Loading
            viewModelScope.launch {
                val eventsTask = async { repo.getHomeEvents(homeId) }
                val membersTask = async { membersRepo.listOccupants(homeId) }
                val events = eventsTask.await()
                val members = membersTask.await()
                when (events) {
                    is NetworkResult.Success -> {
                        val match = events.data.events.firstOrNull { it.id == eventId }
                        if (match == null) {
                            _state.value =
                                EventDetailUiState.Error("This event is no longer available.")
                            return@launch
                        }
                        val nameLookup =
                            when (members) {
                                is NetworkResult.Success ->
                                    members.data.occupants.associate { occupant ->
                                        val name =
                                            occupant.displayName?.takeIf { it.isNotBlank() }
                                                ?: occupant.username
                                                ?: "Member"
                                        occupant.userId to name
                                    }
                                is NetworkResult.Failure -> emptyMap()
                            }
                        _state.value =
                            EventDetailUiState.Loaded(
                                event = match,
                                attendeeNames = nameLookup,
                            )
                    }
                    is NetworkResult.Failure ->
                        _state.value =
                            EventDetailUiState.Error(events.error.message ?: "Couldn't load this event.")
                }
            }
        }

        /** Swap the loaded snapshot in place — used by the host after edit. */
        fun replaceLoaded(event: CalendarEventDto) {
            _state.update { current ->
                if (current is EventDetailUiState.Loaded) current.copy(event = event) else current
            }
        }

        fun delete() {
            val loaded = (_state.value as? EventDetailUiState.Loaded) ?: return
            if (loaded.isDeleting) return
            _state.value = loaded.copy(isDeleting = true, deleteError = null)
            viewModelScope.launch {
                when (val result = repo.deleteHomeEvent(homeId, eventId)) {
                    is NetworkResult.Success -> onDeleted()
                    is NetworkResult.Failure ->
                        _state.update { current ->
                            if (current is EventDetailUiState.Loaded) {
                                current.copy(
                                    isDeleting = false,
                                    deleteError = result.error.message ?: "Couldn't delete this event.",
                                )
                            } else {
                                current
                            }
                        }
                }
            }
        }
    }
