@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.claim_ownership

/** Deterministic fixture data for the Claim Ownership wizard start step. */
data class ClaimOwnershipStartContent(
    val homeLabel: String,
    val contestedClaim: ClaimOwnershipContestedClaim? = null,
) {
    val isContested: Boolean get() = contestedClaim != null
}

data class ClaimOwnershipContestedClaim(
    val title: String,
    val body: String,
    val claimantInitials: String,
    val claimantName: String,
    val filedLabel: String,
    val statusLabel: String,
)

object ClaimOwnershipSampleData {
    fun startContent(homeId: String): ClaimOwnershipStartContent =
        if (homeId.contains("contested", ignoreCase = true)) {
            contestedStart
        } else {
            canonicalStart
        }

    val canonicalStart =
        ClaimOwnershipStartContent(
            homeLabel = "412 Elm St",
        )

    val contestedStart =
        ClaimOwnershipStartContent(
            homeLabel = "412 Elm St",
            contestedClaim =
                ClaimOwnershipContestedClaim(
                    title = "Another claim is already in review",
                    body =
                        "A verified resident at this address filed an ownership claim 3 days ago. " +
                            "You can still claim; both claims will be reviewed together.",
                    claimantInitials = "JR",
                    claimantName = "J. R.",
                    filedLabel = "Filed Oct 9",
                    statusLabel = "Under review",
                ),
        )
}
