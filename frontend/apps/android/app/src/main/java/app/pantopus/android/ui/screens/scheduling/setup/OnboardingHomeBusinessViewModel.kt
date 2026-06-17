@file:Suppress("PackageNaming", "TooManyFunctions", "MagicNumber", "LongMethod", "CyclomaticComplexMethod")

package app.pantopus.android.ui.screens.scheduling.setup

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.scheduling.CreateEventTypeRequest
import app.pantopus.android.data.api.models.scheduling.UpdateBookingPageRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.scheduling.SchedulingError
import app.pantopus.android.data.scheduling.SchedulingErrorDecoder
import app.pantopus.android.data.scheduling.SchedulingFeatureFlags
import app.pantopus.android.data.scheduling.SchedulingOwner
import app.pantopus.android.data.scheduling.SchedulingRepository
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.screens.shared.wizard.WizardChrome
import app.pantopus.android.ui.screens.shared.wizard.WizardLeadingControl
import app.pantopus.android.ui.screens.shared.wizard.WizardModel
import app.pantopus.android.ui.screens.shared.wizard.WizardProgressLabel
import app.pantopus.android.ui.screens.shared.wizard.WizardSecondaryCta
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

private const val SLUG_DEBOUNCE_MS = 450L
private const val DEFAULT_DURATION = 30
private const val CENTS_PER_DOLLAR = 100
private const val MAX_SLUG_ATTEMPTS = 4

enum class OnboardingFlow { Home, Business }

@Immutable
data class OnboardingUiState(
    val flow: OnboardingFlow = OnboardingFlow.Home,
    val stepIndex: Int = 1,
    val isSubmitting: Boolean = false,
    // Home
    val selectedMembers: Set<String> = setOf("you", "m2", "m3"),
    val combineMode: String = "collective",
    val roundRobinRule: String = "balanced",
    // Business
    val slug: String = "",
    val slugState: SlugFieldUiState = SlugFieldUiState.Idle,
    val serviceType: String = "consultation",
    val duration: Int = DEFAULT_DURATION,
    val priceText: String = "120",
    val seatedTeam: Set<String> = setOf("owner", "t2", "t3"),
    val confirmMode: String = "approve",
    val timezoneId: String = ZoneId.systemDefault().id,
    val submitError: String? = null,
) {
    /** INPUT steps before success — Home: Members+Combine (2); Business: Link+Service+Team+Confirm (4). */
    val inputSteps: Int get() = if (flow == OnboardingFlow.Home) 2 else 4

    /** Rail segment count — Home's 3rd "Share" segment represents the success state. */
    val railSteps: Int get() = if (flow == OnboardingFlow.Home) 3 else 4
    val isSuccess: Boolean get() = stepIndex > inputSteps
    val displayStep: Int get() = if (isSuccess) railSteps else stepIndex
    val pillar: SchedulingPillar get() = if (flow == OnboardingFlow.Home) SchedulingPillar.Home else SchedulingPillar.Business
    val shareLink: String
        get() =
            if (flow == OnboardingFlow.Home) {
                "pantopus.com/book/family"
            } else {
                "pantopus.com/book/${slug.ifBlank { "your-link" }}"
            }
}

/**
 * A6 Onboarding for Home & Business. The arg-less route means the flow is
 * chosen in-screen (default Home); the owner is resolved only at
 * [finishSetup] from [HomesRepository]/[AuthRepository].
 */
