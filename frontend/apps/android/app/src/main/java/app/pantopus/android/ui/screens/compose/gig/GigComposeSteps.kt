@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.compose.gig

/**
 * The six pre-success steps of the Post-a-Task wizard, in order. The
 * success state is a sentinel terminal step used to render the success
 * hero block.
 */
enum class GigComposeStep(
    val ordinal0: Int,
) {
    Category(0),
    Basics(1),
    Budget(2),
    Schedule(3),
    Location(4),
    Review(5),
    Success(6),
    ;

    /**
     * One-indexed position used in the "N of M" top-bar readout, or
     * `null` for the success terminal.
     */
    val stepNumber: Int?
        get() =
            when (this) {
                Category -> 1
                Basics -> 2
                Budget -> 3
                Schedule -> 4
                Location -> 5
                Review -> 6
                Success -> null
            }

    companion object {
        /** Total number of "step N of M" steps shown in the readout. */
        const val PROGRESS_TOTAL: Int = 6

        fun fromOrdinal(value: Int): GigComposeStep = entries.firstOrNull { it.ordinal0 == value } ?: Category
    }
}

/**
 * One-of-nine category the user picks in step 1. Mirrors the chip
 * palette in `gigs-frames.jsx` CATS plus an `Other` bucket that is
 * surfaced in the composer but not in the feed filter.
 */
enum class GigComposeCategory(
    val key: String,
    val label: String,
) {
    Handyman("handyman", "Handyman"),
    Cleaning("cleaning", "Cleaning"),
    Moving("moving", "Moving"),
    PetCare("petcare", "Pet care"),
    ChildCare("childcare", "Child care"),
    Tutoring("tutoring", "Tutoring"),
    Delivery("delivery", "Delivery"),
    Tech("tech", "Tech"),
    Other("other", "Other"),
    ;

    companion object {
        /**
         * Maps a `GigsCategory.key` (or any unrecognised string) into
         * the compose enum. Used so the Hub's category-specific entry
         * preselects the right tile.
         */
        fun fromRawKey(raw: String?): GigComposeCategory? {
            val key = raw?.lowercase().orEmpty()
            if (key.isEmpty() || key == "all") return null
            return entries.firstOrNull { it.key == key }
        }
    }
}

/**
 * B.3 (A12.8) — entry mode for step 1. `Magic` is the default AI-assisted
 * describe path; `Manual` is the category-grid fallback reachable via the
 * "Pick a category instead" link.
 */
enum class ComposeMode { Magic, Manual }

/**
 * B.3 (A12.8) — compact Magic Task engagement selector. It pre-fills the
 * downstream schedule / budget steps instead of adding a separate backend
 * field.
 */
enum class GigComposeEngagementMode { OneTime, Recurring, OpenBidding }

/** Budget-type radio in step 3. */
enum class GigComposeBudgetType(
    val wireValue: String,
    val label: String,
) {
    Fixed("fixed", "Fixed price"),
    Hourly("hourly", "Hourly"),
    Offers("offers", "Open to bids"),
    ;

    fun subcopy(): String =
        when (this) {
            Fixed -> "One total price for the whole job."
            Hourly -> "Pay by the hour worked."
            Offers -> "Helpers send their own price and you pick."
        }
}

/** Schedule-type radio in step 4. */
enum class GigComposeScheduleType(
    val label: String,
) {
    OneTime("One-time"),
    Recurring("Recurring"),
    Flexible("Flexible"),
    ;

    /**
     * Wire value forwarded as `schedule_type` to `POST /api/gigs`.
     * `Recurring` maps to `flexible` until the backend gains a true
     * recurring schedule_type — the spec surfaces it in the UI but the
     * API doesn't model it yet.
     */
    val wireValue: String
        get() =
            when (this) {
                OneTime -> "scheduled"
                Recurring -> "flexible"
                Flexible -> "flexible"
            }

    fun subcopy(): String =
        when (this) {
            OneTime -> "A single date and time."
            Recurring -> "Repeats on a regular cadence."
            Flexible -> "Whenever works for both of you."
        }
}

/** Location-mode radio in step 5. */
enum class GigComposeLocationMode(
    val label: String,
) {
    YourAddress("Your address"),
    APlace("A place"),
    Virtual("Virtual"),
    ;

    val wireMode: String
        get() =
            when (this) {
                YourAddress -> "home"
                APlace -> "address"
                Virtual -> "custom"
            }

    fun subcopy(): String =
        when (this) {
            YourAddress -> "Helpers come to the address on your account."
            APlace -> "Helpers come to a different address you'll enter."
            Virtual -> "Done over phone, video, or messages — no on-site visit."
        }
}

/**
 * E.1 — cancellation-policy tier surfaced by the composer's policy picker
 * sheet. The mid tier reads **Moderate** in the design but maps to the
 * backend's `standard` value (`backend/routes/gigs.js:438`).
 */
enum class GigCancellationPolicy(
    val label: String,
    val detail: String,
    val wireValue: String,
) {
    Flexible("Flexible", "Full refund up to 24 hours before the start time.", "flexible"),
    Moderate("Moderate", "50% refund up to 48 hours before. No refund after.", "standard"),
    Strict("Strict", "No refund within 7 days of the start time.", "strict"),
}

