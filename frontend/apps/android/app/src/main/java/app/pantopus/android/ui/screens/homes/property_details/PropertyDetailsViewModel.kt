@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.property_details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

const val PROPERTY_DETAILS_HOME_ID_KEY = "homeId"

/** ViewModel for the read-mostly Property Details screen. */
@HiltViewModel
class PropertyDetailsViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val homeId: String =
            requireNotNull(savedStateHandle[PROPERTY_DETAILS_HOME_ID_KEY]) {
                "PropertyDetailsViewModel requires a '$PROPERTY_DETAILS_HOME_ID_KEY' nav arg."
            }

        private var loader: (String) -> PropertyDetailsContent = PropertyDetailsSampleData::contentFor
        private val _state = MutableStateFlow<PropertyDetailsUiState>(PropertyDetailsUiState.Loading)

        /** Observed state. */
        val state: StateFlow<PropertyDetailsUiState> = _state.asStateFlow()

        internal constructor(
            savedStateHandle: SavedStateHandle,
            loader: (String) -> PropertyDetailsContent,
        ) : this(savedStateHandle) {
            this.loader = loader
        }

        /** Initial load; no-op after content resolves. */
        fun load() {
            if (_state.value !is PropertyDetailsUiState.Loading) return
            apply()
        }

        /** Retry after an error. */
        fun refresh() {
            apply()
        }

        private fun apply() {
            _state.value =
                try {
                    val content = loader(homeId)
                    if (content.banner == null) {
                        PropertyDetailsUiState.Clean(content)
                    } else {
                        PropertyDetailsUiState.Mismatch(content)
                    }
                } catch (_: Exception) {
                    PropertyDetailsUiState.Error("Couldn't load property details. Pull to retry.")
                }
        }
    }
