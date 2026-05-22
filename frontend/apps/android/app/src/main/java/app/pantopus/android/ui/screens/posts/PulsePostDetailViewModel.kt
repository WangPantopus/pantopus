@file:Suppress("MagicNumber", "LongMethod", "PackageNaming", "TooGenericExceptionCaught", "TooManyFunctions")

package app.pantopus.android.ui.screens.posts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.posts.PostCommentDto
import app.pantopus.android.data.api.models.posts.PostCommentRequest
import app.pantopus.android.data.api.models.posts.PostDetailDto
import app.pantopus.android.data.api.models.posts.PostReactionKind
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.posts.PostsRepository
import app.pantopus.android.ui.components.IdentityPillar
import app.pantopus.android.ui.screens.shared.content_detail.bodies.PostCommentRow
import app.pantopus.android.ui.screens.shared.content_detail.bodies.PostReactionCounts
import app.pantopus.android.ui.screens.shared.content_detail.headers.PostIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Nav-arg key for the Pulse post ID. */
const val PULSE_POST_DETAIL_ID_KEY = "postId"

/** Render-ready payload for the Pulse post detail screen. */
data class PulsePostDetailContent(
    val post: PostDetailDto,
    val authorDisplayName: String,
    val authorAvatarUrl: String?,
    val authorIdentity: IdentityPillar,
    val authorVerified: Boolean,
    val timeAndLocality: String,
    val intent: PostIntent,
    val mediaUrls: List<String>,
    val reactions: PostReactionCounts,
    val comments: List<PostCommentRow>,
    val hiddenReplyCount: Int,
)

/** Observed UI state for the Pulse post detail screen. */
sealed interface PulsePostDetailUiState {
    data object Loading : PulsePostDetailUiState

    data class Loaded(val content: PulsePostDetailContent) : PulsePostDetailUiState

    data class Error(val message: String) : PulsePostDetailUiState
}

