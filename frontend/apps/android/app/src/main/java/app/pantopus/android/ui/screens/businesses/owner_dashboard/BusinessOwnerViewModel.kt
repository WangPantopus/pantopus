@file:Suppress("PackageNaming", "MagicNumber", "TooManyFunctions", "ReturnCount")

package app.pantopus.android.ui.screens.businesses.owner_dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.businesses.BusinessDashboardResponse
import app.pantopus.android.data.api.models.businesses.BusinessInsightsResponse
import app.pantopus.android.data.api.models.businesses.BusinessOnboardingDto
import app.pantopus.android.data.api.models.businesses.BusinessOwnerReviewDto
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.businesses.BusinessesRepository
import app.pantopus.android.data.profile.ProfileRepository
import app.pantopus.android.ui.screens.business_profile.BusinessProfileContent
import app.pantopus.android.ui.screens.business_profile.BusinessProfileMapper
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt

/** Nav-arg key for the owned business UUID (matches `ChildRoutes.BUSINESS_OWNER`). */
const val BUSINESS_OWNER_BUSINESS_ID_KEY = "businessId"

/**
 * A10.7 / P1-C — view-model for the single-business owner dashboard. The
 * owner data is now live:
 *   · the shared public render ([BusinessOwnerContent.publicProfile]) is built
 *     by [BusinessProfileMapper] — the exact A10.6 projection;
 *   · live status + edit recency + the profile-strength checklist come from
 *     `GET /:businessId/dashboard`;
 *   · the "This week" tiles come from `GET /:businessId/insights`;
 *   · the reply composer reads `GET /:businessId/reviews` and commits via
 *     `POST /:businessId/reviews/:reviewId/respond` (optimistic + rollback).
 *
 * [BusinessOwnerSampleData] is retained as the preview / snapshot seam —
 * call [seedForPreview] to skip the network. Mirrors iOS
 * `BusinessOwnerViewModel.swift`.
 */
