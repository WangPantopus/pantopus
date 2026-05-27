@file:Suppress("PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.businesses.page_editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav-arg key for the business UUID. */
const val EDIT_BUSINESS_PAGE_BUSINESS_ID_KEY = "businessId"

/**
 * P4.2 — A13.10 Edit Business Page. Owns per-field draft state + dirty
 * tracking. Backend endpoints (load draft / save / publish) are stubbed
 * until the `business-page-editor` API lands; the load path returns one
 * of the two [EditBusinessPageSampleData] payloads so the screen can be
 * exercised end-to-end in previews + Paparazzi snapshots.
 */
@HiltViewModel
class EditBusinessPageViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        @Suppress("UnusedPrivateProperty")
        private val businessId: String =
            requireNotNull(savedStateHandle[EDIT_BUSINESS_PAGE_BUSINESS_ID_KEY]) {
                "EditBusinessPageViewModel requires a '$EDIT_BUSINESS_PAGE_BUSINESS_ID_KEY' nav arg."
            }

        private val _state = MutableStateFlow<EditBusinessPageUiState>(EditBusinessPageUiState.Loading)
        val state: StateFlow<EditBusinessPageUiState> = _state.asStateFlow()

        private val _toast = MutableStateFlow<String?>(null)
        val toast: StateFlow<String?> = _toast.asStateFlow()

        private val _showsDiscardConfirm = MutableStateFlow(false)
        val showsDiscardConfirm: StateFlow<Boolean> = _showsDiscardConfirm.asStateFlow()

        fun load() {
            if (_state.value is EditBusinessPageUiState.Loaded) return
            viewModelScope.launch {
                delay(50)
                _state.value = EditBusinessPageUiState.Loaded(EditBusinessPageSampleData.publishedRoostCafe)
            }
        }

        fun refresh() {
            _state.value = EditBusinessPageUiState.Loading
            load()
        }

        /** Test-only: seed the loaded state directly. */
        fun seedForPreview(content: EditBusinessPageContent) {
            _state.value = EditBusinessPageUiState.Loaded(content)
        }

        fun save() {
            val current = _state.value as? EditBusinessPageUiState.Loaded ?: return
            _state.value = EditBusinessPageUiState.Loaded(promoteCurrentToOriginal(current.content))
            _toast.value = "Saved"
        }

        fun saveDraft() {
            val current = _state.value as? EditBusinessPageUiState.Loaded ?: return
            _state.value = EditBusinessPageUiState.Loaded(promoteCurrentToOriginal(current.content))
            _toast.value = "Draft saved"
        }

        fun publish() {
            _toast.value = "Published"
        }

        fun discardRequested() {
            _showsDiscardConfirm.value = true
        }

        fun cancelDiscard() {
            _showsDiscardConfirm.value = false
        }

        fun discardConfirmed() {
            val current = _state.value as? EditBusinessPageUiState.Loaded ?: return
            _state.value = EditBusinessPageUiState.Loaded(revertToOriginal(current.content))
            _showsDiscardConfirm.value = false
            _toast.value = "Edits discarded"
        }

        fun dismissToast() {
            _toast.value = null
        }

        // MARK: - Helpers

        private fun promoteCurrentToOriginal(content: EditBusinessPageContent): EditBusinessPageContent =
            content.copy(
                mode = zeroUnsaved(content.mode),
                banner = content.banner.cleaned(),
                name = content.name.cleaned(),
                tagline = content.tagline.cleaned(),
                category = content.category.cleaned(),
                price = content.price.cleaned(),
                description = content.description.cleaned(),
                hours = content.hours.cleaned(),
                services = content.services.cleaned(),
                gallery = content.gallery.cleaned(),
                phone = content.phone.cleaned(),
                email = content.email.cleaned(),
                website = content.website.cleaned(),
                bookingLink = content.bookingLink?.cleaned(),
                location = content.location.cleaned(),
            )

        private fun revertToOriginal(content: EditBusinessPageContent): EditBusinessPageContent =
            content.copy(
                mode = zeroUnsaved(content.mode),
                banner = content.banner.cleaned(),
                name = content.name.reverted(),
                tagline = content.tagline.reverted(),
                category = content.category.reverted(),
                price = content.price.reverted(),
                description = content.description.reverted(),
                hours = content.hours.cleaned(),
                services = content.services.cleaned(),
                gallery = content.gallery.cleaned(),
                phone = content.phone.reverted(),
                email = content.email.reverted(),
                website = content.website.reverted(),
                bookingLink = content.bookingLink?.reverted(),
                location = content.location.reverted(),
            )

        private fun zeroUnsaved(mode: EditBusinessPageMode): EditBusinessPageMode =
            when (mode) {
                is EditBusinessPageMode.Published -> mode.copy(unsavedCount = 0)
                is EditBusinessPageMode.Setup -> mode
            }
    }

// MARK: - Local cleanup helpers

private fun EditBusinessPageField.cleaned(): EditBusinessPageField =
    EditBusinessPageField(original = current, current = current, placeholder = placeholder)

private fun EditBusinessPageField.reverted(): EditBusinessPageField =
    EditBusinessPageField(original = original, current = original, placeholder = placeholder)

private fun EditBusinessPageBannerState.cleaned(): EditBusinessPageBannerState =
    when (this) {
        EditBusinessPageBannerState.Empty -> this
        is EditBusinessPageBannerState.Filled -> copy(dirty = false)
    }

private fun EditBusinessPageDescriptionState.cleaned(): EditBusinessPageDescriptionState =
    when (this) {
        is EditBusinessPageDescriptionState.Field -> copy(field = field.cleaned())
        is EditBusinessPageDescriptionState.Prompt -> this
    }

private fun EditBusinessPageDescriptionState.reverted(): EditBusinessPageDescriptionState =
    when (this) {
        is EditBusinessPageDescriptionState.Field -> copy(field = field.reverted())
        is EditBusinessPageDescriptionState.Prompt -> this
    }

private fun EditBusinessPageHoursState.cleaned(): EditBusinessPageHoursState =
    when (this) {
        is EditBusinessPageHoursState.Rows -> copy(rows = rows.map { it.copy(isDirty = false) })
        is EditBusinessPageHoursState.QuickApply -> this
    }

private fun EditBusinessPageServicesState.cleaned(): EditBusinessPageServicesState =
    when (this) {
        is EditBusinessPageServicesState.Chips -> copy(chips = chips.map { it.copy(isFresh = false) })
        is EditBusinessPageServicesState.Prompt -> this
    }

private fun EditBusinessPageGalleryState.cleaned(): EditBusinessPageGalleryState = copy(freshAddTile = false)

private fun EditBusinessPageLocation.cleaned(): EditBusinessPageLocation =
    copy(address = address.cleaned(), error = null, pinDirty = false)

private fun EditBusinessPageLocation.reverted(): EditBusinessPageLocation =
    copy(address = address.reverted(), pinDirty = false)
