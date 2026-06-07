@file:Suppress("MagicNumber", "PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.feed.pulse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.feed.FeedPost
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.location.LocationProvider
import app.pantopus.android.data.posts.PostsRepository
import app.pantopus.android.data.posts.PulsePostsRefreshNotifier
import app.pantopus.android.ui.screens.feed.FeedEmptyContent
import app.pantopus.android.ui.screens.feed.FeedSurface
import app.pantopus.android.ui.screens.shared.feed.FeedAvatarTint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Render state for the Pulse feed screen. */
sealed interface PulseFeedUiState {
    data object Loading : PulseFeedUiState

    data class Empty(
        val content: FeedEmptyContent,
    ) : PulseFeedUiState

    data class Loaded(
        val rows: List<PulsePostCardContent>,
    ) : PulseFeedUiState

    data class Error(
        val message: String,
    ) : PulseFeedUiState
}

/** Pulse feed view-model. */
@HiltViewModel
class PulseFeedViewModel
    @Inject
    constructor(
        private val repo: PostsRepository,
        private val locationProvider: LocationProvider,
        private val postsRefresh: PulsePostsRefreshNotifier,
    ) : ViewModel() {
        private val _state = MutableStateFlow<PulseFeedUiState>(PulseFeedUiState.Loading)
        val state: StateFlow<PulseFeedUiState> = _state.asStateFlow()

        private val _activeIntent = MutableStateFlow(PulseIntent.All)
        val activeIntent: StateFlow<PulseIntent> = _activeIntent.asStateFlow()

        /** True while a pull-to-refresh refetch is in flight (drives the spinner). */
        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        /** Which surface this feed renders (Pulse vs Beacons). */
        var surface: FeedSurface = FeedSurface.Pulse
            private set

        private var scopeLabel: String? = null
        private var latitude: Double? = null
        private var longitude: Double? = null
        private var resolvedLatitude: Double? = null
        private var resolvedLongitude: Double? = null
        private var loading = false

        init {
            viewModelScope.launch {
                postsRefresh.ticks.collect {
                    refresh()
                }
            }
        }

        /** Wire location coordinates from the host before the first load. */
        fun configureLocation(
            latitude: Double?,
            longitude: Double?,
        ) {
            this.latitude = latitude
            this.longitude = longitude
        }

        /** Select the surface (Pulse vs Beacons) before the first load. */
        fun configureSurface(surface: FeedSurface) {
            this.surface = surface
        }

        fun load() {
            if (_state.value is PulseFeedUiState.Loaded) return
            fetch()
        }

        fun refresh() = fetch(isRefresh = true)

        fun selectIntent(intent: PulseIntent) {
            if (_activeIntent.value == intent) return
            _activeIntent.value = intent
            fetch()
        }

        /**
         * Tap on a post's primary reaction. Optimistically toggles the
         * per-post `userHasReacted` + helpful count, then hits
         * `POST /api/posts/:id/like`. Rolls back on failure.
         */
        fun tapReaction(postId: String) {
            val loaded = _state.value as? PulseFeedUiState.Loaded ?: return
            val index = loaded.rows.indexOfFirst { it.id == postId }
            if (index < 0) return
            val original = loaded.rows[index]
            val primary = original.reactions.firstOrNull() ?: return
            val toggled = !original.userHasReacted
            val optimisticCount = (primary.count + if (toggled) 1 else -1).coerceAtLeast(0)
            _state.value = loaded.copy(rows = loaded.rows.update(index, original.withReaction(toggled, optimisticCount)))

            viewModelScope.launch {
                when (val result = repo.toggleLike(postId)) {
                    is NetworkResult.Success -> {
                        val current = _state.value as? PulseFeedUiState.Loaded ?: return@launch
                        val rIndex = current.rows.indexOfFirst { it.id == postId }
                        if (rIndex >= 0) {
                            _state.value =
                                current.copy(
                                    rows =
                                        current.rows.update(
                                            rIndex,
                                            current.rows[rIndex].withReaction(
                                                result.data.liked,
                                                result.data.likeCount,
                                            ),
                                        ),
                                )
                        }
                    }
                    is NetworkResult.Failure -> {
                        val current = _state.value as? PulseFeedUiState.Loaded ?: return@launch
                        val rIndex = current.rows.indexOfFirst { it.id == postId }
                        if (rIndex >= 0) {
                            _state.value =
                                current.copy(
                                    rows = current.rows.update(rIndex, original),
                                )
                        }
                    }
                }
            }
        }

        private fun fetch(isRefresh: Boolean = false) {
            if (loading) return
            loading = true
            if (isRefresh) _isRefreshing.value = true
            if (_state.value !is PulseFeedUiState.Loaded) {
                _state.value = PulseFeedUiState.Loading
            }
            viewModelScope.launch {
                try {
                    val (lat, lng) = resolvedCoordinates()
                    when (
                        val result =
                            repo.feed(
                                surface = surface.backendSurface,
                                latitude = lat,
                                longitude = lng,
                                postType = _activeIntent.value.postType,
                            )
                    ) {
                        is NetworkResult.Success -> {
                            val response = result.data
                            scopeLabel = response.posts.firstOrNull()?.locationName ?: scopeLabel
                            _state.value =
                                if (response.posts.isEmpty()) {
                                    PulseFeedUiState.Empty(
                                        content = surface.emptyContent(scopeLabel = scopeLabel, followCount = 0),
                                    )
                                } else {
                                    PulseFeedUiState.Loaded(rows = response.posts.map(::projectCard))
                                }
                        }
                        is NetworkResult.Failure -> {
                            _state.value = PulseFeedUiState.Error(result.error.message)
                        }
                    }
                } finally {
                    loading = false
                    _isRefreshing.value = false
                }
            }
        }

        private suspend fun resolvedCoordinates(): Pair<Double?, Double?> =
            explicitCoordinates()
                ?: storedCoordinates()
                ?: awaitFreshCoordinates()
                ?: (null to null)

        private fun explicitCoordinates(): Pair<Double, Double>? {
            val lat = latitude ?: return null
            val lng = longitude ?: return null
            return lat to lng
        }

        private fun storedCoordinates(): Pair<Double, Double>? {
            val lat = resolvedLatitude ?: return null
            val lng = resolvedLongitude ?: return null
            return lat to lng
        }

        private suspend fun awaitFreshCoordinates(): Pair<Double, Double>? {
            val cached = locationProvider.cachedCoordinate()
            val fresh = cached ?: locationProvider.requestCurrent(timeoutMillis = 4_000)
            val coordinate = fresh ?: return null
            resolvedLatitude = coordinate.latitude
            resolvedLongitude = coordinate.longitude
            return coordinate.latitude to coordinate.longitude
        }

        private fun projectCard(post: FeedPost): PulsePostCardContent {
            val intent = PulseIntent.fromPostType(post.postType)
            val authorName = post.creator?.displayName() ?: "Pantopus user"
            val isBusiness = post.creator?.accountType == "business"
            return PulsePostCardContent(
                id = post.id,
                authorName = authorName,
                authorInitials = initials(authorName),
                // Beacons authors are all verified by definition; on Pulse, fall
                // back to account-type until the backend surfaces creator.verified.
                authorVerified = surface.authorsAlwaysVerified || isBusiness || post.userHasLiked,
                avatarTint = if (isBusiness) FeedAvatarTint.Violet else FeedAvatarTint.Sky,
                meta = metaString(post),
                intent = intent,
                title = if (intent == PulseIntent.Event) post.title else null,
                body = post.content.orEmpty(),
                reactions =
                    intent.reactionTemplate(
                        helpfulCount = post.likeCount,
                        secondaryCount = post.commentCount,
                    ),
                attendees =
                    if (intent == PulseIntent.Event) {
                        PulseAttendeeStrip(
                            avatars = emptyList(),
                            goingCount = post.likeCount,
                            userIsGoing = post.userHasLiked,
                        )
                    } else {
                        null
                    },
                userHasReacted = post.userHasLiked,
                mediaUrls =
                    resolvePulsePostMediaUrls(
                        urls = post.mediaUrls,
                        thumbnails = post.mediaThumbnails,
                    ),
            )
        }

        private fun metaString(post: FeedPost): String {
            val relative = relativeTimestamp(post.createdAt)
            val locality = post.locationName?.takeIf { it.isNotEmpty() }
            return if (locality != null) "$relative · $locality" else relative
        }

        private fun relativeTimestamp(iso: String): String =
            runCatching {
                val instant = Instant.parse(iso)
                val seconds = Duration.between(instant, Instant.now()).seconds
                when {
                    seconds < 60 -> "Just now"
                    seconds < 3_600 -> "${seconds / 60}m"
                    seconds < 86_400 -> "${seconds / 3_600}h"
                    seconds < 604_800 -> "${seconds / 86_400}d"
                    else -> "${seconds / 604_800}w"
                }
            }.getOrDefault(iso)

        private fun initials(name: String): String {
            val parts = name.split(" ").take(2)
            return parts.mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()
        }
    }

private fun <T> List<T>.update(
    index: Int,
    value: T,
): List<T> = toMutableList().also { it[index] = value }

private fun PulsePostCardContent.withReaction(
    hasReacted: Boolean,
    primaryCount: Int,
): PulsePostCardContent {
    val primary = reactions.firstOrNull() ?: return this
    val updated =
        reactions.toMutableList().also { list ->
            list[0] = primary.copy(count = primaryCount)
        }
    return copy(reactions = updated, userHasReacted = hasReacted)
}
