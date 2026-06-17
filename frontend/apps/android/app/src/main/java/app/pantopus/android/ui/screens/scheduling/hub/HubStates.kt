@file:Suppress("PackageNaming", "MagicNumber", "LongMethod")

package app.pantopus.android.ui.screens.scheduling.hub

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingLoadingSkeleton
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

private data class EmptyCopy(val headline: String, val subhead: String, val steps: String)

private fun emptyCopy(pillar: SchedulingPillar): EmptyCopy =
    when (pillar) {
        SchedulingPillar.Personal ->
            EmptyCopy(
                "Set up your booking link",
                "Create a link anyone can use to book time with you. Pick your hours and the meeting types you offer.",
                "Set your hours, add a meeting type, then share your link.",
            )
        SchedulingPillar.Home ->
            EmptyCopy(
                "Set up family scheduling",
                "Let people book any free member during their own hours. Pick who's scheduled and how times combine.",
                "Pick members, choose how times combine, then share your link.",
            )
        SchedulingPillar.Business ->
            EmptyCopy(
                "Set up business booking",
                "Create a link clients can use to book you. Add a service, seat your team, and choose how bookings confirm.",
                "Claim your link, add a service, then share it with clients.",
            )
    }

@Composable
internal fun HubEmptyState(
    pillar: SchedulingPillar,
    onSetUp: () -> Unit,
) {
    val copy = emptyCopy(pillar)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.padding(top = Spacing.s8).size(88.dp).clip(CircleShape).background(pillar.accentBg),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = PantopusIcon.CalendarPlus, contentDescription = null, size = 38.dp, tint = pillar.accent)
        }
        Spacer(Modifier.height(Spacing.s4 + 2.dp))
        Text(copy.headline, style = PantopusTextStyle.h3, color = PantopusColors.appText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.s2))
        Text(
            copy.subhead,
            style = PantopusTextStyle.small,
            color = PantopusColors.appTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.s4),
        )
        Spacer(Modifier.height(Spacing.s4 + 2.dp))
        // Amber "three quick steps" banner + primary CTA.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CARD_RADIUS))
                    .background(PantopusColors.warningBg)
                    .border(1.dp, PantopusColors.warningLight, RoundedCornerShape(CARD_RADIUS))
                    .padding(Spacing.s4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(PantopusColors.warningBg),
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.WandSparkles,
                        contentDescription = null,
                        size = 19.dp,
                        tint = PantopusColors.warning,
                    )
                }
                Spacer(Modifier.width(Spacing.s3))
                Column(Modifier.weight(1f)) {
                    Text("Three quick steps", color = PantopusColors.appText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(copy.steps, color = PantopusColors.appTextStrong, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(Spacing.s3))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(Radii.lg))
                        .background(pillar.accent)
                        .clickable(onClick = onSetUp)
                        .testTag(HubTags.EMPTY_CTA),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2, Alignment.CenterHorizontally),
            ) {
                Text(copy.headline, color = PantopusColors.appTextInverse, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                PantopusIconImage(
                    icon = PantopusIcon.ArrowRight,
                    contentDescription = null,
                    size = 16.dp,
                    tint = PantopusColors.appTextInverse,
                )
            }
        }
        Spacer(Modifier.height(Spacing.s5))
        // Dashed "not set up" destination preview rows.
        listOf(
            PantopusIcon.Grid3x3 to "Event types",
            PantopusIcon.Clock to "Availability",
            PantopusIcon.CalendarCog to "Connected calendars",
        ).forEach { (icon, label) ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.s2)
                        .clip(RoundedCornerShape(Radii.lg))
                        .background(PantopusColors.appSurface)
                        .border(1.dp, PantopusColors.appBorderStrong, RoundedCornerShape(Radii.lg))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PantopusIconImage(icon = icon, contentDescription = null, size = 18.dp, tint = PantopusColors.appTextMuted)
                Spacer(Modifier.width(Spacing.s3))
                Text(
                    label,
                    color = PantopusColors.appTextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text("Not set up", color = PantopusColors.appTextMuted, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
internal fun HubErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp, start = Spacing.s4, end = Spacing.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(PantopusColors.appSurfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = PantopusIcon.CloudOff, contentDescription = null, size = 28.dp, tint = PantopusColors.appTextSecondary)
        }
        Spacer(Modifier.height(Spacing.s4))
        Text("Couldn't load scheduling", style = PantopusTextStyle.h3, color = PantopusColors.appText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.s2))
        Text(message, style = PantopusTextStyle.small, color = PantopusColors.appTextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.s4))
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.lg))
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s3)
                    .testTag(HubTags.RETRY),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(icon = PantopusIcon.RefreshCw, contentDescription = null, size = 16.dp, tint = PantopusColors.appText)
            Text("Try again", color = PantopusColors.appText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
internal fun HubSkeleton() {
    SchedulingLoadingSkeleton(
        modifier = Modifier.fillMaxWidth().testTag(HubTags.SKELETON),
        rows = 4,
    )
}
