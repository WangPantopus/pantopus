package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.feed.FeedResponse
import app.pantopus.android.data.api.models.posts.MyPostsResponse
import app.pantopus.android.data.api.models.posts.PostCommentCreateResponse
import app.pantopus.android.data.api.models.posts.PostCommentRequest
import app.pantopus.android.data.api.models.posts.PostCommentsResponse
import app.pantopus.android.data.api.models.posts.PostCreateRequest
import app.pantopus.android.data.api.models.posts.PostCreateResponse
import app.pantopus.android.data.api.models.posts.PostDetailResponse
import app.pantopus.android.data.api.models.posts.PostLikeResponse
import app.pantopus.android.data.api.models.posts.PostUpdateRequest
import app.pantopus.android.data.api.models.posts.PostUpdateResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Feed / detail / reaction / comment routes from `backend/routes/posts.js`. */
interface PostsApi {
    /**
     * `GET /api/posts/feed` — paged feed for the Pulse tab. Route
     * `backend/routes/posts.js:1449`. `surface` is required; `place`
     * also requires `latitude`/`longitude`.
     */
    @GET("api/posts/feed")
    suspend fun feed(
        @Query("surface") surface: String = "place",
        @Query("latitude") latitude: Double? = null,
        @Query("longitude") longitude: Double? = null,
        @Query("radiusMiles") radiusMiles: Double? = null,
        @Query("postType") postType: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("cursorCreatedAt") cursorCreatedAt: String? = null,
        @Query("cursorId") cursorId: String? = null,
    ): FeedResponse

    /**
     * `POST /api/posts` — create a new post. Body keys are validated
     * by `createPostSchema` at `backend/routes/posts.js:196-300`.
     * Route `backend/routes/posts.js:862`.
     */
    @POST("api/posts")
    suspend fun createPost(
        @Body body: PostCreateRequest,
    ): PostCreateResponse

    /**
     * `PATCH /api/posts/:id` — author-only edit. Body keys are validated
     * by `updatePostSchema` at `backend/routes/posts.js:298-328`. Route
     * `backend/routes/posts.js:2428`.
     */
    @PATCH("api/posts/{id}")
    suspend fun updatePost(
        @Path("id") id: String,
        @Body body: PostUpdateRequest,
    ): PostUpdateResponse

    /** `GET /api/posts/:id` — route `backend/routes/posts.js:2354`. */
    @GET("api/posts/{id}")
    suspend fun detail(
        @Path("id") id: String,
    ): PostDetailResponse

    /** `POST /api/posts/:id/like` — route `backend/routes/posts.js:2375`. */
    @POST("api/posts/{id}/like")
    suspend fun toggleLike(
        @Path("id") id: String,
    ): PostLikeResponse

    /** `GET /api/posts/:id/comments` — route `backend/routes/posts.js:2520`. */
    @GET("api/posts/{id}/comments")
    suspend fun comments(
        @Path("id") id: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PostCommentsResponse

    /** `POST /api/posts/:id/comments` — route `backend/routes/posts.js:2431`. */
    @POST("api/posts/{id}/comments")
    suspend fun createComment(
        @Path("id") id: String,
        @Body body: PostCommentRequest,
    ): PostCommentCreateResponse

    /**
     * `GET /api/posts/user/:userId` — paged list of posts authored by a
     * user. Backend filters `archived_at IS NULL`, so the response only
     * carries active posts today. Route `backend/routes/posts.js:3016`.
     * Used by the My posts screen with the signed-in user's id.
     */
    @GET("api/posts/user/{userId}")
    suspend fun userPosts(
        @Path("userId") userId: String,
        @Query("limit") limit: Int = 50,
        @Query("cursorCreatedAt") cursorCreatedAt: String? = null,
        @Query("cursorId") cursorId: String? = null,
    ): MyPostsResponse

    /** `DELETE /api/posts/:id` — author-only. Route `backend/routes/posts.js:2483`. */
    @DELETE("api/posts/{id}")
    suspend fun deletePost(
        @Path("id") id: String,
    )
}
