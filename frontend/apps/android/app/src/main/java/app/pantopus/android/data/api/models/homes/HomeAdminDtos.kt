package app.pantopus.android.data.api.models.homes

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs for the owner/admin home-administration surface. Route citations
 * live on [app.pantopus.android.data.api.services.HomeAdminApi]:
 *
 *  - `DELETE /api/homes/:id`                                — `home.js:3191`
 *  - `GET    /api/homes/:id/me`                             — `homeIam.js:51`
 *  - `POST   /api/homes/:id/members/:userId/role`           — `homeIam.js:212`
 *  - `GET    /api/homes/:id/household-access-requests`      — `home.js:2671`
 *  - `POST   …/household-access-requests/:id/approve`       — `home.js:2714`
 *  - `POST   …/household-access-requests/:id/reject`        — `home.js:2831`
 */

/** `{ message }` from `DELETE /api/homes/:id`. */
@JsonClass(generateAdapter = true)
data class DeleteHomeResponse(
    val message: String? = null,
)

/**
 * The viewer's own access record for a home (`GET /:id/me`). Only the
 * fields the Members screen needs are modelled; the handler emits more
 * (challenge/claim windows, postcard context) decoded elsewhere.
 */
@JsonClass(generateAdapter = true)
data class HomeAccessDto(
    val hasAccess: Boolean = false,
    /** Verified/legacy owner OR IAM `role_base == "owner"`. */
    @Json(name = "is_owner") val isOwner: Boolean = false,
    /** One of the `ROLE_RANK` keys, or null when access was denied. */
    @Json(name = "role_base") val roleBase: String? = null,
    /** Raw IAM permission strings; `members.manage` gates the roster. */
    val permissions: List<String> = emptyList(),
    @Json(name = "can_manage_home") val canManageHome: Boolean = false,
    @Json(name = "can_manage_access") val canManageAccess: Boolean = false,
) {
    /**
     * Mirrors `canReviewHouseholdAccessRequests` (`home.js:219`) and the
     * `members.manage` gate on the change-role route (`homeIam.js:218`).
     */
    val canManageMembers: Boolean
        get() = isOwner || permissions.contains("members.manage")
}

/**
 * Body for `POST /:id/members/:userId/role`. The handler accepts
 * `preset_key` or `role_base`; we always send `role_base` so the
 * assignable list is the backend's `ROLE_RANK` vocabulary rather than a
 * preset table that may be empty.
 */
@JsonClass(generateAdapter = true)
data class ChangeMemberRoleRequest(
    @Json(name = "role_base") val roleBase: String,
)

/** `{ message, role_base }` from the change-role route. */
@JsonClass(generateAdapter = true)
data class ChangeMemberRoleResponse(
    val message: String? = null,
    @Json(name = "role_base") val roleBase: String? = null,
)

/** Joined `User` record on a household-access request row. */
@JsonClass(generateAdapter = true)
data class HouseholdAccessRequesterDto(
    val id: String,
    val username: String? = null,
    val name: String? = null,
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "last_name") val lastName: String? = null,
    @Json(name = "profile_picture_url") val profilePictureUrl: String? = null,
)

/**
 * One row from `GET /:id/household-access-requests`. The handler
 * `select('*')`s `HomeHouseholdAccessRequest` and joins the requester.
 */
@JsonClass(generateAdapter = true)
data class HouseholdAccessRequestDto(
    val id: String,
    @Json(name = "home_id") val homeId: String,
    @Json(name = "requester_user_id") val requesterUserId: String,
    /** `owner / resident / household_member / guest`. */
    @Json(name = "requested_identity") val requestedIdentity: String,
    /** `pending / approved / rejected / cancelled`. */
    val status: String,
    @Json(name = "created_at") val createdAt: String? = null,
    val requester: HouseholdAccessRequesterDto? = null,
)

/**
 * Title-case label for `requested_identity`, matching the RN vocabulary
 * in `src/app/homes/[id]/members/index.tsx:26`.
 */
fun HouseholdAccessRequestDto.requestedIdentityLabel(): String =
    when (requestedIdentity) {
        "owner" -> "Owner"
        "resident" -> "Resident"
        "household_member" -> "Household member"
        "guest" -> "Guest"
        else -> requestedIdentity
    }

/** Display-name resolution order mirrors RN's `requesterDisplayName`. */
fun HouseholdAccessRequestDto.requesterDisplayName(): String {
    val user = requester ?: return "Unknown user"
    if (!user.name.isNullOrEmpty()) return user.name
    val parts = listOfNotNull(user.firstName, user.lastName).filter { it.isNotEmpty() }
    if (parts.isNotEmpty()) return parts.joinToString(" ")
    if (!user.username.isNullOrEmpty()) return "@${user.username}"
    return "Unknown user"
}

/** `{ requests }` envelope. */
@JsonClass(generateAdapter = true)
data class HouseholdAccessRequestsResponse(
    val requests: List<HouseholdAccessRequestDto> = emptyList(),
)

/** `{ ok, message }` from approve / reject. */
@JsonClass(generateAdapter = true)
data class HouseholdAccessRequestActionResponse(
    val ok: Boolean = false,
    val message: String? = null,
)
