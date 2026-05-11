@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "TooManyFunctions")

package app.pantopus.android.ui.screens.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.profile.PublicProfileDto
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.profile.ProfileRepository
import app.pantopus.android.ui.components.IdentityPillar
import app.pantopus.android.ui.screens.shared.content_detail.bodies.ProfileReviewCard
import app.pantopus.android.ui.screens.shared.content_detail.bodies.ProfileStatCell
import app.pantopus.android.ui.screens.shared.content_detail.bodies.ProfileTab
import app.pantopus.android.ui.screens.shared.content_detail.bodies.StatsTabsContent
import app.pantopus.android.ui.screens.shared.content_detail.headers.IdentityPillarBadge
import app.pantopus.android.ui.screens.shared.content_detail.headers.IdentityPillarVerificationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Nav-arg key for the user ID. */
const val PUBLIC_PROFILE_USER_ID_KEY = "userId"

/** Header surface for the public profile. */
data class PublicProfileHeader(
    val displayName: String,
    val handle: String?,
    val locality: String?,
    val avatarUrl: String?,
    val isVerified: Boolean,
    val identityBadges: List<IdentityPillarBadge>,
)

/** Render-ready payload emitted by [PublicProfileViewModel]. */
data class PublicProfileContent(
    val profile: PublicProfileDto,
    val header: PublicProfileHeader,
    val stats: StatsTabsContent,
)

/** Observed UI state for the Public profile screen. */
sealed interface PublicProfileUiState {
    data object Loading : PublicProfileUiState

    data class Loaded(val content: PublicProfileContent) : PublicProfileUiState

    data class Error(val message: String) : PublicProfileUiState
}

