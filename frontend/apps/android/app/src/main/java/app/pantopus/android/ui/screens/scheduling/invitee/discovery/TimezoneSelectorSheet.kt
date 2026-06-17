@file:Suppress("PackageNaming")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantopus.android.ui.screens.scheduling.invitee.discovery

import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import app.pantopus.android.ui.screens.scheduling._shared.TimezoneOption
import app.pantopus.android.ui.screens.scheduling._shared.TimezonePickerSheet
import app.pantopus.android.ui.screens.scheduling._shared.defaultTimezoneOptions
import app.pantopus.android.ui.theme.PantopusColors
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val OFFSET_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

/**
 * C7 Timezone selector — a thin local wrapper around the shared
 * [TimezonePickerSheet]. Owns the search-query state and the option list
 * (common zones + the device-detected zone + the currently selected zone), and
 * reports the chosen IANA id so the picker can re-fetch slots in the new zone.
 * The selected check tints to the host's pillar [accent].
 */
@Composable
fun TimezoneSelectorSheet(
    selectedId: String,
    detectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    accent: Color = PantopusColors.primary600,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val options = remember(selectedId, detectedId) { buildTimezoneOptions(detectedId, selectedId) }

    TimezonePickerSheet(
        options = options,
        selectedId = selectedId,
        query = query,
        onQueryChange = { query = it },
        onSelect = {
            onSelect(it.id)
            onDismiss()
        },
        onDismiss = onDismiss,
        sheetState = sheetState,
        detectedId = detectedId.takeIf { it.isNotBlank() },
        accent = accent,
    )
}

/**
 * Common zones plus the detected + selected zones (so both always appear and
 * are reachable without searching). De-duplicated by IANA id.
 */
internal fun buildTimezoneOptions(
    detectedId: String,
    selectedId: String,
    now: Instant = Instant.now(),
): List<TimezoneOption> {
    val base = defaultTimezoneOptions(now)
    val byId = base.associateBy { it.id }.toMutableMap()
    listOf(detectedId, selectedId).forEach { id ->
        if (id.isNotBlank() && id !in byId) {
            optionFor(id, now)?.let { byId[id] = it }
        }
    }
    // Keep the curated order, with any extra (detected/selected) zones appended.
    return base + byId.values.filter { it.id !in base.map { b -> b.id } }
}

private fun optionFor(
    id: String,
    now: Instant,
): TimezoneOption? =
    runCatching {
        val zoned = ZonedDateTime.ofInstant(now, ZoneId.of(id))
        val offsetId = zoned.offset.id.let { if (it == "Z") "GMT" else "GMT$it" }
        TimezoneOption(
            id = id,
            name = id.substringAfterLast('/').replace('_', ' '),
            offset = offsetId,
            localTime = zoned.format(OFFSET_TIME_FORMAT),
        )
    }.getOrNull()
