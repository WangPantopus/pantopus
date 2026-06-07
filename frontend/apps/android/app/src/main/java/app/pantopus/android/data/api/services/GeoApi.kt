package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.geo.GeoReverseResponse
import retrofit2.http.GET
import retrofit2.http.Query

/** Geo endpoints — route `backend/routes/geo.js`. */
interface GeoApi {
    /** `GET /api/geo/reverse?lat=&lon=` */
    @GET("api/geo/reverse")
    suspend fun reverse(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
    ): GeoReverseResponse
}
