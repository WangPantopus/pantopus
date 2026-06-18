@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.scheduling.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.scheduling.BookingPageDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.scheduling.SchedulingError
import app.pantopus.android.data.scheduling.SchedulingErrorDecoder
import app.pantopus.android.data.scheduling.SchedulingFeatureFlags
import app.pantopus.android.data.scheduling.SchedulingOwner
import app.pantopus.android.data.scheduling.SchedulingRepository
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

private const val SAVED_TOAST_MS = 2000L
private const val MIN_PER_DAY = 1440
private const val MIN_PER_HOUR = 60

@Immutable
sealed interface SchedulingSettingsUiState {
    data object Loading : SchedulingSettingsUiState

    data class Loaded(val data: SettingsData) : SchedulingSettingsUiState

    data class Error(val message: String) : SchedulingSettingsUiState
}

@Immutable
data class SettingsData(
    val slug: String?,
    val isFresh: Boolean,
    val isBusiness: Boolean,
    val paidEnabled: Boolean,
    val remindersValue: String?,
    val timezoneValue: String,
    val paymentsConnected: Boolean,
    val monoFooter: String,
)

/** A3 Scheduling Settings Root ("Booking settings"). Personal default (arg-less route). */
@HiltViewModel
class SchedulingSettingsRootViewModel
    @Inject
    constructor(
        private val repo: SchedulingRepository,
        private val errors: SchedulingErrorDecoder,
        private val featureFlags: SchedulingFeatureFlags,
    ) : ViewModel() {
        private val owner: SchedulingOwner = SchedulingOwner.Personal

        private val _state = MutableStateFlow<SchedulingSettingsUiState>(SchedulingSettingsUiState.Loading)
        val state: StateFlow<SchedulingSettingsUiState> = _state.asStateFlow()

        private val _toast = MutableStateFlow<String?>(null)
        val toast: StateFlow<String?> = _toast.asStateFlow()

        fun load() {
            _state.value = SchedulingSettingsUiState.Loading
            viewModelScope.launch {
                val pageResult = repo.getBookingPage(owner)
                val page =
                    when (pageResult) {
                        is NetworkResult.Success -> pageResult.data.page
                        is NetworkResult.Failure -> {
                            _state.value = SchedulingSettingsUiState.Error(errors.decode(pageResult.error).settingsMessage())
                            return@launch
                        }
                    }
                val paymentsDef = viewModelScope.async { repo.getPaymentsStatus(owner) }
                val eventTypesDef = viewModelScope.async { repo.getEventTypes(owner) }
                val payments = (paymentsDef.await() as? NetworkResult.Success)?.data
                val eventTypeCount = (eventTypesDef.await() as? NetworkResult.Success)?.data?.eventTypes?.size ?: 0
                _state.value = SchedulingSettingsUiState.Loaded(buildData(page, payments?.connected == true, eventTypeCount))
            }
        }

        fun refresh() = load()

        private fun buildData(
            page: BookingPageDto,
            paymentsConnected: Boolean,
            eventTypeCount: Int,
        ): SettingsData {
            val isFresh = page.reminderMinutes.isEmpty() && eventTypeCount == 0
            val zone = page.timezone ?: ZoneId.systemDefault().id
            return SettingsData(
                slug = page.slug,
                isFresh = isFresh,
                isBusiness = owner is SchedulingOwner.Business,
                paidEnabled = featureFlags.paidSchedulingEnabled,
                remindersValue = page.reminderMinutes.sortedDescending().joinToString(" · ") { reminderLabel(it) }.ifBlank { null },
                timezoneValue = "$zone · auto",
                paymentsConnected = paymentsConnected,
                monoFooter = "pantopus.com/book/${page.slug ?: "…"} · owner · you",
            )
        }

        fun resetSlug() {
            viewModelScope.launch {
                when (repo.resetSlug(owner)) {
                    is NetworkResult.Success -> {
                        flashToast("Changes saved")
                        load()
                    }
                    is NetworkResult.Failure -> flashToast("Couldn't reset booking link")
                }
            }
        }

        fun disableScheduling() {
            viewModelScope.launch {
                when (repo.disableBookingPage(owner)) {
                    is NetworkResult.Success -> flashToast("Changes saved")
                    is NetworkResult.Failure -> flashToast("Couldn't disable scheduling")
                }
            }
        }

        private fun flashToast(message: String) {
            viewModelScope.launch {
                _toast.value = message
                delay(SAVED_TOAST_MS)
                _toast.value = null
            }
        }

        // Navigation route helpers.
        fun notificationsRoute() = SchedulingRoutes.NOTIFICATIONS

        fun remindersRoute() = SchedulingRoutes.REMINDERS_QUICK_SETUP

        fun workflowsRoute() = SchedulingRoutes.WORKFLOWS_LIST

        fun templatesRoute() = SchedulingRoutes.TEMPLATE_LIBRARY

        fun availabilityRoute() = SchedulingRoutes.AVAILABILITY_LIST

        fun cancellationPolicyRoute() = SchedulingRoutes.CANCELLATION_REFUND_POLICY

        fun paymentsRoute() = SchedulingRoutes.PAYMENTS_SETUP

        fun teamRoute() = SchedulingRoutes.TEAM_BOOKING_AVAILABILITY

        private fun reminderLabel(minutes: Int): String =
            when {
                minutes % MIN_PER_DAY == 0 -> "${minutes / MIN_PER_DAY} day"
                minutes % MIN_PER_HOUR == 0 -> "${minutes / MIN_PER_HOUR} hr"
                else -> "$minutes min"
            }

        private fun SchedulingError.settingsMessage(): String =
            (this as? SchedulingError.Generic)?.message ?: "Couldn't load booking settings."
    }
