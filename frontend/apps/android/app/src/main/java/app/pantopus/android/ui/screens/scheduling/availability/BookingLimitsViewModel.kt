@file:Suppress("PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.scheduling.availability

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.scheduling.EventTypeDto
import app.pantopus.android.data.api.models.scheduling.UpdateEventTypeRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.scheduling.SchedulingErrorDecoder
import app.pantopus.android.data.scheduling.SchedulingOwner
import app.pantopus.android.data.scheduling.SchedulingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** An event type the limits apply to. */
@Immutable
data class EventTypeOption(
    val id: String,
    val name: String,
)

/** The editable booking-limits form (maps to event-type fields). */
@Immutable
data class BookingLimitsForm(
    val eventTypes: List<EventTypeOption>,
    val selectedId: String,
    val minNoticeHours: Int,
    val bookUpToDays: Int,
    val maxPerDay: Int,
    // TODO(backend): wire to weekly_cap once the API exposes it.
    //  Design shows a "Max per week" StepperRow (booking-limits-frames.jsx:151,173,201).
    //  Rendered as a UI-only placeholder (disabled) until the field exists.
    val maxPerWeek: Int = 20,
    val perPerson: Int,
    val startInterval: StartInterval,
    val saving: Boolean = false,
) {
    val selectedName: String get() = eventTypes.firstOrNull { it.id == selectedId }?.name ?: "Event type"

    /** Window-shorter-than-notice conflict (design's error state). */
    val windowError: Boolean
        get() = bookUpToDays < 1 || bookUpToDays.toLong() * MINUTES_PER_DAY < minNoticeHours.toLong() * MINUTES_PER_HOUR

    val isValid: Boolean get() = !windowError

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
        const val MINUTES_PER_HOUR = 60
    }
}

@Immutable
sealed interface BookingLimitsUiState {
    data object Loading : BookingLimitsUiState

    data object Empty : BookingLimitsUiState

    data class Content(val form: BookingLimitsForm) : BookingLimitsUiState

    data class Error(val message: String) : BookingLimitsUiState
}

sealed interface BookingLimitsEvent {
    data object Saved : BookingLimitsEvent

    data class Toast(val message: String) : BookingLimitsEvent
}

/**
 * B7 — Booking limits & notice rules. These are **event-type** fields
 * (`min_notice_min`, `max_horizon_days`, `slot_interval_min`, `daily_cap`,
 * `per_booker_cap`) edited via `PUT /event-types/:id` — A3 reads/writes them
 * through the repository without entering A2's folder. There is no backend
 * weekly cap, so the design's "Max per week" is intentionally omitted.
 */
@HiltViewModel
class BookingLimitsViewModel
    @Inject
    constructor(
        private val repo: SchedulingRepository,
        private val errors: SchedulingErrorDecoder,
    ) : ViewModel() {
        private val owner: SchedulingOwner = SchedulingOwner.Personal

        private val _state = MutableStateFlow<BookingLimitsUiState>(BookingLimitsUiState.Loading)
        val state: StateFlow<BookingLimitsUiState> = _state.asStateFlow()

        private val _events = Channel<BookingLimitsEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        private var eventTypes: List<EventTypeDto> = emptyList()

        fun load() {
            _state.value = BookingLimitsUiState.Loading
            viewModelScope.launch {
                when (val result = repo.getEventTypes(owner)) {
                    is NetworkResult.Success -> {
                        eventTypes = result.data.eventTypes
                        val first = eventTypes.firstOrNull()
                        _state.value =
                            if (first == null) {
                                BookingLimitsUiState.Empty
                            } else {
                                BookingLimitsUiState.Content(first.toForm(eventTypes))
                            }
                    }
                    is NetworkResult.Failure ->
                        _state.value = BookingLimitsUiState.Error(errors.decode(result.error).displayMessage())
                }
            }
        }

        fun refresh() = load()

        private fun EventTypeDto.toForm(all: List<EventTypeDto>): BookingLimitsForm =
            BookingLimitsForm(
                eventTypes = all.map { EventTypeOption(it.id, it.name) },
                selectedId = id,
                minNoticeHours = (minNoticeMin ?: DEFAULT_NOTICE_MIN) / MIN_PER_HOUR,
                bookUpToDays = maxHorizonDays ?: DEFAULT_HORIZON_DAYS,
                maxPerDay = dailyCap ?: 0,
                perPerson = perBookerCap ?: 0,
                startInterval = StartInterval.fromMinutes(slotIntervalMin),
            )

        fun selectEventType(id: String) {
            val dto = eventTypes.firstOrNull { it.id == id } ?: return
            _state.value = BookingLimitsUiState.Content(dto.toForm(eventTypes))
        }

        private inline fun mutate(transform: (BookingLimitsForm) -> BookingLimitsForm) {
            val form = (_state.value as? BookingLimitsUiState.Content)?.form ?: return
            _state.value = BookingLimitsUiState.Content(transform(form))
        }

        fun changeMinNotice(delta: Int) = mutate { it.copy(minNoticeHours = (it.minNoticeHours + delta).coerceIn(0, MAX_NOTICE_HOURS)) }

        fun changeBookUpTo(delta: Int) = mutate { it.copy(bookUpToDays = (it.bookUpToDays + delta).coerceIn(0, MAX_HORIZON_DAYS)) }

        fun changeMaxPerDay(delta: Int) = mutate { it.copy(maxPerDay = (it.maxPerDay + delta).coerceIn(0, MAX_CAP)) }

        fun changePerPerson(delta: Int) = mutate { it.copy(perPerson = (it.perPerson + delta).coerceIn(0, MAX_PER_PERSON)) }

        fun setStartInterval(interval: StartInterval) = mutate { it.copy(startInterval = interval) }

        fun save() {
            val form = (_state.value as? BookingLimitsUiState.Content)?.form ?: return
            if (!form.isValid || form.saving) return
            _state.value = BookingLimitsUiState.Content(form.copy(saving = true))
            viewModelScope.launch {
                val body =
                    UpdateEventTypeRequest(
                        minNoticeMin = form.minNoticeHours * MIN_PER_HOUR,
                        maxHorizonDays = form.bookUpToDays,
                        slotIntervalMin = form.startInterval.minutes,
                        dailyCap = form.maxPerDay.takeIf { it > 0 },
                        perBookerCap = form.perPerson.takeIf { it > 0 },
                    )
                when (val result = repo.updateEventType(owner, form.selectedId, body)) {
                    is NetworkResult.Success -> _events.send(BookingLimitsEvent.Saved)
                    is NetworkResult.Failure -> {
                        _state.value = BookingLimitsUiState.Content(form.copy(saving = false))
                        _events.send(BookingLimitsEvent.Toast(errors.decode(result.error).displayMessage()))
                    }
                }
            }
        }

        private companion object {
            const val MIN_PER_HOUR = 60
            const val DEFAULT_NOTICE_MIN = 240
            const val DEFAULT_HORIZON_DAYS = 60
            const val MAX_NOTICE_HOURS = 168
            const val MAX_HORIZON_DAYS = 365
            const val MAX_CAP = 50
            const val MAX_PER_PERSON = 20
        }
    }
