@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.profile.professional

import app.pantopus.android.ui.screens.shared.form.FormFieldState
import app.pantopus.android.ui.theme.PantopusIcon

enum class ProVerificationStatus {
    Verified,
    Pending,
    Expiring,
    Unverified,
}

val ProVerificationStatus.isAwaitingReview: Boolean
    get() = this == ProVerificationStatus.Pending

data class CompanyClaim(
    val name: String,
    val locality: String,
    val status: ProVerificationStatus,
    val isDirty: Boolean = false,
    val hint: String? = null,
)

data class ProSkill(
    val id: String,
    val label: String,
    val icon: PantopusIcon,
    val isFresh: Boolean = false,
)

data class Certification(
    val id: String,
    val name: String,
    val issuer: String,
    val issued: String,
    val expires: String,
    val status: ProVerificationStatus,
    val isFresh: Boolean = false,
)

enum class PortfolioLinkState { Resolved, Loading, Error }

data class PortfolioLink(
    val id: String,
    val host: String,
    val title: String,
    val url: String,
    val state: PortfolioLinkState,
    val isFresh: Boolean = false,
) {
    val icon: PantopusIcon
        get() =
            when {
                host.contains("behance", ignoreCase = true) -> PantopusIcon.Palette
                host.contains("youtube", ignoreCase = true) || host.contains("youtu.be", ignoreCase = true) ->
                    PantopusIcon.PlayCircle
                else -> PantopusIcon.Link
            }
}

data class VisibilityRow(
    val id: String,
    val label: String,
    val sub: String? = null,
    val isOn: Boolean,
    val scope: String? = null,
    val originalOn: Boolean = isOn,
) {
    val isDirty: Boolean
        get() = isOn != originalOn
}

data class ProfessionalProfileContent(
    val proName: String,
    val strength: Int,
    val title: FormFieldState,
    val yearsInRole: FormFieldState,
    val company: CompanyClaim,
    val skills: List<ProSkill>,
    val certifications: List<Certification>,
    val portfolio: List<PortfolioLink>,
    val visibility: List<VisibilityRow>,
) {
    val dirtyCount: Int
        get() =
            listOf(
                title.isDirty,
                yearsInRole.isDirty,
                company.isDirty,
            ).count { it } +
                skills.count { it.isFresh } +
                certifications.count { it.isFresh } +
                portfolio.count { it.isFresh } +
                visibility.count { it.isDirty }

    val pendingCount: Int
        get() =
            (if (company.status.isAwaitingReview) 1 else 0) +
                certifications.count { it.status.isAwaitingReview }

    val isDirty: Boolean
        get() = dirtyCount > 0

    val strengthCaption: String
        get() =
            if (pendingCount == 0) {
                "All claims verified · ready for high-trust clients."
            } else {
                "$pendingCount ${if (pendingCount == 1) "claim" else "claims"} pending verification · finish to reach Pro+."
            }
}

sealed interface ProfessionalProfileUiState {
    data object Loading : ProfessionalProfileUiState

    data class Verified(
        val content: ProfessionalProfileContent,
    ) : ProfessionalProfileUiState

    data class Pending(
        val content: ProfessionalProfileContent,
        val dirtyCount: Int,
        val pendingCount: Int,
    ) : ProfessionalProfileUiState

    data class Error(
        val message: String,
    ) : ProfessionalProfileUiState
}
