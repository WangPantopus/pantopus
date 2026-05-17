@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.status

import androidx.compose.runtime.Immutable
import app.pantopus.android.ui.theme.PantopusIcon

/** Hero illustration tone. The view picks an icon + tint per case. */
enum class StatusIllustration(val key: String) {
    Success("success"),
    Waiting("waiting"),
    Email("email"),
}

@Immutable
data class StatusTimelineStage(
    val id: String,
    val label: String,
)

@Immutable
data class StatusActionCard(
    val id: String,
    val icon: PantopusIcon,
    val title: String,
    val subtitle: String? = null,
)

/** CTA that carries a label and an opaque key so tests can verify
 *  which CTA fired without inspecting closure identity. */
@Immutable
data class StatusCta(
    val label: String,
    val actionKey: String,
)

@Immutable
data class StatusWaitingContent(
    val illustration: StatusIllustration,
    val headline: String,
    val subcopy: String,
    val timeline: List<StatusTimelineStage> = emptyList(),
    val currentStageId: String? = null,
    val etaChip: String? = null,
    val actionCards: List<StatusActionCard> = emptyList(),
    val explainerBullets: List<String> = emptyList(),
    val primaryCta: StatusCta? = null,
    val secondaryCta: StatusCta? = null,
) {
    companion object {
        /** Three-stage progress used by every Homes flow. */
        val homesClaimTimeline: List<StatusTimelineStage> =
            listOf(
                StatusTimelineStage(id = "submitted", label = "Submitted"),
                StatusTimelineStage(id = "review", label = "Under review"),
                StatusTimelineStage(id = "complete", label = "Complete"),
            )

        /** Frame 1 — Claim submitted (success). */
        fun claimSubmitted(
            homeName: String? = null,
            eta: String? = "2–3 days",
        ): StatusWaitingContent {
            val venue = homeName?.takeIf { it.isNotBlank() } ?: "this home"
            return StatusWaitingContent(
                illustration = StatusIllustration.Success,
                headline = "Claim submitted",
                subcopy = "We'll review your evidence and email you when $venue is verified.",
                timeline = homesClaimTimeline,
                currentStageId = "submitted",
                etaChip = eta,
                actionCards =
                    listOf(
                        StatusActionCard(
                            id = "checkInbox",
                            icon = PantopusIcon.Mailbox,
                            title = "Check your inbox",
                            subtitle = "We'll send a verification email shortly.",
                        ),
                        StatusActionCard(
                            id = "viewClaim",
                            icon = PantopusIcon.File,
                            title = "View claim details",
                            subtitle = "See what you submitted and add more evidence.",
                        ),
                    ),
                explainerBullets =
                    listOf(
                        "We compare your evidence against any prior claims.",
                        "If we need more, we'll ask via email — never SMS.",
                        "Most claims resolve in 2–3 days.",
                    ),
                primaryCta = StatusCta(label = "Back to Hub", actionKey = "back_to_hub"),
                secondaryCta = StatusCta(label = "View claim", actionKey = "view_claim"),
            )
        }

        /** Frame 2 — Under review (mid-process). */
        fun underReview(
            homeName: String? = null,
            submittedAgo: String? = null,
        ): StatusWaitingContent {
            val venue = homeName?.takeIf { it.isNotBlank() } ?: "your claim"
            val baseSubcopy = "We're reviewing your evidence. We'll email you when $venue is verified."
            val subcopy =
                if (!submittedAgo.isNullOrBlank()) "$baseSubcopy Submitted $submittedAgo." else baseSubcopy
            return StatusWaitingContent(
                illustration = StatusIllustration.Waiting,
                headline = "Under review",
                subcopy = subcopy,
                timeline = homesClaimTimeline,
                currentStageId = "review",
                etaChip = "Usually resolved in 2–3 days",
                actionCards =
                    listOf(
                        StatusActionCard(
                            id = "addEvidence",
                            icon = PantopusIcon.Upload,
                            title = "Add more evidence",
                            subtitle = "Strengthen your claim with extra documents.",
                        ),
                        StatusActionCard(
                            id = "contactSupport",
                            icon = PantopusIcon.Mailbox,
                            title = "Contact support",
                            subtitle = "Stuck for more than 3 days? Reach out.",
                        ),
                    ),
                explainerBullets =
                    listOf(
                        "Pantopus never asks for payment to speed up a review.",
                        "You can keep using the rest of the app while we verify.",
                        "We'll notify you in-app AND by email when it resolves.",
                    ),
                primaryCta = StatusCta(label = "Back to Hub", actionKey = "back_to_hub"),
                secondaryCta = StatusCta(label = "View claim", actionKey = "view_claim"),
            )
        }

        /**
         * Frame 4 — Reset link sent. Status frame for the Forgot-password
         * success state — used by `ForgotPasswordScreen`. Primary CTA is
         * "Resend"; ghost CTA is "Back to login". Body interpolates the
         * caller's email so the user can visually confirm where the link
         * went.
         */
        fun resetLinkSent(email: String): StatusWaitingContent {
            val recipient = email.ifBlank { "your email" }
            return StatusWaitingContent(
                illustration = StatusIllustration.Email,
                headline = "Check your email",
                subcopy = "We sent a reset link to $recipient. Click it to set a new password.",
                primaryCta = StatusCta(label = "Resend", actionKey = "resend_reset"),
                secondaryCta = StatusCta(label = "Back to login", actionKey = "back_to_login"),
            )
        }

        /**
         * Frame 5 — Password reset success. Reached after `ResetPasswordScreen`
         * submits a valid token + new password. Auto-redirects to login
         * after 3 seconds (caller wires the timer).
         */
        fun passwordReset(): StatusWaitingContent =
            StatusWaitingContent(
                illustration = StatusIllustration.Success,
                headline = "Password reset",
                subcopy = "You can now log in with your new password.",
                primaryCta = StatusCta(label = "Back to login", actionKey = "back_to_login"),
            )

        /** Frame 3 — Check your email (info). */
        fun checkYourEmail(email: String? = null): StatusWaitingContent {
            val recipient =
                email?.let { "We just sent a link to $it." }
                    ?: "We just sent a verification link to your email."
            return StatusWaitingContent(
                illustration = StatusIllustration.Email,
                headline = "Check your email",
                subcopy = "$recipient Tap the link to finish setting up your account.",
                timeline = emptyList(),
                etaChip = "Should arrive in under a minute",
                actionCards =
                    listOf(
                        StatusActionCard(
                            id = "openMail",
                            icon = PantopusIcon.Mailbox,
                            title = "Open Mail",
                            subtitle = "Jump straight to your inbox.",
                        ),
                        StatusActionCard(
                            id = "resendEmail",
                            icon = PantopusIcon.Send,
                            title = "Resend verification",
                            subtitle = "Didn't get it? We'll send another.",
                        ),
                    ),
                explainerBullets =
                    listOf(
                        "Links expire 30 minutes after they're sent.",
                        "Check your spam folder if it doesn't arrive.",
                        "You can change your email below if it was wrong.",
                    ),
                primaryCta = StatusCta(label = "Resend email", actionKey = "resend_email"),
                secondaryCta = StatusCta(label = "Change email", actionKey = "change_email"),
            )
        }
    }
}
