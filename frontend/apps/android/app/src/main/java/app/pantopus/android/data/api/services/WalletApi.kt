package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.wallet.WalletBalanceResponse
import app.pantopus.android.data.api.models.wallet.WalletPendingReleaseResponse
import app.pantopus.android.data.api.models.wallet.WalletTransactionsResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Read-path wallet routes from `backend/routes/wallet.js`. P1-F wires the
 * READ surface only — `POST /api/wallet/withdraw` and any payout action are
 * Phase 3 (Stripe Connect).
 */
interface WalletApi {
    /** `GET /api/wallet` — route `backend/routes/wallet.js:55`. */
    @GET("api/wallet")
    suspend fun balance(): WalletBalanceResponse

    /** `GET /api/wallet/transactions` — route `backend/routes/wallet.js:124`. */
    @GET("api/wallet/transactions")
    suspend fun transactions(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): WalletTransactionsResponse

    /** `GET /api/wallet/pending-release` — route `backend/routes/wallet.js:160`. */
    @GET("api/wallet/pending-release")
    suspend fun pendingRelease(): WalletPendingReleaseResponse
}
