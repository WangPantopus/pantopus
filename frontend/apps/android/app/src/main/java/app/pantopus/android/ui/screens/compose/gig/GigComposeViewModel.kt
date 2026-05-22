@file:Suppress("PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.compose.gig

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.data.api.models.gigs.CreateGigBody
import app.pantopus.android.data.api.models.gigs.CreateGigLocation
import app.pantopus.android.data.api.net.NetworkResult
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
    ) : ViewModel(),
        WizardModel {
        private val _state =
            MutableStateFlow(GigComposeUiState(form = restoreFormState()))

        /** Combined UI state consumed by [GigComposeWizardScreen]. */
        val state: StateFlow<GigComposeUiState> = _state.asStateFlow()

        /** One-shot navigation events the screen reacts to. */
        val pendingEvent = MutableStateFlow<GigComposeOutboundEvent?>(null)

        /** B.3 — in-flight debounce for the Magic Task archetype parse. */
        private var detectionJob: Job? = null

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
         * debounced archetype parse. Real backend NLP is deferred;
         * [detectArchetype] is a deterministic keyword match standing in.
         */
        fun setDescribeText(text: String) {
            val clamped = text.take(GigComposeLimits.DESCRIBE_MAX)
            _state.update { it.copy(form = it.form.copy(describeText = clamped)) }
            persist()
            detectionJob?.cancel()
            detectionJob =
                viewModelScope.launch {
                    delay(350)
                    applyDetection(clamped)
                }
        }

        /**
         * Apply the parsed archetype if the text hasn't changed since the
         * debounce fired. Mirrors the detected category into [form.category].
         */
        fun applyDetection(text: String) {
            if (_state.value.form.describeText != text) return
            val detected = detectArchetype(text)
            _state.update {
                it.copy(
                    form = it.form.copy(
                        detectedArchetype = detected,
                        category = detected ?: it.form.category,
                    ),
                )
            }
            persist()
        }

        // MARK: - Field updates

        fun selectCategory(category: GigComposeCategory) {
            _state.update { it.copy(form = it.form.copy(category = category)) }
            persist()
        }

        fun setTitle(title: String) {
            val clamped = title.take(GigComposeLimits.TITLE_MAX)
            _state.update { it.copy(form = it.form.copy(title = clamped)) }
            persist()
        }

        fun setDescription(description: String) {
            val clamped = description.take(GigComposeLimits.DESCRIPTION_MAX)
            _state.update { it.copy(form = it.form.copy(description = clamped)) }
            persist()
        }

        /**
         * Append a placeholder photo id. Today the upload pipeline isn't
         * wired (lands with P15.5); the wizard treats the id as opaque
         * so the underlying mechanism can swap later.
         */
        fun addPlaceholderPhoto() {
            val current = _state.value.form.photoIds
            if (current.size >= GigComposeLimits.MAX_PHOTOS) return
            _state.update {
                it.copy(form = it.form.copy(photoIds = current + "placeholder://photo/${UUID.randomUUID()}"))
            }
            persist()
        }

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
            _state.update { it.copy(form = it.form.copy(budgetType = type)) }
            persist()
        }

        fun setBudgetMin(value: String) {
            _state.update { it.copy(form = it.form.copy(budgetMin = sanitizeBudget(value))) }
            persist()
        }

        fun setBudgetMax(value: String) {
            _state.update { it.copy(form = it.form.copy(budgetMax = sanitizeBudget(value))) }
            persist()
        }

        fun selectScheduleType(type: GigComposeScheduleType) {
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
                GigComposeStep.Basics -> hasValidBasics(form)
                GigComposeStep.Budget -> hasValidBudget(form)
                GigComposeStep.Schedule -> hasValidSchedule(form)
                GigComposeStep.Location -> hasValidLocation(form)
                GigComposeStep.Review -> buildCreateBody() != null
                GigComposeStep.Success -> state.createdGigId != null
            }
        }

        private fun hasValidBasics(form: GigComposeFormState): Boolean =
            hasValidTitleAndDescription(form.title.trim(), form.description.trim()) &&
                form.photoIds.size <= GigComposeLimits.MAX_PHOTOS

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
        }

        private fun restoreFormState(): GigComposeFormState {
            val step: Int = savedStateHandle[KEY_STEP] ?: GigComposeStep.Category.ordinal0
            val composeModeName: String? = savedStateHandle[KEY_COMPOSE_MODE]
            val detectedName: String? = savedStateHandle[KEY_DETECTED]
            val categoryName: String? = savedStateHandle[KEY_CATEGORY]
            val budgetTypeName: String? = savedStateHandle[KEY_BUDGET_TYPE]
            val scheduleTypeName: String? = savedStateHandle[KEY_SCHEDULE_TYPE]
            val locationModeName: String? = savedStateHandle[KEY_LOCATION_MODE]
            val photos: ArrayList<String> = savedStateHandle[KEY_PHOTOS] ?: arrayListOf()
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

            /** B.3 — deterministic keyword → archetype map (stand-in for backend NLP). */
            fun detectArchetype(text: String): GigComposeCategory? {
                val lower = text.lowercase()
                if (lower.length < 3) return null
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
