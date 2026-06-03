@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.membership

import com.squareup.moshi.JsonClass

/**
 * Decoder shapes for `GET /api/personas/:id/membership` — the fan-side view
 * of their own membership (A10.8). Built by `serializeMembershipForFan`
 * (`backend/serializers/identitySerializers.js:352`) wrapped in `stripNullish`,
 * so several keys may be absent — every field is optional. The membership
 * serializer emits camelCase keys, so no `@Json` mapping is needed. The cancel
 * route echoes the same envelope.
 */

@JsonClass(generateAdapter = true)
data class PersonaMembershipResponse(
    val membership: PersonaMembershipDto? = null,
)

@JsonClass(generateAdapter = true)
data class PersonaMembershipDto(
    val membershipId: String? = null,
    val persona: MembershipPersonaDto? = null,
    val tier: MembershipTierDto? = null,
    val status: String? = null,
    val cancelAtPeriodEnd: Boolean? = null,
    val currentPeriodStart: String? = null,
    val currentPeriodEnd: String? = null,
)

/** The persona the fan supports — shared `serializeAudienceProfileForViewer`. */
@JsonClass(generateAdapter = true)
data class MembershipPersonaDto(
    val id: String? = null,
    val handle: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val category: String? = null,
    val audienceLabel: String? = null,
    val followerCount: Int? = null,
    val credential: CredentialDto? = null,
)

@JsonClass(generateAdapter = true)
data class CredentialDto(
    val status: String? = null,
    val label: String? = null,
)

/** The fan's tier — perk fields drive the "What you get" benefit rows. */
@JsonClass(generateAdapter = true)
data class MembershipTierDto(
    val id: String? = null,
    val rank: Int? = null,
    val name: String? = null,
    val priceCents: Int? = null,
    val currency: String? = null,
    val billingInterval: String? = null,
    val msgThreadsPerPeriod: Int? = null,
    val creatorCanInitiateDm: Boolean? = null,
    val replyPolicy: String? = null,
)
