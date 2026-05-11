@file:Suppress("MagicNumber")

package app.pantopus.android.ui.screens.shared.wizard.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/** Render-state for one tile. */
sealed interface UploadSlotState {
    data object Empty : UploadSlotState

    data class Picked(val name: String, val sizeBytes: Long) : UploadSlotState

    data class Uploading(val name: String, val fraction: Float) : UploadSlotState

    data class Uploaded(val name: String, val sizeBytes: Long) : UploadSlotState

    data class Failed(val name: String, val message: String) : UploadSlotState
}

/** One upload-tile descriptor passed to [UploadSlotsBlock]. */
data class UploadSlot(
    val id: String,
    val title: String,
    val acceptHint: String,
    val state: UploadSlotState,
)

/**
 * Vertical stack of upload tiles. Each tile is 160 dp tall with a dashed
 * border (empty/failed) or solid border (picked/uploading/uploaded) and a
 * sunken background; tap fires [onPick] when empty/failed and the X-overlay
 * fires [onRemove] when there's a file.
 */
@Composable
fun UploadSlotsBlock(
    slots: List<UploadSlot>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (String) -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        slots.forEach { slot ->
            UploadTile(slot = slot, onPick = onPick, onRemove = onRemove)
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun UploadTile(
    slot: UploadSlot,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val borderColor = slot.state.borderColor()
    val isClickable = slot.state is UploadSlotState.Empty || slot.state is UploadSlotState.Failed
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurfaceSunken)
                .border(
                    width = if (slot.state is UploadSlotState.Empty || slot.state is UploadSlotState.Failed) 1.5.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(Radii.xl),
                )
                .let { mod -> if (isClickable) mod.clickable { onPick(slot.id) } else mod }
                .testTag("uploadSlot_${slot.id}")
                .semantics { contentDescription = slot.state.a11y(slot) },
    ) {
        when (val state = slot.state) {
            is UploadSlotState.Empty -> EmptyContent(slot)
            is UploadSlotState.Picked ->
                FilledContent(slot = slot, name = state.name, sizeBytes = state.sizeBytes, isUploaded = false)
            is UploadSlotState.Uploaded ->
                FilledContent(slot = slot, name = state.name, sizeBytes = state.sizeBytes, isUploaded = true)
            is UploadSlotState.Uploading -> UploadingContent(name = state.name, fraction = state.fraction)
            is UploadSlotState.Failed -> FailedContent(name = state.name, message = state.message)
        }
        if (slot.state.showsRemove) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.s2)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.appText.copy(alpha = 0.7f))
                        .clickable { onRemove(slot.id) }
                        .testTag("uploadSlot_remove_${slot.id}"),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.X,
                    contentDescription = "Remove ${slot.title}",
                    size = 16.dp,
                    tint = PantopusColors.appTextInverse,
                )
            }
        }
    }
}

@Composable
private fun EmptyContent(slot: UploadSlot) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s2, Alignment.CenterVertically),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Upload,
            contentDescription = null,
            size = 28.dp,
            tint = PantopusColors.appTextSecondary,
        )
        Text(slot.title, style = PantopusTextStyle.body, color = PantopusColors.appText)
        Text("Tap to upload", style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
        Text(slot.acceptHint, style = PantopusTextStyle.caption, color = PantopusColors.appTextMuted)
    }
}

@Composable
private fun FilledContent(
    slot: UploadSlot,
    name: String,
    sizeBytes: Long,
    isUploaded: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            PantopusIconImage(
                icon = if (isUploaded) PantopusIcon.CheckCircle else PantopusIcon.File,
                contentDescription = null,
                size = 22.dp,
                tint = if (isUploaded) PantopusColors.success else PantopusColors.primary600,
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(slot.title, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
                Text(
                    name,
                    style = PantopusTextStyle.body,
                    color = PantopusColors.appText,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(formatSize(sizeBytes), style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
    }
}

@Composable
private fun UploadingContent(
    name: String,
    fraction: Float,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("Uploading $name…", style = PantopusTextStyle.body, color = PantopusColors.appText)
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0.05f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = PantopusColors.primary600,
        )
    }
}

@Composable
private fun FailedContent(
    name: String,
    message: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            PantopusIconImage(
                icon = PantopusIcon.AlertCircle,
                contentDescription = null,
                size = 22.dp,
                tint = PantopusColors.error,
            )
            Text(name, style = PantopusTextStyle.body, color = PantopusColors.appText)
        }
        Text(message, style = PantopusTextStyle.caption, color = PantopusColors.error)
        Spacer(Modifier.height(2.dp))
        Text("Tap to retry", style = PantopusTextStyle.caption, color = PantopusColors.primary600)
    }
}

private fun UploadSlotState.borderColor(): Color =
    when (this) {
        is UploadSlotState.Empty -> PantopusColors.appBorderStrong
        is UploadSlotState.Picked, is UploadSlotState.Uploading -> PantopusColors.primary600
        is UploadSlotState.Uploaded -> PantopusColors.success
        is UploadSlotState.Failed -> PantopusColors.error
    }

private val UploadSlotState.showsRemove: Boolean
    get() =
        when (this) {
            is UploadSlotState.Picked, is UploadSlotState.Uploaded, is UploadSlotState.Failed -> true
            is UploadSlotState.Empty, is UploadSlotState.Uploading -> false
        }

private fun UploadSlotState.a11y(slot: UploadSlot): String =
    when (this) {
        is UploadSlotState.Empty -> "${slot.title}. Tap to upload. ${slot.acceptHint}"
        is UploadSlotState.Picked -> "${slot.title}. $name selected. Tap remove to reset."
        is UploadSlotState.Uploading -> "${slot.title}. Uploading $name, ${(fraction * 100).toInt()} percent."
        is UploadSlotState.Uploaded -> "${slot.title}. $name uploaded."
        is UploadSlotState.Failed -> "${slot.title}. Failed to upload $name. $message. Tap to retry."
    }

private fun formatSize(bytes: Long): String {
    val mb = bytes.toDouble() / 1_048_576.0
    if (mb >= 1) return "%.1f MB".format(mb)
    val kb = bytes.toDouble() / 1_024.0
    return "%.0f KB".format(kb)
}
