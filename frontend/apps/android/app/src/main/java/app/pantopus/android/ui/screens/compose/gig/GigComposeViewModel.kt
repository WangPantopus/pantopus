@file:Suppress("PackageNaming", "TooManyFunctions", "LargeClass")

package app.pantopus.android.ui.screens.compose.gig

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.data.api.models.gigs.CreateGigBody
import app.pantopus.android.data.api.models.gigs.CreateGigLocation
import app.pantopus.android.data.api.models.gigs.MagicDraftDto
import app.pantopus.android.data.api.models.gigs.MagicDraftRequest
import app.pantopus.android.data.api.models.gigs.MagicDraftResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.files.FilesRepository
import app.pantopus.android.data.gigs.GigsRepository
import app.pantopus.android.data.network.NetworkMonitor
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID
import javax.inject.Inject

/**
 * Aggregate UI state for the Post-a-Task wizard. Combined into a single
 * [StateFlow] so the screen can derive [WizardChrome] off of it without
 * reading several separate flows.
 */
data class GigComposeUiState(
    val form: GigComposeFormState = GigComposeFormState.EMPTY,
    val isSubmitting: Boolean = false,
    val createdGigId: String? = null,
    val errorMessage: String? = null,
    /**
     * E.1 — the composer picker sheet currently presented over the wizard,
     * or null. Transient UI state — never persisted to [SavedStateHandle]
     * (a half-open sheet shouldn't survive process death).
     */
    val activeSheet: GigPickerSheet? = null,
    /** P0.1 — true while the magic-draft parse call is in flight. */
    val isParsingDraft: Boolean = false,
    /** P0.1 — backend follow-up question shown under the describe field. */
    val clarifyingQuestion: String? = null,
    /** P0.2 — in-flight / failed photo uploads (uploaded URLs live in [form.photoIds]). */
    val photoUploads: List<GigComposePhotoUpload> = emptyList(),
)

/**
 * Drives the 6-step + success Post-a-Task wizard. Posts to
 * `POST /api/gigs` via [GigsRepository] and exposes [WizardChrome] for
 * the shared [app.pantopus.android.ui.screens.shared.wizard.WizardShell].
 *
 * Form state is mirrored into [SavedStateHandle] so the wizard survives
 * config changes and process death.
 */
