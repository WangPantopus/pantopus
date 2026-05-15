@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.homes.bills

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.data.analytics.AnalyticsResult
import app.pantopus.android.data.api.models.homes.CreateBillRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.ui.screens.shared.wizard.WizardChrome
import app.pantopus.android.ui.screens.shared.wizard.WizardLeadingControl
import app.pantopus.android.ui.screens.shared.wizard.WizardModel
import app.pantopus.android.ui.screens.shared.wizard.WizardProgressLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Step identifiers for the Add Bill wizard. */
enum class AddBillStep { Details, Schedule, Review, Success }

/** Recurrence options for step 2. */
enum class AddBillSchedule(
    val label: String,
    val detailsKey: String,
) {
    OneTime("One-time", "one_time"),
    Monthly("Recurring monthly", "monthly"),
    Quarterly("Recurring quarterly", "quarterly"),
    Yearly("Recurring yearly", "yearly"),
}

/** Outbound events for the host to react to. */
sealed interface AddBillEvent {
    data object Dismiss : AddBillEvent

    data class Created(val billId: String) : AddBillEvent
}

/** Nav-arg key. */
const val ADD_BILL_HOME_ID_KEY = "homeId"

@HiltViewModel
class AddBillWizardViewModel
    @Inject
    constructor(
        private val repo: HomesRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel(), WizardModel {
        private val homeId: String = checkNotNull(savedStateHandle[ADD_BILL_HOME_ID_KEY]) {
            "AddBillWizardViewModel requires a $ADD_BILL_HOME_ID_KEY nav argument"
        }

        // Step 1
        var payee: String by mutableStateOf("")
        var amount: String by mutableStateOf("")
        var dueDate: LocalDate? by mutableStateOf(null)

        // Step 2
        var schedule: AddBillSchedule by mutableStateOf(AddBillSchedule.OneTime)

        private val _currentStep = MutableStateFlow(AddBillStep.Details)
        val currentStep: StateFlow<AddBillStep> = _currentStep.asStateFlow()

        private val _isSubmitting = MutableStateFlow(false)
        val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

        private val _submitError = MutableStateFlow<String?>(null)
        val submitError: StateFlow<String?> = _submitError.asStateFlow()

        private val _events = MutableStateFlow<AddBillEvent?>(null)
        val events: StateFlow<AddBillEvent?> = _events.asStateFlow()

        private var createdBillId: String? = null

        override val chrome: WizardChrome
            get() =
                when (_currentStep.value) {
                    AddBillStep.Details ->
                        WizardChrome(
                            title = "Add a bill",
                            progressLabel = WizardProgressLabel.StepOf(current = 1, total = 3),
                            progressFraction = 1f / 3f,
                            leading = WizardLeadingControl.Close,
                            primaryCtaLabel = "Next",
                            primaryCtaEnabled = detailsValid(),
                            isSubmitting = false,
                            dirty = isDirty(),
                            showsProgressBar = true,
                        )
                    AddBillStep.Schedule ->
                        WizardChrome(
                            title = "Schedule",
                            progressLabel = WizardProgressLabel.StepOf(current = 2, total = 3),
                            progressFraction = 2f / 3f,
                            leading = WizardLeadingControl.Back,
                            primaryCtaLabel = "Next",
                            primaryCtaEnabled = true,
                            isSubmitting = false,
                            dirty = isDirty(),
                            showsProgressBar = true,
                        )
                    AddBillStep.Review ->
                        WizardChrome(
                            title = "Review",
                            progressLabel = WizardProgressLabel.StepOf(current = 3, total = 3),
                            progressFraction = 1f,
                            leading = WizardLeadingControl.Back,
                            primaryCtaLabel = "Add bill",
                            primaryCtaEnabled = !_isSubmitting.value,
                            isSubmitting = _isSubmitting.value,
                            dirty = isDirty(),
                            showsProgressBar = true,
                        )
                    AddBillStep.Success ->
                        WizardChrome(
                            title = "Bill added",
                            progressLabel = WizardProgressLabel.Hidden,
                            progressFraction = null,
                            leading = WizardLeadingControl.Close,
                            primaryCtaLabel = "Done",
                            primaryCtaEnabled = true,
                            isSubmitting = false,
                            dirty = false,
                            showsProgressBar = false,
                        )
                }

        override fun onLeading() {
            when (_currentStep.value) {
                AddBillStep.Details -> _events.value = AddBillEvent.Dismiss
                AddBillStep.Schedule -> _currentStep.value = AddBillStep.Details
                AddBillStep.Review -> _currentStep.value = AddBillStep.Schedule
                AddBillStep.Success -> _events.value = AddBillEvent.Dismiss
            }
        }

        override fun onDiscard() {
            _events.value = AddBillEvent.Dismiss
        }

        override fun onPrimary() {
            when (_currentStep.value) {
                AddBillStep.Details -> {
                    _currentStep.value = AddBillStep.Schedule
                    Analytics.track(AnalyticsEvent.ScreenAddBillWizardStepViewed(2, "schedule"))
                }
                AddBillStep.Schedule -> {
                    _currentStep.value = AddBillStep.Review
                    Analytics.track(AnalyticsEvent.ScreenAddBillWizardStepViewed(3, "review"))
                }
                AddBillStep.Review -> submit()
                AddBillStep.Success -> {
                    val id = createdBillId
                    _events.value = id?.let(AddBillEvent::Created) ?: AddBillEvent.Dismiss
                }
            }
        }

        fun consumeEvent() {
            _events.value = null
        }

        fun parsedAmount(): BigDecimal? {
            val trimmed = amount.trim()
            if (trimmed.isEmpty()) return null
            return trimmed.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
        }

        fun detailsValid(): Boolean = payee.trim().isNotEmpty() && parsedAmount() != null

        fun isDirty(): Boolean =
            payee.trim().isNotEmpty() ||
                amount.trim().isNotEmpty() ||
                dueDate != null ||
                schedule != AddBillSchedule.OneTime

        private fun submit() {
            val amountValue = parsedAmount() ?: return
            if (_isSubmitting.value) return
            _isSubmitting.value = true
            _submitError.value = null
            viewModelScope.launch {
                val details = buildMap {
                    put("schedule", schedule.detailsKey)
                    if (schedule != AddBillSchedule.OneTime) {
                        put("frequency", schedule.detailsKey)
                    }
                }
                val request =
                    CreateBillRequest(
                        billType = "other",
                        providerName = payee.trim(),
                        amount = amountValue,
                        dueDate = dueDate?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        details = details,
                    )
                when (val result = repo.createHomeBill(homeId, request)) {
                    is NetworkResult.Success -> {
                        createdBillId = result.data.bill.id
                        _isSubmitting.value = false
                        _currentStep.value = AddBillStep.Success
                        Analytics.track(AnalyticsEvent.CtaAddBillSubmit(AnalyticsResult.SUCCESS))
                    }
                    is NetworkResult.Failure -> {
                        _isSubmitting.value = false
                        _submitError.value = result.error.message
                        Analytics.track(AnalyticsEvent.CtaAddBillSubmit(AnalyticsResult.ERROR))
                    }
                }
            }
        }
    }
