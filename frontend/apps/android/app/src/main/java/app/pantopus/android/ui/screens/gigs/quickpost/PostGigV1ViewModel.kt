@file:Suppress("MagicNumber", "TooManyFunctions")

package app.pantopus.android.ui.screens.gigs.quickpost

import androidx.lifecycle.ViewModel
import app.pantopus.android.ui.screens.gigs.GigsCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

enum class PostGigV1PriceType(
    val label: String,
    val unitLabel: String?,
) {
    Flat("Flat", "flat"),
    Hourly("Hourly", "/ hr"),
    Free("Free", null),
}

enum class PostGigV1PhotoTone { Sofa, Stairs, Street, Neutral }

data class PostGigV1Photo(
    val id: String,
    val tone: PostGigV1PhotoTone,
)

data class PostGigV1Form(
    val category: GigsCategory = GigsCategory.All,
    val title: String = "",
    val description: String = "",
    val price: String = "",
    val priceType: PostGigV1PriceType = PostGigV1PriceType.Flat,
    val scheduledAt: LocalDateTime = LocalDateTime.now().plusDays(1),
    val location: String = "",
    val photos: List<PostGigV1Photo> = emptyList(),
) {
    val hasAnyInput: Boolean
        get() =
            category != GigsCategory.All ||
                title.isNotBlank() ||
                description.isNotBlank() ||
                price.isNotBlank() ||
                priceType != PostGigV1PriceType.Flat ||
                location.isNotBlank() ||
                photos.isNotEmpty()
}

enum class PostGigV1Field { Category, Title, Description, Price, DateTime, Location }

data class PostGigV1ValidationError(
    val field: PostGigV1Field,
    val message: String,
)

sealed interface PostGigV1UiState {
    data object Loading : PostGigV1UiState

    data object Empty : PostGigV1UiState

    data class Content(
        val form: PostGigV1Form = PostGigV1Form(),
        val validationErrors: List<PostGigV1ValidationError> = emptyList(),
        val isSubmitting: Boolean = false,
        val postedGigId: String? = null,
    ) : PostGigV1UiState {
        val canAttemptSubmit: Boolean = !isSubmitting
        val isPostEnabled: Boolean = !isSubmitting && (form.hasAnyInput || validationErrors.isNotEmpty())
    }

    data class FatalError(
        val message: String,
    ) : PostGigV1UiState
}

sealed interface PostGigV1Event {
    data class Posted(
        val gigId: String,
    ) : PostGigV1Event
}