/** E.1 — the composer picker sheets, presented one-at-a-time over the wizard. */
enum class GigPickerSheet { Attachment, Category, Deadline, Policy, Urgency, Tags }

/**
 * P0.2 — one in-flight (or failed) photo upload tile in the Basics step.
 * Uploaded photos graduate into [GigComposeFormState.photoIds] as URLs;
 * these transient tiles are never persisted (raw bytes can't survive
 * process death anyway).
 */
data class GigComposePhotoUpload(
    val id: String,
    val failed: Boolean = false,
)

/**
 * P0.2 — raw bytes of a picked attachment, held by the view-model for
 * upload + retry. Not a data class — [bytes] is an array, so structural
 * equality would be misleading.
 */
class GigComposePickedPhoto(
    val filename: String,
    val mimeType: String,
    val bytes: ByteArray,
)

/** Plain-old-data address fields collected in step 5 for `APlace`. */
data class GigComposePlaceAddress(
    val line1: String = "",
    val city: String = "",
    val state: String = "",
    val zip: String = "",
) {
    val isComplete: Boolean
        get() =
            line1.trim().isNotEmpty() &&
                city.trim().isNotEmpty() &&
                state.trim().isNotEmpty() &&
                zip.trim().isNotEmpty()
}

/**
 * Validation constants enforced at the UI layer. The backend also
 * validates (`backend/routes/gigs.js:425`); these mirror the prompt's
 * stricter UI rules.
 */
object GigComposeLimits {
    const val TITLE_MIN: Int = 5
    const val TITLE_MAX: Int = 100
    const val DESCRIPTION_MIN: Int = 20
    const val DESCRIPTION_MAX: Int = 2000
    const val MAX_PHOTOS: Int = 6

    /** E.1 — gig tag cap. Mirrors `tags` `.max(5)` in `createGigSchema`. */
    const val MAX_TAGS: Int = 5

    /** B.3 — Magic Task describe textarea cap (matches A12.8 "184 / 500"). */
    const val DESCRIBE_MAX: Int = 500
}

/**
 * Snapshot of all wizard form state. Mirrored into [androidx.lifecycle.SavedStateHandle]
 * so the wizard survives config changes and process death.
 */
data class GigComposeFormState(
    val step: Int = GigComposeStep.Category.ordinal0,
    /** B.3 — step-1 entry mode (Magic describe vs manual picker). */
    val composeMode: ComposeMode = ComposeMode.Magic,
    /** B.3 — plain-English Magic Task input. */
    val describeText: String = "",
    /** B.3 — archetype parsed from [describeText] (debounced), mirrored into [category]. */
    val detectedArchetype: GigComposeCategory? = null,
    val category: GigComposeCategory? = null,
    val title: String = "",
    val description: String = "",
    val photoIds: List<String> = emptyList(),
    val budgetType: GigComposeBudgetType? = null,
    val budgetMin: String = "",
    val budgetMax: String = "",
    val scheduleType: GigComposeScheduleType? = null,
    val scheduledStartISO: String? = null,
    val locationMode: GigComposeLocationMode? = null,
    val placeAddress: GigComposePlaceAddress = GigComposePlaceAddress(),
    /** E.1 — optional hard deadline (`deadline`), ISO-8601. null ⇒ flexible. */
    val deadlineISO: String? = null,
    /** E.1 — cancellation policy (`cancellation_policy`). null ⇒ backend default. */
    val cancellationPolicy: GigCancellationPolicy? = null,
    /** E.1 — boost flag (`is_urgent`). */
    val isUrgent: Boolean = false,
    /** E.1 — freeform tags (`tags`), stored without the leading `#`. */
    val tags: List<String> = emptyList(),
) {
    val currentStep: GigComposeStep
        get() = GigComposeStep.fromOrdinal(step)

    val engagementMode: GigComposeEngagementMode
        get() =
            if (budgetType == GigComposeBudgetType.Offers) {
                GigComposeEngagementMode.OpenBidding
            } else if (scheduleType == GigComposeScheduleType.Recurring) {
                GigComposeEngagementMode.Recurring
            } else {
                GigComposeEngagementMode.OneTime
            }

    /** True when any user-visible field carries data — drives the close-confirm gate. */
    val hasAnyData: Boolean
        get() =
            describeText.isNotEmpty() ||
                category != null ||
                title.isNotEmpty() ||
                description.isNotEmpty() ||
                photoIds.isNotEmpty() ||
                budgetType != null ||
                budgetMin.isNotEmpty() ||
                budgetMax.isNotEmpty() ||
                scheduleType != null ||
                scheduledStartISO != null ||
                locationMode != null ||
                placeAddress.isComplete ||
                placeAddress.line1.isNotEmpty() ||
                deadlineISO != null ||
                cancellationPolicy != null ||
                isUrgent ||
                tags.isNotEmpty()

    companion object {
        val EMPTY = GigComposeFormState()
    }
}

/** Outbound navigation events the screen consumes. */
sealed interface GigComposeOutboundEvent {
    /** Pop the wizard with no further navigation. */
    data object Dismiss : GigComposeOutboundEvent

    /** Pop the wizard and route to the newly-created gig's detail. */
    data class OpenGigDetail(
        val gigId: String,
    ) : GigComposeOutboundEvent
}
