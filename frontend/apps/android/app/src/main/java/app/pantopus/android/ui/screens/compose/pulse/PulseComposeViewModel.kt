@file:Suppress("MagicNumber", "PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.compose.pulse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.data.analytics.AnalyticsResult
import app.pantopus.android.data.api.models.posts.PostCreateRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.network.NetworkMonitor
import app.pantopus.android.data.posts.PostsRepository
import app.pantopus.android.ui.screens.feed.pulse.PulseIntent
import app.pantopus.android.ui.screens.shared.form.FormAggregate
import app.pantopus.android.ui.screens.shared.form.FormFieldState
import app.pantopus.android.ui.screens.shared.form.FormValidator
import app.pantopus.android.ui.screens.shared.form.all
import app.pantopus.android.ui.screens.shared.form.isoDateOrEmpty
import app.pantopus.android.ui.screens.shared.form.maxLength
import app.pantopus.android.ui.screens.shared.form.required
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Compose-form variants. Mirrors the iOS `PulseComposeIntent` 1:1 —
 * every `PulseIntent` value except `All` (a feed-row filter sentinel).
 */
enum class PulseComposeIntent(
    val key: String,
    val label: String,
    /** Backend `post_type` enum sent on `POST /api/posts`. */
    val postType: String,
    /** v1.2 `purpose` tag — mirrors postType for sortability. */
    val purpose: String,
) {
    Ask("ask", "Ask", postType = "ask_local", purpose = "ask"),
    Recommend("recommend", "Recommend", postType = "recommendation", purpose = "recommend"),
    Event("event", "Event", postType = "event", purpose = "event"),
    Lost("lost", "Lost & Found", postType = "lost_found", purpose = "lost_found"),
    Announce("announce", "Announce", postType = "local_update", purpose = "local_update"),
    ;

    /** Right-action label for the Form top-bar. */
    val ctaLabel: String get() = "Post"

    companion object {
        fun fromKey(key: String): PulseComposeIntent = entries.firstOrNull { it.key == key } ?: Ask

        /** Bridge a feed-row intent into the compose subset. `All` → `Ask`. */
        fun fromFeedIntent(feed: PulseIntent): PulseComposeIntent =
            when (feed) {
                PulseIntent.All, PulseIntent.Ask -> Ask
                PulseIntent.Recommend -> Recommend
                PulseIntent.Event -> Event
                PulseIntent.Lost -> Lost
                PulseIntent.Announce -> Announce
            }
    }
}

/**
 * Stable identifier for every text field in the compose form. Selector
 * state (category chip / rating stars / lost-vs-found toggle / announce
 * audience) lives on the view-model directly so the field map only
 * carries values the user can type into.
 */
enum class PulseComposeField(val key: String) {
    Title("title"),
    Body("body"),
    RecommendBusiness("recommendBusiness"),
    EventDate("eventDate"),
    EventLocation("eventLocation"),
    EventCapacity("eventCapacity"),
    LostLastSeenLocation("lostLastSeenLocation"),
    LostLastSeenDate("lostLastSeenDate"),
}

/** Author identity the post will be created under. */
enum class PulseComposeIdentity(val key: String, val label: String) {
    Personal("personal", "Personal"),
    Home("home", "Home"),
    Business("business", "Business"),
    ;

    /** Maps to backend `postAs` enum. */
    val postAs: String get() = key
}

/** Visibility scope option. */
enum class PulseComposeVisibility(val key: String, val label: String) {
    Neighbors("neighborhood", "Neighbors"),
    Connections("connections", "Connections"),
    PublicFeed("public", "Public"),
}

enum class PulseLostFoundKind(val key: String, val label: String) {
    Lost("lost", "Lost"),
    Found("found", "Found"),
}

enum class PulseAnnounceAudience(val key: String, val label: String) {
    Neighbors("neighbors", "Neighbors"),
    Followers("followers", "Followers"),
    PublicFeed("public", "Public"),
    ;

    /** Map the announce-audience chip to the backend visibility enum. */
    val backendVisibility: String
        get() =
            when (this) {
                Neighbors -> "neighborhood"
                Followers -> "followers"
                PublicFeed -> "public"
            }
}

enum class PulseAskCategory(val key: String, val label: String) {
    Handyman("handyman", "Handyman"),
    Cleaning("cleaning", "Cleaning"),
    Advice("advice", "Advice"),
    Other("other", "Other"),
}

/** One picked photo. Carries the bytes + a stable id for ForEach. */
data class PulseComposePhoto(
    val id: String,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is PulseComposePhoto && other.id == id

    override fun hashCode(): Int = id.hashCode()
}

/** Maximum number of photos the picker accepts. */
const val PULSE_COMPOSE_MAX_PHOTOS: Int = 4

/** Render state for the compose screen. */
sealed interface PulseComposeUiState {
    data object Idle : PulseComposeUiState

    data object Submitting : PulseComposeUiState

    data class Success(val postId: String?) : PulseComposeUiState

    data class Error(val message: String) : PulseComposeUiState
}

/** Tone-tagged transient message surfaced by the form. */
data class PulseComposeToast(
    val text: String,
    val isError: Boolean,
)

/**
 * Backs `PulseComposeScreen`. Holds intent + identity + visibility,
 * per-field state, picked photos, and a submit pipeline that hits
 * `POST /api/posts` via [PostsRepository]. The wire shape mirrors
 * `createPostSchema` at `backend/routes/posts.js:196-300`.
 */
@HiltViewModel
class PulseComposeViewModel
    @Inject
    constructor(
        private val repo: PostsRepository,
        private val networkMonitor: NetworkMonitor,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val _state = MutableStateFlow<PulseComposeUiState>(PulseComposeUiState.Idle)
        val state: StateFlow<PulseComposeUiState> = _state.asStateFlow()

        private val initialIntent: PulseComposeIntent =
            PulseComposeIntent.fromKey(savedStateHandle.get<String>(INTENT_KEY) ?: PulseComposeIntent.Ask.key)

        private val _activeIntent = MutableStateFlow(initialIntent)
        val activeIntent: StateFlow<PulseComposeIntent> = _activeIntent.asStateFlow()

        private val _identity = MutableStateFlow(PulseComposeIdentity.Personal)
        val identity: StateFlow<PulseComposeIdentity> = _identity.asStateFlow()

        private val _visibility = MutableStateFlow(PulseComposeVisibility.Neighbors)
        val visibility: StateFlow<PulseComposeVisibility> = _visibility.asStateFlow()

        private val _lostFoundKind = MutableStateFlow(PulseLostFoundKind.Lost)
        val lostFoundKind: StateFlow<PulseLostFoundKind> = _lostFoundKind.asStateFlow()

        private val _announceAudience = MutableStateFlow(PulseAnnounceAudience.Neighbors)
        val announceAudience: StateFlow<PulseAnnounceAudience> = _announceAudience.asStateFlow()

        private val _askCategory = MutableStateFlow(PulseAskCategory.Handyman)
        val askCategory: StateFlow<PulseAskCategory> = _askCategory.asStateFlow()

        private val _recommendRating = MutableStateFlow(DEFAULT_RECOMMEND_RATING)
        val recommendRating: StateFlow<Int> = _recommendRating.asStateFlow()

        private val _fields =
            MutableStateFlow(
                PulseComposeField.entries.associateWith {
                    FormFieldState(id = it.key)
                },
            )
        val fields: StateFlow<Map<PulseComposeField, FormFieldState>> = _fields.asStateFlow()

        private val _photos = MutableStateFlow<List<PulseComposePhoto>>(emptyList())
        val photos: StateFlow<List<PulseComposePhoto>> = _photos.asStateFlow()

        private val _toast = MutableStateFlow<PulseComposeToast?>(null)
        val toast: StateFlow<PulseComposeToast?> = _toast.asStateFlow()

        private val _shakeTrigger = MutableStateFlow(0)
        val shakeTrigger: StateFlow<Int> = _shakeTrigger.asStateFlow()

        private val _shouldDismiss = MutableStateFlow(false)
        val shouldDismiss: StateFlow<Boolean> = _shouldDismiss.asStateFlow()

        // MARK: - Selector updates

        fun selectIntent(intent: PulseComposeIntent) {
            if (_activeIntent.value == intent) return
            _activeIntent.value = intent
        }

        fun selectIdentity(identity: PulseComposeIdentity) {
            _identity.value = identity
        }

        fun selectVisibility(visibility: PulseComposeVisibility) {
            _visibility.value = visibility
        }

        fun selectLostFoundKind(kind: PulseLostFoundKind) {
            _lostFoundKind.value = kind
        }

        fun selectAnnounceAudience(audience: PulseAnnounceAudience) {
            _announceAudience.value = audience
        }

        fun selectAskCategory(category: PulseAskCategory) {
            _askCategory.value = category
        }

        fun selectRecommendRating(rating: Int) {
            _recommendRating.value = rating.coerceIn(1, 5)
        }

        fun update(
            field: PulseComposeField,
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

        fun setPhotos(photos: List<PulseComposePhoto>) {
            _photos.value = photos.take(PULSE_COMPOSE_MAX_PHOTOS)
        }

        fun appendPhoto(photo: PulseComposePhoto) {
            if (_photos.value.size >= PULSE_COMPOSE_MAX_PHOTOS) return
            _photos.value = _photos.value + photo
        }

        fun removePhoto(id: String) {
            _photos.value = _photos.value.filterNot { it.id == id }
        }

        fun dismissToast() {
            _toast.value = null
        }

        fun acknowledgeDismiss() {
            _shouldDismiss.value = false
        }

        // MARK: - Dirty / valid

        val isDirty: Boolean
            get() {
                if (_photos.value.isNotEmpty()) return true
                if (_identity.value != PulseComposeIdentity.Personal) return true
                if (_visibility.value != PulseComposeVisibility.Neighbors) return true
                if (activeIntentFields().any { (_fields.value[it]?.isDirty == true) }) return true
                return when (_activeIntent.value) {
                    PulseComposeIntent.Ask -> _askCategory.value != PulseAskCategory.Handyman
                    PulseComposeIntent.Recommend -> _recommendRating.value != DEFAULT_RECOMMEND_RATING
                    PulseComposeIntent.Lost -> _lostFoundKind.value != PulseLostFoundKind.Lost
                    PulseComposeIntent.Announce -> _announceAudience.value != PulseAnnounceAudience.Neighbors
                    PulseComposeIntent.Event -> false
                }
            }

        val isValid: Boolean
            get() = activeIntentFields().all { _fields.value[it]?.error == null }

        val isSubmitting: Boolean
            get() = _state.value is PulseComposeUiState.Submitting

        /** The fields the active intent's sub-form surfaces. */
        fun activeIntentFields(): List<PulseComposeField> =
            when (_activeIntent.value) {
                PulseComposeIntent.Ask -> listOf(PulseComposeField.Title, PulseComposeField.Body)
                PulseComposeIntent.Recommend -> listOf(PulseComposeField.RecommendBusiness, PulseComposeField.Body)
                PulseComposeIntent.Event ->
                    listOf(
                        PulseComposeField.Title,
                        PulseComposeField.EventDate,
                        PulseComposeField.EventLocation,
                        PulseComposeField.EventCapacity,
                        PulseComposeField.Body,
                    )
                PulseComposeIntent.Lost ->
                    listOf(
                        PulseComposeField.Body,
                        PulseComposeField.LostLastSeenLocation,
                        PulseComposeField.LostLastSeenDate,
                    )
                PulseComposeIntent.Announce -> listOf(PulseComposeField.Title, PulseComposeField.Body)
            }

        val aggregate: FormAggregate
            get() = FormAggregate.from(activeIntentFields().mapNotNull { _fields.value[it] })

        // MARK: - Validation

        @Suppress("CyclomaticComplexMethod", "LongMethod")
        private fun validator(field: PulseComposeField): FormValidator =
            when (field) {
                PulseComposeField.Title ->
                    FormValidator.all(listOf(FormValidator.required("Title"), FormValidator.maxLength(TITLE_MAX)))
                PulseComposeField.Body ->
                    FormValidator.all(
                        listOf(
                            FormValidator.required("Description"),
                            FormValidator { value ->
                                val trimmed = value.trim()
                                if (trimmed.length > BODY_MAX) {
                                    "Description must be $BODY_MAX characters or fewer."
                                } else {
                                    null
                                }
                            },
                        ),
                    )
                PulseComposeField.RecommendBusiness ->
                    FormValidator { value ->
                        val trimmed = value.trim()
                        when {
                            trimmed.isEmpty() -> "Add the business name."
                            trimmed.length > BUSINESS_NAME_MAX -> "Must be $BUSINESS_NAME_MAX characters or fewer."
                            else -> null
                        }
                    }
                PulseComposeField.EventDate ->
                    FormValidator.all(listOf(FormValidator.required("Event date"), FormValidator.isoDateOrEmpty()))
                PulseComposeField.EventLocation ->
                    FormValidator.all(listOf(FormValidator.required("Location"), FormValidator.maxLength(LOCATION_MAX)))
                PulseComposeField.EventCapacity ->
                    FormValidator { value ->
                        val trimmed = value.trim()
                        if (trimmed.isEmpty()) return@FormValidator null
                        val n = trimmed.toIntOrNull()
                        when {
                            n == null || n <= 0 -> "Capacity must be a positive number."
                            n > CAPACITY_MAX -> "Capacity is too large."
                            else -> null
                        }
                    }
                PulseComposeField.LostLastSeenLocation ->
                    FormValidator.all(listOf(FormValidator.required("Last seen"), FormValidator.maxLength(LOCATION_MAX)))
                PulseComposeField.LostLastSeenDate -> FormValidator.isoDateOrEmpty()
            }

        /** Touch every active field, return the first invalid id if any. */
        fun validateAll(): PulseComposeField? {
            var firstInvalid: PulseComposeField? = null
            val map = _fields.value.toMutableMap()
            for (field in activeIntentFields()) {
                val snapshot = map[field] ?: FormFieldState(id = field.key)
                val message = validator(field).validate(snapshot.value)
                map[field] = snapshot.copy(error = message, touched = true)
                if (firstInvalid == null && message != null) firstInvalid = field
            }
            _fields.value = map
            return firstInvalid
        }

        // MARK: - Submit

        fun submit() {
            if (_state.value is PulseComposeUiState.Submitting) return
            val invalid = validateAll()
            if (invalid != null) {
                _shakeTrigger.value = _shakeTrigger.value + 1
                _toast.value = PulseComposeToast("Fix the highlighted field.", isError = true)
                Analytics.track(
                    AnalyticsEvent.FormPulseComposeValidationError(
                        intent = _activeIntent.value.key,
                        field = invalid.key,
                    ),
                )
                return
            }
            if (!networkMonitor.isOnline.value) {
                _toast.value =
                    PulseComposeToast("You're offline. Try again when you're back online.", isError = true)
                Analytics.track(
                    AnalyticsEvent.FormPulseComposeSubmit(
                        intent = _activeIntent.value.key,
                        result = AnalyticsResult.ERROR,
                    ),
                )
                return
            }
            _state.value = PulseComposeUiState.Submitting
            viewModelScope.launch {
                when (val result = repo.createPost(buildRequest())) {
                    is NetworkResult.Success -> {
                        _state.value = PulseComposeUiState.Success(postId = result.data.postId)
                        _toast.value = PulseComposeToast("Posted", isError = false)
                        _shouldDismiss.value = true
                        Analytics.track(
                            AnalyticsEvent.FormPulseComposeSubmit(
                                intent = _activeIntent.value.key,
                                result = AnalyticsResult.SUCCESS,
                            ),
                        )
                    }
                    is NetworkResult.Failure -> {
                        val message = result.error.message.ifBlank { "Couldn't post. Try again." }
                        _state.value = PulseComposeUiState.Error(message)
                        _toast.value = PulseComposeToast(message, isError = true)
                        Analytics.track(
                            AnalyticsEvent.FormPulseComposeSubmit(
                                intent = _activeIntent.value.key,
                                result = AnalyticsResult.ERROR,
                            ),
                        )
                    }
                }
            }
        }

        // MARK: - Request assembly

        /** Compose the `POST /api/posts` body from active-intent state. */
        fun buildRequest(): PostCreateRequest {
            val bodyValue = trimmedValue(PulseComposeField.Body)
            val titleValue = trimmedValue(PulseComposeField.Title)
            val intent = _activeIntent.value
            return when (intent) {
                PulseComposeIntent.Ask ->
                    PostCreateRequest(
                        content = bodyValue,
                        title = titleValue.ifEmpty { null },
                        postType = intent.postType,
                        visibility = _visibility.value.key,
                        postAs = _identity.value.postAs,
                        serviceCategory = _askCategory.value.key,
                        purpose = intent.purpose,
                    )
                PulseComposeIntent.Recommend -> {
                    val business = trimmedValue(PulseComposeField.RecommendBusiness)
                    PostCreateRequest(
                        content = composeRecommendBody(_recommendRating.value, bodyValue),
                        postType = intent.postType,
                        visibility = _visibility.value.key,
                        postAs = _identity.value.postAs,
                        businessName = business.ifEmpty { null },
                        purpose = intent.purpose,
                    )
                }
                PulseComposeIntent.Event -> {
                    val venue = trimmedValue(PulseComposeField.EventLocation)
                    val dateRaw = trimmedValue(PulseComposeField.EventDate)
                    PostCreateRequest(
                        content = bodyValue,
                        title = titleValue.ifEmpty { null },
                        postType = intent.postType,
                        visibility = _visibility.value.key,
                        postAs = _identity.value.postAs,
                        eventDate = dateRaw.ifEmpty { null }?.let { isoDateTime(it) },
                        eventVenue = venue.ifEmpty { null },
                        purpose = intent.purpose,
                    )
                }
                PulseComposeIntent.Lost -> {
                    val lastSeen = trimmedValue(PulseComposeField.LostLastSeenLocation)
                    PostCreateRequest(
                        content = prefixLastSeen(bodyValue, lastSeen),
                        postType = intent.postType,
                        visibility = _visibility.value.key,
                        postAs = _identity.value.postAs,
                        lostFoundType = _lostFoundKind.value.key,
                        purpose = intent.purpose,
                    )
                }
                PulseComposeIntent.Announce ->
                    PostCreateRequest(
                        content = bodyValue,
                        title = titleValue.ifEmpty { null },
                        postType = intent.postType,
                        visibility = _announceAudience.value.backendVisibility,
                        postAs = _identity.value.postAs,
                        audience = _announceAudience.value.key,
                        purpose = intent.purpose,
                    )
            }
        }

        private fun trimmedValue(field: PulseComposeField): String = (_fields.value[field]?.value ?: "").trim()

        private fun composeRecommendBody(
            stars: Int,
            body: String,
        ): String {
            val clamped = stars.coerceIn(1, 5)
            val row = "★".repeat(clamped) + "☆".repeat(5 - clamped)
            return if (body.isEmpty()) row else "$row\n\n$body"
        }

        private fun prefixLastSeen(
            body: String,
            location: String,
        ): String =
            if (location.isEmpty()) body else "Last seen: $location\n\n$body"

        /**
         * Normalize an event date to ISO-8601. Accepts plain `yyyy-MM-dd`
         * (treated as 09:00 UTC). Returns the raw string unchanged when
         * it already carries a `T` separator — the view emits both shapes.
         */
        private fun isoDateTime(raw: String): String {
            if (raw.contains('T')) return raw
            return "${raw}T09:00:00Z"
        }

        companion object {
            const val INTENT_KEY = "intent"
            private const val TITLE_MAX = 255
            private const val BODY_MAX = 5000
            private const val LOCATION_MAX = 255
            private const val BUSINESS_NAME_MAX = 255
            private const val CAPACITY_MAX = 100_000
            private const val DEFAULT_RECOMMEND_RATING = 5
        }
    }
