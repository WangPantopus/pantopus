package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.mailbox.AckResponse
import app.pantopus.android.data.api.models.mailbox.MailDetailResponse
import app.pantopus.android.data.api.models.mailbox.MailboxListResponse
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

/** V1 mailbox routes from `backend/routes/mailbox.js`. */
interface MailboxApi {
    /** `GET /api/mailbox` — route `backend/routes/mailbox.js:1306`. */
    @Suppress("LongParameterList")
    @GET("api/mailbox")
    suspend fun list(
        @Query("type") type: String? = null,
        @Query("viewed") viewed: Boolean? = null,
        @Query("archived") archived: Boolean = false,
        @Query("starred") starred: Boolean? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("scope") scope: String = "personal",
        @Query("homeId") homeId: String? = null,
    ): MailboxListResponse

    /** `GET /api/mailbox/:id` — route `backend/routes/mailbox.js:1466`. */
    @GET("api/mailbox/{id}")
    suspend fun detail(
        @Path("id") id: String,
    ): MailDetailResponse

    /** `PATCH /api/mailbox/:id/ack` — route `backend/routes/mailbox.js:2702`. */
    @PATCH("api/mailbox/{id}/ack")
    suspend fun acknowledge(
        @Path("id") id: String,
    ): AckResponse
}
