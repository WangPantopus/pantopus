package app.pantopus.android.data.gigs

import app.pantopus.android.data.api.models.gigs.AnswerGigQuestionBody
import app.pantopus.android.data.api.models.gigs.AskGigQuestionBody
import app.pantopus.android.data.api.models.gigs.BoostGigResponse
import app.pantopus.android.data.api.models.gigs.CancelGigBody
import app.pantopus.android.data.api.models.gigs.CompleteGigResponse
import app.pantopus.android.data.api.models.gigs.CreateGigBody
import app.pantopus.android.data.api.models.gigs.CreateGigResponse
import app.pantopus.android.data.api.models.gigs.DismissGigBody
import app.pantopus.android.data.api.models.gigs.GigActionSuccessResponse
import app.pantopus.android.data.api.models.gigs.GigBidAcceptResponse
import app.pantopus.android.data.api.models.gigs.GigBidsResponse
import app.pantopus.android.data.api.models.gigs.GigChatRoomResponse
import app.pantopus.android.data.api.models.gigs.GigDetailResponse
import app.pantopus.android.data.api.models.gigs.GigQuestionMutationResponse
import app.pantopus.android.data.api.models.gigs.GigQuestionsResponse
import app.pantopus.android.data.api.models.gigs.GigSaveResponse
import app.pantopus.android.data.api.models.gigs.GigsBrowseResponse
import app.pantopus.android.data.api.models.gigs.GigsInBoundsResponse
import app.pantopus.android.data.api.models.gigs.GigsListResponse
import app.pantopus.android.data.api.models.gigs.GigTemplatesResponse
import app.pantopus.android.data.api.models.gigs.HiddenCategoryBody
import app.pantopus.android.data.api.models.gigs.MagicDraftRequest
import app.pantopus.android.data.api.models.gigs.MagicDraftResponse
import app.pantopus.android.data.api.models.gigs.MagicPostBody
import app.pantopus.android.data.api.models.gigs.MagicPostResponse
import app.pantopus.android.data.api.models.gigs.MagicUndoResponse
import app.pantopus.android.data.api.models.gigs.MarkCompletedBody
import app.pantopus.android.data.api.models.gigs.MarkCompletedResponse
import app.pantopus.android.data.api.models.gigs.MyGigsResponse
import app.pantopus.android.data.api.models.gigs.PlaceBidBody
import app.pantopus.android.data.api.models.gigs.PlaceBidResponse
import app.pantopus.android.data.api.models.gigs.PriceBenchmarkResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.GigsApi
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps the `/api/gigs` endpoints in the [NetworkResult] taxonomy. */
@Suppress("TooManyFunctions")
@Singleton
class GigsRepository
    @Inject
    constructor(
        private val api: GigsApi,
    ) {
        @Suppress("LongParameterList")
        suspend fun list(
            category: String? = null,
            sort: String? = null,
            latitude: Double? = null,
            longitude: Double? = null,
            radiusMiles: Double? = null,
            limit: Int = 20,
            offset: Int = 0,
            search: String? = null,
            minPrice: Double? = null,
            maxPrice: Double? = null,
            scheduleType: String? = null,
            payType: String? = null,
        ): NetworkResult<GigsListResponse> =
            safeApiCall {
                api.list(
                    category = category,
                    sort = sort,
                    latitude = latitude,
                    longitude = longitude,
                    radiusMiles = radiusMiles,
                    minPrice = minPrice,
                    maxPrice = maxPrice,
                    scheduleType = scheduleType,
                    payType = payType,
                    search = search,
                    limit = limit,
                    offset = offset,
                )
            }

        /** `GET /api/gigs/browse` — pre-sectioned browse feed. `radiusMeters` null ⇒ server default (~100 mi). */
        suspend fun browse(
            lat: Double,
            lng: Double,
            radiusMeters: Int? = null,
        ): NetworkResult<GigsBrowseResponse> = safeApiCall { api.browse(lat, lng, radiusMeters) }

        /** `GET /api/gigs/price-benchmark` — category price percentiles for the composer hint. */
        suspend fun priceBenchmark(
            category: String,
            lat: Double? = null,
            lng: Double? = null,
        ): NetworkResult<PriceBenchmarkResponse> = safeApiCall { api.priceBenchmark(category, lat, lng) }

        /** `POST /api/gigs/:gigId/dismiss` — "Not interested". */
        suspend fun dismissGig(
            gigId: String,
            reason: String? = null,
        ): NetworkResult<GigActionSuccessResponse> = safeApiCall { api.dismissGig(gigId, DismissGigBody(reason = reason)) }

        /** `DELETE /api/gigs/:gigId/dismiss` — undo a dismissal. */
        suspend fun undoDismissGig(gigId: String): NetworkResult<GigActionSuccessResponse> = safeApiCall { api.undoDismissGig(gigId) }

        /** `POST /api/gigs/hidden-categories` — hide every gig of a category. */
        suspend fun hideCategory(category: String): NetworkResult<GigActionSuccessResponse> =
            safeApiCall { api.hideCategory(HiddenCategoryBody(category = category)) }

        /** `DELETE /api/gigs/hidden-categories/:category` — unhide. */
        suspend fun unhideCategory(category: String): NetworkResult<GigActionSuccessResponse> =
            safeApiCall { api.unhideCategory(category) }

        /** `POST /api/gigs/magic-draft` — NLP-parse the describe text. */
        suspend fun magicDraft(body: MagicDraftRequest): NetworkResult<MagicDraftResponse> = safeApiCall { api.magicDraft(body) }

        /** `POST /api/gigs/magic-post` — A12.8 wizard submission with an undo window. */
        suspend fun magicPost(body: MagicPostBody): NetworkResult<MagicPostResponse> = safeApiCall { api.magicPost(body) }

        /** `POST /api/gigs/:gigId/undo` — undo a just-posted gig within its window. */
        suspend fun undoMagicPost(gigId: String): NetworkResult<MagicUndoResponse> = safeApiCall { api.undoMagicPost(gigId) }

        /** `GET /api/gigs/templates/library` — smart-template chips (silent failure at call sites). */
        suspend fun templatesLibrary(): NetworkResult<GigTemplatesResponse> = safeApiCall { api.templatesLibrary() }

        suspend fun nearby(
            latitude: Double,
            longitude: Double,
            radiusMeters: Int = 5_000,
            limit: Int = 20,
        ): NetworkResult<GigsListResponse> = safeApiCall { api.nearby(latitude, longitude, radiusMeters, limit = limit) }

        suspend fun inBounds(
            minLat: Double,
            minLon: Double,
            maxLat: Double,
            maxLon: Double,
            category: String? = null,
        ): NetworkResult<GigsInBoundsResponse> = safeApiCall { api.inBounds(minLat, minLon, maxLat, maxLon, category = category) }

        suspend fun save(id: String): NetworkResult<GigSaveResponse> = safeApiCall { api.save(id) }

        suspend fun unsave(id: String): NetworkResult<GigSaveResponse> = safeApiCall { api.unsave(id) }

        suspend fun detail(id: String): NetworkResult<GigDetailResponse> = safeApiCall { api.detail(id) }

        suspend fun bids(gigId: String): NetworkResult<GigBidsResponse> = safeApiCall { api.bids(gigId) }

        suspend fun chatRoom(gigId: String): NetworkResult<GigChatRoomResponse> = safeApiCall { api.chatRoom(gigId) }

        suspend fun questions(gigId: String): NetworkResult<GigQuestionsResponse> = safeApiCall { api.questions(gigId) }

        suspend fun askQuestion(
            gigId: String,
            question: String,
        ): NetworkResult<GigQuestionMutationResponse> = safeApiCall { api.askQuestion(gigId, AskGigQuestionBody(question = question)) }

        suspend fun answerQuestion(
            gigId: String,
            questionId: String,
            answer: String,
        ): NetworkResult<GigQuestionMutationResponse> =
            safeApiCall {
                api.answerQuestion(gigId, questionId, AnswerGigQuestionBody(answer = answer))
            }

        suspend fun placeBid(
            gigId: String,
            body: PlaceBidBody,
        ): NetworkResult<PlaceBidResponse> = safeApiCall { api.placeBid(gigId, body) }

        suspend fun acceptBid(
            gigId: String,
            bidId: String,
        ): NetworkResult<GigBidAcceptResponse> = safeApiCall { api.acceptBid(gigId, bidId) }

        suspend fun finalizeAcceptBid(
            gigId: String,
            bidId: String,
        ): NetworkResult<GigBidAcceptResponse> = safeApiCall { api.finalizeAcceptBid(gigId, bidId) }

        suspend fun abortAcceptBid(
            gigId: String,
            bidId: String,
        ): NetworkResult<GigBidAcceptResponse> = safeApiCall { api.abortAcceptBid(gigId, bidId) }

        suspend fun markCompleted(
            gigId: String,
            note: String? = null,
            photos: List<String>? = null,
        ): NetworkResult<MarkCompletedResponse> =
            safeApiCall {
                api.markCompleted(gigId, MarkCompletedBody(note = note, photos = photos))
            }

        suspend fun myGigs(
            limit: Int = 100,
            status: String? = null,
        ): NetworkResult<MyGigsResponse> = safeApiCall { api.myGigs(limit, status) }

        suspend fun boostGig(gigId: String): NetworkResult<BoostGigResponse> = safeApiCall { api.boostGig(gigId) }

        suspend fun completeGigAsPoster(gigId: String): NetworkResult<CompleteGigResponse> = safeApiCall { api.completeGigAsPoster(gigId) }

        suspend fun cancelGig(
            gigId: String,
            reason: String? = null,
        ): NetworkResult<CompleteGigResponse> = safeApiCall { api.cancelGig(gigId, CancelGigBody(reason = reason)) }

        /** `POST /api/gigs` — create a new gig. */
        suspend fun create(body: CreateGigBody): NetworkResult<CreateGigResponse> = safeApiCall { api.createGig(body) }

        /** `PATCH /api/gigs/:id` — owner edits an open gig (A13.8 P4 edit mode). */
        suspend fun update(
            id: String,
            body: CreateGigBody,
        ): NetworkResult<CreateGigResponse> = safeApiCall { api.updateGig(id, body) }
    }
