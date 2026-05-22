@file:Suppress("MagicNumber")

package app.pantopus.android.ui.screens.homes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.homes.HomeDetail
import app.pantopus.android.data.api.models.homes.HomePublicProfile
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.ui.screens.shared.content_detail.GridTabsTab
import app.pantopus.android.ui.screens.shared.content_detail.HomeHeroStat
import app.pantopus.android.ui.screens.shared.content_detail.QuickActionTile
import app.pantopus.android.ui.screens.shared.content_detail.QuickActionTone
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Key used to read the home id from the nav backstack's SavedStateHandle. */
const val HOME_DASHBOARD_HOME_ID_KEY = "homeId"

/** Projection shown by [HomeDashboardScreen]. */
data class HomeDashboardContent(
    val address: String,
    /**
     * True when the home has any verified owner; drives the header
     * "Verified" badge and the summary status row. Distinct from
     * [isVerifiedOwner] because the home can have a verified owner who
     * isn't the signed-in user.
     */
    val verified: Boolean,
    /**
     * True when the signed-in user is the verified owner of this home.
     * Drives the claim-ownership banner gate: shown when this is false
     * regardless of whether anyone else is a verified owner.
     */
    val isVerifiedOwner: Boolean,
    val stats: List<HomeHeroStat>,
    val quickActions: List<QuickActionTile>,
    val tabs: List<GridTabsTab>,
    val overview: HomeDashboardOverviewContent,
    val attentionSummary: HomeDashboardAttentionSummary? = null,
)

data class HomeDashboardOverviewContent(
    val upcoming: List<HomeDashboardTimelineItem>,
    val activity: List<HomeDashboardActivityItem>,
    val emergency: HomeDashboardEmergencyInfo,
)

data class HomeDashboardTimelineItem(
    val id: String,
    val icon: PantopusIcon,
    val tone: QuickActionTone,
    val title: String,
    val subtitle: String,
    val trailing: String?,
)

data class HomeDashboardActivityItem(
    val id: String,
    val initials: String,
    val tone: QuickActionTone,
    val title: String,
    val detail: String,
    val time: String,
)

data class HomeDashboardEmergencyInfo(
    val title: String,
    val body: String,
    val isConfigured: Boolean,
)

data class HomeDashboardAttentionSummary(
    val message: String,
    val chips: List<HomeDashboardQuickJump>,
)

data class HomeDashboardQuickJump(
    val id: String,
    val label: String,
    val icon: PantopusIcon,
    val actionId: String,
)

data class HomeDashboardBrandNewContent(
    val content: HomeDashboardContent,
    val onboardingSteps: List<HomeDashboardOnboardingStep>,
)

data class HomeDashboardOnboardingStep(
    val id: String,
    val title: String,
    val body: String,
    val cta: String,
    val icon: PantopusIcon,
    val tone: QuickActionTone,
    val actionId: String,
)

/** Observed state for the Home Dashboard. */
sealed interface HomeDashboardUiState {
    data object Loading : HomeDashboardUiState

    data class Loaded(
        val content: HomeDashboardContent,
    ) : HomeDashboardUiState

    data class Empty(
        val brandNew: HomeDashboardBrandNewContent,
    ) : HomeDashboardUiState

    data class NeedsAttention(
        val content: HomeDashboardContent,
    ) : HomeDashboardUiState

    data class Error(
        val message: String,
    ) : HomeDashboardUiState
}

/**
 * ViewModel for the Home Dashboard screen. Receives the home id via the
 * nav-backstack [SavedStateHandle].
 */
