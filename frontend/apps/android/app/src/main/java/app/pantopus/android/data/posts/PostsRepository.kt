package app.pantopus.android.data.posts

import app.pantopus.android.data.api.models.feed.FeedResponse
import app.pantopus.android.data.api.models.posts.MyPostsResponse
import app.pantopus.android.data.api.models.posts.PostCommentCreateResponse
import app.pantopus.android.data.api.models.posts.PostCommentRequest
import app.pantopus.android.data.api.models.posts.PostCommentsResponse
import app.pantopus.android.data.api.models.posts.PostCreateRequest
import app.pantopus.android.data.api.models.posts.PostCreateResponse
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
        /** `GET /api/posts/feed`. */
        suspend fun feed(
            surface: String = "place",
            latitude: Double? = null,
            longitude: Double? = null,
            postType: String? = null,
            limit: Int = 20,
        ): NetworkResult<FeedResponse> =
            safeApiCall {
                api.feed(
                    surface = surface,
                    latitude = latitude,
                    longitude = longitude,
                    postType = postType,
                    limit = limit,
                )
            }

        /** `POST /api/posts` — create a new post. */
        suspend fun createPost(body: PostCreateRequest): NetworkResult<PostCreateResponse> = safeApiCall { api.createPost(body) }

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

        /**
         * `GET /api/posts/user/:userId` — paged list of posts authored by a
         * user. T5.3.3 My posts uses the signed-in user's id. Backend filters
         * archived posts out today; the Archived tab is fed by local
         * optimistic state until a `?status=archived` filter ships.
         */
        suspend fun userPosts(
            userId: String,
            limit: Int = 50,
        ): NetworkResult<MyPostsResponse> = safeApiCall { api.userPosts(userId, limit) }

        /** `DELETE /api/posts/:id`. */
        suspend fun deletePost(id: String): NetworkResult<Unit> = safeApiCall { api.deletePost(id) }
    }
