@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.hub.today

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.OfflineBanner
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

@Composable
fun TodayDetailScreen(
    onBack: () -> Unit,
    viewModel: TodayDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .semantics { contentDescription = "todayDetail" },
    ) {
        TodayTopBar(onBack)
        OfflineBanner(modifier = Modifier.fillMaxWidth())
        when (val current = state) {
            TodayDetailViewModel.UiState.Loading -> LoadingBody()
            TodayDetailViewModel.UiState.Empty ->
                EmptyState(
                    icon = PantopusIcon.Sun,
                    headline = "Nothing to show yet",
                    subcopy = "Add a home address to see weather, air quality, and your day at a glance.",
                )
            is TodayDetailViewModel.UiState.Error ->
                EmptyState(
                    icon = PantopusIcon.AlertCircle,
                    headline = "Couldn't load today",
                    subcopy = current.message,
                    ctaTitle = "Try again",
                    onCta = { viewModel.refresh() },
                )
            is TodayDetailViewModel.UiState.Loaded -> LoadedBody(current.content)
        }
    }
}

@Composable
private fun TodayTopBar(onBack: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(PantopusColors.appSurface),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Today", style = PantopusTextStyle.h3, color = PantopusColors.appText)
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = Spacing.s2)
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.ChevronLeft,
                contentDescription = null,
                size = 22.dp,
                tint = PantopusColors.appText,
            )
        }
    }
}

@Composable
private fun LoadingBody() {
    Column(modifier = Modifier.padding(Spacing.s4), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        SkeletonBar(120.dp)
        SkeletonBar(72.dp)
        SkeletonBar(72.dp)
    }
}

@Composable
private fun SkeletonBar(height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurfaceSunken),
    )
}

@Composable
private fun LoadedBody(content: TodayDetailContent) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        if (content.hasWeather) {
            WeatherHero(content)
        }
        content.aqiLabel?.let { aqi ->
            StatCard(
                icon = PantopusIcon.Leaf,
                title = "Air quality",
                value = content.aqiValue?.let { "$it · $aqi" } ?: aqi,
                tag = "todayDetailAQI",
            )
        }
        content.commute?.let { commute ->
            StatCard(
                icon = PantopusIcon.Navigation,
                title = "Commute",
                value = commute,
                tag = "todayDetailCommute",
            )
        }
        EventsSection(content.events)
    }
}

@Composable
private fun WeatherHero(content: TodayDetailContent) {
    Row(
        modifier = cardModifier().semantics { contentDescription = "todayDetailWeather" }.padding(Spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(PantopusColors.personalBg),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(PantopusIcon.Sun, null, size = 28.dp, tint = PantopusColors.primary600)
        }
        Column {
            content.temperatureFahrenheit?.let {
                Text("$it°", style = PantopusTextStyle.h1, color = PantopusColors.appText)
            }
            content.conditions?.let {
                Text(it, style = PantopusTextStyle.body, color = PantopusColors.appTextSecondary)
            }
        }
    }
}

@Composable
private fun StatCard(icon: PantopusIcon, title: String, value: String, tag: String) {
    Row(
        modifier = cardModifier().semantics { contentDescription = "$tag $title $value" }.padding(Spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        PantopusIconImage(icon, null, size = 20.dp, tint = PantopusColors.primary600)
        Column {
            Text(title, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
            Text(value, style = PantopusTextStyle.body, color = PantopusColors.appText)
        }
    }
}

@Composable
private fun EventsSection(events: List<TodayEventRow>) {
    Column(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "todayDetailEvents" },
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Text(
            text = "TODAY'S EVENTS",
            style = PantopusTextStyle.overline,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.semantics { heading() },
        )
        if (events.isEmpty()) {
            Box(modifier = cardModifier().padding(Spacing.s4)) {
                Text(
                    "No events scheduled for today.",
                    style = PantopusTextStyle.body,
                    color = PantopusColors.appTextSecondary,
                )
            }
        } else {
            Column(modifier = cardModifier()) {
                events.forEachIndexed { index, event ->
                    if (index > 0) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(PantopusColors.appBorderSubtle),
                        )
                    }
                    EventRow(event)
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: TodayEventRow) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s4)
                .semantics { contentDescription = "${event.title}, ${event.timeLabel}, ${event.typeLabel}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        PantopusIconImage(event.icon, null, size = 18.dp, tint = PantopusColors.home)
        Column {
            Text(event.title, style = PantopusTextStyle.body, color = PantopusColors.appText)
            Text(
                "${event.timeLabel} · ${event.typeLabel}",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
        }
    }
}

private fun cardModifier(): Modifier =
    Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(Radii.lg))
        .background(PantopusColors.appSurface)
        .border(1.dp, PantopusColors.appBorderSubtle, RoundedCornerShape(Radii.lg))
