@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.review_claims

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.admin.AdminRepository
import app.pantopus.android.data.api.models.admin.AdminClaimDetailResponse
import app.pantopus.android.data.api.models.admin.AdminClaimReviewAction
import app.pantopus.android.data.api.models.admin.AdminClaimReviewRequest
import app.pantopus.android.data.api.net.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Tiny tone+text bundle the screen turns into a bottom-overlay toast. */
data class ReviewClaimToast(
    val text: String,
    val isError: Boolean,
)

/** Lifecycle state for the claim detail screen. */
sealed interface ReviewClaimDetailUiState {
    data object Loading : ReviewClaimDetailUiState

    data class Loaded(
        val detail: AdminClaimDetailResponse,
    ) : ReviewClaimDetailUiState

    data class Error(
        val message: String,
    ) : ReviewClaimDetailUiState
}

/**
 * P1.1 — Admin claim-detail view-model. Reads `claimId` from
 * [SavedStateHandle], loads the full claim payload (claimant + home +
 * evidence) and exposes [review] for the Approve / Reject /
 * Request-more-info actions.
 */
@HiltViewModel
class ReviewClaimDetailViewModel
    @Inject
    constructor(
        private val repo: AdminRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val claimId: String =
            savedStateHandle.get<String>(CLAIM_ID_KEY).orEmpty()

        private val _state = MutableStateFlow<ReviewClaimDetailUiState>(ReviewClaimDetailUiState.Loading)
        val state: StateFlow<ReviewClaimDetailUiState> = _state.asStateFlow()

        private val _reviewingAction = MutableStateFlow<AdminClaimReviewAction?>(null)
        val reviewingAction: StateFlow<AdminClaimReviewAction?> = _reviewingAction.asStateFlow()

        private val _toast = MutableStateFlow<ReviewClaimToast?>(null)
        val toast: StateFlow<ReviewClaimToast?> = _toast.asStateFlow()

        private var loadedOnce: Boolean = false

        fun load() {
            if (claimId.isBlank()) {
                _state.value = ReviewClaimDetailUiState.Error("Missing claim id.")
                return
            }
            // Refetch on every appear so the admin always sees fresh state;
            // only flip back to the loading shimmer the first time.
            if (!loadedOnce) _state.value = ReviewClaimDetailUiState.Loading
            viewModelScope.launch {
                when (val result = repo.claimDetail(claimId)) {
                    is NetworkResult.Success -> {
                        _state.value = ReviewClaimDetailUiState.Loaded(result.data)
                        loadedOnce = true
                    }
                    is NetworkResult.Failure -> {
                        _state.value =
                            ReviewClaimDetailUiState.Error("Couldn't load claim details. Try again.")
                    }
                }
            }
        }

        /**
         * Submit the reviewer decision. Returns `true` on success so the
         * host can dismiss its note sheet. Surfaces a toast either way.
         */
        suspend fun review(
            action: AdminClaimReviewAction,
            note: String? = null,
        ): Boolean {
            if (_reviewingAction.value != null) return false
            _reviewingAction.value = action
            return try {
                val request = AdminClaimReviewRequest(action = action.backendValue, note = note)
                when (val result = repo.reviewClaim(claimId, request)) {
                    is NetworkResult.Success -> {
                        _toast.update {
                            ReviewClaimToast(text = successCopy(action), isError = false)
                        }
                        // Refresh detail so the body shows the new state.
                        viewModelScope.launch { reload() }
                        true
                    }
                    is NetworkResult.Failure -> {
                        _toast.update {
                            ReviewClaimToast(text = "Couldn't review this claim. Try again.", isError = true)
                        }
                        false
                    }
                }
            } finally {
                _reviewingAction.value = null
            }
        }

        fun dismissToast() {
            _toast.value = null
        }

        private suspend fun reload() {
            when (val result = repo.claimDetail(claimId)) {
                is NetworkResult.Success -> {
                    _state.value = ReviewClaimDetailUiState.Loaded(result.data)
                }
                is NetworkResult.Failure -> Unit
            }
        }

        private fun successCopy(action: AdminClaimReviewAction): String =
            when (action) {
                AdminClaimReviewAction.Approve -> "Claim approved. User has been verified."
                AdminClaimReviewAction.Reject -> "Claim rejected. User has been notified."
                AdminClaimReviewAction.RequestMoreInfo -> "More info requested. User has been notified."
            }

        companion object {
            /** Nav-arg key matching `ChildRoutes.REVIEW_CLAIM_DETAIL_ID_KEY`. */
            const val CLAIM_ID_KEY = "claimId"
        }
    }
