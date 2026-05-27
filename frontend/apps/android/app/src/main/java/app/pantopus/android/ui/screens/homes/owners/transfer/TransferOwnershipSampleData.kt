@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.homes.owners.transfer

import androidx.compose.ui.graphics.Color
import app.pantopus.android.ui.theme.PantopusColors

/**
 * A13.4 — Deterministic seed data for the Transfer Ownership form.
 * Mirrors iOS `TransferOwnershipSampleData`. The backend has been
 * removed from the repo, so the recipient roster, the current owner
 * roster (you + 2 co-owners), the home-context strip copy, and the
 * irreversibility warning template all live here so previews + Paparazzi
 * baselines render the same shape every time.
 */
object TransferOwnershipSampleData {
    // ─── Home strip ────────────────────────────────────────────────

    data class HomeContext(
        val title: String,
        val address: String,
        val since: String,
        val yourStake: Int,
        val coOwnerNames: String,
    )

    @Suppress("UnusedParameter")
    fun homeContext(homeId: String): HomeContext =
        HomeContext(
            title = "412 Elm Street",
            address = "412 Elm Street",
            since = "since 2019",
            yourStake = 60,
            coOwnerNames = "Mateo & Jin",
        )

    // ─── Owners + recipient ─────────────────────────────────────────

    data class OwnerSeed(
        val id: String,
        val displayName: String,
        val initials: String,
        val percent: Int,
        val palette: TransferOwnerPalette,
    )

    val currentUser =
        OwnerSeed(
            id = "you",
            displayName = "You",
            initials = "DK",
            percent = 60,
            palette = TransferOwnerPalette.Personal,
        )

    val coOwners: List<OwnerSeed> =
        listOf(
            OwnerSeed(id = "mateo", displayName = "Mateo", initials = "MR", percent = 25, palette = TransferOwnerPalette.Handyman),
            OwnerSeed(id = "jin", displayName = "Jin", initials = "JL", percent = 15, palette = TransferOwnerPalette.Home),
        )

    const val SENDER_FULL_NAME = "Daniel Kovács"

    data class RecipientSeed(
        val id: String,
        val name: String,
        val initials: String,
        val handle: String,
        val email: String,
        val owns: String,
        val onPantopus: String,
        val mutual: String,
        val verified: Boolean,
    )

    val mayaFortune =
        RecipientSeed(
            id = "maya_fortune",
            name = "Maya Fortune",
            initials = "MF",
            handle = "mayaf",
            email = "maya.fortune@pantopus.app",
            owns = "2 homes",
            onPantopus = "4 yrs",
            mutual = "5",
            verified = true,
        )

    // ─── Slider configuration ───────────────────────────────────────

    val sliderRange: IntRange = 1..60
    val presets: List<Int> = listOf(10, 25, 33, 50)
    const val DEFAULT_AMOUNT = 25

    // ─── Confirmation phrase ────────────────────────────────────────

    const val CONFIRMATION_PHRASE = "TRANSFER"

    // ─── Recipient palette ──────────────────────────────────────────

    val recipientPaletteStart: Color get() = PantopusColors.business
    val recipientPaletteEnd: Color get() = PantopusColors.businessDark
}

/**
 * Avatar gradient palettes for each owner row. Distinct from the
 * `OwnerProofPalette` rotation used by the Owners list so the diff bar's
 * colours stay legible and visually distinct without colliding with the
 * pillar tokens used elsewhere on the screen.
 */
enum class TransferOwnerPalette {
    Personal,
    Handyman,
    Home,
    Business,
    ;

    val color: Color
        get() =
            when (this) {
                Personal -> PantopusColors.primary600
                Handyman -> PantopusColors.handyman
                Home -> PantopusColors.success
                Business -> PantopusColors.business
            }

    val gradientStart: Color
        get() =
            when (this) {
                Personal -> PantopusColors.primary500
                Handyman -> PantopusColors.handyman
                Home -> PantopusColors.success
                Business -> PantopusColors.business
            }

    val gradientEnd: Color
        get() =
            when (this) {
                Personal -> PantopusColors.primary700
                Handyman -> PantopusColors.warning
                Home -> PantopusColors.homeDark
                Business -> PantopusColors.businessDark
            }
}
