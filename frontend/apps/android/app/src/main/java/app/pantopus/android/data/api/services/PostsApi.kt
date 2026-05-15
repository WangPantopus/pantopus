package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.feed.FeedResponse
import app.pantopus.android.data.api.models.posts.PostCommentCreateResponse
import app.pantopus.android.data.api.models.posts.PostCommentRequest
import app.pantopus.android.data.api.models.posts.PostCommentsResponse
import app.pantopus.android.data.api.models.posts.PostDetailResponse
import app.pantopus.android.data.api.models.posts.PostLikeResponse
import retrofit2.http.Body
import retrofit2.http.GET
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
}
