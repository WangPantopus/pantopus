@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.scheduling.bookings

import app.pantopus.android.data.api.models.scheduling.BookingDetailResponse
import app.pantopus.android.data.scheduling.SchedulingOwner
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillStatus
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.theme.PantopusIcon

/** One intake answer rendered in the expandable card. */
data class IntakeAnswer(val label: String, val value: String)

/** A booking's location, mapped from the event type's `location_mode`. */
data class LocationInfo(val icon: PantopusIcon, val value: String, val sub: String)

/** The mapped detail view model the E2 screen renders. */
data class BookingDetailData(
    val id: String,
    val owner: SchedulingOwner,
    val pillar: SchedulingPillar,
    val ownerLabel: String,
    val status: BookingStatus,
    val pillStatus: SchedulingPillStatus,
    val eventName: String,
    val whenRange: String,
    val startUtc: String?,
    val endUtc: String?,
    val requesterName: String,
    val requesterInitials: String,
    val requesterSub: String,
    val location: LocationInfo?,
    val intakeAnswers: List<IntakeAnswer>,
    val assignedHostInitials: String?,
    val cancelledNote: String?,
    val rescheduled: Boolean,
    val hasPayment: Boolean,
    val currency: String?,
    // Server-/status-derived gates for the dock + kebab actions.
    val canApprove: Boolean,
    val canReschedule: Boolean,
    val canReassign: Boolean,
    val canCancel: Boolean,
) {
    val isActive: Boolean get() = status == BookingStatus.Pending || status == BookingStatus.Confirmed
}

/** E2 lifecycle state. */
sealed interface BookingDetailUiState {
    data object Loading : BookingDetailUiState

    data class Loaded(val data: BookingDetailData) : BookingDetailUiState

    data class Error(val message: String) : BookingDetailUiState
}

/** Local E3 Approve/Decline sheet state (driven by [BookingDetailViewModel]). */
data class ApproveDeclineSheetState(
    val pillar: SchedulingPillar,
    val requesterName: String,
    val requesterInitials: String,
    val requesterSub: String,
    val slotLabel: String,
    val intakeCount: Int,
    val declineExpanded: Boolean = false,
    val selectedReason: String? = null,
    val note: String = "",
    val submitting: Boolean = false,
    val approving: Boolean = false,
    val errorMessage: String? = null,
)

/** The fixed decline reasons the E3 sheet offers (last is free-text "Other"). */
val DECLINE_REASONS: List<String> =
    listOf("Time doesn't work", "Fully booked", "Not a fit", "Other")

/** Map a `location_mode` to its icon + labels. */
fun locationInfoFor(mode: String?): LocationInfo? =
    when (mode) {
        "video" -> LocationInfo(PantopusIcon.Video, "Video call", "Link sent on confirm")
        "phone" -> LocationInfo(PantopusIcon.Phone, "Phone call", "We'll share the number")
        "in_person" -> LocationInfo(PantopusIcon.MapPin, "In person", "Location shared on confirm")
        "custom" -> LocationInfo(PantopusIcon.MapPin, "Custom location", "Details on confirm")
        "ask" ->
            LocationInfo(
                PantopusIcon.MessageCircle,
                "Decide together",
                "You'll agree on a place",
            )
        else -> null
    }

/** Build the mapped detail data from the host detail response + resolved owner. */
fun BookingDetailResponse.toDetailData(owner: SchedulingOwner): BookingDetailData {
    val status = BookingStatus.fromRaw(booking.status)
    val pillar = owner.toPillar()
    val isActive = status == BookingStatus.Pending || status == BookingStatus.Confirmed
    val name = booking.inviteeName?.takeIf { it.isNotBlank() } ?: "Guest"
    val sub = booking.inviteeEmail?.takeIf { it.isNotBlank() } ?: "Invitee"
    val answers =
        booking.intakeAnswers
            ?.entries
            ?.mapNotNull { (k, v) ->
                v?.toString()?.takeIf { it.isNotBlank() }?.let {
                    IntakeAnswer(
                        k,
                        it,
                    )
                }
            }
            .orEmpty()
    return BookingDetailData(
        id = booking.id,
        owner = owner,
        pillar = pillar,
        ownerLabel = ownerLabelFor(pillar),
        status = status,
        pillStatus = status.toPillStatus(),
        eventName = eventType?.name?.takeIf { it.isNotBlank() } ?: "Booking",
        whenRange = rangeLabel(booking.startAt, booking.endAt),
        startUtc = booking.startAt,
        endUtc = booking.endAt,
        requesterName = name,
        requesterInitials = initialsOf(name),
        requesterSub = sub,
        location = locationInfoFor(eventType?.locationMode),
        intakeAnswers = answers,
        assignedHostInitials =
            booking.hostUserId?.takeIf {
                pillar == SchedulingPillar.Business
            }?.let { initialsOf(it.take(2)) },
        cancelledNote =
            booking.cancelReason?.takeIf {
                status == BookingStatus.Cancelled || status == BookingStatus.Declined
            },
        rescheduled = !booking.previousStartAt.isNullOrBlank(),
        hasPayment = !booking.paymentId.isNullOrBlank(),
        currency = null,
        canApprove = status == BookingStatus.Pending,
        canReschedule = isActive,
        canReassign = isActive && pillar != SchedulingPillar.Personal,
        canCancel = status == BookingStatus.Confirmed,
    )
}

private fun ownerLabelFor(pillar: SchedulingPillar): String =
    when (pillar) {
        SchedulingPillar.Personal -> "Personal"
        SchedulingPillar.Home -> "Home"
        SchedulingPillar.Business -> "Business"
    }
