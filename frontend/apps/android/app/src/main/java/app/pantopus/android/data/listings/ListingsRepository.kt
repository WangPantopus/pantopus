package app.pantopus.android.data.listings

import app.pantopus.android.data.api.models.listings.CreateListingRequest
import app.pantopus.android.data.api.models.listings.CreateListingResponse
import app.pantopus.android.data.api.models.listings.ListingDetailResponse
import app.pantopus.android.data.api.models.listings.ListingSaveResponse
import app.pantopus.android.data.api.models.listings.ListingsBrowseResponse
import app.pantopus.android.data.api.models.listings.ListingsCategoriesResponse
import app.pantopus.android.data.api.models.listings.ListingsInBoundsResponse
import app.pantopus.android.data.api.models.listings.ListingsNearbyResponse
import app.pantopus.android.data.api.models.listings.MessageListingBody
import app.pantopus.android.data.api.models.listings.MessageListingResponse
import app.pantopus.android.data.api.models.listings.MyListingsResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.ListingsApi
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps the `/api/listings` endpoints in the [NetworkResult] taxonomy. */
@Singleton
class ListingsRepository
    @Inject
    constructor(
        private val api: ListingsApi,
    ) {
        suspend fun nearby(
            latitude: Double,
            longitude: Double,
            radiusMiles: Double? = null,
            layer: String? = null,
            isFree: Boolean? = null,
            search: String? = null,
            sort: String = "newest",
            limit: Int = 30,
            offset: Int = 0,
        ): NetworkResult<ListingsNearbyResponse> =
            safeApiCall {
                api.nearby(
                    latitude = latitude,
                    longitude = longitude,
                    radiusMiles = radiusMiles,
                    layer = layer,
                    isFree = isFree,
                    search = search,
                    sort = sort,
                    limit = limit,
                    offset = offset,
                )
            }

        suspend fun browse(
            south: Double,
            west: Double,
            north: Double,
            east: Double,
            layer: String? = null,
            isFree: Boolean? = null,
            search: String? = null,
            sort: String = "newest",
            cursor: String? = null,
            limit: Int = 30,
        ): NetworkResult<ListingsBrowseResponse> =
            safeApiCall {
                api.browse(
                    south = south,
                    west = west,
                    north = north,
                    east = east,
                    layer = layer,
                    isFree = isFree,
                    search = search,
                    sort = sort,
                    cursor = cursor,
                    limit = limit,
                )
            }

        suspend fun inBounds(
            south: Double,
            west: Double,
            north: Double,
            east: Double,
            category: String? = null,
        ): NetworkResult<ListingsInBoundsResponse> = safeApiCall { api.inBounds(south, west, north, east, category) }

        suspend fun categories(): NetworkResult<ListingsCategoriesResponse> = safeApiCall { api.categories() }

        /** Wraps `POST /api/listings`. Used by the Snap & Sell wizard. */
        suspend fun create(request: CreateListingRequest): NetworkResult<CreateListingResponse> =
            safeApiCall { api.create(request) }

        suspend fun save(id: String): NetworkResult<ListingSaveResponse> = safeApiCall { api.save(id) }

        suspend fun unsave(id: String): NetworkResult<ListingSaveResponse> = safeApiCall { api.unsave(id) }

        suspend fun detail(id: String): NetworkResult<ListingDetailResponse> = safeApiCall { api.detail(id) }

        suspend fun messageListing(
            id: String,
            body: MessageListingBody,
        ): NetworkResult<MessageListingResponse> = safeApiCall { api.messageListing(id, body) }

        /**
         * T6.3f / P14 — backs the My listings screen. Optional `status`
         * filters server-side; the screen typically loads all and buckets
         * client-side so tab counts stay honest.
         */
        suspend fun myListings(
            status: String? = null,
            limit: Int = 100,
            offset: Int = 0,
        ): NetworkResult<MyListingsResponse> = safeApiCall { api.myListings(status, limit, offset) }
    }
