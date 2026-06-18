@file:Suppress("PackageNaming", "MatchingDeclarationName", "MagicNumber")

package app.pantopus.android.ui.screens.scheduling._shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/**
 * The canonical Calendarly booking / page / link status pill — the single source
 * of truth that mirrors iOS `SchedulingStatusPill`. Text-only chip grammar (NO
 * leading icon), 10sp / weight 700, tight 8×3 padding, tinted fill + 1dp
 * tone-light hairline border, capsule shape. Tones: green = confirmed / active,
 * amber = pending / draft, red = declined / no-show, neutral grey = paused /
 * cancelled / completed / past / expired / secret / unavailable / waitlisted.
 *
 * Replaces the per-screen variants (HubStatusPill, MyBookings StatusPill,
 * BookResource StatusPill, ManageBooking StatusBadge, …) so the chip stops
 * drifting across screens. testTag mirrors iOS `scheduling.statusPill.<status>`.
 */
enum class SchedulingPillStatus(val backend: String, val label: String) {
    Pending("pending", "Pending"),
    Confirmed("confirmed", "Confirmed"),
    Cancelled("cancelled", "Cancelled"),
    Declined("declined", "Declined"),
    NoShow("no_show", "No-show"),
    Completed("completed", "Completed"),
    Past("past", "Past"),
    Active("active", "Active"),
    Paused("paused", "Paused"),
    Draft("draft", "Draft"),
    Secret("secret", "Private"),
    Expired("expired", "Expired"),
    Unavailable("unavailable", "Fully booked"),
    Waitlisted("waitlisted", "Waitlisted"),
    Unknown("unknown", "Status"),
    ;

    internal val tone: PillTone
        get() =
            when (this) {
                Confirmed, Active -> PillTone.Success
                Pending, Draft -> PillTone.Warning
                Declined, NoShow -> PillTone.Error
                Cancelled, Completed, Past, Paused, Secret,
                Expired, Unavailable, Waitlisted, Unknown,
                -> PillTone.Neutral
            }

    companion object {
        /** Backend wire value (incl. aliases) → pill status; tolerant of unknowns. */
        private val ALIASES: Map<String, SchedulingPillStatus> =
            mapOf(
                "pending" to Pending, "pending_approval" to Pending, "requested" to Pending,
                "confirmed" to Confirmed, "approved" to Confirmed, "accepted" to Confirmed, "booked" to Confirmed,
                "cancelled" to Cancelled, "canceled" to Cancelled,
                "declined" to Declined, "rejected" to Declined,
                "no_show" to NoShow, "noshow" to NoShow,
                "completed" to Completed, "done" to Completed,
                "past" to Past,
                "active" to Active, "live" to Active, "published" to Active,
                "paused" to Paused,
                "draft" to Draft,
                "secret" to Secret, "private" to Secret, "hidden" to Secret,
                "expired" to Expired,
                "unavailable" to Unavailable, "full" to Unavailable, "fully_booked" to Unavailable,
                "waitlisted" to Waitlisted, "waitlist" to Waitlisted,
            )

        fun fromBackend(raw: String): SchedulingPillStatus = ALIASES[raw.lowercase().replace('-', '_')] ?: Unknown
    }
}

internal enum class PillTone {
    Success,
    Warning,
    Error,
    Neutral,
    ;

    val bg: Color
        get() =
            when (this) {
                Success -> PantopusColors.successBg
                Warning -> PantopusColors.warningBg
                Error -> PantopusColors.errorBg
                Neutral -> PantopusColors.appSurfaceSunken
            }

    val fg: Color
        get() =
            when (this) {
                Success -> PantopusColors.success
                Warning -> PantopusColors.warning
                Error -> PantopusColors.error
                Neutral -> PantopusColors.appTextSecondary
            }

    /** Hairline border tint — a 1dp tone-light outline around every chip. */
    val border: Color
        get() =
            when (this) {
                Success -> PantopusColors.successLight
                Warning -> PantopusColors.warningLight
                Error -> PantopusColors.errorLight
                Neutral -> PantopusColors.appBorder
            }
}

@Composable
fun SchedulingStatusPill(
    status: SchedulingPillStatus,
    modifier: Modifier = Modifier,
) {
    val tone = status.tone
    val shape = RoundedCornerShape(Radii.pill)
    Text(
        text = status.label,
        color = tone.fg,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier =
            modifier
                .testTag("scheduling.statusPill.${status.backend}")
                .background(tone.bg, shape)
                .border(1.dp, tone.border, shape)
                .padding(horizontal = Spacing.s2, vertical = 3.dp),
    )
}

/** Convenience overload: render directly from a backend status string. */
@Composable
fun SchedulingStatusPill(
    status: String,
    modifier: Modifier = Modifier,
) = SchedulingStatusPill(SchedulingPillStatus.fromBackend(status), modifier)
