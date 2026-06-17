@file:Suppress("PackageNaming", "MagicNumber", "LongParameterList", "LongMethod")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantopus.android.ui.screens.scheduling.invitee.edge

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val ADD_TO_CALENDAR_TAG = "schedulingAddToCalendar"

/** The booking an [AddToCalendarSheet] writes to the calendar. */
data class AddToCalendarEvent(
    val title: String,
    val startUtc: String?,
    val endUtc: String?,
    val location: String? = null,
    val description: String? = null,
    val manageToken: String? = null,
    val timezone: String? = null,
)

/**
 * D8 — Add to calendar. A local action sheet surfaced from booking-confirmed /
 * manage. The native "Add to calendar" path opens the device calendar's event
 * editor (CalendarContract `ACTION_INSERT`) with the join link + a reminder;
 * Google / Outlook open the provider's web template; "Download .ics" pulls the
 * RFC-5545 invite and shares it through the FileProvider. Context-neutral chrome,
 * pillar accent on the native primary.
 */
@Composable
fun AddToCalendarSheet(
    event: AddToCalendarEvent,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    pillar: SchedulingPillar = SchedulingPillar.Personal,
    viewModel: AddToCalendarViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val icsState by viewModel.ics.collectAsStateWithLifecycle()
    var added by remember { mutableStateOf(false) }

    // When the .ics text arrives, write it to cache and open via FileProvider.
    LaunchedEffect(icsState) {
        val ready = icsState as? AddToCalendarViewModel.IcsState.Ready ?: return@LaunchedEffect
        runCatching {
            val file = File(context.cacheDir, "invite.ics").apply { writeText(ready.content) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val view =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "text/calendar")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(Intent.createChooser(view, "Add to calendar"))
        }
        viewModel.consume()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PantopusColors.appSurface,
        modifier = modifier.testTag(ADD_TO_CALENDAR_TAG),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s6)) {
            Text(
                text = "Add to your calendar",
                style = PantopusTextStyle.h3,
                color = PantopusColors.appText,
                modifier = Modifier.padding(horizontal = Spacing.s4),
            )
            RecapChip(event = event, pillar = pillar)

            Box(modifier = Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s3)) {
                NativeAddButton(
                    added = added,
                    accent = pillar.accent,
                    onClick = {
                        added = runCatching { context.startActivity(buildInsertIntent(event)) }.isSuccess
                    },
                )
            }

            Text(
                text = "MORE OPTIONS",
                style = PantopusTextStyle.overline,
                color = PantopusColors.appTextSecondary,
                modifier = Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s1),
            )
            Column(
                modifier =
                    Modifier
                        .padding(horizontal = Spacing.s4)
                        .clip(RoundedCornerShape(Radii.xl))
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl)),
            ) {
                ProviderRow(
                    icon = PantopusIcon.CalendarPlus,
                    label = "Google Calendar",
                    sub = "Opens in your browser",
                    onClick = { runCatching { context.startActivity(webIntent(googleUrl(event))) } },
                )
                RowDivider()
                ProviderRow(
                    icon = PantopusIcon.CalendarPlus,
                    label = "Outlook",
                    sub = "Opens in your browser",
                    onClick = { runCatching { context.startActivity(webIntent(outlookUrl(event))) } },
                )
                RowDivider()
                ProviderRow(
                    icon = PantopusIcon.Download,
                    label = "Download .ics file",
                    sub = "Works with any calendar app",
                    loading = icsState is AddToCalendarViewModel.IcsState.Loading,
                    enabled = event.manageToken != null,
                    onClick = { event.manageToken?.let(viewModel::downloadIcs) },
                )
            }

            Row(
                modifier = Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s3),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.BellRing,
                    contentDescription = null,
                    size = 13.dp,
                    tint = PantopusColors.appTextMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = "We'll add the event with the join link and a reminder.",
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                )
            }
            (icsState as? AddToCalendarViewModel.IcsState.Error)?.let {
                Text(
                    text = it.message,
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.error,
                    modifier = Modifier.padding(horizontal = Spacing.s4),
                )
            }
        }
    }
}

