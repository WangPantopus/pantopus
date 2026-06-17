@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "MatchingDeclarationName", "LongParameterList")

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import java.util.Locale

internal enum class SettingsChipTone { Success, Warning, Primary, Neutral }

private fun chipColors(tone: SettingsChipTone): Pair<Color, Color> =
    when (tone) {
        SettingsChipTone.Success -> PantopusColors.successLight to PantopusColors.success
        SettingsChipTone.Warning -> PantopusColors.warningBg to PantopusColors.warning
        SettingsChipTone.Primary -> PantopusColors.primary50 to PantopusColors.primary700
        SettingsChipTone.Neutral -> PantopusColors.appSurfaceSunken to PantopusColors.appTextStrong
    }

@Composable
internal fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
) {
    Column {
        Box(
            modifier = Modifier.fillMaxWidth().height(52.dp).background(PantopusColors.appSurfaceMuted).padding(horizontal = Spacing.s3),
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .size(36.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .clickable(onClickLabel = "Back", onClick = onBack)
                        .testTag("settingsTopBarBack"),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(icon = PantopusIcon.ChevronLeft, contentDescription = "Back", size = 22.dp, tint = PantopusColors.appText)
            }
            Text(
                title,
                color = PantopusColors.appText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
    }
}

@Composable
internal fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    accent: Color = PantopusColors.primary600,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.s3)) {
        Text(
            title.uppercase(Locale.US),
            color = accent,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 18.dp, bottom = Spacing.s2),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg)),
        ) { content() }
        helper?.let {
            Text(
                it,
                color = PantopusColors.appTextSecondary,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(start = Spacing.s1, top = Spacing.s2),
            )
        }
    }
}

@Composable
internal fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = { SettingsChevron() },
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("settingsRow_$label"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = PantopusColors.appText, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            sublabel?.let { Text(it, color = PantopusColors.appTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)) }
        }
        Spacer(Modifier.width(Spacing.s2))
        trailing()
    }
    if (showDivider) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(start = 16.dp).background(PantopusColors.appBorderSubtle))
    }
}

@Composable
internal fun SettingsChevron() {
    PantopusIconImage(icon = PantopusIcon.ChevronRight, contentDescription = null, size = 16.dp, tint = PantopusColors.appTextSecondary)
}

@Composable
internal fun SettingsChip(
    text: String,
    tone: SettingsChipTone,
    icon: PantopusIcon? = null,
) {
    val (bg, fg) = chipColors(tone)
    Row(
        modifier = Modifier.clip(RoundedCornerShape(Radii.pill)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        icon?.let { PantopusIconImage(icon = it, contentDescription = null, size = 10.dp, tint = fg) }
        Text(text.uppercase(Locale.US), color = fg, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
    }
}

@Composable
internal fun SettingsChipChevron(
    text: String,
    tone: SettingsChipTone,
    icon: PantopusIcon? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        SettingsChip(text = text, tone = tone, icon = icon)
        SettingsChevron()
    }
}

@Composable
internal fun SettingsConnectPill(
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier.clip(
                RoundedCornerShape(Radii.pill),
            ).background(accent).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text("Connect", color = PantopusColors.appTextInverse, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
    }
}

@Composable
internal fun SettingsDangerGroup(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s3)) {
        Text(
            "DANGER ZONE",
            color = PantopusColors.error,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 18.dp, bottom = Spacing.s2),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.errorBg)
                    .border(1.dp, PantopusColors.errorLight, RoundedCornerShape(Radii.lg)),
        ) { content() }
    }
}

@Composable
internal fun SettingsDangerRow(
    label: String,
    icon: PantopusIcon,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("settingsDanger_$label"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        PantopusIconImage(icon = icon, contentDescription = null, size = 17.dp, tint = PantopusColors.error)
        Text(label, color = PantopusColors.error, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
    if (showDivider) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.errorLight))
    }
}

/** Custom "New bookings" block in the Business Team group: label + helper + presentational segmented control. */
@Composable
internal fun SettingsNewBookingsBlock(accent: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text("New bookings", color = PantopusColors.appText, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        Text(
            "Choose how incoming bookings are handled.",
            color = PantopusColors.appTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        SettingsSegmented(
            options = listOf("Auto-confirm", "Approve first"),
            selectedIndex = 1,
            accent = accent,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
internal fun SettingsSegmented(
    options: List<String>,
    selectedIndex: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md)).background(PantopusColors.appSurfaceSunken).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val active = index == selectedIndex
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(Radii.sm))
                        .background(if (active) PantopusColors.appSurface else PantopusColors.appSurfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    color = if (active) accent else PantopusColors.appTextSecondary,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                )
            }
        }
    }
}

@Composable
internal fun SettingsMonoFooter(text: String) {
    Text(
        text,
        color = PantopusColors.appTextMuted,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.s4, end = Spacing.s4, top = 18.dp, bottom = Spacing.s1),
    )
}

@Composable
internal fun SettingsSavedToast(
    message: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .padding(top = Spacing.s12)
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.appText)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        PantopusIconImage(icon = PantopusIcon.Check, contentDescription = null, size = 15.dp, tint = PantopusColors.success)
        Text(message, color = PantopusColors.appTextInverse, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
