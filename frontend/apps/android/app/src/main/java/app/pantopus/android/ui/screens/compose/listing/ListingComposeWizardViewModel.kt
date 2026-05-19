@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.compose.listing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.data.api.models.listings.CreateListingRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.listings.ListingsRepository
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

/** Aggregate UI state for the Snap & Sell wizard. */
data class ListingComposeUiState(
    val form: ListingComposeFormState = ListingComposeFormState.EMPTY,
    val isSubmitting: Boolean = false,
    val createdListingId: String? = null,
    val errorMessage: String? = null,
)

/**
 * Drives the six-step + success Snap & Sell wizard. Submits to
 * `POST /api/listings` (`backend/routes/listings.js:426`) via
 * [ListingsRepository], and exposes the [WizardChrome] for the shared
 * [app.pantopus.android.ui.screens.shared.wizard.WizardShell].
 *
 * Form state is mirrored into [SavedStateHandle] so the wizard
 * survives config changes and process death.
 */
@HiltViewModel
@Suppress("TooManyFunctions")
open class ListingComposeWizardViewModel
    @Inject
    constructor(
        private val repository: ListingsRepository,
        private val savedStateHandle: SavedStateHandle,
        private val networkMonitor: NetworkMonitor,
    ) : ViewModel(),
        WizardModel {
        private val _state =
            MutableStateFlow(restoreFormState().let { ListingComposeUiState(form = it) })

        /** Combined UI state consumed by [ListingComposeWizardScreen]. */
        val state: StateFlow<ListingComposeUiState> = _state.asStateFlow()

        /** One-shot navigation events the screen reacts to. */
        val pendingEvent = MutableStateFlow<ListingComposeOutboundEvent?>(null)

        // MARK: - WizardModel

        override val chrome: WizardChrome
            get() = computeChrome(_state.value)

        override fun onLeading() {
            val current = _state.value.form.currentStep
            when (leadingControl(current)) {
                WizardLeadingControl.Back -> goBack()
                WizardLeadingControl.Close -> pendingEvent.value = ListingComposeOutboundEvent.Dismiss
            }
        }

        override fun onDiscard() {
            pendingEvent.value = ListingComposeOutboundEvent.Dismiss
        }

        override fun onPrimary() {
            viewModelScope.launch { advance() }
        }

        override fun onSecondary() {
            // Success step's "Back to Marketplace".
            if (_state.value.form.currentStep == ListingComposeStep.Success) {
                pendingEvent.value = ListingComposeOutboundEvent.Dismiss
            }
        }

        fun acknowledgeEvent() {
            pendingEvent.value = null
        }

        // MARK: - Photo step

        /** Append a new photo to the grid. Captures up to [MAX_PHOTOS]. */
        fun addPhoto(token: String = "photo_${java.util.UUID.randomUUID().toString().take(6)}") {
            _state.update { current ->
                if (current.form.photos.size >= ListingComposeFormState.MAX_PHOTOS) return@update current
                current.copy(form = current.form.copy(photos = current.form.photos + ListingComposePhoto(token = token)))
            }
            persist()
        }

        /** Remove the photo with the given id. */
        fun removePhoto(id: String) {
            _state.update { current ->
                current.copy(form = current.form.copy(photos = current.form.photos.filterNot { it.id == id }))
            }
            persist()
        }

        /** Move photo `from -> to`. First slot is the hero. */
        fun movePhoto(
            from: Int,
            to: Int,
        ) {
            _state.update { current ->
                val photos = current.form.photos.toMutableList()
                if (from == to || from !in photos.indices || to < 0 || to > photos.size) return@update current
                val photo = photos.removeAt(from)
                val insertIndex = if (to > photos.size) photos.size else to
                photos.add(insertIndex, photo)
                current.copy(form = current.form.copy(photos = photos.toList()))
            }
            persist()
        }

        /** Promote a photo to the hero slot (index 0). */
        fun makeHero(id: String) {
            _state.update { current ->
                val photos = current.form.photos.toMutableList()
                val index = photos.indexOfFirst { it.id == id }
                if (index <= 0) return@update current
                val photo = photos.removeAt(index)
                photos.add(0, photo)
                current.copy(form = current.form.copy(photos = photos.toList()))
            }
            persist()
        }

        // MARK: - Other step mutations

        fun setTitle(value: String) {
            _state.update { it.copy(form = it.form.copy(title = value)) }
            persist()
        }

        fun setCategory(category: ListingComposeCategory) {
            _state.update { current ->
                var form = current.form.copy(category = category)
                // Category implies price kind for Free; clear stale state when switching out of Free.
                if (category == ListingComposeCategory.Free) {
                    form = form.copy(priceKind = ListingComposePriceKind.Free, priceAmount = "")
                } else if (form.priceKind == ListingComposePriceKind.Free) {
                    form = form.copy(priceKind = null)
                }
                if (!category.requiresCondition) {
                    form = form.copy(condition = null)
                }
                current.copy(form = form)
            }
            persist()
        }

        fun setCondition(condition: ListingComposeCondition) {
            _state.update { it.copy(form = it.form.copy(condition = condition)) }
            persist()
        }

        fun setBody(value: String) {
            _state.update { it.copy(form = it.form.copy(bodyText = value)) }
            persist()
        }

        fun setPriceKind(kind: ListingComposePriceKind) {
            _state.update { current ->
                val cleared = if (kind == ListingComposePriceKind.Free) "" else current.form.priceAmount
                current.copy(form = current.form.copy(priceKind = kind, priceAmount = cleared))
            }
            persist()
        }

        fun setPriceAmount(value: String) {
            val filtered = value.filter { it.isDigit() || it == '.' }
            val parts = filtered.split('.')
            // Reject input with more than one decimal separator.
            if (parts.size > 2) return
            _state.update { it.copy(form = it.form.copy(priceAmount = filtered)) }
            persist()
        }

        fun setFulfillment(value: ListingComposeFulfillment) {
            _state.update { it.copy(form = it.form.copy(fulfillment = value)) }
            persist()
        }

        fun setLocationKind(kind: ListingComposeLocationKind) {
            _state.update { it.copy(form = it.form.copy(locationKind = kind)) }
            persist()
        }

        fun setLocationLabel(value: String) {
            _state.update { it.copy(form = it.form.copy(locationLabel = value)) }
            persist()
        }

        // MARK: - State machine

        private suspend fun advance() {
            val current = _state.value.form.currentStep
            when (current) {
                ListingComposeStep.Photos -> transitionTo(ListingComposeStep.TitleCategory)
                ListingComposeStep.TitleCategory -> transitionTo(ListingComposeStep.ConditionDescription)
                ListingComposeStep.ConditionDescription -> transitionTo(ListingComposeStep.Price)
                ListingComposeStep.Price -> transitionTo(ListingComposeStep.Location)
                ListingComposeStep.Location -> transitionTo(ListingComposeStep.Review)
                ListingComposeStep.Review -> submit()
                ListingComposeStep.Success -> {
                    val listingId = _state.value.createdListingId ?: return
                    pendingEvent.value = ListingComposeOutboundEvent.OpenListingDetail(listingId)
                }
            }
        }

        private fun goBack() {
            val previous = ListingComposeStep.fromOrdinal(_state.value.form.step - 1)
            transitionTo(previous)
        }

        private fun transitionTo(step: ListingComposeStep) {
            _state.update {
                it.copy(form = it.form.copy(step = step.ordinal0), errorMessage = null)
            }
            persist()
            step.stepNumber?.let { number ->
                Analytics.track(
                    AnalyticsEvent.ScreenListingComposeWizardStepViewed(
                        stepNumber = number,
                        stepName = step.name,
                    ),
                )
            }
        }

        // MARK: - Submit

        private suspend fun submit() {
            val form = _state.value.form
            val category = form.category ?: return
            val priceKind = form.priceKind ?: return
            Analytics.track(AnalyticsEvent.CtaListingComposeSubmit)
            if (!networkMonitor.isOnline.value) {
                _state.update {
                    it.copy(
                        errorMessage = "You're offline. Try again when you're back online.",
                    )
                }
                return
            }
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }

            val isFree = priceKind == ListingComposePriceKind.Free || category == ListingComposeCategory.Free
            val price: Double? = if (isFree) null else form.priceAmount.toDoubleOrNull()

            val request =
                CreateListingRequest(
                    title = form.title.trim(),
                    description = form.bodyText.trim(),
                    price = price,
                    isFree = isFree,
                    category = category.key,
                    condition = form.condition?.key,
                    mediaUrls = form.photos.map { it.token },
                    layer = category.layer,
                    listingType = category.listingType,
                    locationName = form.locationLabel.takeIf { it.isNotEmpty() },
                    meetupPreference = form.fulfillment.meetupPreference,
                    deliveryAvailable = form.fulfillment == ListingComposeFulfillment.Delivery,
                    isWanted = category.isWanted,
                )

            when (val result = repository.create(request)) {
                is NetworkResult.Success -> {
                    _state.update {
                        it.copy(
                            createdListingId = result.data.listing.id,
                            isSubmitting = false,
                            form = it.form.copy(step = ListingComposeStep.Success.ordinal0),
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
                                    ?: "Couldn't list your item. Please try again.",
                        )
                    }
            }
        }

        // MARK: - Validation helpers

        /** True when the title trims to between min and max length. */
        fun isTitleValid(form: ListingComposeFormState): Boolean {
            val length = form.title.trim().length
            return length in ListingComposeFormState.TITLE_MIN_LENGTH..ListingComposeFormState.TITLE_MAX_LENGTH
        }

        /** True when the description trims to between min and max length. */
        fun isDescriptionValid(form: ListingComposeFormState): Boolean {
            val length = form.bodyText.trim().length
            return length in ListingComposeFormState.DESCRIPTION_MIN_LENGTH..
                ListingComposeFormState.DESCRIPTION_MAX_LENGTH
        }

        /** Condition is mandatory unless the category is Wanted. */
        fun conditionSatisfied(form: ListingComposeFormState): Boolean {
            val category = form.category ?: return false
            return !category.requiresCondition || form.condition != null
        }

        /** Free is always valid; Fixed/Negotiable need a positive amount. */
        fun isPriceValid(form: ListingComposeFormState): Boolean {
            val kind = form.priceKind ?: return false
            return when (kind) {
                ListingComposePriceKind.Free -> true
                ListingComposePriceKind.Fixed, ListingComposePriceKind.Negotiable ->
                    (form.priceAmount.toDoubleOrNull() ?: 0.0) > 0.0
            }
        }

        // MARK: - Persistence

        private fun persist() {
            val form = _state.value.form
            savedStateHandle[KEY_STEP] = form.step
            savedStateHandle[KEY_PHOTOS_IDS] = form.photos.map { it.id }
            savedStateHandle[KEY_PHOTOS_TOKENS] = form.photos.map { it.token }
            savedStateHandle[KEY_TITLE] = form.title
            savedStateHandle[KEY_CATEGORY] = form.category?.name
            savedStateHandle[KEY_CONDITION] = form.condition?.name
            savedStateHandle[KEY_BODY] = form.bodyText
            savedStateHandle[KEY_PRICE_KIND] = form.priceKind?.name
            savedStateHandle[KEY_PRICE_AMOUNT] = form.priceAmount
            savedStateHandle[KEY_FULFILLMENT] = form.fulfillment.name
            savedStateHandle[KEY_LOCATION_KIND] = form.locationKind?.name
            savedStateHandle[KEY_LOCATION_LABEL] = form.locationLabel
        }

        private fun restoreFormState(): ListingComposeFormState {
            val step: Int = savedStateHandle[KEY_STEP] ?: ListingComposeStep.Photos.ordinal0
            val ids: List<String> = savedStateHandle[KEY_PHOTOS_IDS] ?: emptyList()
            val tokens: List<String> = savedStateHandle[KEY_PHOTOS_TOKENS] ?: emptyList()
            val photos =
                ids.zip(tokens).map { (id, token) ->
                    ListingComposePhoto(id = id, token = token)
                }
            val title: String = savedStateHandle[KEY_TITLE] ?: ""
            val categoryName: String? = savedStateHandle[KEY_CATEGORY]
            val category = categoryName?.let { name -> ListingComposeCategory.entries.firstOrNull { it.name == name } }
            val conditionName: String? = savedStateHandle[KEY_CONDITION]
            val condition =
                conditionName?.let { name ->
                    ListingComposeCondition.entries.firstOrNull { it.name == name }
                }
            val body: String = savedStateHandle[KEY_BODY] ?: ""
            val priceKindName: String? = savedStateHandle[KEY_PRICE_KIND]
            val priceKind = priceKindName?.let { name -> ListingComposePriceKind.entries.firstOrNull { it.name == name } }
            val priceAmount: String = savedStateHandle[KEY_PRICE_AMOUNT] ?: ""
            val fulfillmentName: String? = savedStateHandle[KEY_FULFILLMENT]
            val fulfillment =
                fulfillmentName?.let { name ->
                    ListingComposeFulfillment.entries.firstOrNull { it.name == name }
                } ?: ListingComposeFulfillment.Pickup
            val locationKindName: String? = savedStateHandle[KEY_LOCATION_KIND]
            val locationKind =
                locationKindName?.let { name ->
                    ListingComposeLocationKind.entries.firstOrNull { it.name == name }
                }
            val locationLabel: String = savedStateHandle[KEY_LOCATION_LABEL] ?: ""
            return ListingComposeFormState(
                step = step,
                photos = photos,
                title = title,
                category = category,
                condition = condition,
                bodyText = body,
                priceKind = priceKind,
                priceAmount = priceAmount,
                fulfillment = fulfillment,
                locationKind = locationKind,
                locationLabel = locationLabel,
            )
        }

        // MARK: - Chrome derivation

        private fun computeChrome(state: ListingComposeUiState): WizardChrome {
            val step = state.form.currentStep
            return WizardChrome(
                title = "List an item",
                progressLabel = progressLabel(step),
                progressFraction = progressFraction(step),
                leading = leadingControl(step),
                primaryCtaLabel = primaryCtaLabel(step),
                primaryCtaEnabled = primaryEnabled(state) && !state.isSubmitting,
                secondaryCta = secondaryCta(step),
                isSubmitting = state.isSubmitting,
                dirty = dirtyForCloseConfirm(state),
                showsProgressBar = step != ListingComposeStep.Success,
            )
        }

        private fun leadingControl(step: ListingComposeStep): WizardLeadingControl =
            when (step) {
                ListingComposeStep.Photos, ListingComposeStep.Success -> WizardLeadingControl.Close
                else -> WizardLeadingControl.Back
            }

        private fun primaryCtaLabel(step: ListingComposeStep): String =
            when (step) {
                ListingComposeStep.Photos,
                ListingComposeStep.TitleCategory,
                ListingComposeStep.ConditionDescription,
                ListingComposeStep.Price,
                ListingComposeStep.Location,
                -> "Continue"
                ListingComposeStep.Review -> "List it"
                ListingComposeStep.Success -> "View listing"
            }

        private fun secondaryCta(step: ListingComposeStep): WizardSecondaryCta? =
            if (step == ListingComposeStep.Success) {
                WizardSecondaryCta(
                    label = "Back to Marketplace",
                    testTag = "listingComposeBackToMarketplace",
                )
            } else {
                null
            }

        private fun progressLabel(step: ListingComposeStep): WizardProgressLabel {
            val number = step.stepNumber ?: return WizardProgressLabel.Hidden
            return WizardProgressLabel.StepOf(
                current = number,
                total = ListingComposeStep.PROGRESS_TOTAL,
            )
        }

        private fun progressFraction(step: ListingComposeStep): Float? {
            val number = step.stepNumber ?: return null
            return number.toFloat() / ListingComposeStep.PROGRESS_TOTAL
        }

        private fun primaryEnabled(state: ListingComposeUiState): Boolean {
            val form = state.form
            return when (form.currentStep) {
                ListingComposeStep.Photos -> form.photos.isNotEmpty()
                ListingComposeStep.TitleCategory -> isTitleValid(form) && form.category != null
                ListingComposeStep.ConditionDescription -> isDescriptionValid(form) && conditionSatisfied(form)
                ListingComposeStep.Price -> isPriceValid(form)
                ListingComposeStep.Location -> form.locationKind != null
                ListingComposeStep.Review -> true
                ListingComposeStep.Success -> state.createdListingId != null
            }
        }

        private fun dirtyForCloseConfirm(state: ListingComposeUiState): Boolean =
            state.form.currentStep != ListingComposeStep.Success &&
                (
                    state.form.photos.isNotEmpty() ||
                        state.form.title.isNotEmpty() ||
                        state.form.bodyText.isNotEmpty()
                )

        companion object {
            private const val KEY_STEP = "listingCompose.step"
            private const val KEY_PHOTOS_IDS = "listingCompose.photoIds"
            private const val KEY_PHOTOS_TOKENS = "listingCompose.photoTokens"
            private const val KEY_TITLE = "listingCompose.title"
            private const val KEY_CATEGORY = "listingCompose.category"
            private const val KEY_CONDITION = "listingCompose.condition"
            private const val KEY_BODY = "listingCompose.body"
            private const val KEY_PRICE_KIND = "listingCompose.priceKind"
            private const val KEY_PRICE_AMOUNT = "listingCompose.priceAmount"
            private const val KEY_FULFILLMENT = "listingCompose.fulfillment"
            private const val KEY_LOCATION_KIND = "listingCompose.locationKind"
            private const val KEY_LOCATION_LABEL = "listingCompose.locationLabel"
        }
    }
