package app.pantopus.android.data.listings

import app.pantopus.android.data.api.models.listings.ListingsInBoundsResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.ListingsApi
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps the listings endpoints in the [NetworkResult] taxonomy. */
@Singleton
class ListingsRepository
    @Inject
    constructor(
        private val api: ListingsApi,
    ) {
        suspend fun inBounds(
            south: Double,
            west: Double,
            north: Double,
            east: Double,
            category: String? = null,
        ): NetworkResult<ListingsInBoundsResponse> = safeApiCall { api.inBounds(south, west, north, east, category) }
    }
