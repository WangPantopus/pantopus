package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.gigs.BoostGigResponse
import app.pantopus.android.data.api.models.gigs.CancelGigBody
import app.pantopus.android.data.api.models.gigs.CompleteGigResponse
import app.pantopus.android.data.api.models.gigs.CreateGigBody
import app.pantopus.android.data.api.models.gigs.CreateGigResponse
import app.pantopus.android.data.api.models.gigs.GigBidAcceptResponse
import app.pantopus.android.data.api.models.gigs.GigBidsResponse
import app.pantopus.android.data.api.models.gigs.AnswerGigQuestionBody
import app.pantopus.android.data.api.models.gigs.AskGigQuestionBody
import app.pantopus.android.data.api.models.gigs.GigChatRoomResponse
import app.pantopus.android.data.api.models.gigs.GigDetailResponse
import app.pantopus.android.data.api.models.gigs.GigQuestionMutationResponse
import app.pantopus.android.data.api.models.gigs.GigQuestionsResponse
import app.pantopus.android.data.api.models.gigs.GigSaveResponse
import app.pantopus.android.data.api.models.gigs.GigsInBoundsResponse
import app.pantopus.android.data.api.models.gigs.GigsListResponse
import app.pantopus.android.data.api.models.gigs.MarkCompletedBody
import app.pantopus.android.data.api.models.gigs.MarkCompletedResponse
import app.pantopus.android.data.api.models.gigs.MyGigsResponse
import app.pantopus.android.data.api.models.gigs.PlaceBidBody
import app.pantopus.android.data.api.models.gigs.PlaceBidResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Gig endpoints from `backend/routes/gigs.js`. Mounted at `/api/gigs`
 * (see `backend/app.js:308`).
 */
@Suppress("TooManyFunctions")
interface GigsApi {
    /**
     * `GET /api/gigs` — paginated browse with category + sort filters.
     * Excludes the caller's own gigs by default.
     */
    @GET("api/gigs")
    suspend fun list(
        @Query("category") category: String? = null,
        @Query("sort") sort: String? = null,
        @Query("latitude") latitude: Double? = null,
        @Query("longitude") longitude: Double? = null,
        @Query("radiusMiles") radiusMiles: Double? = null,
        @Query("includeRemote") includeRemote: Boolean? = null,
        @Query("minPrice") minPrice: Double? = null,
        @Query("maxPrice") maxPrice: Double? = null,
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): GigsListResponse

    /** `GET /api/gigs/nearby` — radius search in meters. */
    @GET("api/gigs/nearby")
    suspend fun nearby(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radius") radiusMeters: Int = 5_000,
        @Query("status") status: String = "open",
        @Query("limit") limit: Int = 20,
    ): GigsListResponse

    /** `GET /api/gigs/in-bounds` — map-viewport pins for T2.4. */
    @GET("api/gigs/in-bounds")
    suspend fun inBounds(
        @Query("min_lat") minLat: Double,
        @Query("min_lon") minLon: Double,
        @Query("max_lat") maxLat: Double,
        @Query("max_lon") maxLon: Double,
        @Query("status") status: String = "open",
        @Query("category") category: String? = null,
        @Query("includeRemote") includeRemote: Boolean? = null,
    ): GigsInBoundsResponse

    /** `GET /api/gigs/:id` — single-gig detail wrapper. */
    @GET("api/gigs/{id}")
    suspend fun detail(
        @Path("id") id: String,
    ): GigDetailResponse

    /** `GET /api/gigs/:gigId/bids` — owner-only. */
    @GET("api/gigs/{gigId}/bids")
    suspend fun bids(
        @Path("gigId") gigId: String,
    ): GigBidsResponse

    /** `GET /api/gigs/:gigId/chat-room` — get-or-create gig chat room. */
    @GET("api/gigs/{gigId}/chat-room")
    suspend fun chatRoom(
        @Path("gigId") gigId: String,
    ): GigChatRoomResponse

    /** `GET /api/gigs/:gigId/questions` — structured Q&A (public). */
    @GET("api/gigs/{gigId}/questions")
    suspend fun questions(
        @Path("gigId") gigId: String,
    ): GigQuestionsResponse

    /** `POST /api/gigs/:gigId/questions` — ask a question. */
    @POST("api/gigs/{gigId}/questions")
    suspend fun askQuestion(
        @Path("gigId") gigId: String,
        @Body body: AskGigQuestionBody,
    ): GigQuestionMutationResponse

