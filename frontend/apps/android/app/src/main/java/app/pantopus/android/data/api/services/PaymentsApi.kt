package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.payments.AddCardSheetParamsDto
import app.pantopus.android.data.api.models.payments.PaymentMethodAckResponse
import app.pantopus.android.data.api.models.payments.PaymentMethodsResponse
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Stripe payment-methods routes from `backend/routes/pays.js` (mounted at
 * `/api/payments`). Phase 3 (3A) wires the Settings → Payments methods
 * card. Connect onboarding, payouts, checkout and tips are 3B/3C/3D.
 */
interface PaymentsApi {
    /** `GET /api/payments/methods` — route `backend/routes/pays.js:701`. */
    @GET("api/payments/methods")
    suspend fun methods(): PaymentMethodsResponse

    /**
     * `POST /api/payments/payment-sheet-add-card` — route
     * `backend/routes/pays.js:1095`. SetupIntent params for the mobile
     * PaymentSheet "add a card" flow.
     */
    @POST("api/payments/payment-sheet-add-card")
    suspend fun addCardSheet(): AddCardSheetParamsDto

    /** `PUT /api/payments/methods/{id}/default` — route `backend/routes/pays.js:754`. */
    @PUT("api/payments/methods/{id}/default")
    suspend fun setDefault(
        @Path("id") id: String,
    ): PaymentMethodAckResponse

    /** `DELETE /api/payments/methods/{id}` — route `backend/routes/pays.js:776`. */
    @DELETE("api/payments/methods/{id}")
    suspend fun removeMethod(
        @Path("id") id: String,
    ): PaymentMethodAckResponse
}
