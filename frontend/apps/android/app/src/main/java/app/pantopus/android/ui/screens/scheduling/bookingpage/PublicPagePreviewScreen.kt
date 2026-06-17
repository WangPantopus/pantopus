@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "TooManyFunctions")

package app.pantopus.android.ui.screens.scheduling.bookingpage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.api.models.scheduling.PublicEventTypeView
import app.pantopus.android.ui.components.ErrorState
import app.pantopus.android.ui.screens.scheduling._shared.PausedExpiredUnavailableState
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingLoadingSkeleton
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingTerminalState
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

@Composable
fun PublicPagePreviewScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: PublicPagePreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) {
        PreviewBar(onExit = onBack)
        PreviewCaption()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                PreviewUiState.Loading -> SchedulingLoadingSkeleton(modifier = Modifier.fillMaxSize(), rows = 4)
                is PreviewUiState.Rendered -> RenderedBody(s)
                is PreviewUiState.AllHidden -> AllHiddenBody(s)
                is PreviewUiState.Notice ->
                    PausedExpiredUnavailableState(
                        state = SchedulingTerminalState.Paused,
                        pillar = SchedulingPillar.Personal,
                        title = s.title,
                        body = s.body,
                    )
                is PreviewUiState.Error ->
                    ErrorState(headline = "Couldn't load preview", message = s.message, onRetry = viewModel::refresh)
            }
        }
    }
}

@Composable
private fun PreviewBar(onExit: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PantopusColors.appText)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(icon = PantopusIcon.Eye, contentDescription = null, size = 16.dp, tint = PantopusColors.appTextInverse)
        Text(
            "Previewing your booking page",
            color = PantopusColors.appTextInverse,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier =
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.appSurfaceDark)
                    .clickable(onClickLabel = "Close preview", onClick = onExit)
                    .testTag("previewExit"),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.X,
                contentDescription = "Close preview",
                size = 15.dp,
                tint = PantopusColors.appTextInverse,
            )
        }
    }
}

@Composable
private fun PreviewCaption() {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 3.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.appSurfaceSunken)
                    .padding(horizontal = 11.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            PantopusIconImage(icon = PantopusIcon.EyeOff, contentDescription = null, size = 11.dp, tint = PantopusColors.appTextSecondary)
            Text(
                "Preview only. Nothing here is bookable.",
                color = PantopusColors.appTextSecondary,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun RenderedBody(s: PreviewUiState.Rendered) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
            verticalArrangement = Arrangement.spacedBy(Spacing.s4),
        ) {
            PublicHeader(s.header)
            s.eventTypes.forEachIndexed { index, et -> EventTypeCard(et, selected = index == 0) }
        }
        InertPickTimeCta()
    }
}

@Composable
private fun AllHiddenBody(s: PreviewUiState.AllHidden) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        PublicHeader(s.header)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.xl))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorderStrong, RoundedCornerShape(Radii.xl))
                    .padding(vertical = Spacing.s6, horizontal = Spacing.s5),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(Radii.lg)).background(PantopusColors.appSurfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.CalendarX,
                    contentDescription = null,
                    size = 20.dp,
                    tint = PantopusColors.appTextSecondary,
                )
            }
            Text("No services are visible yet", color = PantopusColors.appText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                "Turn one on so people see something to book.",
                color = PantopusColors.appTextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun PublicHeader(header: PreviewHeader) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BLAvatar(initials = header.initials, pillar = SchedulingPillar.Personal, diameter = 64.dp, fontSize = 22.sp)
        Text(
            header.name.ifBlank { "Your name" },
            color = PantopusColors.appText,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(top = 7.dp),
        )
        if (header.headline.isNotBlank()) {
            Text(
                header.headline,
                color = PantopusColors.primary700,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (header.blurb.isNotBlank()) {
            Text(
                header.blurb,
                color = PantopusColors.appTextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = Spacing.s1),
            )
        }
    }
}

@Composable
private fun EventTypeCard(et: PublicEventTypeView, selected: Boolean) {
    val duration = et.defaultDuration ?: et.durations.firstOrNull() ?: 30
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(
                    if (selected) 1.5.dp else 1.dp,
                    if (selected) PantopusColors.primary600 else PantopusColors.appBorder,
                    RoundedCornerShape(Radii.xl),
                )
                .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(if (selected) PantopusColors.primary50 else PantopusColors.appSurfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = serviceIcon(et.locationMode),
                contentDescription = null,
                size = 18.dp,
                tint = if (selected) PantopusColors.primary600 else PantopusColors.appTextStrong,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(et.name.orEmpty(), color = PantopusColors.appText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Row(
                modifier = Modifier.padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                    PantopusIconImage(
                        icon = PantopusIcon.Clock,
                        contentDescription = null,
                        size = 11.dp,
                        tint = PantopusColors.appTextSecondary,
                    )
                    Text("$duration min", color = PantopusColors.appTextSecondary, fontSize = 11.5.sp)
                }
                ModeChip(et.locationMode)
            }
        }
        PantopusIconImage(icon = PantopusIcon.ChevronRight, contentDescription = null, size = 18.dp, tint = PantopusColors.appTextMuted)
    }
}

@Composable
private fun ModeChip(locationMode: String?) {
    val label =
        when (locationMode) {
            "video" -> "Video call"
            "phone" -> "Phone call"
            "in_person" -> "In person"
            else -> "Online"
        }
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.primary50)
                .padding(horizontal = Spacing.s2, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        PantopusIconImage(icon = serviceIcon(locationMode), contentDescription = null, size = 10.dp, tint = PantopusColors.primary700)
        Text(label, color = PantopusColors.primary700, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

@Composable
private fun InertPickTimeCta() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .padding(horizontal = Spacing.s4, top = Spacing.s2, bottom = Spacing.s4),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(Radii.lg)).background(PantopusColors.primary600),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Text("Pick a time", color = PantopusColors.appTextInverse, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                PantopusIconImage(
                    icon = PantopusIcon.ArrowRight,
                    contentDescription = null,
                    size = 16.dp,
                    tint = PantopusColors.appTextInverse,
                )
            }
        }
    }
}
