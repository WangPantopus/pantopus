@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.identity

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Envelope from `GET /api/identity-center`. */
@JsonClass(generateAdapter = true)
data class IdentityCenterResponse(
    val privateAccount: PrivateAccountDto? = null,
    val localProfile: LocalProfileDto? = null,
    val audienceProfile: AudienceProfileDto? = null,
    val bridges: BridgesDto? = null,
    val homes: List<HomeIdentityDto>? = null,
    val businessProfiles: List<BusinessIdentityDto>? = null,
    val personaCount: Int? = null,
    val blockCounts: BlockCountsDto? = null,
)

@JsonClass(generateAdapter = true)
data class PrivateAccountDto(
    val id: String,
    val email: String? = null,
    val name: String? = null,
    val verified: Boolean? = null,
    @Json(name = "profile_picture_url") val profilePictureUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class LocalProfileDto(
    val id: String,
    val handle: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "post_count") val postCount: Int? = null,
    @Json(name = "connection_count") val connectionCount: Int? = null,
    val verified: Boolean? = null,
    val locality: String? = null,
)

@JsonClass(generateAdapter = true)
data class AudienceProfileDto(
    val id: String,
    val handle: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "follower_count") val followerCount: Int? = null,
    @Json(name = "post_cadence") val postCadence: String? = null,
    val status: String? = null,
)

@JsonClass(generateAdapter = true)
data class BridgesDto(
    @Json(name = "show_persona_on_local") val showPersonaOnLocal: Boolean? = null,
    @Json(name = "show_local_on_persona") val showLocalOnPersona: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class HomeIdentityDto(
    val id: String,
    val name: String? = null,
    val city: String? = null,
    val state: String? = null,
    @Json(name = "primary_photo_url") val primaryPhotoUrl: String? = null,
    val role: String? = null,
)

@JsonClass(generateAdapter = true)
data class BusinessIdentityDto(
    val id: String,
    @Json(name = "display_name") val displayName: String? = null,
    val role: String? = null,
    @Json(name = "is_active") val isActive: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class BlockCountsDto(
    val personal: Int? = null,
    val audience: Int? = null,
)

/** Body for `PATCH /api/identity-center/bridges/:personaId`. */
@JsonClass(generateAdapter = true)
data class UpdateBridgesBody(
    @Json(name = "show_persona_on_local") val showPersonaOnLocal: Boolean? = null,
    @Json(name = "show_local_on_persona") val showLocalOnPersona: Boolean? = null,
)

/** Acknowledgement envelope from the PATCH. */
@JsonClass(generateAdapter = true)
data class BridgesEchoResponse(
    val bridges: BridgesDto? = null,
)
