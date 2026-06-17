@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "MatchingDeclarationName", "CyclomaticComplexMethod", "LongParameterList")

package app.pantopus.android.ui.screens.scheduling.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

internal enum class NotifChipState { On, Off, Disabled, Locked }

private val ACCENT = PantopusColors.primary600
private val ACCENT_BG = PantopusColors.primary50

@Composable
internal fun NotifChannelChip(
    letter: String,
    chipState: NotifChipState,
) {
    val on = chipState == NotifChipState.On || chipState == NotifChipState.Locked
    val bg =
        when (chipState) {
            NotifChipState.On, NotifChipState.Locked -> ACCENT
            NotifChipState.Off -> PantopusColors.appSurface
            NotifChipState.Disabled -> PantopusColors.appSurfaceSunken
        }
    val fg =
        when (chipState) {
            NotifChipState.On, NotifChipState.Locked -> PantopusColors.appTextInverse
            NotifChipState.Off -> PantopusColors.appTextMuted
            NotifChipState.Disabled -> PantopusColors.appBorderStrong
        }
    val border =
        if (chipState == NotifChipState.Off) {
            PantopusColors.appBorderStrong
        } else if (on) {
            ACCENT
        } else {
            PantopusColors.appBorder
        }
    Box(
        modifier =
            Modifier.size(
                22.dp,
            ).clip(RoundedCornerShape(Radii.sm)).background(bg).border(1.dp, border, RoundedCornerShape(Radii.sm)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = fg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        if (chipState == NotifChipState.Locked) {
            Box(modifier = Modifier.align(Alignment.BottomEnd).size(8.dp), contentAlignment = Alignment.Center) {
                PantopusIconImage(icon = PantopusIcon.Lock, contentDescription = null, size = 7.dp, tint = PantopusColors.appTextInverse)
            }
        }
    }
}

@Composable
internal fun NotifCategoryCard(
    label: String,
    helper: String,
    disabled: Boolean,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s3)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                    .alpha(if (disabled) 0.55f else 1f),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(ACCENT_BG)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label.uppercase(java.util.Locale.US),
                    color = ACCENT,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    modifier = Modifier.weight(1f),
                )
                ColumnLetter("P")
                ColumnLetter("E")
                Row(
                    modifier = Modifier.width(22.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "S",
                        color = PantopusColors.appTextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                    )
                }
            }
            content()
        }
        Text(
            helper,
            color = PantopusColors.appTextSecondary,
            fontSize = 11.5.sp,
            modifier = Modifier.padding(start = Spacing.s1, top = Spacing.s2),
        )
    }
}

@Composable
private fun ColumnLetter(text: String) {
    Box(modifier = Modifier.width(22.dp), contentAlignment = Alignment.Center) {
        Text(text, color = PantopusColors.appTextMuted, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

@Composable
internal fun NotifMatrixRow(
    row: NotifRow,
    isAttendee: Boolean,
    paused: Boolean,
    pushOff: Boolean,
    showDivider: Boolean,
    onToggle: () -> Unit,
) {
    val pState =
        when {
            paused || isAttendee -> NotifChipState.Disabled
            pushOff -> NotifChipState.Disabled
            row.locked -> if (row.enabled) NotifChipState.Locked else NotifChipState.Off
            else -> if (row.enabled) NotifChipState.On else NotifChipState.Off
        }
    val eState =
        when {
            paused -> NotifChipState.Disabled
            row.locked -> if (row.enabled) NotifChipState.Locked else NotifChipState.Off
            else -> if (row.enabled) NotifChipState.On else NotifChipState.Off
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (paused || row.locked) Modifier else Modifier.clickable(onClick = onToggle))
                .padding(horizontal = 16.dp, vertical = 11.dp)
                .testTag("notifRow_${if (isAttendee) "att" else "me"}_${row.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.label, color = PantopusColors.appText, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            row.sub?.let { Text(it, color = PantopusColors.appTextSecondary, fontSize = 11.5.sp, modifier = Modifier.padding(top = 1.dp)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            NotifChannelChip("P", pState)
            NotifChannelChip("E", eState)
            NotifChannelChip("S", NotifChipState.Disabled)
        }
    }
    if (showDivider) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(start = 16.dp).background(PantopusColors.appBorderSubtle))
    }
}

