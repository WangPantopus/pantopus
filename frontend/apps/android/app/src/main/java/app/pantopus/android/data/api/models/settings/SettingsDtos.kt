@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.settings

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Envelope from `GET /api/privacy/settings`. */
@JsonClass(generateAdapter = true)
data class PrivacySettingsResponse(
    val settings: PrivacySettingsDto,
)

/** One privacy-settings row. All fields are nullable so the backend
 * can roll out new keys without breaking older clients. */
@JsonClass(generateAdapter = true)
data class PrivacySettingsDto(
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "search_visibility") val searchVisibility: String? = null,
    @Json(name = "address_precision") val addressPrecision: String? = null,
    @Json(name = "hide_from_search") val hideFromSearch: Boolean? = null,
    @Json(name = "show_online_status") val showOnlineStatus: Boolean? = null,
    @Json(name = "show_last_active") val showLastActive: Boolean? = null,
    @Json(name = "show_read_receipts") val showReadReceipts: Boolean? = null,
    @Json(name = "share_home_check_ins") val shareHomeCheckIns: Boolean? = null,
    @Json(name = "push_preferences") val pushPreferences: Map<String, Boolean>? = null,
    @Json(name = "email_preferences") val emailPreferences: Map<String, Boolean>? = null,
    @Json(name = "sms_preferences") val smsPreferences: Map<String, Boolean>? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
)

/** Partial-update body for `PATCH /api/privacy/settings`. */
@JsonClass(generateAdapter = true)
data class PrivacySettingsUpdate(
    @Json(name = "search_visibility") val searchVisibility: String? = null,
    @Json(name = "address_precision") val addressPrecision: String? = null,
    @Json(name = "hide_from_search") val hideFromSearch: Boolean? = null,
    @Json(name = "show_online_status") val showOnlineStatus: Boolean? = null,
    @Json(name = "show_last_active") val showLastActive: Boolean? = null,
    @Json(name = "show_read_receipts") val showReadReceipts: Boolean? = null,
    @Json(name = "share_home_check_ins") val shareHomeCheckIns: Boolean? = null,
    @Json(name = "push_preferences") val pushPreferences: Map<String, Boolean>? = null,
    @Json(name = "email_preferences") val emailPreferences: Map<String, Boolean>? = null,
    @Json(name = "sms_preferences") val smsPreferences: Map<String, Boolean>? = null,
)

/** Envelope from `GET /api/privacy/blocks`. */
@JsonClass(generateAdapter = true)
data class PrivacyBlocksResponse(
    val blocks: List<PrivacyBlockDto>,
)

@JsonClass(generateAdapter = true)
data class PrivacyBlockDto(
    val id: String,
    @Json(name = "blocked_user_id") val blockedUserId: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
)

/** Body for `POST /api/notifications/push-token`. */
@JsonClass(generateAdapter = true)
data class PushTokenBody(
    val token: String,
    val platform: String = "android",
)

/** Body for `POST /api/users/password`. */
@JsonClass(generateAdapter = true)
data class PasswordUpdateBody(
    @Json(name = "current_password") val currentPassword: String,
    @Json(name = "new_password") val newPassword: String,
)

/** Envelope from `GET /api/users/auth-methods`. */
@JsonClass(generateAdapter = true)
data class AuthMethodsResponse(
    val methods: List<AuthMethodDto>? = null,
    @Json(name = "has_password") val hasPassword: Boolean? = null,
    val providers: List<String>? = null,
    @Json(name = "two_factor_enabled") val twoFactorEnabled: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class AuthMethodDto(
    val id: String,
    val provider: String? = null,
    val label: String? = null,
)