@HiltViewModel
class BusinessOwnerViewModel
    @Inject
    constructor(
        private val businesses: BusinessesRepository,
        private val profiles: ProfileRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val businessId: String =
            requireNotNull(savedStateHandle[BUSINESS_OWNER_BUSINESS_ID_KEY]) {
                "BusinessOwnerViewModel requires a '$BUSINESS_OWNER_BUSINESS_ID_KEY' nav arg."
            }

        private val _state = MutableStateFlow<BusinessOwnerUiState>(BusinessOwnerUiState.Loading)
        val state: StateFlow<BusinessOwnerUiState> = _state.asStateFlow()

        fun load() {
            if (_state.value is BusinessOwnerUiState.Loaded) return
            _state.value = BusinessOwnerUiState.Loading
            viewModelScope.launch { fetch() }
        }

        fun refresh() {
            _state.value = BusinessOwnerUiState.Loading
            viewModelScope.launch { fetch() }
        }

        /** Test / preview hook: seed the loaded state directly. */
        fun seedForPreview(content: BusinessOwnerContent) {
            _state.value = BusinessOwnerUiState.Loaded(content)
        }

        /**
         * Commit a review reply: optimistic local update, then `POST …/respond`.
         * On failure the optimistic change is rolled back.
         */
        fun submitReply(
            reviewId: String,
            text: String,
        ) {
            val current = _state.value as? BusinessOwnerUiState.Loaded ?: return
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return

            // Optimistic update.
            _state.value = BusinessOwnerUiState.Loaded(current.content.applyingReply(trimmed, reviewId))

            viewModelScope.launch {
                val result = businesses.respondToReview(businessId, reviewId, trimmed)
                if (result is NetworkResult.Failure && _state.value is BusinessOwnerUiState.Loaded) {
                    // Roll back to the pre-optimistic content.
                    _state.value = current
                }
            }
        }

        // MARK: - Fetch

        private suspend fun fetch() {
            // 1. Public render (primary) — the same projection the Business
            //    Profile screen renders, so the owner / preview frames agree.
            val detail = businesses.business(businessId)
            if (detail is NetworkResult.Failure) {
                _state.value = mapDetailFailure(detail.error)
                return
            }
            val payload = (detail as NetworkResult.Success).data
            val publicProfile =
                coroutineScope {
                    val publicDeferred =
                        async {
                            payload.business.username
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { (businesses.publicBusiness(it) as? NetworkResult.Success)?.data }
                        }
                    val reviewsDeferred =
                        async { (profiles.publicProfile(businessId) as? NetworkResult.Success)?.data }
                    BusinessProfileMapper.build(payload, publicDeferred.await(), reviewsDeferred.await())
                }

            // 2. Owner-scoped dashboard (required) — publish state + strength.
            val dash = businesses.dashboard(businessId)
            if (dash is NetworkResult.Failure) {
                _state.value = mapDashboardFailure(dash.error)
                return
            }
            val dashboard = (dash as NetworkResult.Success).data

            // 3. Tiles + reviews (best-effort overlays).
            val insights = (businesses.insights(businessId) as? NetworkResult.Success)?.data
            val ownerReviews =
                (businesses.reviews(businessId) as? NetworkResult.Success)?.data?.reviews ?: emptyList()

            _state.value =
                BusinessOwnerUiState.Loaded(buildContent(publicProfile, dashboard, insights, ownerReviews))
        }

        private fun mapDetailFailure(error: NetworkError): BusinessOwnerUiState =
            when (error) {
                NetworkError.NotFound -> BusinessOwnerUiState.NotFound
                else -> BusinessOwnerUiState.Error("Couldn't load your business")
            }

        private fun mapDashboardFailure(error: NetworkError): BusinessOwnerUiState =
            when (error) {
                NetworkError.Forbidden -> BusinessOwnerUiState.Error("You don't have access to this business.")
                NetworkError.NotFound -> BusinessOwnerUiState.NotFound
                else -> BusinessOwnerUiState.Error("Couldn't load your business")
            }

        // MARK: - Projection (pure; testable)

        /** Compose the owner content from the public render + owner fetches. */
        fun buildContent(
            publicProfile: BusinessProfileContent,
            dashboard: BusinessDashboardResponse,
            insights: BusinessInsightsResponse?,
            reviews: List<BusinessOwnerReviewDto>,
        ): BusinessOwnerContent {
            val isLive = dashboard.profile?.isPublished == true
            val mappedReviews = reviews.map { ownerReview(it) }
            val pending = mappedReviews.count { it.reply == null }
            return BusinessOwnerContent(
                businessId = businessId,
                isLive = isLive,
                editedMeta = editedMeta(dashboard.profile?.updatedAt, isLive),
                insights = insightTiles(insights),
                profileStrength = profileStrength(dashboard.onboarding),
                reviewsToReplyLabel = if (pending > 0) "$pending to reply" else null,
                reviews = mappedReviews,
                publicProfile = publicProfile,
            )
        }

        private fun insightTiles(insights: BusinessInsightsResponse?): List<OwnerInsightTile> {
            if (insights == null) return emptyList()
            return listOf(
                OwnerInsightTile(
                    id = "views",
                    icon = PantopusIcon.Eye,
                    value = formatCount(insights.views.total),
                    label = "Views",
                    delta = trendLabel(insights.views.trend),
                ),
                OwnerInsightTile(
                    id = "followers",
                    icon = PantopusIcon.Users,
                    value = formatCount(insights.followers.total),
                    label = "Followers",
                    delta = trendLabel(insights.followers.trend),
                ),
                OwnerInsightTile(
                    id = "reviews",
                    icon = PantopusIcon.Star,
                    value = formatCount(insights.reviews.count),
                    label = "Reviews",
                    delta = trendLabel(insights.reviews.trend),
                ),
            )
        }

        private fun profileStrength(onboarding: BusinessOnboardingDto?): OwnerProfileStrength {
            if (onboarding == null || onboarding.totalCount <= 0) {
                return OwnerProfileStrength(0, "Finish setting up your page", emptyList())
            }
            val percent = (onboarding.completedCount.toDouble() / onboarding.totalCount * 100).roundToInt()
            val remaining = (onboarding.totalCount - onboarding.completedCount).coerceAtLeast(0)
            val caption =
                when (remaining) {
                    0 -> "Your page is complete"
                    1 -> "One step from a complete page"
                    else -> "$remaining steps from a complete page"
                }
            val steps =
                onboarding.checklist.map {
                    OwnerStrengthStep(it.key, it.label, it.done, ctaLabel = if (it.done) null else "Add")
                }
            return OwnerProfileStrength(percent, caption, steps)
        }

        private fun ownerReview(dto: BusinessOwnerReviewDto): OwnerReviewItem {
            val meta =
                listOfNotNull(relativeTimestamp(dto.createdAt), dto.gigTitle?.takeIf { it.isNotEmpty() })
                    .joinToString(" · ")
            return OwnerReviewItem(
                id = dto.id,
                reviewerName = dto.reviewerName?.takeIf { it.isNotEmpty() } ?: "Anonymous",
                reviewerAvatarUrl = dto.reviewerAvatar,
                meta = meta,
                rating = dto.rating,
                body = dto.comment.orEmpty(),
                reply = dto.ownerResponse?.takeIf { it.isNotEmpty() },
            )
        }

        // MARK: - Formatting

        private fun editedMeta(
            updatedAt: String?,
            isLive: Boolean,
        ): String {
            val relative = relativeTimestamp(updatedAt)
            return when {
                relative != null -> "Edited $relative"
                isLive -> "Live"
                else -> "Draft"
            }
        }

        /** `1234 → "1.2k"`, `84 → "84"`. */
        private fun formatCount(value: Int): String {
            if (value < 1_000) return value.toString()
            val truncated = value / 1_000.0
            return String.format(java.util.Locale.US, "%.1fk", truncated).replace(".0k", "k")
        }

        /** Only positive trends render a pill — the tile draws a fixed up-arrow. */
        private fun trendLabel(trend: Int): String? = if (trend > 0) "$trend%" else null

        private fun relativeTimestamp(iso: String?): String? {
            if (iso.isNullOrEmpty()) return null
            val instant =
                try {
                    Instant.parse(iso)
                } catch (_: Throwable) {
                    return null
                }
            val seconds = Duration.between(instant, Instant.now()).seconds
            return when {
                seconds < 60 -> "just now"
                seconds < 3_600 -> "${seconds / 60}m ago"
                seconds < 86_400 -> "${seconds / 3_600}h ago"
                seconds < 604_800 -> "${seconds / 86_400}d ago"
                else -> "${seconds / 604_800}w ago"
            }
        }
    }