@Composable
internal fun ReminderLeadTime(
    selected: List<Int>,
    paused: Boolean,
    onToggle: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorderSubtle))
        Spacer(Modifier.height(Spacing.s2 + 1.dp))
        Text("Send reminders", color = PantopusColors.appTextStrong, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            REMINDER_PRESETS.forEach { (minutes, label) ->
                val active = minutes in selected
                Row(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(Radii.pill))
                            .background(if (active) ACCENT else PantopusColors.appSurface)
                            .border(1.dp, if (active) ACCENT else PantopusColors.appBorderStrong, RoundedCornerShape(Radii.pill))
                            .then(if (paused) Modifier.alpha(0.5f) else Modifier.clickable { onToggle(minutes) })
                            .padding(horizontal = 13.dp, vertical = 7.dp)
                            .testTag("reminderChip_$minutes"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                ) {
                    if (active) {
                        PantopusIconImage(
                            icon = PantopusIcon.Check,
                            contentDescription = null,
                            size = 12.dp,
                            tint = PantopusColors.appTextInverse,
                        )
                    }
                    Text(
                        label,
                        color = if (active) PantopusColors.appTextInverse else PantopusColors.appTextStrong,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                    )
                }
            }
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .border(1.dp, PantopusColors.appBorderStrong, RoundedCornerShape(Radii.pill))
                        .alpha(0.6f)
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                        .testTag("reminderChipAdd"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                PantopusIconImage(icon = PantopusIcon.Plus, contentDescription = null, size = 12.dp, tint = PantopusColors.appTextSecondary)
                Text("Add", color = PantopusColors.appTextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
            }
        }
    }
}

@Composable
internal fun NotifLegend() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        listOf("P · Push", "E · Email").forEach {
            Text(it, color = PantopusColors.appTextMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("S · SMS", color = PantopusColors.appTextMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            PantopusIconImage(icon = PantopusIcon.Lock, contentDescription = null, size = 9.dp, tint = PantopusColors.appTextMuted)
            Text("soon", color = PantopusColors.appTextMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun NotifPauseBanner() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s3, vertical = Spacing.s1)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.warningBg)
                .border(1.dp, PantopusColors.warningLight, RoundedCornerShape(Radii.lg))
                .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        PantopusIconImage(icon = PantopusIcon.BellOff, contentDescription = null, size = 18.dp, tint = PantopusColors.warning)
        Column(Modifier.weight(1f)) {
            Text("Notifications paused", color = PantopusColors.appText, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
            Text("Emergency alerts still come through", color = PantopusColors.appTextSecondary, fontSize = 11.5.sp)
        }
    }
}

@Composable
internal fun NotifPushOffNotice(onOpenSettings: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s3, vertical = Spacing.s1)
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.errorBg)
                .border(1.dp, PantopusColors.errorLight, RoundedCornerShape(Radii.md))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(icon = PantopusIcon.BellOff, contentDescription = null, size = 15.dp, tint = PantopusColors.error)
        Text(
            "Push is off for Pantopus. Turn it on in Settings to get booking alerts.",
            color = PantopusColors.appTextStrong,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.errorLight, RoundedCornerShape(Radii.pill))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 11.dp, vertical = 5.dp),
        ) {
            Text("Settings", color = PantopusColors.error, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
        }
    }
}

@Composable
internal fun NotifOverline(
    text: String,
    color: Color = ACCENT,
) {
    Text(
        text.uppercase(java.util.Locale.US),
        color = color,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = Spacing.s2),
    )
}
