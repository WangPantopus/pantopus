@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.gigs

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Optional context block for `POST /api/gigs/magic-draft`. All fields are
 * nullable so Moshi omits them when unset.
 */
@JsonClass(generateAdapter = true)
data class MagicDraftContext(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val budget: Double? = null,
)

/** Body for `POST /api/gigs/magic-draft`. `text` must be 3–2000 chars. */
@JsonClass(generateAdapter = true)
data class MagicDraftRequest(
    val text: String,
    val context: MagicDraftContext? = null,
    val attachmentUrls: List<String>? = null,
)

/** Nested `budget_range` on a magic draft. */
@JsonClass(generateAdapter = true)
data class MagicDraftBudgetRange(
    val min: Double? = null,
    val max: Double? = null,
)

/**
 * The structured draft the backend NLP extracts from the describe text.
 * Every field is nullable — the parser only emits what it's confident
 * about, and unknown keys are tolerated (Moshi skips them by default).
 */
@JsonClass(generateAdapter = true)
data class MagicDraftDto(
    val title: String? = null,
    val description: String? = null,
    val category: String? = null,
    @Json(name = "task_archetype") val taskArchetype: String? = null,
    /** "offers" | "fixed" | "hourly". */
    @Json(name = "pay_type") val payType: String? = null,
    @Json(name = "budget_fixed") val budgetFixed: Double? = null,
    @Json(name = "hourly_rate") val hourlyRate: Double? = null,
    @Json(name = "budget_range") val budgetRange: MagicDraftBudgetRange? = null,
    @Json(name = "schedule_type") val scheduleType: String? = null,
    @Json(name = "location_mode") val locationMode: String? = null,
    @Json(name = "privacy_level") val privacyLevel: String? = null,
    val tags: List<String>? = null,
    @Json(name = "is_urgent") val isUrgent: Boolean? = null,
    @Json(name = "attachments_suggested") val attachmentsSuggested: Boolean? = null,
)

/** Envelope from `POST /api/gigs/magic-draft`. */
@JsonClass(generateAdapter = true)
data class MagicDraftResponse(
    val draft: MagicDraftDto,
    val confidence: Double? = null,
    val fieldConfidence: Map<String, Double>? = null,
    val clarifyingQuestion: String? = null,
    val source: String? = null,
    val elapsed: Long? = null,
    @Json(name = "_fallback") val fallback: Boolean? = null,
)
