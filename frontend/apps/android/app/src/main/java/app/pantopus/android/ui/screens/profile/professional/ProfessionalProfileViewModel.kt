@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.profile.professional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.professional.ProfessionalProfileUpdateRequest
import app.pantopus.android.data.api.net.NetworkResult
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

        private var content: ProfessionalProfileContent? = null
        private var baseline: ProfessionalProfileContent? = null

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
            viewModelScope.launch {
                when (val result = repository.profileMe()) {
                    is NetworkResult.Success -> {
                        val verificationResult = repository.verificationStatus()
                        val verification =
                            if (verificationResult is NetworkResult.Success) verificationResult.data else null
                        val mapped = ProfessionalProfileMapper.build(result.data.profile, verification)
                        content = mapped
                        baseline = mapped
                        recompute()
                    }
                    is NetworkResult.Failure -> {
                        _state.value = ProfessionalProfileUiState.Error(result.error.message)
                    }
                }
            }
        }

        fun refresh() = load()

        fun dismissToast() {
            _toast.value = null
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
