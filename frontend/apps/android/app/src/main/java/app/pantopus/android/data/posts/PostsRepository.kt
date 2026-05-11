package app.pantopus.android.data.posts

import app.pantopus.android.data.api.models.posts.PostCommentCreateResponse
import app.pantopus.android.data.api.models.posts.PostCommentRequest
import app.pantopus.android.data.api.models.posts.PostCommentsResponse
import app.pantopus.android.data.api.models.posts.PostDetailResponse
import app.pantopus.android.data.api.models.posts.PostLikeResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.PostsApi
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps [PostsApi] in the [NetworkResult] taxonomy. */
@Singleton
class PostsRepository
    @Inject
    constructor(
        private val api: PostsApi,
    ) {
        /** `GET /api/posts/:id`. */
        suspend fun detail(id: String): NetworkResult<PostDetailResponse> = safeApiCall { api.detail(id) }

        /** `POST /api/posts/:id/like`. */
        suspend fun toggleLike(id: String): NetworkResult<PostLikeResponse> = safeApiCall { api.toggleLike(id) }

        /** `GET /api/posts/:id/comments`. */
        suspend fun comments(
            id: String,
            limit: Int = 50,
            offset: Int = 0,
        ): NetworkResult<PostCommentsResponse> = safeApiCall { api.comments(id, limit, offset) }

        /** `POST /api/posts/:id/comments`. */
        suspend fun createComment(
            id: String,
            body: PostCommentRequest,
        ): NetworkResult<PostCommentCreateResponse> = safeApiCall { api.createComment(id, body) }
    }