    /** `POST /api/gigs/:gigId/questions/{questionId}/answer` — poster answers. */
    @POST("api/gigs/{gigId}/questions/{questionId}/answer")
    suspend fun answerQuestion(
        @Path("gigId") gigId: String,
        @Path("questionId") questionId: String,
        @Body body: AnswerGigQuestionBody,
    ): GigQuestionMutationResponse

    /** `POST /api/gigs/:gigId/bids` — place a bid. */
    @POST("api/gigs/{gigId}/bids")
    suspend fun placeBid(
        @Path("gigId") gigId: String,
        @Body body: PlaceBidBody,
    ): PlaceBidResponse

    /** `POST /api/gigs/:gigId/bids/:bidId/accept` — poster accepts a bid. */
    @POST("api/gigs/{gigId}/bids/{bidId}/accept")
    suspend fun acceptBid(
        @Path("gigId") gigId: String,
        @Path("bidId") bidId: String,
    ): GigBidAcceptResponse

    /** `POST /api/gigs/:gigId/bids/:bidId/finalize-accept` after PaymentSheet. */
    @POST("api/gigs/{gigId}/bids/{bidId}/finalize-accept")
    suspend fun finalizeAcceptBid(
        @Path("gigId") gigId: String,
        @Path("bidId") bidId: String,
    ): GigBidAcceptResponse

    /** `POST /api/gigs/:gigId/bids/:bidId/abort-accept` after cancel/decline. */
    @POST("api/gigs/{gigId}/bids/{bidId}/abort-accept")
    suspend fun abortAcceptBid(
        @Path("gigId") gigId: String,
        @Path("bidId") bidId: String,
    ): GigBidAcceptResponse

    /** `POST /api/gigs/:id/save` — bookmark. */
    @POST("api/gigs/{id}/save")
    suspend fun save(
        @Path("id") id: String,
    ): GigSaveResponse

    /** `DELETE /api/gigs/:id/save` — un-bookmark. */
    @DELETE("api/gigs/{id}/save")
    suspend fun unsave(
        @Path("id") id: String,
    ): GigSaveResponse

    /**
     * `POST /api/gigs/:gigId/mark-completed` — assigned worker marks
     * the gig done. Route `backend/routes/gigs.js:5926`. T5.3.1 sends
     * only the optional `note`; the full backend shape also accepts
     * photos + checklist for a future PR.
     */
    @POST("api/gigs/{gigId}/mark-completed")
    suspend fun markCompleted(
        @Path("gigId") gigId: String,
        @Body body: MarkCompletedBody,
    ): MarkCompletedResponse

    /**
     * `GET /api/gigs/my-gigs` — list the caller's posted gigs with
     * inlined `bid_count`, `top_bid_amount`, `top_bidders[≤3]`, and
     * boost timestamps. Route `backend/routes/gigs.js:1169`.
     */
    @GET("api/gigs/my-gigs")
    suspend fun myGigs(
        @Query("limit") limit: Int = 100,
        @Query("status") status: String? = null,
    ): MyGigsResponse

    /**
     * `POST /api/gigs/:gigId/boost` — poster promotes their gig in the
     * feed for 24h. Route added in T5.3.2.
     */
    @POST("api/gigs/{gigId}/boost")
    suspend fun boostGig(
        @Path("gigId") gigId: String,
    ): BoostGigResponse

    /**
     * `POST /api/gigs/:gigId/complete` — poster confirms completion.
     * Alias of `/confirm-completion`. Route `backend/routes/gigs.js:6170`.
     * Distinct from `markCompleted(...)` which is the worker-only
     * `/mark-completed` route.
     */
    @POST("api/gigs/{gigId}/complete")
    suspend fun completeGigAsPoster(
        @Path("gigId") gigId: String,
    ): CompleteGigResponse

    /**
     * `POST /api/gigs/:gigId/cancel` — poster cancels the gig. Route
     * `backend/routes/gigs.js:6233`.
     */
    @POST("api/gigs/{gigId}/cancel")
    suspend fun cancelGig(
        @Path("gigId") gigId: String,
        @Body body: CancelGigBody,
    ): CompleteGigResponse

    /**
     * `POST /api/gigs` — create a new gig. Route
     * `backend/routes/gigs.js:841`. The full schema accepts ~40 fields;
     * the Post-a-Task wizard sends the eight required ones plus a few
     * Magic Task tags.
     */
    @POST("api/gigs")
    suspend fun createGig(
        @Body body: CreateGigBody,
    ): CreateGigResponse
}
