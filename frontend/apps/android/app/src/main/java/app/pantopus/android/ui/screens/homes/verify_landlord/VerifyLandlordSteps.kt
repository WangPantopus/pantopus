@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.verify_landlord

/**
 * Steps the A12.5 / A12.6 verify-landlord wizard owns. The third leg
 * of the flow (A12.7 Postcard verification) lives outside this state
 * machine; the wizard advertises "1 of 3" / "2 of 3" purely so the
 * user understands where they are in the broader flow.
 */
enum class VerifyLandlordStep { Start, Details }

/**
 * Which Start variant the wizard renders. The fast-track path is
 * surfaced when 2+ other tenants in the building have already
 * verified the same landlord; we skip the email confirmation in that
 * case.
 */
enum class VerifyLandlordVariant { Canonical, FastTrack }

/**
 * Submit-time state machine — shared shape between iOS + Android.
 */
sealed interface VerifyLandlordSubmitState {
    data object Idle : VerifyLandlordSubmitState

    data object Submitting : VerifyLandlordSubmitState

    data object Submitted : VerifyLandlordSubmitState

    data class Error(val message: String) : VerifyLandlordSubmitState
}

/** Detected attributes from a lease upload — drives the done / warn
 *  DLeaseUpload variants and the unit-mismatch validation. */
data class VerifyLandlordLeaseFile(
    val filename: String,
    val sizeLabel: String,
    val pageCount: Int,
    val detectedOwner: String?,
    val detectedUnit: String?,
)

/**
 * Per-slot validation messages surfaced in the A12.6 error frame
 * (per-field chips) and aggregated into the top error-summary banner.
 */
data class VerifyLandlordValidationErrors(
    val ownerName: String? = null,
    val contactName: String? = null,
    val email: String? = null,
    val lease: String? = null,
    val pmName: String? = null,
    val pmEmail: String? = null,
) {
    /** Used by the error-summary banner ("Fix N things to submit"). */
    val count: Int
        get() = listOfNotNull(ownerName, contactName, email, lease, pmName, pmEmail).size

    /**
     * Compact dot-separated list rendered as the banner sub-label —
     * matches iOS' `compactSummary` output character-for-character.
     */
    val compactSummary: String
        get() =
            buildList {
                if (email != null) add("Email format")
                if (lease != null) add("Lease unit mismatch")
                if (ownerName != null) add("Owner name")
                if (contactName != null) add("Contact name")
                if (pmName != null) add("PM name")
                if (pmEmail != null) add("PM email")
            }.joinToString(" · ")

    val isEmpty: Boolean get() = count == 0
}

/**
 * The full A12.6 form state. Held inside the wizard VM and projected
 * into per-field views on the Details step.
 */
data class VerifyLandlordForm(
    val ownerName: String = "",
    val contactName: String = "",
    val email: String = "",
    val phone: String = "",
    val lease: VerifyLandlordLeaseFile? = null,
    val pmEnabled: Boolean = false,
    val pmName: String = "",
    val pmEmail: String = "",
    val pmPhone: String = "",
    /** Registered unit on the home record — drives the lease unit
     *  mismatch validation when the OCR'd unit doesn't agree. */
    val registeredUnit: String = "",
) {
    /**
     * Pure validation projection — same logic on iOS + Android. The
     * three contracts from the audit:
     *  1. Email must be RFC-shaped (`x@y.z`).
     *  2. The lease's detected unit must match `registeredUnit` when
     *     OCR was able to read one.
     *  3. When the PM toggle is on, PM name + PM email are both
     *     required (PM phone stays optional).
     */
    fun validate(): VerifyLandlordValidationErrors {
        val ownerNameError = if (ownerName.trim().isEmpty()) "Required" else null
        val contactNameError = if (contactName.trim().isEmpty()) "Required" else null
        val trimmedEmail = email.trim()
        val emailError =
            when {
                trimmedEmail.isEmpty() -> "Required"
                !looksLikeEmail(trimmedEmail) -> "Missing top-level domain"
                else -> null
            }
        val leaseError =
            when {
                lease == null -> "Required"
                lease.detectedUnit != null &&
                    registeredUnit.isNotEmpty() &&
                    !lease.detectedUnit.equals(registeredUnit, ignoreCase = true) -> "Unit mismatch"
                else -> null
            }
        val pmNameError: String?
        val pmEmailError: String?
        if (pmEnabled) {
            pmNameError = if (pmName.trim().isEmpty()) "Required" else null
            val trimmedPmEmail = pmEmail.trim()
            pmEmailError =
                when {
                    trimmedPmEmail.isEmpty() -> "Required"
                    !looksLikeEmail(trimmedPmEmail) -> "Missing top-level domain"
                    else -> null
                }
        } else {
            pmNameError = null
            pmEmailError = null
        }
        return VerifyLandlordValidationErrors(
            ownerName = ownerNameError,
            contactName = contactNameError,
            email = emailError,
            lease = leaseError,
            pmName = pmNameError,
            pmEmail = pmEmailError,
        )
    }

    companion object {
        /**
         * Lightweight client-side email check — catches the missing-TLD
         * case from the design ("mira@elmstholdings"). Server-side
         * still runs the authoritative validation.
         */
        internal fun looksLikeEmail(candidate: String): Boolean {
            val at = candidate.indexOf('@')
            if (at <= 0 || at == candidate.lastIndex) return false
            val local = candidate.substring(0, at)
            val domain = candidate.substring(at + 1)
            if (local.isEmpty() || !domain.contains('.')) return false
            val parts = domain.split('.')
            val tld = parts.lastOrNull() ?: return false
            return parts.size >= 2 && tld.isNotEmpty()
        }
    }
}

/** Outbound events the wizard view needs the host nav stack to act on. */
sealed interface VerifyLandlordOutboundEvent {
    /** Pop the wizard with no further navigation. */
    data object Dismiss : VerifyLandlordOutboundEvent

    /**
     * Submit succeeded — pop the wizard and push the standalone A12.7
     * Postcard verification screen so the user can track delivery.
     */
    data class OpenPostcardVerification(val homeId: String) : VerifyLandlordOutboundEvent
}
