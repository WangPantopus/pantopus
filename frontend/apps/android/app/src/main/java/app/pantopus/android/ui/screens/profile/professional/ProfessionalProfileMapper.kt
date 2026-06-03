@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.profile.professional

import app.pantopus.android.data.api.models.professional.ProfessionalProfileDto
import app.pantopus.android.data.api.models.professional.ProfessionalVerificationStatusResponse
import app.pantopus.android.ui.screens.shared.form.FormFieldState
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * P1-F — projects the backend professional record onto editor
 * [ProfessionalProfileContent] (mirrors the iOS `ProfessionalProfileViewModel`
 * mapping). The backend `profile/me` is thin, so only the overlapping fields
 * map: title ← headline, skills ← categories, the verification pill ←
 * verification_status, visibility ← is_public / is_active. Company name,
 * certifications, and portfolio start empty (no profile/me field).
 */
object ProfessionalProfileMapper {
    fun build(
        dto: ProfessionalProfileDto?,
        verification: ProfessionalVerificationStatusResponse?,
        proName: String = "",
    ): ProfessionalProfileContent {
        val status = verificationStatus(dto?.verificationStatus ?: verification?.status)
        val locality =
            listOfNotNull(dto?.serviceArea?.city, dto?.serviceArea?.state)
                .filter { it.isNotEmpty() }
                .joinToString(", ")
        val skills =
            (dto?.categories ?: emptyList()).map {
                ProSkill(id = it, label = categoryLabel(it), icon = categoryIcon(it))
            }
        return ProfessionalProfileContent(
            proName = proName,
            strength = strength(dto),
            title = FormFieldState(id = "title", value = dto?.headline ?: "", originalValue = dto?.headline ?: ""),
            yearsInRole = FormFieldState(id = "yearsInRole"),
            company = CompanyClaim(name = "", locality = locality, status = status),
            skills = skills,
            certifications = emptyList(),
            portfolio = emptyList(),
            visibility = visibilityRows(dto?.isPublic ?: false, dto?.isActive ?: false),
        )
    }

    fun verificationStatus(raw: String?): ProVerificationStatus =
        when (raw) {
            "verified" -> ProVerificationStatus.Verified
            "pending" -> ProVerificationStatus.Pending
            else -> ProVerificationStatus.Unverified
        }

    /** `pet_care` → `Pet Care`. */
    fun categoryLabel(key: String): String =
        key.split("_").joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

    fun categoryIcon(key: String): PantopusIcon =
        when (key) {
            "plumber" -> PantopusIcon.Droplet
            "electrician" -> PantopusIcon.Zap
            "carpentry" -> PantopusIcon.Hammer
            "cleaning" -> PantopusIcon.Sparkles
            "pet_care", "childcare", "elder_care" -> PantopusIcon.Users
            else -> PantopusIcon.Wrench
        }

    /** Coarse 0–100 completeness heuristic — the record has no strength field. */
    fun strength(dto: ProfessionalProfileDto?): Int {
        if (dto == null) return 0
        var score = 40
        if (!dto.headline.isNullOrEmpty()) score += 15
        if (!dto.bio.isNullOrEmpty()) score += 10
        if (!dto.categories.isNullOrEmpty()) score += 15
        when (dto.verificationStatus) {
            "verified" -> score += 20
            "pending" -> score += 10
        }
        return minOf(score, 100)
    }

    private fun visibilityRows(
        isPublic: Boolean,
        isActive: Boolean,
    ): List<VisibilityRow> =
        listOf(
            VisibilityRow(
                id = "publicProfile",
                label = "Public profile",
                sub = "Neighbors can open your professional profile from search and gigs.",
                isOn = isPublic,
            ),
            VisibilityRow(
                id = "activeForHire",
                label = "Active for hire",
                sub = "Show as available to take on new work.",
                isOn = isActive,
            ),
        )
}
