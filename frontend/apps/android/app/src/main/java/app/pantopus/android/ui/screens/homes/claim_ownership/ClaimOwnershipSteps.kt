@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.claim_ownership

/**
 * Steps the claim-ownership wizard can be on. Order is meaningful — the
 * wizard advances `Start → Upload → Success` and back-navigates
 * `Upload → Start`. Success has no back chevron and ends the flow.
 */
enum class ClaimOwnershipStep(
    val ordinal0: Int,
) {
    Start(0),
    Upload(1),
    Success(2),
    ;

    /** One-indexed position used in the "N of M" top-bar readout, or null
     *  for the success terminal. */
    val stepNumber: Int?
        get() =
            when (this) {
                Start -> 1
                Upload -> 2
                Success -> null
            }

    companion object {
        const val PROGRESS_TOTAL: Int = 2

        fun fromOrdinal(value: Int): ClaimOwnershipStep =
            entries.firstOrNull { it.ordinal0 == value } ?: Start
    }
}

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
