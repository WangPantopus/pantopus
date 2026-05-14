package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.listings.ListingsInBoundsResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Listings endpoints from `backend/routes/listings.js`. T2.4 only wires
 * the map-viewport read — full browse / detail land in T2.5.
 *
 * Note: this route uses `south/west/north/east` (NOT `min_lat/min_lon/…`).
 */
interface ListingsApi {
    /** `GET /api/listings/in-bounds` — map-viewport pins. */
    @GET("api/listings/in-bounds")
    suspend fun inBounds(
        @Query("south") south: Double,
        @Query("west") west: Double,
        @Query("north") north: Double,
        @Query("east") east: Double,
        @Query("category") category: String? = null,
        @Query("layer") layer: String? = null,
        @Query("limit") limit: Int = 100,
    ): ListingsInBoundsResponse
}