@HiltViewModel
class HomeDashboardViewModel
    @Inject
    constructor(
        private val repo: HomesRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val homeId: String =
            requireNotNull(savedStateHandle[HOME_DASHBOARD_HOME_ID_KEY]) {
                "HomeDashboardViewModel requires a '$HOME_DASHBOARD_HOME_ID_KEY' nav arg."
            }

        private val _state = MutableStateFlow<HomeDashboardUiState>(HomeDashboardUiState.Loading)

        /** Observed state. */
        val state: StateFlow<HomeDashboardUiState> = _state.asStateFlow()

        private val _selectedTab = MutableStateFlow("overview")

        /** Currently-selected grid tab. */
        val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

        /** Switch the active grid tab. */
        fun selectTab(id: String) {
            _selectedTab.value = id
        }

        /** Expose the home id so the screen can build outbound nav routes. */
        fun currentHomeId(): String? = homeId

        /**
         * Display name of the loaded home, used as the 2-line top-bar
         * subtitle on the Access codes destination. Returns null while
         * the dashboard is still loading.
         */
        fun currentHomeName(): String? =
            when (val current = _state.value) {
                is HomeDashboardUiState.Loaded -> current.content.address
                is HomeDashboardUiState.Empty -> current.brandNew.content.address
                is HomeDashboardUiState.NeedsAttention -> current.content.address
                HomeDashboardUiState.Loading, is HomeDashboardUiState.Error -> null
            }

        /** Initial load; no-op when already loaded. */
        fun load() {
            if (_state.value is HomeDashboardUiState.Loaded ||
                _state.value is HomeDashboardUiState.Empty ||
                _state.value is HomeDashboardUiState.NeedsAttention
            ) {
                return
            }
            refresh()
        }

        /** Retry / pull-to-refresh. */
        fun refresh() {
            HomeDashboardSampleData.stateFor(homeId)?.let { sample ->
                _state.value = sample
                return
            }
            _state.value = HomeDashboardUiState.Loading
            viewModelScope.launch { fetch() }
        }

        private suspend fun fetch() {
            when (val result = repo.detail(homeId)) {
                is NetworkResult.Success -> applyDetail(result.data.home)
                is NetworkResult.Failure ->
                    if (result.error is NetworkError.Forbidden || result.error is NetworkError.NotFound) {
                        fetchPublic()
                    } else {
                        _state.value = HomeDashboardUiState.Error(result.error.message)
                    }
            }
        }

        private suspend fun fetchPublic() {
            when (val result = repo.publicProfile(homeId)) {
                is NetworkResult.Success -> applyPublic(result.data.home)
                is NetworkResult.Failure -> _state.value = HomeDashboardUiState.Error(result.error.message)
            }
        }

        private fun applyDetail(detail: HomeDetail) {
            val address = detail.address ?: detail.name ?: "Home"
            val stats =
                listOf(
                    HomeHeroStat("packages", "4", "Packages"),
                    HomeHeroStat("access_codes", "2", "Access codes"),
                    HomeHeroStat("tasks", "7", "Tasks"),
                )
            // Header / summary: home has any verified owner. Banner gate:
            // I'm the verified owner only when isOwner is true and there
            // is no pending claim still in flight.
            val homeVerified = detail.isOwner || detail.owners.any { it.ownerStatus == "verified" }
            val iAmVerified = detail.isOwner && !detail.isPendingOwner
            _state.value =
                HomeDashboardUiState.Loaded(
                    content(address, verified = homeVerified, isVerifiedOwner = iAmVerified, stats = stats),
                )
        }

        private fun applyPublic(publicProfile: HomePublicProfile) {
            val stats =
                listOf(
                    HomeHeroStat("packages", "0", "Packages"),
                    HomeHeroStat("access_codes", "0", "Access codes"),
                    HomeHeroStat("tasks", "0", "Tasks"),
                )
            // Public-profile path is hit when the user is NOT a verified
            // owner; the private detail call returned 403/404 first.
            _state.value =
                HomeDashboardUiState.Loaded(
                    content(
                        address = publicProfile.address,
                        verified = publicProfile.hasVerifiedOwner,
                        isVerifiedOwner = false,
                        stats = stats,
                    ),
                )
        }

        private fun content(
            address: String,
            verified: Boolean,
            isVerifiedOwner: Boolean,
            stats: List<HomeHeroStat>,
        ): HomeDashboardContent =
            HomeDashboardContent(
                address = address,
                verified = verified,
                isVerifiedOwner = isVerifiedOwner,
                stats = stats,
                quickActions =
                    HomeDashboardSampleData.populatedQuickActions,
                tabs = HomeDashboardSampleData.tabs,
                overview = HomeDashboardSampleData.populatedOverview,
                attentionSummary = null,
            )
    }
