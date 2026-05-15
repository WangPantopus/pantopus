package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.audience.AudienceListResponse
import app.pantopus.android.data.api.models.audience.MembershipStatsResponse
import app.pantopus.android.data.api.models.audience.PersonaMeResponse
import app.pantopus.android.data.api.models.audience.PersonaPostsResponse
import app.pantopus.android.data.api.models.audience.PersonaThreadsResponse
import app.pantopus.android.data.api.models.audience.PersonaTiersResponse
import app.pantopus.android.data.api.models.audience.PublishUpdateBody
import app.pantopus.android.data.api.models.audience.PublishUpdateResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Backs the T3.3 Public Profile management screen. Backend keeps the
 * legacy `persona` / `broadcast` / `audience` names on the wire; UI
 * strings the user sees say "Public Profile" / "Updates" / "Followers"
 * per `docs/identity-firewall-ui-ux-redesign-2026-05-06.md`.
 */
interface AudienceProfileApi {
    /** `GET /api/personas/me` — owner persona + primary broadcast channel.
     *  Route `backend/routes/personas.js:367`. */
    @GET("api/personas/me")
    suspend fun me(): PersonaMeResponse

    /** `GET /api/personas/me/audience` — fan list + counts by tier.
     *  Route `backend/routes/personas.js:649`. */
    @GET("api/personas/me/audience")
    suspend fun audience(
        @Query("sort") sort: String? = null,
        @Query("status") status: String? = null,
        @Query("tier_rank") tierRank: Int? = null,
    ): AudienceListResponse

    /** `GET /api/personas/:handle/posts` — recent Update posts.
     *  Route `backend/routes/personas.js:1046`. */
    @GET("api/personas/{handle}/posts")
    suspend fun posts(
        @Path("handle") handle: String,
    ): PersonaPostsResponse

    /** `GET /api/personas/:handle/tiers` — tier ladder (chips).
     *  Route `backend/routes/personas.js:1111`. */
    @GET("api/personas/{handle}/tiers")
    suspend fun tiers(
        @Path("handle") handle: String,
    ): PersonaTiersResponse

    /** `GET /api/personas/:id/membership-stats` — owner-only counts by
     *  tier for analytics cells. Route `backend/routes/personas.js:1256`. */
    @GET("api/personas/{id}/membership-stats")
    suspend fun membershipStats(
        @Path("id") personaId: String,
    ): MembershipStatsResponse

    /** `GET /api/personas/:id/dms/threads` — owner inbox of fan threads.
     *  Route `backend/routes/personaDms.js:185`. */
    @GET("api/personas/{id}/dms/threads")
    suspend fun threads(
        @Path("id") personaId: String,
    ): PersonaThreadsResponse

    /** `POST /api/broadcast/channels/:channelId/messages` — publish a
     *  new Update. Route `backend/routes/broadcastChannels.js:450`. */
    @POST("api/broadcast/channels/{channelId}/messages")
    suspend fun publishUpdate(
        @Path("channelId") channelId: String,
        @Body body: PublishUpdateBody,
    ): PublishUpdateResponse
}
