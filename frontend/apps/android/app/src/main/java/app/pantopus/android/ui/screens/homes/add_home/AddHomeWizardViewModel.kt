@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.add_home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.data.api.models.homes.CheckAddressRequest
import app.pantopus.android.data.api.models.homes.CheckAddressResponse
import app.pantopus.android.data.api.models.homes.CreateHomeRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.network.NetworkMonitor
import app.pantopus.android.ui.screens.shared.wizard.WizardChrome
import app.pantopus.android.ui.screens.shared.wizard.WizardLeadingControl
import app.pantopus.android.ui.screens.shared.wizard.WizardModel
import app.pantopus.android.ui.screens.shared.wizard.WizardProgressLabel
import app.pantopus.android.ui.screens.shared.wizard.WizardSecondaryCta
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Aggregate UI state for the AddHome wizard. Combined into a single flow
 * so the screen can derive the [WizardChrome] off of it without reading
 * five separate StateFlows.
 */
data class AddHomeUiState(
    val form: AddHomeFormState = AddHomeFormState.EMPTY,
    val homeSearchQuery: String = "",
    val selectedHomeId: String? = null,
    val addressCheck: CheckAddressResponse? = null,
    val isCheckingAddress: Boolean = false,
    val isSubmitting: Boolean = false,
    val createdHomeId: String? = null,
    val errorMessage: String? = null,
)

/**
 * Drives the four-step + success Add-Home wizard. Step 1 uses
 * deterministic address fixtures, then the remaining steps keep using
 * the existing structured address shape and [WizardChrome] for the shared
 * [app.pantopus.android.ui.screens.shared.wizard.WizardShell].
 *
 * Form state is mirrored into [SavedStateHandle] so the wizard survives
 * config changes and process death (acceptance criterion #5).
 */
