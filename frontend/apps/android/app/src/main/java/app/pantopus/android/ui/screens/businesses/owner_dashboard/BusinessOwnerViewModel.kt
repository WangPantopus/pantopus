@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.businesses.owner_dashboard

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

/** Nav-arg key for the owned business UUID (matches `ChildRoutes.BUSINESS_OWNER`). */
const val BUSINESS_OWNER_BUSINESS_ID_KEY = "businessId"

private const val OWNER_DASHBOARD_LOAD_DELAY_MILLIS = 50L

/**
 * A10.7 — view-model for the single-business owner dashboard. B3.2 is
 * sample-driven (no analytics or review-reply backend); the load path returns
 * the [BusinessOwnerSampleData] payload and the reply composer commits to
 * local state via [BusinessOwnerContent.applyingReply]. The shared public
 * render reused by "preview as neighbor" is the A10.6 sample (B3.1).
 *
 * Mirrors iOS `BusinessOwnerViewModel.swift`.
 */
@HiltViewModel
class BusinessOwnerViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        @Suppress("UnusedPrivateProperty")
        private val businessId: String =
            requireNotNull(savedStateHandle[BUSINESS_OWNER_BUSINESS_ID_KEY]) {
                "BusinessOwnerViewModel requires a '$BUSINESS_OWNER_BUSINESS_ID_KEY' nav arg."
            }

        private val _state = MutableStateFlow<BusinessOwnerUiState>(BusinessOwnerUiState.Loading)
        val state: StateFlow<BusinessOwnerUiState> = _state.asStateFlow()

        fun load() {
            if (_state.value is BusinessOwnerUiState.Loaded) return
            viewModelScope.launch {
                // A short delay exercises the shimmer skeleton in the running
                // app; tests seed content directly and skip this path.
                delay(OWNER_DASHBOARD_LOAD_DELAY_MILLIS)
                _state.value = BusinessOwnerUiState.Loaded(BusinessOwnerSampleData.marlow)
            }
        }

        fun refresh() {
            _state.value = BusinessOwnerUiState.Loading
            load()
        }

        /** Test / preview hook: seed the loaded state directly. */
        fun seedForPreview(content: BusinessOwnerContent) {
            _state.value = BusinessOwnerUiState.Loaded(content)
        }

        /** Commit a review reply to local state (no backend in B3.2). */
        fun submitReply(
            reviewId: String,
            text: String,
        ) {
            val current = _state.value as? BusinessOwnerUiState.Loaded ?: return
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return
            _state.value = BusinessOwnerUiState.Loaded(current.content.applyingReply(trimmed, reviewId))
        }
    }
