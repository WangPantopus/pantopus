@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.data.analytics.AnalyticsResult
import app.pantopus.android.data.api.models.users.ProfileUpdateRequest
import app.pantopus.android.data.api.models.users.UserProfile
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.network.NetworkMonitor
import app.pantopus.android.data.profile.ProfileRepository
import app.pantopus.android.ui.screens.shared.form.FormAggregate
import app.pantopus.android.ui.screens.shared.form.FormFieldState
import app.pantopus.android.ui.screens.shared.form.FormValidator
import app.pantopus.android.ui.screens.shared.form.all
import app.pantopus.android.ui.screens.shared.form.e164Phone
import app.pantopus.android.ui.screens.shared.form.isoDateOrEmpty
import app.pantopus.android.ui.screens.shared.form.maxLength
import app.pantopus.android.ui.screens.shared.form.optionalLength
import app.pantopus.android.ui.screens.shared.form.required
import app.pantopus.android.ui.screens.shared.form.urlOrEmpty
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Stable identifiers for every editable field in the Edit Profile form.
 * Mirrors the iOS `EditProfileField` enum 1:1 — order matches
 * `updateProfileSchema` (`backend/routes/users.js:324-351`).
 */
enum class EditProfileField(val key: String) {
    // About
    FirstName("firstName"),
    MiddleName("middleName"),
    LastName("lastName"),
    Bio("bio"),
    Tagline("tagline"),

    // Contact
    PhoneNumber("phoneNumber"),
    DateOfBirth("dateOfBirth"),

    // Address
    Address("address"),
    City("city"),
    State("state"),
    Zipcode("zipcode"),

    // Social — backend stores these in the `social_links` jsonb column
    // but accepts them as flat keys on PATCH.
    Website("website"),
    Linkedin("linkedin"),
    Twitter("twitter"),
    Instagram("instagram"),
    Facebook("facebook"),

    // Visibility
    ProfileVisibility("profileVisibility"),
}

/** Render states for the Edit Profile screen. */
sealed interface EditProfileUiState {
    data object Loading : EditProfileUiState

    data object Loaded : EditProfileUiState

    data class Error(val message: String) : EditProfileUiState
}

/** Tone-tagged transient message surfaced by the form. */
data class EditProfileToast(
    val text: String,
    val isError: Boolean,
)

/**
 * Backs `EditProfileScreen`. Fetches `GET /api/users/profile`
 * (`backend/routes/users.js:1962`) and submits
 * `PATCH /api/users/profile` (`backend/routes/users.js:2052`). The
 * editable field set is defined by `updateProfileSchema`
 * (`backend/routes/users.js:324-351`) and is mirrored 1:1 by
 * [EditProfileField].
 *
 * Note: the design also calls for an avatar upload, an editable email
 * when unverified, and boolean visibility toggles
 * (`profile_visibility_public` + `show_in_neighbor_discovery`). None
 * exist in `updateProfileSchema` today, so those affordances are
 * intentionally omitted on both platforms until the backend adds the
 * keys.
 */
