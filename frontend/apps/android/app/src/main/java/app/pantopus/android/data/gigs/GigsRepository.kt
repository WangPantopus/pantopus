package app.pantopus.android.data.gigs

import app.pantopus.android.data.api.models.gigs.BoostGigResponse
import app.pantopus.android.data.api.models.gigs.CancelGigBody
import app.pantopus.android.data.api.models.gigs.CompleteGigResponse
import app.pantopus.android.data.api.models.gigs.GigBidsResponse
import app.pantopus.android.data.api.models.gigs.GigDetailResponse
import app.pantopus.android.data.api.models.gigs.GigSaveResponse
import app.pantopus.android.data.api.models.gigs.GigsInBoundsResponse
import app.pantopus.android.data.api.models.gigs.GigsListResponse
import app.pantopus.android.data.api.models.gigs.MarkCompletedBody
import app.pantopus.android.data.api.models.gigs.MarkCompletedResponse
import app.pantopus.android.data.api.models.gigs.MyGigsResponse
import app.pantopus.android.data.api.models.gigs.PlaceBidBody
import app.pantopus.android.data.api.models.gigs.PlaceBidResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.GigsApi
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps the `/api/gigs` endpoints in the [NetworkResult] taxonomy. */
@Singleton
class GigsRepository
    @Inject
    constructor(
        private val api: GigsApi,
    ) {
        suspend fun list(
            category: String? = null,
            sort: String? = null,
            latitude: Double? = null,
            longitude: Double? = null,
            radiusMiles: Double? = null,
            limit: Int = 20,
            offset: Int = 0,
        ): NetworkResult<GigsListResponse> =
            safeApiCall {
                api.list(
                    category = category,
                    sort = sort,
                    latitude = latitude,
                    longitude = longitude,
                    radiusMiles = radiusMiles,
                    limit = limit,
                    offset = offset,
                )
            }

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

        suspend fun placeBid(
            gigId: String,
            body: PlaceBidBody,
        ): NetworkResult<PlaceBidResponse> = safeApiCall { api.placeBid(gigId, body) }

        suspend fun markCompleted(
            gigId: String,
            note: String? = null,
        ): NetworkResult<MarkCompletedResponse> =
            safeApiCall {
                api.markCompleted(gigId, MarkCompletedBody(note = note))
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
    }
