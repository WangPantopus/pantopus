@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "UnusedParameter")

package app.pantopus.android.ui.screens.scheduling.eventtypes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.api.models.scheduling.ConnectedCalendarDto
import app.pantopus.android.ui.components.ErrorState
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay

private val PROVIDER_ICONS =
    mapOf(
        "google" to PantopusIcon.CalendarDays,
        "apple" to PantopusIcon.Calendar,
        "outlook" to PantopusIcon.CalendarClock,
    )

// Friendly provider names per design `connected-calendars-frames.jsx` PROVIDERS.
private val PROVIDER_NAMES =
    mapOf(
        "google" to "Google Calendar",
        "apple" to "Apple Calendar",
        "outlook" to "Outlook",
    )

@Composable
fun ConnectedCalendarsScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: ConnectedCalendarsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    var toastText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(toast) {
        toast?.let {
            toastText = it
            viewModel.toastConsumed()
        }
    }
    LaunchedEffect(toastText) {
        if (toastText != null) {
            delay(2200)
            toastText = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            EtTopBar(title = "Connected calendars", onBack = onBack)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val s = state) {
                    ConnectedCalendarsUiState.Loading ->
                        Box(modifier = Modifier.fillMaxSize().padding(Spacing.s4)) {
                            app.pantopus.android.ui.components.Shimmer(width = 240.dp, height = 120.dp, cornerRadius = Radii.xl)
                        }
                    is ConnectedCalendarsUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                    is ConnectedCalendarsUiState.Loaded ->
                        if (s.calendars.isEmpty()) {
                            ComingSoon()
                        } else {
                            ConnectedList(calendars = s.calendars)
                        }
                }
            }
        }
        toastText?.let { msg ->
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = Spacing.s12)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.appText)
                        .padding(horizontal = Spacing.s4, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.CalendarCog,
                    contentDescription = null,
                    size = 15.dp,
                    tint = PantopusColors.primary300,
                )
                Text(text = msg, color = PantopusColors.appTextInverse, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
internal fun ComingSoon() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.xl))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                    .padding(vertical = Spacing.s6, horizontal = Spacing.s5),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(Radii.xl)).background(PantopusColors.primary50),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.CalendarCog,
                    contentDescription = null,
                    size = 26.dp,
                    tint = PantopusColors.primary600,
                )
            }
            Spacer(Modifier.height(Spacing.s3))
            Text(
                "Calendar sync is coming soon",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.s2))
            Text(
                "We'll let you know when you can connect Google, Apple, and Outlook to check for conflicts.",
                fontSize = 12.5.sp,
                color = PantopusColors.appTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.s2),
            )
            Spacer(Modifier.height(Spacing.s5))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                listOf("google", "apple", "outlook").forEach { provider ->
                    ProviderTile(provider = provider, muted = true)
                }
            }
        }
    }
}

@Composable
private fun ConnectedList(calendars: List<ConnectedCalendarDto>) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        calendars.forEach { calendar ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.xl))
                        .background(PantopusColors.appSurface)
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                        .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                val provider = calendar.provider ?: "google"
                ProviderTile(provider = provider, muted = false)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = PROVIDER_NAMES[provider] ?: provider.replaceFirstChar { it.uppercase() },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PantopusColors.appText,
                    )
                    Text(
                        text = calendar.externalAccount ?: "Connected",
                        fontSize = 11.sp,
                        color = PantopusColors.appTextSecondary,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderTile(
    provider: String,
    muted: Boolean,
) {
    Box(
        modifier =
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg)),
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(
            icon = PROVIDER_ICONS[provider] ?: PantopusIcon.Calendar,
            contentDescription = provider,
            size = 19.dp,
            tint = if (muted) PantopusColors.appTextMuted else PantopusColors.primary600,
        )
    }
}