@HiltViewModel
class EditProfileViewModel
    @Inject
    constructor(
        private val repo: ProfileRepository,
        private val networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _state = MutableStateFlow<EditProfileUiState>(EditProfileUiState.Loading)
        val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

        private val _fields =
            MutableStateFlow(
                EditProfileField.entries.associateWith {
                    FormFieldState(id = it.key)
                },
            )
        val fields: StateFlow<Map<EditProfileField, FormFieldState>> = _fields.asStateFlow()

        private val _isSaving = MutableStateFlow(false)
        val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

        private val _toast = MutableStateFlow<EditProfileToast?>(null)
        val toast: StateFlow<EditProfileToast?> = _toast.asStateFlow()

        private val _shouldDismiss = MutableStateFlow(false)
        val shouldDismiss: StateFlow<Boolean> = _shouldDismiss.asStateFlow()

        /** Increments on a save attempt whose validation fails — drives
         *  the first-invalid shake on the iOS counterpart. */
        private val _shakeTrigger = MutableStateFlow(0)
        val shakeTrigger: StateFlow<Int> = _shakeTrigger.asStateFlow()

        private val _email = MutableStateFlow("")
        val email: StateFlow<String> = _email.asStateFlow()

        private val _emailVerified = MutableStateFlow(false)
        val emailVerified: StateFlow<Boolean> = _emailVerified.asStateFlow()

        val aggregate: FormAggregate
            get() = FormAggregate.from(EditProfileField.entries.mapNotNull { _fields.value[it] })

        val isValid: Boolean get() = aggregate.isValid
        val isDirty: Boolean get() = aggregate.isDirty

        /** Idempotent — refuses to refetch when already loaded. */
        fun load() {
            if (_state.value is EditProfileUiState.Loaded) return
            _state.value = EditProfileUiState.Loading
            viewModelScope.launch {
                when (val result = repo.ownProfile()) {
                    is NetworkResult.Success -> {
                        hydrate(result.data.user)
                        _state.value = EditProfileUiState.Loaded
                    }
                    is NetworkResult.Failure ->
                        _state.value = EditProfileUiState.Error(
                            result.error.message.ifBlank { "Couldn't load profile." },
                        )
                }
            }
        }

        fun refresh() = load()

        fun update(
            field: EditProfileField,
            value: String,
        ) {
            val map = _fields.value.toMutableMap()
            val snapshot = map[field] ?: FormFieldState(id = field.key)
            map[field] =
                snapshot.copy(
                    value = value,
                    touched = true,
                    error = validator(field).validate(value),
                )
            _fields.value = map
        }

        fun dismissToast() {
            _toast.value = null
        }

        fun acknowledgeDismiss() {
            _shouldDismiss.value = false
        }

        /** Run every validator. Returns the first invalid field, if any. */
        fun validateAll(): EditProfileField? {
            var firstInvalid: EditProfileField? = null
            val map = _fields.value.toMutableMap()
            for (field in EditProfileField.entries) {
                val snapshot = map[field] ?: FormFieldState(id = field.key)
                val message = validator(field).validate(snapshot.value)
                map[field] = snapshot.copy(error = message, touched = true)
                if (firstInvalid == null && message != null) firstInvalid = field
            }
            _fields.value = map
            return firstInvalid
        }

        fun save() {
            if (_isSaving.value) return
            val invalid = validateAll()
            if (invalid != null) {
                _shakeTrigger.value = _shakeTrigger.value + 1
                _toast.value = EditProfileToast("Fix the highlighted field.", isError = true)
                Analytics.track(AnalyticsEvent.FormEditProfileValidationError(field = invalid.key))
                return
            }
            if (!aggregate.isDirty) return
            if (!networkMonitor.isOnline.value) {
                // P15: don't silently queue. Surface the error inline.
                _toast.value =
                    EditProfileToast(
                        "You're offline. Try again when you're back online.",
                        isError = true,
                    )
                Analytics.track(AnalyticsEvent.FormEditProfileSubmit(result = AnalyticsResult.ERROR))
                return
            }
            _isSaving.value = true
            viewModelScope.launch {
                when (val result = repo.updateProfile(buildRequest())) {
                    is NetworkResult.Success -> {
                        hydrate(result.data.user)
                        _toast.value = EditProfileToast("Profile updated.", isError = false)
                        _shouldDismiss.value = true
                        Analytics.track(
                            AnalyticsEvent.FormEditProfileSubmit(result = AnalyticsResult.SUCCESS),
                        )
                    }
                    is NetworkResult.Failure -> {
                        _toast.value =
                            EditProfileToast(
                                result.error.message.ifBlank { "Couldn't save profile." },
                                isError = true,
                            )
                        Analytics.track(
                            AnalyticsEvent.FormEditProfileSubmit(result = AnalyticsResult.ERROR),
                        )
                    }
                }
                _isSaving.value = false
            }
        }

        private fun hydrate(profile: UserProfile) {
            _email.value = profile.email
            _emailVerified.value = profile.verified
            seed(EditProfileField.FirstName, profile.firstName)
            seed(EditProfileField.MiddleName, profile.middleName.orEmpty())
            seed(EditProfileField.LastName, profile.lastName)
            seed(EditProfileField.Bio, profile.bio.orEmpty())
            seed(EditProfileField.Tagline, profile.tagline.orEmpty())
            seed(EditProfileField.PhoneNumber, profile.phoneNumber.orEmpty())
            seed(EditProfileField.DateOfBirth, profile.dateOfBirth.orEmpty())
            seed(EditProfileField.Address, profile.address.orEmpty())
            seed(EditProfileField.City, profile.city.orEmpty())
            seed(EditProfileField.State, profile.state.orEmpty())
            seed(EditProfileField.Zipcode, profile.zipcode.orEmpty())
            seed(EditProfileField.Website, profile.socialLinks?.website.orEmpty())
            seed(EditProfileField.Linkedin, profile.socialLinks?.linkedin.orEmpty())
            seed(EditProfileField.Twitter, profile.socialLinks?.twitter.orEmpty())
            seed(EditProfileField.Instagram, profile.socialLinks?.instagram.orEmpty())
            seed(EditProfileField.Facebook, profile.socialLinks?.facebook.orEmpty())
            seed(EditProfileField.ProfileVisibility, profile.profileVisibility ?: "public")
        }

        private fun seed(
            field: EditProfileField,
            value: String,
        ) {
            val error = validator(field).validate(value)
            val map = _fields.value.toMutableMap()
            map[field] =
                FormFieldState(
                    id = field.key,
                    value = value,
                    originalValue = value,
                    touched = false,
                    error = error,
                )
            _fields.value = map
        }

        private fun validator(field: EditProfileField): FormValidator = VALIDATORS[field] ?: FormValidator { null }

        /**
         * Assemble a PATCH body containing only the dirty fields. Empty
         * strings are kept for fields whose schema entry has
         * `.allow('', null)` so the user can clear them; non-nullable
         * fields are skipped when blank so the server doesn't reject the
         * call.
         */
        private fun buildRequest(): ProfileUpdateRequest {
            val map = _fields.value
            fun trimmed(field: EditProfileField): String? {
                val snapshot = map[field] ?: return null
                if (!snapshot.isDirty) return null
                val text = snapshot.value.trim()
                if (text.isEmpty() && field !in ALLOWS_EMPTY) return null
                return text
            }
            return ProfileUpdateRequest(
                firstName = trimmed(EditProfileField.FirstName),
                middleName = trimmed(EditProfileField.MiddleName),
                lastName = trimmed(EditProfileField.LastName),
                phoneNumber = trimmed(EditProfileField.PhoneNumber),
                address = trimmed(EditProfileField.Address),
                city = trimmed(EditProfileField.City),
                state = trimmed(EditProfileField.State),
                zipcode = trimmed(EditProfileField.Zipcode),
                dateOfBirth = trimmed(EditProfileField.DateOfBirth),
                bio = trimmed(EditProfileField.Bio),
                tagline = trimmed(EditProfileField.Tagline),
                profileVisibility = trimmed(EditProfileField.ProfileVisibility),
                website = trimmed(EditProfileField.Website),
                linkedin = trimmed(EditProfileField.Linkedin),
                twitter = trimmed(EditProfileField.Twitter),
                instagram = trimmed(EditProfileField.Instagram),
                facebook = trimmed(EditProfileField.Facebook),
            )
        }

        companion object {
            /** Fields whose Joi declaration allows `''` / `null`. */
            private val ALLOWS_EMPTY: Set<EditProfileField> =
                setOf(
                    EditProfileField.MiddleName,
                    EditProfileField.Bio,
                    EditProfileField.Tagline,
                    EditProfileField.DateOfBirth,
                    EditProfileField.Website,
                    EditProfileField.Linkedin,
                    EditProfileField.Twitter,
                    EditProfileField.Instagram,
                    EditProfileField.Facebook,
                )

            /** Validators keyed by field — mirrors iOS exactly. */
            private val VALIDATORS: Map<EditProfileField, FormValidator> =
                mapOf(
                    EditProfileField.FirstName to
                        FormValidator.all(
                            listOf(FormValidator.required("First name"), FormValidator.maxLength(255)),
                        ),
                    EditProfileField.LastName to
                        FormValidator.all(
                            listOf(FormValidator.required("Last name"), FormValidator.maxLength(255)),
                        ),
                    EditProfileField.MiddleName to
                        FormValidator.optionalLength("Middle name", min = 1, max = 255),
                    EditProfileField.Bio to FormValidator.maxLength(2000),
                    EditProfileField.Tagline to FormValidator.maxLength(255),
                    EditProfileField.PhoneNumber to FormValidator.e164Phone(),
                    EditProfileField.DateOfBirth to FormValidator.isoDateOrEmpty(),
                    EditProfileField.Address to FormValidator.optionalLength("Address", min = 5, max = 255),
                    EditProfileField.City to FormValidator.optionalLength("City", min = 2, max = 100),
                    EditProfileField.State to FormValidator.optionalLength("State", min = 2, max = 50),
                    EditProfileField.Zipcode to FormValidator.optionalLength("Zipcode", min = 3, max = 20),
                    EditProfileField.Website to FormValidator.urlOrEmpty(),
                    EditProfileField.Linkedin to FormValidator.urlOrEmpty(),
                    EditProfileField.Twitter to FormValidator.urlOrEmpty(),
                    EditProfileField.Instagram to FormValidator.urlOrEmpty(),
                    EditProfileField.Facebook to FormValidator.urlOrEmpty(),
                    EditProfileField.ProfileVisibility to
                        FormValidator { value ->
                            if (value in VISIBILITY_OPTIONS) null else "Pick a visibility option."
                        },
                )

            internal val VISIBILITY_OPTIONS: List<String> = listOf("public", "registered", "private")
        }
    }
