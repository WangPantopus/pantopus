@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.business_profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.businesses.BusinessHoursDto
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.businesses.BusinessesRepository
import app.pantopus.android.data.profile.ProfileRepository
import app.pantopus.android.ui.screens.contentdetail.GigOpenChatEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/** Nav-arg key for the business UUID. */
const val BUSINESS_PROFILE_BUSINESS_ID_KEY = "businessId"

/** View-model for the single-scroll Business Profile screen (A10.6). The
 *  projection lives in [BusinessProfileMapper] so the owner dashboard can
 *  reuse it verbatim. */
@HiltViewModel
class BusinessProfileViewModel
    @Inject
    constructor(
        private val businesses: BusinessesRepository,
        private val profiles: ProfileRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val businessId: String =
            requireNotNull(savedStateHandle[BUSINESS_PROFILE_BUSINESS_ID_KEY]) {
                "BusinessProfileViewModel requires a '$BUSINESS_PROFILE_BUSINESS_ID_KEY' nav arg."
            }

        private val _state = MutableStateFlow<BusinessProfileUiState>(BusinessProfileUiState.Loading)
        val state: StateFlow<BusinessProfileUiState> = _state.asStateFlow()

        private val _saveState = MutableStateFlow<BusinessProfileSaveState>(BusinessProfileSaveState.Idle)
        val saveState: StateFlow<BusinessProfileSaveState> = _saveState.asStateFlow()

        private val _toastMessage = MutableStateFlow<String?>(null)
        val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

        private val _showOverflow = MutableStateFlow(false)
        val showOverflow: StateFlow<Boolean> = _showOverflow.asStateFlow()

        private val _openChatEvents = MutableSharedFlow<GigOpenChatEvent>(extraBufferCapacity = 1)
        val openChatEvents: SharedFlow<GigOpenChatEvent> = _openChatEvents.asSharedFlow()

        private var isStartingInquiry = false

        fun load() {
            if (_state.value is BusinessProfileUiState.Loaded) return
            refresh()
        }

        fun refresh() {
            _state.value = BusinessProfileUiState.Loading
            viewModelScope.launch { fetch() }
        }

        fun dismissToast() {
            _toastMessage.value = null
        }

        fun setShowOverflow(show: Boolean) {
            _showOverflow.value = show
        }

        fun save() {
            if (_saveState.value is BusinessProfileSaveState.InFlight) return
            if (_saveState.value is BusinessProfileSaveState.Saved) return
            _saveState.value = BusinessProfileSaveState.InFlight
            viewModelScope.launch {
                when (val result = businesses.followBusiness(businessId)) {
                    is NetworkResult.Success -> {
                        _saveState.value = BusinessProfileSaveState.Saved
                        _toastMessage.value = if (result.data.following) "Saved" else "Updated"
                    }
                    is NetworkResult.Failure -> {
                        val message = friendlyMessage(result.error)
                        _saveState.value = BusinessProfileSaveState.Failed(message)
                        _toastMessage.value = message
                    }
                }
            }
        }

        /**
         * Contact dock — `POST /api/businesses/:id/inbox/start`, then emit a
         * one-shot chat open event. Mirrors RN/web `startBusinessInquiry`.
         */
        fun startInquiry() {
            val loaded = _state.value as? BusinessProfileUiState.Loaded ?: return
            if (isStartingInquiry) return
            isStartingInquiry = true
            val name = loaded.content.header.displayName
            val handle = loaded.content.header.handle
            val subject =
                if (!handle.isNullOrEmpty()) {
                    "Inquiry for @${handle.trimStart('@')}"
                } else {
                    "Inquiry for $name"
                }
            viewModelScope.launch {
                when (val result = businesses.startInquiry(businessId, subject)) {
                    is NetworkResult.Success -> {
                        _openChatEvents.emit(
                            GigOpenChatEvent(
                                roomId = result.data.roomId,
                                displayName = name,
                                initials = initialsFromName(name),
                                verified = loaded.content.header.isVerified,
                            ),
                        )
                    }
                    is NetworkResult.Failure -> {
                        _toastMessage.value = friendlyMessage(result.error)
                    }
                }
                isStartingInquiry = false
            }
        }

        private suspend fun fetch() {
            when (val detail = businesses.business(businessId)) {
                is NetworkResult.Success -> {
                    val payload = detail.data
                    coroutineScope {
                        val publicDeferred =
                            async {
                                payload.business.username
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { username ->
                                        (businesses.publicBusiness(username) as? NetworkResult.Success)?.data
                                    }
                            }
                        val reviewsDeferred =
                            async {
                                (profiles.publicProfile(businessId) as? NetworkResult.Success)?.data
                            }
                        val publicResponse = publicDeferred.await()
                        val reviewsResponse = reviewsDeferred.await()
                        _state.value =
                            BusinessProfileUiState.Loaded(
                                BusinessProfileMapper.build(payload, publicResponse, reviewsResponse),
                            )
                    }
                }
                is NetworkResult.Failure -> {
                    when (detail.error) {
                        NetworkError.NotFound -> _state.value = BusinessProfileUiState.NotFound
                        else -> _state.value = BusinessProfileUiState.Error(friendlyMessage(detail.error))
                    }
                }
            }
        }

        /** Thin wrapper over [BusinessProfileMapper.computeOpenState] retained
         *  for the unit-test surface. */
        fun computeOpenState(
            rows: List<BusinessHoursDto>,
            now: LocalDateTime,
        ): BusinessOpenState? = BusinessProfileMapper.computeOpenState(rows, now)

        private fun friendlyMessage(error: NetworkError): String =
            when (error) {
                NetworkError.NotFound -> "We couldn't find this business."
                NetworkError.Forbidden -> "This business profile is private."
                is NetworkError.Transport -> "Check your connection and try again."
                else -> "Something went wrong. Try again."
            }

        private fun initialsFromName(name: String): String {
            val joined =
                name
                    .split(" ")
                    .take(2)
                    .mapNotNull { it.firstOrNull()?.toString() }
                    .joinToString("")
                    .uppercase()
            return joined.ifEmpty { "··" }
        }
    }
