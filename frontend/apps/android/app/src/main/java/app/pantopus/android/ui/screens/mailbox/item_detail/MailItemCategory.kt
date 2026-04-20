@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.mailbox.item_detail

import androidx.compose.ui.graphics.Color
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * 14-category enum for mailbox items. Maps 1:1 to the backend
 * `mail.mail_type` string — unknown values fall through to [General].
 */
enum class MailItemCategory(val raw: String, val accent: Color) {
    Package("package", PantopusColors.delivery),
    Coupon("coupon", PantopusColors.childCare),
    Notice("notice", PantopusColors.warning),
    Bill("bill", PantopusColors.error),
    Statement("statement", PantopusColors.tutoring),
    Insurance("insurance", PantopusColors.info),
    Tax("tax", PantopusColors.vehicles),
    Subscription("subscription", PantopusColors.goods),
    Legal("legal", PantopusColors.moving),
    Healthcare("healthcare", PantopusColors.petCare),
    Membership("membership", PantopusColors.personal),
    Delivery("delivery", PantopusColors.handyman),
    Social("social", PantopusColors.cleaning),
    General("general", PantopusColors.appTextSecondary),
    ;

    companion object {
        /** Matches a backend `mail_type`/`type` string onto a typed case. */
        fun fromRaw(value: String?): MailItemCategory {
            val normalized = value?.lowercase() ?: return General
            return entries.firstOrNull { it.raw == normalized } ?: General
        }
    }
}

/** Sender trust level for the detail pill. */
enum class MailTrust(
    val label: String,
    val icon: PantopusIcon,
    val background: Color,
    val foreground: Color,
) {
    Verified("Verified", PantopusIcon.ShieldCheck, PantopusColors.successBg, PantopusColors.success),
    Partial("Partial", PantopusIcon.Shield, PantopusColors.warningBg, PantopusColors.warning),
    Unverified("Unverified", PantopusIcon.Shield, PantopusColors.appSurfaceSunken, PantopusColors.appTextSecondary),
    Chain("Pantopus user", PantopusIcon.ShieldCheck, PantopusColors.infoBg, PantopusColors.primary600),
    ;

    companion object {
        /** Maps the backend `sender_trust` string onto a pill state. */
        fun fromRaw(value: String?): MailTrust =
            when (value) {
                "verified_gov", "verified_utility", "verified_business" -> Verified
                "pantopus_user" -> Chain
                "partial" -> Partial
                else -> Unverified
            }
    }
}
