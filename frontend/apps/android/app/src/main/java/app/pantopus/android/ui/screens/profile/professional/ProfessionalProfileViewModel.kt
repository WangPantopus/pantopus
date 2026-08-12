@file:Suppress("MagicNumber", "PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.profile.professional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.professional.ProfessionalEnableRequest
import app.pantopus.android.data.api.models.professional.ProfessionalPricingInput
import app.pantopus.android.data.api.models.professional.ProfessionalProfileDto
import app.pantopus.android.data.api.models.professional.ProfessionalProfileUpdateRequest
import app.pantopus.android.data.api.models.professional.ProfessionalServiceAreaInput
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.displayMessage
import app.pantopus.android.data.professional.ProfessionalRepository
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ProfessionalProfileToast(
    val text: String,
    val isError: Boolean = false,
)

@HiltViewModel
class ProfessionalProfileViewModel
    @Inject
    constructor(
        private val repository: ProfessionalRepository,
    ) : ViewModel() {
        private var seed: ProfessionalProfileContent = ProfessionalProfileSampleData.published
        private var baselineSeed: ProfessionalProfileContent = seed
        private var simulateFailure: Boolean = false
        private var useSample: Boolean = false

        /** Sample/preview + test seam. Sets the deterministic seed and skips
         *  the network on [load]. */
        internal constructor(
            repository: ProfessionalRepository,
            seed: ProfessionalProfileContent = ProfessionalProfileSampleData.published,
            baseline: ProfessionalProfileContent? = null,
            simulateFailure: Boolean = false,
        ) : this(repository) {
            this.seed = seed
            baselineSeed = baseline ?: seed
            this.simulateFailure = simulateFailure
            this.useSample = true
        }

        private val _state = MutableStateFlow<ProfessionalProfileUiState>(ProfessionalProfileUiState.Loading)
        val state: StateFlow<ProfessionalProfileUiState> = _state.asStateFlow()

        private val _toast = MutableStateFlow<ProfessionalProfileToast?>(null)
        val toast: StateFlow<ProfessionalProfileToast?> = _toast.asStateFlow()

        /** Drives the destructive "Disable professional mode?" confirm. */
        private val _showsDisableConfirm = MutableStateFlow(false)
        val showsDisableConfirm: StateFlow<Boolean> = _showsDisableConfirm.asStateFlow()

        /** True while `DELETE /profile/me` is in flight. */
        private val _isDisabling = MutableStateFlow(false)
        val isDisabling: StateFlow<Boolean> = _isDisabling.asStateFlow()

        private var content: ProfessionalProfileContent? = null
        private var baseline: ProfessionalProfileContent? = null

        /** Working copy for the enable (create / re-enable) form. */
        private var draft: ProfessionalEnableDraft? = null

        fun load() {
            _state.value = ProfessionalProfileUiState.Loading
            if (useSample) {
                if (simulateFailure) {
                    _state.value = ProfessionalProfileUiState.Error("We couldn't load your professional profile.")
                    return
                }
                content = seed
                baseline = baselineSeed
                recompute()
                return
            }
            viewModelScope.launch { fetchLive() }
        }

        private suspend fun fetchLive() {
            when (val result = repository.profileMe()) {
                is NetworkResult.Success -> {
                    val profile = result.data.profile
                    // `profile: null` (200) means professional mode was never
                    // enabled; `is_active = false` means it was disabled. Both
                    // are the create state, not an error — RN professional.tsx:100.
                    if (profile == null || profile.isActive == false) {
                        enterCreateMode(profile)
                    } else {
                        hydrate(profile)
                    }
                }
                is NetworkResult.Failure -> {
                    // Older deployments 404 `profile/me` instead of returning
                    // null — still the create state, not a failure.
                    if (result.error is NetworkError.NotFound) {
                        enterCreateMode(null)
                    } else {
                        _state.value = ProfessionalProfileUiState.Error(result.error.displayMessage("Couldn't load this profile."))
                    }
                }
            }
        }

        /** Map an **active** backend record into the editor and render it. */
        private suspend fun hydrate(profile: ProfessionalProfileDto) {
            val verificationResult = repository.verificationStatus()
            val verification =
                if (verificationResult is NetworkResult.Success) verificationResult.data else null
            val mapped = ProfessionalProfileMapper.build(profile, verification)
            draft = null
            content = mapped
            baseline = mapped
            recompute()
        }

        /**
         * Switch to the "professional mode is off" form, seeded from a
         * soft-disabled record when one exists.
         */
        private fun enterCreateMode(dto: ProfessionalProfileDto?) {
            content = null
            baseline = null
            val seeded = ProfessionalProfileMapper.draft(dto)
            draft = seeded
            _state.value = ProfessionalProfileUiState.Create(seeded)
        }

        fun refresh() = load()

        fun dismissToast() {
            _toast.value = null
        }

        // Enable / disable professional mode

        fun updateDraftHeadline(value: String) = mutateDraft { it.copy(headline = value.take(HEADLINE_MAX)) }

        fun updateDraftBio(value: String) = mutateDraft { it.copy(bio = value.take(BIO_MAX)) }

        fun updateDraftCity(value: String) = mutateDraft { it.copy(city = value) }

        fun updateDraftState(value: String) = mutateDraft { it.copy(state = value) }

        fun updateDraftRadius(value: String) = mutateDraft { it.copy(radiusKm = value.filter(Char::isDigit).take(3)) }

        fun updateDraftHourlyRate(value: String) =
            mutateDraft { current ->
                current.copy(hourlyRate = value.filter { char -> char.isDigit() || char == '.' })
            }

        fun setDraftPublic(isOn: Boolean) = mutateDraft { it.copy(isPublic = isOn) }

        /** Add/remove a category, capped at the server's 5 (`professional.js:45`). */
        fun toggleDraftCategory(key: String) =
            mutateDraft { current ->
                when {
                    current.categories.contains(key) -> current.copy(categories = current.categories - key)
                    current.canSelectMoreCategories -> current.copy(categories = current.categories + key)
                    else -> current
                }
            }

        /**
         * Turn professional mode on. A never-created profile goes through
         * `POST api/professional/profile`; a soft-disabled one is switched back
         * on with `PATCH api/professional/profile/me { is_active: true }` — the
         * same split as RN `professional.tsx:141`.
         */
        fun enable() {
            val working = draft ?: return
            if (working.isSubmitting) return
            val submitting = working.copy(isSubmitting = true, errorMessage = null)
            draft = submitting
            _state.value = ProfessionalProfileUiState.Create(submitting)
            viewModelScope.launch {
                val result =
                    if (submitting.isReEnable) {
                        repository.updateProfileMe(updateRequest(submitting))
                    } else {
                        repository.createProfile(enableRequest(submitting))
                    }
                when (result) {
                    is NetworkResult.Success -> {
                        val profile = result.data.profile
                        if (profile != null) hydrate(profile) else fetchLive()
                        _toast.value = ProfessionalProfileToast("Professional mode enabled")
                    }
                    is NetworkResult.Failure -> {
                        val message = result.error.displayMessage("Failed to enable professional mode")
                        val failed = submitting.copy(isSubmitting = false, errorMessage = message)
                        draft = failed
                        _state.value = ProfessionalProfileUiState.Create(failed)
                        _toast.value = ProfessionalProfileToast(message, isError = true)
                    }
                }
            }
        }

        /** Open the destructive confirm — nothing is sent until it's accepted. */
        fun requestDisable() {
            _showsDisableConfirm.value = true
        }

        fun dismissDisableConfirm() {
            _showsDisableConfirm.value = false
        }

        /**
         * `DELETE api/professional/profile/me` — soft-disable. The row survives,
         * so the screen drops back into the re-enable form.
         */
        fun disableConfirmed() {
            if (_isDisabling.value) return
            _showsDisableConfirm.value = false
            _isDisabling.value = true
            viewModelScope.launch {
                when (val result = repository.disableProfile()) {
                    is NetworkResult.Success -> {
                        enterCreateMode(result.data.profile)
                        _toast.value = ProfessionalProfileToast("Professional mode disabled")
                    }
                    is NetworkResult.Failure -> {
                        _toast.value = ProfessionalProfileToast("Could not disable", isError = true)
                    }
                }
                _isDisabling.value = false
            }
        }

        private fun mutateDraft(transform: (ProfessionalEnableDraft) -> ProfessionalEnableDraft) {
            val working = draft ?: return
            val updated = transform(working).copy(errorMessage = null)
            draft = updated
            _state.value = ProfessionalProfileUiState.Create(updated)
        }

        fun updateTitle(value: String) {
            mutate {
                it.copy(title = it.title.copy(value = value, touched = true))
            }
        }

        fun updateYearsInRole(value: String) {
            val digitsOnly = value.filter(Char::isDigit)
            mutate {
                it.copy(yearsInRole = it.yearsInRole.copy(value = digitsOnly, touched = true))
            }
        }

        fun setVisibility(
            id: String,
            isOn: Boolean,
        ) {
            mutate { profile ->
                profile.copy(
                    visibility =
                        profile.visibility.map {
                            if (it.id == id) it.copy(isOn = isOn) else it
                        },
                )
            }
        }

        fun removeSkill(id: String) {
            mutate { it.copy(skills = it.skills.filterNot { skill -> skill.id == id }) }
        }

        fun removeCertification(id: String) {
            mutate { it.copy(certifications = it.certifications.filterNot { cert -> cert.id == id }) }
        }

        fun addSkill() {
            mutate {
                it.copy(
                    skills =
                        it.skills +
                            ProSkill(
                                id = "skill-${UUID.randomUUID()}",
                                label = "New skill",
                                icon = PantopusIcon.Plus,
                                isFresh = true,
                            ),
                )
            }
        }

        fun addCertification() {
            mutate {
                it.copy(
                    certifications =
                        it.certifications +
                            Certification(
                                id = "cert-${UUID.randomUUID()}",
                                name = "New certification",
                                issuer = "Awaiting upload",
                                issued = "—",
                                expires = "—",
                                status = ProVerificationStatus.Pending,
                                isFresh = true,
                            ),
                )
            }
        }

        fun addPortfolioLink() {
            mutate {
                it.copy(
                    portfolio =
                        it.portfolio +
                            PortfolioLink(
                                id = "link-${UUID.randomUUID()}",
                                host = "link",
                                title = "New link",
                                url = "Fetching preview…",
                                state = PortfolioLinkState.Loading,
                                isFresh = true,
                            ),
                )
            }
        }

        fun discard() {
            content = baseline ?: return
            recompute()
            _toast.value = ProfessionalProfileToast("Edits discarded.")
        }

        fun saveAndSubmit() {
            val working = content ?: return
            if (!working.isDirty) return
            val pending = working.pendingCount
            val committed =
                working.copy(
                    title = working.title.committed(),
                    yearsInRole = working.yearsInRole.committed(),
                    company = working.company.copy(isDirty = false),
                    skills = working.skills.map { it.copy(isFresh = false) },
                    certifications = working.certifications.map { it.copy(isFresh = false) },
                    portfolio = working.portfolio.map { it.copy(isFresh = false) },
                    visibility = working.visibility.map { it.copy(originalOn = it.isOn) },
                )
            content = committed
            baseline = committed
            recompute()
            if (!useSample) persist(committed)
            _toast.value =
                ProfessionalProfileToast(
                    if (pending > 0) {
                        "Submitted — $pending ${if (pending == 1) "claim" else "claims"} in review."
                    } else {
                        "Professional profile published."
                    },
                )
        }

        /** Best-effort PATCH of the safe, unambiguous fields (headline +
         *  public/active flags). `categories` are enum-constrained server-side,
         *  so free-text skills are not written here. */
        private fun persist(content: ProfessionalProfileContent) {
            val request =
                ProfessionalProfileUpdateRequest(
                    headline = content.title.value,
                    isPublic = content.visibility.firstOrNull { it.id == "publicProfile" }?.isOn,
                    isActive = content.visibility.firstOrNull { it.id == "activeForHire" }?.isOn,
                )
            viewModelScope.launch { repository.updateProfileMe(request) }
        }

        private fun mutate(transform: (ProfessionalProfileContent) -> ProfessionalProfileContent) {
            val working = content ?: return
            content = transform(working)
            recompute()
        }

        companion object {
            private const val HEADLINE_MAX = 200
            private const val BIO_MAX = 2000
            private const val DEFAULT_RADIUS_KM = 50
            private const val MIN_RADIUS_KM = 1
            private const val MAX_RADIUS_KM = 500

            /** Body for the first-time enable (`POST /profile`). */
            fun enableRequest(draft: ProfessionalEnableDraft): ProfessionalEnableRequest =
                ProfessionalEnableRequest(
                    headline = draft.headline.trimOrNull(),
                    bio = draft.bio.trimOrNull(),
                    categories = draft.categories.ifEmpty { null },
                    serviceArea = serviceArea(draft),
                    pricingMeta = pricing(draft),
                    isPublic = draft.isPublic,
                )

            /** Body for re-enabling a soft-disabled row (`PATCH /profile/me`). */
            fun updateRequest(draft: ProfessionalEnableDraft): ProfessionalProfileUpdateRequest =
                ProfessionalProfileUpdateRequest(
                    headline = draft.headline.trimOrNull(),
                    bio = draft.bio.trimOrNull(),
                    isPublic = draft.isPublic,
                    isActive = true,
                    categories = draft.categories.ifEmpty { null },
                    serviceArea = serviceArea(draft),
                    pricingMeta = pricing(draft),
                )

            private fun serviceArea(draft: ProfessionalEnableDraft): ProfessionalServiceAreaInput? {
                // Joi caps radius at 1…500 (`professional.js:50`) — a blank or
                // out-of-range field would fail validation for the whole request.
                val radius =
                    (draft.radiusKm.toIntOrNull() ?: DEFAULT_RADIUS_KM)
                        .coerceIn(MIN_RADIUS_KM, MAX_RADIUS_KM)
                val area =
                    ProfessionalServiceAreaInput(
                        city = draft.city.trimOrNull(),
                        state = draft.state.trimOrNull(),
                        radiusKm = radius,
                    )
                return if (area.isEmpty) null else area
            }

            private fun pricing(draft: ProfessionalEnableDraft): ProfessionalPricingInput? {
                val rate = draft.hourlyRate.toDoubleOrNull() ?: return null
                if (rate <= 0.0) return null
                return ProfessionalPricingInput(hourlyRate = rate, currency = "USD")
            }

            private fun String.trimOrNull(): String? = trim().ifEmpty { null }
        }

        private fun recompute() {
            val snapshot = content
            if (snapshot == null) {
                _state.value = ProfessionalProfileUiState.Loading
                return
            }
            val dirty = snapshot.dirtyCount
            _state.value =
                if (dirty == 0) {
                    ProfessionalProfileUiState.Verified(snapshot)
                } else {
                    ProfessionalProfileUiState.Pending(
                        content = snapshot,
                        dirtyCount = dirty,
                        pendingCount = snapshot.pendingCount,
                    )
                }
        }
    }
