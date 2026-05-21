@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.hub.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.homes.CalendarEventDto
import app.pantopus.android.data.api.models.hub.HubTodayResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.hub.HubRepository
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** One calendar row for today, projected from [CalendarEventDto]. */
data class TodayEventRow(
    val id: String,
    val title: String,
    val timeLabel: String,
    val typeLabel: String,
    val icon: PantopusIcon,
)

/** Render payload for the loaded state. Pure → unit-tested. */
data class TodayDetailContent(
    val temperatureFahrenheit: Int?,
    val conditions: String?,
    val aqiLabel: String?,
    val aqiValue: Int?,
    val commute: String?,
    val events: List<TodayEventRow>,
) {
    val hasWeather: Boolean get() = temperatureFahrenheit != null || conditions != null
    val isEmpty: Boolean
        get() =
            temperatureFahrenheit == null && conditions == null && aqiLabel == null &&
                commute == null && events.isEmpty()
}

/**
 * P6.6 — backs the full Today detail behind the Hub's Today card. Weather /
 * AQI / commute from `GET /api/hub/today`; today's events from the primary
 * home's calendar (`GET /api/homes/:id/events`), filtered to the current day.
 */
@HiltViewModel
class TodayDetailViewModel
    @Inject
    constructor(
        private val hubRepository: HubRepository,
        private val homesRepository: HomesRepository,
    ) : ViewModel() {
        sealed interface UiState {
            data object Loading : UiState

            data object Empty : UiState

            data class Loaded(val content: TodayDetailContent) : UiState

            data class Error(val message: String) : UiState
        }

        private val _state = MutableStateFlow<UiState>(UiState.Loading)
        val state: StateFlow<UiState> = _state.asStateFlow()

        fun load() = refresh()

        fun refresh() {
            _state.value = UiState.Loading
            viewModelScope.launch {
                when (val today = hubRepository.today()) {
                    is NetworkResult.Failure ->
                        _state.value = UiState.Error(today.error.message)
                    is NetworkResult.Success -> {
                        val summary = projectToday(today.data)
                        val events = loadTodaysEvents()
                        val content =
                            TodayDetailContent(
                                temperatureFahrenheit = summary.temperatureFahrenheit,
                                conditions = summary.conditions,
                                aqiLabel = summary.aqiLabel,
                                aqiValue = summary.aqiValue,
                                commute = summary.commute,
                                events = events,
                            )
                        _state.value = if (content.isEmpty) UiState.Empty else UiState.Loaded(content)
                    }
                }
            }
        }

        /** Primary home's events for today — best-effort; a missing home or
         * events failure simply leaves the events section empty. */
        private suspend fun loadTodaysEvents(): List<TodayEventRow> {
            val hub = (hubRepository.overview() as? NetworkResult.Success)?.data ?: return emptyList()
            val home = hub.homes.firstOrNull { it.isPrimary } ?: hub.homes.firstOrNull() ?: return emptyList()
            val events =
                (homesRepository.getHomeEvents(home.id) as? NetworkResult.Success)?.data?.events
                    ?: return emptyList()
            return todaysEvents(events, Instant.now(), ZoneId.systemDefault())
        }

        companion object {
            data class TodaySummary(
                val temperatureFahrenheit: Int?,
                val conditions: String?,
                val aqiLabel: String?,
                val aqiValue: Int?,
                val commute: String?,
            )

            /** Extract weather/AQI/commute from the opaque Hub today payload —
             * mirrors `HubViewModel`'s Hub-card projection. */
            fun projectToday(response: HubTodayResponse?): TodaySummary {
                val today = response?.today ?: return TodaySummary(null, null, null, null, null)
                val weather = today["weather"] as? Map<*, *>
                val temperature = (weather?.get("temperatureF") as? Number)?.toInt()
                val conditions = weather?.get("conditions") as? String
                val aqi = today["aqi"] as? Map<*, *>
                val aqiLabel = aqi?.get("label") as? String
                val aqiValue = (aqi?.get("value") as? Number)?.toInt()
                val commute = (today["commute"] as? Map<*, *>)?.get("label") as? String
                return TodaySummary(temperature, conditions, aqiLabel, aqiValue, commute)
            }

            /** Keep only events whose start is the current local day, sorted by
             * start time — the same TODAY bucket the calendar renders. */
            fun todaysEvents(
                events: List<CalendarEventDto>,
                now: Instant,
                zone: ZoneId,
            ): List<TodayEventRow> {
                val todayDate = now.atZone(zone).toLocalDate()
                val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
                return events
                    .mapNotNull { dto -> parseInstant(dto.startAt)?.let { it to dto } }
                    .filter { (instant, _) -> instant.atZone(zone).toLocalDate() == todayDate }
                    .sortedBy { it.first }
                    .map { (instant, dto) ->
                        TodayEventRow(
                            id = dto.id,
                            title = dto.title,
                            timeLabel = instant.atZone(zone).toLocalTime().format(timeFormatter),
                            typeLabel =
                                dto.eventType.replace("_", " ")
                                    .replaceFirstChar { it.titlecase(Locale.getDefault()) },
                            icon = iconFor(dto.eventType),
                        )
                    }
            }

            fun iconFor(eventType: String): PantopusIcon =
                when (eventType.lowercase(Locale.getDefault())) {
                    "repair", "maintenance" -> PantopusIcon.Hammer
                    "pet" -> PantopusIcon.PawPrint
                    "delivery", "trash", "trash_day" -> PantopusIcon.MapPin
                    else -> PantopusIcon.CalendarDays
                }

            private fun parseInstant(iso: String?): Instant? {
                if (iso.isNullOrBlank()) return null
                return runCatching { Instant.parse(iso) }
                    .recoverCatching { LocalDate.parse(iso).atStartOfDay(ZoneId.of("UTC")).toInstant() }
                    .getOrNull()
            }
        }
    }
