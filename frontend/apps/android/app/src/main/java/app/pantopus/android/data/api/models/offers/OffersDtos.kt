package app.pantopus.android.data.api.models.offers

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * `GET /api/gigs/my-bids` envelope — route `backend/routes/gigs.js:1253`.
 *
 * Sent perspective. Includes the bid's counter / expiry fields so the
 * client can derive the design's `countered` / `expiring` chips.
 */
@JsonClass(generateAdapter = true)
data class MyBidsResponse(
    val bids: List<BidDto> = emptyList(),
    val total: Int? = null,
)

/**
 * `GET /api/gigs/received-offers` envelope — route
 * `backend/routes/gigs.js:1469`.
 *
 * Received perspective. Inlines the bidder's identity card so the row
 * subtitle can render "From {bidder name}".
 */
@JsonClass(generateAdapter = true)
data class ReceivedOffersResponse(
    val offers: List<BidDto> = emptyList(),
    val total: Int? = null,
)

/**
 * One bid as returned by both endpoints. The Sent endpoint omits
 * `bidder`; the Received endpoint omits `expires_at` / `counter_*`. Both
 * are nullable here so the same DTO works for either tab.
 */
@JsonClass(generateAdapter = true)
data class BidDto(
    val id: String,
    @Json(name = "gig_id") val gigId: String? = null,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "bid_amount") val bidAmount: Double? = null,
    val message: String? = null,
    @Json(name = "proposed_time") val proposedTime: String? = null,
    val status: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "counter_amount") val counterAmount: Double? = null,
    @Json(name = "counter_status") val counterStatus: String? = null,
    @Json(name = "countered_at") val counteredAt: String? = null,
    @Json(name = "withdrawn_at") val withdrawnAt: String? = null,
    val gig: BidGigDto? = null,
    val bidder: BidderUserDto? = null,
)

/** Inlined gig summary on each bid. */
@JsonClass(generateAdapter = true)
data class BidGigDto(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val price: Double? = null,
    val category: String? = null,
    val status: String? = null,
    @Json(name = "user_id") val userId: String? = null,
)

/** Inlined bidder identity on Received rows. */
@JsonClass(generateAdapter = true)
data class BidderUserDto(
    val id: String,
    val username: String? = null,
    val name: String? = null,
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "profile_picture_url") val profilePictureUrl: String? = null,
    val city: String? = null,
    val state: String? = null,
)