/** Loads `GET /api/users/id/:id` and exposes a stable tab + toast surface. */
@HiltViewModel
class PublicProfileViewModel
    @Inject
    constructor(
        private val repo: ProfileRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val userId: String =
            requireNotNull(savedStateHandle[PUBLIC_PROFILE_USER_ID_KEY]) {
                "PublicProfileViewModel requires a '$PUBLIC_PROFILE_USER_ID_KEY' nav arg."
            }

        private val _state = MutableStateFlow<PublicProfileUiState>(PublicProfileUiState.Loading)
        val state: StateFlow<PublicProfileUiState> = _state.asStateFlow()

        private val _selectedTab = MutableStateFlow(ProfileTab.About)
        val selectedTab: StateFlow<ProfileTab> = _selectedTab.asStateFlow()

        private val _toastMessage = MutableStateFlow<String?>(null)
        val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

        fun load() {
            if (_state.value is PublicProfileUiState.Loaded) return
            refresh()
        }

        fun refresh() {
            _state.value = PublicProfileUiState.Loading
            viewModelScope.launch { fetch() }
        }

        fun selectTab(tab: ProfileTab) {
            _selectedTab.value = tab
        }

        fun dismissToast() {
            _toastMessage.value = null
        }

        fun tapMessage() {
            _toastMessage.value = "Messaging coming soon"
        }

        fun tapConnect() {
            _toastMessage.value = "Connect coming soon"
        }

        fun tapOverflow() {
            _toastMessage.value = "More actions coming soon"
        }

        private suspend fun fetch() {
            when (val result = repo.publicProfile(userId)) {
                is NetworkResult.Success -> {
                    _state.value = PublicProfileUiState.Loaded(build(result.data))
                }
                is NetworkResult.Failure -> {
                    _state.value = PublicProfileUiState.Error(friendlyMessage(result.error))
                }
            }
        }

        private fun build(profile: PublicProfileDto): PublicProfileContent {
            val header =
                PublicProfileHeader(
                    displayName = profile.displayName,
                    handle = profile.username.takeIf { it.isNotEmpty() },
                    locality = profile.locality,
                    avatarUrl = profile.profilePictureUrl ?: profile.avatarUrl,
                    isVerified = profile.verified == true,
                    identityBadges = buildBadges(profile),
                )
            val stats = mutableListOf<ProfileStatCell>()
            val reviewCount = profile.reviewCount ?: 0
            if (reviewCount > 0 || profile.reviews.isNotEmpty()) {
                stats +=
                    ProfileStatCell(
                        id = "reviews",
                        value = "${profile.reviewCount ?: profile.reviews.size}",
                        label = "Reviews",
                    )
            }
            val rating = profile.averageRating ?: 0.0
            if (rating > 0) {
                stats +=
                    ProfileStatCell(
                        id = "rating",
                        value = "%.1f".format(rating),
                        label = "Rating",
                    )
            }
            val gigsCompleted = profile.gigsCompleted ?: 0
            val gigsPosted = profile.gigsPosted ?: 0
            when {
                gigsCompleted > 0 ->
                    stats += ProfileStatCell(id = "gigs", value = "$gigsCompleted", label = "Gigs")
                gigsPosted > 0 ->
                    stats += ProfileStatCell(id = "gigs", value = "$gigsPosted", label = "Gigs")
            }
            if (stats.isEmpty()) {
                stats += ProfileStatCell(id = "placeholder", value = "—", label = "Activity")
            }

            val reviewCards =
                profile.reviews.map { r ->
                    ProfileReviewCard(
                        id = r.id ?: UUID.randomUUID().toString(),
                        reviewerName = r.reviewerName ?: "Anonymous",
                        reviewerAvatarUrl = r.reviewerAvatar,
                        rating = r.rating.coerceIn(0, 5),
                        body = r.content.orEmpty(),
                        timestamp = relativeTimestamp(r.createdAt),
                    )
                }

            return PublicProfileContent(
                profile = profile,
                header = header,
                stats =
                    StatsTabsContent(
                        stats = stats,
                        bio = profile.bio,
                        skills = profile.skills,
                        reviews = reviewCards,
                    ),
            )
        }

        private fun buildBadges(profile: PublicProfileDto): List<IdentityPillarBadge> {
            val verified = profile.verified == true
            val homeState =
                if (hasHomeResidency(profile)) {
                    IdentityPillarVerificationState.Verified
                } else {
                    IdentityPillarVerificationState.Unverified
                }
            val businessState =
                if (profile.accountType == "business") {
                    IdentityPillarVerificationState.Verified
                } else {
                    IdentityPillarVerificationState.Unverified
                }
            return listOf(
                IdentityPillarBadge(
                    pillar = IdentityPillar.Personal,
                    state = if (verified) IdentityPillarVerificationState.Verified else IdentityPillarVerificationState.Unverified,
                ),
                IdentityPillarBadge(
                    pillar = IdentityPillar.Home,
                    state = homeState,
                ),
                IdentityPillarBadge(
                    pillar = IdentityPillar.Business,
                    state = businessState,
                ),
            )
        }

        private fun hasHomeResidency(profile: PublicProfileDto): Boolean {
            val r = profile.residency ?: return false
            val verifiedValue = r["verified"]
            if (verifiedValue is Boolean) return verifiedValue
            return r.isNotEmpty()
        }

        private fun relativeTimestamp(iso: String?): String {
            if (iso.isNullOrEmpty()) return ""
            val instant =
                try {
                    Instant.parse(iso)
                } catch (_: Throwable) {
                    return ""
                }
            val seconds = Duration.between(instant, Instant.now()).seconds
            return when {
                seconds < 60 -> "Just now"
                seconds < 3_600 -> "${seconds / 60}m ago"
                seconds < 86_400 -> "${seconds / 3_600}h ago"
                seconds < 604_800 -> "${seconds / 86_400}d ago"
                else -> instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
            }
        }

        private fun friendlyMessage(error: NetworkError): String =
            when (error) {
                NetworkError.NotFound -> "We couldn't find this profile."
                NetworkError.Forbidden -> "This profile is private."
                is NetworkError.Transport -> "Check your connection and try again."
                else -> "Something went wrong. Try again."
            }
    }
