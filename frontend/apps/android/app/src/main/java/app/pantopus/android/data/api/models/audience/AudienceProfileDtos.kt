@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.audience

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// GET /api/personas/me

@JsonClass(generateAdapter = true)
data class PersonaMeResponse(
    val persona: PersonaSummaryDto? = null,
    val channel: BroadcastChannelDto? = null,
)

@JsonClass(generateAdapter = true)
data class PersonaSummaryDto(
    val id: String,
    val handle: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bio: String? = null,
    val category: String? = null,
    val audienceLabel: String? = null,
    val followerCount: Int? = null,
    val postCount: Int? = null,
)

@JsonClass(generateAdapter = true)
data class BroadcastChannelDto(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val status: String? = null,
)

// GET /api/personas/me/audience

@JsonClass(generateAdapter = true)
data class AudienceListResponse(
    val persona: PersonaSummaryDto? = null,
    val items: List<FanDto> = emptyList(),
    val counts: AudienceCountsDto = AudienceCountsDto(),
)

@JsonClass(generateAdapter = true)
data class AudienceCountsDto(
    val totalActive: Int? = null,
    val pending: Int? = null,
    val byTier: Map<String, Int>? = null,
)

/**
 * One follower row. The backend's creator-side serializer emits
 * camelCase keys (`fanHandle`, not `fan_handle`) so no `@Json` mapping
 * is needed. `id` is sourced from `membershipId` when present.
 */
@JsonClass(generateAdapter = true)
data class FanDto(
    val membershipId: String? = null,
    val fanHandle: String? = null,
    val fanDisplayName: String? = null,
    val fanAvatarUrl: String? = null,
    val status: String? = null,
    val tier: FanTierBadgeDto? = null,
    val verifiedLocal: Boolean? = null,
    val tenureMonths: Int? = null,
    val joinedMonth: String? = null,
    val cancelAtPeriodEnd: Boolean? = null,
) {
    val id: String get() = membershipId ?: fanHandle ?: ""
}

@JsonClass(generateAdapter = true)
data class FanTierBadgeDto(
    val rank: Int? = null,
    val name: String? = null,
)

// GET /api/personas/:handle/posts

@JsonClass(generateAdapter = true)
data class PersonaPostsResponse(
    val posts: List<PersonaPostDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PersonaPostDto(
    val id: String,
    val body: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    val visibility: String? = null,
    @Json(name = "target_tier_rank") val targetTierRank: Int? = null,
    @Json(name = "delivered_count") val deliveredCount: Int? = null,
    @Json(name = "read_count") val readCount: Int? = null,
    @Json(name = "media_urls") val mediaUrls: List<String>? = null,
)

// GET /api/personas/:handle/tiers

@JsonClass(generateAdapter = true)
data class PersonaTiersResponse(
    val tiers: List<PersonaTierDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PersonaTierDto(
    val id: String,
    val rank: Int,
    val name: String,
    val description: String? = null,
    val priceCents: Int? = null,
    val currency: String? = null,
)

// GET /api/personas/:id/membership-stats

@JsonClass(generateAdapter = true)
data class MembershipStatsResponse(
    val counts: MembershipStatsCountsDto = MembershipStatsCountsDto(),
)

@JsonClass(generateAdapter = true)
data class MembershipStatsCountsDto(
    val followers: Int? = null,
    val members: Int? = null,
    val insiders: Int? = null,
    val direct: Int? = null,
)

// GET /api/personas/:id/dms/threads

@JsonClass(generateAdapter = true)
data class PersonaThreadsResponse(
    val threads: List<PersonaThreadDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PersonaThreadDto(
    val id: String,
    val membershipId: String? = null,
    val fanHandle: String? = null,
    val fanDisplayName: String? = null,
    val fanAvatarUrl: String? = null,
    val tier: FanTierBadgeDto? = null,
    val lastMessagePreview: String? = null,
    val lastMessageAt: String? = null,
    val unreadCount: Int? = null,
    val status: String? = null,
)

// POST /api/broadcast/channels/:id/messages

@JsonClass(generateAdapter = true)
data class PublishUpdateBody(
    val body: String,
    val visibility: String,
    @Json(name = "target_tier_rank") val targetTierRank: Int? = null,
)

@JsonClass(generateAdapter = true)
data class PublishUpdateResponse(
    val message: BroadcastMessageDto? = null,
)

@JsonClass(generateAdapter = true)
data class BroadcastMessageDto(
    val id: String? = null,
    val body: String? = null,
    val visibility: String? = null,
    @Json(name = "target_tier_rank") val targetTierRank: Int? = null,
    @Json(name = "created_at") val createdAt: String? = null,
)