@HiltViewModel
class PostGigV1ViewModel
    @Inject
    constructor() : ViewModel() {
        private val _state = MutableStateFlow<PostGigV1UiState>(PostGigV1UiState.Content())
        val state: StateFlow<PostGigV1UiState> = _state.asStateFlow()

        private val _pendingEvent = MutableStateFlow<PostGigV1Event?>(null)
        val pendingEvent: StateFlow<PostGigV1Event?> = _pendingEvent.asStateFlow()

        fun acknowledgeEvent() {
            _pendingEvent.value = null
        }

        fun retry() {
            _state.value = PostGigV1UiState.Content()
        }

        fun startFromEmpty() {
            _state.value = PostGigV1UiState.Content()
        }

        fun preselectCategoryIfNeeded(category: GigsCategory) {
            updateContent { content ->
                if (content.form.category != GigsCategory.All || category == GigsCategory.All) {
                    content
                } else {
                    content.copy(form = content.form.copy(category = category))
                }
            }
        }

        fun updateCategory(category: GigsCategory) {
            updateForm { it.copy(category = category) }
        }

        fun updateTitle(title: String) {
            updateForm { it.copy(title = title) }
        }

        fun updateDescription(description: String) {
            updateForm {
                it.copy(description = description.take(PostGigV1SampleData.DESCRIPTION_MAX_LENGTH))
            }
        }

        fun updatePrice(price: String) {
            updateForm {
                it.copy(price = price.filter { char -> char.isDigit() || char == '.' })
            }
        }

        fun updatePriceType(type: PostGigV1PriceType) {
            updateForm {
                it.copy(priceType = type, price = if (type == PostGigV1PriceType.Free) "" else it.price)
            }
        }

        fun updateScheduledAt(date: LocalDateTime) {
            updateForm { it.copy(scheduledAt = date) }
        }

        fun updateLocation(location: String) {
            updateForm { it.copy(location = location) }
        }

        fun addPlaceholderPhoto() {
            updateForm { form ->
                if (form.photos.size >= PostGigV1SampleData.MAX_PHOTOS) return@updateForm form
                val tones =
                    listOf(
                        PostGigV1PhotoTone.Sofa,
                        PostGigV1PhotoTone.Stairs,
                        PostGigV1PhotoTone.Street,
                        PostGigV1PhotoTone.Neutral,
                    )
                val index = form.photos.size
                form.copy(
                    photos =
                        form.photos +
                            PostGigV1Photo(
                                id = "photo-${index + 1}",
                                tone = tones[index % tones.size],
                            ),
                )
            }
        }

        fun removePhoto(id: String) {
            updateForm { it.copy(photos = it.photos.filterNot { photo -> photo.id == id }) }
        }

        fun pickNextSaturday() {
            updateScheduledAt(PostGigV1SampleData.filledForm.scheduledAt)
        }

        fun submit(now: LocalDateTime = LocalDateTime.now()) {
            val current = _state.value as? PostGigV1UiState.Content ?: return
            val errors = validate(current.form, now)
            if (errors.isNotEmpty()) {
                _state.value = current.copy(validationErrors = errors)
                return
            }
            val gigId = "gig-v1-${UUID.randomUUID().toString().take(8)}"
            _state.value = current.copy(validationErrors = emptyList(), postedGigId = gigId)
            _pendingEvent.value = PostGigV1Event.Posted(gigId)
        }

        private fun updateForm(transform: (PostGigV1Form) -> PostGigV1Form) {
            updateContent { content -> content.copy(form = transform(content.form)) }
        }

        private fun updateContent(transform: (PostGigV1UiState.Content) -> PostGigV1UiState.Content) {
            val current = _state.value as? PostGigV1UiState.Content ?: return
            _state.value = transform(current)
        }

        private fun validate(
            form: PostGigV1Form,
            now: LocalDateTime,
        ): List<PostGigV1ValidationError> {
            val errors = mutableListOf<PostGigV1ValidationError>()
            if (form.category == GigsCategory.All) {
                errors += PostGigV1ValidationError(PostGigV1Field.Category, "Choose a category.")
            }
            if (form.title.isBlank()) {
                errors += PostGigV1ValidationError(PostGigV1Field.Title, "Title is required.")
            }
            if (form.description.trim().length < PostGigV1SampleData.DESCRIPTION_MIN_LENGTH) {
                errors +=
                    PostGigV1ValidationError(
                        PostGigV1Field.Description,
                        "Description must be at least ${PostGigV1SampleData.DESCRIPTION_MIN_LENGTH} characters.",
                    )
            }
            if (form.priceType != PostGigV1PriceType.Free) {
                val price = form.price.trim()
                if (price.isEmpty()) {
                    errors += PostGigV1ValidationError(PostGigV1Field.Price, "Enter a price, or pick Free.")
                } else if ((price.toDoubleOrNull() ?: 0.0) <= 0.0) {
                    errors += PostGigV1ValidationError(PostGigV1Field.Price, "Price must be greater than zero.")
                }
            }
            if (!form.scheduledAt.isAfter(now)) {
                errors += PostGigV1ValidationError(PostGigV1Field.DateTime, "Date is in the past. Pick a future time.")
            }
            if (form.location.isBlank()) {
                errors += PostGigV1ValidationError(PostGigV1Field.Location, "Add a pickup or meetup location.")
            }
            return errors
        }
    }
