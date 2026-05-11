@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.claim_ownership

/**
 * Steps the claim-ownership wizard can be on. Order is meaningful — the
 * wizard advances `Start → Upload → Success` and back-navigates
 * `Upload → Start`. Success has no back chevron and ends the flow.
 *
 * Chrome (top-bar readout + progress fraction) is computed in the VM's
 * `computeChrome` rather than via per-step metadata on this enum; the
 * wizard doesn't survive process-death so there's nothing to (de)serialise.
 */
enum class ClaimOwnershipStep { Start, Upload, Success }

/**
 * Identifier for one of the two upload tiles. The `backendType` matches
 * the `evidence_type` Joi enum at `backend/routes/homeOwnership.js:43`.
 */
enum class ClaimEvidenceSlot(
    val backendType: String,
    val title: String,
) {
    Identity("idv", "Government ID"),
    Ownership("deed", "Proof of ownership"),
    ;

    val acceptHint: String get() = "JPG, PNG, or PDF up to 10 MB"
}

/** Per-slot upload state surfaced to the UI. */
sealed interface ClaimSlotState {
    data object Empty : ClaimSlotState

    data class Picked(val file: ClaimPickedFile) : ClaimSlotState

    data class Uploading(val file: ClaimPickedFile, val fraction: Float) : ClaimSlotState

    data class Uploaded(val file: ClaimPickedFile, val fileUrl: String) : ClaimSlotState

    data class Failed(val file: ClaimPickedFile, val message: String) : ClaimSlotState

    val hasFile: Boolean
        get() = this !is Empty

    val pickedFile: ClaimPickedFile?
        get() =
            when (this) {
                is Picked -> file
                is Uploading -> file
                is Uploaded -> file
                is Failed -> file
                is Empty -> null
            }
}

/** A single picked file buffered in memory until upload. */
data class ClaimPickedFile(
    val filename: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    val sizeBytes: Long get() = bytes.size.toLong()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClaimPickedFile) return false
        if (filename != other.filename) return false
        if (mimeType != other.mimeType) return false
        if (!bytes.contentEquals(other.bytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

/** Outbound navigation events the screen consumes. */
sealed interface ClaimOwnershipOutboundEvent {
    /** Pop the wizard with no further navigation. */
    data object Dismiss : ClaimOwnershipOutboundEvent

    /** Pop the wizard and route to the user's claims list. */
    data object OpenClaimsList : ClaimOwnershipOutboundEvent
}