@HiltViewModel
open class GigComposeViewModel
    @Inject
    constructor(
        private val repository: GigsRepository,
        private val savedStateHandle: SavedStateHandle,
        private val networkMonitor: NetworkMonitor,
        private val filesRepository: FilesRepository,
    ) : ViewModel(),
        WizardModel {
        private val _state =
            MutableStateFlow(GigComposeUiState(form = restoreFormState()))

        /** Combined UI state consumed by [GigComposeWizardScreen]. */
        val state: StateFlow<GigComposeUiState> = _state.asStateFlow()

        /** One-shot navigation events the screen reacts to. */
        val pendingEvent = MutableStateFlow<GigComposeOutboundEvent?>(null)

        /**
         * B.3 / P0.1 — in-flight debounce + magic-draft call for the Magic
         * Task parse. Cancelled (which also aborts the HTTP call) whenever
         * new describe input arrives.
         */
        private var detectionJob: Job? = null

        /**
         * P0.1 — field keys the user has manually edited. The magic-draft
         * prefill skips these so a backend parse never stomps explicit
         * input. Persisted alongside the form so restore keeps the rule.
         */
        private val touchedFields: MutableSet<String> =
            (savedStateHandle.get<ArrayList<String>>(KEY_TOUCHED) ?: arrayListOf()).toMutableSet()

        /** P0.2 — picked-photo bytes held for upload + tap-to-retry. */
        private val pendingPhotoBytes = mutableMapOf<String, GigComposePickedPhoto>()

        /** P0.2 — per-tile upload jobs so remove can cancel in flight. */
        private val uploadJobs = mutableMapOf<String, Job>()

        // MARK: - WizardModel

        override val chrome: WizardChrome
            get() = computeChrome(_state.value)

        override fun onLeading() {
            val current = _state.value.form.currentStep
            when (leadingControl(current)) {
                WizardLeadingControl.Back -> goBack()
                WizardLeadingControl.Close -> pendingEvent.value = GigComposeOutboundEvent.Dismiss
            }
        }

        override fun onDiscard() {
            pendingEvent.value = GigComposeOutboundEvent.Dismiss
        }

        override fun onPrimary() {
            viewModelScope.launch { advance() }
        }

        override fun onSecondary() {
            val form = _state.value.form
            when {
                form.currentStep == GigComposeStep.Success ->
                    pendingEvent.value = GigComposeOutboundEvent.Dismiss
                form.currentStep == GigComposeStep.Category && form.composeMode == ComposeMode.Magic ->
                    setComposeMode(ComposeMode.Manual)
            }
        }

        fun acknowledgeEvent() {
            pendingEvent.value = null
        }

        /**
         * Seed the form's category once on entry (e.g. when the user
         * arrives from the Gigs feed with `handyman` selected). No-op
         * if a category is already set or the form was restored from
         * SavedStateHandle.
         */
        fun preselectCategoryIfNeeded(category: GigComposeCategory?) {
            if (category == null) return
            val current = _state.value.form
            if (current.category != null || current.hasAnyData) return
            // A preselected category means the user already chose one, so
            // land on the manual picker (tile pre-selected) rather than Magic.
            _state.update { it.copy(form = it.form.copy(composeMode = ComposeMode.Manual, category = category)) }
            persist()
        }

        // MARK: - B.3 Magic Task

        /** Switch the step-1 entry mode (Magic describe ⇄ manual picker). */
        fun setComposeMode(mode: ComposeMode) {
            _state.update { it.copy(form = it.form.copy(composeMode = mode)) }
            persist()
        }

        /**
         * Update the plain-English describe text and (re)schedule a
         * debounced parse. P0.1 — the parse is the real backend
         * `POST /api/gigs/magic-draft` call; the [detectArchetype] keyword
         * matcher remains the offline / short-input fallback. Cancelling
         * [detectionJob] also cancels any magic-draft call still in flight.
         */
        fun setDescribeText(text: String) {
            val clamped = text.take(GigComposeLimits.DESCRIBE_MAX)
            _state.update { it.copy(form = it.form.copy(describeText = clamped)) }
            persist()
            detectionJob?.cancel()
            detectionJob =
                viewModelScope.launch {
                    delay(DETECTION_DEBOUNCE_MS)
                    parseDescribe(clamped)
                }
        }

        /**
         * P0.1 — parse the describe text. Input of ≥ [MIN_DRAFT_WORDS]
         * words goes to the backend magic-draft endpoint; shorter input —
         * and any network failure — falls back to the deterministic
         * keyword matcher so the step still works offline.
         */
        suspend fun parseDescribe(text: String) {
            if (_state.value.form.describeText != text) return
            if (wordCount(text) < MIN_DRAFT_WORDS) {
                // Too short for the backend — drop any stale follow-up
                // question and fall back to the keyword matcher.
                _state.update { it.copy(clarifyingQuestion = null) }
                applyDetection(text)
                return
            }
            _state.update { it.copy(isParsingDraft = true) }
            val request =
                MagicDraftRequest(
                    text = text.trim(),
                    attachmentUrls = _state.value.form.photoIds.ifEmpty { null },
                )
            val result = repository.magicDraft(request)
            if (_state.value.form.describeText != text) {
                // Stale — newer input arrived while the call was in flight.
                _state.update { it.copy(isParsingDraft = false) }
                return
            }
            when (result) {
                is NetworkResult.Success -> applyMagicDraft(result.data)
                is NetworkResult.Failure -> {
                    _state.update { it.copy(isParsingDraft = false, clarifyingQuestion = null) }
                    applyDetection(text)
                }
            }
        }

        /**
         * Apply the keyword-matched archetype if the text hasn't changed
         * since the debounce fired. Mirrors the detected category into
         * [form.category]. Kept as the magic-draft fallback (offline etc.).
         */
        fun applyDetection(text: String) {
            if (_state.value.form.describeText != text) return
            val detected = detectArchetype(text)
            _state.update {
                it.copy(
                    form =
                        it.form.copy(
                            detectedArchetype = detected,
                            category = detected ?: it.form.category,
                        ),
                )
            }
            persist()
        }

        /**
         * P0.1 — map a magic-draft response onto the form. **Decision:**
         * the draft is applied the moment it arrives (not on step advance),
         * but only into fields the user hasn't manually edited
         * ([touchedFields]) — this keeps the prefill live while the user
         * types, matches how the keyword matcher already mirrored the
         * category, and never overwrites explicit input.
         */
        private fun applyMagicDraft(response: MagicDraftResponse) {
            val draft = response.draft
            _state.update { state ->
                state.copy(
                    isParsingDraft = false,
                    clarifyingQuestion = response.clarifyingQuestion?.takeIf { it.isNotBlank() },
                    form = prefillFormFromDraft(state.form, draft),
                )
            }
            persist()
        }

        private fun prefillFormFromDraft(
            form: GigComposeFormState,
            draft: MagicDraftDto,
        ): GigComposeFormState {
            val category =
                GigComposeCategory.fromRawKey(draft.category)
                    ?: GigComposeCategory.fromRawKey(draft.taskArchetype)
                    ?: detectArchetype(form.describeText)
            val budgetType =
                draft.payType?.let { pay -> GigComposeBudgetType.entries.firstOrNull { it.wireValue == pay } }
            val (draftMin, draftMax) = draftBudgetBounds(draft)
            val draftTags =
                draft.tags
                    .orEmpty()
                    .mapNotNull { normalizeTag(it) }
                    .distinct()
                    .take(GigComposeLimits.MAX_TAGS)
                    .ifEmpty { null }
            return form.copy(
                detectedArchetype = category ?: form.detectedArchetype,
                category = prefill(FIELD_CATEGORY, category, form.category),
                title = prefill(FIELD_TITLE, draft.title?.take(GigComposeLimits.TITLE_MAX), form.title),
                description =
                    prefill(
                        FIELD_DESCRIPTION,
                        draft.description?.take(GigComposeLimits.DESCRIPTION_MAX),
                        form.description,
                    ),
                budgetType = prefill(FIELD_BUDGET, budgetType, form.budgetType),
                budgetMin = prefill(FIELD_BUDGET, draftMin, form.budgetMin),
                budgetMax = prefill(FIELD_BUDGET, draftMax, form.budgetMax),
                scheduleType = prefill(FIELD_SCHEDULE, scheduleTypeFromDraft(draft.scheduleType), form.scheduleType),
                tags = prefill(FIELD_TAGS, draftTags, form.tags),
                isUrgent = prefill(FIELD_URGENT, draft.isUrgent, form.isUrgent),
            )
        }

        /** Draft value wins only when present and the field is untouched. */
        private fun <T> prefill(
            field: String,
            draftValue: T?,
            current: T,
        ): T = if (draftValue != null && field !in touchedFields) draftValue else current

        private fun markTouched(field: String) {
            touchedFields.add(field)
            savedStateHandle[KEY_TOUCHED] = ArrayList(touchedFields)
        }

        // MARK: - Field updates

        fun selectCategory(category: GigComposeCategory) {
            markTouched(FIELD_CATEGORY)
            _state.update { it.copy(form = it.form.copy(category = category)) }
            persist()
        }

        fun selectEngagementMode(mode: GigComposeEngagementMode) {
            // Deliberate user choice that prefills schedule + budget — both
            // count as touched so the magic draft won't override them.
            markTouched(FIELD_SCHEDULE)
            markTouched(FIELD_BUDGET)
            _state.update {
                val form = it.form
                val next =
                    when (mode) {
                        GigComposeEngagementMode.OneTime ->
                            form.copy(
                                scheduleType = GigComposeScheduleType.OneTime,
                                budgetType = form.budgetType.takeUnless { type -> type == GigComposeBudgetType.Offers },
                            )
                        GigComposeEngagementMode.Recurring ->
                            form.copy(
                                scheduleType = GigComposeScheduleType.Recurring,
                                budgetType = form.budgetType.takeUnless { type -> type == GigComposeBudgetType.Offers },
                            )
                        GigComposeEngagementMode.OpenBidding ->
                            form.copy(budgetType = GigComposeBudgetType.Offers)
                    }
                it.copy(form = next)
            }
            persist()
        }

        fun setTitle(title: String) {
            markTouched(FIELD_TITLE)
            val clamped = title.take(GigComposeLimits.TITLE_MAX)
            _state.update { it.copy(form = it.form.copy(title = clamped)) }
            persist()
        }

        fun setDescription(description: String) {
            markTouched(FIELD_DESCRIPTION)
            val clamped = description.take(GigComposeLimits.DESCRIPTION_MAX)
            _state.update { it.copy(form = it.form.copy(description = clamped)) }
            persist()
        }

        // MARK: - P0.2 Photo upload pipeline

        /**
         * P0.2 — accept a picked attachment and upload it immediately via
         * `POST /api/files/upload`. The tile is tracked as uploading /
         * failed (tap-to-retry) until the URL lands in [form.photoIds].
         */
        fun addPickedPhoto(photo: GigComposePickedPhoto) {
            val current = _state.value
            if (current.form.photoIds.size + current.photoUploads.size >= GigComposeLimits.MAX_PHOTOS) return
            val id = UUID.randomUUID().toString()
            pendingPhotoBytes[id] = photo
            _state.update { it.copy(photoUploads = it.photoUploads + GigComposePhotoUpload(id = id)) }
            startUpload(id)
        }

        /** P0.2 — retry a failed upload tile (bytes are still held). */
        fun retryPhotoUpload(id: String) {
            if (pendingPhotoBytes[id] == null) return
            _state.update {
                it.copy(photoUploads = it.photoUploads.map { tile -> if (tile.id == id) tile.copy(failed = false) else tile })
            }
            startUpload(id)
        }

        /** P0.2 — drop an uploading / failed tile, cancelling its call. */
        fun removePhotoUpload(id: String) {
            uploadJobs.remove(id)?.cancel()
            pendingPhotoBytes.remove(id)
            _state.update { it.copy(photoUploads = it.photoUploads.filterNot { tile -> tile.id == id }) }
        }

        private fun startUpload(id: String) {
            val photo = pendingPhotoBytes[id] ?: return
            uploadJobs[id] =
                viewModelScope.launch {
                    val result =
                        filesRepository.uploadFile(
                            filename = photo.filename,
                            mimeType = photo.mimeType,
                            bytes = photo.bytes,
                            fileType = GIG_PHOTO_FILE_TYPE,
                            visibility = "public",
                        )
                    when (result) {
                        is NetworkResult.Success -> {
                            pendingPhotoBytes.remove(id)
                            uploadJobs.remove(id)
                            _state.update {
                                it.copy(
                                    form = it.form.copy(photoIds = it.form.photoIds + result.data.file.url),
                                    photoUploads = it.photoUploads.filterNot { tile -> tile.id == id },
                                )
                            }
                            persist()
                        }
                        is NetworkResult.Failure ->
                            _state.update {
                                it.copy(
                                    photoUploads =
                                        it.photoUploads.map { tile ->
                                            if (tile.id == id) tile.copy(failed = true) else tile
                                        },
                                )
                            }
                    }
                }
        }

        /** Remove an already-uploaded photo URL by its slot index. */
        fun removePhoto(index: Int) {
            val current = _state.value.form.photoIds
            if (index !in current.indices) return
            _state.update {
                it.copy(
                    form = it.form.copy(photoIds = current.toMutableList().also { list -> list.removeAt(index) }),
                )
            }
            persist()
        }

        fun selectBudgetType(type: GigComposeBudgetType) {
            markTouched(FIELD_BUDGET)
            _state.update { it.copy(form = it.form.copy(budgetType = type)) }
            persist()
        }

        fun setBudgetMin(value: String) {
            markTouched(FIELD_BUDGET)
            _state.update { it.copy(form = it.form.copy(budgetMin = sanitizeBudget(value))) }
            persist()
        }

        fun setBudgetMax(value: String) {
            markTouched(FIELD_BUDGET)
            _state.update { it.copy(form = it.form.copy(budgetMax = sanitizeBudget(value))) }
            persist()
        }

        fun selectScheduleType(type: GigComposeScheduleType) {
            markTouched(FIELD_SCHEDULE)
            _state.update {
                it.copy(
                    form =
                        it.form.copy(
                            scheduleType = type,
                            // Drop the date when leaving "one-time" so it can't bleed past.
                            scheduledStartISO = if (type == GigComposeScheduleType.OneTime) it.form.scheduledStartISO else null,
                        ),
                )
            }
            persist()
        }

        fun setScheduledStart(iso: String?) {
            markTouched(FIELD_SCHEDULE)
            _state.update { it.copy(form = it.form.copy(scheduledStartISO = iso)) }
            persist()
        }

        fun selectLocationMode(mode: GigComposeLocationMode) {
            _state.update { it.copy(form = it.form.copy(locationMode = mode)) }
            persist()
        }

        fun updatePlaceAddress(
            line1: String? = null,
            city: String? = null,
            state: String? = null,
            zip: String? = null,
        ) {
            _state.update {
                val current = it.form.placeAddress
                it.copy(
                    form =
                        it.form.copy(
                            placeAddress =
                                current.copy(
                                    line1 = line1 ?: current.line1,
                                    city = city ?: current.city,
                                    state = state ?: current.state,
                                    zip = zip ?: current.zip,
                                ),
                        ),
                )
            }
            persist()
        }

        // MARK: - E.1 Composer picker sheets

        /** Present one of the composer's bottom-sheet pickers. */
        fun presentPicker(sheet: GigPickerSheet) {
            _state.update { it.copy(activeSheet = sheet) }
        }

        /** Dismiss whichever picker sheet is open. */
        fun dismissPicker() {
            _state.update { it.copy(activeSheet = null) }
        }

        /** E.1 — set (or clear) the optional deadline. null ⇒ flexible. */
        fun setDeadline(iso: String?) {
            _state.update { it.copy(form = it.form.copy(deadlineISO = iso)) }
            persist()
        }

        /** E.1 — choose the cancellation-policy tier. */
        fun setCancellationPolicy(policy: GigCancellationPolicy) {
            _state.update { it.copy(form = it.form.copy(cancellationPolicy = policy)) }
            persist()
        }

        /** E.1 — toggle the urgent boost flag. */
        fun setUrgent(isUrgent: Boolean) {
            markTouched(FIELD_URGENT)
            _state.update { it.copy(form = it.form.copy(isUrgent = isUrgent)) }
            persist()
        }

        /** E.1 — add a tag if there's room ([GigComposeLimits.MAX_TAGS]). */
        fun addTag(raw: String) {
            val tag = normalizeTag(raw) ?: return
            markTouched(FIELD_TAGS)
            val current = _state.value.form.tags
            if (current.size >= GigComposeLimits.MAX_TAGS || current.contains(tag)) return
            _state.update { it.copy(form = it.form.copy(tags = current + tag)) }
            persist()
        }

        /** E.1 — remove a tag by its normalised value. */
        fun removeTag(tag: String) {
            markTouched(FIELD_TAGS)
            _state.update {
                it.copy(form = it.form.copy(tags = it.form.tags.filterNot { existing -> existing == tag }))
            }
            persist()
        }

        /** E.1 — add a suggested tag if absent, remove it if already chosen. */
        fun toggleTag(raw: String) {
            val tag = normalizeTag(raw) ?: return
            if (_state.value.form.tags.contains(tag)) removeTag(tag) else addTag(tag)
        }

        // MARK: - State machine

        private suspend fun advance() {
            when (_state.value.form.currentStep) {
                GigComposeStep.Category,
                GigComposeStep.Basics,
                GigComposeStep.Budget,
                GigComposeStep.Schedule,
                GigComposeStep.Location,
                -> {
                    val next = GigComposeStep.fromOrdinal(_state.value.form.step + 1)
                    transitionTo(next)
                }
                GigComposeStep.Review -> submit()
                GigComposeStep.Success -> {
                    val gigId = _state.value.createdGigId ?: return
                    pendingEvent.value = GigComposeOutboundEvent.OpenGigDetail(gigId)
                }
            }
        }

        private fun goBack() {
            val previous = GigComposeStep.fromOrdinal(_state.value.form.step - 1)
            transitionTo(previous)
        }

        private fun transitionTo(step: GigComposeStep) {
            _state.update {
                it.copy(form = it.form.copy(step = step.ordinal0), errorMessage = null)
            }
            persist()
            step.stepNumber?.let { number ->
                Analytics.track(
                    AnalyticsEvent.ScreenComposeGigWizardStepViewed(
                        stepNumber = number,
                        stepName = step.name,
                    ),
                )
            }
        }

        // MARK: - API

        private suspend fun submit() {
            Analytics.track(AnalyticsEvent.CtaComposeGigSubmit)
            if (!networkMonitor.isOnline.value) {
                _state.update { it.copy(errorMessage = "You're offline. Try again when you're back online.") }
                return
            }
            val body =
                buildCreateBody() ?: run {
                    _state.update { it.copy(errorMessage = "Please complete each step before posting.") }
                    return
                }
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = repository.create(body)) {
                is NetworkResult.Success -> {
                    _state.update {
                        it.copy(
                            createdGigId = result.data.gig.id,
                            isSubmitting = false,
                            form = it.form.copy(step = GigComposeStep.Success.ordinal0),
                        )
                    }
                    persist()
                }
                is NetworkResult.Failure ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = result.error.message ?: "Couldn't post your task. Please try again.",
                        )
                    }
            }
        }

        /**
         * Assemble a [CreateGigBody] from the form. Returns null if any
         * required field is missing — [primaryEnabled] should have caught
         * it but we double-check before sending.
         */
        fun buildCreateBody(): CreateGigBody? {
            val form = _state.value.form
            val fields = createGigRequiredFields(form) ?: return null
            return CreateGigBody(
                title = fields.title,
                description = fields.description,
                category = form.category?.key,
                // Backend requires positive number; we send 1 for
                // open-to-bids so the schema accepts it and treat
                // `pay_type` as the source of truth.
                price = if (fields.price > 0) fields.price else 1.0,
                payType = fields.budgetType.wireValue,
                scheduleType = fields.scheduleType.wireValue,
                scheduledStart = fields.scheduledStart,
                taskFormat = fields.taskFormat,
                attachments = form.photoIds.ifEmpty { null },
                // E.1 — composer picker-sheet fields. `is_urgent` only rides
                // along when the boost is on; the rest are omitted when unset.
                deadline = form.deadlineISO,
                cancellationPolicy = form.cancellationPolicy?.wireValue,
                isUrgent = if (form.isUrgent) true else null,
                tags = form.tags.ifEmpty { null },
                location = fields.location,
            )
        }

        private data class CreateGigRequiredFields(
            val budgetType: GigComposeBudgetType,
            val scheduleType: GigComposeScheduleType,
            val title: String,
            val description: String,
            val price: Double,
            val scheduledStart: String?,
            val taskFormat: String?,
            val location: CreateGigLocation,
        )

        private data class CreateGigRequiredOptions(
            val budgetType: GigComposeBudgetType,
            val scheduleType: GigComposeScheduleType,
            val locationMode: GigComposeLocationMode,
        )

        private fun createGigRequiredFields(form: GigComposeFormState): CreateGigRequiredFields? {
            val options = requiredCreateOptions(form) ?: return null
            val title = form.title.trim()
            val description = form.description.trim()
            val price = priceFromBudget(options.budgetType, form.budgetMin)
            val scheduledStart = if (options.scheduleType == GigComposeScheduleType.OneTime) form.scheduledStartISO else null
            val location = composedLocation(options.locationMode, form.placeAddress) ?: return null
            return CreateGigRequiredFields(
                budgetType = options.budgetType,
                scheduleType = options.scheduleType,
                title = title,
                description = description,
                price = price,
                scheduledStart = scheduledStart,
                taskFormat = taskFormatFor(options.locationMode),
                location = location,
            ).takeIf {
                hasValidTitleAndDescription(title, description) &&
                    hasValidPrice(options.budgetType, price) &&
                    hasValidScheduledStart(options.scheduleType, scheduledStart)
            }
        }

        private fun requiredCreateOptions(form: GigComposeFormState): CreateGigRequiredOptions? {
            val budgetType = form.budgetType ?: return null
            val scheduleType = form.scheduleType ?: return null
            val locationMode = form.locationMode ?: return null
            return CreateGigRequiredOptions(
                budgetType = budgetType,
                scheduleType = scheduleType,
                locationMode = locationMode,
            )
        }

        private fun composedLocation(
            mode: GigComposeLocationMode,
            place: GigComposePlaceAddress,
        ): CreateGigLocation? =
            when (mode) {
                GigComposeLocationMode.YourAddress ->
                    CreateGigLocation(
                        mode = mode.wireMode,
                        latitude = 0.0,
                        longitude = 0.0,
                        address = "Your saved address",
                    )
                GigComposeLocationMode.APlace -> {
                    if (!place.isComplete) {
                        null
                    } else {
                        CreateGigLocation(
                            mode = mode.wireMode,
                            latitude = 0.0,
                            longitude = 0.0,
                            address = place.line1.trim(),
                            city = place.city.trim(),
                            state = place.state.trim(),
                            zip = place.zip.trim(),
                        )
                    }
                }
                GigComposeLocationMode.Virtual ->
                    CreateGigLocation(
                        mode = mode.wireMode,
                        latitude = 0.0,
                        longitude = 0.0,
                        address = "Remote / Online",
                    )
            }

        // MARK: - Chrome derivation

        private fun computeChrome(state: GigComposeUiState): WizardChrome {
            val step = state.form.currentStep
            return WizardChrome(
                title = "Post a task",
                progressLabel = progressLabel(step),
                progressFraction = progressFraction(step),
                leading = leadingControl(step),
                primaryCtaLabel = primaryCtaLabel(step),
                primaryCtaEnabled = primaryEnabled(state) && !state.isSubmitting,
                secondaryCta = secondaryCta(state),
                isSubmitting = state.isSubmitting,
                dirty = step != GigComposeStep.Success && state.form.hasAnyData,
                showsProgressBar = step != GigComposeStep.Success,
            )
        }

        private fun leadingControl(step: GigComposeStep): WizardLeadingControl =
            when (step) {
                GigComposeStep.Category, GigComposeStep.Success -> WizardLeadingControl.Close
                GigComposeStep.Basics,
                GigComposeStep.Budget,
                GigComposeStep.Schedule,
                GigComposeStep.Location,
                GigComposeStep.Review,
                -> WizardLeadingControl.Back
            }

        private fun primaryCtaLabel(step: GigComposeStep): String =
            when (step) {
                GigComposeStep.Category,
                GigComposeStep.Basics,
                GigComposeStep.Budget,
                GigComposeStep.Schedule,
                GigComposeStep.Location,
                -> "Continue"
                GigComposeStep.Review -> "Post task"
                GigComposeStep.Success -> "View task"
            }

        private fun secondaryCta(state: GigComposeUiState): WizardSecondaryCta? {
            val form = state.form
            return when {
                form.currentStep == GigComposeStep.Success ->
                    WizardSecondaryCta(label = "Done", testTag = "composeGigDone")
                form.currentStep == GigComposeStep.Category && form.composeMode == ComposeMode.Magic ->
                    // Ghost link beside the primary CTA → manual picker.
                    WizardSecondaryCta(label = "Pick category", testTag = "composeGigPickCategory")
                else -> null
            }
        }

        private fun progressLabel(step: GigComposeStep): WizardProgressLabel {
            val number = step.stepNumber ?: return WizardProgressLabel.Hidden
            return WizardProgressLabel.StepOf(current = number, total = GigComposeStep.PROGRESS_TOTAL)
        }

        private fun progressFraction(step: GigComposeStep): Float? {
            val number = step.stepNumber ?: return null
            return number.toFloat() / GigComposeStep.PROGRESS_TOTAL
        }

        private fun primaryEnabled(state: GigComposeUiState): Boolean {
            val form = state.form
            return when (form.currentStep) {
                GigComposeStep.Category ->
                    // Magic: enabled once an archetype is detected. Manual:
                    // enabled once a category tile is selected.
                    if (form.composeMode == ComposeMode.Magic) form.detectedArchetype != null else form.category != null
                // P0.2 — photo uploads in flight gate Continue / Post so a
                // submit never races a half-done upload.
                GigComposeStep.Basics -> hasValidBasics(state)
                GigComposeStep.Budget -> hasValidBudget(form)
                GigComposeStep.Schedule -> hasValidSchedule(form)
                GigComposeStep.Location -> hasValidLocation(form)
                GigComposeStep.Review -> buildCreateBody() != null && !hasUploadsInFlight(state)
                GigComposeStep.Success -> state.createdGigId != null
            }
        }

        private fun hasUploadsInFlight(state: GigComposeUiState): Boolean = state.photoUploads.any { !it.failed }

        private fun hasValidBasics(state: GigComposeUiState): Boolean =
            hasValidTitleAndDescription(state.form.title.trim(), state.form.description.trim()) &&
                state.form.photoIds.size + state.photoUploads.size <= GigComposeLimits.MAX_PHOTOS &&
                !hasUploadsInFlight(state)

        private fun hasValidTitleAndDescription(
            title: String,
            description: String,
        ): Boolean =
            title.length in GigComposeLimits.TITLE_MIN..GigComposeLimits.TITLE_MAX &&
                description.length in GigComposeLimits.DESCRIPTION_MIN..GigComposeLimits.DESCRIPTION_MAX

        private fun hasValidBudget(form: GigComposeFormState): Boolean =
            form.budgetType?.let { type ->
                hasValidPrice(type, priceFromBudget(type, form.budgetMin))
            } ?: false

        private fun hasValidPrice(
            type: GigComposeBudgetType,
            price: Double,
        ): Boolean = type == GigComposeBudgetType.Offers || price > 0.0

        private fun hasValidSchedule(form: GigComposeFormState): Boolean =
            when (form.scheduleType) {
                null -> false
                GigComposeScheduleType.OneTime -> isFutureInstant(form.scheduledStartISO)
                GigComposeScheduleType.Recurring, GigComposeScheduleType.Flexible -> true
            }

        private fun hasValidScheduledStart(
            type: GigComposeScheduleType,
            scheduledStart: String?,
        ): Boolean = type != GigComposeScheduleType.OneTime || isFutureInstant(scheduledStart)

        private fun isFutureInstant(iso: String?): Boolean =
            iso?.let { value ->
                try {
                    Instant.parse(value).isAfter(Instant.now())
                } catch (_: DateTimeParseException) {
                    false
                }
            } ?: false

        private fun hasValidLocation(form: GigComposeFormState): Boolean =
            when (form.locationMode) {
                null -> false
                GigComposeLocationMode.YourAddress, GigComposeLocationMode.Virtual -> true
                GigComposeLocationMode.APlace -> form.placeAddress.isComplete
            }

        private fun taskFormatFor(mode: GigComposeLocationMode): String? =
            if (mode == GigComposeLocationMode.Virtual) {
                "remote"
            } else {
                null
            }

        // MARK: - Persistence

        private fun persist() {
            val form = _state.value.form
            savedStateHandle[KEY_STEP] = form.step
            savedStateHandle[KEY_COMPOSE_MODE] = form.composeMode.name
            savedStateHandle[KEY_DESCRIBE] = form.describeText
            savedStateHandle[KEY_DETECTED] = form.detectedArchetype?.name
            savedStateHandle[KEY_CATEGORY] = form.category?.name
            savedStateHandle[KEY_TITLE] = form.title
            savedStateHandle[KEY_DESCRIPTION] = form.description
            savedStateHandle[KEY_PHOTOS] = ArrayList(form.photoIds)
            savedStateHandle[KEY_BUDGET_TYPE] = form.budgetType?.name
            savedStateHandle[KEY_BUDGET_MIN] = form.budgetMin
            savedStateHandle[KEY_BUDGET_MAX] = form.budgetMax
            savedStateHandle[KEY_SCHEDULE_TYPE] = form.scheduleType?.name
            savedStateHandle[KEY_SCHEDULED_START] = form.scheduledStartISO
            savedStateHandle[KEY_LOCATION_MODE] = form.locationMode?.name
            savedStateHandle[KEY_PLACE_LINE1] = form.placeAddress.line1
            savedStateHandle[KEY_PLACE_CITY] = form.placeAddress.city
            savedStateHandle[KEY_PLACE_STATE] = form.placeAddress.state
            savedStateHandle[KEY_PLACE_ZIP] = form.placeAddress.zip
            savedStateHandle[KEY_DEADLINE] = form.deadlineISO
            savedStateHandle[KEY_CANCELLATION] = form.cancellationPolicy?.name
            savedStateHandle[KEY_IS_URGENT] = form.isUrgent
            savedStateHandle[KEY_TAGS] = ArrayList(form.tags)
        }

        @Suppress("CyclomaticComplexMethod")
        private fun restoreFormState(): GigComposeFormState {
            val step: Int = savedStateHandle[KEY_STEP] ?: GigComposeStep.Category.ordinal0
            val composeModeName: String? = savedStateHandle[KEY_COMPOSE_MODE]
            val detectedName: String? = savedStateHandle[KEY_DETECTED]
            val categoryName: String? = savedStateHandle[KEY_CATEGORY]
            val budgetTypeName: String? = savedStateHandle[KEY_BUDGET_TYPE]
            val scheduleTypeName: String? = savedStateHandle[KEY_SCHEDULE_TYPE]
            val locationModeName: String? = savedStateHandle[KEY_LOCATION_MODE]
            val cancellationName: String? = savedStateHandle[KEY_CANCELLATION]
            val photos: ArrayList<String> = savedStateHandle[KEY_PHOTOS] ?: arrayListOf()
            val tags: ArrayList<String> = savedStateHandle[KEY_TAGS] ?: arrayListOf()
            return GigComposeFormState(
                step = step,
                composeMode = composeModeName?.let { name -> ComposeMode.entries.firstOrNull { it.name == name } } ?: ComposeMode.Magic,
                describeText = savedStateHandle[KEY_DESCRIBE] ?: "",
                detectedArchetype = detectedName?.let { name -> GigComposeCategory.entries.firstOrNull { it.name == name } },
                category = categoryName?.let { name -> GigComposeCategory.entries.firstOrNull { it.name == name } },
                title = savedStateHandle[KEY_TITLE] ?: "",
                description = savedStateHandle[KEY_DESCRIPTION] ?: "",
                photoIds = photos.toList(),
                budgetType = budgetTypeName?.let { name -> GigComposeBudgetType.entries.firstOrNull { it.name == name } },
                budgetMin = savedStateHandle[KEY_BUDGET_MIN] ?: "",
                budgetMax = savedStateHandle[KEY_BUDGET_MAX] ?: "",
                scheduleType = scheduleTypeName?.let { name -> GigComposeScheduleType.entries.firstOrNull { it.name == name } },
                scheduledStartISO = savedStateHandle[KEY_SCHEDULED_START],
                locationMode = locationModeName?.let { name -> GigComposeLocationMode.entries.firstOrNull { it.name == name } },
                placeAddress =
                    GigComposePlaceAddress(
                        line1 = savedStateHandle[KEY_PLACE_LINE1] ?: "",
                        city = savedStateHandle[KEY_PLACE_CITY] ?: "",
                        state = savedStateHandle[KEY_PLACE_STATE] ?: "",
                        zip = savedStateHandle[KEY_PLACE_ZIP] ?: "",
                    ),
                deadlineISO = savedStateHandle[KEY_DEADLINE],
                cancellationPolicy =
                    cancellationName?.let { name ->
                        GigCancellationPolicy.entries.firstOrNull { it.name == name }
                    },
                isUrgent = savedStateHandle[KEY_IS_URGENT] ?: false,
                tags = tags.toList(),
            )
        }

        companion object {
            private const val KEY_STEP = "composeGig.step"
            private const val KEY_COMPOSE_MODE = "composeGig.composeMode"
            private const val KEY_DESCRIBE = "composeGig.describeText"
            private const val KEY_DETECTED = "composeGig.detectedArchetype"
            private const val KEY_CATEGORY = "composeGig.category"
            private const val KEY_TITLE = "composeGig.title"
            private const val KEY_DESCRIPTION = "composeGig.description"
            private const val KEY_PHOTOS = "composeGig.photos"
            private const val KEY_BUDGET_TYPE = "composeGig.budgetType"
            private const val KEY_BUDGET_MIN = "composeGig.budgetMin"
            private const val KEY_BUDGET_MAX = "composeGig.budgetMax"
            private const val KEY_SCHEDULE_TYPE = "composeGig.scheduleType"
            private const val KEY_SCHEDULED_START = "composeGig.scheduledStart"
            private const val KEY_LOCATION_MODE = "composeGig.locationMode"
            private const val KEY_PLACE_LINE1 = "composeGig.placeLine1"
            private const val KEY_PLACE_CITY = "composeGig.placeCity"
            private const val KEY_PLACE_STATE = "composeGig.placeState"
            private const val KEY_PLACE_ZIP = "composeGig.placeZip"
            private const val KEY_DEADLINE = "composeGig.deadline"
            private const val KEY_CANCELLATION = "composeGig.cancellationPolicy"
            private const val KEY_IS_URGENT = "composeGig.isUrgent"
            private const val KEY_TAGS = "composeGig.tags"
            private const val KEY_TOUCHED = "composeGig.touchedFields"

            /** P0.1 — debounce ahead of the backend magic-draft call. */
            private const val DETECTION_DEBOUNCE_MS = 700L

            /** P0.1 — minimum word count before the backend parse fires. */
            private const val MIN_DRAFT_WORDS = 3
            private const val MIN_DETECT_TEXT_LENGTH = 3
            private const val MAX_TAG_LENGTH = 50

            /** P0.2 — `file_type` form field on `POST /api/files/upload`. */
            private const val GIG_PHOTO_FILE_TYPE = "gig_photo"

            // P0.1 — touched-field keys for the magic-draft prefill guard.
            private const val FIELD_CATEGORY = "category"
            private const val FIELD_TITLE = "title"
            private const val FIELD_DESCRIPTION = "description"
            private const val FIELD_BUDGET = "budget"
            private const val FIELD_SCHEDULE = "schedule"
            private const val FIELD_TAGS = "tags"
            private const val FIELD_URGENT = "urgent"

            private fun wordCount(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

            /** "60.0" reads as "60" in the budget fields; keep cents when present. */
            internal fun formatBudgetValue(value: Double?): String? =
                value?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }

            /** P0.1 — draft budget → (min, max) field strings. */
            internal fun draftBudgetBounds(draft: MagicDraftDto): Pair<String?, String?> {
                val range = draft.budgetRange
                return when {
                    range?.min != null || range?.max != null ->
                        formatBudgetValue(range?.min) to formatBudgetValue(range?.max)
                    draft.budgetFixed != null -> formatBudgetValue(draft.budgetFixed) to null
                    draft.hourlyRate != null -> formatBudgetValue(draft.hourlyRate) to null
                    else -> null to null
                }
            }

            /** P0.1 — tolerant backend `schedule_type` → composer enum. */
            internal fun scheduleTypeFromDraft(raw: String?): GigComposeScheduleType? =
                when ((raw ?: "").lowercase().replace("_", "").replace("-", "")) {
                    "scheduled", "onetime", "once" -> GigComposeScheduleType.OneTime
                    "recurring", "repeat", "repeating" -> GigComposeScheduleType.Recurring
                    "flexible", "flex", "anytime" -> GigComposeScheduleType.Flexible
                    else -> null
                }

            /** B.3 — deterministic keyword → archetype map (stand-in for backend NLP). */
            fun detectArchetype(text: String): GigComposeCategory? {
                val lower = text.lowercase()
                if (lower.length < MIN_DETECT_TEXT_LENGTH) return null

                fun has(words: List<String>) = words.any { lower.contains(it) }

                return when {
                    has(listOf("move", "moving", "haul", "u-haul", "load boxes")) -> GigComposeCategory.Moving
                    has(listOf("clean", "tidy", "scrub", "vacuum", "mop")) -> GigComposeCategory.Cleaning
                    has(
                        listOf(
                            "assemble", "ikea", "furniture", "shelf", "shelves", "mount", "drill",
                            "fix", "repair", "install", "handy", "patch", "drywall",
                        ),
                    ) -> GigComposeCategory.Handyman
                    has(listOf("dog", "cat", " pet", "puppy", "litter", "groom", "walk")) -> GigComposeCategory.PetCare
                    has(listOf("babysit", "nanny", "kids", "child", "daycare")) -> GigComposeCategory.ChildCare
                    has(listOf("tutor", "lesson", "math", "homework", "test prep", "teach")) -> GigComposeCategory.Tutoring
                    has(listOf("deliver", "pickup", "pick up", "drop off", "errand", "courier")) -> GigComposeCategory.Delivery
                    has(
                        listOf("wifi", "wi-fi", "computer", "laptop", "printer", "router", "troubleshoot", "setup"),
                    ) -> GigComposeCategory.Tech
                    else -> null
                }
            }

            /**
             * E.1 — normalise a freeform tag for storage: trimmed,
             * lowercased, without a leading `#`, whitespace collapsed to
             * single hyphens, capped at 50 chars. Returns null for empty
             * input.
             */
            internal fun normalizeTag(raw: String): String? {
                val withoutHash = raw.trim().lowercase().removePrefix("#")
                val hyphenated =
                    withoutHash
                        .split(Regex("\\s+"))
                        .filter { it.isNotEmpty() }
                        .joinToString("-")
                return hyphenated.take(MAX_TAG_LENGTH).ifEmpty { null }
            }

            /** Strip everything except digits + a single decimal point. */
            internal fun sanitizeBudget(raw: String): String {
                var seenDot = false
                val out = StringBuilder()
                for (c in raw) {
                    if (c.isDigit()) {
                        out.append(c)
                    } else if (c == '.' && !seenDot) {
                        out.append(c)
                        seenDot = true
                    }
                }
                return out.toString()
            }

            private fun priceFromBudget(
                type: GigComposeBudgetType,
                budgetMin: String,
            ): Double =
                when (type) {
                    GigComposeBudgetType.Offers -> 0.0
                    GigComposeBudgetType.Fixed, GigComposeBudgetType.Hourly -> budgetMin.toDoubleOrNull() ?: 0.0
                }
        }
    }
