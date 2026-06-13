package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.geo.GeoAutocompleteResponse
import app.pantopus.android.data.api.models.geo.GeoResolveRequest
import app.pantopus.android.data.api.models.geo.GeoResolveResponse
import app.pantopus.android.data.api.models.geo.GeoReverseResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Geo endpoints — route `backend/routes/geo.js`. */
interface GeoApi {
    /**
     * Address typeahead (Mapbox-backed). Suggestions carry a GeoJSON
     * `[lng, lat]` center; resolve one via [resolve].
     * Route `backend/routes/geo.js:39`.
     */
    @GET("api/geo/autocomplete")
    suspend fun autocomplete(
        @Query("q") query: String,
    ): GeoAutocompleteResponse

    /**
     * Resolve a suggestion to a normalized address.
     * Route `backend/routes/geo.js:120`.
     */
    @POST("api/geo/resolve")
    suspend fun resolve(
        @Body body: GeoResolveRequest,
    ): GeoResolveResponse

    /** `GET /api/geo/reverse?lat=&lon=` — route `backend/routes/geo.js:185`. */
    @GET("api/geo/reverse")
    suspend fun reverse(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
    ): GeoReverseResponse
}
