@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.scheduling.invitee.customer

/** Recurrence cadence for a series. */
enum class RecurrenceRepeat {
    Weekly,
    Daily,
}

/** Per-occurrence status in the preview / after submit. */
enum class OccurrenceStatus {
    Open,
    Failed,
}

/** One generated session in the series. */
data class RecurrenceOccurrence(
    val startUtc: String,
    val dateLabel: String,
    val timeLabel: String,
    val status: OccurrenceStatus = OccurrenceStatus.Open,
)

/** The recurrence pattern the user is configuring. */
data class RecurringConfig(
    val repeat: RecurrenceRepeat = RecurrenceRepeat.Weekly,
    /** 0 = Sunday … 6 = Saturday. */
    val weekdayIndex: Int = 2,
    /** Minutes from midnight (local). */
    val startMinutes: Int = 14 * 60,
    val count: Int = 6,
    val durationMin: Int = 30,
)

/** Loading the user's event type to attach the series to. */
sealed interface RecurringLoadState {
    data object Loading : RecurringLoadState

    data object Empty : RecurringLoadState

    data class Error(val message: String) : RecurringLoadState

    data class Loaded(
        val eventTypeId: String,
        val eventTypeName: String,
        val durationMin: Int,
    ) : RecurringLoadState
}

/** The submit lifecycle for the series. */
sealed interface RecurringSubmitState {
    data object Idle : RecurringSubmitState

    data object Saving : RecurringSubmitState

    /** Booked [created] of the requested sessions; [failed] couldn't be booked. */
    data class Result(
        val created: Int,
        val requested: Int,
        val failed: List<RecurrenceOccurrence>,
    ) : RecurringSubmitState

    data class Error(val message: String) : RecurringSubmitState
}