@HiltViewModel
class OnboardingHomeBusinessViewModel
    @Inject
    constructor(
        private val repo: SchedulingRepository,
        private val homes: HomesRepository,
        private val auth: AuthRepository,
        private val errors: SchedulingErrorDecoder,
        featureFlags: SchedulingFeatureFlags,
    ) : ViewModel(),
        WizardModel {
        val paidEnabled: Boolean = featureFlags.paidSchedulingEnabled

        private val _state = MutableStateFlow(OnboardingUiState())
        val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

        private val _pendingShareUrl = MutableStateFlow<String?>(null)
        val pendingShareUrl: StateFlow<String?> = _pendingShareUrl.asStateFlow()

        private val _finished = MutableStateFlow(false)
        val finished: StateFlow<Boolean> = _finished.asStateFlow()

        private var slugJob: Job? = null

        override val chrome: WizardChrome
            get() {
                val s = _state.value
                val title = if (s.flow == OnboardingFlow.Home) "Family scheduling" else "Business booking"
                return if (s.isSuccess) {
                    WizardChrome(
                        title = title,
                        progressLabel = WizardProgressLabel.StepOf(s.railSteps, s.railSteps),
                        progressFraction = 1f,
                        leading = WizardLeadingControl.Back,
                        primaryCtaLabel = "Share link",
                        primaryCtaEnabled = !s.isSubmitting,
                        secondaryCta =
                            WizardSecondaryCta(
                                if (s.flow == OnboardingFlow.Home) "Members" else "Add service",
                                "onboardingSecondary",
                            ),
                        isSubmitting = s.isSubmitting,
                        dirty = false,
                        showsProgressBar = false,
                        primaryCtaTestTag = "onboardingShare",
                    )
                } else {
                    WizardChrome(
                        title = title,
                        progressLabel = WizardProgressLabel.StepOf(s.displayStep, s.railSteps),
                        progressFraction = s.displayStep.toFloat() / s.railSteps,
                        leading = WizardLeadingControl.Back,
                        primaryCtaLabel = primaryLabel(s),
                        primaryCtaEnabled = primaryEnabled(s) && !s.isSubmitting,
                        secondaryCta = secondaryCta(s),
                        isSubmitting = s.isSubmitting,
                        footerHint = s.submitError,
                        dirty = false,
                        showsProgressBar = false,
                        primaryCtaTestTag = "onboardingPrimary",
                    )
                }
            }

        private fun primaryLabel(s: OnboardingUiState): String =
            when {
                s.flow == OnboardingFlow.Home && s.stepIndex == 1 -> "Continue · ${s.selectedMembers.size} selected"
                s.flow == OnboardingFlow.Business && s.stepIndex == 1 -> "Continue · add a service"
                s.flow == OnboardingFlow.Business && s.stepIndex == s.inputSteps -> "Finish setup"
                else -> "Continue"
            }

        private fun primaryEnabled(s: OnboardingUiState): Boolean =
            when {
                s.flow == OnboardingFlow.Business && s.stepIndex == 1 -> s.slugState is SlugFieldUiState.Available
                s.flow == OnboardingFlow.Home && s.stepIndex == 1 -> s.selectedMembers.isNotEmpty()
                else -> true
            }

        private fun secondaryCta(s: OnboardingUiState): WizardSecondaryCta? =
            when {
                s.flow == OnboardingFlow.Home && s.stepIndex == 2 -> WizardSecondaryCta("Use defaults", "onboardingDefaults")
                s.flow == OnboardingFlow.Business && s.stepIndex == 2 -> WizardSecondaryCta("Use defaults", "onboardingDefaults")
                s.flow == OnboardingFlow.Business && s.stepIndex == 3 -> WizardSecondaryCta("Skip · just me", "onboardingSkipTeam")
                else -> null
            }

        // ─── Flow chooser + selections ────────────────────────────────────

        fun selectFlow(flow: OnboardingFlow) {
            if (flow == _state.value.flow) return
            _state.value = OnboardingUiState(flow = flow)
        }

        fun toggleMember(id: String) {
            val s = _state.value
            _state.value = s.copy(selectedMembers = s.selectedMembers.toggle(id))
        }

        fun toggleSeat(id: String) {
            val s = _state.value
            _state.value = s.copy(seatedTeam = s.seatedTeam.toggle(id))
        }

        fun setCombineMode(mode: String) {
            _state.value = _state.value.copy(combineMode = mode)
        }

        fun setRoundRobinRule(rule: String) {
            _state.value = _state.value.copy(roundRobinRule = rule)
        }

        fun setServiceType(type: String) {
            _state.value = _state.value.copy(serviceType = type)
        }

        fun setDuration(minutes: Int) {
            _state.value = _state.value.copy(duration = minutes)
        }

        fun setPriceText(text: String) {
            _state.value = _state.value.copy(priceText = text.filter { it.isDigit() })
        }

        fun setConfirmMode(mode: String) {
            _state.value = _state.value.copy(confirmMode = mode)
        }

        // ─── Slug live check (business) ───────────────────────────────────

        fun onSlugChange(value: String) {
            _state.value = _state.value.copy(slug = value)
            slugJob?.cancel()
            if (value.isBlank()) {
                _state.value = _state.value.copy(slugState = SlugFieldUiState.Idle)
                return
            }
            _state.value = _state.value.copy(slugState = SlugFieldUiState.Checking)
            slugJob =
                viewModelScope.launch {
                    delay(SLUG_DEBOUNCE_MS)
                    if (value != _state.value.slug) return@launch
                    when (val r = repo.checkSlug(businessOwner(), value)) {
                        is NetworkResult.Success ->
                            _state.value =
                                _state.value.copy(
                                    slugState =
                                        if (r.data.available) {
                                            SlugFieldUiState.Available
                                        } else {
                                            SlugFieldUiState.Taken(r.data.suggestions)
                                        },
                                )
                        is NetworkResult.Failure -> _state.value = _state.value.copy(slugState = SlugFieldUiState.Idle)
                    }
                }
        }

        fun onPickSuggestion(suggestion: String) = onSlugChange(suggestion)

        // ─── Wizard nav ───────────────────────────────────────────────────

        override fun onLeading() {
            val s = _state.value
            when {
                s.isSuccess -> _state.value = s.copy(stepIndex = s.inputSteps)
                s.stepIndex == 1 -> _finished.value = true
                else -> _state.value = s.copy(stepIndex = s.stepIndex - 1)
            }
        }

        override fun onPrimary() {
            val s = _state.value
            if (s.isSubmitting) return
            when {
                s.isSuccess -> _pendingShareUrl.value = "https://${s.shareLink}"
                s.flow == OnboardingFlow.Business && s.stepIndex == 1 -> claimSlug()
                s.stepIndex < s.inputSteps -> _state.value = s.copy(stepIndex = s.stepIndex + 1)
                else -> finishSetup()
            }
        }

        override fun onSecondary() {
            val s = _state.value
            when {
                s.isSuccess -> _finished.value = true
                s.stepIndex < s.inputSteps -> _state.value = s.copy(stepIndex = s.stepIndex + 1)
                else -> finishSetup()
            }
        }

        override fun onDiscard() {
            _finished.value = true
        }

        private fun claimSlug() {
            val slug = _state.value.slug.trim()
            _state.value = _state.value.copy(isSubmitting = true)
            viewModelScope.launch {
                when (val r = repo.updateSlug(businessOwner(), slug)) {
                    is NetworkResult.Success -> _state.value = _state.value.copy(stepIndex = 2, isSubmitting = false)
                    is NetworkResult.Failure -> {
                        val taken =
                            (errors.decode(r.error) as? SchedulingError.SlugTaken)?.suggestions ?: emptyList()
                        _state.value = _state.value.copy(slugState = SlugFieldUiState.Taken(taken), isSubmitting = false)
                    }
                }
            }
        }

        private fun finishSetup() {
            val s = _state.value
            _state.value = s.copy(isSubmitting = true, submitError = null)
            viewModelScope.launch {
                val owner = resolveOwner(s.flow)
                if (owner == null) {
                    _state.value = _state.value.copy(isSubmitting = false, submitError = ownerErrorMessage(s.flow))
                    return@launch
                }
                repo.updateBookingPage(owner, UpdateBookingPageRequest(timezone = s.timezoneId))
                if (createEventTypeWithRetry(owner, s)) {
                    _state.value = _state.value.copy(stepIndex = s.inputSteps + 1, isSubmitting = false, submitError = null)
                } else {
                    _state.value = _state.value.copy(isSubmitting = false, submitError = "Couldn't finish setup. Please try again.")
                }
            }
        }

        /** Mirrors the iOS up-to-4-attempt slug-collision retry; returns true once an event type is created. */
        private suspend fun createEventTypeWithRetry(
            owner: SchedulingOwner,
            s: OnboardingUiState,
        ): Boolean {
            val base = eventTypeRequest(s)
            repeat(MAX_SLUG_ATTEMPTS) { attempt ->
                val body = if (attempt == 0) base else base.copy(slug = "${base.slug}-${attempt + 1}")
                when (val r = repo.createEventType(owner, body)) {
                    is NetworkResult.Success -> return true
                    is NetworkResult.Failure ->
                        if (errors.decode(r.error) !is SchedulingError.SlugTaken) return false
                }
            }
            return false
        }

        private fun businessOwner(): SchedulingOwner = businessUserId()?.let { SchedulingOwner.Business(it) } ?: SchedulingOwner.Personal

        private fun businessUserId(): String? = (auth.state.value as? AuthRepository.State.SignedIn)?.user?.id?.takeIf { it.isNotBlank() }

        private fun ownerErrorMessage(flow: OnboardingFlow): String =
            if (flow == OnboardingFlow.Home) {
                "No household yet. Create one to share a family booking link."
            } else {
                "Couldn't load your business. Try signing in again."
            }

        private fun eventTypeRequest(s: OnboardingUiState): CreateEventTypeRequest =
            if (s.flow == OnboardingFlow.Home) {
                CreateEventTypeRequest(
                    name = "Household meeting",
                    slug = "household-meeting",
                    durations = listOf(s.duration),
                    defaultDuration = s.duration,
                    locationMode = "video",
                    assignmentMode = if (s.combineMode == "round_robin") "round_robin" else "collective",
                )
            } else {
                CreateEventTypeRequest(
                    name = serviceLabel(s.serviceType),
                    slug = "${s.serviceType}-meeting",
                    durations = listOf(s.duration),
                    defaultDuration = s.duration,
                    locationMode = "in_person",
                    assignmentMode = "one_on_one",
                    requiresApproval = s.confirmMode == "approve",
                    priceCents = if (paidEnabled) s.priceText.toIntOrNull()?.times(CENTS_PER_DOLLAR) else null,
                )
            }

        /** Resolves the concrete owner; null when no household/business id can be resolved (then setup must not proceed). */
        private suspend fun resolveOwner(flow: OnboardingFlow): SchedulingOwner? =
            when (flow) {
                OnboardingFlow.Home ->
                    (homes.myHomes() as? NetworkResult.Success)
                        ?.data
                        ?.homes
                        ?.firstOrNull()
                        ?.id
                        ?.takeIf { it.isNotBlank() }
                        ?.let { SchedulingOwner.Home(it) }
                OnboardingFlow.Business -> businessUserId()?.let { SchedulingOwner.Business(it) }
            }

        fun shareConsumed() {
            _pendingShareUrl.value = null
        }

        private fun Set<String>.toggle(id: String): Set<String> = if (contains(id)) this - id else this + id
    }

internal fun serviceLabel(type: String): String =
    when (type) {
        "quote" -> "Quote visit"
        "survey" -> "Site survey"
        "service_call" -> "Service call"
        else -> "Consultation"
    }
