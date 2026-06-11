@file:Suppress("MagicNumber", "TooManyFunctions")

package app.pantopus.android.ui.screens.gigs.quickpost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.gigs.CreateGigBody
import app.pantopus.android.data.api.models.gigs.CreateGigLocation
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.files.FilesRepository
import app.pantopus.android.data.gigs.GigsRepository
import app.pantopus.android.ui.screens.gigs.GigsCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
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

/** P0.2 — per-tile upload lifecycle for the photo grid. */
enum class PostGigV1PhotoStatus { Uploading, Failed, Uploaded }

data class PostGigV1Photo(
    val id: String,
    val tone: PostGigV1PhotoTone,
    /** P0.2 — upload state; sample/preview tiles default to uploaded. */
    val status: PostGigV1PhotoStatus = PostGigV1PhotoStatus.Uploaded,
    /** P0.2 — backend URL once the upload lands; rides `attachments`. */
    val url: String? = null,
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
        /** P0.2 — true while any photo upload is still in flight. */
        val hasUploadsInFlight: Boolean = form.photos.any { it.status == PostGigV1PhotoStatus.Uploading }
        val canAttemptSubmit: Boolean = !isSubmitting && !hasUploadsInFlight
        val isPostEnabled: Boolean = canAttemptSubmit && (form.hasAnyInput || validationErrors.isNotEmpty())
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
    constructor(
        private val repo: GigsRepository,
        private val filesRepo: FilesRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<PostGigV1UiState>(PostGigV1UiState.Content())
        val state: StateFlow<PostGigV1UiState> = _state.asStateFlow()

        private val _pendingEvent = MutableStateFlow<PostGigV1Event?>(null)
        val pendingEvent: StateFlow<PostGigV1Event?> = _pendingEvent.asStateFlow()

        // Stashed so a post failure can flip to FatalError yet restore the
        // filled form on retry (mirrors iOS, where the form survives .error).
        private var lastForm: PostGigV1Form? = null

        // P0.2 — picked-photo bytes (for retry) + per-tile upload jobs.
        private val pendingPhotoBytes = mutableMapOf<String, PostGigV1PickedPhoto>()
        private val uploadJobs = mutableMapOf<String, Job>()

        fun acknowledgeEvent() {
            _pendingEvent.value = null
        }

        fun retry() {
            _state.value = PostGigV1UiState.Content(form = lastForm ?: PostGigV1Form())
            lastForm = null
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

        /**
         * P0.2 — accept a picked photo and upload it immediately via
         * `POST /api/files/upload` (`FilesRepository`). The tile tracks
         * uploading / failed (tap-to-retry) / uploaded-URL states.
         */
        fun addPickedPhoto(picked: PostGigV1PickedPhoto) {
            val content = _state.value as? PostGigV1UiState.Content ?: return
            if (content.form.photos.size >= PostGigV1SampleData.MAX_PHOTOS) return
            val id = "photo-${UUID.randomUUID()}"
            pendingPhotoBytes[id] = picked
            updateForm { form ->
                form.copy(
                    photos =
                        form.photos +
                            PostGigV1Photo(
                                id = id,
                                tone = PostGigV1PhotoTone.Neutral,
                                status = PostGigV1PhotoStatus.Uploading,
                            ),
                )
            }
            startUpload(id)
        }

        /** P0.2 — retry a failed upload tile (bytes are still held). */
        fun retryPhotoUpload(id: String) {
            if (pendingPhotoBytes[id] == null) return
            updatePhoto(id) { it.copy(status = PostGigV1PhotoStatus.Uploading) }
            startUpload(id)
        }

        fun removePhoto(id: String) {
            uploadJobs.remove(id)?.cancel()
            pendingPhotoBytes.remove(id)
            updateForm { it.copy(photos = it.photos.filterNot { photo -> photo.id == id }) }
        }

        private fun startUpload(id: String) {
            val picked = pendingPhotoBytes[id] ?: return
            uploadJobs[id] =
                viewModelScope.launch {
                    val result =
                        filesRepo.uploadFile(
                            filename = picked.filename,
                            mimeType = picked.mimeType,
                            bytes = picked.bytes,
                            fileType = GIG_PHOTO_FILE_TYPE,
                            visibility = "public",
                        )
                    when (result) {
                        is NetworkResult.Success -> {
                            pendingPhotoBytes.remove(id)
                            uploadJobs.remove(id)
                            updatePhoto(id) {
                                it.copy(status = PostGigV1PhotoStatus.Uploaded, url = result.data.file.url)
                            }
                        }
                        is NetworkResult.Failure ->
                            updatePhoto(id) { it.copy(status = PostGigV1PhotoStatus.Failed) }
                    }
                }
        }

        private fun updatePhoto(
            id: String,
            transform: (PostGigV1Photo) -> PostGigV1Photo,
        ) {
            updateForm { form ->
                form.copy(photos = form.photos.map { if (it.id == id) transform(it) else it })
            }
        }

        /**
         * Validate, then create the gig via `POST /api/gigs`
         * (`GigsRepository.create`) — the same create path the V2 composer
         * and gigs feed use. On success the backend-issued id drives the
         * Posted event; on failure we flip to FatalError (the form is stashed
         * so retry restores it).
         */
        fun submit(now: LocalDateTime = LocalDateTime.now()) {
            val current = _state.value as? PostGigV1UiState.Content ?: return
            // P0.2 — never race a half-done upload; the Post CTA is also
            // disabled while uploads are in flight.
            if (!current.canAttemptSubmit) return
            val errors = validate(current.form, now)
            if (errors.isNotEmpty()) {
                _state.value = current.copy(validationErrors = errors)
                return
            }
            _state.value = current.copy(validationErrors = emptyList(), isSubmitting = true)
            viewModelScope.launch {
                when (val result = repo.create(buildCreateBody(current.form))) {
                    is NetworkResult.Success -> {
                        val gigId = result.data.gig.id
                        _state.value = current.copy(isSubmitting = false, postedGigId = gigId)
                        _pendingEvent.value = PostGigV1Event.Posted(gigId)
                    }
                    is NetworkResult.Failure -> {
                        lastForm = current.form
                        _state.value = PostGigV1UiState.FatalError(result.error.message)
                    }
                }
            }
        }

        /**
         * Map the V1 form onto the `POST /api/gigs` body. The legacy composer
         * collects a free-text location only, so it rides as the `custom`
         * location `address` with a `(0, 0)` placeholder coordinate. Pay-type
         * maps Flat→`fixed`, Hourly→`hourly`, Free→`offers`; the backend
         * rejects a non-positive price so Free uses a `1` sentinel.
         */
        private fun buildCreateBody(form: PostGigV1Form): CreateGigBody {
            val trimmedPrice = form.price.trim().toDoubleOrNull() ?: 0.0
            val payType: String
            val price: Double
            when (form.priceType) {
                PostGigV1PriceType.Flat -> {
                    payType = "fixed"
                    price = if (trimmedPrice > 0.0) trimmedPrice else 1.0
                }
                PostGigV1PriceType.Hourly -> {
                    payType = "hourly"
                    price = if (trimmedPrice > 0.0) trimmedPrice else 1.0
                }
                PostGigV1PriceType.Free -> {
                    payType = "offers"
                    price = 1.0
                }
            }
            return CreateGigBody(
                title = form.title.trim(),
                description = form.description.trim(),
                category = if (form.category == GigsCategory.All) null else form.category.key,
                price = price,
                payType = payType,
                scheduleType = "scheduled",
                scheduledStart = form.scheduledAt.atZone(ZoneId.systemDefault()).toInstant().toString(),
                taskFormat = null,
                // P0.2 — uploaded photo URLs ride as attachments.
                attachments = form.photos.mapNotNull { it.url }.ifEmpty { null },
                location =
                    CreateGigLocation(
                        mode = "custom",
                        latitude = 0.0,
                        longitude = 0.0,
                        address = form.location.trim(),
                    ),
            )
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

        private companion object {
            /** P0.2 — `file_type` form field on `POST /api/files/upload`. */
            const val GIG_PHOTO_FILE_TYPE = "gig_photo"
        }
    }

/**
 * P0.2 — raw bytes of a picked photo, held by the view-model for upload +
 * retry. Not a data class — [bytes] is an array, so structural equality
 * would be misleading.
 */
class PostGigV1PickedPhoto(
    val filename: String,
    val mimeType: String,
    val bytes: ByteArray,
)
