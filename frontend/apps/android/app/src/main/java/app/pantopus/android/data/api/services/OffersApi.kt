package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.offers.MyBidsResponse
import app.pantopus.android.data.api.models.offers.ReceivedOffersResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * T5.2.4 — cross-listing Offers screen wraps two existing gig-bid
 * endpoints. No new backend routes; the same shapes the web Offers
 * page already consumes.
 */
interface OffersApi {
    /**
     * `GET /api/gigs/my-bids` — route `backend/routes/gigs.js:1253`.
     * Returns bids the current user placed on other people's gigs.
     */
    @GET("api/gigs/my-bids")
    suspend fun myBids(
        @Query("limit") limit: Int = 100,
    ): MyBidsResponse

    /**
     * `GET /api/gigs/received-offers` — route
     * `backend/routes/gigs.js:1469`. Returns bids other people placed
     * on gigs the current user posted (or manages on behalf of a
     * business).
     */
    @GET("api/gigs/received-offers")
    suspend fun receivedOffers(
        @Query("limit") limit: Int = 100,
    ): ReceivedOffersResponse
}
