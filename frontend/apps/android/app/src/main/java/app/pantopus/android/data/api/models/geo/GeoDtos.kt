package app.pantopus.android.data.api.models.geo

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Normalized address from reverse geocode. Route: `backend/routes/geo.js`. */
@JsonClass(generateAdapter = true)
data class NormalizedAddress(
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zipcode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Json(name = "place_id") val placeId: String? = null,
    val verified: Boolean? = null,
    val source: String? = null,
) {
    val localityLabel: String
        get() =
            listOfNotNull(city, state)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(", ")
}

/** `GET /api/geo/reverse` envelope. */
@JsonClass(generateAdapter = true)
data class GeoReverseResponse(
    val normalized: NormalizedAddress,
)
