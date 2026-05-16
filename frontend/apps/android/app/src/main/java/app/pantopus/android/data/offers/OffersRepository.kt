package app.pantopus.android.data.offers

import app.pantopus.android.data.api.models.offers.MyBidsResponse
import app.pantopus.android.data.api.models.offers.ReceivedOffersResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.OffersApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around [OffersApi] returning the typed [NetworkResult]
 * taxonomy. No optimistic mutations or caching live here — the
 * Offers screen only reads.
 */
@Singleton
class OffersRepository
    @Inject
    constructor(
        private val api: OffersApi,
    ) {
        suspend fun myBids(limit: Int = 100): NetworkResult<MyBidsResponse> = safeApiCall { api.myBids(limit) }

        suspend fun receivedOffers(limit: Int = 100): NetworkResult<ReceivedOffersResponse> = safeApiCall { api.receivedOffers(limit) }
    }