@Composable
private fun RecapChip(
    event: AddToCalendarEvent,
    pillar: SchedulingPillar,
) {
    val zone = zoneOf(event.timezone)
    Row(
        modifier =
            Modifier
                .padding(horizontal = Spacing.s4, vertical = Spacing.s2)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(pillar.accentBg)
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(icon = PantopusIcon.Calendar, contentDescription = null, size = 14.dp, tint = pillar.accent)
        Text(
            text = "${event.title} · ${formatWhenRange(event.startUtc, event.endUtc, zone)}",
            style = PantopusTextStyle.caption,
            fontWeight = FontWeight.SemiBold,
            color = pillar.accent,
        )
    }
}

@Composable
private fun NativeAddButton(
    added: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(if (added) PantopusColors.successBg else accent)
                .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PantopusIconImage(
            icon = if (added) PantopusIcon.CheckCircle else PantopusIcon.CalendarPlus,
            contentDescription = null,
            size = 17.dp,
            tint = if (added) PantopusColors.success else PantopusColors.appTextInverse,
            modifier = Modifier.padding(end = Spacing.s2),
        )
        Text(
            text = if (added) "Added to calendar" else "Add to calendar",
            style = PantopusTextStyle.body,
            color = if (added) PantopusColors.success else PantopusColors.appTextInverse,
        )
    }
}

@Composable
private fun ProviderRow(
    icon: PantopusIcon,
    label: String,
    sub: String,
    onClick: () -> Unit,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .clickable(enabled = enabled && !loading, onClick = onClick)
                .padding(horizontal = Spacing.s3, vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(Radii.md)).background(PantopusColors.appSurfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = icon, contentDescription = null, size = 18.dp, tint = PantopusColors.appTextSecondary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) PantopusColors.appText else PantopusColors.appTextMuted,
            )
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Shimmer(width = 80.dp, height = 8.dp, cornerRadius = Radii.xs)
                    Text(text = "Preparing your file", style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
                }
            } else {
                Text(text = sub, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
            }
        }
        if (!loading) {
            PantopusIconImage(icon = PantopusIcon.ChevronRight, contentDescription = null, size = 16.dp, tint = PantopusColors.appTextMuted)
        }
    }
}

@Composable
private fun RowDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
}

// ─── Intent builders ────────────────────────────────────────────────────────

private fun buildInsertIntent(event: AddToCalendarEvent): Intent {
    val start = parseInstant(event.startUtc)?.toEpochMilli()
    val end = parseInstant(event.endUtc)?.toEpochMilli()
    return Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, event.title)
        event.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        event.description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
        start?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
        end?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
    }
}

private fun webIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

private val ICS_UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"))

private fun icsStamp(utc: String?): String? = parseInstant(utc)?.let { ICS_UTC.format(it) }

private fun googleUrl(event: AddToCalendarEvent): String {
    val dates = "${icsStamp(event.startUtc).orEmpty()}/${icsStamp(event.endUtc).orEmpty()}"
    return Uri.parse("https://calendar.google.com/calendar/render").buildUpon()
        .appendQueryParameter("action", "TEMPLATE")
        .appendQueryParameter("text", event.title)
        .appendQueryParameter("dates", dates)
        .apply {
            event.description?.let { appendQueryParameter("details", it) }
            event.location?.let { appendQueryParameter("location", it) }
        }
        .build()
        .toString()
}

private fun outlookUrl(event: AddToCalendarEvent): String =
    Uri.parse("https://outlook.live.com/calendar/0/deeplink/compose").buildUpon()
        .appendQueryParameter("path", "/calendar/action/compose")
        .appendQueryParameter("rru", "addevent")
        .appendQueryParameter("subject", event.title)
        .apply {
            parseInstant(event.startUtc)?.let { appendQueryParameter("startdt", it.toString()) }
            parseInstant(event.endUtc)?.let { appendQueryParameter("enddt", it.toString()) }
            event.description?.let { appendQueryParameter("body", it) }
            event.location?.let { appendQueryParameter("location", it) }
        }
        .build()
        .toString()
