@file:Suppress("PackageNaming", "TooManyFunctions", "MagicNumber")

package app.pantopus.android.ui.screens.scheduling.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.scheduling.UpdateBookingPageRequest
import app.pantopus.android.data.api.models.scheduling.UpdateNotificationPrefsRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.scheduling.SchedulingOwner
import app.pantopus.android.data.scheduling.SchedulingRepository
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.screens.scheduling._shared.pillar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal val REMINDER_PRESETS = listOf(1440 to "1 day", 60 to "1 hr", 15 to "15 min")

@Immutable
data class NotifRow(
    val key: String,
    val label: String,
    val sub: String?,
    val enabled: Boolean,
    val locked: Boolean = false,
)

@Immutable
data class NotifPrefsData(
    val notifyMe: List<NotifRow>,
    val notifyAttendees: List<NotifRow>,
    val reminderMinutes: List<Int>,
    val paused: Boolean,
    val pushOff: Boolean,
    /** Pillar accent for chips/header tint — defaults to Personal (current data contract is personal-only). */
    val pillar: SchedulingPillar = SchedulingPillar.Personal,
)

@Immutable
sealed interface NotificationPrefsUiState {
    data object Loading : NotificationPrefsUiState

    data class Loaded(val data: NotifPrefsData) : NotificationPrefsUiState

    data class Error(val message: String) : NotificationPrefsUiState
}

/** A4 Notification Preferences. Prefs are PERSONAL-only (no owner); the flexible map round-trips unknown keys. */
@HiltViewModel
class NotificationPrefsViewModel
    @Inject
    constructor(
        private val repo: SchedulingRepository,
    ) : ViewModel() {
        /** Current owner — personal-only for now; here to drive pillar theming without VM refactor later. */
        private val owner: SchedulingOwner = SchedulingOwner.Personal
        private val _state = MutableStateFlow<NotificationPrefsUiState>(NotificationPrefsUiState.Loading)
        val state: StateFlow<NotificationPrefsUiState> = _state.asStateFlow()

        /** The full prefs object as returned by the server — preserved so unknown keys round-trip. */
        private var prefsRoot: Map<String, Any?> = emptyMap()

        /** OS-level push permission state, supplied by the screen (which holds the Android Context). */
        private var pushOff: Boolean = false

        fun load(pushOff: Boolean = this.pushOff) {
            this.pushOff = pushOff
            _state.value = NotificationPrefsUiState.Loading
            viewModelScope.launch {
                val prefsDef = viewModelScope.async { repo.getNotificationPreferences() }
                val pageDef = viewModelScope.async { repo.getBookingPage(SchedulingOwner.Personal) }
                val prefsResult = prefsDef.await()
                val page = (pageDef.await() as? NetworkResult.Success)?.data?.page

                if (prefsResult !is NetworkResult.Success) {
                    _state.value = NotificationPrefsUiState.Error("Couldn't load notification preferences.")
                    return@launch
                }
                prefsRoot = prefsResult.data.prefs
                _state.value =
                    NotificationPrefsUiState.Loaded(
                        NotifPrefsData(
                            notifyMe = buildNotifyMe(),
                            notifyAttendees = buildNotifyAttendees(),
                            reminderMinutes = page?.reminderMinutes ?: emptyList(),
                            paused = page?.isPaused ?: false,
                            pushOff = pushOff,
                            pillar = owner.pillar(),
                        ),
                    )
            }
        }

        fun refresh() = load()

        private fun nested(key: String): Map<String, Any?> =
            @Suppress("UNCHECKED_CAST")
            (prefsRoot[key] as? Map<String, Any?>)
                ?: emptyMap()

        private fun bool(
            map: Map<String, Any?>,
            key: String,
            default: Boolean,
        ): Boolean = (map[key] as? Boolean) ?: default

        private fun buildNotifyMe(): List<NotifRow> {
            val m = nested("notify_me")
            return listOf(
                NotifRow("new_booking", "New booking", "We'll tell you the moment someone books.", bool(m, "new_booking", true)),
                NotifRow("cancellation", "Cancellation", null, bool(m, "cancellation", true)),
                NotifRow("reschedule", "Reschedule", null, bool(m, "reschedule", true)),
                NotifRow("reminder", "Reminder sent", "When your reminder goes out", bool(m, "reminder", true)),
                NotifRow("no_show", "No-show", "Attendee missed the booking", bool(m, "no_show", false)),
                NotifRow("booking_request", "Daily agenda", "Each morning at 8am", bool(m, "booking_request", false)),
            )
        }

        private fun buildNotifyAttendees(): List<NotifRow> {
            val a = nested("notify_attendees")
            return listOf(
                NotifRow("confirmation", "Booking confirmation", "Sent the moment they book", enabled = true, locked = true),
                NotifRow("reminder", "Reminder", "Before the booking starts", bool(a, "reminder", true)),
                NotifRow("reschedule", "Reschedule notice", null, bool(a, "reschedule", true)),
                NotifRow("cancellation", "Cancellation notice", null, bool(a, "cancellation", true)),
            )
        }

        fun toggleNotifyMe(key: String) {
            val loaded = _state.value as? NotificationPrefsUiState.Loaded ?: return
            val updated = loaded.data.notifyMe.map { if (it.key == key && !it.locked) it.copy(enabled = !it.enabled) else it }
            _state.value = NotificationPrefsUiState.Loaded(loaded.data.copy(notifyMe = updated))
            persistPrefs(updated, loaded.data.notifyAttendees)
        }

        fun toggleNotifyAttendees(key: String) {
            val loaded = _state.value as? NotificationPrefsUiState.Loaded ?: return
            val updated = loaded.data.notifyAttendees.map { if (it.key == key && !it.locked) it.copy(enabled = !it.enabled) else it }
            _state.value = NotificationPrefsUiState.Loaded(loaded.data.copy(notifyAttendees = updated))
            persistPrefs(loaded.data.notifyMe, updated)
        }

        private fun persistPrefs(
            notifyMe: List<NotifRow>,
            notifyAttendees: List<NotifRow>,
        ) {
            val meMap = notifyMe.associate { it.key to it.enabled }
            val attMap = notifyAttendees.associate { it.key to it.enabled } + ("confirmation" to true)
            val root = prefsRoot.toMutableMap()
            root["notify_me"] = meMap
            root["notify_attendees"] = attMap
            prefsRoot = root
            viewModelScope.launch {
                when (val r = repo.updateNotificationPreferences(UpdateNotificationPrefsRequest(prefs = root))) {
                    is NetworkResult.Success -> prefsRoot = r.data.prefs
                    is NetworkResult.Failure -> load()
                }
            }
        }

        fun toggleReminder(minutes: Int) {
            val loaded = _state.value as? NotificationPrefsUiState.Loaded ?: return
            val current = loaded.data.reminderMinutes
            val updated = (if (minutes in current) current - minutes else current + minutes).sortedDescending()
            _state.value = NotificationPrefsUiState.Loaded(loaded.data.copy(reminderMinutes = updated))
            viewModelScope.launch {
                if (repo.updateBookingPage(
                        SchedulingOwner.Personal,
                        UpdateBookingPageRequest(reminderMinutes = updated),
                    ) is NetworkResult.Failure
                ) {
                    load()
                }
            }
        }
    }