/** Loads + mutates a single Pulse post. */
@HiltViewModel
class PulsePostDetailViewModel
    @Inject
    constructor(
        private val repo: PostsRepository,
        private val authRepo: AuthRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val postId: String =
            requireNotNull(savedStateHandle[PULSE_POST_DETAIL_ID_KEY]) {
                "PulsePostDetailViewModel requires a '$PULSE_POST_DETAIL_ID_KEY' nav arg."
            }

        private val _state = MutableStateFlow<PulsePostDetailUiState>(PulsePostDetailUiState.Loading)
        val state: StateFlow<PulsePostDetailUiState> = _state.asStateFlow()

        private val _composerText = MutableStateFlow("")
        val composerText: StateFlow<String> = _composerText.asStateFlow()

        private val _isSendingComment = MutableStateFlow(false)
        val isSendingComment: StateFlow<Boolean> = _isSendingComment.asStateFlow()

        private val _toastMessage = MutableStateFlow<String?>(null)
        val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

        private val _showsOverflowMenu = MutableStateFlow(false)

        /**
         * Bound to the screen's overflow modal so the Edit action sheet
         * pops when the owner taps the top-bar's more-horizontal icon.
         */
        val showsOverflowMenu: StateFlow<Boolean> = _showsOverflowMenu.asStateFlow()

        private val showingAllReplies = MutableStateFlow(false)

        private val maxInitialReplies = 3

        /**
         * True when the signed-in user authored the post on screen. The
         * screen uses this to gate the Edit overflow action.
         */
        val isOwner: Boolean
            get() {
                val loaded = _state.value as? PulsePostDetailUiState.Loaded ?: return false
                val signedIn = authRepo.state.value as? AuthRepository.State.SignedIn ?: return false
                return loaded.content.post.userId == signedIn.user.id
            }

        fun openOverflowMenu() {
            _showsOverflowMenu.value = true
        }

        fun dismissOverflowMenu() {
            _showsOverflowMenu.value = false
        }

        /** First-load entry. */
        fun load() {
            if (_state.value is PulsePostDetailUiState.Loaded) return
            refresh()
        }

        /** Pull-to-refresh / retry. */
        fun refresh() {
            _state.value = PulsePostDetailUiState.Loading
            viewModelScope.launch { fetch() }
        }

        fun setComposerText(text: String) {
            _composerText.value = text
        }

        fun dismissToast() {
            _toastMessage.value = null
        }

        fun showMoreReplies() {
            val loaded = _state.value as? PulsePostDetailUiState.Loaded ?: return
            showingAllReplies.value = true
            _state.value = PulsePostDetailUiState.Loaded(rebuildContent(loaded.content.post))
        }

        /**
         * Tap one of the reaction pills. Only `.Helpful` is wired to a
         * backend route today; the `ReactionsBar` view renders the
         * other kinds as display-only so this method only ever sees
         * `.Helpful`, but we keep the guard as a safety net.
         */
        fun tapReaction(kind: PostReactionKind) {
            val loaded = _state.value as? PulsePostDetailUiState.Loaded ?: return
            if (!kind.isBackendWired) return
            val initialReactions = loaded.content.reactions
            val wasOn = initialReactions.userReaction == PostReactionKind.Helpful
            val optimistic =
                initialReactions.copy(
                    helpful = if (wasOn) (initialReactions.helpful - 1).coerceAtLeast(0) else initialReactions.helpful + 1,
                    userReaction = if (wasOn) null else PostReactionKind.Helpful,
                )
            _state.value = PulsePostDetailUiState.Loaded(loaded.content.copy(reactions = optimistic))

            viewModelScope.launch {
                when (val result = repo.toggleLike(postId)) {
                    is NetworkResult.Success -> {
                        val reconciled =
                            optimistic.copy(
                                helpful = result.data.likeCount,
                                userReaction = if (result.data.liked) PostReactionKind.Helpful else null,
                            )
                        _state.update { current ->
                            (current as? PulsePostDetailUiState.Loaded)?.let {
                                PulsePostDetailUiState.Loaded(it.content.copy(reactions = reconciled))
                            } ?: current
                        }
                    }
                    is NetworkResult.Failure -> {
                        _toastMessage.value = "Couldn't update your reaction"
                        _state.update { current ->
                            (current as? PulsePostDetailUiState.Loaded)?.let {
                                PulsePostDetailUiState.Loaded(
                                    it.content.copy(reactions = initialReactions),
                                )
                            } ?: current
                        }
                    }
                }
            }
        }

        /** Submit the composer's current text as a new top-level comment. */
        fun sendComment() {
            val body = _composerText.value.trim()
            if (body.isEmpty() || _isSendingComment.value) return
            _isSendingComment.value = true
            viewModelScope.launch {
                val req = PostCommentRequest(comment = body, parentCommentId = null)
                when (repo.createComment(postId, req)) {
                    is NetworkResult.Success -> {
                        _composerText.value = ""
                        fetch()
                    }
                    is NetworkResult.Failure -> {
                        _toastMessage.value = "Couldn't post your comment"
                    }
                }
                _isSendingComment.value = false
            }
        }

        // MARK: - Internal helpers

        private suspend fun fetch() {
            when (val result = repo.detail(postId)) {
                is NetworkResult.Success -> {
                    _state.value = PulsePostDetailUiState.Loaded(rebuildContent(result.data.post))
                }
                is NetworkResult.Failure -> {
                    _state.value = PulsePostDetailUiState.Error(friendlyMessage(result.error))
                }
            }
        }

        private fun rebuildContent(post: PostDetailDto): PulsePostDetailContent {
            val (rows, hidden) = buildCommentRows(post.comments)
            val identity = mapAccountType(post.creator?.accountType)
            return PulsePostDetailContent(
                post = post,
                authorDisplayName = post.creator?.displayName ?: "Pantopus user",
                authorAvatarUrl = post.creator?.profilePictureUrl,
                authorIdentity = identity,
                // TODO(backend): backend `CREATOR_SELECT` for posts does
                // not include the `verified` column today, so the post
                // header can never show a verified badge. Wire this up
                // once feedService.js#CREATOR_SELECT joins `verified`.
                authorVerified = false,
                timeAndLocality =
                    formatTimeAndLocality(
                        createdAt = post.createdAt,
                        locality = post.creator?.locality ?: post.home?.city,
                    ),
                intent = PostIntent.from(post.purpose, post.postType),
                mediaUrls = post.mediaUrls,
                reactions =
                    PostReactionCounts(
                        helpful = post.likeCount,
                        heart = 0,
                        going = 0,
                        userReaction = if (post.userHasLiked) PostReactionKind.Helpful else null,
                    ),
                comments = rows,
                hiddenReplyCount = hidden,
            )
        }

        private fun mapAccountType(type: String?): IdentityPillar =
            when (type) {
                "business" -> IdentityPillar.Business
                "home" -> IdentityPillar.Home
                else -> IdentityPillar.Personal
            }

        private fun buildCommentRows(comments: List<PostCommentDto>): Pair<List<PostCommentRow>, Int> {
            val visible = comments.filter { !it.isDeleted }
            val topLevel = visible.filter { it.parentCommentId == null }
            val repliesByParent =
                visible.filter { it.parentCommentId != null }
                    .groupBy { it.parentCommentId ?: "" }
                    .mapValues { (_, list) -> list.sortedBy { it.createdAt } }

            val rows = mutableListOf<PostCommentRow>()
            var visibleReplyCount = 0
            var totalReplyCount = 0

            for (parent in topLevel) {
                rows += rowFrom(parent, indent = 0)
                val replies = repliesByParent[parent.id].orEmpty()
                totalReplyCount += replies.size
                val cap =
                    if (showingAllReplies.value) {
                        replies.size
                    } else {
                        minOf(maxInitialReplies, replies.size)
                    }
                for (reply in replies.take(cap)) {
                    rows += rowFrom(reply, indent = 1)
                    visibleReplyCount++
                }
            }
            val hidden =
                if (showingAllReplies.value) {
                    0
                } else {
                    (totalReplyCount - visibleReplyCount).coerceAtLeast(0)
                }
            return rows to hidden
        }

        private fun rowFrom(
            comment: PostCommentDto,
            indent: Int,
        ): PostCommentRow =
            PostCommentRow(
                id = comment.id,
                authorName = comment.author?.displayName ?: "Pantopus user",
                authorAvatarUrl = comment.author?.profilePictureUrl,
                authorIdentity = mapAccountType(comment.author?.accountType),
                body = comment.comment,
                timestamp = relativeTimestamp(comment.createdAt),
                reactionCount = comment.likeCount ?: 0,
                userReacted = comment.userHasLiked ?: false,
                indentLevel = indent,
                authorUserId = comment.author?.id,
            )

        private fun formatTimeAndLocality(
            createdAt: String,
            locality: String?,
        ): String {
            val ts = relativeTimestamp(createdAt)
            return if (!locality.isNullOrEmpty()) "$ts · $locality" else ts
        }

        private fun relativeTimestamp(iso: String?): String {
            if (iso.isNullOrEmpty()) return ""
            val instant =
                try {
                    Instant.parse(iso)
                } catch (_: Throwable) {
                    return ""
                }
            val elapsed = Duration.between(instant, Instant.now())
            val seconds = elapsed.seconds
            return when {
                seconds < 60 -> "Just now"
                seconds < 3_600 -> "${seconds / 60}m ago"
                seconds < 86_400 -> "${seconds / 3_600}h ago"
                seconds < 604_800 -> "${seconds / 86_400}d ago"
                else ->
                    DateTimeFormatter.ISO_LOCAL_DATE.format(
                        instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                    )
            }
        }

        private fun friendlyMessage(error: NetworkError): String =
            when (error) {
                NetworkError.NotFound -> "We couldn't find this post."
                NetworkError.Forbidden -> "You don't have access to this post."
                is NetworkError.Transport -> "Check your connection and try again."
                else -> "Something went wrong. Try again."
            }
    }