@HiltViewModel
@Suppress("TooManyFunctions")
open class AddHomeWizardViewModel
    @Inject
    constructor(
        private val repository: HomesRepository,
        private val savedStateHandle: SavedStateHandle,
        private val networkMonitor: NetworkMonitor,
    ) : ViewModel(),
        WizardModel {
        private val _state =
            MutableStateFlow(
                restoreFormState().let { form ->
                    val candidate = AddHomeSampleData.candidateFor(form.address)
                    AddHomeUiState(
                        form = form,
                        homeSearchQuery = candidate?.line1.orEmpty(),
                        selectedHomeId = candidate?.id,
                    )
                },
            )

        /** Combined UI state consumed by [AddHomeWizardScreen]. */
        val state: StateFlow<AddHomeUiState> = _state.asStateFlow()

        /** One-shot navigation events the screen reacts to. */
        val pendingEvent = MutableStateFlow<AddHomeOutboundEvent?>(null)

        // MARK: - WizardModel

        override val chrome: WizardChrome
            get() = computeChrome(_state.value)

        override fun onLeading() {
            val current = _state.value.form.currentStep
            when (leadingControl(current)) {
                WizardLeadingControl.Back -> goBack()
                WizardLeadingControl.Close -> pendingEvent.value = AddHomeOutboundEvent.Dismiss
            }
        }

        override fun onDiscard() {
            pendingEvent.value = AddHomeOutboundEvent.Dismiss
        }

        override fun onPrimary() {
            viewModelScope.launch { advance() }
        }

        override fun onSecondary() {
            // Success step's "Back to Hub" — no other step uses the secondary.
            if (_state.value.form.currentStep == AddHomeStep.Success) {
                pendingEvent.value = AddHomeOutboundEvent.Dismiss
            }
        }

        // MARK: - Search updates

        val nearbyHomes: List<AddHomeAddressCandidate>
            get() = AddHomeSampleData.nearbyHomes

        val autocompleteResults: List<AddHomeAddressCandidate>
            get() =
                if (_state.value.selectedHomeId == null) {
                    AddHomeSampleData.autocompleteResults(_state.value.homeSearchQuery)
                } else {
                    emptyList()
                }

        val showsAutocomplete: Boolean
            get() = _state.value.selectedHomeId == null && _state.value.homeSearchQuery.trim().isNotEmpty()

        fun updateSearchQuery(query: String) {
            _state.update {
                it.copy(
                    homeSearchQuery = query,
                    selectedHomeId = null,
                    form = it.form.copy(address = AddHomeAddressFields()),
                )
            }
            persist()
        }

        fun clearSearchQuery() {
            _state.update {
                it.copy(
                    homeSearchQuery = "",
                    selectedHomeId = null,
                    form = it.form.copy(address = AddHomeAddressFields()),
                )
            }
            persist()
        }

        fun useCurrentLocation() {
            _state.update {
                it.copy(
                    homeSearchQuery = "",
                    selectedHomeId = null,
                    form = it.form.copy(address = AddHomeAddressFields()),
                )
            }
            persist()
        }

        fun selectAddressCandidate(candidate: AddHomeAddressCandidate) {
            if (candidate.isClaimed) return
            _state.update {
                it.copy(
                    homeSearchQuery = candidate.line1,
                    selectedHomeId = candidate.id,
                    form = it.form.copy(address = candidate.addressFields),
                )
            }
            persist()
        }

        fun addManuallyTapped() {
            _state.update {
                it.copy(
                    selectedHomeId = null,
                    form = it.form.copy(address = AddHomeAddressFields()),
                )
            }
            persist()
        }

        // MARK: - Legacy field updates

        fun updateField(
            field: AddressField,
            value: String,
        ) {
            _state.update { current ->
                val next =
                    when (field) {
                        AddressField.Street -> current.form.address.copy(street = value)
                        AddressField.Unit -> current.form.address.copy(unit = value)
                        AddressField.City -> current.form.address.copy(city = value)
                        AddressField.State -> current.form.address.copy(state = value)
                        AddressField.Zip -> current.form.address.copy(zipCode = value)
                    }
                val candidate = AddHomeSampleData.candidateFor(next)
                current.copy(
                    form = current.form.copy(address = next),
                    homeSearchQuery = candidate?.line1 ?: next.street,
                    selectedHomeId = candidate?.id,
                )
            }
            persist()
        }

        fun setPrimaryHome(isPrimary: Boolean) {
            _state.update { it.copy(form = it.form.copy(isPrimary = isPrimary)) }
            persist()
        }

        fun selectRole(role: AddHomeRole) {
            _state.update { it.copy(form = it.form.copy(role = role)) }
            persist()
        }

        fun acknowledgeEvent() {
            pendingEvent.value = null
        }

        // MARK: - State machine

        private suspend fun advance() {
            val current = _state.value.form.currentStep
            when (current) {
                AddHomeStep.Address -> {
                    transitionTo(AddHomeStep.Confirm)
                    runCheckAddress()
                }
                AddHomeStep.Confirm -> {
                    if (!_state.value.isCheckingAddress) transitionTo(AddHomeStep.Role)
                }
                AddHomeStep.Role -> transitionTo(AddHomeStep.Review)
                AddHomeStep.Review -> submit()
                AddHomeStep.Success -> {
                    val homeId = _state.value.createdHomeId ?: return
                    pendingEvent.value = AddHomeOutboundEvent.OpenHomeDashboard(homeId)
                }
            }
        }

        private fun goBack() {
            val previous = AddHomeStep.fromOrdinal(_state.value.form.step - 1)
            transitionTo(previous)
        }

        private fun transitionTo(step: AddHomeStep) {
            _state.update {
                it.copy(form = it.form.copy(step = step.ordinal0), errorMessage = null)
            }
            persist()
            step.stepNumber?.let { number ->
                Analytics.track(
                    AnalyticsEvent.ScreenAddHomeWizardStepViewed(
                        stepNumber = number,
                        stepName = step.name,
                    ),
                )
            }
        }

        // MARK: - API calls

        private suspend fun runCheckAddress() {
            val fields = _state.value.form.address
            _state.update { it.copy(isCheckingAddress = true, addressCheck = null, errorMessage = null) }
            val request =
                CheckAddressRequest(
                    address = fields.street,
                    unitNumber = fields.unit.takeIf { it.isNotEmpty() },
                    city = fields.city,
                    state = fields.state,
                    zipCode = fields.zipCode,
                )
            when (val result = repository.checkAddress(request)) {
                is NetworkResult.Success ->
                    _state.update {
                        it.copy(addressCheck = result.data, isCheckingAddress = false)
                    }
                is NetworkResult.Failure ->
                    _state.update {
                        it.copy(
                            isCheckingAddress = false,
                            errorMessage =
                                result.error.message
                                    ?: "Couldn't verify that address. Try again.",
                        )
                    }
            }
        }

        private suspend fun submit() {
            val role = _state.value.form.role ?: return
            val fields = _state.value.form.address
            Analytics.track(AnalyticsEvent.CtaAddHomeSubmit)
            if (!networkMonitor.isOnline.value) {
                // P15: surface offline state inline; never silent-queue.
                _state.update {
                    it.copy(
                        errorMessage = "You're offline. Try again when you're back online.",
                    )
                }
                return
            }
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            val request =
                CreateHomeRequest(
                    address = fields.street,
                    unitNumber = fields.unit.takeIf { it.isNotEmpty() },
                    city = fields.city,
                    state = fields.state,
                    zipCode = fields.zipCode,
                    name = role.label,
                )
            when (val result = repository.create(request)) {
                is NetworkResult.Success -> {
                    _state.update {
                        it.copy(
                            createdHomeId = result.data.home.id,
                            isSubmitting = false,
                            form = it.form.copy(step = AddHomeStep.Success.ordinal0),
                        )
                    }
                    persist()
                }
                is NetworkResult.Failure ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage =
                                result.error.message
                                    ?: "Couldn't add your home. Please try again.",
                        )
                    }
            }
        }

        // MARK: - Persistence

        private fun persist() {
            val form = _state.value.form
            savedStateHandle[KEY_STEP] = form.step
            savedStateHandle[KEY_STREET] = form.address.street
            savedStateHandle[KEY_UNIT] = form.address.unit
            savedStateHandle[KEY_CITY] = form.address.city
            savedStateHandle[KEY_STATE] = form.address.state
            savedStateHandle[KEY_ZIP] = form.address.zipCode
            savedStateHandle[KEY_PRIMARY] = form.isPrimary
            savedStateHandle[KEY_ROLE] = form.role?.name
        }

        private fun restoreFormState(): AddHomeFormState {
            val step: Int = savedStateHandle[KEY_STEP] ?: AddHomeStep.Address.ordinal0
            val street: String = savedStateHandle[KEY_STREET] ?: ""
            val unit: String = savedStateHandle[KEY_UNIT] ?: ""
            val city: String = savedStateHandle[KEY_CITY] ?: ""
            val state: String = savedStateHandle[KEY_STATE] ?: ""
            val zip: String = savedStateHandle[KEY_ZIP] ?: ""
            val isPrimary: Boolean = savedStateHandle[KEY_PRIMARY] ?: true
            val roleName: String? = savedStateHandle[KEY_ROLE]
            val role =
                roleName?.let { name ->
                    AddHomeRole.entries.firstOrNull { it.name == name }
                }
            return AddHomeFormState(
                step = step,
                address = AddHomeAddressFields(street, unit, city, state, zip),
                isPrimary = isPrimary,
                role = role,
            )
        }

        // MARK: - Chrome derivation

        private fun computeChrome(state: AddHomeUiState): WizardChrome {
            val step = state.form.currentStep
            val progress = progressLabel(step)
            return WizardChrome(
                title = title(step),
                progressLabel = progress,
                progressFraction = progressFraction(step),
                leading = leadingControl(step),
                primaryCtaLabel = primaryCtaLabel(step),
                primaryCtaEnabled = primaryEnabled(state) && !state.isSubmitting && !state.isCheckingAddress,
                secondaryCta = secondaryCta(step),
                isSubmitting = state.isSubmitting || state.isCheckingAddress,
                dirty =
                    step != AddHomeStep.Success &&
                        (
                            state.selectedHomeId != null ||
                                state.homeSearchQuery.isNotEmpty() ||
                                state.form.address.street.isNotEmpty()
                        ),
                showsProgressBar = step != AddHomeStep.Success,
            )
        }

        private fun title(step: AddHomeStep): String =
            when (step) {
                AddHomeStep.Address -> "Find your home"
                else -> "Add a home"
            }

        private fun leadingControl(step: AddHomeStep): WizardLeadingControl =
            when (step) {
                AddHomeStep.Address, AddHomeStep.Success -> WizardLeadingControl.Close
                AddHomeStep.Confirm, AddHomeStep.Role, AddHomeStep.Review -> WizardLeadingControl.Back
            }

        private fun primaryCtaLabel(step: AddHomeStep): String =
            when (step) {
                AddHomeStep.Address, AddHomeStep.Confirm, AddHomeStep.Role -> "Continue"
                AddHomeStep.Review -> "Submit"
                AddHomeStep.Success -> "View home"
            }

        private fun secondaryCta(step: AddHomeStep): WizardSecondaryCta? =
            if (step == AddHomeStep.Success) {
                WizardSecondaryCta(label = "Back to Hub", testTag = "addHomeBackToHub")
            } else {
                null
            }

        private fun progressLabel(step: AddHomeStep): WizardProgressLabel {
            val number = step.stepNumber ?: return WizardProgressLabel.Hidden
            return WizardProgressLabel.StepOf(current = number, total = AddHomeStep.PROGRESS_TOTAL)
        }

        private fun progressFraction(step: AddHomeStep): Float? {
            val number = step.stepNumber ?: return null
            return number.toFloat() / AddHomeStep.PROGRESS_TOTAL
        }

        private fun primaryEnabled(state: AddHomeUiState): Boolean =
            when (state.form.currentStep) {
                AddHomeStep.Address -> state.selectedHomeId != null
                AddHomeStep.Confirm -> !state.isCheckingAddress && state.errorMessage == null
                AddHomeStep.Role -> state.form.role != null
                AddHomeStep.Review -> state.form.role != null
                AddHomeStep.Success -> state.createdHomeId != null
            }

        companion object {
            private const val KEY_STEP = "addHome.step"
            private const val KEY_STREET = "addHome.street"
            private const val KEY_UNIT = "addHome.unit"
            private const val KEY_CITY = "addHome.city"
            private const val KEY_STATE = "addHome.state"
            private const val KEY_ZIP = "addHome.zip"
            private const val KEY_PRIMARY = "addHome.primary"
            private const val KEY_ROLE = "addHome.role"
        }
    }

/** The five user-facing input fields in step 1. */
enum class AddressField { Street, Unit, City, State, Zip }
